# Phase 03: Cache and Scoring — Research

**Researched:** 2026-04-06
**Domain:** Spring Cache (Caffeine) + configurable scoring engine (Kotlin/Spring Boot 3.5)
**Confidence:** HIGH

## Summary

Phase 3 adds two orthogonal capabilities: a Caffeine read-through cache wrapping three specific scraper methods, and a stateless scoring engine driven entirely by `application.yml` configuration. All dependencies (Caffeine 3.2.1, spring-boot-starter-cache 3.5.3) are already declared in `build.gradle.kts` — no new library additions are needed. `@EnableCaching` has not yet been placed on any class; that is the first gate this phase must open.

The scoring engine design (five criteria, flat `scoreDetalhes` map, null for missing data) is fully locked by CONTEXT.md decisions D-01 through D-15. The `@ConfigurationProperties` pattern to follow is already validated in `ScraperProperties` / `application.yml`. ScoringService is a pure function with no side effects, making it straightforward to unit test with plain JUnit 5 + MockK — no Spring context needed.

The primary risk is the `@Cacheable` interaction with Resilience4j programmatic decoration. Spring Cache uses a proxy, so `@Cacheable` must be on the public interface method (e.g., `fetchProcesso`), not the internal `doFetchProcesso` helper — otherwise the proxy is bypassed. Verified patterns and pitfalls are documented below.

**Primary recommendation:** Enable caching via a dedicated `CacheConfig @Configuration` class, wire three Caffeine caches programmatically (so each cache gets its own TTL and max-size), and annotate the three public scraper methods with `@Cacheable`. Implement `ScoringService` as a pure `@Service` reading a `@ConfigurationProperties`-bound `ScoringProperties` — no Spring context required in unit tests.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Precatorio value criterion (30pts) uses tiered brackets configurable in application.yml (e.g., >R$1M = 30pts, R$500k-1M = 22pts, R$150k-500k = 15pts, R$50k-150k = 8pts, <R$50k = 3pts)
- **D-02:** Debtor entity criterion (25pts) uses an entity whitelist map in application.yml — specific entity names mapped to scores (e.g., 'Fazenda do Estado de SP' = 25pts, 'Prefeitura de SP' = 20pts, unknown = 5pts)
- **D-03:** Payment status criterion (20pts) uses a status keyword map in application.yml — each CAC/SCP status string mapped to a score (e.g., PENDENTE=20, EM PROCESSAMENTO=15, PARCIALMENTE PAGO=10, PAGO=0)
- **D-04:** Chronological position criterion (15pts) uses tiered brackets in application.yml (e.g., 1-100=15pts, 101-500=12pts, 501-1000=8pts, 1001-5000=4pts, >5000=1pt). Lower position = closer to payment = higher score
- **D-05:** Nature criterion (10pts) uses alimentar/comum binary — Alimentar=10pts, Comum=4pts, configurable in application.yml
- **D-06:** All scoring weights AND thresholds/maps are fully configurable via application.yml using @ConfigurationProperties (ScoringProperties), following the ScraperProperties pattern from Phase 2
- **D-07:** @Cacheable applied to primary fetch methods only: EsajScraper.fetchProcesso(), CacScraper.fetchPrecatorio(), DataJudClient.buscarPorNumeroProcesso()
- **D-08:** buscarPorNome (e-SAJ name search) is NOT cached — search results change over time and BFS needs fresh results per run
- **D-09:** buscarPorMunicipio (DataJud) is NOT cached — same rationale as name search
- **D-10:** Three named Caffeine caches: processos, precatorios, datajud — each with 24h TTL, `unless = "#result == null"` to prevent negative caching
- **D-11:** scoreDetalhes JSON uses a flat map structure: `{"valor": 22, "entidadeDevedora": 25, "statusPagamento": 15, "posicaoCronologica": 8, "natureza": 10, "total": 80}`
- **D-12:** Criterion keys in scoreDetalhes match the Precatorio entity field names for consistency
- **D-13:** When a scoring criterion's input data is null (scraper returned partial data), that criterion scores 0 points
- **D-14:** scoreDetalhes uses null (not 0) for criteria with missing input data — distinguishes "scored 0 because data was bad" from "data was unavailable"
- **D-15:** Example: `{"valor": 22, "entidadeDevedora": 25, "statusPagamento": null, "posicaoCronologica": 8, "natureza": 10, "total": 65}` — statusPagamento was missing from scraper

