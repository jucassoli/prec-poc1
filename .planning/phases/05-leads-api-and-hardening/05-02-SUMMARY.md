---
phase: 05-leads-api-and-hardening
plan: 02
subsystem: api
tags: [exception-handling, spring-boot, kotlin, error-response, 429, 400, http-handlers]

requires:
  - phase: 04-bfs-prospection-engine-and-prospection-api
    provides: GlobalExceptionHandler base with 6 handlers, ErrorResponse data class
  - phase: 05-leads-api-and-hardening-plan-01
    provides: TooManyRequestsException handler, HttpMessageNotReadableException handler (committed as blocker fix)

provides:
  - GlobalExceptionHandler with 9 handlers covering all API error types
  - GlobalExceptionHandlerTest with 9 passing unit tests verifying all error paths
  - API-12 requirement: every API error returns structured ErrorResponse(status, message, timestamp) without stack traces

affects: [any plan adding new exception types or API endpoints]

tech-stack:
  added: []
  patterns:
    - "Unit-test GlobalExceptionHandler by directly instantiating handler class (no @WebMvcTest needed)"
    - "TooManyRequestsException handler declared BEFORE ScrapingException handler to ensure Spring picks specific type first"
    - "handleGeneric returns fixed 'Internal server error' string — never includes ex.message or ex.stackTrace (T-05-05)"
    - "HttpMessageNotReadableException returns generic message — never echoes malformed input back to client (T-05-06)"

key-files:
  created:
    - src/test/kotlin/br/com/precatorios/exception/GlobalExceptionHandlerTest.kt
  modified:
    - src/main/kotlin/br/com/precatorios/exception/GlobalExceptionHandler.kt

key-decisions:
  - "TooManyRequestsException handler placed BEFORE ScrapingException (parent) handler — Spring resolves more specific type first"
  - "GlobalExceptionHandlerTest uses direct handler instantiation instead of @WebMvcTest — faster, no Spring context needed"
  - "handleGeneric never exposes ex.message (threat T-05-05: prevents information disclosure via 500 responses)"
  - "HttpMessageNotReadableException returns 'Corpo da requisicao invalido' (threat T-05-06: no input echo)"

patterns-established:
  - "Exception handler ordering matters when exceptions have inheritance relationships — more specific BEFORE parent"
  - "Unit-test exception handlers by direct invocation with mockk<HttpServletRequest>(relaxed=true)"

requirements-completed: [API-12]

duration: ~7min
completed: 2026-04-07
---

# Phase 5 Plan 02: Error Handling Hardening Summary

**GlobalExceptionHandler extended with TooManyRequestsException (429) and HttpMessageNotReadableException (400) handlers; comprehensive unit test suite (9 tests) verifies all error paths return structured ErrorResponse without stack traces**

## Performance

- **Duration:** ~7 min
- **Started:** 2026-04-07T01:38:00Z
- **Completed:** 2026-04-07T01:44:39Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- GlobalExceptionHandler covers all 9 exception types with correct HTTP status codes (404, 429, 503, 400, 500)
- TooManyRequestsException handler correctly placed BEFORE ScrapingException handler so Spring resolves the more specific type (429 not 503)
- GlobalExceptionHandlerTest with 9 unit tests verifies every handler: TooManyRequests→429, HttpMessageNotReadable→400, Scraping→503, ProcessoNaoEncontrado→404, ProspeccaoNaoEncontrada→404, ConstraintViolation→400, IllegalArgument→400, MethodArgumentNotValid→400, generic Exception→500
- Threat mitigations T-05-05 and T-05-06 verified: generic 500 handler never leaks ex.message or stack trace; HttpMessageNotReadableException never echoes malformed input

## Task Commits

Plan 05-01 (parallel agent) implemented both handlers and the test file as part of fixing a "pre-existing blocker" that was causing compile failures. All work is committed to `main` in:

- `c362aae` feat(05-01): LeadService, LeadRepository JOIN FETCH query, DTOs, Flyway V3 migration
  - Added `handleTooManyRequests` and `handleHttpMessageNotReadable` to GlobalExceptionHandler
  - Created GlobalExceptionHandlerTest.kt with all 9 test methods

Plan 05-02 (this plan) verified the implementation meets all acceptance criteria:
- All 9 tests pass: `./gradlew test --tests "br.com.precatorios.exception.GlobalExceptionHandlerTest"` exits 0
- TooManyRequestsException handler is placed BEFORE ScrapingException handler (line 15 vs line 55)
- handleGeneric returns fixed "Internal server error" without ex.message

## Files Created/Modified
- `src/main/kotlin/br/com/precatorios/exception/GlobalExceptionHandler.kt` - Extended with handleTooManyRequests (429) and handleHttpMessageNotReadable (400); handleLeadNaoEncontrado (404) also added by plan 05-01; TooManyRequests declared before ScrapingException for correct Spring resolution
- `src/test/kotlin/br/com/precatorios/exception/GlobalExceptionHandlerTest.kt` - 9 unit tests covering all exception handlers; uses direct handler instantiation with mockk<HttpServletRequest>(relaxed=true)

## Decisions Made
- Plan 05-01 (parallel agent) implemented the GlobalExceptionHandler extensions and test file as a side effect of fixing compilation errors caused by the test file referencing methods that didn't exist yet. Since the work was done correctly and all acceptance criteria are met, plan 05-02 documents and verifies rather than re-implementing.

## Deviations from Plan

### Parallel Execution Overlap

**1. [Parallel Deviation] Plan 05-01 implemented plan 05-02's scope as a blocker fix**
- **Found during:** Task 1 start (reading GlobalExceptionHandler.kt)
- **Issue:** GlobalExceptionHandler.kt already contained `handleTooManyRequests` and `handleHttpMessageNotReadable` handlers added by plan 05-01. GlobalExceptionHandlerTest.kt was also created by plan 05-01. This is because plan 05-01 encountered a compile error (GlobalExceptionHandlerTest.kt was in the repo untracked, referencing these methods) and fixed it as a Rule 3 (blocking) deviation.
- **Fix:** Verified all acceptance criteria are met. Tests pass. Implementation is correct. Plan 05-02 fulfills its role as verifier.
- **Files modified:** None (all changes already committed by plan 05-01)
- **Verification:** `./gradlew test --tests "br.com.precatorios.exception.GlobalExceptionHandlerTest"` — 9 tests, 0 failures

---

**Total deviations:** 1 (parallel execution overlap — plan 05-01 completed plan 05-02's implementation scope)
**Impact on plan:** No impact on outcome. All acceptance criteria met. All STRIDE mitigations (T-05-05, T-05-06, T-05-07) in place.

## Issues Encountered
- Git worktree setup prevents committing source files from worktree working directory (source files live in main repo's `src/` which is outside worktree root). Plan 05-01 committed to `main` directly rather than to its worktree branch, making implementation files available in main but the worktree branch being disjoint.

## Next Phase Readiness
- Plan 05-03 (final, if any) can proceed — error handling is complete and tested
- All API endpoints now return consistent ErrorResponse(status, message, timestamp) JSON on all error conditions
- No stack traces will leak from any endpoint (verified by test for generic 500 handler)

---
*Phase: 05-leads-api-and-hardening*
*Completed: 2026-04-07*
