# Project Research Summary

**Project:** Precatórios API — TJ-SP Lead Prospecting
**Domain:** Court portal scraping + recursive graph traversal + lead qualification REST API
**Researched:** 2026-04-03
**Confidence:** HIGH (stack, architecture, pitfalls patterns); MEDIUM (live CAC/SCP portal behavior, e-SAJ selector accuracy)

## Executive Summary

This is a single-team internal tool: a Kotlin/Spring Boot REST API that accepts a seed court case number, recursively discovers co-creditors in TJ-SP public portals, fuses data from three sources (e-SAJ HTML, CAC/SCP ASP.NET forms, DataJud REST API), scores each creditor as a lead, and returns a ranked list ready for FUNPREC advisors to action. The core differentiator over commercial alternatives is recursive co-creditor discovery from a single seed — exponential lead surface from a minimal input. The recommended approach is a layered monolith (no microservices), async prospection via a dedicated `@Async` thread pool with BFS/queue traversal, Caffeine in-memory cache, and Spring Data JPA with PostgreSQL. This is a well-understood Spring Boot application pattern complicated by two high-risk external factors: scraped portals with no API contract and mandatory rate-limiting that forces slow, sequential execution.

The biggest risks are at the scraper boundary, not in the application code. The e-SAJ CSS selectors specified in the SPEC are unvalidated hypotheses that could silently return zero results after any TJ-SP portal update. The CAC/SCP ASP.NET portal requires careful ViewState session management and will silently reset mid-prospection if the server-side session expires during a long run. These two risks must be addressed in Phase 2 with smoke tests against live TJ-SP infrastructure before any business logic is built on top. All other risks (BFS fanout, async proxy issues, rate limit coordination) are standard patterns with well-documented mitigations that must be designed in from the start — not retrofitted.

The SPEC versions for Spring Boot (3.3.0) and Kotlin (2.0.0) are outdated — both are end-of-life or superseded. The updated dependency set (Spring Boot 3.5.9, Kotlin 2.3.20, Jsoup 1.22.1, MockK 1.14.5, Testcontainers 2.0.4) is the correct baseline and must be reflected in `build.gradle.kts` from project initialization. Testcontainers 2.0 has breaking coordinate and package name changes vs 1.x — these are documented in STACK.md and must be followed exactly.

---

## Key Findings

### Recommended Stack

The SPEC's technology choices are sound but versions require updates. Spring Boot 3.3.x is end-of-life; use 3.5.9. Kotlin 2.0.0 is superseded; Spring Boot 3.5 BOM bundles Kotlin 2.3.x and 2.3.20 is current stable. Jsoup 1.22.1 (Jan 2026) adds HTTP/2 and improved session management critical for the CAC/SCP ViewState flow. The testing stack requires careful attention: Testcontainers underwent a major version bump (1.x → 2.0.4) with breaking coordinate and package name changes; MockK 1.14.5 includes Kotlin 2.x fixes that the SPEC's 1.13.10 lacks.

The async execution strategy has a deliberate decision: use `@Async` with a named `ThreadPoolTaskExecutor` (not Kotlin coroutines) for the prospection BFS job. The rationale is that the prospection job is one long sequential rate-limited task — 2s delay between requests means the bottleneck is clock time, not thread count. `@Async` with a dedicated `prospeccaoExecutor` bean gives explicit control and avoids coroutine/JPA dispatcher mixing issues. Kotlin coroutines remain appropriate for WebClient HTTP calls (DataJud), but the internal BFS loop is better served by a straightforward platform thread.

**Core technologies:**
- **Spring Boot 3.5.9** — framework; current stable, includes JUnit 5.12, Hibernate 6.6.x, virtual thread support
- **Kotlin 2.3.20** — primary language; minimum required by Spring Boot 3.5 BOM
- **Jsoup 1.22.1** — e-SAJ + CAC/SCP scraping; HTTP/2, session cookie jar, no headless browser needed for static HTML portals
- **HtmlUnit 4.21.0** — CAC/SCP fallback ONLY if Jsoup ViewState approach fails; use `org.htmlunit:htmlunit` (coordinates changed from `net.sourceforge.htmlunit`)
- **PostgreSQL 17-alpine** — database; improved JSONB performance relevant to `dados_brutos` columns
- **Flyway 11.x** (via BOM) — migrations; requires BOTH `flyway-core` AND `flyway-database-postgresql` since Flyway 10+
- **Caffeine** (via BOM) — in-memory cache; 24h TTL for process/precatório data; two named caches
- **Resilience4j** — retry with exponential backoff for scraper calls; cleaner than manual loops or Spring Retry
- **MockK 1.14.5** — Kotlin-first mocking with `coEvery`/`coVerify` for suspend functions; use instead of Mockito
- **Testcontainers 2.0.4** — integration test DB; BREAKING vs 1.x: new coordinates `testcontainers-postgresql`, new package `org.testcontainers.postgresql.PostgreSQLContainer`
- **SpringDoc OpenAPI 2.8.16** — Swagger UI; 2.x targets Spring Boot 3.x (SpringDoc 3.x targets Spring Boot 4.x — do NOT use 3.x)