### Claude's Discretion
- CacheConfig bean implementation details (Caffeine builder settings, max cache size)
- ScoringProperties nested class structure for @ConfigurationProperties binding
- Default threshold/bracket values in application.yml (exact numbers for initial deployment)
- Unit test structure for scoring weight combinations

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CACHE-01 | Scraping results cached in-memory (Caffeine) with 24h TTL to avoid redundant requests | CacheConfig bean with Caffeine builder, ExpireAfterWrite(24h), @Cacheable on three methods |
| CACHE-02 | Cache keyed by process/precatório number so identical lookups share cached result | @Cacheable key = "#numero" or "#id" on each method; default key resolves from single param |
| SCOR-01 | Score each lead 0-100 with five criteria: value (30), entity (25), status (20), position (15), nature (10) | ScoringService pure function, each criterion method returns Int; total is sum |
| SCOR-02 | Scoring weights and thresholds fully configurable via application.yml without code changes | @ConfigurationProperties(prefix = "scoring") ScoringProperties with nested threshold/map classes |
| SCOR-03 | Each lead score includes scoreDetalhes breakdown showing each criterion contribution | Return ScoredResult(total, detalhes: Map<String, Int?>) serialized to JSONB on Lead |
| SCOR-04 | Leads scoring 0 across all criteria still persisted but excluded from default lead list results | LeadRepository needs query with score > 0 filter for default listing (Phase 5 implements API, but repo method can be added here) |
</phase_requirements>

---

## Standard Stack

### Core (already in build.gradle.kts)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| com.github.ben-manes.caffeine:caffeine | 3.2.1 | In-process cache store | Industry standard JVM cache; Spring Boot's preferred provider |
| spring-boot-starter-cache | 3.5.3 | Spring cache abstraction + @Cacheable | Wires CacheManager, proxy infrastructure |
| spring-boot-starter-test | 3.5.3 (managed) | JUnit 5 + Mockito/MockK base | Already present for all test phases |
| io.mockk:mockk | 1.14.3 | Kotlin-idiomatic mocking | Already present in build |

[VERIFIED: gradle dependencyInsight output in this session — `com.github.ben-manes.caffeine:caffeine:3.2.1`, `spring-boot-starter-cache:3.5.3`]

**No new dependencies needed.** All Phase 3 libraries are already declared.

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| com.fasterxml.jackson.module:jackson-module-kotlin | managed by BOM | Serialize `scoreDetalhes` map to JSON string for JSONB column | Already used in DataJudClient; same ObjectMapper instance reusable |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Programmatic CaffeineCacheManager | application.yml `spring.cache.caffeine.spec` | Spec string loses per-cache TTL; all three caches share one TTL — not appropriate since decisions call for named caches |
| Three separate Caffeine specs | One shared TTL | All three happen to use 24h TTL so shared spec would work now, but programmatic is more explicit and extensible |

**Installation:** No new installs required.

---

## Architecture Patterns

### Recommended Project Structure

```
src/main/kotlin/br/com/precatorios/
├── config/
│   ├── CacheConfig.kt          # @Configuration — @EnableCaching + three CaffeineCache beans
│   ├── ScoringProperties.kt    # @ConfigurationProperties(prefix = "scoring")
│   ├── ScraperProperties.kt    # existing — pattern to mirror
│   └── ResilienceConfig.kt     # existing — @Bean factory pattern to mirror
├── service/
│   └── ScoringService.kt       # @Service — pure scoring function, no I/O
└── scraper/
    ├── EsajScraper.kt          # add @Cacheable("processos") to fetchProcesso()
    ├── CacScraper.kt           # add @Cacheable("precatorios") to fetchPrecatorio()
    └── DataJudClient.kt        # add @Cacheable("datajud") to buscarPorNumeroProcesso()

src/main/resources/
└── application.yml             # extend with cache: and scoring: sections

src/test/kotlin/br/com/precatorios/
├── config/
│   ├── CacheConfigTest.kt      # integration: verifies second call hits cache
│   └── ScoringPropertiesTest.kt# integration: verifies yml binding
└── service/
    └── ScoringServiceTest.kt   # unit: no Spring context, covers all five criteria
```

