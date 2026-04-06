---
phase: 04-bfs-prospection-engine-and-prospection-api
plan: 02
subsystem: engine
tags: [kotlin, bfs, filtering, spring-boot, mockk, junit5]

# Dependency graph
requires:
  - phase: 04-01
    provides: BFS engine core with @Async dispatch, visited set, and partial failure isolation
provides:
  - passaFiltros method with all four filter checks (entidadesDevedoras, valorMinimo, apenasAlimentar, apenasPendentes)
  - depth control guarding BFS expansion (if depth < profundidadeMaxima)
  - maxCredores early-exit in while loop and inner partes loop
  - maxSearchResults cap via take(maxSearchResults) on buscarPorNome results
  - max-search-results-per-creditor config key in application.yml
  - 10 new unit tests covering all filter types, depth, maxCredores, D-10, D-11, D-03
affects:
  - 04-03-REST-endpoints (ProspeccaoController dispatches to this engine)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "passaFiltros predicate: filters applied during BFS traversal, not at expansion — D-09/D-10 compliance"
    - "credoresEncontrados counts only filter-passing leads — D-11"
    - "BFS expansion is independent of filter results — D-10"

key-files:
  created: []
  modified:
    - src/main/kotlin/br/com/precatorios/engine/BfsProspeccaoEngine.kt
    - src/main/resources/application.yml
    - src/test/kotlin/br/com/precatorios/engine/BfsProspeccaoEngineTest.kt

key-decisions:
  - "D-09: Filters applied during BFS traversal — non-matching precatorios not scored or persisted"
  - "D-10: Filters do NOT affect BFS expansion — all creditor branches explored regardless of filter outcome"
  - "D-11: credoresEncontrados counts only filter-passing leads, not all processed creditors"

patterns-established:
  - "passaFiltros pattern: early return false on each filter check, return true at end — clean short-circuit predicate"

requirements-completed:
  - PROS-02
  - PROS-03
  - PROS-05

# Metrics
duration: 25min
completed: 2026-04-06
---

# Phase 4 Plan 02: BFS Filters, Depth Control, and maxCredores Summary

**BFS passaFiltros predicate with all four prospection filters, depth/maxCredores guards already in place from plan 01, max-search-results-per-creditor config key, and 10 new unit tests verifying filter independence from BFS expansion**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-04-06T20:00:00Z
- **Completed:** 2026-04-06T20:25:00Z
- **Tasks:** 1
- **Files modified:** 3

## Accomplishments
- Implemented `passaFiltros` with four filter checks: entidadesDevedoras (case-insensitive contains), valorMinimo (BigDecimal comparison), apenasAlimentar (natureza contains "ALIMENTAR"), apenasPendentes (statusPagamento == "PENDENTE")
- Added `max-search-results-per-creditor: 10` to `application.yml` under the existing `prospeccao:` block
- Verified depth control (`if (depth < profundidadeMaxima)`) and maxCredores early-exit guards were already correctly in place from plan 01
- Added 10 new unit tests covering all filter types, BFS depth bounds (0 and 1), maxCredores stop, D-10 expansion independence, D-11 counter accuracy, and D-03 search results cap

## Task Commits

Each task was committed atomically:

1. **Task 1: Add filters, depth control, maxCredores early-exit, and maxSearchResults config to BFS engine** - `df5348e` (feat)

## Files Created/Modified
- `src/main/kotlin/br/com/precatorios/engine/BfsProspeccaoEngine.kt` - Implemented passaFiltros with four filter checks (was stub returning true)
- `src/main/resources/application.yml` - Added `max-search-results-per-creditor: 10` under `prospeccao:` section
- `src/test/kotlin/br/com/precatorios/engine/BfsProspeccaoEngineTest.kt` - Added 10 new test methods (Tests 7-16) covering all filter/depth/limit scenarios

## Decisions Made
- Test for `valorMinimo` filter uses `returnsMany listOf(precLow, precHigh, precLow, precHigh)` with 4 entries to correctly match 2 parties × 2 incidentes = 4 `precatorioRepository.save` calls; using only 2 entries caused MockK to repeat the last entry and incorrectly pass the third call.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed test mock for valorMinimo filter — incorrect returnsMany entry count**
- **Found during:** Task 1 (TDD GREEN phase verification)
- **Issue:** `precatorioRepository.save` mock had `returnsMany listOf(precLow, precHigh)` (2 entries) but test scenario has 2 parties × 2 incidentes = 4 save calls. MockK repeats the last entry after exhausting the list, so call 3 returned `precHigh` (passes filter) instead of `precLow` (fails filter), causing 3 leads to be created instead of the expected 2.
- **Fix:** Changed to `returnsMany listOf(precLow, precHigh, precLow, precHigh)` to correctly model Alice[low,high] and Bob[low,high]
- **Files modified:** src/test/kotlin/br/com/precatorios/engine/BfsProspeccaoEngineTest.kt
- **Verification:** `./gradlew test --tests "br.com.precatorios.engine.BfsProspeccaoEngineTest"` passes (16 tests)
- **Committed in:** df5348e (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 bug in test mock setup)
**Impact on plan:** Fix essential for test correctness. No scope creep.

## Issues Encountered
- Depth control and maxCredores guards were already correctly implemented in plan 01 — plan 02 verified them in tests rather than adding new code for those aspects.

## Known Stubs
None — `passaFiltros` is fully implemented with all four filters.

## Next Phase Readiness
- BFS engine fully complete: core traversal (04-01) + filter/depth/limit controls (04-02)
- Ready for 04-03: REST endpoints (ProspeccaoController, POST/GET prospection endpoints)
- No blockers

---
*Phase: 04-bfs-prospection-engine-and-prospection-api*
*Completed: 2026-04-06*
