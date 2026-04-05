# Technology Stack

**Project:** Precatórios API — TJ-SP Lead Prospecting
**Researched:** 2026-04-03
**Overall confidence:** HIGH (verified against official sources and changelogs)

---

## Version Verdict: SPEC Needs Updates

The SPEC specifies Spring Boot 3.3.0 and Kotlin 2.0.0 — both are significantly outdated as of April 2026. Use the versions below.

---

## Recommended Stack

### Core Framework

| Technology | SPEC Version | Recommended Version | Why |
|------------|-------------|--------------------|----|
| Kotlin | 2.0.0 | **2.3.x (2.3.20)** | Spring Boot 3.5 requires Kotlin 2.2+ minimum; 2.3.20 is current stable with performance and compiler improvements. Spring Boot bundles 2.3.10 in its BOM. |
| Spring Boot | 3.3.0 | **3.5.x (3.5.9)** | 3.3.x is end-of-life. 3.5.9 is the current stable minor in the 3.x line (3.4.x also reached end of open source support Dec 2025). JUnit 5.12, Mockito 5.17, virtual thread improvements bundled. |
| JVM | 21 | **21 (unchanged)** | JVM 21 is the LTS target for Spring Boot 3.x. No reason to change for this project. |
| Gradle Kotlin DSL | 1.1.5 plugin | Current via Boot BOM | Spring Boot 3.5 uses `io.spring.dependency-management` 1.1.x — use whatever version Boot 3.5 ships. |

**Confidence: HIGH** — verified via spring.io/blog release announcements and kotlinlang.org release docs.

---

### Scraping Layer

#### HTML Scraping: Jsoup

| Technology | SPEC Version | Recommended Version | Why |
|------------|-------------|--------------------|----|
| Jsoup | 1.18.1 | **1.22.1** | Released January 2026. Adds HTTP/2 support by default (Java 11+), improved HTML parse rules aligned with modern browsers, parser thread-safety improvements, concise CSS selectors, and `TagSet` API for custom tag definitions. Use `1.22.1` — meaningfully better than the SPEC's 1.18.1. |

**Confidence: HIGH** — verified via jsoup.org/news official release page.

**Jsoup is the right choice for e-SAJ.** The e-SAJ portal serves static HTML — no JavaScript rendering required. Jsoup's native Java implementation is faster and lighter than any headless browser alternative. The 1.22.1 HTTP/2 support also reduces connection overhead when making many requests.

**Resilient Jsoup patterns for this project:**

1. **Null-safe selector access** — always use `selectFirst()` returning nullable, never assume elements exist:
   ```kotlin
   val nome = doc.selectFirst("#tablePartesPrincipais td.nomeParteEsquerda")?.text() ?: ""
   ```

2. **Multiple selector fallbacks** — the e-SAJ HTML varies between process types; use ordered fallbacks:
   ```kotlin
   val partes = doc.select("#tablePartesPrincipais tr")
       .takeIf { it.isNotEmpty() }
       ?: doc.select("#tableTodasPartes tr")
   ```

3. **Centralize all selectors as constants** — the SPEC already recommends this. Changes to TJ-SP HTML only require updating one file, not hunting through scraper code.

4. **Session management for CAC/SCP** — use `Jsoup.newSession()` (added in 1.14.x, stable in 1.22.1) which maintains a cookie jar automatically across requests. This is the preferred approach for the ASP.NET ViewState portal before reaching for HtmlUnit.

#### ASP.NET ViewState (CAC/SCP Portal): Jsoup First, HtmlUnit Fallback

The CAC/SCP portal at `https://www.tjsp.jus.br/cac/scp/` uses ASP.NET WebForms with `__VIEWSTATE`, `__VIEWSTATEGENERATOR`, and `__EVENTVALIDATION` hidden fields.

**Recommended approach — Jsoup with explicit ViewState extraction (start here):**

The pattern is straightforward and does NOT require a headless browser:
1. `GET /webmenupesquisa.aspx` — obtain the page, extract hidden fields
2. Extract `__VIEWSTATE`, `__VIEWSTATEGENERATOR`, `__EVENTVALIDATION` from `<input type="hidden">` elements
3. `POST /pesquisainternetv2.aspx` with extracted values included as form data fields
4. Jsoup's session cookie jar handles `ASP.NET_SessionId` automatically

