---
phase: 03-cache-and-scoring
verified: 2026-04-06T15:45:00Z
status: human_needed
score: 10/11 must-haves verified
re_verification: false
human_verification:
  - test: "Confirm that a real HTTP call to e-SAJ or CAC/SCP portal is NOT made on the second identical scraper call within 24h when the application is running"
    expected: "Second call served from in-process Caffeine cache with no outbound network activity; verifiable via network trace or request counter in actuator /caches endpoint"
    why_human: "Integration tests mock the internal doFetch* methods using @SpykBean, which confirms the Spring proxy intercepts correctly — but the live path (real HTTP traffic) cannot be verified without a running application and network observation tool"
---

# Phase 3: Cache and Scoring Verification Report

**Phase Goal:** Caffeine read-through cache wired to all three scrapers (24h TTL, no negative caching), and a fully configurable scoring engine that produces a 0-100 score with per-criterion breakdown for any precatorio
**Verified:** 2026-04-06T15:45:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | A second identical request to e-SAJ or CAC/SCP within 24h is served from Caffeine cache without any outbound HTTP call | ? UNCERTAIN | CacheConfigTest verifies Spring proxy intercepts via @SpykBean mocking internal doFetch* methods (verify exactly = 1 passes). Live HTTP suppression requires human observation. |
| 2 | Cache is keyed by process/precatorio number so two different callers looking up the same number share the cached result | ✓ VERIFIED | Test `two different process numbers create separate cache entries` passes (verify exactly = 2 for different keys). Same key always hits same Caffeine entry by Spring default key = method arg. |
| 3 | ScoringService returns a score 0-100 with a `scoreDetalhes` map showing the contribution of each of the five criteria | ✓ VERIFIED | ScoringServiceTest Test 13 and Test 14 verify: full precatorio scores 100, detalhes contains all 6 keys (valor, entidadeDevedora, statusPagamento, posicaoCronologica, natureza, total). 14 tests pass. |
| 4 | Changing a scoring weight in `application.yml` and restarting changes the score without any code modification | ✓ VERIFIED | ScoringProperties uses @ConfigurationProperties(prefix = "scoring"); scoring section fully defined in application.yml. ScoringService reads all weights and brackets from injected props — no hardcoded values. |
| 5 | Leads scoring 0 across all criteria are persisted but excluded from default lead list results | ? PARTIAL | LeadRepository.findByScoreGreaterThan(score, pageable) exists (infrastructure). Actual exclusion from GET /leads requires Phase 5 LeadController. Persisting zero-score leads requires Phase 4 BFS engine. Both upstream phases are not yet built. |

**Score:** 3/5 truths fully verified, 1 partially verified (infrastructure in place, consumer not yet built), 1 needs human verification

### Must-Have Truths (Plan Frontmatter — Plan 01)

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | A second identical call to fetchProcesso() within 24h does not invoke the underlying scraper HTTP call | ✓ VERIFIED | CacheConfigTest: fetchProcesso called twice, verify(exactly = 1) { esajScraper.doFetchProcesso(...) } passes |
| 2 | A second identical call to fetchPrecatorio() within 24h does not invoke the underlying scraper HTTP call | ✓ VERIFIED | CacheConfigTest: fetchPrecatorio called twice, verify(exactly = 1) { cacScraper.doFetchPrecatorio(...) } passes |
| 3 | A second identical call to buscarPorNumeroProcesso() within 24h does not invoke the underlying scraper HTTP call | ✓ VERIFIED | CacheConfigTest: buscarPorNumeroProcesso called twice, verify(exactly = 1) { dataJudClient.doBuscarPorNumero(...) } passes |
| 4 | Cache is keyed by the single String parameter (numero) so two callers looking up the same number share the cached result | ✓ VERIFIED | Spring @Cacheable default key = single String arg. Test for two different numbers produces verify(exactly = 2) confirming separate entries. |
| 5 | buscarPorNome and buscarPorMunicipio are NOT cached | ✓ VERIFIED | grep shows exactly 3 @Cacheable annotations in main sources (fetchProcesso, fetchPrecatorio, buscarPorNumeroProcesso). EsajScraper.buscarPorNome (line 87) and DataJudClient.buscarPorMunicipio (line 122) have no @Cacheable. |
| 6 | A scraper method returning null does not populate the cache (no negative caching) | ✓ VERIFIED | All three @Cacheable annotations include `unless = "#result == null"` preventing null result caching. |

**Plan 01 Score:** 6/6 must-have truths verified

