# Domain Pitfalls

**Domain:** Kotlin/Spring Boot court portal scraping — TJ-SP precatório lead prospecting
**Researched:** 2026-04-03
**Confidence:** MEDIUM (domain-specific e-SAJ behaviour verified via courtsbr/esaj project; general patterns HIGH confidence from official sources)

---

## Critical Pitfalls

Mistakes that cause rewrites, data corruption, or silent production failure.

---

### Pitfall 1: e-SAJ CSS Selectors Silently Return Empty Results After Portal Updates

**What goes wrong:** The IDs `#tablePartesPrincipais`, `#incidentes`, `#tabelaTodasMovimentacoes` are hardcoded in the SPEC. TJ-SP periodically redeploys e-SAJ with renamed IDs or restructured tables — the selectors return empty NodeLists rather than throwing exceptions. The scraper continues, persists empty party lists, and the prospection run appears to complete successfully with zero leads. The defect is invisible without end-to-end data validation.

**Why it happens:** Jsoup selector failures are silent by default — `doc.select("#nonExistent")` returns an empty `Elements`, not null and not an exception. The SPEC itself acknowledges selectors "will need calibration with real requests."

**Consequences:** Entire prospection runs produce zero leads. No error surfaces in logs. Caffeine caches the empty result for 24 hours, so retrying immediately makes no difference. Root cause discovery requires manual portal inspection.

**Prevention:**
- Centralise every selector string in a single `EsajSelectors` constants object — one change location.
- Add mandatory non-null assertions: after parsing parties, assert `parties.isNotEmpty()` before persisting. If empty, throw `ScrapingException("Party table empty — selector may be broken")` and do NOT cache the result.
- Log raw HTML (or at least the relevant section) to the `dados_brutos` JSONB column on every successful parse for offline reprocessing.
- Write a smoke-test that fires one real e-SAJ request against a known stable process number and validates that at least one party is returned. Run in CI with a `@Tag("integration")` guard.

**Detection warning signs:**
- All prospection runs complete with `credoresEncontrados = 0`
- Logs show no `ScrapingException` but also no parsed parties

**Phase:** Foundation / scraper bootstrap (Phase 1). Never defer selector validation.

---

### Pitfall 2: CAC/SCP ViewState Session Expiry Mid-Prospection

**What goes wrong:** The CAC/SCP portal is ASP.NET Web Forms. Each form submission requires `__VIEWSTATE`, `__VIEWSTATEGENERATOR`, and `__EVENTVALIDATION` values extracted from the most recent GET response. These values are cryptographically bound to the server-side session. If the server-side session expires (typically 20 minutes in IIS defaults), the next POST silently returns the empty search form rather than an error response — the server resets the page state and the scraper receives a "fresh" page with no results.

**Why it happens:** Jsoup's session (`Jsoup.newSession()`) maintains cookies but does not renew them. A long rate-limited prospection that processes 30+ processes will exceed the server session timeout. The POST appears to succeed (HTTP 200) but the body contains the blank form, not results.

**Consequences:** Precatório data silently missing from the database. Leads scored as 0 (no precatório found) because the scraper saw no results but reported no error.

**Prevention:**
- After every CAC/SCP POST, validate the response: assert that the result page contains expected elements (e.g., a results table or a "no results found" message). If neither is found, treat it as a session reset.
- Implement session re-establishment logic: on suspected session expiry, re-run the initial GET to obtain fresh ViewState, recreate the Jsoup session, and retry the POST once.
- Track `__VIEWSTATEGENERATOR` across requests — if the GET after a POST returns a different generator value than expected, the session was reset.
- Set a per-session time budget: after 15 minutes, proactively re-run the GET and refresh the session before the server-side expiry.

**Detection warning signs:**
- CAC/SCP responses that parse to zero results even for process numbers known to have precatórios
- Response HTML size significantly smaller than usual (blank form = smaller page)

**Phase:** CAC/SCP scraper implementation. Critical to test before writing the full recursion logic.

