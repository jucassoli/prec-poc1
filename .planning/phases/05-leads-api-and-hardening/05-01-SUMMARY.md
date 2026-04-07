---
phase: 05-leads-api-and-hardening
plan: 01
subsystem: api
tags: [kotlin, spring-boot, jpa, flyway, pagination, jpql, join-fetch]

# Dependency graph
requires:
  - phase: 04-bfs-prospection-engine
    provides: Lead entity, LeadRepository, StatusContato enum, scoring pipeline

provides:
  - GET /api/v1/leads with JOIN FETCH pagination, multi-filter AND logic, score DESC default sort
  - PATCH /api/v1/leads/{id}/status with statusContato and observacao update
  - LeadResponseDTO with nested CredorSummaryDTO and PrecatorioSummaryDTO
  - LeadService with zero-score exclusion (incluirZero flag)
  - LeadRepository.findLeadsFiltered with explicit countQuery for JOIN FETCH pagination
  - Flyway V3 migration adding observacao TEXT column to leads table

affects:
  - 05-02-hardening (builds on GlobalExceptionHandler, same Lead entity)
  - 05-03-integration-tests (tests LeadController endpoints added here)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - JPQL JOIN FETCH with explicit countQuery to avoid Hibernate pagination issues
    - Nested DTO companion object fromEntity mapping pattern
    - @PageableDefault for configurable sort with query param override
    - zero-score exclusion via effectiveScoreMinimo = max(scoreMinimo ?: 0, 1)

key-files:
  created:
    - src/main/kotlin/br/com/precatorios/controller/LeadController.kt
    - src/main/kotlin/br/com/precatorios/service/LeadService.kt
    - src/main/kotlin/br/com/precatorios/dto/LeadResponseDTO.kt
    - src/main/kotlin/br/com/precatorios/dto/AtualizarStatusContatoRequestDTO.kt
    - src/main/kotlin/br/com/precatorios/exception/LeadNaoEncontradoException.kt
    - src/main/resources/db/migration/V3__add_lead_observacao.sql
    - src/test/kotlin/br/com/precatorios/service/LeadServiceTest.kt
    - src/test/kotlin/br/com/precatorios/controller/LeadControllerTest.kt
  modified:
    - src/main/kotlin/br/com/precatorios/domain/Lead.kt (added observacao field)
    - src/main/kotlin/br/com/precatorios/repository/LeadRepository.kt (added findLeadsFiltered)
    - src/main/kotlin/br/com/precatorios/exception/GlobalExceptionHandler.kt (added handlers)

key-decisions:
  - "New LeadResponseDTO (D-04) with nested CredorSummaryDTO/PrecatorioSummaryDTO — separate from LeadSummaryDTO used by ProspeccaoController"
  - "TooManyRequestsException and HttpMessageNotReadable handlers added to GlobalExceptionHandler as part of fixing pre-existing compilation blocker"
  - "effectiveScoreMinimo = max(scoreMinimo ?: 0, 1) when incluirZero=false implements D-02 zero-score exclusion"

patterns-established:
  - "JOIN FETCH + explicit countQuery pattern for paginated JPA queries with lazy associations"
  - "LeadResponseDTO.fromEntity companion object for entity-to-DTO mapping"
  - "@PageableDefault(sort=[score], direction=DESC) with Pageable param override support"

requirements-completed: [API-10, API-11]

# Metrics
duration: 30min
completed: 2026-04-07
---

# Phase 05 Plan 01: Leads REST API Summary

**GET /api/v1/leads with JOIN FETCH pagination and multi-filter AND logic, PATCH /api/v1/leads/{id}/status with statusContato and observacao, zero-score exclusion via incluirZero flag**

## Performance

- **Duration:** ~30 min
- **Started:** 2026-04-07T01:09:00Z
- **Completed:** 2026-04-07T01:39:13Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments

- LeadController with GET /api/v1/leads (pagination, score/status/entidadeDevedora filters, score DESC default) and PATCH /api/v1/leads/{id}/status
- LeadService with zero-score exclusion logic (D-02): effectiveScoreMinimo = max(scoreMinimo ?: 0, 1) when incluirZero=false
- LeadRepository extended with findLeadsFiltered JPQL JOIN FETCH query and explicit countQuery (prevents Hibernate N+1 and pagination count issues)
- LeadResponseDTO with nested CredorSummaryDTO and PrecatorioSummaryDTO shape (D-04) — separate from LeadSummaryDTO
- Flyway V3 migration adding observacao TEXT column to leads table

## Task Commits

Each task was committed atomically:

1. **Task 1: LeadResponseDTO, LeadService, LeadRepository, Flyway V3** - `c362aae` (feat)
2. **Task 2: LeadController and controller tests** - `e40c4cc` (feat)

