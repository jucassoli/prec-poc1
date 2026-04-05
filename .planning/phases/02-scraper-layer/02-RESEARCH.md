# Phase 2: Scraper Layer - Research

**Researched:** 2026-04-05
**Domain:** Web scraping (Jsoup, Jsoup sessions, ASP.NET ViewState), resilience patterns (Resilience4j), reactive HTTP client (Spring WebClient), REST controller layer
**Confidence:** MEDIUM-HIGH (stack verified against Maven Central; e-SAJ/CAC selectors are hypotheses pending live validation; DataJud auth confirmed against official docs)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Per-scraper rate limiters (not a single centralized limiter) — each scraper (e-SAJ, CAC/SCP, DataJud) has its own Resilience4j RateLimiter instance configured independently
- **D-02:** Use Resilience4j RateLimiter composed with Retry and CircuitBreaker — declarative, configurable via application.yml, already in build.gradle.kts
- **D-03:** 2s inter-request delay per scraper, exponential backoff on failure (3 retries), 60s pause on HTTP 429 — all from existing application.yml config
- **D-04:** Graceful degradation when CSS selectors fail — do not throw exceptions on missing elements
- **D-05:** Return partial data with a flag indicating which fields failed extraction — caller decides whether to accept partial results
- **D-06:** Log warnings on selector mismatches (not errors) — allows monitoring without breaking the pipeline
- **D-07:** Extract all CSS selectors into a centralized constants class (EsajSelectors) for single-point updates when HTML structure changes

### Claude's Discretion

- CAC/SCP session management details (ViewState lifecycle, cookie persistence, Jsoup vs HtmlUnit fallback)
- DTO structure for lookup endpoints (field naming, nesting)
- Error response contract for 404/500 cases (building on existing GlobalExceptionHandler and ErrorResponse)
- DataJud Elasticsearch DSL query construction

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ESAJ-01 | Fetch process details from e-SAJ by process number using Jsoup (no login required) | EsajScraper with Jsoup.connect(), URL pattern `cpopg/show.do?processo.codigo=` |
| ESAJ-02 | Extract all parties (Reqte, Reqdo, Exequente, Credor) from the process page | CSS selector `.nomeParteEAdvogado` rows; `#tablePartesPrincipais` table [ASSUMED — needs live validation] |
| ESAJ-03 | Extract all linked precatório incidents from a process page | Table scan for incident links `#incidentes` or similar [ASSUMED — needs live validation] |
| ESAJ-04 | Search processes by name via e-SAJ public search | `cpopg/search.do` endpoint with query params |
| ESAJ-05 | e-SAJ enforces minimum 2s delay between requests | Resilience4j RateLimiter: limitForPeriod=1, limitRefreshPeriod=2s |
| ESAJ-06 | e-SAJ retries on failure with exponential backoff (1s, 2s, 4s; max 3 attempts) | Resilience4j Retry: maxAttempts=3, waitDuration=1s, exponentialBackoffMultiplier=2 |
| ESAJ-07 | e-SAJ pauses 60s on HTTP 429 or CAPTCHA | Custom ResponseStatusException detection; Resilience4j Retry with custom waitDuration override on 429 |
| CAC-01 | Fetch precatório data (value, payment status, chronological position, nature, debtor entity) | Jsoup session scraping CAC/SCP portal fields |
| CAC-02 | CAC/SCP maintains HTTP session and handles ASP.NET ViewState across GET→POST cycle | `Jsoup.newSession()` + extract `__VIEWSTATE`, `__VIEWSTATEGENERATOR`, `__EVENTVALIDATION` hidden fields |
| CAC-03 | CAC/SCP enforces same rate limiting and retry policy as e-SAJ | Separate Resilience4j RateLimiter/Retry instances with identical config |
| DATJ-01 | Query DataJud CNJ API for process metadata by process number | WebClient POST to `api_publica_tjsp/_search` with `match` query on `numeroProcesso` |
| DATJ-02 | Query DataJud for precatório-class processes by IBGE municipality code | WebClient POST with `term` filter on `codigoMunicipioIBGE` field [ASSUMED — field name needs verification] |
| DATJ-03 | DataJud client uses configurable API key from application.yml | `Authorization: APIKey {key}` header; key already in `application.yml` |
| API-06 | `GET /api/v1/processo/{numero}` returns structured process data from e-SAJ | ProcessoController → EsajScraper; returns ProcessoResponseDTO |
| API-07 | `GET /api/v1/processo/buscar` searches by `?nome=`, `?cpf=`, or `?numero=` | ProcessoController with `@RequestParam`; delegates to EsajScraper search method |
| API-08 | `GET /api/v1/precatorio/{numero}` returns precatório data from CAC/SCP | PrecatorioController → CacScraper; returns PrecatorioResponseDTO |
| API-09 | `POST /api/v1/datajud/buscar` proxies a DataJud query | DataJudController accepts DataJudBuscarRequestDTO; delegates to DataJudClient |
</phase_requirements>

---

## Summary

Phase 2 builds three scrapers and four REST endpoints that expose them. The core library choices are already decided and in place: Jsoup 1.21.1 is in the build for HTML scraping, Spring WebFlux (WebClient) is in the build for DataJud HTTP calls, and the existing `application.yml` already has all scraper configuration. The one significant gap is **Resilience4j**: the CONTEXT.md states it is "already in build.gradle.kts" but the actual `build.gradle.kts` file does not include it. Adding `resilience4j-spring-boot3:2.3.0` (plus `spring-boot-starter-aop`) is a required build change before any scraper implementation can proceed.