---

### Pitfall 3: BFS Fanout Explosion — Creditor Name Search Returns Unrelated Processes

**What goes wrong:** The recursive logic searches e-SAJ by creditor name at each depth level (`esajScraper.buscarPorNome(credor.nome)`). Common names like "MARIA DA SILVA" or "FAZENDA DO ESTADO DE SAO PAULO" return hundreds of processes. At depth=2 with 50 creditors, the queue can grow to thousands of entries. The `visitados` set prevents re-processing individual process numbers, but does not cap the total queue size. Memory exhaustion or a multi-hour run results.

**Why it happens:** The SPEC pseudocode shows `proximaFila.addAll(processosDoCredor)` without a size guard. For a name search returning 200 results per creditor × 50 creditors = 10,000 queue entries at depth 2 alone.

**Consequences:** Out-of-memory errors, run times measured in hours rather than minutes, thread pool starvation if multiple concurrent prospections run simultaneously.

**Prevention:**
- Cap the name-search result set at the scraper level: take at most N results (e.g., 5 most recent) per creditor name lookup.
- Implement a hard global cap on the total queue size per prospection: if `proximaFila.size > maxQueueSize` (e.g., 200), log a warning and truncate.
- Prefer searching by CPF/CNPJ when available — CPF is unique, name is not. Parse CPF from the party data and use `DOCPART` instead of `NMPART` when a CPF is found.
- Add an absolute time limit per prospection (e.g., 10 minutes) as a safety valve.
- Store queue depth stats in the `prospeccoes` table for post-run analysis.

**Detection warning signs:**
- `processos_visitados` counter growing faster than expected
- Heap memory climbing continuously during a single run
- Run time exceeding 5 minutes for a simple seed process

**Phase:** Recursion logic / BFS implementation (Phase 2). Design the caps before writing the BFS loop, not after.

---

### Pitfall 4: @Async Self-Invocation — Prospection Runs Synchronously Without Warning

**What goes wrong:** If `ProspeccaoService.iniciarProspeccao()` is annotated `@Async` and is called from within the same class (e.g., from a method that also lives in `ProspeccaoService`), Spring's AOP proxy is bypassed. The method runs synchronously on the HTTP thread, blocking the `POST /api/v1/prospeccao` endpoint for the full duration of the run — potentially minutes. No error is thrown. The endpoint eventually returns 200 instead of 202.

**Why it happens:** Spring `@Async` works via proxy interception. Direct `this.method()` calls bypass the proxy. The SPEC specifies both `@Async` and coroutines as options — mixing them without understanding proxy boundaries is a common mistake.

**Consequences:** The endpoint blocks, the client connection times out, and the prospection may be partially executed or rolled back on timeout. In load scenarios, thread pool exhaustion.

**Prevention:**
- Place the `@Async` method in a dedicated `ProspeccaoExecutor` bean, separate from `ProspeccaoService`. The controller calls `prospeccaoExecutor.executar(id)` — always goes through the proxy.
- Or use Kotlin coroutines launched from the controller with a dedicated coroutine scope, bypassing `@Async` entirely. This is the preferred pattern for Kotlin-first Spring Boot.
- Add a test that verifies the `POST /api/v1/prospeccao` endpoint returns 202 within 500ms even when the scraping mocks are slow (500ms delay). This will catch synchronous execution.

**Detection warning signs:**
- `POST /api/v1/prospeccao` takes longer than 200ms
- The returned `status` is already `CONCLUIDA` in the 202 response body

**Phase:** Async infrastructure setup (Phase 1). Decide @Async vs coroutines before writing any service code.

---

### Pitfall 5: Blocking JPA Calls on Reactive/Coroutine Threads

**What goes wrong:** The SPEC uses Spring WebClient (reactive, non-blocking) for external HTTP calls alongside Spring Data JPA (JDBC, blocking). If the prospection logic mixes coroutine suspend functions with `@Transactional` JPA repository calls without switching dispatchers, blocking JDBC calls execute on the coroutine dispatcher thread pool (typically Dispatchers.Default or Dispatchers.Unconfined in Spring). This starves the thread pool, causing all concurrent prospections to queue behind a single blocked thread.

