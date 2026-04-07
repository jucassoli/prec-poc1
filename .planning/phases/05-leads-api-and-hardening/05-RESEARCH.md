# Phase 5: Leads API and Hardening - Research

**Researched:** 2026-04-06
**Domain:** Spring Boot REST API, Spring Data JPA (JOIN FETCH / pagination), Spring Boot HealthIndicator, Testcontainers 2.x full-stack integration testing, ApplicationRunner startup recovery
**Confidence:** HIGH

## Summary

Phase 5 completes the v1 milestone. The codebase entering this phase is mature: four prior phases established entity/repository/service/controller patterns, a Testcontainers integration test baseline (`RepositoryIntegrationTest` with `@ServiceConnection`), and a `GlobalExceptionHandler` that already covers six exception types with a structured `ErrorResponse(status, message, timestamp)`. The implementation work is therefore additive and well-constrained — no new libraries are required.

The three plans are largely independent. Plan 05-01 (Leads REST endpoints) requires a new `@Query`-annotated JPQL method on `LeadRepository` to deliver JOIN FETCH + multi-filter + sort in one query, and a thin `LeadController`/`LeadService`. Plan 05-02 (Error handling hardening) needs only minor additions to the existing `GlobalExceptionHandler` — a handler for `TooManyRequestsException` and verification that all error types produce the correct shape. Plan 05-03 (Integration tests + operational hardening) needs an `ApplicationRunner` for stale-job recovery, a `HealthIndicator` for DataJud, and one new Testcontainers `@SpringBootTest` that runs a full BFS with `@MockBean` scrapers.

**Primary recommendation:** Extend the existing patterns exactly as established — same `@RestController` / `JpaRepository` / `@WebMvcTest` style. No new dependencies. All work fits within the established package structure.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Multiple filters combine with AND logic — scoreMinimo=60 AND statusContato=NAO_CONTACTADO AND entidadeDevedora=Estado de SP
- **D-02:** Zero-score leads excluded by default; add `?incluirZero=true` query param to include them. Aligns with SCOR-04 and existing `findByScoreGreaterThan`
- **D-03:** Support sort by `score` (DESC, default) and `dataCriacao` (DESC) via Spring Data `?sort=` parameter
- **D-04:** GET /leads returns summary per lead: id, score, scoreDetalhes, statusContato, dataCriacao, credor(id, nome), precatorio(id, numero, valorAtualizado, entidadeDevedora, statusPagamento). Requires JOIN FETCH for credor and precatorio to avoid N+1
- **D-05:** Scrapers mocked with @MockBean returning fixed data — no WireMock, no live TJ-SP dependency
- **D-06:** Single-depth BFS with 2-3 mock parties, depth=1. Tests full pipeline: BFS -> persist -> score -> API
- **D-07:** No @IntegrationLive tag — live validation is manual, no automated selector drift detection
- **D-08:** Startup-only recovery via ApplicationRunner — marks stale EM_ANDAMENTO jobs as ERRO on boot
- **D-09:** Error message includes original start timestamp: "Interrompida por reinicio (iniciada em {dataInicio})"
- **D-10:** Custom Spring Boot HealthIndicator that sends a lightweight DataJud query. UP if 200, DOWN if timeout/error. Visible in /actuator/health. No caching or retry

### Claude's Discretion

- Status transition validation on PATCH /leads/{id}/status — Claude may allow any valid StatusContato enum or constrain transitions
- Exact mock fixture data for integration tests
- DataJud health check query payload

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| API-10 | `GET /api/v1/leads` returns paginated leads with filters: `scoreMinimo`, `statusContato`, `entidadeDevedora`; default sort `score DESC` | JOIN FETCH JPQL query on LeadRepository; Spring Data Pageable; existing LeadSummaryDTO is a near-fit (needs credor.id + precatorio.id added per D-04) |
| API-11 | `PATCH /api/v1/leads/{id}/status` updates lead contact status with an optional `observacao` note | New LeadController + LeadService; Lead entity currently has no `observacao` field — requires a Flyway migration adding `observacao TEXT` column |
| API-12 | All API errors return structured JSON with `status`, `message`, and `timestamp` fields (no stack traces) | GlobalExceptionHandler is substantially complete; needs TooManyRequestsException handler + verification; ErrorResponse shape already matches requirement |
</phase_requirements>

