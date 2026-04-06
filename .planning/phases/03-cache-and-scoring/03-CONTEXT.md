# Phase 3: Cache and Scoring - Context

**Gathered:** 2026-04-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Caffeine read-through cache wired to all three scrapers (24h TTL, no negative caching), and a fully configurable scoring engine that produces a 0-100 score with per-criterion breakdown for any precatorio.

</domain>

<decisions>
## Implementation Decisions

### Scoring Thresholds
- **D-01:** Precatorio value criterion (30pts) uses tiered brackets configurable in application.yml (e.g., >R$1M = 30pts, R$500k-1M = 22pts, R$150k-500k = 15pts, R$50k-150k = 8pts, <R$50k = 3pts)
- **D-02:** Debtor entity criterion (25pts) uses an entity whitelist map in application.yml — specific entity names mapped to scores (e.g., 'Fazenda do Estado de SP' = 25pts, 'Prefeitura de SP' = 20pts, unknown = 5pts)
- **D-03:** Payment status criterion (20pts) uses a status keyword map in application.yml — each CAC/SCP status string mapped to a score (e.g., PENDENTE=20, EM PROCESSAMENTO=15, PARCIALMENTE PAGO=10, PAGO=0)
- **D-04:** Chronological position criterion (15pts) uses tiered brackets in application.yml (e.g., 1-100=15pts, 101-500=12pts, 501-1000=8pts, 1001-5000=4pts, >5000=1pt). Lower position = closer to payment = higher score
- **D-05:** Nature criterion (10pts) uses alimentar/comum binary — Alimentar=10pts, Comum=4pts, configurable in application.yml
- **D-06:** All scoring weights AND thresholds/maps are fully configurable via application.yml using @ConfigurationProperties (ScoringProperties), following the ScraperProperties pattern from Phase 2

### Cache Scope
- **D-07:** @Cacheable applied to primary fetch methods only: EsajScraper.fetchProcesso(), CacScraper.fetchPrecatorio(), DataJudClient.buscarPorNumeroProcesso()
- **D-08:** buscarPorNome (e-SAJ name search) is NOT cached — search results change over time and BFS needs fresh results per run
- **D-09:** buscarPorMunicipio (DataJud) is NOT cached — same rationale as name search
- **D-10:** Three named Caffeine caches: processos, precatorios, datajud — each with 24h TTL, `unless = "#result == null"` to prevent negative caching

### Score Details Format
- **D-11:** scoreDetalhes JSON uses a flat map structure: `{"valor": 22, "entidadeDevedora": 25, "statusPagamento": 15, "posicaoCronologica": 8, "natureza": 10, "total": 80}`
- **D-12:** Criterion keys in scoreDetalhes match the Precatorio entity field names for consistency

### Missing Data Handling
- **D-13:** When a scoring criterion's input data is null (scraper returned partial data), that criterion scores 0 points
- **D-14:** scoreDetalhes uses null (not 0) for criteria with missing input data — distinguishes "scored 0 because data was bad" from "data was unavailable"
- **D-15:** Example: `{"valor": 22, "entidadeDevedora": 25, "statusPagamento": null, "posicaoCronologica": 8, "natureza": 10, "total": 65}` — statusPagamento was missing from scraper

### Claude's Discretion
- CacheConfig bean implementation details (Caffeine builder settings, max cache size)
- ScoringProperties nested class structure for @ConfigurationProperties binding
- Default threshold/bracket values in application.yml (exact numbers for initial deployment)
- Unit test structure for scoring weight combinations

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project context
- `.planning/PROJECT.md` — Core value, requirements, constraints, key decisions
- `.planning/REQUIREMENTS.md` — Requirement IDs CACHE-01, CACHE-02, SCOR-01, SCOR-02, SCOR-03, SCOR-04
- `.planning/ROADMAP.md` §Phase 3 — Success criteria, plan structure (03-01 cache, 03-02 scoring)

### Prior phase context
- `.planning/phases/02-scraper-layer/02-CONTEXT.md` — Scraper design decisions (graceful degradation D-04/D-05, centralized selectors D-07, Resilience4j integration)

### Existing code (cache targets)
- `src/main/kotlin/br/com/precatorios/scraper/EsajScraper.kt` — fetchProcesso() and buscarPorNome() methods to annotate with @Cacheable
- `src/main/kotlin/br/com/precatorios/scraper/CacScraper.kt` — fetchPrecatorio() method to annotate with @Cacheable
- `src/main/kotlin/br/com/precatorios/scraper/DataJudClient.kt` — buscarPorNumeroProcesso() method to annotate with @Cacheable

### Existing code (scoring inputs)
- `src/main/kotlin/br/com/precatorios/domain/Lead.kt` — score (Int) and scoreDetalhes (String? JSONB) fields
- `src/main/kotlin/br/com/precatorios/domain/Precatorio.kt` — valorOriginal, valorAtualizado, posicaoCronologica, statusPagamento, natureza, entidadeDevedora
- `src/main/kotlin/br/com/precatorios/domain/Credor.kt` — linked entity for lead association

### Existing code (patterns to follow)
- `src/main/kotlin/br/com/precatorios/config/ScraperProperties.kt` — @ConfigurationProperties pattern for ScoringProperties
- `src/main/kotlin/br/com/precatorios/config/ResilienceConfig.kt` — @Bean factory pattern for CacheConfig
- `src/main/resources/application.yml` — Config file to extend with cache and scoring sections
- `build.gradle.kts` — Caffeine and spring-boot-starter-cache dependencies already declared

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `caffeine` + `spring-boot-starter-cache` already in build.gradle.kts — no new dependencies needed
- `ScraperProperties` with `@ConfigurationProperties` — pattern to follow for `ScoringProperties`
- `ResilienceConfig` with `@Bean` factories — pattern to follow for `CacheConfig`
- `application.yml` with nested scraper config — extend with `cache:` and `scoring:` sections

### Established Patterns
- `@ConfigurationProperties` with nested data classes for type-safe config binding
- `@Service` + `@Repository` layering from Phase 1
- Graceful degradation in scrapers — partial data returned with flags (affects scoring null handling)

### Integration Points
- Scraper methods receive @Cacheable annotations (cache sits between controllers/BFS and scrapers)
- ScoringService will be called by BFS engine in Phase 4 to score each discovered lead
- Lead.score and Lead.scoreDetalhes populated by ScoringService, consumed by Leads API in Phase 5
- @EnableCaching on main application class or a dedicated CacheConfig

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 03-cache-and-scoring*
*Context gathered: 2026-04-06*