### Pattern 1: CacheConfig — programmatic Caffeine CacheManager

**What:** A `@Configuration @EnableCaching` class that builds a `CaffeineCacheManager` with three named caches, each backed by a `Caffeine` spec with `expireAfterWrite(24h)` and a bounded `maximumSize`.

**When to use:** When different caches need different TTL or size settings, or when you want explicit control over Caffeine builder flags.

**Example:**

```kotlin
// Source: Spring Boot 3.x docs — spring.io/guides/gs/caching
// [VERIFIED: Spring Boot 3.5 auto-configuration supports CaffeineCacheManager via bean override]
@Configuration
@EnableCaching
class CacheConfig {

    private fun buildCache(maxSize: Long, ttlHours: Long): Cache =
        CaffeineCache(
            "placeholder",   // name set via SimpleCacheManager or CaffeineCacheManager
            Caffeine.newBuilder()
                .expireAfterWrite(ttlHours, TimeUnit.HOURS)
                .maximumSize(maxSize)
                .recordStats()
                .build()
        )

    @Bean
    fun cacheManager(): CacheManager {
        val manager = SimpleCacheManager()
        manager.setCaches(listOf(
            CaffeineCache("processos",   buildCaffeine(500, 24)),
            CaffeineCache("precatorios", buildCaffeine(500, 24)),
            CaffeineCache("datajud",     buildCaffeine(1000, 24))
        ))
        return manager
    }

    private fun buildCaffeine(maxSize: Long, ttlHours: Long): com.github.ben-manes.caffeine.cache.Cache<Any, Any> =
        Caffeine.newBuilder()
            .expireAfterWrite(ttlHours, TimeUnit.HOURS)
            .maximumSize(maxSize)
            .recordStats()
            .build()
}
```

[ASSUMED — exact API surface of `CaffeineCache` constructor. The pattern of using `SimpleCacheManager` + named `CaffeineCache` instances is standard; verify import paths against Caffeine 3.2.1 at implementation time.]

**Simpler alternative (single TTL via CaffeineCacheManager):**

```kotlin
@Bean
fun cacheManager(): CacheManager {
    val manager = CaffeineCacheManager("processos", "precatorios", "datajud")
    manager.setCaffeine(
        Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(500)
            .recordStats()
    )
    return manager
}
```

[ASSUMED — `CaffeineCacheManager.setCaffeine(Caffeine)` API. All three caches share one spec here. Acceptable given decisions all specify 24h TTL.]

The simpler `CaffeineCacheManager` approach is recommended for this phase because all three caches share the same 24h TTL (D-10). If per-cache max size differs in future, switch to `SimpleCacheManager`.

### Pattern 2: @Cacheable on scraper methods

**What:** Annotate the public proxy-eligible methods. Key defaults to method argument when there is exactly one parameter.

```kotlin
// EsajScraper.kt
@Cacheable(cacheNames = ["processos"], unless = "#result == null")
fun fetchProcesso(numero: String): ProcessoScraped { ... }

// CacScraper.kt
@Cacheable(cacheNames = ["precatorios"], unless = "#result == null")
fun fetchPrecatorio(numero: String): PrecatorioScraped { ... }

// DataJudClient.kt
@Cacheable(cacheNames = ["datajud"], unless = "#result == null")
fun buscarPorNumeroProcesso(numero: String): DataJudResult { ... }
```

[CITED: https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-default-key]

The `unless` SpEL guard prevents a `null` result (e.g., scraper failure that returned null rather than throwing) from being cached. This satisfies the "no negative caching" requirement of D-10.

### Pattern 3: ScoringProperties — @ConfigurationProperties

**What:** Mirrors `ScraperProperties` exactly. Nested data classes hold tier lists (for bracketed criteria) and maps (for keyword criteria).

