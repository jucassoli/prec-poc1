---
phase: 02-scraper-layer
plan: 01
subsystem: scraper-infrastructure
tags: [resilience4j, rate-limiting, retry, config-binding, kotlin, spring-boot]
dependency_graph:
  requires: []
  provides: [esajRateLimiter, cacRateLimiter, datajudRateLimiter, esajRetry, cacRetry, datajudRetry, ScraperProperties, TooManyRequestsException]
  affects: [02-02, 02-03, 02-04]
tech_stack:
  added: [resilience4j-spring-boot3:2.3.0, spring-boot-starter-aop]
  patterns: [ConfigurationProperties constructor binding, programmatic RateLimiter/Retry bean creation, IntervalBiFunction for 429 backoff]
key_files:
  created:
    - src/main/kotlin/br/com/precatorios/config/ScraperProperties.kt
    - src/main/kotlin/br/com/precatorios/config/ResilienceConfig.kt
    - src/main/kotlin/br/com/precatorios/exception/TooManyRequestsException.kt
    - src/test/kotlin/br/com/precatorios/config/ResilienceConfigTest.kt
    - src/test/kotlin/br/com/precatorios/config/ScraperPropertiesTest.kt
  modified:
    - build.gradle.kts
    - src/main/resources/application.yml
    - src/main/kotlin/br/com/precatorios/exception/ScrapingException.kt
    - src/main/kotlin/br/com/precatorios/PrecatoriosApiApplication.kt
decisions:
  - Use programmatic RateLimiter/Retry construction (not annotation-based) to avoid AOP proxy pitfalls
  - Use @ConfigurationPropertiesScan on main application class for constructor-binding discovery
  - Make ScrapingException open to allow TooManyRequestsException subclassing
  - IntervalBiFunction returns 60s on TooManyRequestsException, exponential backoff otherwise
metrics:
  duration: 3m
  completed_date: "2026-04-06T12:20:44Z"
  tasks_completed: 2
  files_created: 5
  files_modified: 4
---

# Phase 2 Plan 1: Resilience4j Infrastructure Summary

**One-liner:** Resilience4j rate limiter and retry beans (per-scraper) with @ConfigurationProperties constructor binding for all scraper.* config keys.

## What Was Built

Added Resilience4j dependency to the project and created the foundational resilience infrastructure that all three scrapers (esaj, cac, datajud) depend on:

- **ScraperProperties** — `@ConfigurationProperties(prefix = "scraper")` data class binding all existing `scraper.*` YAML keys via constructor binding (no setters required)
- **ResilienceConfig** — 6 programmatic Spring beans: 3 `RateLimiter` beans (esaj/cac: 1 req/2s, datajud: 5 req/1s) and 3 `Retry` beans (maxAttempts=3, exponential backoff 1s/2x, 60s pause on HTTP 429)
- **TooManyRequestsException** — extends `ScrapingException`, used as the signal for the 60s 429 backoff in `IntervalBiFunction`
- **Resilience4j YAML block** — `resilience4j.ratelimiter` and `resilience4j.retry` sections added to `application.yml` (for reference/metrics; actual bean config is programmatic)

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 269a5b8 | feat(02-01): add Resilience4j deps, ScraperProperties, ResilienceConfig, TooManyRequestsException |
| 2 | 9134b2e | test(02-01): add ResilienceConfigTest and ScraperPropertiesTest, fix constructor binding |

## Verification

- `./gradlew compileKotlin` — PASSED
- `./gradlew test --tests "*ResilienceConfigTest*" --tests "*ScraperPropertiesTest*"` — PASSED (10 tests)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ScrapingException was final, blocking TooManyRequestsException inheritance**
- **Found during:** Task 1 compilation
- **Issue:** Kotlin classes are final by default; `TooManyRequestsException extends ScrapingException` failed to compile
- **Fix:** Added `open` modifier to `ScrapingException`
- **Files modified:** `src/main/kotlin/br/com/precatorios/exception/ScrapingException.kt`
- **Commit:** 269a5b8

**2. [Rule 1 - Bug] @ConfigurationProperties with @Configuration used JavaBean binding requiring setters**
- **Found during:** Task 2 test run — `No setter found for property: api-key`
- **Issue:** When `@Configuration` is present alongside `@ConfigurationProperties`, Spring uses JavaBean binding (requires `var`/setters). The plan intended `val` constructor binding for the data class.
- **Fix:** Removed `@Configuration` from `ScraperProperties`, added `@ConfigurationPropertiesScan` to `PrecatoriosApiApplication` for auto-discovery via constructor binding
- **Files modified:** `src/main/kotlin/br/com/precatorios/config/ScraperProperties.kt`, `src/main/kotlin/br/com/precatorios/PrecatoriosApiApplication.kt`
- **Commit:** 9134b2e

## Known Stubs

None — all beans are fully configured and functional.

## Threat Flags

No new security surface introduced. All config is server-side YAML only (no user input paths).

## Self-Check: PASSED

- [x] `src/main/kotlin/br/com/precatorios/config/ScraperProperties.kt` — exists
- [x] `src/main/kotlin/br/com/precatorios/config/ResilienceConfig.kt` — exists
- [x] `src/main/kotlin/br/com/precatorios/exception/TooManyRequestsException.kt` — exists
- [x] `src/test/kotlin/br/com/precatorios/config/ResilienceConfigTest.kt` — exists
- [x] `src/test/kotlin/br/com/precatorios/config/ScraperPropertiesTest.kt` — exists
- [x] Commit 269a5b8 — verified
- [x] Commit 9134b2e — verified
- [x] All tests green