```kotlin
val session = Jsoup.newSession()
    .userAgent(USER_AGENT)
    .timeout(30_000)

// Step 1: GET to obtain ViewState
val menuPage = session.newRequest("https://www.tjsp.jus.br/cac/scp/webmenupesquisa.aspx").get()
val viewState = menuPage.selectFirst("input[name=__VIEWSTATE]")?.`val`() ?: ""
val eventValidation = menuPage.selectFirst("input[name=__EVENTVALIDATION]")?.`val`() ?: ""

// Step 2: POST with ViewState
val result = session.newRequest("https://www.tjsp.jus.br/cac/scp/pesquisainternetv2.aspx")
    .data("__VIEWSTATE", viewState)
    .data("__EVENTVALIDATION", eventValidation)
    .data("txtnumeroProcesso", processNumber)
    .post()
```

**HtmlUnit as fallback (4.21.0):**

| Technology | When to Use | Version | Coordinates |
|------------|-------------|---------|-------------|
| HtmlUnit | If CAC/SCP uses JavaScript-driven page events that break the Jsoup POST approach | **4.21.0** | `org.htmlunit:htmlunit:4.21.0` |

**Important:** HtmlUnit changed Maven coordinates in Spring Boot 3.4+. The old `net.sourceforge.htmlunit:htmlunit` no longer applies — the new group is `org.htmlunit:htmlunit`. Package names also changed from `com.gargoylesoftware.htmlunit` to `org.htmlunit`.

**Do NOT use Selenium/Playwright.** The SPEC constraint is correct — the portals are standard form-based HTML, not complex SPAs. Adding a browser binary to the Docker image increases complexity and memory footprint for no gain.

**Confidence: MEDIUM** — Jsoup ViewState pattern is well-established; HtmlUnit coordinates verified via Spring Boot release notes. CAC/SCP portal behavior under this approach requires validation with live requests.

---

### Async Execution

**Recommendation: Kotlin coroutines with Spring's suspend function support — NOT `@Async`.**

| Approach | Recommendation | Rationale |
|----------|---------------|-----------|
| Kotlin coroutines (`suspend` + `CoroutineScope`) | **Use this** | Native Kotlin, readable sequential code for what is inherently sequential (scrape → parse → scrape next), structured cancellation, built-in delay/retry primitives |
| `@Async` with `ThreadPoolTaskExecutor` | Avoid for new Kotlin code | Java-centric, requires futures/callbacks, loses type safety, no structured concurrency |
| JVM Virtual Threads (Project Loom) | Do not mix with coroutines | Virtual threads are a JVM scheduler concern; mixing with Kotlin coroutines is redundant and can cause subtle dispatcher interaction issues |

**Spring Boot 3.5 coroutine support:**
- `suspend` functions in `@RestController` are supported natively — Spring MVC/WebFlux detect suspend and bridge to coroutines
- `@Service` beans can be `suspend` functions called from coroutines
- For the prospection background job: launch in a `CoroutineScope` with `Dispatchers.IO` (blocking I/O bound work)

**Pattern for prospection endpoint (202 Accepted + polling):**

```kotlin
@RestController
class ProspeccaoController(
    private val prospeccaoService: ProspeccaoService,
    private val scope: CoroutineScope  // injected application-scoped
) {
    @PostMapping("/api/v1/prospeccao")
    suspend fun iniciar(@RequestBody request: ProspeccaoRequest): ResponseEntity<ProspeccaoResponse> {
        val prospeccao = prospeccaoService.criar(request)
        scope.launch(Dispatchers.IO) { prospeccaoService.executar(prospeccao.id) }
        return ResponseEntity.accepted().body(prospeccaoResponse(prospeccao))
    }
}
```

**Key coroutines dependencies (already in SPEC, versions to update):**

```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.x")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.x")
```

Spring Boot 3.5 BOM manages the coroutines version — do not pin manually unless there is a specific reason.

