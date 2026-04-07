---
phase: 05-leads-api-and-hardening
plan: "03"
subsystem: testing
tags: [testcontainers, spring-boot-actuator, application-runner, health-indicator, awaitility, mockk, springmockk, kotlin, postgresql]

# Dependency graph
requires:
  - phase: 05-leads-api-and-hardening/05-01
    provides: LeadController, LeadService, LeadRepository, Flyway V3 migration
  - phase: 05-leads-api-and-hardening/05-02
    provides: GlobalExceptionHandler with TooManyRequests and HttpMessageNotReadable handlers
provides:
  - StaleJobRecoveryRunner: ApplicationRunner that marks EM_ANDAMENTO jobs as ERRO on startup
  - DataJudHealthIndicator: custom HealthIndicator reporting DataJud UP/DOWN at /actuator/health
  - FullStackProspeccaoIntegrationTest: end-to-end BFS test with Testcontainers PostgreSQL and mock scrapers
  - application.yml management.endpoint.health.show-details: always for visible health details
affects: [future operational monitoring, deployment runbooks, health check configuration]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ApplicationRunner for startup side-effects (stale job recovery)"
    - "HealthIndicator bypassing @Cacheable by calling internal Kotlin method directly"
    - "Full-stack Testcontainers test with @MockkBean scrapers and Awaitility async polling"
    - "JPQL LIKE with pre-built %pattern% from service layer to avoid Hibernate 6 lower(bytea) type inference bug"

key-files:
  created:
    - src/main/kotlin/br/com/precatorios/startup/StaleJobRecoveryRunner.kt
    - src/main/kotlin/br/com/precatorios/health/DataJudHealthIndicator.kt
    - src/test/kotlin/br/com/precatorios/startup/StaleJobRecoveryRunnerTest.kt
    - src/test/kotlin/br/com/precatorios/health/DataJudHealthIndicatorTest.kt
    - src/test/kotlin/br/com/precatorios/integration/FullStackProspeccaoIntegrationTest.kt
  modified:
    - src/main/resources/application.yml
    - src/main/kotlin/br/com/precatorios/repository/LeadRepository.kt
    - src/main/kotlin/br/com/precatorios/service/LeadService.kt

key-decisions:
  - "DataJudHealthIndicator calls doBuscarPorNumero (internal Kotlin method) directly to bypass @Cacheable proxy — no retry or rate limiting on health checks"
  - "StaleJobRecoveryRunner uses @Transactional on run() for atomicity within single JVM — acceptable for single-instance v1 deployment"
  - "Integration test uses @MockkBean for all three scrapers (EsajScraper, CacScraper, DataJudClient) — no WireMock, no live dependency"
  - "JPQL LIKE query refactored: service layer builds %pattern% string, repository applies LOWER only to entity column to avoid Hibernate 6 type inference bug with nullable CONCAT parameters"

patterns-established:
  - "Startup recovery pattern: ApplicationRunner + ProspeccaoRepository.findByStatus(EM_ANDAMENTO, Pageable.unpaged()) + @Transactional"
  - "Health indicator pattern: inject DataJudClient + ScraperProperties, call internal method, expose base URL on UP, exception class name on DOWN"
  - "Integration test pattern: @SpringBootTest RANDOM_PORT + @Testcontainers + @MockkBean + Awaitility.await().atMost(30s)"

requirements-completed: [API-10, API-11, API-12]

# Metrics
duration: 35min
completed: 2026-04-06
---

# Phase 5 Plan 03: Integration Tests and Operational Hardening Summary

**Startup stale job recovery (ApplicationRunner), DataJud custom HealthIndicator (/actuator/health), and full-stack Testcontainers integration test with @MockkBean scrapers verifying BFS -> scored leads pipeline**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-04-06T22:40:00Z
- **Completed:** 2026-04-06T23:00:00Z
- **Tasks:** 2
- **Files modified:** 8 (5 created, 3 modified)

## Accomplishments
- StaleJobRecoveryRunner marks any EM_ANDAMENTO prospeccao jobs as ERRO on application startup, including original start timestamp in error message (D-08, D-09)
- DataJudHealthIndicator exposes DataJud connectivity at /actuator/health by calling `doBuscarPorNumero` directly (bypassing @Cacheable), reporting UP with base URL or DOWN with exception class name — no sensitive data exposed (T-05-08)
- FullStackProspeccaoIntegrationTest runs complete BFS pipeline with real PostgreSQL (Testcontainers), Flyway migrations, @MockkBean scrapers returning deterministic fixture data (FAZENDA DO ESTADO DE SAO PAULO, 250k ALIMENTAR PENDENTE precatorio), and Awaitility polling for async BFS completion — asserts scored leads in database and leads API response

## Task Commits

Each task was committed atomically:

1. **Task 1: StaleJobRecoveryRunner and DataJudHealthIndicator with unit tests** - `e0fdd25` (feat)
2. **Task 2: Full-stack Testcontainers integration test with mock scrapers** - `2350bc4` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified
- `src/main/kotlin/br/com/precatorios/startup/StaleJobRecoveryRunner.kt` - ApplicationRunner recovering stale EM_ANDAMENTO jobs to ERRO on startup
- `src/main/kotlin/br/com/precatorios/health/DataJudHealthIndicator.kt` - HealthIndicator calling doBuscarPorNumero to check DataJud connectivity
- `src/main/resources/application.yml` - Added management.endpoint.health.show-details: always
- `src/test/kotlin/br/com/precatorios/startup/StaleJobRecoveryRunnerTest.kt` - 2 unit tests (recovery + no-op)
- `src/test/kotlin/br/com/precatorios/health/DataJudHealthIndicatorTest.kt` - 2 unit tests (UP + DOWN)
- `src/test/kotlin/br/com/precatorios/integration/FullStackProspeccaoIntegrationTest.kt` - Full-stack integration test with Testcontainers
- `src/main/kotlin/br/com/precatorios/repository/LeadRepository.kt` - JPQL LIKE query refactored (Rule 1 fix)
- `src/main/kotlin/br/com/precatorios/service/LeadService.kt` - Service builds %pattern% for LIKE query (Rule 1 fix)

## Decisions Made
- DataJudHealthIndicator uses a bogus process number `"0000000-00.0000.0.00.0000"` for health probes — minimal server load, returns 0 results, not cached
- Integration test does NOT use @Transactional (async BFS thread needs to see committed data)
- Mock rawHtml values use JSON strings (`{"html":"<html>mock</html>"}`) to satisfy JSONB column constraints in the Processo and Precatorio entities
- Health details set to `show-details: always` (acceptable for internal API in v1 per threat model T-05-08)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed invalid rawHtml mock values for JSONB dadosBrutos columns**
- **Found during:** Task 2 (FullStackProspeccaoIntegrationTest)
- **Issue:** Mock `ProcessoScraped` and `PrecatorioScraped` returned `rawHtml = "<html>mock</html>"` which BFS engine persists to `dadosBrutos` (JSONB column). PostgreSQL rejected the insert with `invalid input syntax for type json`
- **Fix:** Changed rawHtml to valid JSON: `{"html":"<html>mock</html>"}` and `{"html":"<html>prec-mock</html>"}`
- **Files modified:** `src/test/kotlin/br/com/precatorios/integration/FullStackProspeccaoIntegrationTest.kt`
- **Verification:** BFS node no longer fails, prospeccao reaches CONCLUIDA
- **Committed in:** `2350bc4` (Task 2 commit)

**2. [Rule 1 - Bug] Fixed Hibernate 6 lower(bytea) type inference error in LeadRepository JPQL**
- **Found during:** Task 2 (GET /api/v1/leads assertion)
- **Issue:** `LOWER(CONCAT('%', :entidadeDevedora, '%'))` in JPQL caused Hibernate 6 to infer the parameter type as `bytea` instead of `varchar`, producing SQL error `function lower(bytea) does not exist` in PostgreSQL
- **Fix:** Refactored query to use `:entidadeDevedoraPattern` parameter (pre-built `%pattern%` string from service layer). Service builds `entidadeDevedora?.lowercase()?.let { "%$it%" }` before passing to repository. LOWER is applied only to `p.entidadeDevedora` (entity column, unambiguous varchar type)
- **Files modified:** `src/main/kotlin/br/com/precatorios/repository/LeadRepository.kt`, `src/main/kotlin/br/com/precatorios/service/LeadService.kt`
- **Verification:** GET /api/v1/leads returns HTTP 200 with leads in integration test
- **Committed in:** `2350bc4` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (2 Rule 1 bugs)
**Impact on plan:** Both fixes required for correct behavior. Rule 1 fix #1 was a mock data correctness issue; Rule 1 fix #2 was a pre-existing query bug exposed by full-stack integration testing. No scope creep.

## Issues Encountered
- Hibernate 6 type inference on JPQL `LOWER(CONCAT('%', :param, '%'))` with nullable `String?` parameters fails in PostgreSQL (`lower(bytea)` error). Fixed by moving pattern construction to service layer and parameterizing only the already-lowercased pattern string.

## Known Stubs
None — all data flows are wired. Mock scraper data produces real scored leads in the database.

## Threat Flags
None — no new network endpoints, auth paths, or schema changes introduced. DataJud health indicator exposes only public base URL and exception class name, per T-05-08 mitigation already in the threat model.

## User Setup Required
None — no external service configuration required.

## Next Phase Readiness
- Phase 5 v1 milestone complete: REST Leads API (05-01), error handling hardening (05-02), and operational hardening (05-03) all delivered
- StaleJobRecoveryRunner and DataJudHealthIndicator ready for production deployment
- Full test suite passes: 40+ unit tests + integration test covering BFS pipeline end-to-end

---
*Phase: 05-leads-api-and-hardening*
*Completed: 2026-04-06*
