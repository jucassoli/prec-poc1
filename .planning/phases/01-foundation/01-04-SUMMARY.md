---
phase: 01-foundation
plan: 04
subsystem: infra
tags: [spring-boot, async, swagger, openapi, springdoc, exception-handler, testcontainers]

# Dependency graph
requires:
  - phase: 01-foundation/01-01
    provides: build.gradle.kts with SpringDoc 2.8.6, application.yml with springdoc config
  - phase: 01-foundation/01-03
    provides: JPA entities, repositories, and Flyway migrations for integration test context
provides:
  - AsyncConfig with @EnableAsync and named prospeccaoExecutor (core=2, max=4, queue=25)
  - OpenApiConfig with SpringDoc OpenAPI bean providing Swagger UI at /swagger-ui.html
  - GlobalExceptionHandler @ControllerAdvice with structured ErrorResponse (no stack trace leaks)
  - ProcessoNaoEncontradoException and ScrapingException domain exceptions
  - ApplicationSmokeTest proving full Spring context assembles and Swagger UI is HTTP accessible
affects:
  - phase-4-bfs (uses prospeccaoExecutor via @Async)
  - phase-5-api (extends GlobalExceptionHandler with specific handlers)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@EnableAsync with separate @Bean executor (not embedded in service) to avoid AOP self-invocation"
    - "@ControllerAdvice GlobalExceptionHandler returning ErrorResponse — no stack traces to clients"
    - "Smoke test with RANDOM_PORT + TestRestTemplate for HTTP-level Swagger UI verification"

key-files:
  created:
    - src/main/kotlin/br/com/precatorios/config/AsyncConfig.kt
    - src/main/kotlin/br/com/precatorios/config/OpenApiConfig.kt
    - src/main/kotlin/br/com/precatorios/exception/GlobalExceptionHandler.kt
    - src/main/kotlin/br/com/precatorios/exception/ErrorResponse.kt
    - src/main/kotlin/br/com/precatorios/exception/ProcessoNaoEncontradoException.kt
    - src/main/kotlin/br/com/precatorios/exception/ScrapingException.kt
    - src/test/kotlin/br/com/precatorios/ApplicationSmokeTest.kt
  modified: []

key-decisions:
  - "prospeccaoExecutor is a separate Spring bean (not inline in service) to allow @Async AOP proxy to work correctly"
  - "setAwaitTerminationSeconds(120) used instead of property assignment — ThreadPoolTaskExecutor in Spring 6 requires the setter method"
  - "Smoke test uses RANDOM_PORT + TestRestTemplate (not MockMvc) so Swagger UI HTTP redirect is actually verified"

patterns-established:
  - "AsyncConfig: separate @Configuration class with @EnableAsync and named executor bean"
  - "GlobalExceptionHandler: @ControllerAdvice catching specific then generic exceptions, returning ErrorResponse"
  - "ErrorResponse: data class with status, message, timestamp — all handlers return this type"
  - "Smoke test: RANDOM_PORT + @ServiceConnection Testcontainers for full-stack context verification"

requirements-completed: [INFRA-01, INFRA-04]

# Metrics
duration: 8min
completed: 2026-04-04
---

# Phase 01 Plan 04: Async Infrastructure, OpenAPI Config, Exception Handling, and Smoke Test Summary

**Named async executor (prospeccaoExecutor, core=2/max=4/queue=25), SpringDoc Swagger UI config, @ControllerAdvice exception skeleton, and smoke test confirming full Spring context loads with Swagger UI accessible via HTTP**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-04-04T22:20:00Z
- **Completed:** 2026-04-04T22:30:30Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- AsyncConfig registers prospeccaoExecutor ThreadPoolTaskExecutor bean with correct thread pool sizes (core=2, max=4, queue=25) and graceful shutdown (120s await)
- OpenApiConfig provides SpringDoc OpenAPI bean with Precatorios API metadata for Swagger UI
- GlobalExceptionHandler @ControllerAdvice catches ProcessoNaoEncontradoException (404), ScrapingException (503), and generic Exception (500) returning structured ErrorResponse — no stack traces
- ApplicationSmokeTest proves full Spring context (all beans, entities, repositories, Flyway) assembles without errors and Swagger UI at /swagger-ui.html returns HTTP 200/302