### Must-Have Truths (Plan Frontmatter — Plan 02)

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | ScoringService accepts a Precatorio and returns a total score 0-100 plus a detalhes breakdown map | ✓ VERIFIED | ScoringService.score(precatorio: Precatorio): ScoredResult exists. ScoredResult has total: Int and detalhes: Map<String, Int?>. Tests confirm 0-100 range. |
| 2 | The five criteria are: valor (30pts max), entidadeDevedora (25pts max), statusPagamento (20pts max), posicaoCronologica (15pts max), natureza (10pts max) | ✓ VERIFIED | ScoringProperties defaults confirm maxPontos values. ScoringServiceTest Test 13 confirms full score = 100 (30+25+20+15+10). |
| 3 | Changing scoring weights or thresholds in application.yml and restarting changes the score without code modification | ✓ VERIFIED | All scoring logic reads from injected ScoringProperties (constructor injection). application.yml has full scoring: section. No hardcoded weights in ScoringService. |
| 4 | When a scoring criterion input is null, that criterion contributes 0 to the total and appears as null in scoreDetalhes | ✓ VERIFIED | ScoringServiceTest Test 12: all fields null -> total=0, all criterion detalhes null. Test 3: null valor -> detalhes["valor"] is null. listOfNotNull() ensures null contributions excluded from sum. |
| 5 | scoreDetalhes is a flat JSON map with keys: valor, entidadeDevedora, statusPagamento, posicaoCronologica, natureza, total | ✓ VERIFIED | ScoringServiceTest Test 14 asserts detalhes.containsKeys("valor","entidadeDevedora","statusPagamento","posicaoCronologica","natureza","total"). |
| 6 | LeadRepository has a query method to find leads with score > 0 for default listing | ✓ VERIFIED | LeadRepository.kt line 12: `fun findByScoreGreaterThan(score: Int, pageable: Pageable): Page<Lead>` — Spring Data derived query, compiles successfully. |