**Confidence: HIGH** — Spring Framework official docs confirm suspend function support in `@Controller`; coroutine vs @Async comparison verified across multiple sources.

---

### Database

| Technology | SPEC Version | Recommended Version | Why |
|------------|-------------|--------------------|----|
| PostgreSQL (Docker) | 16-alpine | **17-alpine** | PostgreSQL 17 is current stable, released September 2024. Better JSON/JSONB performance matters here given `dados_brutos JSONB` columns. |
| Spring Data JPA / Hibernate | via Spring Boot | via Spring Boot 3.5 BOM | No manual version pinning needed. Spring Boot 3.5 ships with Hibernate 6.6.x which handles the JSONB columns well. |
| Flyway | via Spring Boot | **11.x** (via Spring Boot 3.5 BOM) | Spring Boot 3.5 manages Flyway 11.x. Requires both `flyway-core` AND `flyway-database-postgresql` — the SPEC correctly includes both. The separate PostgreSQL module is required since Flyway 10+. |
| PostgreSQL JDBC Driver | via Spring Boot | via Spring Boot 3.5 BOM | No manual version needed. |

**Confidence: HIGH** — PostgreSQL release schedule verified; Flyway modular structure verified via mvnrepository.

---

### Cache

| Technology | SPEC Version | Recommended Version | Why |
|------------|-------------|--------------------|----|
| Caffeine | via Spring Boot BOM | via Spring Boot 3.5 BOM | Spring Boot manages the Caffeine version. `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine` is the correct minimal setup. |

**Caffeine configuration pattern for this project:**

The project needs two distinct TTL behaviors:
- **Scraping cache** (processo/precatório data): 24h TTL — data changes infrequently
- **Search results cache** (busca por nome): shorter, 1h TTL — search results can drift

Use programmatic `CacheManager` (not `spring.cache.caffeine.spec`) to configure per-cache TTL:

```kotlin
@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager()
        cacheManager.setCacheLoader(caffeineLoader())
        return cacheManager
    }

    // Per-cache specs for different TTLs
    @Bean
    fun processoCacheSpec(): Caffeine<Any, Any> =
        Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(1_000)

    @Bean
    fun searchCacheSpec(): Caffeine<Any, Any> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(500)
}
```

For truly independent TTLs per cache name, build a `SimpleCacheManager` with individual `CaffeineCache` instances rather than `CaffeineCacheManager`.

Cache key for processo: the process number string (e.g., `"1234567-89.2020.8.26.0053"`).
Cache key for precatório: the precatório number string.

**Confidence: HIGH** — Caffeine + Spring Cache integration is well-established; patterns verified via Spring Boot documentation and community sources.

---

### REST/HTTP Client

| Technology | Purpose | Recommendation |
|------------|---------|---------------|
| Spring WebClient (WebFlux) | External HTTP calls to DataJud API | Keep as specified. WebFlux is already a dependency for WebClient. Use with coroutines bridge via `.awaitBody()`. |
| Jsoup `Connection` (session) | Scraping e-SAJ and CAC/SCP | Built into Jsoup — no separate HTTP client needed for scraping. Jsoup 1.22.1 uses Java `HttpClient` internally (HTTP/2). |
| Spring `RestClient` | DataJud JSON API (alternative) | Spring Boot 3.5 ships RestClient as the synchronous successor to RestTemplate. For DataJud, either WebClient+coroutines or RestClient+`withContext(Dispatchers.IO)` works. WebClient is already pulled in for the SPEC. |

**Do NOT add OkHttp as a separate dependency.** The SPEC already has WebClient + Jsoup's built-in client. Adding OkHttp is redundant for this project.

**Confidence: HIGH** — WebClient coroutine bridge (`.awaitBody()`, `.awaitBodilessEntity()`) is in the official Spring Framework docs.

---

### API Documentation

| Technology | SPEC Version | Recommended Version | Why |
|------------|-------------|--------------------|----|
| SpringDoc OpenAPI | 2.5.0 | **2.8.x (2.8.16)** | Latest stable for Spring Boot 3.x. The 2.5.0 in the SPEC is outdated. Version 3.x is for Spring Boot 4.x only — do not use 3.x with Spring Boot 3.5. |