```kotlin
@ConfigurationProperties(prefix = "scoring")
data class ScoringProperties(
    val valor: ValorProps = ValorProps(),
    val entidadeDevedora: EntidadeDevedoraProps = EntidadeDevedoraProps(),
    val statusPagamento: StatusPagamentoProps = StatusPagamentoProps(),
    val posicaoCronologica: PosicaoProps = PosicaoProps(),
    val natureza: NaturezaProps = NaturezaProps()
) {
    data class ValorProps(
        val maxPontos: Int = 30,
        val faixas: List<FaixaValor> = emptyList()   // sorted descending by limiteInferior
    )
    data class FaixaValor(val limiteInferior: Long = 0, val pontos: Int = 0)

    data class EntidadeDevedoraProps(
        val maxPontos: Int = 25,
        val pontosDesconhecida: Int = 5,
        val mapa: Map<String, Int> = emptyMap()      // entity name -> score
    )

    data class StatusPagamentoProps(
        val maxPontos: Int = 20,
        val pontosPadrao: Int = 0,
        val mapa: Map<String, Int> = emptyMap()      // status keyword -> score
    )

    data class PosicaoProps(
        val maxPontos: Int = 15,
        val faixas: List<FaixaPosicao> = emptyList() // sorted ascending by limiteInferior
    )
    data class FaixaPosicao(val limiteInferior: Int = 0, val limiteSuperior: Int = Int.MAX_VALUE, val pontos: Int = 0)

    data class NaturezaProps(
        val maxPontos: Int = 10,
        val pontosAlimentar: Int = 10,
        val pontosComum: Int = 4
    )
}
```

[ASSUMED — exact nested structure. Adjust field names at implementation. The pattern matches `ScraperProperties` style exactly (verified in codebase).]

`@ConfigurationPropertiesScan` is already on `PrecatoriosApiApplication` — `ScoringProperties` will be picked up automatically.

### Pattern 4: ScoringService — pure stateless function

**What:** Accepts a `Precatorio` domain object, reads `ScoringProperties`, returns a `ScoredResult` value object.

```kotlin
data class ScoredResult(
    val total: Int,
    val detalhes: Map<String, Int?>   // null means data was unavailable (D-14)
)

@Service
class ScoringService(private val props: ScoringProperties) {

    fun score(precatorio: Precatorio): ScoredResult {
        val valor      = scoreValor(precatorio.valorAtualizado)
        val entidade   = scoreEntidade(precatorio.entidadeDevedora)
        val status     = scoreStatus(precatorio.statusPagamento)
        val posicao    = scorePosicao(precatorio.posicaoCronologica)
        val natureza   = scoreNatureza(precatorio.natureza)

        val total = listOfNotNull(valor, entidade, status, posicao, natureza).sum()

        return ScoredResult(
            total = total,
            detalhes = mapOf(
                "valor"              to valor,
                "entidadeDevedora"   to entidade,
                "statusPagamento"    to status,
                "posicaoCronologica" to posicao,
                "natureza"           to natureza,
                "total"              to total
            )
        )
    }

    // D-13: null input -> 0 points; D-14: null captured in detalhes
    private fun scoreValor(valor: BigDecimal?): Int? {
        valor ?: return null
        val centavos = valor.toLong()     // or multiply; depends on unit
        return props.valor.faixas
            .sortedByDescending { it.limiteInferior }
            .firstOrNull { centavos >= it.limiteInferior }
            ?.pontos ?: 0
    }
    // ... similar for other criteria
}
```

[ASSUMED — exact implementation. The key design decisions (null-returns-null, sum of non-null, total in map) are from locked decisions D-13/D-14/D-11.]

### Pattern 5: application.yml scoring section

```yaml
scoring:
  valor:
    max-pontos: 30
    faixas:
      - limite-inferior: 1000000  # > R$1M
        pontos: 30
      - limite-inferior: 500000   # R$500k-1M
        pontos: 22
      - limite-inferior: 150000   # R$150k-500k
        pontos: 15
      - limite-inferior: 50000    # R$50k-150k
        pontos: 8
      - limite-inferior: 0        # < R$50k
        pontos: 3
  entidade-devedora:
    max-pontos: 25
    pontos-desconhecida: 5
    mapa:
      "Fazenda do Estado de SP": 25
      "Prefeitura de SP": 20
  status-pagamento:
    max-pontos: 20
    pontos-padrao: 0
    mapa:
      PENDENTE: 20
      "EM PROCESSAMENTO": 15
      "PARCIALMENTE PAGO": 10
      PAGO: 0
  posicao-cronologica:
    max-pontos: 15
    faixas:
      - limite-inferior: 1
        limite-superior: 100
        pontos: 15
      - limite-inferior: 101
        limite-superior: 500
        pontos: 12
      - limite-inferior: 501
        limite-superior: 1000
        pontos: 8
      - limite-inferior: 1001
        limite-superior: 5000
        pontos: 4
      - limite-inferior: 5001
        limite-superior: 2147483647
        pontos: 1
  natureza:
    max-pontos: 10
    pontos-alimentar: 10
    pontos-comum: 4
```