**Why it happens:** JPA is built on JDBC, which is inherently blocking. Kotlin coroutines do not automatically offload blocking calls to a separate pool — they run on whatever dispatcher is active.

**Consequences:** Under concurrent load (multiple simultaneous prospections), runs that should execute in parallel queue serially. Response times degrade non-linearly as concurrency increases.

**Prevention:**
- Wrap all JPA repository calls in `withContext(Dispatchers.IO)` blocks: this offloads to the IO-optimised thread pool (64 threads default).
- Alternatively, keep all persistence calls in `@Async` methods with the dedicated `ProspeccaoExecutor` thread pool — the thread is already allocated for blocking work.
- The cleaner long-term solution: since this project uses Spring MVC (not WebFlux), use `@Async` with a dedicated `ThreadPoolTaskExecutor` for prospection. Reserve WebClient for HTTP calls only. Avoid mixing reactive and coroutine patterns.

**Detection warning signs:**
- All concurrent prospections taking `N × single_run_time` instead of `single_run_time` in parallel
- Thread dump showing coroutine threads blocked on `ResultSet.next()` or similar JDBC calls

**Phase:** Async infrastructure setup (Phase 1). The dispatcher strategy must be decided before service logic is written.

---

## Moderate Pitfalls

Mistakes that cause incorrect data or wasted development time.

---

### Pitfall 6: Flyway Checksum Failure Blocks Application Startup

**What goes wrong:** Flyway computes and stores a CRC32 checksum of each applied migration file. If a developer modifies `V1__create_tables.sql` after it has been applied (even changing a comment or trailing whitespace), Flyway throws `FlywayException: Validate failed... Migration checksum mismatch` and the Spring Boot application fails to start.

**Why it happens:** Developers often edit migrations during development after first applying them. In a Docker Compose setup, the PostgreSQL container persists the `flyway_schema_history` table in the named volume (`pgdata`) across restarts.

**Consequences:** Application fails to start. Recovery requires either `flyway repair` (updates the stored checksum, risky in production) or deleting the Docker volume and rebuilding.

**Prevention:**
- Treat applied migration files as immutable. Never edit them after first `docker compose up`.
- For schema changes during development: create `V2__...sql`, `V3__...sql` — never modify V1.
- Document this rule in the project README. New developers consistently violate it.
- In the Docker Compose file, document that `docker compose down -v` wipes all migrations and requires re-running from V1.

**Detection warning signs:**
- Application fails to start with `FlywayException` in logs
- Logs contain `Migration checksum mismatch for migration version 1`

**Phase:** Foundation (Phase 1). Establish the "never edit applied migrations" rule on day one.

---

### Pitfall 7: Docker Compose Race Condition — App Starts Before PostgreSQL Is Ready

**What goes wrong:** `depends_on: db` with only `condition: service_started` (the default) ensures the `db` container has started but not that PostgreSQL is actually accepting connections. If the API container starts before PostgreSQL finishes initializing, Flyway migration attempts fail with `Connection refused`, and Spring Boot exits with an error.

**Why it happens:** Docker `service_started` means the container process launched, not that the service inside is ready. PostgreSQL takes 1–5 seconds to initialise after the container starts.

**Consequences:** Application fails to start on first `docker compose up` in a fresh environment. Common in CI/CD pipelines.

**Prevention:**
- The SPEC already shows `condition: service_healthy` with a `pg_isready` healthcheck — this is correct. Verify it's actually implemented (not just in the SPEC but in the actual `docker-compose.yml`).
- Set `start_period: 10s` on the healthcheck to avoid false failures on slow machines.
- Use `restart: unless-stopped` on the API container as a secondary safety net for the rare case where Postgres is slow.

**Detection warning signs:**
- `docker compose up` works on the second run but fails on the first
- Logs show `Connection refused` or `FATAL: the database system is starting up`