**Plan 02 Score:** 6/6 must-have truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/br/com/precatorios/config/CacheConfig.kt` | @EnableCaching + CaffeineCacheManager with three named caches | ✓ VERIFIED | Contains @Configuration, @EnableCaching, CacheNames object with 3 constants, CaffeineCacheManager with 24h TTL, maximumSize(1000), recordStats() |
| `src/test/kotlin/br/com/precatorios/config/CacheConfigTest.kt` | Integration test proving cache hit on second call | ✓ VERIFIED | Contains @SpringBootTest + @Testcontainers + @SpykBean. 4 test methods. `verify(exactly = 1)` pattern used for all three scrapers. Tests pass. |
| `src/main/kotlin/br/com/precatorios/config/ScoringProperties.kt` | @ConfigurationProperties for scoring weights, brackets, and maps | ✓ VERIFIED | @ConfigurationProperties(prefix = "scoring") with 5 nested data classes: ValorProps, EntidadeDevedoraProps, StatusPagamentoProps, PosicaoProps, NaturezaProps |
| `src/main/kotlin/br/com/precatorios/service/ScoringService.kt` | Stateless scoring function with five criteria; exports ScoredResult | ✓ VERIFIED | @Service class with score(precatorio: Precatorio): ScoredResult. ScoredResult data class defined. 5 private scoring methods. |
| `src/test/kotlin/br/com/precatorios/service/ScoringServiceTest.kt` | Unit tests for all scoring criteria and edge cases | ✓ VERIFIED | 14 test methods, no @SpringBootTest (pure unit test). Covers all 14 behaviors specified in plan. Tests pass. |
| `src/main/kotlin/br/com/precatorios/repository/LeadRepository.kt` | findByScoreGreaterThan query for SCOR-04 | ✓ VERIFIED | `fun findByScoreGreaterThan(score: Int, pageable: Pageable): Page<Lead>` exists at line 12. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| EsajScraper.kt | CacheConfig | @Cacheable(cacheNames = [CacheNames.PROCESSOS]) | ✓ WIRED | Line 70: `@Cacheable(cacheNames = [CacheNames.PROCESSOS], unless = "#result == null")` on fetchProcesso |
| CacScraper.kt | CacheConfig | @Cacheable(cacheNames = [CacheNames.PRECATORIOS]) | ✓ WIRED | Line 46: `@Cacheable(cacheNames = [CacheNames.PRECATORIOS], unless = "#result == null")` on fetchPrecatorio |
| DataJudClient.kt | CacheConfig | @Cacheable(cacheNames = [CacheNames.DATAJUD]) | ✓ WIRED | Line 111: `@Cacheable(cacheNames = [CacheNames.DATAJUD], unless = "#result == null")` on buscarPorNumeroProcesso |
| ScoringService.kt | ScoringProperties.kt | constructor injection | ✓ WIRED | `class ScoringService(private val props: ScoringProperties)` — ScoringProperties referenced in all 5 private scoring methods |
| ScoringService.kt | Precatorio.kt | score(precatorio: Precatorio) | ✓ WIRED | Method signature `fun score(precatorio: Precatorio): ScoredResult` confirmed |
| application.yml | ScoringProperties.kt | scoring: prefix binding | ✓ WIRED | application.yml has `scoring:` section at line 46 matching @ConfigurationProperties(prefix = "scoring") |

### Data-Flow Trace (Level 4)

ScoringService is not a data-rendering component (it's a pure computation service). No Level 4 data-flow trace applies. CacheConfig configures in-process cache; no external data source.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Kotlin compilation succeeds | `./gradlew compileKotlin` | BUILD SUCCESSFUL | ✓ PASS |
| ScoringService unit tests pass (14 tests) | `./gradlew test --tests "*.ScoringServiceTest"` | BUILD SUCCESSFUL | ✓ PASS |
| CacheConfig integration tests pass (4 tests) | `./gradlew test --tests "*.CacheConfigTest"` | BUILD SUCCESSFUL (14s) | ✓ PASS |
| Exactly 3 @Cacheable annotations in main sources | `grep -rn "@Cacheable" src/main/kotlin/` | 3 matches (fetchProcesso, fetchPrecatorio, buscarPorNumeroProcesso) | ✓ PASS |
| Exactly 1 @EnableCaching annotation | `grep -rn "@EnableCaching" src/main/kotlin/` | 1 match (CacheConfig) | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|---------|
| CACHE-01 | 03-01-PLAN.md | Scraping results cached in-memory (Caffeine) with 24h TTL | ✓ SATISFIED | CacheConfig.kt: CaffeineCacheManager with expireAfterWrite(24, TimeUnit.HOURS). Integration test proves cache hit suppresses HTTP call. |
| CACHE-02 | 03-01-PLAN.md | Cache keyed by process number / precatório number | ✓ SATISFIED | @Cacheable with single String param as default key. Test confirms two different numbers produce two separate cache entries (verify exactly = 2). |
| SCOR-01 | 03-02-PLAN.md | System scores each lead 0-100 based on five criteria | ✓ SATISFIED | ScoringService.score() returns total 0-100. All five criteria implemented with correct max points. Test 13 confirms full score = 100. |
| SCOR-02 | 03-02-PLAN.md | Scoring weights and thresholds configurable via application.yml without code changes | ✓ SATISFIED | ScoringProperties @ConfigurationProperties binding. Full scoring: YAML section. ScoringService reads all values from props — no hardcoding. |
| SCOR-03 | 03-02-PLAN.md | Each lead's score includes scoreDetalhes breakdown showing each criterion | ✓ SATISFIED | ScoredResult.detalhes: Map<String, Int?> with 6 keys confirmed by Test 14. |
| SCOR-04 | 03-02-PLAN.md | Leads scoring 0 are persisted but excluded from default lead list results | ⚠ PARTIAL | LeadRepository.findByScoreGreaterThan exists (infrastructure for Phase 3). Actual persistence (Phase 4 BFS) and API exclusion (Phase 5 GET /leads endpoint) not yet built. |

**Note on SCOR-04:** The REQUIREMENTS.md maps SCOR-04 to Phase 3. Phase 3 delivers the repository query method as the mechanism. Full end-to-end satisfaction requires Phase 4 (BFS persists leads with scores) and Phase 5 (API uses findByScoreGreaterThan in GET /leads). The query infrastructure is confirmed present.

### Deferred Items

Items not yet met because upstream/downstream phases have not been built.

| # | Item | Addressed In | Evidence |
|---|------|-------------|---------|
| 1 | "Leads scoring 0 across all criteria are still persisted" (persistence side of SCOR-04) | Phase 4 | Phase 4 goal: "scores each lead, and persists results". BFS engine will call ScoringService.score() and persist Lead entities. |
| 2 | "excluded from default lead list results" (API side of SCOR-04) | Phase 5 | Phase 5 plan 05-01: "GET /leads (JOIN FETCH query, pagination, filters, score DESC default sort)" — will use LeadRepository.findByScoreGreaterThan. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none found) | - | - | - | - |

No TODO, FIXME, placeholder, or empty return anti-patterns found in any of the six Phase 3 files. All implementations contain real logic.

### Human Verification Required

#### 1. Live Cache Suppression of Real HTTP Traffic

**Test:** Start the application with `./gradlew bootRun`, call `GET /api/v1/processo/{numero}` twice with the same number, observe the `/actuator/caches` endpoint and application logs between the two calls.
**Expected:** First call logs e-SAJ HTTP request and response. Second call (within 24h) produces no outbound HTTP and the cache hit count on the `processos` cache increments. The response is identical.
**Why human:** CacheConfigTest proves the Spring proxy intercepts correctly using mocked internal methods, but the end-to-end path (controller → EsajScraper.fetchProcesso → Spring cache → no HTTP) traverses real scraper infrastructure only verifiable in a live run.

---

## Gaps Summary

No blocking gaps were found. All plan-declared must-haves are verified at the code level. All six required artifacts exist, are substantive, and are correctly wired. Both test suites pass (4 integration tests, 14 unit tests).

The one human verification item is a live-smoke confirmation that the Spring cache proxy correctly suppresses real outbound HTTP calls — the automated evidence (integration tests + `unless="#result == null"` annotation + correct CaffeineCacheManager setup) is strong, but live behavior under real HTTP requires human observation.

SCOR-04 is partially satisfied: the repository infrastructure is in place. Full end-to-end satisfaction (persist + exclude) is deferred to Phase 4 and Phase 5 respectively, which is the intended design per the PLAN frontmatter.

---

_Verified: 2026-04-06T15:45:00Z_
_Verifier: Claude (gsd-verifier)_