[ASSUMED — exact property names follow Spring Boot kebab-case convention for camelCase field names. Verified convention matches existing `scraper.precatorio-portal.*` pattern in codebase.]

### Anti-Patterns to Avoid

- **Self-invocation bypasses proxy:** If `EsajScraper.fetchProcesso()` is called from within the same bean (e.g., from `buscarPorNome`), the cache proxy is not invoked. Phase 3 methods are called from controllers and BFS (Phase 4), not self — this is not a risk here, but documents the rule.
- **@Cacheable on internal/private methods:** Spring proxy only wraps public methods. `doFetchProcesso()` is `internal` — do NOT place `@Cacheable` there. The annotation belongs on the public `fetchProcesso()` public method.
- **Caching methods that throw:** Caffeine does not cache exceptions by default. The current scrapers throw on hard failures and return partial data on selector mismatches. `unless = "#result == null"` covers the null case; exceptions will simply propagate and not be cached.
- **Using CaffeineCacheManager without declaring cache names:** If `@Cacheable` uses a cache name not declared in `CaffeineCacheManager`, Spring Boot will either create it dynamically (with default settings, no TTL) or throw. Always declare names explicitly in the constructor.
- **Missing ObjectMapper for scoreDetalhes:** `Lead.scoreDetalhes` is a `String? JSONB` column. Serializing `Map<String, Int?>` requires an `ObjectMapper` call — do not write raw string concatenation.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| In-process caching with TTL eviction | Custom HashMap + scheduler thread | Caffeine (already declared) | Caffeine handles concurrency, expiry, stats; HashMap has no TTL eviction |
| Cache proxy interception | Manual if-else in method body | Spring @Cacheable | Spring proxy handles cache lookup/store transparently; manual branches are error-prone |
| Property binding with nested objects | Manual `@Value` injection per field | @ConfigurationProperties | Nested structures, list/map binding, type safety — @Value cannot bind collections cleanly |
| JSON serialization of scoreDetalhes | String template `"{"key":${val}}"` | ObjectMapper.writeValueAsString | Edge cases: null values, special chars, future fields — always use Jackson |
| Score bracket lookup | If-else chain in method | List of data class brackets sorted and `firstOrNull` | Configuration-driven brackets cannot be in code; data-driven lookup is mandatory for SCOR-02 |

---

## Common Pitfalls

### Pitfall 1: @EnableCaching missing
**What goes wrong:** `@Cacheable` is silently ignored — no error, no cache, every call hits the scraper.
**Why it happens:** Without `@EnableCaching`, Spring does not register the cache interceptor. The annotation compiles fine and no startup error is raised.
**How to avoid:** Place `@EnableCaching` on the `CacheConfig` class. Verify with a unit test that calls the same method twice and asserts the underlying method is invoked only once (use MockK `verify(exactly = 1)`).
**Warning signs:** Actuator `/actuator/caches` endpoint returns empty list; scraper log lines appear on every request.

### Pitfall 2: Caffeine version mismatch — `build()` vs `build(loader)`
**What goes wrong:** `Caffeine.newBuilder().build()` returns a manual cache (no loader); calling `get(key, loader)` works fine. Using `.build(loader)` creates a loading cache. Spring's `CaffeineCache` works with both, but the auto-configuration path differs.
**Why it happens:** Confusion between Caffeine's two cache types when following old examples.
**How to avoid:** For `@Cacheable` (Spring-managed population), use `.build()` — Spring populates on miss via the method call. LoadingCache is for cases where you pass a loader function to Caffeine directly.
**Warning signs:** Compile error if wrong generic type is passed to `CaffeineCache` constructor.

