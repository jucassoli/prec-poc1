---
phase: 03-cache-and-scoring
plan: 01
subsystem: cache
tags: [caffeine, caching, spring-cache, scrapers]
dependency_graph:
  requires: []
  provides: [CacheConfig, CacheNames, @Cacheable on three scraper methods]
  affects: [EsajScraper, CacScraper, DataJudClient]
tech_stack:
  added: [spring-boot-starter-cache, com.github.ben-manes.caffeine:caffeine (already in build)]
  patterns: [Spring @Cacheable read-through cache, CaffeineCacheManager with named caches, @SpykBean integration test]
key_files:
  created:
    - src/main/kotlin/br/com/precatorios/config/CacheConfig.kt
    - src/test/kotlin/br/com/precatorios/config/CacheConfigTest.kt
  modified:
    - src/main/kotlin/br/com/precatorios/scraper/EsajScraper.kt
    - src/main/kotlin/br/com/precatorios/scraper/CacScraper.kt
    - src/main/kotlin/br/com/precatorios/scraper/DataJudClient.kt
    - src/main/resources/application.yml
decisions:
  - "Used CaffeineCacheManager constructor with all three cache names (simpler than per-cache spec) since all share the same 24h TTL per D-10"
  - "Tests verify doFetch* internal methods called exactly once, proving Spring proxy intercepts the second call at fetchProcesso/fetchPrecatorio/buscarPorNumeroProcesso level"
  - "clearAllMocks(answers = false) in @BeforeEach resets MockK call counters without clearing stubs, enabling independent verify(exactly = N) per test"
metrics:
  duration: "~20 minutes"
  completed: "2026-04-06T15:22:22Z"
  tasks_completed: 2
  files_created: 2
  files_modified: 4
---

# Phase 3 Plan 1: Caffeine Cache Wiring Summary

One-liner: Caffeine read-through cache with 24h TTL wired to three scraper fetch methods via Spring @Cacheable, with integration test proving cache-hit suppresses HTTP calls.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Create CacheConfig with CaffeineCacheManager | 84e32f3 | CacheConfig.kt, application.yml |
| 2 (RED) | Add failing cache integration tests | 8effda4 | CacheConfigTest.kt |
| 2 (GREEN) | Add @Cacheable to three scraper methods | 27fb920 | EsajScraper.kt, CacScraper.kt, DataJudClient.kt, CacheConfigTest.kt |

## What Was Built

**CacheConfig.kt** — defines `CacheNames` object (PROCESSOS, PRECATORIOS, DATAJUD constants) and `@Configuration @EnableCaching CacheConfig` bean with `CaffeineCacheManager` configured for 24h TTL, maximumSize 1000, and stats recording.

**Scraper annotations:**
- `EsajScraper.fetchProcesso` — `@Cacheable(cacheNames = [CacheNames.PROCESSOS], unless = "#result == null")`
- `CacScraper.fetchPrecatorio` — `@Cacheable(cacheNames = [CacheNames.PRECATORIOS], unless = "#result == null")`
- `DataJudClient.buscarPorNumeroProcesso` — `@Cacheable(cacheNames = [CacheNames.DATAJUD], unless = "#result == null")`
- `buscarPorNome` and `buscarPorMunicipio` intentionally NOT cached (per D-08, D-09)

**CacheConfigTest.kt** — `@SpringBootTest` + `@Testcontainers` + `@SpykBean` integration test with 4 test methods proving: fetchProcesso cache hit, fetchPrecatorio cache hit, buscarPorNumeroProcesso cache hit, two different keys produce two separate cache entries.

**application.yml** — actuator exposure updated from `include: health` to `include: health,caches` for cache monitoring.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Caffeine import package name**
- **Found during:** Task 1 compilation
- **Issue:** Plan specified `com.github.ben-manes.caffeine.cache.Caffeine` (with hyphen) but the actual Java package is `com.github.benmanes.caffeine.cache.Caffeine` (no hyphen — artifact group ID uses hyphen, package name uses camelCase)
- **Fix:** Corrected import to `com.github.benmanes.caffeine.cache.Caffeine`
- **Files modified:** CacheConfig.kt
- **Commit:** 84e32f3

**2. [Rule 1 - Bug] Fixed MockK accumulated call counts across tests**
- **Found during:** Task 2 GREEN phase — "two different process numbers" test saw 3 calls instead of 2
- **Issue:** `@SpykBean` reuses the same spy instance across tests; MockK accumulates call history from prior tests. `@BeforeEach` only cleared caches, not mock call records.
- **Fix:** Added `clearAllMocks(answers = false)` to `@BeforeEach` to reset call counters while preserving any stub configuration
- **Files modified:** CacheConfigTest.kt
- **Commit:** 27fb920

## Verification Results

```
./gradlew compileKotlin         → BUILD SUCCESSFUL
./gradlew test --tests "*.CacheConfigTest"  → 4 tests, 0 failures
grep -r "@Cacheable" src/main/kotlin/       → 3 annotations (fetchProcesso, fetchPrecatorio, buscarPorNumeroProcesso)
grep -r "@EnableCaching" src/main/kotlin/   → 1 annotation (CacheConfig)
```

## Known Stubs

None — all cache wiring is functional, no placeholder data.

## Threat Flags

No new security-relevant surface beyond what is documented in the plan's threat model. `maximumSize(1000)` mitigates T-03-01 (DoS via unbounded cache growth). Actuator `/caches` endpoint exposes only cache names and hit/miss statistics, no cached values (T-03-02 accepted).
