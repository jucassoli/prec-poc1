# Phase 2: Scraper Layer - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-05
**Phase:** 02-scraper-layer
**Areas discussed:** Rate limiting strategy, e-SAJ selector validation approach

---

## Rate Limiting Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Per-scraper | Each scraper has its own independent rate limiter | ✓ |
| Centralized | Single shared rate limiter across all scrapers | |

**User's choice:** Per-scraper
**Notes:** User selected without elaboration — clear preference.

### Follow-up: Implementation mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| Resilience4j RateLimiter | Declarative, composable with Retry/CircuitBreaker, configurable via yml | ✓ |
| Manual delay (Thread.sleep) | Simple fixed delay, no extra dependency | |

**User's choice:** Resilience4j RateLimiter (Recommended)

---

## e-SAJ Selector Validation Approach

| Option | Description | Selected |
|--------|-------------|----------|
| Fail-fast assertions | Throw exception when selector returns empty | |
| Graceful degradation | Return partial data, log warnings | ✓ |

**User's choice:** Graceful degradation
**Notes:** User selected without elaboration — clear preference.

### Follow-up: Degradation signaling

| Option | Description | Selected |
|--------|-------------|----------|
| Partial with flag | Return what succeeded + list of failed fields | ✓ |
| Null silencioso | Field is null with no indication of selector failure | |

**User's choice:** Parcial com flag (Recommended)

---

## Claude's Discretion

- CAC/SCP session management (ViewState lifecycle, cookie persistence, fallback trigger)
- DTO structure for lookup endpoints
- Error response contract (extending existing GlobalExceptionHandler)
- DataJud Elasticsearch DSL query construction

## Deferred Ideas

None.