### Pitfall 3: scoreDetalhes null vs 0 serialization
**What goes wrong:** Jackson serializes `null` map values by default with `include: NON_NULL`, omitting them from the JSON. Consumer (Phase 5 API) cannot distinguish "criterion skipped" from "criterion not computed."
**Why it happens:** Default `ObjectMapper` with `setSerializationInclusion(NON_NULL)` drops null values.
**How to avoid:** Use a local `ObjectMapper` without `NON_NULL` for `scoreDetalhes` serialization. Or use `ALWAYS` include. Document that null in the JSON means data was unavailable (D-14).
**Warning signs:** `scoreDetalhes` JSON string is shorter than expected; missing keys for null criteria.

### Pitfall 4: Map key case mismatch in status/entity lookup
**What goes wrong:** CAC/SCP returns `"Pendente"` but the yml map key is `"PENDENTE"` — lookup misses, criterion scores 0.
**Why it happens:** Portal text uses title case; yml config uses upper case.
**How to avoid:** Normalize both sides to uppercase before lookup: `statusPagamento?.uppercase()`. Document this normalization in the service.
**Warning signs:** All leads score 0 on `statusPagamento` even for clearly pending precatórios.

### Pitfall 5: CacheName typo — silent miss
**What goes wrong:** `@Cacheable("processo")` (missing 's') — cache never hits because the named cache is `"processos"`.
**Why it happens:** String literal typo, no compile-time check.
**How to avoid:** Extract cache names as constants in a `CacheNames` object or companion object. Reference the constant in both `CacheConfig` and `@Cacheable`.
**Warning signs:** Cache hit rate stays 0% in actuator stats.

---

## Code Examples

### Enabling caching with CaffeineCacheManager (recommended simple form)

```kotlin
// src/main/kotlin/.../config/CacheConfig.kt
// Source: [ASSUMED — standard Spring Boot 3.x pattern]
import com.github.ben-manes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

object CacheNames {
    const val PROCESSOS   = "processos"
    const val PRECATORIOS = "precatorios"
    const val DATAJUD     = "datajud"
}

@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val manager = CaffeineCacheManager(
            CacheNames.PROCESSOS,
            CacheNames.PRECATORIOS,
            CacheNames.DATAJUD
        )
        manager.setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .maximumSize(1000)
                .recordStats()
        )
        return manager
    }
}
```

### @Cacheable annotation on scraper methods

```kotlin
// EsajScraper.kt — add import and annotation
import org.springframework.cache.annotation.Cacheable

@Cacheable(cacheNames = [CacheNames.PROCESSOS], unless = "#result == null")
fun fetchProcesso(numero: String): ProcessoScraped { ... }
```

### ScoredResult value object and serialization

```kotlin
// src/main/kotlin/.../service/ScoringService.kt
data class ScoredResult(
    val total: Int,
    val detalhes: Map<String, Int?>
)

// Serializing to Lead.scoreDetalhes:
val result = scoringService.score(precatorio)
lead.score = result.total
lead.scoreDetalhes = objectMapper.writeValueAsString(result.detalhes)
// ObjectMapper must NOT have NON_NULL inclusion to preserve null entries (D-14)
```

### Unit test pattern for ScoringService (no Spring context)

```kotlin
// src/test/kotlin/.../service/ScoringServiceTest.kt
class ScoringServiceTest {

    private val defaultProps = ScoringProperties(
        valor = ScoringProperties.ValorProps(
            maxPontos = 30,
            faixas = listOf(
                ScoringProperties.FaixaValor(1_000_000, 30),
                ScoringProperties.FaixaValor(0, 3)
            )
        )
        // ... other criteria with test defaults
    )

    private val service = ScoringService(defaultProps)

    @Test
    fun `null valorAtualizado scores null on valor criterion`() {
        val precatorio = Precatorio().apply { valorAtualizado = null }
        val result = service.score(precatorio)
        assertThat(result.detalhes["valor"]).isNull()
    }

    @Test
    fun `value above 1M scores full 30 points`() {
        val precatorio = Precatorio().apply {
            valorAtualizado = BigDecimal("1500000")
        }
        val result = service.score(precatorio)
        assertThat(result.detalhes["valor"]).isEqualTo(30)
    }
}
```

### LeadRepository — score > 0 filter (SCOR-04)