The biggest technical risk in Phase 2 is not coding — it is live validation. The e-SAJ CSS selectors (table IDs, class names for parties and incidents) are hypotheses based on publicly available R package documentation and community scrapers; they have not been confirmed against the current TJ-SP HTML. Similarly, the CAC/SCP portal's exact form field names and the specific URLs involved are unconfirmed. Both scrapers need a live validation spike (Plan 02-01 and 02-02) before the BFS engine in Phase 4 is built on top of them. The DataJud API is the best-documented of the three — authentication format confirmed against official CNJ documentation, endpoint URL verified, query format confirmed from multiple sources.

**Primary recommendation:** Add Resilience4j to `build.gradle.kts` first (Wave 0 of Plan 02-01), then run live validation spikes for e-SAJ and CAC/SCP before writing the full scraper code. Do not defer live validation to the end of the phase — it is the phase's highest-risk activity.

---

## Project Constraints (from CLAUDE.md)

CLAUDE.md contains no actionable directives. No project-specific constraints override standard research findings.

---

## Standard Stack

### CRITICAL: Missing Dependency

Resilience4j is NOT in `build.gradle.kts` despite being referenced in CONTEXT.md decisions D-01 through D-03. This must be added in Wave 0 of Plan 02-01.

**Version verified:** `resilience4j-spring-boot3:2.3.0` [VERIFIED: Maven Central, timestamp 2026-01-03]

```kotlin
// Required additions to build.gradle.kts dependencies block:
implementation("io.github.resilience4j:resilience4j-spring-boot3:2.3.0")
implementation("org.springframework.boot:spring-boot-starter-aop")  // required by Resilience4j
```

### Core Libraries (Already in build.gradle.kts)

| Library | Version | Purpose | Status |
|---------|---------|---------|--------|
| `org.jsoup:jsoup` | 1.21.1 | HTML scraping for e-SAJ and CAC/SCP | [VERIFIED: build.gradle.kts, confirmed on registry] |
| `spring-boot-starter-webflux` | via BOM (3.5.3) | WebClient for DataJud HTTP calls | [VERIFIED: build.gradle.kts] |
| `kotlinx-coroutines-reactor` | via BOM | Bridges WebClient reactive types to coroutine-friendly API | [VERIFIED: build.gradle.kts] |
| `spring-boot-starter-aop` | via BOM | Required for Resilience4j annotation processing | NOT YET IN BUILD |

### Libraries to Add

| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| `resilience4j-spring-boot3` | **2.3.0** | RateLimiter + Retry + CircuitBreaker for scrapers | [VERIFIED: Maven Central] |
| `spring-boot-starter-aop` | via BOM | AOP proxy support required by Resilience4j | Already pulled transitively by many starters — add explicitly |

### Already Available (No Action Needed)

| Capability | How Available |
|-----------|--------------|
| Jsoup HTML scraping | `jsoup:1.21.1` in build |
| Reactive HTTP client (WebClient) | `spring-boot-starter-webflux` in build |
| Configuration properties | `@ConfigurationProperties` + application.yml with all URLs/keys |
| Exception handling | `GlobalExceptionHandler`, `ScrapingException`, `ProcessoNaoEncontradoException` |
| Test framework | JUnit 5 + MockK + Testcontainers (PostgreSQL) |

### Version Verification

```bash
# Confirm Resilience4j Spring Boot 3 latest
curl -s "https://search.maven.org/solrsearch/select?q=g:io.github.resilience4j+AND+a:resilience4j-spring-boot3&rows=1&wt=json" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['response']['docs'][0]['latestVersion'])"
# Returns: 2.3.0 (verified 2026-04-05)
```

---

## Architecture Patterns

### Recommended Project Structure (Phase 2 additions)

```
src/main/kotlin/br/com/precatorios/
├── scraper/
│   ├── EsajScraper.kt               # Jsoup-based e-SAJ scraper
│   ├── EsajSelectors.kt             # Centralized CSS selector constants (D-07)
│   ├── CacScraper.kt                # Jsoup.newSession() CAC/SCP scraper
│   └── DataJudClient.kt             # WebClient-based DataJud HTTP client
├── config/
│   ├── ResilienceConfig.kt          # Resilience4j bean definitions (RateLimiter, Retry per scraper)
│   └── ScraperProperties.kt         # @ConfigurationProperties binding for scraper.* keys
├── controller/
│   ├── ProcessoController.kt        # GET /api/v1/processo/{numero}, GET /api/v1/processo/buscar
│   ├── PrecatorioController.kt      # GET /api/v1/precatorio/{numero}
│   └── DataJudController.kt         # POST /api/v1/datajud/buscar
└── dto/
    ├── ProcessoResponseDTO.kt       # Process data + parties + incidents
    ├── ParteDTO.kt                  # Party name + role + counsel
    ├── IncidenteDTO.kt              # Incident number + type + link
    ├── PrecatorioResponseDTO.kt     # Value + status + position + nature + debtor
    ├── DataJudBuscarRequestDTO.kt   # Passthrough query body to DataJud
    └── DataJudBuscarResponseDTO.kt  # Structured DataJud result
```

### Pattern 1: Resilience4j RateLimiter + Retry (Programmatic, Per-Scraper)

Use programmatic bean definition rather than annotation-based, because annotation-based requires beans to call each other through Spring proxy boundaries — scrapers calling their own methods won't be decorated. With programmatic decoration, the rate limiting is explicit and testable.