**Coordinates (unchanged):** `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.16`

**Confidence: HIGH** — verified via springdoc.org official site.

---

### Testing Stack

| Technology | SPEC Version | Recommended Version | Why |
|------------|-------------|--------------------|----|
| JUnit 5 (Jupiter) | via Spring Boot | via Spring Boot 3.5 BOM (5.12.x) | No manual version needed. |
| MockK | 1.13.10 | **1.14.5** | Released July 2025. Full coroutine support, Kotlin 2.x compatible. The SPEC's 1.13.10 predates several Kotlin 2.x fixes. |
| Testcontainers | 1.19.8 | **2.0.4** | Major version released October 2025. Breaking changes: module prefix changed (`testcontainers-postgresql` instead of `postgresql`), class package changed (`org.testcontainers.postgresql.PostgreSQLContainer`), JUnit 4 removed. Spring Boot 3.5 supports TC 2.0. |

**Testcontainers 2.0 migration — dependency change:**

```kotlin
// OLD (SPEC) — do not use
testImplementation("org.testcontainers:postgresql:1.19.8")
testImplementation("org.testcontainers:junit-jupiter:1.19.8")

// NEW (Testcontainers 2.0)
testImplementation("org.testcontainers:testcontainers-postgresql:2.0.4")
testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.4")
```

**Class import change:**
```kotlin
// OLD
import org.testcontainers.containers.PostgreSQLContainer
// NEW
import org.testcontainers.postgresql.PostgreSQLContainer
```

**Spring Boot 3.1+ Testcontainers integration (use this):**

Spring Boot 3.1 introduced `@ServiceConnection` which auto-configures the datasource from a running container — no manual property override required:

```kotlin
@SpringBootTest
class IntegrationTest {
    companion object {
        @Container
        @JvmStatic
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
```

**MockK patterns for this project:**

```kotlin
// Coroutine-aware mocking (critical for suspend functions)
@Test
fun `should score lead correctly`() = runTest {
    val mockScraper = mockk<EsajScraper>()
    coEvery { mockScraper.extrairDados(any()) } returns mockProcesso()

    val service = ProspeccaoService(mockScraper, ...)
    val result = service.executar("1234567-89.2020.8.26.0053")

    coVerify { mockScraper.extrairDados("1234567-89.2020.8.26.0053") }
}
```

Use `coEvery`/`coVerify` for `suspend` functions. Use `every`/`verify` for regular functions. Do not mix.

**Confidence: HIGH for MockK version** (verified via github.com/mockk/mockk releases). **HIGH for Testcontainers 2.0** (verified via GitHub releases + Spring Boot issue tracker). **MEDIUM for TC 2.0 with Spring Boot 3.5** — Spring Boot 3.5 is confirmed compatible per community reports, but Spring Boot 4.0 is the primary target for TC 2.0 testing.

---

## Full Dependency Block (Updated build.gradle.kts)

```kotlin
plugins {
    id("org.springframework.boot") version "3.5.9"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.spring") version "2.3.20"
    kotlin("plugin.jpa") version "2.3.20"
}

group = "br.com.precatorios"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webflux")   // WebClient + coroutines bridge
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")       // Needed for WebFlux bridge

    // Scraping — HTML
    implementation("org.jsoup:jsoup:1.22.1")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")                // Required separately since Flyway 10+

    // Cache
    implementation("com.github.ben-manes.caffeine:caffeine")

    // API Docs — 2.x for Spring Boot 3.x (NOT 3.x which is for Spring Boot 4)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.16")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.14.5")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.4")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.4")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")  // @ServiceConnection support
}
```

---