---

## Standard Stack

### Core (all already in build.gradle.kts)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot Starter Web | 3.5.3 | `@RestController`, `@RequestMapping`, `@PatchMapping` | Established in every prior controller |
| Spring Boot Starter Data JPA | 3.5.3 | `@Query` JPQL with JOIN FETCH, `JpaRepository`, `Page<T>` | All repos use this pattern |
| Spring Boot Starter Actuator | 3.5.3 | `HealthIndicator` interface, `/actuator/health` endpoint | Already enabled; `health` already in `management.endpoints.web.exposure.include` |
| Testcontainers (PostgreSQL) | 2.0.4 [VERIFIED: build.gradle.kts] | Full-stack integration test with real Postgres | `RepositoryIntegrationTest` already uses `@ServiceConnection` with `PostgreSQLContainer("postgres:17-alpine")` |
| Spring Boot Testcontainers | 3.5.3 | `@ServiceConnection` auto-wiring | Already in test dependencies |
| MockK / SpringMockK | 1.14.3 / 4.0.2 [VERIFIED: build.gradle.kts] | `@MockkBean` for scraper mocking in integration test | All controller tests use this pattern |

**No new dependencies required for this phase.**

### Alternatives Considered

| Instead of | Could Use | Why Not |
|------------|-----------|---------|
| JPQL `@Query` JOIN FETCH | Spring Data Specifications / QueryDSL | Overkill for 3 fixed filters; JPQL is simpler and consistent with existing repos |
| `ApplicationRunner` | `@EventListener(ApplicationReadyEvent)` | Both work; `ApplicationRunner` is more idiomatic for startup tasks with side effects |
| Custom `HealthIndicator` | DataJud actuator `InfoContributor` | `HealthIndicator` integrates directly into `/actuator/health` with UP/DOWN semantics — correct fit per D-10 |

---

## Architecture Patterns

### Recommended Project Structure (no new top-level packages needed)

```
src/main/kotlin/br/com/precatorios/
├── controller/
│   └── LeadController.kt          # NEW — GET /leads, PATCH /leads/{id}/status
├── service/
│   └── LeadService.kt             # NEW — query + status update logic
├── repository/
│   └── LeadRepository.kt          # EXTEND — add JOIN FETCH @Query method
├── dto/
│   └── LeadSummaryDTO.kt          # EXTEND — add credor.id + precatorio.id (D-04)
│   └── AtualizarStatusContatoRequestDTO.kt  # NEW — PATCH body
├── exception/
│   └── GlobalExceptionHandler.kt  # EXTEND — add TooManyRequestsException handler
│   └── LeadNaoEncontradoException.kt  # NEW — for 404 on PATCH
├── health/
│   └── DataJudHealthIndicator.kt  # NEW — HealthIndicator implementation
└── startup/
    └── StaleJobRecoveryRunner.kt  # NEW — ApplicationRunner
src/main/resources/db/migration/
    └── V3__add_lead_observacao.sql # NEW — adds observacao TEXT column
src/test/kotlin/br/com/precatorios/
├── controller/
│   └── LeadControllerTest.kt      # NEW — @WebMvcTest
└── integration/
    └── FullStackProspeccaoIntegrationTest.kt  # NEW — @SpringBootTest + Testcontainers
```

### Pattern 1: JOIN FETCH @Query for N+1 Avoidance

The Lead entity has LAZY relations to Credor and Precatorio. The existing `findByScoreGreaterThan(score, pageable)` will trigger N+1 loads for each lead when the controller accesses `lead.credor` or `lead.precatorio`. The solution is a single JPQL query that eagerly fetches both relations.

**Critical Testcontainers 2.0 note:** `Page<T>` + `JOIN FETCH` requires a separate count query. Spring Data's auto-generated count query breaks when a fetch join is present. The `@Query` annotation must supply an explicit `countQuery`. [VERIFIED: Spring Data JPA documentation pattern]