**Full dependency block:** See `.planning/research/STACK.md` for the complete updated `build.gradle.kts`.

### Expected Features

**Must have (table stakes):**
- Async prospection endpoint returning 202 Accepted + `prospeccaoId` — a synchronous endpoint will always timeout; non-negotiable
- Machine-readable status enum (`EM_ANDAMENTO`, `CONCLUIDA`, `ERRO`) with progress counters (`processosVisitados`, `credoresEncontrados`) — callers cannot distinguish "working" from "stuck" without counters
- Seed process CNJ format validation before async dispatch — fail fast with 400/422 on malformed number, not a silent ERRO after dispatch
- Scored lead list (0–100) with `scoreDetalhes` breakdown — the entire value proposition; black-box scores reduce advisor trust and adoption
- Configurable scoring weights in `application.yml` — FUNPREC's business rules will change without needing a code deploy
- Filterable lead list with pagination — `scoreMinimo`, `statusContato`, `entidadeDevedora`, `prospeccaoId` filters minimum; unbounded responses unacceptable
- Lead contact status tracking (`PATCH /leads/{id}/status`) — without this, advisors use spreadsheets alongside the tool
- In-memory scraping cache (24h TTL) — repeat requests without cache risk IP blocks; this is an operational safety requirement
- Swagger UI at `/swagger-ui.html` — no frontend exists; self-documentation is essential for an internal tool
- Docker Compose deployment (API + PostgreSQL) with proper health check — single-command startup is required for a team tool
- `Retry-After: 10` header on 202 and EM_ANDAMENTO responses — prevents aggressive polling (not in SPEC, add it)
- Link to leads in completed status response (`/api/v1/leads?prospeccaoId=X`) — reduces client-side coupling (not in SPEC, add it)

**Should have (differentiators):**
- Recursive co-creditor discovery (BFS from seed) — the core differentiator; JUDIT Miner requires direct creditor identifiers
- Multi-source data fusion (e-SAJ + CAC/SCP + DataJud per lead) — each source alone gives incomplete data; fusion is the value-add
- `dados_brutos JSONB` column preservation — enables retroactive re-scoring when rules change without re-scraping
- Configurable `profundidadeMaxima` — lets FUNPREC control scope vs runtime per use case

**Defer (v2+):**
- CSV/XLS export endpoints — manual `curl | jq` covers v1 needs
- Webhook notification on prospection completion — polling at 10–15s is sufficient for this latency class
- Scheduled/recurring prospections — needs usage data before adding a job scheduler
- Job cancellation endpoint — graceful async interrupt is complex; not warranted for a POC
- Multi-court support (TRT, STJ, federal) — TJ-SP must be proven stable first; scrapers are behind interfaces for future extension
- Authentication/OAuth — internal tool behind network controls; API key env-var check is sufficient if needed

### Architecture Approach

The recommended architecture is a layered monolith with five explicit layers: Controller → Service → Scraper + Repository → Cache → Database. The key boundary rules are: controllers never hold JPA entities (DTOs only at the HTTP boundary); scrapers never write to repositories (they return domain objects, services persist); `ScoringService` is a pure function with no I/O (fully unit-testable without Spring context). The async prospection job uses a dedicated `BfsProspeccaoEngine` bean with `@Async("prospeccaoExecutor")` — placing `@Async` in a separate bean (not `ProspeccaoService` itself) is mandatory to avoid the Spring AOP self-invocation trap. Each `persistir*` call inside the BFS loop uses `@Transactional(REQUIRES_NEW)` — never a single transaction wrapping the full run — to avoid holding a DB connection for the 2–5 minute BFS duration.