## Alternatives Considered and Rejected

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| HTML scraping | Jsoup 1.22.1 | Playwright/Selenium | e-SAJ is static HTML. Browser binary adds 100+ MB to Docker image, complex setup, much slower. Not needed. |
| HTML scraping | Jsoup 1.22.1 | HtmlUnit (primary) | HtmlUnit is the fallback for CAC/SCP only if Jsoup ViewState extraction fails. Use Jsoup first — it handles ViewState fine as a form POST library. |
| Async | Kotlin coroutines | `@Async` + `CompletableFuture` | Java-centric pattern. No structured concurrency, no automatic cancellation, harder to read in Kotlin. Coroutines are idiomatic and first-class in Spring Boot 3.x. |
| Async | Kotlin coroutines | JVM Virtual Threads | Virtual threads and coroutines solve different problems; mixing them creates dispatcher confusion. Coroutines are already the right Kotlin abstraction. |
| Cache | Caffeine | Redis | Single-instance deployment (Docker Compose). Redis adds operational overhead (another container, connection pool, serialization). Caffeine with 24h TTL and `maximumSize(1000)` is sufficient for v1. |
| API Docs | SpringDoc 2.8.x | SpringDoc 3.x | SpringDoc 3.x targets Spring Boot 4.x only. Incompatible with this project's Spring Boot 3.5. |
| ORM | Spring Data JPA | R2DBC (reactive) | R2DBC is correct if the entire stack is reactive WebFlux. This project uses Spring MVC (starter-web) + coroutines for the async prospection — JPA is appropriate and simpler. Switching to R2DBC would require removing Hibernate entirely and rewriting all repositories. |
| HTTP client | WebClient + Jsoup | OkHttp | OkHttp is redundant. Jsoup 1.22.1 uses Java's HttpClient internally; WebClient handles DataJud. No third HTTP client needed. |
| Testing: mock | MockK | Mockito | Mockito requires `open` classes in Kotlin (or additional plugins) and doesn't understand coroutines natively. MockK is Kotlin-first with `coEvery`/`coVerify` for suspend functions. |

---

## Confidence Assessment

| Area | Confidence | Basis |
|------|------------|-------|
| Spring Boot version (3.5.9) | HIGH | spring.io/blog official release announcements |
| Kotlin version (2.3.20) | HIGH | kotlinlang.org official release docs, Spring Boot BOM confirmed 2.3.x support |
| Jsoup version (1.22.1) | HIGH | jsoup.org/news official release page |
| Jsoup ViewState pattern | MEDIUM | Well-established pattern with Jsoup session API; live CAC/SCP validation required |
| Coroutines vs @Async | HIGH | Spring Framework official docs, Kotlin discussions, multiple verified sources |
| Caffeine configuration | HIGH | Spring Boot docs + community patterns; multiple TTL approach widely verified |
| Testcontainers 2.0 | HIGH | Official GitHub releases + Spring Boot issue tracker |
| MockK 1.14.5 | HIGH | Official GitHub releases page |
| SpringDoc 2.8.16 | HIGH | springdoc.org official site |
| HtmlUnit 4.21.0 as fallback | HIGH for version, MEDIUM for need | Spring Boot release notes confirm version; need depends on CAC/SCP runtime behavior |

---

## Sources

- Spring Boot 3.5 release: https://spring.io/blog/2025/05/22/spring-boot-3-5-0-available-now/
- Spring Boot 3.5 Release Notes: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.5-Release-Notes
- Spring Boot endoflife: https://endoflife.date/spring-boot
- Kotlin 2.3.0 release: https://blog.jetbrains.com/kotlin/2025/12/kotlin-2-3-0-released/
- Kotlin releases: https://kotlinlang.org/docs/releases.html
- jsoup news page: https://jsoup.org/news/ (1.22.1 released January 2026)
- jsoup session API: https://jsoup.org/cookbook/web/request-session
- Spring coroutines docs: https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html
- Spring Data JPA coroutines: https://docs.spring.io/spring-data/jpa/reference/data-commons/kotlin/coroutines.html
- HtmlUnit coordinate change in Spring Boot 3.4: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.4-Release-Notes
- Testcontainers 2.0 releases: https://github.com/testcontainers/testcontainers-java/releases
- Testcontainers migration guide: https://docs.openrewrite.org/recipes/java/testing/testcontainers/testcontainers2migration
- MockK releases: https://github.com/mockk/mockk/releases
- SpringDoc OpenAPI: https://springdoc.org/
- Spring Boot next level Kotlin support: https://spring.io/blog/2025/12/18/next-level-kotlin-support-in-spring-boot-4/
