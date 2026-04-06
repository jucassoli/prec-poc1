---
phase: 02-scraper-layer
plan: "04"
subsystem: api-controllers
tags: [controllers, dto, rest-api, validation, mockmvc]
dependency_graph:
  requires: [02-02, 02-03]
  provides: [REST endpoints for processo, precatorio, datajud]
  affects: [phase-03-bfs-engine]
tech_stack:
  added:
    - com.ninja-squad:springmockk:4.0.2 (MockkBean support for @WebMvcTest)
  patterns:
    - "@WebMvcTest slice tests with @MockkBean for isolated controller testing"
    - "@Validated + @Pattern on path variable for CNJ format enforcement"
    - "IllegalArgumentException handler in GlobalExceptionHandler for 400 responses"
    - "ConstraintViolationException handler in GlobalExceptionHandler for 400 responses"
key_files:
  created:
    - src/main/kotlin/br/com/precatorios/controller/ProcessoController.kt
    - src/main/kotlin/br/com/precatorios/controller/PrecatorioController.kt
    - src/main/kotlin/br/com/precatorios/controller/DataJudController.kt
    - src/main/kotlin/br/com/precatorios/dto/ProcessoResponseDTO.kt
    - src/main/kotlin/br/com/precatorios/dto/ParteDTO.kt
    - src/main/kotlin/br/com/precatorios/dto/IncidenteDTO.kt
    - src/main/kotlin/br/com/precatorios/dto/BuscaProcessoResponseDTO.kt
    - src/main/kotlin/br/com/precatorios/dto/PrecatorioResponseDTO.kt
    - src/main/kotlin/br/com/precatorios/dto/DataJudBuscarRequestDTO.kt
    - src/main/kotlin/br/com/precatorios/dto/DataJudBuscarResponseDTO.kt
    - src/test/kotlin/br/com/precatorios/controller/ProcessoControllerTest.kt
    - src/test/kotlin/br/com/precatorios/controller/PrecatorioControllerTest.kt
    - src/test/kotlin/br/com/precatorios/controller/DataJudControllerTest.kt
  modified:
    - src/main/kotlin/br/com/precatorios/exception/GlobalExceptionHandler.kt
    - build.gradle.kts
decisions:
  - "Use springmockk (com.ninja-squad:springmockk) for @MockkBean in @WebMvcTest since project already uses mockk and standard @MockBean would require Mockito"
  - "DataJudController uses body-level null check (when expression) rather than @Valid annotation since no Bean Validation constraint maps to null-check-at-least-one logic"
metrics:
  duration_minutes: 15
  completed_date: "2026-04-06"
  tasks_completed: 2
  files_created: 13
  files_modified: 2
---

# Phase 02 Plan 04: REST API Controllers Summary

**One-liner:** Three REST controllers exposing e-SAJ, CAC/SCP, and DataJud scrapers as typed JSON endpoints with CNJ format validation and MockMvc controller tests.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create DTOs and ProcessoController with buscar endpoint | 1055bd3 | ProcessoController.kt, 4 DTOs, ProcessoControllerTest.kt, GlobalExceptionHandler.kt, build.gradle.kts |
| 2 | Create PrecatorioController, DataJudController, DTOs, and controller tests | d9c5a44 | PrecatorioController.kt, DataJudController.kt, 3 DTOs, PrecatorioControllerTest.kt, DataJudControllerTest.kt |

## What Was Built

### Controllers

**ProcessoController** (`GET /api/v1/processo/{numero}`, `GET /api/v1/processo/buscar`):
- Path variable `{numero}` validated with `@Pattern` enforcing CNJ format `^[0-9]{7}-[0-9]{2}\.[0-9]{4}\.[0-9]\.[0-9]{2}\.[0-9]{4}$`
- Buscar endpoint accepts `nome`, `cpf`, or `numero` query params; throws `IllegalArgumentException` if all null (returns 400)
- Maps `ProcessoScraped` → `ProcessoResponseDTO` including `dadosCompletos` derived field

**PrecatorioController** (`GET /api/v1/precatorio/{numero}`):
- Delegates to `CacScraper.fetchPrecatorio`
- Maps `PrecatorioScraped` → `PrecatorioResponseDTO` with all CAC/SCP fields