**Major components:**
1. `ProspeccaoController` — accepts POST, returns 202 + job ID, serves status polling endpoint
2. `BfsProspeccaoEngine` — dispatched via `@Async`; owns BFS queue, local visited set, progress updates, lead persistence
3. `EsajScraper` — Jsoup HTML scraper; rate-limited; centralized CSS selector constants; mandatory empty-result assertions
4. `CacScraper` — Jsoup session with ViewState extraction; GET/POST cycle; session renewal logic for expiry during long runs
5. `DataJudClient` — WebClient-based; Elasticsearch DSL POST; handles pagination and 401/403 alerts on key rotation
6. `ScoringService` — pure stateless function; reads weights from injected config; no DB access; fully unit-testable
7. `LeadService` — list/filter with JOIN FETCH to avoid N+1; contact status updates
8. `GlobalExceptionHandler` — structured error responses; `ScrapingException` surfaces source name + URL + retry count

**Full component diagram and data flow:** See `.planning/research/ARCHITECTURE.md`.

### Critical Pitfalls

1. **e-SAJ CSS selectors silently return empty results after portal updates** — Jsoup `select()` never throws on a missing selector; a changed TJ-SP HTML structure produces a successful-looking run with zero leads and no error in logs. Prevention: centralize all selectors in `EsajSelectors` constants; assert `parties.isNotEmpty()` after each parse and throw `ScrapingException` if empty; do NOT cache empty results; smoke test against a known stable process number in CI.

2. **CAC/SCP ViewState session silently expires mid-prospection** — ASP.NET server-side sessions expire (~20 min by default); a POST after expiry returns the blank form (HTTP 200, no error), silently yielding zero precatório results. Prevention: validate every CAC/SCP POST response for expected result elements; implement automatic session re-establishment (re-run the initial GET) when blank-form response is detected; proactively refresh after 15 minutes of a long run.

3. **BFS fanout explosion from creditor name searches** — common names ("MARIA DA SILVA") return hundreds of processes; 50 creditors × 200 results = 10,000 queue entries at depth=2, leading to OOM or multi-hour runs. Prevention: cap name-search results at N most recent per creditor (e.g., 5); enforce hard global queue size limit per prospection (e.g., 200 entries); prefer CPF-based search when available; add absolute time limit per run.

4. **`@Async` self-invocation bypasses Spring AOP proxy — prospection runs synchronously** — calling an `@Async` method from within the same Spring bean silently executes it on the HTTP thread, blocking the caller for the full BFS duration. Prevention: place `@Async` in `BfsProspeccaoEngine` (a separate bean), not in `ProspeccaoService`; add a test asserting `POST /api/v1/prospeccao` returns 202 within 500ms with slow scraper mocks.

5. **Global vs per-thread rate limiting** — the SPEC's per-request 2s sleep with 4 concurrent threads = 2 req/sec aggregate to TJ-SP from one IP, which can trigger anti-bot detection regardless of per-thread compliance. Prevention: use a single shared `RateLimiter` (Guava) across all scraping threads; expose as `scraper.global-rate-limit-rps: 0.5`; add ±500ms jitter to avoid detectable fixed intervals.

---

## Implications for Roadmap

Based on combined research, the phase structure below is strongly recommended. The ordering is driven by two constraints: (1) scrapers are the highest-risk unknowns and must be validated against live infrastructure before building on top, and (2) several critical pitfalls (async proxy separation, transaction boundary strategy, global rate limiter design) must be resolved at infrastructure setup time — retrofitting them is expensive across all phases.

### Phase 1: Foundation + Infrastructure

**Rationale:** Every subsequent phase depends on a working DB schema, Docker Compose, and async infrastructure decisions locked in before any service code is written. The @Async proxy pitfall, blocking JPA on wrong dispatcher, and global rate limiter design all must be decided here. Flyway checksum discipline must be established on day one.

**Delivers:** Running Spring Boot skeleton, all Flyway migrations (V1__create_tables.sql), all JPA entities and repositories, Docker Compose with `service_healthy` + `pg_isready` healthcheck, `AsyncConfig` with dedicated `prospeccaoExecutor` bean, `CacheConfig` with named Caffeine caches, `GlobalExceptionHandler` skeleton.

**Addresses:**
- Docker Compose race condition (Pitfall 7): use SPEC's `service_healthy` healthcheck — do not simplify it
- @Async self-invocation (Pitfall 4): create `BfsProspeccaoEngine` as a separate bean now
- Transaction boundary strategy (Pitfall 5): decide `REQUIRES_NEW` per-persist pattern before writing any service code
- Flyway checksum discipline (Pitfall 6): establish immutable migration rule on day one; document in README