## Task Commits

Each task was committed atomically:

1. **Task 1: Create AsyncConfig, OpenApiConfig, and exception classes** - `55136f2` (feat)
2. **Task 2: Create application smoke test with Swagger UI HTTP verification** - `3e6d4bb` (feat)

## Files Created/Modified
- `src/main/kotlin/br/com/precatorios/config/AsyncConfig.kt` - @Configuration @EnableAsync with prospeccaoExecutor bean (core=2, max=4, queue=25, 120s shutdown)
- `src/main/kotlin/br/com/precatorios/config/OpenApiConfig.kt` - SpringDoc OpenAPI bean with Precatorios API metadata
- `src/main/kotlin/br/com/precatorios/exception/GlobalExceptionHandler.kt` - @ControllerAdvice for structured error responses (404/503/500)
- `src/main/kotlin/br/com/precatorios/exception/ErrorResponse.kt` - data class with status, message, timestamp
- `src/main/kotlin/br/com/precatorios/exception/ProcessoNaoEncontradoException.kt` - domain exception for missing processo
- `src/main/kotlin/br/com/precatorios/exception/ScrapingException.kt` - domain exception for scraping failures
- `src/test/kotlin/br/com/precatorios/ApplicationSmokeTest.kt` - RANDOM_PORT smoke test with Testcontainers PostgreSQL and Swagger UI HTTP verification

## Decisions Made
- `setAwaitTerminationSeconds(120)` called as method (not property) — Kotlin property synthesis for `ThreadPoolTaskExecutor.setAwaitTerminationSeconds` is not available in Spring 6/Boot 3; method call is required
- Smoke test uses `RANDOM_PORT` + `TestRestTemplate` rather than `MockMvc` so the actual Swagger UI HTTP redirect chain (302 to /swagger-ui/index.html) is verified

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed `awaitTerminationSeconds = 120` to `setAwaitTerminationSeconds(120)`**
- **Found during:** Task 1 verification (compileKotlin)
- **Issue:** `awaitTerminationSeconds` is not a Kotlin property on `ThreadPoolTaskExecutor` — compiler error: "Unresolved reference 'awaitTerminationSeconds'"
- **Fix:** Replaced property assignment with explicit setter method call `setAwaitTerminationSeconds(120)`
- **Files modified:** src/main/kotlin/br/com/precatorios/config/AsyncConfig.kt
- **Verification:** `./gradlew compileKotlin` exits 0
- **Committed in:** 55136f2 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - compilation bug)
**Impact on plan:** Minimal — single setter call name correction. No behavior change; shutdown behavior is identical.

## Issues Encountered
- Worktree was initialized at initial commit (pre-wave files); required `git reset --soft FETCH_HEAD` + `git checkout HEAD -- .` to restore all project files before execution could begin

## Next Phase Readiness
- prospeccaoExecutor bean ready for BFS engine to use `@Async("prospeccaoExecutor")` in Phase 4
- GlobalExceptionHandler skeleton ready for Phase 5 to add `MethodArgumentNotValidException` and other specific handlers
- Full Spring context proven to load — INFRA-01 and INFRA-04 requirements satisfied
- No blockers for remaining phases

---
*Phase: 01-foundation*
*Completed: 2026-04-04*

## Self-Check: PASSED

- FOUND: src/main/kotlin/br/com/precatorios/config/AsyncConfig.kt
- FOUND: src/main/kotlin/br/com/precatorios/config/OpenApiConfig.kt
- FOUND: src/main/kotlin/br/com/precatorios/exception/GlobalExceptionHandler.kt
- FOUND: src/main/kotlin/br/com/precatorios/exception/ErrorResponse.kt
- FOUND: src/main/kotlin/br/com/precatorios/exception/ProcessoNaoEncontradoException.kt
- FOUND: src/main/kotlin/br/com/precatorios/exception/ScrapingException.kt
- FOUND: src/test/kotlin/br/com/precatorios/ApplicationSmokeTest.kt
- FOUND commit: 55136f2 (Task 1 - config and exception classes)
- FOUND commit: 3e6d4bb (Task 2 - smoke test)
- FOUND commit: 00b954e (docs - SUMMARY.md)
