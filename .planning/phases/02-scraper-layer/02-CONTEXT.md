# Phase 2: Scraper Layer - Context

**Gathered:** 2026-04-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Working e-SAJ, CAC/SCP, and DataJud scrapers validated against live TJ-SP infrastructure, with the four lookup REST endpoints that expose them — no business logic yet, just fetching and returning data.

</domain>

<decisions>
## Implementation Decisions

### Rate Limiting Strategy
- **D-01:** Per-scraper rate limiters (not a single centralized limiter) — each scraper (e-SAJ, CAC/SCP, DataJud) has its own Resilience4j RateLimiter instance configured independently
- **D-02:** Use Resilience4j RateLimiter composed with Retry and CircuitBreaker — declarative, configurable via application.yml, already in build.gradle.kts
- **D-03:** 2s inter-request delay per scraper, exponential backoff on failure (3 retries), 60s pause on HTTP 429 — all from existing application.yml config

### e-SAJ Selector Validation
- **D-04:** Graceful degradation when CSS selectors fail — do not throw exceptions on missing elements
- **D-05:** Return partial data with a flag indicating which fields failed extraction — caller decides whether to accept partial results
- **D-06:** Log warnings on selector mismatches (not errors) — allows monitoring without breaking the pipeline
- **D-07:** Extract all CSS selectors into a centralized constants class (EsajSelectors) for single-point updates when HTML structure changes

### Claude's Discretion
- CAC/SCP session management details (ViewState lifecycle, cookie persistence, Jsoup vs HtmlUnit fallback)
- DTO structure for lookup endpoints (field naming, nesting)
- Error response contract for 404/500 cases (building on existing GlobalExceptionHandler and ErrorResponse)
- DataJud Elasticsearch DSL query construction

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project context
- `.planning/PROJECT.md` — Core value, requirements, constraints, key decisions
- `.planning/REQUIREMENTS.md` — Requirement IDs ESAJ-01 through ESAJ-07, CAC-01 through CAC-03, DATJ-01 through DATJ-03, API-06 through API-09
- `.planning/ROADMAP.md` §Phase 2 — Success criteria, plan structure, dependency on Phase 1

### Existing code
- `src/main/resources/application.yml` — All scraper config (base URLs, delays, timeouts, API key, user-agent)
- `src/main/kotlin/br/com/precatorios/exception/ScrapingException.kt` — Existing exception for scraper errors
- `src/main/kotlin/br/com/precatorios/exception/GlobalExceptionHandler.kt` — Existing error handler to extend
- `src/main/kotlin/br/com/precatorios/domain/` — JPA entities that scraped data maps to

### Prior research
- `.planning/phases/01-foundation/01-RESEARCH.md` — Stack versions, CAC/SCP ViewState notes, e-SAJ selector hypotheses

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ScrapingException` — ready for wrapping scraper failures
- `GlobalExceptionHandler` + `ErrorResponse` — extend for scraper-specific HTTP errors
- `ProcessoNaoEncontradoException` — 404 pattern already established
- `application.yml` scraper config — base URLs, delays, timeouts, user-agent all externalized

### Established Patterns
- Spring `@Service` + `@Repository` layering from Phase 1
- Testcontainers for integration testing
- Config externalization via `@ConfigurationProperties`

### Integration Points
- Scraper services will be called by BFS engine in Phase 4
- Lookup controllers expose scrapers as REST endpoints
- Scraped data maps to existing JPA entities (Processo, Credor, Precatorio)

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

*Phase: 02-scraper-layer*
*Context gathered: 2026-04-05*