```kotlin
// Source: Spring Data JPA @Query with countQuery
@Query(
    value = """
        SELECT l FROM Lead l
        JOIN FETCH l.credor c
        JOIN FETCH l.precatorio p
        WHERE (:scoreMinimo IS NULL OR l.score >= :scoreMinimo)
          AND (:statusContato IS NULL OR l.statusContato = :statusContato)
          AND (:entidadeDevedora IS NULL OR LOWER(p.entidadeDevedora) LIKE LOWER(CONCAT('%', :entidadeDevedora, '%')))
        ORDER BY l.score DESC
    """,
    countQuery = """
        SELECT COUNT(l) FROM Lead l
        JOIN l.precatorio p
        WHERE (:scoreMinimo IS NULL OR l.score >= :scoreMinimo)
          AND (:statusContato IS NULL OR l.statusContato = :statusContato)
          AND (:entidadeDevedora IS NULL OR LOWER(p.entidadeDevedora) LIKE LOWER(CONCAT('%', :entidadeDevedora, '%')))
    """
)
fun findLeadsFiltered(
    @Param("scoreMinimo") scoreMinimo: Int?,
    @Param("statusContato") statusContato: StatusContato?,
    @Param("entidadeDevedora") entidadeDevedora: String?,
    pageable: Pageable
): Page<Lead>
```

**D-02 zero-score exclusion:** The `incluirZero` param is handled in `LeadService`: if `false` (default), set `scoreMinimo = max(scoreMinimo ?: 0, 1)`. This reuses the existing `findByScoreGreaterThan` logic conceptually, but the unified `findLeadsFiltered` handles it via the `scoreMinimo` param — pass `scoreMinimo = 1` when `incluirZero=false` and caller provides no `scoreMinimo`.

**D-03 sort:** Spring Data `Pageable` sort via `?sort=score,desc` or `?sort=dataCriacao,desc` is supported natively when using `Pageable` parameter in the `@Query` method — Spring Data applies the `Pageable.sort` to the query. However, JOIN FETCH queries with dynamic `ORDER BY` from Pageable can be unreliable in some Hibernate versions. The safest approach: accept a `sort` request param explicitly and map to a hardcoded JPQL ORDER BY clause in service layer, passing `Pageable.unpaged()` for count and a fixed `PageRequest.of(page, size, sort)` where sort is pre-validated.

**Alternative simpler approach for D-03:** Declare two repository methods — `findLeadsByScoreDesc` and `findLeadsByDataCriacaoDesc` — and route in the service layer. This avoids dynamic ORDER BY in JPQL. Recommended.

### Pattern 2: ApplicationRunner for Stale Job Recovery (D-08, D-09)

```kotlin
// Source: Spring Boot ApplicationRunner pattern [ASSUMED from training knowledge]
@Component
class StaleJobRecoveryRunner(
    private val prospeccaoRepository: ProspeccaoRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(StaleJobRecoveryRunner::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val stale = prospeccaoRepository.findByStatus(StatusProspeccao.EM_ANDAMENTO, Pageable.unpaged())
        stale.forEach { prospeccao ->
            val msg = "Interrompida por reinicio (iniciada em ${prospeccao.dataInicio})"
            prospeccao.status = StatusProspeccao.ERRO
            prospeccao.erroMensagem = msg
            prospeccao.dataFim = LocalDateTime.now()
            prospeccaoRepository.save(prospeccao)
            log.warn("Recovered stale prospeccao id={}: {}", prospeccao.id, msg)
        }
        if (stale.totalElements > 0) {
            log.info("Stale job recovery complete — {} jobs marked ERRO", stale.totalElements)
        }
    }
}
```

**ProspeccaoRepository note:** `findByStatus(StatusProspeccao, Pageable)` already exists. `Pageable.unpaged()` returns all results — correct for startup recovery. [VERIFIED: ProspeccaoRepository.kt in codebase]

### Pattern 3: Custom HealthIndicator (D-10)

Spring Boot Actuator's `HealthIndicator` interface is the standard extension point. `management.endpoints.web.exposure.include` already lists `health`. The existing `application.yml` needs `management.endpoint.health.show-details: always` (or `when-authorized`) to expose component-level breakdown, otherwise `/actuator/health` returns just `{"status":"UP"}` without naming the DataJud component. [VERIFIED: Spring Boot Actuator docs pattern]