**Research flag:** Standard patterns — no additional research needed for Spring Boot scaffolding with Flyway and Docker Compose.

### Phase 2: Scraper Layer (Highest Risk — Validate Before Building On Top)

**Rationale:** Scrapers are external dependencies with no API contract. All selectors and session management patterns must be validated against live TJ-SP infrastructure before business logic is built on top. A scraper that silently returns empty results produces a system that appears to work but generates no leads — the worst failure mode. This phase must produce smoke tests with real requests before Phase 3 begins. This is the phase most likely to surface surprises.

**Delivers:** `EsajScraper` with centralized selector constants (`EsajSelectors` object) and mandatory `isNotEmpty()` assertions; `CacScraper` with ViewState session cycle and renewal logic on expiry detection; `DataJudClient` with WebClient; Caffeine cache wiring with `unless = "#result == null"` on all `@Cacheable` annotations; Resilience4j retry config (3 attempts, exponential backoff, 1s base); `ProcessoNumberNormalizer` utility (canonical CNJ format); global shared `RateLimiter` with jitter; smoke tests against live TJ-SP portals.

**Addresses:**
- CSS selector silent failure (Pitfall 1): assertions + centralized selectors + smoke test
- CAC/SCP session expiry (Pitfall 2): session renewal logic + POST response validation
- BFS queue fanout — name search cap (Pitfall 3, partial): cap search results at scraper level
- CNJ format inconsistency (Pitfall 8): normalizer utility before any BFS code
- Global rate limit (Pitfall 9): shared RateLimiter instead of per-thread sleep
- Negative caching of failures (Pitfall 10): `unless = "#result == null"` from first annotation
- DataJud key rotation (Pitfall 12, partial): WARN-level 401/403 alerts

**Research flag:** Needs live validation — must fire real requests against e-SAJ and CAC/SCP against a known stable TJ-SP process number before declaring Phase 2 complete.

### Phase 3: BFS Engine + Scoring

**Rationale:** With scrapers validated and repositories ready, the BFS engine and scoring logic can be built with confidence. BFS fanout caps (queue size limit, creditor name-search result limit) must be designed in before writing the recursion — not added after. `ScoringService` is a pure function and should be fully unit-tested in isolation before wiring into the BFS loop.

**Delivers:** `ScoringService` with configurable weights from `application.yml` (pure, fully unit-tested); `ProspeccaoService.iniciar()` dispatching to `BfsProspeccaoEngine`; `BfsProspeccaoEngine.executarAsync()` with bounded BFS queue (global max queue size + per-creditor name-search cap), visited set local per job, `maxCredores` early-exit guard, progress counter updates, and CONCLUIDA/ERRO terminal state transitions.

**Addresses:**
- BFS queue fanout explosion (Pitfall 3): hard queue size cap + creditor search result cap built in from the start
- @Async self-invocation (Pitfall 4): validated at Phase 1; `BfsProspeccaoEngine` is a separate bean
- Partial-result safety: each lead persisted with `REQUIRES_NEW` so partial data survives JVM crash

**Research flag:** Standard patterns — BFS with a bounded queue is a textbook algorithm; no additional research needed.

### Phase 4: REST API Layer

**Rationale:** Controllers, DTOs, and mappers can only be finalized once services are stable. This phase wires the 202/polling async contract, adds the lead filtering endpoint with JOIN FETCH queries to avoid N+1, and adds the seed validation logic missing from the SPEC.

**Delivers:** All REST controllers (`ProspeccaoController`, `LeadController`, `ProcessoController`, `PrecatorioController`, `DataJudController`); DTOs with mapper functions; `GET /api/v1/leads` with JOIN FETCH projection (avoids N+1 across leads → credores → precatórios); `PATCH /leads/{id}/status`; SpringDoc Swagger UI at `/swagger-ui.html`; `Retry-After: 10` header on 202 and EM_ANDAMENTO responses; seed process CNJ format validation (fail-fast 400/422 before async dispatch); link to leads in completed status response.

**Addresses:**
- N+1 query on lead listing: JOIN FETCH projection query
- Seed validation UX: 400/422 fail-fast before async dispatch (not in SPEC — add it)
- Polling API contract: `Retry-After` header, 202 only on initial dispatch, 200 on all polls

**Research flag:** Standard patterns — Spring MVC controllers, SpringDoc, Spring Data `Page` are well-documented.

### Phase 5: Hardening + Integration Tests