```kotlin
// Source: https://resilience4j.readme.io/docs/getting-started-3 [CITED]
@Configuration
class ResilienceConfig {

    @Bean
    fun esajRateLimiter(): RateLimiter {
        val config = RateLimiterConfig.custom()
            .limitForPeriod(1)
            .limitRefreshPeriod(Duration.ofSeconds(2))
            .timeoutDuration(Duration.ofSeconds(5))
            .build()
        return RateLimiter.of("esaj", config)
    }

    @Bean
    fun esajRetry(): Retry {
        val config = RetryConfig.custom<Any>()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(1))
            .enableExponentialBackoff()
            .exponentialBackoffMultiplier(2.0)
            .retryOnException { t -> t !is BusinessException }
            .build()
        return Retry.of("esaj", config)
    }

    // Identical beans for cacRateLimiter, cacRetry, datajudRateLimiter, datajudRetry
}
```

**application.yml counterpart** (declarative override capability):

```yaml
resilience4j:
  ratelimiter:
    instances:
      esaj:
        limit-for-period: 1
        limit-refresh-period: 2s
        timeout-duration: 5s
      cac:
        limit-for-period: 1
        limit-refresh-period: 2s
        timeout-duration: 5s
      datajud:
        limit-for-period: 5
        limit-refresh-period: 1s
        timeout-duration: 3s
  retry:
    instances:
      esaj:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2.0
      cac:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2.0
      datajud:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2.0
```

### Pattern 2: e-SAJ Scraper with Graceful Degradation

Per decisions D-04 through D-07: use `select()` not `selectFirst()?.text() ?: throw`, return nullable fields, set a `fieldsMissing` flag in the response DTO.

```kotlin
// Source: Jsoup official docs + D-04/D-05/D-06/D-07 constraints [CITED: jsoup.org/cookbook]
@Service
class EsajScraper(
    private val esajRateLimiter: RateLimiter,
    private val esajRetry: Retry
) {
    private val log = LoggerFactory.getLogger(EsajScraper::class.java)

    fun fetchProcesso(numero: String): ProcessoScraped {
        return RateLimiter.decorateCheckedSupplier(esajRateLimiter) {
            Retry.decorateCheckedSupplier(esajRetry) {
                doFetchProcesso(numero)
            }.apply()
        }.apply()
    }

    private fun doFetchProcesso(numero: String): ProcessoScraped {
        val doc = Jsoup.connect("${baseUrl}${EsajSelectors.SHOW_PATH}")
            .data("processo.codigo", numero)
            .userAgent(userAgent)
            .timeout(timeoutMs)
            .get()

        val missingFields = mutableListOf<String>()

        val classe = doc.select(EsajSelectors.CLASSE).firstOrNull()?.text().also {
            if (it == null) { log.warn("Selector '{}' returned no match for processo {}", EsajSelectors.CLASSE, numero); missingFields.add("classe") }
        }

        // ... repeat pattern for each field

        return ProcessoScraped(
            numero = numero,
            classe = classe,
            partes = extractPartes(doc, missingFields),
            incidentes = extractIncidentes(doc, missingFields),
            missingFields = missingFields
        )
    }
}
```

### Pattern 3: Centralized EsajSelectors Constants

```kotlin
// All selectors in one place — update here when TJ-SP changes HTML (D-07)
object EsajSelectors {
    const val SHOW_PATH = "/cpopg/show.do"
    const val SEARCH_PATH = "/cpopg/search.do"

    // Process header fields — ASSUMED, verify in live smoke test
    const val CLASSE = "#classeProcesso"
    const val ASSUNTO = "#assuntoProcesso"
    const val FORO = "#foroProcesso"
    const val VARA = "#varaProcesso"
    const val JUIZ = "#juizProcesso"
    const val VALOR_ACAO = "#valorAcaoProcesso"

    // Parties table — ASSUMED based on community scrapers
    const val PARTES_TABLE = "#tablePartesPrincipais"
    const val PARTE_NOME = ".nomeParteEAdvogado"

    // Incidents — ASSUMED, verify live
    const val INCIDENTES_TABLE = "#incidentes"

    // Search page
    const val SEARCH_RESULT_TABLE = "#listagemDeProcessos"
    const val SEARCH_NUMERO = "#numeroProcesso"
}
```

### Pattern 4: CAC/SCP ViewState GET/POST Cycle

The CAC/SCP portal uses ASP.NET ViewState. The correct pattern is:
1. GET the search form → extract `__VIEWSTATE`, `__VIEWSTATEGENERATOR`, `__EVENTVALIDATION`
2. POST form data + all three ViewState fields in the same Jsoup session (cookies auto-propagated)
3. Detect blank-form response as signal that session expired → renew session and retry