```kotlin
// Source: Spring Boot HealthIndicator [ASSUMED from training knowledge - standard API]
@Component
class DataJudHealthIndicator(
    private val dataJudClient: DataJudClient,
    private val properties: ScraperProperties
) : HealthIndicator {

    override fun health(): Health {
        return try {
            // Lightweight query — match 0 documents, just tests connectivity
            dataJudClient.buscarPorNumeroProcesso("0000000-00.0000.0.00.0000")
            Health.up().withDetail("url", properties.datajud.baseUrl).build()
        } catch (e: Exception) {
            Health.down().withDetail("error", e.javaClass.simpleName).build()
        }
    }
}
```

**Important:** `DataJudClient.buscarPorNumeroProcesso` is `@Cacheable`. A zero-document query result will be cached, so the health check will use the cached response on subsequent calls. Per D-10 "no caching or retry" — the planner should note that calling the existing cached method violates D-10. The correct approach is to call `dataJudClient.doBuscarPorNumero(...)` directly (the internal non-cached method, which is `internal` visibility in Kotlin — accessible from same module) or inject `WebClient` directly into the health indicator. The `doBuscarPorNumero` internal method is the simpler path. [VERIFIED: DataJudClient.kt in codebase — method is `internal` in Kotlin, accessible within the same module]

### Pattern 4: Full-Stack Integration Test with @MockBean (D-05, D-06)

The existing `RepositoryIntegrationTest` demonstrates the correct `@SpringBootTest` + `@Testcontainers` + `@ServiceConnection` pattern. The new test extends this to exercise the full pipeline.

```kotlin
// Source: existing RepositoryIntegrationTest pattern [VERIFIED: codebase]
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FullStackProspeccaoIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }

    @MockkBean  // D-05: no live TJ-SP
    lateinit var esajScraper: EsajScraper

    @MockkBean
    lateinit var cacScraper: CacScraper

    @Autowired
    lateinit var testRestTemplate: TestRestTemplate  // or MockMvc via @AutoConfigureMockMvc

    // ... autowire ProspeccaoController, LeadRepository, etc.
}
```

**D-06 design:** Test flow — mock `esajScraper.fetchProcesso(seed)` to return a `ProcessoScraped` with 2 parties, mock `cacScraper.fetchPrecatorio(...)` to return scored precatorio data, call `POST /api/v1/prospeccao` (HTTP), poll until status=CONCLUIDA, assert `GET /api/v1/leads` returns 2 leads with score > 0 sorted DESC.

**Async challenge:** `BfsProspeccaoEngine.start()` is `@Async`. The integration test must wait for the async job to complete. Pattern: poll `GET /api/v1/prospeccao/{id}` in a loop with `Awaitility` (already available as a transitive test dependency via `spring-boot-starter-test`) until status is CONCLUIDA.

### Pattern 5: PATCH /leads/{id}/status

```kotlin
// Request DTO
data class AtualizarStatusContatoRequestDTO(
    @field:NotNull val statusContato: StatusContato,
    val observacao: String? = null
)

// Controller
@PatchMapping("/{id}/status")
fun atualizarStatus(
    @PathVariable id: Long,
    @RequestBody @Valid request: AtualizarStatusContatoRequestDTO
): ResponseEntity<LeadSummaryDTO>
```

**observacao field:** The Lead entity currently has no `observacao` field. A Flyway migration `V3__add_lead_observacao.sql` adding `ALTER TABLE leads ADD COLUMN observacao TEXT` is required. The `Lead.kt` entity must also gain a `var observacao: String? = null` field. [VERIFIED: Lead.kt in codebase — field is absent]

### Anti-Patterns to Avoid

