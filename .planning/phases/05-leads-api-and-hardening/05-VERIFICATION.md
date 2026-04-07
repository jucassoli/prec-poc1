---
phase: 05-leads-api-and-hardening
verified: 2026-04-06T23:30:00Z
status: human_needed
score: 10/10 must-haves verified
overrides_applied: 0
re_verification: false
human_verification:
  - test: "Run full test suite to confirm all tests pass"
    expected: "All tests green: LeadServiceTest (6), LeadControllerTest (8), GlobalExceptionHandlerTest (9), StaleJobRecoveryRunnerTest (2), DataJudHealthIndicatorTest (2), FullStackProspeccaoIntegrationTest (1)"
    why_human: "Testcontainers integration test requires Docker daemon; cannot run in static file analysis context"
  - test: "Start the API with docker-compose up and call GET /api/v1/leads"
    expected: "HTTP 200 with paginated JSON body containing content array, sorted by score DESC"
    why_human: "Runtime behavior and sort order correctness cannot be verified without a running PostgreSQL instance"
  - test: "Call PATCH /api/v1/leads/{id}/status then GET /api/v1/leads to confirm the change is immediately visible"
    expected: "Updated statusContato and observacao appear in subsequent GET /leads response"
    why_human: "Transactional visibility and response shape correctness require live database"
  - test: "Verify /actuator/health shows dataJudHealthIndicator component status"
    expected: "Response includes 'dataJudHealthIndicator': { 'status': 'UP' or 'DOWN' } with details"
    why_human: "Spring Boot auto-naming of HealthIndicator beans and actual endpoint response require running application"
---

# Phase 05: Leads API and Hardening Verification Report

**Phase Goal:** The leads management endpoints (filtered list, contact status updates, structured error responses) are live; the full stack is covered by Testcontainers integration tests; operational concerns (stale job recovery, DataJud health check) are addressed
**Verified:** 2026-04-06T23:30:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

Derived from ROADMAP.md Phase 5 success criteria (5 criteria) merged with plan must_haves (10 items).

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | GET /api/v1/leads returns paginated leads sorted by score DESC, filterable by scoreMinimo / statusContato / entidadeDevedora, with no N+1 queries | VERIFIED | LeadController.kt line 38: `@PageableDefault(size=20, sort=["score"], direction=DESC)`; LeadRepository.kt has JOIN FETCH query with explicit countQuery preventing N+1 |
| 2 | PATCH /api/v1/leads/{id}/status updates contact status and optional note | VERIFIED | LeadService.atualizarStatusContato updates both `lead.statusContato` and `lead.observacao`, saves, returns updated DTO |
| 3 | All API errors return structured JSON with status, message, timestamp — no stack traces | VERIFIED | GlobalExceptionHandler has 9 handlers; handleGeneric returns fixed "Internal server error" string without ex.message; test asserts `doesNotContain("secret error details")` |
| 4 | Testcontainers integration test runs full prospection with mock scrapers and asserts scored leads | VERIFIED | FullStackProspeccaoIntegrationTest.kt exists, uses `@SpringBootTest(RANDOM_PORT)`, `@Testcontainers`, `PostgreSQLContainer`, `@MockkBean` scrapers, Awaitility polling, asserts leads in DB and GET /api/v1/leads returns HTTP 200 with non-empty content |
| 5 | On startup, EM_ANDAMENTO jobs are reset to ERRO with a recovery message containing original dataInicio | VERIFIED | StaleJobRecoveryRunner.kt sets status=ERRO and erroMensagem="Interrompida por reinicio (iniciada em ${prospeccao.dataInicio})"; test asserts exact message format |
| 6 | GET /api/v1/leads excludes zero-score leads by default, includes them with incluirZero=true | VERIFIED | LeadService.kt: `effectiveScoreMinimo = maxOf(scoreMinimo ?: 0, 1)` when incluirZero=false; passes null when incluirZero=true with no scoreMinimo |
| 7 | GET /api/v1/leads filters with AND logic (scoreMinimo, statusContato, entidadeDevedora) | VERIFIED | JPQL query in LeadRepository.kt uses `AND` chains for all three filter params |
| 8 | PATCH on non-existent lead returns 404 with structured ErrorResponse | VERIFIED | GlobalExceptionHandler has `@ExceptionHandler(LeadNaoEncontradoException::class)` returning 404; controller test asserts status=404 and message="Lead id=999 nao encontrado" |
| 9 | TooManyRequestsException returns 429, not 503 from parent ScrapingException | VERIFIED | handleTooManyRequests declared at line 15, handleScrapingException at line 55 — correct ordering for Spring exception resolution |
| 10 | DataJud health indicator shows UP/DOWN at /actuator/health by calling doBuscarPorNumero directly | VERIFIED | DataJudHealthIndicator.kt calls `dataJudClient.doBuscarPorNumero(...)` directly (internal Kotlin method, bypasses @Cacheable); application.yml has `management.endpoint.health.show-details: always` |