**Phase:** Foundation (Phase 1). The SPEC healthcheck config is correct — ensure it is not simplified away during implementation.

---

### Pitfall 8: CNJ Process Number Format Inconsistency Breaks Deduplication

**What goes wrong:** The `visitados` set and the `UNIQUE` constraint on `processos.numero` assume a canonical process number format. TJ-SP returns process numbers in at least two formats:
- Canonical CNJ: `1234567-89.2020.8.26.0053`
- Legacy TJ-SP: `0012345678.2020.8.260053` or variations without dashes

If the same process appears in both formats across different scraping sources, the `visitados` set treats them as different nodes. The BFS visits the same process twice, doubling the network requests and potentially creating duplicate database entries (or a unique constraint violation that surfaces as an unhandled exception).

**Why it happens:** e-SAJ search results, process detail pages, and DataJud API can use different number formats. DataJud uses canonical CNJ; e-SAJ sometimes uses legacy format in links.

**Consequences:** Duplicate scraping of the same process, potential database unique constraint exceptions, inflated `processos_visitados` counters, degraded performance.

**Prevention:**
- Implement a `ProcessoNumberNormalizer` utility that converts all formats to canonical CNJ before any comparison or persistence.
- Apply normalization at every ingestion point: URL parameter parsing, HTML link extraction, DataJud response parsing.
- The `visitados` set and `processos.numero` UNIQUE constraint should always use normalized numbers.
- Unit test the normalizer against both formats before wiring it to the BFS logic.

**Detection warning signs:**
- Same process appearing twice in `processos` table with slightly different `numero` values
- `DataIntegrityViolationException` from the UNIQUE constraint on `processos.numero`

**Phase:** BFS + scraper integration (Phase 2). Normalizer must be in place before any real data is scraped.

---

### Pitfall 9: Rate Limiting Is Scoped Per Request But Not Per Burst

**What goes wrong:** The SPEC specifies a 2-second delay between requests (`sleep(delay)` in the pseudocode). This works for a single-threaded sequential flow. But if `thread-pool-size: 4` is used and multiple concurrent prospections run in parallel, each prospection independently enforces its own 2-second delay — resulting in 4 × (1 request per 2 seconds) = 2 requests per second to TJ-SP from the same IP. During an aggressive prospection with deep BFS, burst patterns emerge regardless of per-request delays.

**Why it happens:** The SPEC conflates "delay between my requests" with "rate limit enforcement." With multiple threads, per-thread delay is insufficient. TJ-SP's anti-bot measures likely monitor aggregate request rate per IP.

**Consequences:** IP block or CAPTCHA challenge, possibly affecting all concurrent users of the office's IP range.

**Prevention:**
- Implement a global rate limiter (not per-thread): use a `RateLimiter` (Guava) or a semaphore-backed delay shared across all scraping threads. Configure as: 1 request per 2 seconds system-wide.
- Expose this as a configuration property: `scraper.global-rate-limit-rps: 0.5` (0.5 requests/second).
- For a single-instance deployment (which this is), a shared `AtomicLong lastRequestTime` with synchronized delay is sufficient.
- Add jitter to the delay (±500ms randomness) to avoid detectable fixed-interval patterns.

**Detection warning signs:**
- HTTP 429 responses from TJ-SP
- Sudden empty responses or CAPTCHA HTML in scraping results
- All prospections failing simultaneously (IP-level block)

**Phase:** Scraper infrastructure (Phase 1). Must be global before any concurrent prospections are possible.

---

### Pitfall 10: Caffeine Cache Caches Failures — Negative Caching

**What goes wrong:** If a scraping call fails due to a transient network error or a rate limit response, and the exception is caught and a null/empty result is returned to the caller, `@Cacheable` may cache the null/empty result. On retry, the cached "no result" is returned immediately without re-querying. The process is effectively poisoned in the cache for 24 hours.