```kotlin
// Source: Jsoup newSession docs [CITED: jsoup.org/cookbook/web/request-session]
//         ASP.NET ViewState pattern [CITED: trickster.dev/post/scraping-legacy-asp-net-site]
@Service
class CacScraper(
    private val cacRateLimiter: RateLimiter,
    private val cacRetry: Retry
) {
    private var session: Connection = Jsoup.newSession()
        .timeout(timeoutMs)
        .userAgent(userAgent)

    fun fetchPrecatorio(numero: String): PrecatorioScraped {
        return RateLimiter.decorateCheckedSupplier(cacRateLimiter) {
            Retry.decorateCheckedSupplier(cacRetry) {
                doFetchPrecatorio(numero)
            }.apply()
        }.apply()
    }

    private fun doFetchPrecatorio(numero: String): PrecatorioScraped {
        // Step 1: GET form page
        val formPage = session.newRequest(baseUrl)
            .get()

        // Step 2: Extract all ViewState hidden fields
        val viewState = formPage.select("input[name=__VIEWSTATE]").firstOrNull()?.attr("value") ?: ""
        val viewStateGen = formPage.select("input[name=__VIEWSTATEGENERATOR]").firstOrNull()?.attr("value") ?: ""
        val eventValidation = formPage.select("input[name=__EVENTVALIDATION]").firstOrNull()?.attr("value") ?: ""

        // Step 3: POST with ViewState included
        val resultPage = session.newRequest(baseUrl)
            .data("__VIEWSTATE", viewState)
            .data("__VIEWSTATEGENERATOR", viewStateGen)
            .data("__EVENTVALIDATION", eventValidation)
            .data("numeroPrecatorio", numero)  // ASSUMED field name — verify live
            .post()

        // Step 4: Blank-form detection
        if (isBlankFormResponse(resultPage)) {
            session = Jsoup.newSession().timeout(timeoutMs).userAgent(userAgent)
            throw ScrapingException("CAC session expired and was renewed — retry will occur")
        }

        return parsePrecatorioPage(resultPage)
    }

    private fun isBlankFormResponse(doc: Document): Boolean {
        // ASSUMED: blank form response has empty result container — verify live
        return doc.select(".resultadoPesquisa").isEmpty()
    }
}
```

### Pattern 5: DataJud WebClient with Blocking Adapter

EsajScraper and CacScraper are synchronous Jsoup-based. DataJudClient uses reactive WebClient. Since the controller layer uses Spring MVC (not WebFlux), use `.block()` at the service boundary to get the result synchronously. This is acceptable because DataJud calls are infrequent and the controller sits in a Tomcat thread (not an event loop thread).

```kotlin
// Source: Spring WebFlux docs [CITED: docs.spring.io/spring-framework/reference]
@Service
class DataJudClient(
    private val webClientBuilder: WebClient.Builder,
    private val datajudRateLimiter: RateLimiter,
    private val datajudRetry: Retry
) {
    private val client: WebClient = webClientBuilder
        .baseUrl(baseUrl)
        .defaultHeader("Authorization", "APIKey $apiKey")
        .defaultHeader("Content-Type", "application/json")
        .build()

    fun buscar(queryBody: String): DataJudResult {
        return RateLimiter.decorateCheckedSupplier(datajudRateLimiter) {
            Retry.decorateCheckedSupplier(datajudRetry) {
                client.post()
                    .uri(endpointTjsp)
                    .bodyValue(queryBody)
                    .retrieve()
                    .onStatus({ it.value() == 429 }) { _ ->
                        throw ScrapingException("DataJud HTTP 429 — rate limit exceeded")
                    }
                    .bodyToMono(String::class.java)
                    .block(Duration.ofSeconds(30))
                    ?: throw ScrapingException("DataJud returned empty response")
            }.apply()
        }.apply()
    }
}
```

**DataJud Elasticsearch query format** (confirmed from official CNJ docs):

```json
{
  "query": {
    "match": {
      "numeroProcesso": "0001234-56.2023.8.26.0100"
    }
  }
}
```

**Authentication header** confirmed: `Authorization: APIKey {key}` [VERIFIED: datajud-wiki.cnj.jus.br/api-publica/acesso/]

**TJSP endpoint:** `https://api-publica.datajud.cnj.jus.br/api_publica_tjsp/_search` [VERIFIED: datajud-wiki.cnj.jus.br/api-publica/endpoints/]

### Pattern 6: ScraperProperties @ConfigurationProperties

```kotlin
// Binds scraper.* from application.yml — already has all needed values
@ConfigurationProperties(prefix = "scraper")
@Configuration
data class ScraperProperties(
    val esaj: EsajProps = EsajProps(),
    val precatorioPortal: CacProps = CacProps(),
    val datajud: DataJudProps = DataJudProps()
) {
    data class EsajProps(
        val baseUrl: String = "",
        val delayMs: Long = 2000,
        val timeoutMs: Int = 30000,
        val userAgent: String = "",
        val maxRetries: Int = 3
    )
    data class CacProps(
        val baseUrl: String = "",
        val delayMs: Long = 2000,
        val timeoutMs: Int = 30000
    )
    data class DataJudProps(
        val baseUrl: String = "",
        val endpointTjsp: String = "",
        val apiKey: String = "",
        val timeoutMs: Int = 30000,
        val maxResultadosPorPagina: Int = 100
    )
}
```

### Anti-Patterns to Avoid