**Rationale:** Integration tests with Testcontainers 2.0 validate the full stack under a real database. Startup recovery for stale `EM_ANDAMENTO` jobs (leftover from crashed prospection runs) is an operational requirement that only makes sense after the full flow works end-to-end. Creditor deduplication by CPF (Pitfall 13) is the last data quality concern to address.

**Delivers:** Integration tests with Testcontainers 2.0 and `@ServiceConnection`; DataJud health indicator at application startup; startup recovery job (reset stale `EM_ANDAMENTO` to `ERRO` on boot); CSS selector smoke test suite (tagged `@IntegrationLive`, requires network access); CPF-based creditor deduplication pass in `LeadService`; Docker image optimization.

**Addresses:**
- DataJud key rotation silent failure (Pitfall 12): startup health check + WARN-level alert
- Stale job recovery: startup scan for orphaned `EM_ANDAMENTO` records
- Creditor deduplication quality (Pitfall 13): cross-run CPF deduplication when CPF is available

**Research flag:** Testcontainers 2.0 breaking changes are documented in STACK.md — follow coordinate and import changes exactly.

---

### Phase Ordering Rationale

- Phase 1 before Phase 2: async infrastructure decisions (executor bean separation, transaction boundary strategy, rate limiter) must be locked in before any scraping code is written — these structural choices affect every scraper method signature and every service call.
- Phase 2 before Phase 3: BFS logic is meaningless without validated scrapers. Building recursion on top of unvalidated selectors guarantees silent zero-lead failures that are impossible to debug across two layers simultaneously.
- Phase 3 before Phase 4: controllers need stable service contracts; the 202/polling pattern depends on the BFS engine correctly transitioning status from `EM_ANDAMENTO` to `CONCLUIDA`.
- Phase 5 last: hardening and integration tests assume a working system to validate.

### Research Flags

**Needs live validation before proceeding:**
- **Phase 2 (Scraper Layer):** e-SAJ CSS selectors (`#tablePartesPrincipais`, `#incidentes`, etc.) are unvalidated hypotheses — not confirmed against live TJ-SP HTML. CAC/SCP ViewState flow has not been confirmed against the live portal. Fire real requests against both portals with a known process number before building any BFS or service logic on top.

**Standard patterns — no additional research needed:**
- **Phase 1 (Foundation):** Spring Boot scaffolding with Flyway and Docker Compose is entirely standard; all library versions confirmed in STACK.md.
- **Phase 3 (BFS Engine):** BFS with a bounded queue and local visited set is a textbook algorithm; Kotlin `ArrayDeque` + `mutableSetOf` is idiomatic.
- **Phase 4 (REST API):** Spring MVC controllers, DTOs, SpringDoc, and `Page` pagination are well-documented Spring Boot patterns.
- **Phase 5 (Hardening):** Testcontainers 2.0 migration steps are explicit in STACK.md; Spring Boot `@ServiceConnection` integration is documented.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack versions | HIGH | All versions verified against official release announcements and changelogs (spring.io, kotlinlang.org, jsoup.org, GitHub releases pages). Current as of 2026-04-03. |
| Stack architecture decisions | HIGH | @Async vs coroutines, Caffeine vs Redis, JPA vs R2DBC, Jsoup vs Selenium all verified across multiple official and community sources. Decisions are stable. |
| Features — table stakes | HIGH | Derived from SPEC + REST async API design standards (Azure, Zuplo, RESTfulAPI.net). Well-established patterns. |
| Features — differentiators | MEDIUM | Competitive analysis limited: JUDIT Miner product page 404'd; details from search snippet only. FUNPREC requirements from PROJECT.md are clear and reliable. |
| Architecture patterns | HIGH | Spring Boot @Async with ThreadPoolTaskExecutor, JPA REQUIRES_NEW transaction per-persist, JOIN FETCH for collections, Caffeine @Cacheable — all verified via official Spring docs and tested community patterns. |
| Pitfalls — general | HIGH | Flyway checksum, Docker healthcheck, @Async proxy self-invocation, Caffeine negative caching — all sourced from official documentation. |
| Pitfalls — TJ-SP portal specific | MEDIUM | Rate limiting risk and CAPTCHA behavior based on community scraping tools (courtsbr/esaj) and ASP.NET ViewState guides. Not directly observable without live requests. |

**Overall confidence:** HIGH for all build decisions and technology choices; MEDIUM for portal-specific scraper behavior which requires live validation in Phase 2.

### Gaps to Address