_Note: TDD tasks — tests written first (RED), then implementation (GREEN)._

## Files Created/Modified

- `src/main/kotlin/br/com/precatorios/controller/LeadController.kt` - REST controller: GET /api/v1/leads + PATCH /{id}/status
- `src/main/kotlin/br/com/precatorios/service/LeadService.kt` - Business logic: listarLeads with zero-score exclusion, atualizarStatusContato
- `src/main/kotlin/br/com/precatorios/dto/LeadResponseDTO.kt` - Nested response DTO with CredorSummaryDTO and PrecatorioSummaryDTO
- `src/main/kotlin/br/com/precatorios/dto/AtualizarStatusContatoRequestDTO.kt` - PATCH request body with @NotNull statusContato
- `src/main/kotlin/br/com/precatorios/exception/LeadNaoEncontradoException.kt` - 404 exception for missing leads
- `src/main/resources/db/migration/V3__add_lead_observacao.sql` - Flyway migration: ALTER TABLE leads ADD COLUMN observacao TEXT
- `src/main/kotlin/br/com/precatorios/domain/Lead.kt` - Added var observacao: String? = null field
- `src/main/kotlin/br/com/precatorios/repository/LeadRepository.kt` - Added findLeadsFiltered with JOIN FETCH and countQuery
- `src/main/kotlin/br/com/precatorios/exception/GlobalExceptionHandler.kt` - Added LeadNaoEncontradoException, TooManyRequestsException, HttpMessageNotReadable handlers
- `src/test/kotlin/br/com/precatorios/service/LeadServiceTest.kt` - 6 unit tests for LeadService (TDD)
- `src/test/kotlin/br/com/precatorios/controller/LeadControllerTest.kt` - 8 controller tests (TDD)

## Decisions Made

- Created separate `LeadResponseDTO` (D-04) rather than reusing `LeadSummaryDTO` — preserves backward compatibility with ProspeccaoController
- Used `@PageableDefault(sort=["score"], direction=DESC)` with Spring Pageable for configurable sort override via `?sort=dataCriacao,desc`
- Added `countQuery` explicitly to `findLeadsFiltered` to avoid Hibernate JOIN FETCH pagination issues (otherwise Hibernate fetches all rows in memory for counting)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed pre-existing GlobalExceptionHandlerTest compilation failure**
- **Found during:** Task 1 (first test run)
- **Issue:** GlobalExceptionHandlerTest.kt referenced `handleTooManyRequests`, `handleHttpMessageNotReadable` methods and `TooManyRequestsException` that didn't exist in GlobalExceptionHandler. Also had a type mismatch: passing `null as HttpInputMessage?` where `HttpInputMessage` is required.
- **Fix:** Added `handleTooManyRequests` (429) and `handleHttpMessageNotReadable` (400) to GlobalExceptionHandler. Fixed test constructor call to use single-arg `HttpMessageNotReadableException("bad json")`.
- **Files modified:** `GlobalExceptionHandler.kt`, `GlobalExceptionHandlerTest.kt`
- **Verification:** Test compilation succeeds, all GlobalExceptionHandlerTest tests pass
- **Committed in:** `c362aae` (Task 1 commit)

**2. [Rule 2 - Missing Critical] LeadNaoEncontradoException handler added to GlobalExceptionHandler**
- **Found during:** Task 2 (controller test for 404 case)
- **Issue:** Plan specified adding the handler in Task 2 action step 3; handled as part of the plan.
- **Fix:** Added `@ExceptionHandler(LeadNaoEncontradoException::class)` handler returning 404 ErrorResponse.
- **Files modified:** `GlobalExceptionHandler.kt`
- **Verification:** PATCH /api/v1/leads/999/status returns 404 with structured error in controller test
- **Committed in:** `c362aae` (Task 1 commit, moved from Task 2 to avoid ordering issue)

---

**Total deviations:** 2 auto-fixed (1 blocking pre-existing bug, 1 planned handler moved to Task 1 commit)
**Impact on plan:** Both fixes necessary for compilation and correctness. No scope creep.

## Issues Encountered

- Pre-existing GlobalExceptionHandlerTest had compilation errors referencing methods from plan 05-02 that weren't implemented yet — fixed by implementing those handlers as part of this plan (they were logically needed for LeadNaoEncontradoException anyway).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- LeadController endpoints ready for integration testing in Phase 05-03
- GlobalExceptionHandler now handles TooManyRequests and HttpMessageNotReadable (pre-empting 05-02 work)
- Flyway V3 migration in place — next migration should be V4

---
*Phase: 05-leads-api-and-hardening*
*Completed: 2026-04-07*