**Why it happens:** Spring's `@Cacheable` caches the return value including `null` if `unless` conditions are not set. Exceptions that are caught inside the cached method (returning null rather than throwing) result in null being cached.

**Consequences:** Transient failures become permanent for the cache TTL window. A temporarily unavailable process returns no data even after the portal recovers.

**Prevention:**
- Never catch scraping exceptions inside a `@Cacheable` method and return null. Let exceptions propagate — `@Cacheable` does not cache on exception by default.
- Configure `unless = "#result == null"` on all `@Cacheable` annotations that could return null.
- For the 24h TTL: consider using a shorter TTL for "no result found" responses vs. successful results (Caffeine supports per-entry expiry via `Expiry` interface).

**Detection warning signs:**
- A process that failed during prospection cannot be re-scraped after the portal recovers, even after 30 minutes
- Cache hit rate suspiciously high for processes that should have fresh data

**Phase:** Caching layer (Phase 1/2). Add `unless` condition guards from the first cache annotation.

---

## Minor Pitfalls

Mistakes that cause developer friction or minor data quality issues.

---

### Pitfall 11: Jsoup Memory Leak from Retained `Document` Objects in Long-Running Sessions

**What goes wrong:** Large HTML pages parsed by Jsoup retain the full DOM tree in memory. If `Document` objects are stored in fields, passed around, or referenced in closures during the BFS traversal, the full HTML DOM trees for all visited processes accumulate in the JVM heap. For 100 visited processes at 200KB each, this is 20MB — manageable alone, but combined with Caffeine's raw HTML cache (`dados_brutos` JSONB), memory pressure builds.

**Prevention:**
- Parse what is needed immediately in the scraper method and discard the `Document`. Never store `Document` objects outside the parsing method.
- Store raw HTML strings in the cache/database (already planned via `dados_brutos`), not parsed `Document` objects.

**Phase:** Scraper implementation. Code review concern, not architectural.

---

### Pitfall 12: DataJud API Key Rotation Breaks All Enrichment Silently

**What goes wrong:** The SPEC correctly notes the DataJud API key "may change" and must be in config. If the key is rotated and the config is not updated, DataJud calls return 401. The SPEC's error handling ("continue prospection even if one source fails") means DataJud failures are logged but the prospection continues — with no DataJud enrichment. Scores are calculated without DataJud data and the degradation is invisible.

**Prevention:**
- Add a DataJud connectivity health check at application startup (can be disabled via config flag `datajud.health-check-on-startup: true`).
- Log a WARN-level alert (not just DEBUG) whenever any DataJud call returns 401/403, and include a counter in the `prospeccoes` record (e.g., `datajud_failures: INTEGER`).
- Expose a `/actuator/health` custom indicator that reports DataJud connectivity status.

**Phase:** DataJud client (Phase 2). Monitoring concern — implement alongside the client.

---

### Pitfall 13: `UNIQUE(nome, processo_id)` on `credores` Is Too Loose for Deduplication

**What goes wrong:** The SPEC defines `UNIQUE(nome, processo_id)` on the `credores` table. A single process can have multiple parties with the same name but different CPFs (unusual but possible in joinder cases). More critically, the same physical creditor appears in multiple processes — the unique constraint allows duplicate `credores` rows for the same person across different processes. The scoring engine then creates separate leads for the same creditor, inflating the lead count.

**Prevention:**
- If CPF is available, add a secondary deduplication pass in `LeadService` that collapses leads from the same CPF across processes.
- Consider a `pessoas` table (by CPF/CNPJ) that `credores` references, enabling cross-process deduplication at the data model level. (Out of scope for v1, but worth noting as v2 evolution.)
- At minimum, add a note in the scoring service that scores should be per-CPF, not per-`credor_id`.