- **e-SAJ CSS selectors require live calibration** — IDs `#tablePartesPrincipais`, `#incidentes`, `#tabelaTodasMovimentacoes`, `#tableTodasPartes` are taken from the SPEC and community tools. They have NOT been validated against live e-SAJ HTML. Resolution: fire a real Jsoup request in the Phase 2 smoke test against a known public process number before writing any business logic that depends on these selectors.

- **CAC/SCP portal live behavior is unknown** — the ViewState extraction pattern is well-established for ASP.NET forms generally, but whether TJ-SP's specific CAC/SCP portal adds JavaScript event handling that breaks a pure Jsoup POST approach is unconfirmed. If Jsoup POST fails, HtmlUnit 4.21.0 is the documented fallback (use `org.htmlunit:htmlunit`, not the old `net.sourceforge.htmlunit` coordinates). Resolution: validate in Phase 2 with a real precatório number before declaring `CacScraper` complete.

- **DataJud API key validity** — the key in PROJECT.md is noted as potentially changing. Resolution: externalize to `application.yml` from day one (SPEC already requires this); add startup health check in Phase 5 per Pitfall 12 mitigation.

- **TJ-SP rate limit threshold is unknown** — the 2s per-request delay is an assumption. The actual threshold that triggers a CAPTCHA or IP block is not documented. Resolution: implement the global `RateLimiter` with jitter in Phase 2; monitor for HTTP 429 responses; adjust rate based on observed behavior.

- **CPF availability on e-SAJ public pages** — cross-prospection deduplication by CPF/CNPJ (Pitfall 13) depends on CPF being parseable from e-SAJ party listings. Public pages may mask or omit CPF. Resolution: confirm during Phase 2 scraper validation; design deduplication defensively (CPF-when-available, name-based fallback for Phase 5).

---

## Sources

### Primary (HIGH confidence)
- Spring Boot 3.5 release: https://spring.io/blog/2025/05/22/spring-boot-3-5-0-available-now/
- Spring Boot 3.5 Release Notes: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.5-Release-Notes
- Kotlin 2.3.0 release: https://blog.jetbrains.com/kotlin/2025/12/kotlin-2-3-0-released/
- jsoup 1.22.1 release: https://jsoup.org/news/
- jsoup session API: https://jsoup.org/cookbook/web/request-session
- Testcontainers 2.0 releases: https://github.com/testcontainers/testcontainers-java/releases
- MockK releases: https://github.com/mockk/mockk/releases
- SpringDoc OpenAPI: https://springdoc.org/
- Spring coroutines docs: https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html
- Spring @Async docs: https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html
- Azure: Asynchronous Request-Reply Pattern: https://learn.microsoft.com/en-us/azure/architecture/patterns/asynchronous-request-reply
- Zuplo: Asynchronous Operations in REST APIs: https://zuplo.com/learning-center/asynchronous-operations-in-rest-apis-managing-long-running-tasks
- Baeldung: Spring @Async proxy pitfalls: https://www.baeldung.com/spring-async
- Baeldung: Resilience4j backoff/jitter: https://www.baeldung.com/resilience4j-backoff-jitter
- Docker Compose startup order: https://docs.docker.com/compose/how-tos/startup-order/

### Secondary (MEDIUM confidence)
- courtsbr/esaj (R scrapers for e-SAJ): https://github.com/courtsbr/esaj — confirms e-SAJ endpoint structure and CAPTCHA presence
- Trickster Dev: Scraping legacy ASP.Net with Scrapy: https://www.trickster.dev/post/scraping-legacy-asp-net-site-with-scrapy-a-real-example/ — ViewState session mechanics
- GoodData: Accidental DoS with Kotlin Coroutines: https://medium.com/gooddata-developers/how-we-accidentally-dos-ed-ourselves-with-kotlin-coroutines-22cc4be60370 — dispatcher thread exhaustion
- Lukas Lechner: 7 common Kotlin Coroutines mistakes: https://www.lukaslechner.com/7-common-mistakes-you-might-be-making-when-using-kotlin-coroutines/
- JUDIT Miner competitive reference: https://judit.io/en/ — product page 404'd; details from search result snippets only

### Tertiary (LOW confidence — requires live validation)
- e-SAJ CSS selector IDs (`#tablePartesPrincipais`, `#incidentes`, etc.) — sourced from SPEC and courtsbr/esaj community tools; NOT validated against live portal
- CAC/SCP portal ViewState behavior — sourced from ASP.NET scraping guides; NOT validated against `https://www.tjsp.jus.br/cac/scp/`

---
*Research completed: 2026-04-03*
*Ready for roadmap: yes*