- **Throwing exceptions on missing CSS selectors:** Instead log warnings and populate `missingFields` (D-04, D-05, D-06)
- **Single global rate limiter:** Each scraper must have its own Resilience4j instance (D-01)
- **Hard-coded CSS selectors inline:** Must all go in `EsajSelectors` object (D-07)
- **Calling a Resilience4j-annotated method from within the same bean:** Spring AOP self-invocation skips the proxy. Use programmatic decoration or separate beans.
- **Using `@Async` in the scraper layer:** Scrapers are synchronous data fetchers; async execution belongs to the BFS engine in Phase 4
- **Using `.block()` on WebClient inside a Reactor event loop thread:** Only call `.block()` from a Tomcat thread (MVC controller context). If called from within a reactive pipeline, use `.flatMap()` instead.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Rate limiting / throttling | Thread.sleep() loop | Resilience4j RateLimiter | Handles permit exhaustion, timeouts, thread safety |
| Retry with exponential backoff | Manual try/catch loop with sleep | Resilience4j Retry | Handles jitter, configurable backoff, retry on exception predicates |
| HTTP 429 handling | Custom response code checker | Resilience4j Retry `retryExceptions` + custom predicate | Composable with other resilience patterns |
| Cookie/session persistence for scraping | Manual cookie store Map | Jsoup `newSession()` + `cookieStore()` | Thread-safe, automatically propagates cookies across requests |
| ViewState extraction | Manual Base64 parsing | Jsoup `select("input[name=__VIEWSTATE]").attr("value")` | ASP.NET ViewState is a hidden form field — just extract it as a string and resubmit unchanged |
| Reactive HTTP client | Apache HttpClient with custom pool | Spring WebClient (already in build) | Non-blocking, composable with Reactor retry/timeout |

**Key insight:** The scraping reliability problems (rate limiting, retry, backoff) are solved problems with mature library support. Resilience4j handles all of them declaratively; the phase's value is in correctly mapping the portal HTML structures, not in building resilience infrastructure.

---

## Runtime State Inventory

Step 2.5: SKIPPED — Phase 2 is greenfield implementation, not a rename/refactor/migration phase.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JVM 21 | Building and running the app | ✓ | OpenJDK 21.0.10 | — |
| Gradle 8.12 | Build system | ✓ | 8.12 | — |
| PostgreSQL (Docker) | Integration tests via Testcontainers | ✓ (Docker) | — | Testcontainers starts it |
| Internet access to esaj.tjsp.jus.br | Live validation spike | [UNKNOWN] | — | Cannot stub live validation |
| Internet access to tjsp.jus.br/cac/scp | Live validation spike | [UNKNOWN] | — | Cannot stub live validation |
| Internet access to api-publica.datajud.cnj.jus.br | DataJud smoke test | [UNKNOWN] | — | Cannot stub live validation |
| DATAJUD_API_KEY | DataJud auth | ✓ (hardcoded default in application.yml) | public key | Default key is the CNJ public key |

**Missing dependencies with no fallback:**
- Internet access to TJ-SP portals (e-SAJ, CAC/SCP, DataJud) — live validation spikes in Plans 02-01 and 02-02 require outbound internet. If the development machine lacks this, live validation must be run on a machine with access before declaring the scrapers done.

**Missing dependencies with fallback:**
- None.

---

## Common Pitfalls

### Pitfall 1: Resilience4j Not in Build

**What goes wrong:** Build compiles but any Resilience4j import causes `ClassNotFoundException` at runtime; or IDE shows unresolved references.
**Why it happens:** `build.gradle.kts` does not include `resilience4j-spring-boot3` or `spring-boot-starter-aop` despite CONTEXT.md stating they are present.
**How to avoid:** Add both dependencies in Wave 0 of Plan 02-01 before writing any scraper code. Run `./gradlew compileKotlin` to confirm.
**Warning signs:** `import io.github.resilience4j` causes IDE red underlines.

### Pitfall 2: e-SAJ Selector Mismatch

**What goes wrong:** All scraper calls return `ProcessoScraped` with all fields null and `missingFields` containing everything — every request degrades gracefully but returns no data.
**Why it happens:** CSS selector names like `#classeProcesso`, `#tablePartesPrincipais`, `.nomeParteEAdvogado` are hypotheses based on older community scrapers. TJ-SP may have changed their HTML.
**How to avoid:** Run the live smoke test in Plan 02-01 against a known process number and inspect the raw HTML before writing the full extraction logic. Put a test assertion that at least `classe` is non-null.
**Warning signs:** All extracted fields are null on the first live run; full HTML dump shows different element IDs.

### Pitfall 3: CAC/SCP ViewState Session Expiry Loop

**What goes wrong:** CacScraper detects blank form, renews session, throws ScrapingException for retry — but the retry fetches a new form and the new ViewState is identical, causing an infinite renewal loop.
**Why it happens:** Session renewal logic may be triggered by something other than session expiry (e.g., form validation error, CAPTCHA).
**How to avoid:** Add a `sessionRenewCount` per request; throw a non-retryable `ScrapingException` if blank form appears after a fresh session was just created. Inspect the blank form HTML to understand the actual cause.
**Warning signs:** Logs show repeated "CAC session expired and was renewed" messages for the same process number.

### Pitfall 4: Resilience4j Self-Invocation AOP Bypass

**What goes wrong:** `@RateLimiter` annotation on a method is bypassed entirely — no rate limiting occurs.
**Why it happens:** When `EsajScraper.fetchProcesso()` calls `this.doFetch()` internally, the call bypasses the Spring AOP proxy. `@RateLimiter` relies on the proxy interceptor.
**How to avoid:** Use programmatic Resilience4j decoration (as shown in Pattern 1) rather than `@RateLimiter` annotation. The `RateLimiter.decorateCheckedSupplier()` approach works regardless of call origin.
**Warning signs:** Rate limiting has no visible effect; requests go out faster than 2s intervals.

### Pitfall 5: WebClient `.block()` Called on Reactor Event Loop Thread