**Score:** 10/10 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/br/com/precatorios/controller/LeadController.kt` | GET /api/v1/leads and PATCH /api/v1/leads/{id}/status | VERIFIED | 56 lines, both endpoints implemented, wired to LeadService |
| `src/main/kotlin/br/com/precatorios/service/LeadService.kt` | listarLeads + atualizarStatusContato | VERIFIED | Both methods present, effectiveScoreMinimo logic correct |
| `src/main/kotlin/br/com/precatorios/repository/LeadRepository.kt` | findLeadsFiltered with JOIN FETCH + countQuery | VERIFIED | JPQL query with `JOIN FETCH l.credor`, `JOIN FETCH l.precatorio`, explicit countQuery |
| `src/main/kotlin/br/com/precatorios/dto/LeadResponseDTO.kt` | Nested CredorSummaryDTO + PrecatorioSummaryDTO, fromEntity companion | VERIFIED | Both inner DTOs present, fromEntity maps all fields |
| `src/main/kotlin/br/com/precatorios/dto/AtualizarStatusContatoRequestDTO.kt` | @NotNull statusContato, optional observacao | VERIFIED | `@field:NotNull val statusContato: StatusContato`, `val observacao: String? = null` |
| `src/main/kotlin/br/com/precatorios/exception/LeadNaoEncontradoException.kt` | RuntimeException subclass | VERIFIED | `class LeadNaoEncontradoException(message: String) : RuntimeException(message)` |
| `src/main/kotlin/br/com/precatorios/domain/Lead.kt` | observacao field added | VERIFIED | `var observacao: String? = null` with `@Column(name = "observacao", columnDefinition = "TEXT")` |
| `src/main/resources/db/migration/V3__add_lead_observacao.sql` | ALTER TABLE leads ADD COLUMN observacao TEXT | VERIFIED | Exact content: `ALTER TABLE leads ADD COLUMN observacao TEXT;` |
| `src/main/kotlin/br/com/precatorios/exception/GlobalExceptionHandler.kt` | handleTooManyRequests, handleHttpMessageNotReadable, handleLeadNaoEncontrado | VERIFIED | All three handlers present; TooManyRequests at line 15, ScrapingException at line 55 (correct order) |
| `src/main/kotlin/br/com/precatorios/startup/StaleJobRecoveryRunner.kt` | ApplicationRunner recovering EM_ANDAMENTO jobs | VERIFIED | Contains "Interrompida por reinicio"; calls findByStatus(EM_ANDAMENTO, Pageable.unpaged()) |
| `src/main/kotlin/br/com/precatorios/health/DataJudHealthIndicator.kt` | HealthIndicator calling doBuscarPorNumero | VERIFIED | Health.up() / Health.down() pattern; calls internal method directly |
| `src/test/kotlin/br/com/precatorios/integration/FullStackProspeccaoIntegrationTest.kt` | @SpringBootTest Testcontainers test | VERIFIED | @SpringBootTest, @Testcontainers, PostgreSQLContainer, @MockkBean scrapers, Awaitility, asserts leads and GET /api/v1/leads |
| `src/test/kotlin/br/com/precatorios/startup/StaleJobRecoveryRunnerTest.kt` | 2 unit tests for recovery | VERIFIED | 2 tests: recovery of stale jobs, no-op when none exist |
| `src/test/kotlin/br/com/precatorios/health/DataJudHealthIndicatorTest.kt` | 2 unit tests UP/DOWN | VERIFIED | 2 tests: UP on success, DOWN on exception |
| `src/test/kotlin/br/com/precatorios/service/LeadServiceTest.kt` | 6 unit tests | VERIFIED | 6 tests covering scoreMinimo, incluirZero, atualizarStatusContato, LeadNaoEncontradoException, fromEntity |
| `src/test/kotlin/br/com/precatorios/controller/LeadControllerTest.kt` | 8 controller tests | VERIFIED | 8 tests: GET filters, incluirZero, sort override, PATCH success, PATCH 404, PATCH 400 |
| `src/test/kotlin/br/com/precatorios/exception/GlobalExceptionHandlerTest.kt` | 9 unit tests all exception paths | VERIFIED | 9 tests covering all handlers including TooManyRequests→429, generic→500 without stack trace |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| LeadController.kt | LeadService.kt | constructor injection | WIRED | `class LeadController(private val leadService: LeadService)` line 26 |
| LeadService.kt | LeadRepository.kt | findLeadsFiltered call | WIRED | `leadRepository.findLeadsFiltered(effectiveScoreMinimo, statusContato, entidadeDevedoraPattern, pageable)` |
| LeadRepository.kt | Lead entity (JOIN FETCH credor + precatorio) | JPQL @Query | WIRED | `JOIN FETCH l.credor c` and `JOIN FETCH l.precatorio p` in query |
| GlobalExceptionHandler.kt | ErrorResponse.kt | all handlers return ErrorResponse | WIRED | All 9 handlers return `ErrorResponse(...)` instances |
| StaleJobRecoveryRunner.kt | ProspeccaoRepository | findByStatus(EM_ANDAMENTO) | WIRED | `prospeccaoRepository.findByStatus(StatusProspeccao.EM_ANDAMENTO, Pageable.unpaged())` |
| DataJudHealthIndicator.kt | DataJudClient.doBuscarPorNumero | direct internal method call | WIRED | `dataJudClient.doBuscarPorNumero("0000000-00.0000.0.00.0000")` — not cached public method |
| FullStackProspeccaoIntegrationTest.kt | POST /api/v1/prospeccao -> GET /api/v1/leads | TestRestTemplate HTTP calls | WIRED | `testRestTemplate.postForEntity("/api/v1/prospeccao", ...)` then `testRestTemplate.getForEntity("/api/v1/leads", ...)` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| LeadController.listar | `result: Page<LeadResponseDTO>` | LeadRepository.findLeadsFiltered (JOIN FETCH JPQL) | Yes — JPQL query fetches from `leads` table with joins to `credores` and `precatorios` | FLOWING |
| LeadController.atualizarStatus | `updated: LeadResponseDTO` | leadRepository.save(lead) after fetching by ID | Yes — fetches from DB, saves back, returns mapped DTO | FLOWING |
| DataJudHealthIndicator.health | `Health` result | dataJudClient.doBuscarPorNumero (live HTTP call) | Yes — live network probe to DataJud API | FLOWING |
| FullStackProspeccaoIntegrationTest | `leads` in DB | BFS engine with mock scrapers -> PostgreSQL via Testcontainers | Yes — mock scrapers produce deterministic ProcessoScraped/PrecatorioScraped data; BFS engine persists real scored leads | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED — no runnable entry points available without Docker/PostgreSQL (Testcontainers test requires Docker daemon). Static analysis and test structure verification sufficient.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| API-10 | 05-01-PLAN.md | GET /api/v1/leads — paginated leads with filters, score DESC sort | SATISFIED | LeadController.kt GET endpoint; LeadRepository findLeadsFiltered with JOIN FETCH; LeadService filter + zero-score logic |
| API-11 | 05-01-PLAN.md | PATCH /api/v1/leads/{id}/status — update contact status with optional note | SATISFIED | LeadController.kt PATCH endpoint; LeadService.atualizarStatusContato updates statusContato + observacao |
| API-12 | 05-02-PLAN.md | All API errors return structured JSON {status, message, timestamp}, no stack traces | SATISFIED | GlobalExceptionHandler with 9 handlers; all return ErrorResponse; handleGeneric returns fixed string without ex.message; 9 unit tests verify all paths |

No orphaned requirements detected — REQUIREMENTS.md maps API-10, API-11, API-12 to Phase 5 only, and all three are claimed by plan frontmatter.

### Anti-Patterns Found

No anti-patterns detected in any key file. Scan ran across all 5 main source files (LeadController, LeadService, LeadRepository, StaleJobRecoveryRunner, DataJudHealthIndicator) — no TODO/FIXME/placeholder comments, no empty implementations, no hardcoded empty returns flowing to rendering.

One note: `entidadeDevedoraPattern` parameter name in LeadRepository differs from the original plan's `entidadeDevedora` name. This is a deliberate bug fix (Hibernate 6 type inference issue with `LOWER(CONCAT('%', :param, '%'))`) documented in 05-03-SUMMARY.md. The service layer now pre-builds the `%pattern%` string. The fix is correct and complete — not a stub.

### Human Verification Required

#### 1. Full Test Suite Execution

**Test:** Run `./gradlew test` (requires Docker daemon for Testcontainers integration test)
**Expected:** All tests pass — 6 (LeadServiceTest) + 8 (LeadControllerTest) + 9 (GlobalExceptionHandlerTest) + 2 (StaleJobRecoveryRunnerTest) + 2 (DataJudHealthIndicatorTest) + 1 (FullStackProspeccaoIntegrationTest) = 28 phase-05 tests
**Why human:** Testcontainers requires a running Docker daemon which is not available in static analysis context

#### 2. GET /api/v1/leads Runtime Behavior

**Test:** Start the API with `docker-compose up`, call `GET /api/v1/leads`
**Expected:** HTTP 200, paginated JSON body with `content` array, `totalElements`, `pageable` — leads sorted by score DESC, zero-score leads absent by default
**Why human:** Sort order correctness and pagination metadata shape require a running database with seed data

#### 3. PATCH then GET Visibility

**Test:** PATCH /api/v1/leads/{id}/status with `{"statusContato": "CONTACTADO", "observacao": "Test note"}`, then GET /api/v1/leads
**Expected:** Updated lead appears in GET response with new statusContato and observacao values
**Why human:** Transactional commit and query refresh require live PostgreSQL

#### 4. /actuator/health DataJud Component

**Test:** Call `GET /actuator/health` against running application
**Expected:** Response body contains `dataJudHealthIndicator` component with `status: "UP"` or `"DOWN"` and `details` object
**Why human:** Spring Boot bean naming for HealthIndicator and actual actuator response require running application; `show-details: always` is configured but component naming needs runtime confirmation

### Gaps Summary

No gaps. All 10 must-have truths are verified at code level. All 17 artifacts exist, are substantive, and are wired. All 7 key links are confirmed in the actual source files. Requirements API-10, API-11, and API-12 are fully implemented with tests.

The human_needed status is due to 4 runtime behaviors that require Docker + live database to confirm. These are standard pre-deployment verification steps, not implementation deficiencies.

---

_Verified: 2026-04-06T23:30:00Z_
_Verifier: Claude (gsd-verifier)_