- **N+1 via LAZY loading:** Never call `lead.credor` or `lead.precatorio` inside a loop without JOIN FETCH. The existing `findByProspeccaoId` in `ProspeccaoController` already does this — it is acceptable there (leads per prospeccao is bounded by `maxCredores`). For the open-ended `GET /leads` endpoint, JOIN FETCH is mandatory.
- **Missing countQuery on JOIN FETCH Page query:** Spring Data cannot derive the count query when a FETCH join is present. Omitting `countQuery` causes `HibernateQueryException` at runtime.
- **Calling @Cacheable method in health check:** Will silently return cached result rather than testing live connectivity. Use `doBuscarPorNumero` directly.
- **ApplicationRunner without @Transactional:** The stale job loop must be `@Transactional`. If run outside a transaction, each `save()` works but the scope is wider than necessary. Annotating the `run()` method is the cleanest approach.
- **Blocking async in integration test without Awaitility:** Using `Thread.sleep` is fragile. `Awaitility.await().atMost(30, TimeUnit.SECONDS).until { ... }` is the standard.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Pagination + filtering | Custom page slice logic | Spring Data `Page<T>` + `@Query` with `Pageable` | Spring handles page/sort/count automatically |
| Health check integration | Custom endpoint | `HealthIndicator` interface + Actuator | Auto-integrated into `/actuator/health` aggregation |
| Startup hook | `@PostConstruct` on a service | `ApplicationRunner` | Runs after full context + datasource are initialized; `@PostConstruct` may run before connection pool is ready |
| Enum deserialization in PATCH | Manual string-to-enum conversion | Jackson + `@Enumerated(EnumType.STRING)` already configured | Jackson handles case-sensitive enum name deserialization by default |
| Async completion detection | `Thread.sleep` | Awaitility (transitive dep from `spring-boot-starter-test`) | Deterministic wait without flakiness |

---

## Common Pitfalls

### Pitfall 1: JOIN FETCH + Pagination = HibernateException in count query

**What goes wrong:** Hibernate logs `HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory!` or throws `HibernateQueryException: query specified join fetching, but the owner of the fetched association was not present in the select list`.
**Why it happens:** Spring Data auto-generates a count query from the value query. When `JOIN FETCH` is present, the count query is invalid JPQL.
**How to avoid:** Always supply explicit `countQuery` in `@Query` annotations that use `JOIN FETCH`. The count query must use regular `JOIN` (not `JOIN FETCH`) and `COUNT(l)`.
**Warning signs:** `HHH90003004` in logs; or `org.hibernate.query.QueryTypeMismatchException` at runtime.

### Pitfall 2: Stale @Cacheable Result in Health Indicator

**What goes wrong:** The DataJud health check always returns UP even when the service is down.
**Why it happens:** `DataJudClient.buscarPorNumeroProcesso` is annotated `@Cacheable(cacheNames = [CacheNames.DATAJUD])`. A successful response is cached for 24h (Caffeine TTL). Subsequent health checks hit the cache.
**How to avoid:** Use `dataJudClient.doBuscarPorNumero(testNumero)` directly in the health indicator — this is the internal method that bypasses the cache proxy. It is `internal` visibility in Kotlin, so it is accessible from the same module.
**Warning signs:** Health remains UP after killing network connectivity to DataJud.

### Pitfall 3: Async BFS not Complete Before Integration Test Assertions

**What goes wrong:** Integration test asserts `GET /leads` returns 2 leads but finds 0, because the BFS async job hasn't finished yet.
**Why it happens:** `BfsProspeccaoEngine.start()` runs on `prospeccaoExecutor` thread pool. `POST /prospeccao` returns HTTP 202 immediately.
**How to avoid:** Use `Awaitility.await().atMost(Duration.ofSeconds(30)).until { prospeccaoStatus == "CONCLUIDA" }` before asserting leads.
**Warning signs:** Flaky test that passes occasionally (when machine is fast) and fails otherwise.

### Pitfall 4: @MockBean Replaces the Entire Bean — Breaks @Cacheable

**What goes wrong:** `@MockkBean esajScraper` and `@MockkBean cacScraper` in `@SpringBootTest` work correctly. But if the test also tries to `@MockkBean DataJudClient`, the health indicator may fail to autowire because it is also mocked.
**Why it happens:** Not a problem here since DataJudClient is NOT mocked in the integration test (D-05 only mocks e-SAJ and CAC scrapers). Mentioning to pre-empt the instinct to mock it.
**How to avoid:** Do not `@MockkBean DataJudClient` in the full-stack test — let the health indicator call through (it will return DOWN since the test has no real DataJud connectivity, which is acceptable).

### Pitfall 5: LeadSummaryDTO Shape Mismatch with D-04