**What goes wrong:** `IllegalStateException: block()/blockFirst()/blockLast() are blocking, which is not supported in thread reactor-http-epoll-X`
**Why it happens:** If DataJudClient is called from any reactive context (e.g., from a Mono/Flux pipeline), `.block()` will throw.
**How to avoid:** DataJudClient must only be invoked from Spring MVC (Tomcat) controller threads, not from reactive pipelines. In Phase 4 (BFS engine), use `.subscribe()` or `awaitSingle()` in a coroutine scope instead.
**Warning signs:** The exception message contains `reactor-http-epoll` or `parallel` in the thread name.

### Pitfall 6: HTTP 429 Requiring 60s Pause Conflicts with Resilience4j Retry Timing

**What goes wrong:** Resilience4j Retry fires retries at 1s, 2s, 4s — but requirement says pause 60s on HTTP 429 (ESAJ-07).
**Why it happens:** Standard Resilience4j Retry uses waitDuration for all retries without differentiating by status code.
**How to avoid:** Add a custom `retryOnResult` predicate that checks for 429 status and a separate recovery path that sleeps 60s before retrying. Alternatively: catch the 429 response in Jsoup, wrap it in a custom `TooManyRequestsException`, and configure Resilience4j Retry to use `waitDuration=60s` when that specific exception is thrown, with normal backoff for other exceptions. Use two Retry configs: a `Http429Retry` (1 attempt, 60s wait) wrapping an outer `DefaultRetry` (3 attempts, exponential).

---

## Code Examples

Verified patterns from official sources:

### Jsoup newSession with POST (CAC/SCP pattern)
```java
// Source: https://jsoup.org/cookbook/web/request-session [CITED]
Connection session = Jsoup.newSession()
    .timeout(45 * 1000)
    .maxBodySize(5 * 1024 * 1024);

Document formPage = session.newRequest("https://portal.tjsp.jus.br/cac/scp/form")
    .get();

String viewState = formPage.select("input[name=__VIEWSTATE]").attr("value");
String viewStateGen = formPage.select("input[name=__VIEWSTATEGENERATOR]").attr("value");
String eventValidation = formPage.select("input[name=__EVENTVALIDATION]").attr("value");

Document result = session.newRequest("https://portal.tjsp.jus.br/cac/scp/form")
    .data("__VIEWSTATE", viewState)
    .data("__VIEWSTATEGENERATOR", viewStateGen)
    .data("__EVENTVALIDATION", eventValidation)
    .data("numeroPrecatorio", "0001234-56.2023.8.26.0100")
    .post();
```

### DataJud API call (confirmed format)
```bash
# Source: https://datajud-wiki.cnj.jus.br/api-publica/acesso/ [VERIFIED]
curl -X POST "https://api-publica.datajud.cnj.jus.br/api_publica_tjsp/_search" \
  -H "Authorization: APIKey cDZHYzlZa0JadVREZDJCendQbXY6SkJlTzNjLV9TRENyQk1RdnFKZGRQdw==" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "match": {
        "numeroProcesso": "0001234-56.2023.8.26.0100"
      }
    }
  }'
```

### Resilience4j RateLimiter configuration (1 req/2s)
```yaml
# Source: https://resilience4j.readme.io/docs/getting-started-3 [CITED]
resilience4j:
  ratelimiter:
    instances:
      esaj:
        limit-for-period: 1
        limit-refresh-period: 2s
        timeout-duration: 5s
```

### Resilience4j Retry with exponential backoff
```yaml
# Source: https://resilience4j.readme.io/docs/getting-started-3 [CITED]
resilience4j:
  retry:
    instances:
      esaj:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2.0
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `retryBackoff()` on Reactor Mono | `Retry.backoff()` with `retryWhen()` | Reactor 3.4+ | Existing code using `retryBackoff` must migrate |
| Jsoup blocking HTTP only | Jsoup `newSession()` for multi-request sessions | Jsoup 1.14+ | Sessions are the correct API for stateful scraping |
| HtmlUnit for JS-heavy pages | HtmlUnit 4.x (major rewrite) | HtmlUnit 4.0 (2023) | API changed significantly; do not use HtmlUnit < 4 |
| `resilience4j-spring-boot2` | `resilience4j-spring-boot3` | Spring Boot 3.0 | Different artifact ID — must use the `-boot3` variant |

**Deprecated/outdated:**
- `resilience4j-spring-boot2`: Use `resilience4j-spring-boot3:2.3.0` for Spring Boot 3.x

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | e-SAJ CSS selector `#classeProcesso` extracts the process class field | Architecture Patterns (EsajSelectors) | All process class data returns null; smoke test will catch this |
| A2 | e-SAJ CSS selector `#tablePartesPrincipais` contains the party rows | Architecture Patterns (EsajSelectors) | No party data extracted; fundamental to ESAJ-02 |
| A3 | e-SAJ CSS selector `.nomeParteEAdvogado` extracts party name within the parties table | Architecture Patterns (EsajSelectors) | Party names all null; blocks ESAJ-02 |
| A4 | e-SAJ CSS selector `#incidentes` identifies the incidents table | Architecture Patterns (EsajSelectors) | No incident/precatorio links extracted; blocks ESAJ-03 |
| A5 | CAC/SCP result container uses CSS class `.resultadoPesquisa` for blank-form detection | Architecture Patterns (CacScraper) | Blank-form detection fails; session renewal loop or missed renewal |
| A6 | CAC/SCP form field for precatório number input is named `numeroPrecatorio` | Architecture Patterns (CacScraper) | POST returns no results despite correct ViewState |
| A7 | DataJud field for IBGE municipality code is named `codigoMunicipioIBGE` (for DATJ-02) | Phase Requirements table | Municipality-based DataJud queries return no results |
| A8 | `spring-boot-starter-aop` is not yet in `build.gradle.kts` and needs to be added | Standard Stack | If already transitive from another starter, adding explicitly is harmless |