**Phase:** Data model review (Phase 1). Minor for v1, critical for production lead quality.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|----------------|------------|
| Scraper bootstrap (Phase 1) | CSS selector silent failure (Pitfall 1) | Mandatory empty-check assertions, smoke test |
| Async infrastructure (Phase 1) | @Async self-invocation (Pitfall 4) | Separate executor bean or pure coroutine approach |
| Async infrastructure (Phase 1) | Blocking JPA on coroutine thread (Pitfall 5) | Decide dispatcher strategy before writing services |
| Caching layer (Phase 1) | Negative caching of failures (Pitfall 10) | Add `unless = "#result == null"` from first annotation |
| CAC/SCP scraper (Phase 2) | ViewState session expiry (Pitfall 2) | Session renewal logic + response validation |
| BFS recursion (Phase 2) | Queue fanout explosion (Pitfall 3) | Queue size cap + CPF-based name search |
| BFS recursion (Phase 2) | Process number format inconsistency (Pitfall 8) | Normalizer utility before any BFS code |
| Rate limiting (Phase 1/2) | Per-thread vs global rate limit (Pitfall 9) | Shared global RateLimiter, add jitter |
| Docker + DB (Phase 1) | Flyway checksum mismatch (Pitfall 6) | Immutable migration rule, team documentation |
| Docker + DB (Phase 1) | Startup race condition (Pitfall 7) | `service_healthy` + `pg_isready` (already in SPEC) |
| DataJud client (Phase 2) | API key rotation silent failure (Pitfall 12) | Startup health check + WARN-level alerts |
| Lead quality (any) | Creditor deduplication by CPF (Pitfall 13) | Post-score deduplication pass in LeadService |

---

## Sources

- [courtsbr/esaj — R scrapers for e-SAJ systems](https://github.com/courtsbr/esaj) — confirms CAPTCHA presence and structure of e-SAJ endpoints (MEDIUM confidence)
- [Scraping legacy ASP.Net sites with Scrapy — Trickster Dev](https://www.trickster.dev/post/scraping-legacy-asp-net-site-with-scrapy-a-real-example/) — ViewState + EventValidation scraping patterns (HIGH confidence)
- [odetocode.com — Screen Scraping, ViewState, and Authentication using ASP.Net](https://odetocode.com/articles/162.aspx) — ViewState session mechanics (HIGH confidence)
- [Baeldung — How To Do @Async in Spring](https://www.baeldung.com/spring-async) — @Async proxy pitfalls (HIGH confidence)
- [Common mistakes to avoid when using @Async in Spring — Engati](https://www.engati.ai/blog/common-mistakes-to-avoid-when-using-async-in-spring) — thread pool and self-invocation (MEDIUM confidence)
- [7 common mistakes with Kotlin Coroutines — Lukas Lechner](https://www.lukaslechner.com/7-common-mistakes-you-might-be-making-when-using-kotlin-coroutines/) — SupervisorJob, runBlocking pitfalls (HIGH confidence)
- [How We Accidentally DoS-ed Ourselves with Kotlin Coroutines — GoodData](https://medium.com/gooddata-developers/how-we-accidentally-dos-ed-ourselves-with-kotlin-coroutines-22cc4be60370) — dispatcher thread exhaustion (HIGH confidence)
- [Handling the Blocking Method in Non-blocking Context Warning — Baeldung](https://www.baeldung.com/java-handle-blocking-method-in-non-blocking-context-warning) — JPA + WebClient blocking issue (HIGH confidence)
- [Flyway 9 and PostgreSQL 15 Upgrade — ZoolaTech](https://zoolatech.com/blog/flyway-migration/) — Flyway checksum and locking pitfalls (MEDIUM confidence)
- [Docker Compose startup order — official docs](https://docs.docker.com/compose/how-tos/startup-order/) — `service_healthy` condition (HIGH confidence)
- [Cache Stampede Protection in Spring Boot — Medium](https://medium.com/@AlexanderObregon/cache-stampede-protection-in-spring-boot-applications-341f87b37649) — Caffeine negative caching (MEDIUM confidence)
- [Jsoup request session — official docs](https://jsoup.org/cookbook/web/request-session) — cookie jar memory pitfall (HIGH confidence)