**What goes wrong:** The existing `LeadSummaryDTO` has flat fields (`credorNome`, `credorCpfCnpj`, etc.) but D-04 requires nested objects: `credor(id, nome)` and `precatorio(id, numero, valorAtualizado, entidadeDevedora, statusPagamento)`.
**Why it happens:** `LeadSummaryDTO` was designed for the `ProspeccaoController` (Phase 4) which has a different response contract.
**How to avoid:** Either (a) extend `LeadSummaryDTO` with `credorId`/`precatorioId` fields, or (b) create a new `LeadResponseDTO` for `LeadController` that nests `CredorSummaryDTO` and `PrecatorioSummaryDTO`. Option (b) is cleaner but requires updating OpenAPI docs. Option (a) is backward compatible with ProspeccaoController. Decision left to Claude's discretion.
**Warning signs:** API-10 acceptance test fails because `credor.id` field is missing from response.

---

## Code Examples

### LeadRepository — Filtered JOIN FETCH Query

```kotlin
// Source: Spring Data JPA @Query pattern [VERIFIED: existing repo patterns in codebase]
interface LeadRepository : JpaRepository<Lead, Long> {

    fun findByProspeccaoId(prospeccaoId: Long): List<Lead>

    fun findByScoreGreaterThan(score: Int, pageable: Pageable): Page<Lead>

    @Query(
        value = """
            SELECT l FROM Lead l
            JOIN FETCH l.credor c
            JOIN FETCH l.precatorio p
            WHERE (:scoreMinimo IS NULL OR l.score >= :scoreMinimo)
              AND (:statusContato IS NULL OR l.statusContato = :statusContato)
              AND (:entidadeDevedora IS NULL OR LOWER(p.entidadeDevedora) LIKE LOWER(CONCAT('%', :entidadeDevedora, '%')))
        """,
        countQuery = """
            SELECT COUNT(l) FROM Lead l
            JOIN l.precatorio p
            WHERE (:scoreMinimo IS NULL OR l.score >= :scoreMinimo)
              AND (:statusContato IS NULL OR l.statusContato = :statusContato)
              AND (:entidadeDevedora IS NULL OR LOWER(p.entidadeDevedora) LIKE LOWER(CONCAT('%', :entidadeDevedora, '%')))
        """
    )
    fun findLeadsFiltered(
        @Param("scoreMinimo") scoreMinimo: Int?,
        @Param("statusContato") statusContato: StatusContato?,
        @Param("entidadeDevedora") entidadeDevedora: String?,
        pageable: Pageable
    ): Page<Lead>
}
```

### LeadController — GET and PATCH

```kotlin
// Source: existing ProspeccaoController pattern [VERIFIED: codebase]
@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "Leads", description = "Lead management endpoints")
class LeadController(private val leadService: LeadService) {

    @GetMapping
    fun listar(
        @RequestParam(required = false) scoreMinimo: Int?,
        @RequestParam(required = false) statusContato: StatusContato?,
        @RequestParam(required = false) entidadeDevedora: String?,
        @RequestParam(defaultValue = "false") incluirZero: Boolean,
        pageable: Pageable
    ): ResponseEntity<Page<LeadResponseDTO>>

    @PatchMapping("/{id}/status")
    fun atualizarStatus(
        @PathVariable id: Long,
        @RequestBody @Valid request: AtualizarStatusContatoRequestDTO
    ): ResponseEntity<LeadResponseDTO>
}
```

### GlobalExceptionHandler — Missing Handler

```kotlin
// Add to existing GlobalExceptionHandler [VERIFIED: TooManyRequestsException.kt exists; handler missing]
@ExceptionHandler(TooManyRequestsException::class)
fun handleTooManyRequests(
    ex: TooManyRequestsException,
    request: HttpServletRequest
): ResponseEntity<ErrorResponse> {
    return ResponseEntity
        .status(HttpStatus.TOO_MANY_REQUESTS)
        .body(ErrorResponse(429, ex.message ?: "Muitas requisicoes — tente novamente em instantes"))
}
```

### Flyway Migration V3

```sql
-- V3__add_lead_observacao.sql
ALTER TABLE leads ADD COLUMN observacao TEXT;
```

### management.yml Addition for Health Details

```yaml
# Add to application.yml under management:
management:
  endpoint:
    health:
      show-details: always
  endpoints:
    web:
      exposure:
        include: health,caches
```

---

## Runtime State Inventory