**All A1–A6 are invalidated or confirmed by live validation spikes in Plans 02-01 and 02-02. These must be confirmed before writing full extraction logic.**

---

## Open Questions

1. **e-SAJ selector names for 2026**
   - What we know: Community scrapers from 2022-2024 used IDs like `#classeProcesso`, `#foroProcesso`, `.nomeParteEAdvogado`
   - What's unclear: TJ-SP may have updated its HTML since these were documented
   - Recommendation: Plan 02-01 Wave 0 must include a live spike that dumps the raw HTML of a known process number and confirms selector names before writing extraction logic

2. **CAC/SCP exact URL and form structure**
   - What we know: Base URL is `https://www.tjsp.jus.br/cac/scp` per application.yml; it uses ASP.NET ViewState; `__VIEWSTATE`, `__VIEWSTATEGENERATOR`, `__EVENTVALIDATION` are standard ASP.NET hidden fields
   - What's unclear: The exact URL path for the precatório search form, the input field name for precatório number, what CSS selectors identify the result fields
   - Recommendation: Plan 02-02 must start with a live spike against the CAC/SCP portal to map the form structure before writing the full scraper

3. **HTTP 429 pause strategy with Resilience4j**
   - What we know: ESAJ-07 requires 60s pause on HTTP 429; standard Resilience4j Retry uses one waitDuration for all retries
   - What's unclear: Best Resilience4j pattern for status-code-conditional wait times
   - Recommendation: Use a custom `retryOnResult` predicate that throws a `TooManyRequestsException` on 429, then configure a second Retry instance with 60s waitDuration for that specific exception type

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (via `spring-boot-starter-test`, BOM-managed) + MockK 1.14.3 |
| Config file | No separate config — Gradle `useJUnitPlatform()` in `build.gradle.kts` |
| Quick run command | `./gradlew test --tests "br.com.precatorios.scraper.*" -x integrationTest` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ESAJ-01 | Jsoup fetches process page; returns ProcessoScraped with numero set | unit (mock HTTP) | `./gradlew test --tests "*EsajScraperTest*"` | ❌ Wave 0 |
| ESAJ-02 | Parties extracted from HTML fixture | unit (static HTML) | `./gradlew test --tests "*EsajScraperTest*partes*"` | ❌ Wave 0 |
| ESAJ-03 | Incidents extracted from HTML fixture | unit (static HTML) | `./gradlew test --tests "*EsajScraperTest*incidentes*"` | ❌ Wave 0 |
| ESAJ-04 | Search method returns list of results from HTML fixture | unit (static HTML) | `./gradlew test --tests "*EsajScraperTest*buscar*"` | ❌ Wave 0 |
| ESAJ-05 | Rate limiter bean has limitForPeriod=1, limitRefreshPeriod=2s | unit (config check) | `./gradlew test --tests "*ResilienceConfigTest*"` | ❌ Wave 0 |
| ESAJ-06/07 | Retry bean has maxAttempts=3, exponential backoff | unit (config check) | `./gradlew test --tests "*ResilienceConfigTest*"` | ❌ Wave 0 |
| CAC-01 | CacScraper returns PrecatorioScraped with non-null fields | smoke (live) | Manual / live spike in Plan 02-02 | ❌ Wave 0 |
| CAC-02 | ViewState extraction from HTML fixture; POST includes all three fields | unit (static HTML) | `./gradlew test --tests "*CacScraperTest*viewstate*"` | ❌ Wave 0 |
| CAC-03 | CAC rate limiter config matches e-SAJ config | unit (config check) | `./gradlew test --tests "*ResilienceConfigTest*"` | ❌ Wave 0 |
| DATJ-01 | WebClient POST sends correct query body and Authorization header | unit (WireMock or MockWebServer) | `./gradlew test --tests "*DataJudClientTest*"` | ❌ Wave 0 |
| DATJ-02 | Municipality query uses correct field name | unit (verify query body) | `./gradlew test --tests "*DataJudClientTest*municipio*"` | ❌ Wave 0 |
| DATJ-03 | API key from config injected into Authorization header | unit | `./gradlew test --tests "*DataJudClientTest*apikey*"` | ❌ Wave 0 |
| API-06 | GET /api/v1/processo/{numero} returns 200 with ProcessoResponseDTO | integration (MockMvc + MockK) | `./gradlew test --tests "*ProcessoControllerTest*"` | ❌ Wave 0 |
| API-07 | GET /api/v1/processo/buscar?nome= returns list | integration (MockMvc + MockK) | `./gradlew test --tests "*ProcessoControllerTest*buscar*"` | ❌ Wave 0 |
| API-08 | GET /api/v1/precatorio/{numero} returns 200 | integration (MockMvc + MockK) | `./gradlew test --tests "*PrecatorioControllerTest*"` | ❌ Wave 0 |
| API-09 | POST /api/v1/datajud/buscar returns proxied results | integration (MockMvc + MockK) | `./gradlew test --tests "*DataJudControllerTest*"` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "br.com.precatorios.scraper.*" --tests "br.com.precatorios.controller.*" -x :test --no-daemon` (unit tests only, skip Testcontainers)
- **Per wave merge:** `./gradlew test` (full suite)
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

All test files are missing — Phase 1 tests cover infrastructure only. Phase 2 must create:

- [ ] `src/test/kotlin/br/com/precatorios/scraper/EsajScraperTest.kt` — unit tests with HTML fixtures
- [ ] `src/test/kotlin/br/com/precatorios/scraper/CacScraperTest.kt` — ViewState extraction tests
- [ ] `src/test/kotlin/br/com/precatorios/scraper/DataJudClientTest.kt` — MockWebServer-based tests
- [ ] `src/test/kotlin/br/com/precatorios/config/ResilienceConfigTest.kt` — RateLimiter/Retry config assertions
- [ ] `src/test/kotlin/br/com/precatorios/controller/ProcessoControllerTest.kt` — MockMvc + MockK
- [ ] `src/test/kotlin/br/com/precatorios/controller/PrecatorioControllerTest.kt` — MockMvc + MockK
- [ ] `src/test/kotlin/br/com/precatorios/controller/DataJudControllerTest.kt` — MockMvc + MockK
- [ ] `src/test/resources/fixtures/esaj_processo.html` — captured HTML from live smoke test
- [ ] `src/test/resources/fixtures/esaj_busca.html` — captured HTML from search results

**Note:** HTML fixtures must be captured during the live validation spike (Plan 02-01), not fabricated. Fabricated fixtures would test against non-existent HTML structure.

---

## Security Domain

> `security_enforcement` is not set to false in config.json — treating as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | No user auth in this phase |
| V3 Session Management | Partial | CAC/SCP Jsoup session is internal only, not user-facing |
| V4 Access Control | No | No access control logic in this phase |
| V5 Input Validation | Yes | Process numbers and search terms from HTTP request parameters must be validated before passing to scraper URLs |
| V6 Cryptography | No | No crypto in scraping layer |

### Known Threat Patterns for Scraping + REST Layer

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Unvalidated process number injected into scraper URL | Tampering | `@Pattern(regexp = "^[0-9]{7}-[0-9]{2}\\.[0-9]{4}\\.[0-9]\\.[0-9]{2}\\.[0-9]{4}$")` on `@PathVariable` or `@RequestParam` |
| Unconstrained `?nome=` search parameter causing excessive scraping | Tampering/DoS | Minimum length (`@Size(min = 3)`) on search term; Resilience4j rate limiting at the scraper level |
| DataJud API key exposed in error responses | Information Disclosure | `GlobalExceptionHandler` must not propagate cause messages that contain the API key; scraper internals must not leak to HTTP response body |
| SSRF via user-controlled URL parameter | Tampering | No URL parameters accepted from user — all URLs are constructed from validated process numbers against fixed base URLs in application.yml |

---

## Sources

### Primary (HIGH confidence)
- [Jsoup newSession docs](https://jsoup.org/cookbook/web/request-session) — session API, cookie store, newRequest()
- [Jsoup Connection API](https://jsoup.org/apidocs/org/jsoup/Connection.html) — data(), post(), get(), timeout()
- [Resilience4j Spring Boot 3 Getting Started](https://resilience4j.readme.io/docs/getting-started-3) — dependency coordinates, application.yml configuration format
- [Resilience4j RateLimiter](https://resilience4j.readme.io/docs/ratelimiter) — programmatic API, limitForPeriod, limitRefreshPeriod
- [DataJud API Acesso](https://datajud-wiki.cnj.jus.br/api-publica/acesso/) — `Authorization: APIKey {key}` header confirmed
- [DataJud API Endpoints](https://datajud-wiki.cnj.jus.br/api-publica/endpoints/) — `api_publica_tjsp/_search` endpoint confirmed
- Maven Central `io.github.resilience4j:resilience4j-spring-boot3` — version 2.3.0 verified 2026-04-05

### Secondary (MEDIUM confidence)
- [ASP.NET ViewState scraping pattern](https://www.trickster.dev/post/scraping-legacy-asp-net-site-with-scrapy-a-real-example/) — `__VIEWSTATE`, `__VIEWSTATEGENERATOR`, `__EVENTVALIDATION` field names; extract-and-resubmit unchanged pattern
- [jespimentel/esaj_2_grau](https://github.com/jespimentel/esaj_2_grau) — e-SAJ selector hypotheses: `#orgaoJulgadorProcesso`, `.nomeParteEAdvogado`, `#classeProcesso`; URL pattern `cpopg/search.do`
- [Baeldung Spring Boot Resilience4j](https://www.baeldung.com/spring-boot-resilience4j) — Spring Boot 3 integration patterns

### Tertiary (LOW confidence — needs live validation)
- e-SAJ CSS selector names (A1–A4 in Assumptions Log) — based on community scraper code from 2022–2024; TJ-SP HTML may have changed
- CAC/SCP form field names and result selectors (A5–A6) — no primary source; must be confirmed live

---

## Metadata

**Confidence breakdown:**
- Standard stack (Jsoup, WebClient, Resilience4j versions): HIGH — verified against Maven Central and official docs
- Architecture patterns (Resilience4j, session management): HIGH — confirmed from official documentation
- e-SAJ CSS selectors: LOW — community scrapers, not verified against current TJ-SP HTML
- CAC/SCP form field names: LOW — no primary source, must be validated live
- DataJud API format: HIGH — confirmed against official CNJ documentation
- Controller/DTO structure: HIGH — standard Spring MVC patterns

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 for stack versions; e-SAJ/CAC selectors must be revalidated before each scraper implementation regardless of age
