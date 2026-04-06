---
phase: 03-cache-and-scoring
plan: 02
subsystem: api
tags: [kotlin, spring-boot, scoring, configuration-properties, jpa, spring-data]

# Dependency graph
requires:
  - phase: 01-foundation
    provides: Lead and Precatorio JPA entities, LeadRepository base interface
  - phase: 03-cache-and-scoring/03-01
    provides: CacheConfig and Caffeine-wired cache infrastructure
provides:
  - ScoringProperties @ConfigurationProperties binding five scoring criteria from application.yml
  - ScoringService.score() returning ScoredResult with 0-100 total and per-criterion detalhes map
  - LeadRepository.findByScoreGreaterThan for Phase 5 Leads API SCOR-04
  - Scoring section in application.yml with all weights, brackets, and entity maps
affects:
  - phase-04-bfs-engine
  - phase-05-leads-api

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@ConfigurationProperties with nested data classes for multi-level YAML binding"
    - "Null-safe scoring: null input -> null criterion -> omitted from total sum (listOfNotNull)"
    - "Case-normalization via .uppercase() before map/contains lookups"
    - "Bracket lookup via sortedByDescending on limiteInferior for range matching"

key-files:
  created:
    - src/main/kotlin/br/com/precatorios/config/ScoringProperties.kt
    - src/main/kotlin/br/com/precatorios/service/ScoringService.kt
    - src/test/kotlin/br/com/precatorios/service/ScoringServiceTest.kt
  modified:
    - src/main/resources/application.yml
    - src/main/kotlin/br/com/precatorios/repository/LeadRepository.kt

key-decisions:
  - "Null input produces null in detalhes (not 0) to distinguish 'data unavailable' from 'scored zero' (D-13/D-14)"
  - "Entity lookup uses String.contains() not equality to handle variations in debtor name formatting"
  - "Status lookup uses equality (uppercase) not contains to avoid false positives between PAGO/PARCIALMENTE PAGO"
  - "listOfNotNull() used for total summation so null criteria contribute 0 without separate null checks"

patterns-established:
  - "Scoring criteria null-safety: fun scoreFoo(x: T?): Int? { x ?: return null; ... }"
  - "Bracket range matching: faixas.sortedByDescending { it.limiteInferior }.firstOrNull { value >= it.limiteInferior }"
  - "Pure unit tests: ScoringService instantiated directly without Spring context"

requirements-completed: [SCOR-01, SCOR-02, SCOR-03, SCOR-04]

# Metrics
duration: 2min
completed: 2026-04-06
---

# Phase 03 Plan 02: Scoring Engine Summary

**Configurable 0-100 scoring engine for precatorios with five weighted criteria, null-safe detalhes breakdown, and LeadRepository score filter**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-06T15:24:24Z
- **Completed:** 2026-04-06T15:26:25Z
- **Tasks:** 2 (TDD: RED + GREEN commits)
- **Files modified:** 5

## Accomplishments

- ScoringProperties binds all five scoring criteria from application.yml via @ConfigurationProperties; weights and brackets configurable without code changes
- ScoringService.score() returns ScoredResult with 0-100 total and flat detalhes map (keys: valor, entidadeDevedora, statusPagamento, posicaoCronologica, natureza, total)
- Null-safe design: null field input produces null in detalhes and contributes 0 to total (D-13/D-14/D-15)
- Case normalization for status and entity lookups prevents scoring misses on mixed-case scraped data (Pitfall 4)
- LeadRepository.findByScoreGreaterThan added as Spring Data derived query for SCOR-04

## Task Commits

Each task was committed atomically:

1. **Task 1: Create ScoringProperties and add scoring section to application.yml** - `2ab50eb` (feat)
2. **Task 2 RED: Failing unit tests for ScoringService** - `9ebbede` (test)
3. **Task 2 GREEN: Implement ScoringService and LeadRepository score filter** - `26ba76a` (feat)

## Files Created/Modified

- `src/main/kotlin/br/com/precatorios/config/ScoringProperties.kt` - @ConfigurationProperties for all five scoring criteria with nested data classes and sensible defaults
- `src/main/kotlin/br/com/precatorios/service/ScoringService.kt` - Stateless scoring function with five private criterion methods; exports ScoredResult data class
- `src/test/kotlin/br/com/precatorios/service/ScoringServiceTest.kt` - 14 pure unit tests covering all criteria, null handling, case normalization, and bracket ranges
- `src/main/resources/application.yml` - Added `scoring:` section with full bracket/map configuration for all five criteria
- `src/main/kotlin/br/com/precatorios/repository/LeadRepository.kt` - Added findByScoreGreaterThan(score: Int, pageable: Pageable): Page<Lead>

## Decisions Made

- Null input produces null in detalhes (not 0): distinguishes "data unavailable" from "scored zero", allowing Phase 5 to render absent data differently from a zero score
- Entity lookup uses `String.contains()` (not equality) to handle variations in scraped debtor names (e.g., full vs. partial entity strings)
- Status lookup uses equality after `.uppercase()` to avoid false positives between "PAGO" and "PARCIALMENTE PAGO"
- `listOfNotNull()` for total summation: clean idiom that correctly excludes null criteria without separate null checks

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- ScoringService is ready to be injected into the Phase 4 BFS engine to score each discovered lead
- LeadRepository.findByScoreGreaterThan is ready for the Phase 5 Leads API endpoint
- Scoring weights in application.yml can be tuned by FUNPREC without code changes
- No blockers for Phase 4

## Known Stubs

None - ScoringService is fully wired with real logic. No placeholder data.

---
*Phase: 03-cache-and-scoring*
*Completed: 2026-04-06*