```kotlin
// LeadRepository.kt — add default query method for Phase 5 API
@Query("SELECT l FROM Lead l WHERE l.score > 0")
fun findAllWithPositiveScore(pageable: Pageable): Page<Lead>

// Or use derived query:
fun findByScoreGreaterThan(score: Int, pageable: Pageable): Page<Lead>
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Guava Cache (com.google.guava) | Caffeine (com.github.ben-manes.caffeine) | Spring Boot 2.x era | Caffeine is Guava's successor; same API shape but non-blocking |
| `@EnableCaching` on main app class | `@EnableCaching` on dedicated `@Configuration` | Best practice evolved | Keeps configuration concerns separated; easier to test |
| `application.properties` scalar values | `@ConfigurationProperties` with nested data classes | Spring Boot 2.x | Typed binding, IDE support, validation — scalars via `@Value` are fragile for complex config |

**Deprecated/outdated:**
- `spring.cache.caffeine.spec` string (e.g., `"expireAfterWrite=24h,maximumSize=500"`): Works for a single global spec but cannot configure per-named-cache TTL. Use programmatic builder when named caches need individual settings.

---

## Runtime State Inventory

Phase 3 is additive (new classes, new annotations on existing classes). No rename, no data migration.

| Category | Items Found | Action Required |
|----------|-------------|-----------------|
| Stored data | None | None |
| Live service config | None | None |
| OS-registered state | None | None |
| Secrets/env vars | None — no new secrets; all scoring is local | None |
| Build artifacts | None | None |

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JVM 21 | Spring Boot 3.5 + Kotlin 2.2 | Verified via build.gradle toolchain | 21 | — |
| Caffeine | CACHE-01, CACHE-02 | Yes (declared in build.gradle.kts) | 3.2.1 | — |
| spring-boot-starter-cache | @Cacheable proxy | Yes (declared in build.gradle.kts) | 3.5.3 | — |
| PostgreSQL (Testcontainers) | ScoringPropertiesTest (if @SpringBootTest) | Yes — postgres:17-alpine pulled in Phase 1/2 tests | 17-alpine | Use @DataJpaTest slice |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** None.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (via spring-boot-starter-test 3.5.3) + MockK 1.14.3 |
| Config file | `build.gradle.kts` — `tasks.withType<Test> { useJUnitPlatform() }` |
| Quick run command | `./gradlew test --tests "*.ScoringServiceTest" -x integrationTest` |
| Full suite command | `./gradlew test` |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CACHE-01 | Second call within 24h does not invoke scraper | Integration (MockK spy) | `./gradlew test --tests "*.CacheConfigTest"` | No — Wave 0 |
| CACHE-02 | Two callers with same number share cached result | Integration (MockK spy) | `./gradlew test --tests "*.CacheConfigTest"` | No — Wave 0 |
| SCOR-01 | Score 0-100, five criteria, correct weights | Unit | `./gradlew test --tests "*.ScoringServiceTest"` | No — Wave 0 |
| SCOR-02 | Custom weights in yml change score without code change | Integration (@SpringBootTest) | `./gradlew test --tests "*.ScoringPropertiesTest"` | No — Wave 0 |
| SCOR-03 | scoreDetalhes map has all five criterion keys | Unit | `./gradlew test --tests "*.ScoringServiceTest"` | No — Wave 0 |
| SCOR-04 | Score=0 leads persisted but excluded from default query | Unit (repo method) | `./gradlew test --tests "*.LeadRepositoryTest"` | No — Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "*.ScoringServiceTest" -x processResources`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/kotlin/br/com/precatorios/service/ScoringServiceTest.kt` — covers SCOR-01, SCOR-03 (pure unit, no Spring)
- [ ] `src/test/kotlin/br/com/precatorios/config/CacheConfigTest.kt` — covers CACHE-01, CACHE-02 (MockK spy on scraper, @SpringBootTest)
- [ ] `src/test/kotlin/br/com/precatorios/config/ScoringPropertiesTest.kt` — covers SCOR-02 (mirrors ScraperPropertiesTest pattern)
- [ ] `src/test/kotlin/br/com/precatorios/repository/LeadRepositoryTest.kt` — covers SCOR-04 (findByScoreGreaterThan)

---

## Security Domain

This phase adds no authentication, network calls, or user-controlled input to the scoring engine. Scoring reads scraped data already in memory; cache keys are process/precatório numbers from internal BFS logic.

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | — |
| V3 Session Management | No | — |
| V4 Access Control | No | — |
| V5 Input Validation | Partial | Scoring inputs come from scrapers (internal); no external user input in this phase |
| V6 Cryptography | No | — |

No additional security controls required for this phase.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `CaffeineCacheManager.setCaffeine(Caffeine)` accepts a builder, not a built cache | Standard Stack, Code Examples | API may require `.build()` on the builder; adjust in implementation |
| A2 | `CaffeineCache(name, caffeineCache)` constructor signature is correct for SimpleCacheManager path | Architecture Patterns P1 | Compile error if signature differs; trivial fix |
| A3 | Nested `ScoringProperties` data class structure with `faixas: List<FaixaValor>` binds correctly from yml | Architecture Patterns P3 | List-of-objects binding is standard in Spring Boot 3.x @ConfigurationProperties; LOW risk |
| A4 | `unless = "#result == null"` prevents null results from being cached | Common Pitfalls, Code Examples | Core Spring Cache behavior — HIGH confidence from documentation; LOW risk |
| A5 | Status/entity string comparison requires `.uppercase()` normalization | Common Pitfalls P4 | CAC/SCP actual text case unknown until live scraper tested; if not normalized, silent scoring miss |

---

## Open Questions

1. **BigDecimal unit for `valorAtualizado`**
   - What we know: `Precatorio.valorAtualizado` is `BigDecimal?`, precision 15, scale 2
   - What's unclear: Whether the value stored is in Reais (e.g., `1500000.00` for R$1.5M) or centavos. The scoring brackets in D-01 use R$ values.
   - Recommendation: Assume Reais (standard for monetary JPA columns with scale 2). ScoringService should compare directly against bracket `limiteInferior` expressed as whole reais (Long or BigDecimal). Verify with a sample scraped value in Phase 2 smoke test.

2. **`unless` SpEL with non-null return types**
   - What we know: All three scraper methods return non-null types (`ProcessoScraped`, `PrecatorioScraped`, `DataJudResult`) — they throw on hard failure, return partial data on selector miss.
   - What's unclear: Since the return type is never `null`, is `unless = "#result == null"` meaningful?
   - Recommendation: Keep the guard anyway as defensive programming. If the scraper contract ever changes to return null, the guard is already in place. It adds zero overhead.

3. **Cache coherence with Resilience4j retry**
   - What we know: The public methods (`fetchProcesso`, etc.) are decorated by Resilience4j programmatically inside the method body — not via AOP annotations.
   - What's unclear: Does Spring Cache proxy wrap the Resilience4j decoration or vice versa? Since the decoration is inside the method body (not a separate proxy), the Spring Cache proxy wraps the entire method including retry. This means: a cached result prevents the retry logic from ever running — which is the desired behavior.
   - Recommendation: Document this interaction in `CacheConfig` Kdoc. No code change needed.

---

## Sources

### Primary (HIGH confidence)
- Codebase inspection — `build.gradle.kts`, `ScraperProperties.kt`, `ResilienceConfig.kt`, `EsajScraper.kt`, `CacScraper.kt`, `DataJudClient.kt`, `Lead.kt`, `Precatorio.kt`, `application.yml`, `PrecatoriosApiApplication.kt` — verified in this session
- `./gradlew dependencyInsight` — Caffeine 3.2.1, spring-boot-starter-cache 3.5.3 confirmed

### Secondary (MEDIUM confidence)
- Spring Framework cache annotations documentation: https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html

### Tertiary (LOW confidence — assumed from training knowledge)
- `CaffeineCacheManager` API surface (Spring Cache 6.x wrapping Caffeine 3.x) — training knowledge, verify at implementation
- `@ConfigurationProperties` list-of-objects binding for `faixas` — consistent with Spring Boot 3.x behavior, verify at implementation

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — dependency versions verified via Gradle in this session
- Architecture: HIGH — patterns mirror existing verified code in codebase
- Pitfalls: MEDIUM — derived from known Spring Cache AOP behavior; some items assumed from training
- Scoring service design: HIGH — all locked decisions from CONTEXT.md, no discretion required

**Research date:** 2026-04-06
**Valid until:** 2026-05-06 (stable APIs — Caffeine + Spring Boot)
