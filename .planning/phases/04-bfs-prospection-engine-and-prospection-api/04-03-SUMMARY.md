---
phase: 04-bfs-prospection-engine-and-prospection-api
plan: 03
subsystem: api
tags: [spring-boot, kotlin, rest, validation, webmvctest, mockk, prospeccao, bfs]

requires:
  - phase: 04-01
    provides: ProspeccaoService.criar(), BfsProspeccaoEngine.start(), ProspeccaoRepository
  - phase: 04-02
    provides: BfsProspeccaoEngine.passaFiltros() filter parameters (entidadesDevedoras, valorMinimo, apenasAlimentar, apenasPendentes)

provides:
  - POST /api/v1/prospeccao — 202 with prospeccaoId, CNJ validation, async BFS dispatch
  - GET /api/v1/prospeccao/{id} — status counters, Retry-After header, leads on CONCLUIDA, erroMensagem on ERRO
  - GET /api/v1/prospeccao — paginated list with optional status filter
  - ProspeccaoNaoEncontradaException mapped to 404 in GlobalExceptionHandler
  - MethodArgumentNotValidException handler for @RequestBody @Valid failures

affects:
  - phase-05-leads-api (LeadSummaryDTO structure)
  - future-integration-tests

tech-stack:
  added: []
  patterns:
    - "@RequestBody @Valid with MethodArgumentNotValidException handler for field-level validation errors (vs ConstraintViolationException for path/query params)"
    - "Retry-After: 10 header on EM_ANDAMENTO status responses per D-14"
    - "ProspeccaoController delegates to service.criar() (sync persist) then engine.start() (@Async dispatch) — no @Transactional on controller"
    - "ObjectMapper injected into controller for scoreDetalhes JSON deserialization using TypeReference anonymous object"

key-files:
  created:
    - src/main/kotlin/br/com/precatorios/controller/ProspeccaoController.kt
    - src/main/kotlin/br/com/precatorios/dto/ProspeccaoRequestDTO.kt
    - src/main/kotlin/br/com/precatorios/dto/ProspeccaoIniciadaDTO.kt
    - src/main/kotlin/br/com/precatorios/dto/ProspeccaoStatusDTO.kt
    - src/main/kotlin/br/com/precatorios/dto/LeadSummaryDTO.kt
    - src/main/kotlin/br/com/precatorios/dto/ProspeccaoListItemDTO.kt
    - src/main/kotlin/br/com/precatorios/exception/ProspeccaoNaoEncontradaException.kt
    - src/test/kotlin/br/com/precatorios/controller/ProspeccaoControllerTest.kt
  modified:
    - src/main/kotlin/br/com/precatorios/exception/GlobalExceptionHandler.kt

key-decisions:
  - "ObjectMapper not mocked in @WebMvcTest — Spring's auto-configured instance handles JSON serialization; lead.scoreDetalhes=null avoids objectMapper.readValue() call in tests"
  - "MethodArgumentNotValidException handler added to GlobalExceptionHandler (D-13: @RequestBody @Valid throws this, not ConstraintViolationException which is for path/query params)"
  - "TypeReference anonymous object used in controller for scoreDetalhes deserialization to avoid Kotlin extension function ambiguity with mocked ObjectMapper in tests"

patterns-established:
  - "ProspeccaoController pattern: controller is not @Transactional; service.criar() commits first, then @Async engine.start() dispatches"
  - "@WebMvcTest test pattern: exclude ObjectMapper from MockkBeans; set lead.scoreDetalhes=null to skip deserialization code path"

requirements-completed: [PROS-07, API-01, API-02, API-03, API-04, API-05]

duration: 18min
completed: 2026-04-06
---

# Phase 4 Plan 3: Prospection REST API Summary

**Three REST endpoints exposing the BFS prospection engine: POST (202 + async dispatch), GET /{id} (counters + Retry-After + leads), GET list (paginated + status filter), with 12 WebMvcTest cases all passing**

## Performance

- **Duration:** 18 min
- **Started:** 2026-04-06T20:05:00Z
- **Completed:** 2026-04-06T20:23:00Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments

- POST /api/v1/prospeccao returns HTTP 202 with `prospeccaoId`; invalid CNJ format returns 400 synchronously before async dispatch
- GET /api/v1/prospeccao/{id} returns status with three counters, Retry-After: 10 header when EM_ANDAMENTO, leads[] array when CONCLUIDA, erroMensagem when ERRO, 404 for non-existent
- GET /api/v1/prospeccao returns paginated list filterable by `?status=` query param
- GlobalExceptionHandler extended with ProspeccaoNaoEncontradaException (404) and MethodArgumentNotValidException (400) handlers
- 12 @WebMvcTest cases covering all endpoint behaviors

## Task Commits

1. **Task 1: DTOs, ProspeccaoController POST and GET status endpoints** - `386fb70` (feat)
2. **Task 2: GET list endpoint and ProspeccaoListItemDTO** - `938e54b` (feat)

## Files Created/Modified

- `src/main/kotlin/br/com/precatorios/controller/ProspeccaoController.kt` — REST controller with iniciar, getStatus, listar endpoints
- `src/main/kotlin/br/com/precatorios/dto/ProspeccaoRequestDTO.kt` — @Valid request DTO with CNJ @Pattern and @NotBlank
- `src/main/kotlin/br/com/precatorios/dto/ProspeccaoIniciadaDTO.kt` — 202 response with prospeccaoId
- `src/main/kotlin/br/com/precatorios/dto/ProspeccaoStatusDTO.kt` — status response with counters, leads, erroMensagem
- `src/main/kotlin/br/com/precatorios/dto/LeadSummaryDTO.kt` — lead summary with score, credor, precatorio fields
- `src/main/kotlin/br/com/precatorios/dto/ProspeccaoListItemDTO.kt` — list item DTO for paginated GET
- `src/main/kotlin/br/com/precatorios/exception/ProspeccaoNaoEncontradaException.kt` — 404 exception for missing prospeccao
- `src/main/kotlin/br/com/precatorios/exception/GlobalExceptionHandler.kt` — added two new handlers
- `src/test/kotlin/br/com/precatorios/controller/ProspeccaoControllerTest.kt` — 12 WebMvcTest cases

## Decisions Made

- ObjectMapper is NOT mocked as MockkBean — Spring's auto-configured ObjectMapper handles response JSON serialization. Mocking it would break the entire MockMvc serialization pipeline.
- `scoreDetalhes = null` in test Lead fixture avoids the objectMapper.readValue() code path, keeping tests clean without mocking ObjectMapper.
- `TypeReference` anonymous object (`object : TypeReference<Map<String, Any?>>() {}`) used instead of Kotlin extension `readValue<T>()` to avoid type inference ambiguity in test contexts.

## Deviations from Plan

None — plan executed exactly as written, with one implementation detail adjusted: used `TypeReference` anonymous object instead of Kotlin extension function in the controller for cleaner test isolation.

## Issues Encountered

During the RED phase, the initial test file used the Kotlin extension `objectMapper.readValue<Map<String, Any?>>()` as a mock target, which caused type inference errors. Resolved by removing ObjectMapper from MockkBeans (it is not needed in tests since scoreDetalhes is null in test data) and using TypeReference in the controller implementation.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Full prospection REST API is ready for integration testing
- Phase 5 (Leads API) can use LeadSummaryDTO structure as a reference for the leads management endpoints
- All three contract requirements (POST, GET status, GET list) are implemented and tested

## Self-Check: PASSED

- `src/main/kotlin/br/com/precatorios/controller/ProspeccaoController.kt` — FOUND
- `src/main/kotlin/br/com/precatorios/dto/ProspeccaoRequestDTO.kt` — FOUND
- `src/main/kotlin/br/com/precatorios/dto/ProspeccaoStatusDTO.kt` — FOUND
- `src/test/kotlin/br/com/precatorios/controller/ProspeccaoControllerTest.kt` — FOUND
- Commit `386fb70` — FOUND
- Commit `938e54b` — FOUND
- 12 tests, 0 failures — VERIFIED

---
*Phase: 04-bfs-prospection-engine-and-prospection-api*
*Completed: 2026-04-06*