**DataJudController** (`POST /api/v1/datajud/buscar`):
- Accepts `DataJudBuscarRequestDTO` (JSON body) with `numeroProcesso` or `codigoMunicipioIBGE`
- Delegates to `DataJudClient.buscarPorNumeroProcesso` or `buscarPorMunicipio` depending on which field is present
- Returns 400 if both fields are null

### Exception Handling (GlobalExceptionHandler additions)

- `ConstraintViolationException` → 400 with constraint messages joined by "; "
- `IllegalArgumentException` → 400 with exception message

### DTOs

| DTO | Purpose |
|-----|---------|
| `ProcessoResponseDTO` | Response for processo lookup; includes `dadosCompletos` boolean |
| `ParteDTO` | Embedded in ProcessoResponseDTO; nome/tipo/advogado |
| `IncidenteDTO` | Embedded in ProcessoResponseDTO; numero/descricao/link |
| `BuscaProcessoResponseDTO` / `BuscaProcessoItemDTO` | Response for buscar endpoint |
| `PrecatorioResponseDTO` | Response for precatorio lookup |
| `DataJudBuscarRequestDTO` | Input for DataJud POST endpoint |
| `DataJudBuscarResponseDTO` / `DataJudResultadoDTO` | Response for DataJud endpoint |

## Test Coverage

- `ProcessoControllerTest`: 6 tests — 200 valid, 400 invalid CNJ, 400 missing buscar params, 200 buscar with results, 503 on ScrapingException, partial missingFields response
- `PrecatorioControllerTest`: 3 tests — 200 valid, 503 on ScrapingException, 200 with partial missingFields
- `DataJudControllerTest`: 4 tests — 200 with numeroProcesso, 200 with codigoMunicipioIBGE, 400 empty body, 503 on ScrapingException

All 13 tests pass. Full test suite (`./gradlew test`) passes including all Phase 1 and Phase 2 scraper tests.

## Deviations from Plan

### Auto-added Dependencies

**1. [Rule 2 - Missing Critical Functionality] Added springmockk dependency**
- **Found during:** Task 1 test creation
- **Issue:** Plan specifies `@MockkBean` which requires `com.ninja-squad:springmockk` — not present in build.gradle.kts
- **Fix:** Added `testImplementation("com.ninja-squad:springmockk:4.0.2")` to build.gradle.kts
- **Files modified:** build.gradle.kts
- **Commit:** 1055bd3

## Known Stubs

None — all controllers are fully wired to their respective scrapers/clients.

## Threat Flags

No new trust boundaries introduced beyond those in the plan's threat model. All four threats (T-02-09 through T-02-12) are mitigated:
- T-02-09: `@Pattern` on ProcessoController `{numero}` path variable rejects non-CNJ strings
- T-02-10: DataJudController validates at least one search field present (400 on empty body)
- T-02-11: `@Size(min=3)` on `nome` parameter prevents single-character e-SAJ searches
- T-02-12: GlobalExceptionHandler returns `ErrorResponse` without stack traces on all exception types

## Self-Check: PASSED

Files verified:
- FOUND: src/main/kotlin/br/com/precatorios/controller/ProcessoController.kt
- FOUND: src/main/kotlin/br/com/precatorios/controller/PrecatorioController.kt
- FOUND: src/main/kotlin/br/com/precatorios/controller/DataJudController.kt
- FOUND: src/main/kotlin/br/com/precatorios/dto/ProcessoResponseDTO.kt
- FOUND: src/main/kotlin/br/com/precatorios/dto/PrecatorioResponseDTO.kt
- FOUND: src/main/kotlin/br/com/precatorios/dto/DataJudBuscarRequestDTO.kt
- FOUND: src/main/kotlin/br/com/precatorios/dto/DataJudBuscarResponseDTO.kt
- FOUND: src/test/kotlin/br/com/precatorios/controller/ProcessoControllerTest.kt
- FOUND: src/test/kotlin/br/com/precatorios/controller/PrecatorioControllerTest.kt
- FOUND: src/test/kotlin/br/com/precatorios/controller/DataJudControllerTest.kt

Commits verified:
- FOUND: 1055bd3 (Task 1)
- FOUND: d9c5a44 (Task 2)