Step 2.5: SKIPPED — this is a greenfield feature addition phase, not a rename/refactor/migration phase. No runtime state inventory required.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|---------|
| JDK 21 | Build and run | Yes | OpenJDK 21.0.10 (Zulu) [VERIFIED: java -version] | — |
| Docker | Testcontainers integration test | Yes | Docker 29.3.1 [VERIFIED: docker --version] | — |
| PostgreSQL (via Testcontainers) | Integration test | Yes — pulled by Testcontainers | postgres:17-alpine (same image as RepositoryIntegrationTest) [VERIFIED: RepositoryIntegrationTest.kt] | — |
| Gradle wrapper | Build | Yes | Wrapper present [VERIFIED: build.gradle.kts] | — |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** None.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (via `spring-boot-starter-test` 3.5.3) + MockK 1.14.3 |
| Config file | None — JUnit Platform auto-discovered; `useJUnitPlatform()` in build.gradle.kts |
| Quick run command | `./gradlew test --tests "br.com.precatorios.controller.LeadControllerTest"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| API-10 | GET /leads returns paginated, filtered, score-DESC leads with no N+1 | unit (@WebMvcTest) | `./gradlew test --tests "br.com.precatorios.controller.LeadControllerTest"` | No — Wave 0 |
| API-10 | Zero-score exclusion (incluirZero=false default) | unit (@WebMvcTest) | same | No — Wave 0 |
| API-11 | PATCH /leads/{id}/status updates statusContato + observacao | unit (@WebMvcTest) | `./gradlew test --tests "br.com.precatorios.controller.LeadControllerTest"` | No — Wave 0 |
| API-11 | PATCH on non-existent ID returns 404 | unit (@WebMvcTest) | same | No — Wave 0 |
| API-12 | TooManyRequestsException returns 429 JSON | unit (@WebMvcTest) | same | No — Wave 0 |
| API-12 | Generic Exception returns 500 JSON with no stack trace | unit (GlobalExceptionHandlerTest or controller test) | `./gradlew test --tests "br.com.precatorios.controller.*"` | Partial — handler exists |
| D-05/D-06 | Full BFS pipeline test with mock scrapers | integration (@SpringBootTest + Testcontainers) | `./gradlew test --tests "br.com.precatorios.integration.FullStackProspeccaoIntegrationTest"` | No — Wave 0 |
| D-08/D-09 | Stale EM_ANDAMENTO jobs marked ERRO on startup | integration (or unit with mocked repo) | `./gradlew test --tests "br.com.precatorios.startup.StaleJobRecoveryRunnerTest"` | No — Wave 0 |
| D-10 | DataJud health indicator UP/DOWN | unit (with mocked DataJudClient) | `./gradlew test --tests "br.com.precatorios.health.DataJudHealthIndicatorTest"` | No — Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "br.com.precatorios.controller.LeadControllerTest"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- `src/test/kotlin/br/com/precatorios/controller/LeadControllerTest.kt` — covers API-10, API-11, API-12
- `src/test/kotlin/br/com/precatorios/integration/FullStackProspeccaoIntegrationTest.kt` — covers D-05, D-06
- `src/test/kotlin/br/com/precatorios/startup/StaleJobRecoveryRunnerTest.kt` — covers D-08, D-09
- `src/test/kotlin/br/com/precatorios/health/DataJudHealthIndicatorTest.kt` — covers D-10
- `src/main/resources/db/migration/V3__add_lead_observacao.sql` — required for Lead.observacao field (API-11)

---

## Security Domain

Security enforcement is not explicitly disabled. Applying ASVS check.

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | No auth in v1 (out of scope per REQUIREMENTS.md Out of Scope table) |
| V3 Session Management | No | Stateless REST API |
| V4 Access Control | No | Internal tool, network controls only (v1) |
| V5 Input Validation | Yes | `@Valid` + `@NotNull` on request DTOs; `StatusContato` enum deserialization bounds input |
| V6 Cryptography | No | No new crypto in this phase |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Invalid enum value in PATCH body | Tampering | Jackson throws `HttpMessageNotReadableException` → `GlobalExceptionHandler` should catch and return 400 (add handler if missing) |
| SQL injection via entidadeDevedora filter | Tampering | JPQL parameterized query with `@Param` — Hibernate prevents injection; never concatenate into query string |
| Stack trace exposure in 500 responses | Information Disclosure | `GlobalExceptionHandler.handleGeneric` already returns "Internal server error" without exception detail [VERIFIED: GlobalExceptionHandler.kt] |

**Note on `HttpMessageNotReadableException`:** If a caller sends `"statusContato": "INVALID_VALUE"` in the PATCH body, Jackson throws `HttpMessageNotReadableException`. The existing `GlobalExceptionHandler` does not handle this type explicitly — it falls through to `handleGeneric` (500). The planner should add a handler that returns 400 for this exception, since invalid enum values are client errors, not server errors.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `ApplicationRunner` runs after the datasource/JPA context is fully initialized | Architecture Patterns (stale job recovery) | If wrong, `ProspeccaoRepository` call in `run()` fails with no datasource; mitigate by wrapping in try/catch with log |
| A2 | `DataJudClient.doBuscarPorNumero` being `internal` in Kotlin is accessible from the `health/` package in the same Gradle module | Code Examples (health indicator) | If wrong, health indicator must inject WebClient directly — slightly more code but same behavior |
| A3 | Awaitility is available as a transitive test dependency via `spring-boot-starter-test` | Common Pitfalls / Integration test | If wrong, add `testImplementation("org.awaitility:awaitility:4.x")` — minor build change |
| A4 | Hibernate 6 (via Spring Boot 3.5.3) handles `IS NULL OR l.score >= :scoreMinimo` JPQL correctly for optional filter params | Standard Stack / JPQL pattern | If wrong, use two separate query methods or a Specification — moderate rework |

---

## Open Questions

1. **LeadSummaryDTO shape: extend vs. new DTO**
   - What we know: Existing `LeadSummaryDTO` is used by `ProspeccaoController` (returns it inside `ProspeccaoStatusDTO`). D-04 requires nested `credor(id, nome)` and `precatorio(id, numero, ...)` objects.
   - What's unclear: Should `LeadController` reuse the same DTO (adding `credorId`/`precatorioId` to the flat structure) or define a new nested `LeadResponseDTO`?
   - Recommendation: Create a new `LeadResponseDTO` with nested `CredorSummaryDTO`/`PrecatorioSummaryDTO` for `LeadController`. Leave `LeadSummaryDTO` unchanged for backward compatibility with `ProspeccaoController`. This is cleaner API design and avoids breaking existing test contracts.

2. **StatusContato transition validation (Claude's Discretion)**
   - What we know: 5 statuses exist: `NAO_CONTACTADO, CONTACTADO, INTERESSADO, CONTRATADO, DESCARTADO`.
   - What's unclear: Whether any transitions should be forbidden (e.g., `CONTRATADO` → `NAO_CONTACTADO`).
   - Recommendation: Allow any valid enum transition in v1. Transition constraints add complexity without a stated business rule. The PATCH endpoint should accept any `StatusContato` value.

3. **DataJud health check query payload (Claude's Discretion)**
   - What we know: The health check must send a lightweight query to verify DataJud connectivity.
   - Recommendation: Use a match-none query `{"query":{"match_none":{}}, "size":0}` — returns 200 with 0 hits, no data fetched, minimal server load. This is more explicit than using a bogus process number.

---

## Sources

### Primary (HIGH confidence)

- `build.gradle.kts` [VERIFIED] — all dependency versions confirmed from source
- `src/main/kotlin/...` all referenced files [VERIFIED] — entity shapes, repository methods, exception handler, patterns all read from actual codebase
- `RepositoryIntegrationTest.kt` [VERIFIED] — Testcontainers 2.0 `@ServiceConnection` pattern confirmed in working test

### Secondary (MEDIUM confidence)

- Spring Data JPA documentation pattern for `@Query` + `countQuery` on JOIN FETCH queries — standard known behavior, consistent with Hibernate 6 / Spring Boot 3.x

### Tertiary (LOW confidence / ASSUMED)

- A1-A4 in Assumptions Log above

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all dependencies verified from build.gradle.kts; no new deps required
- Architecture: HIGH — all patterns derived from existing codebase code; well-established Spring patterns
- Pitfalls: HIGH — JOIN FETCH + countQuery, cache bypass in health check, and async test timing are well-known Spring Data JPA / Spring Boot issues
- Integration test design: HIGH — existing RepositoryIntegrationTest is the direct template

**Research date:** 2026-04-06
**Valid until:** 2026-05-06 (stable Spring Boot 3.5.x ecosystem)
