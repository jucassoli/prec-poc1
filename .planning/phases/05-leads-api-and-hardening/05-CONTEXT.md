# Phase 5: Leads API and Hardening - Context

**Gathered:** 2026-04-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Leads management endpoints (filtered list, contact status updates), structured error responses across all API errors, Testcontainers integration tests for the full stack, and operational hardening (stale job recovery, DataJud health check). This phase completes the v1 milestone.

</domain>

<decisions>
## Implementation Decisions

### Lead Filtering Rules
- **D-01:** Multiple filters combine with AND logic — scoreMinimo=60 AND statusContato=NAO_CONTACTADO AND entidadeDevedora=Estado de SP
- **D-02:** Zero-score leads excluded by default; add `?incluirZero=true` query param to include them. Aligns with SCOR-04 and existing `findByScoreGreaterThan`
- **D-03:** Support sort by `score` (DESC, default) and `dataCriacao` (DESC) via Spring Data `?sort=` parameter

### Lead Response Shape
- **D-04:** GET /leads returns summary per lead: id, score, scoreDetalhes, statusContato, dataCriacao, credor(id, nome), precatorio(id, numero, valorAtualizado, entidadeDevedora, statusPagamento). Requires JOIN FETCH for credor and precatorio to avoid N+1

### Integration Test Strategy
- **D-05:** Scrapers mocked with @MockBean returning fixed data — no WireMock, no live TJ-SP dependency
- **D-06:** Single-depth BFS with 2-3 mock parties, depth=1. Tests full pipeline: BFS -> persist -> score -> API
- **D-07:** No @IntegrationLive tag — live validation is manual, no automated selector drift detection

### Stale Job Recovery
- **D-08:** Startup-only recovery via ApplicationRunner — marks stale EM_ANDAMENTO jobs as ERRO on boot
- **D-09:** Error message includes original start timestamp: "Interrompida por reinicio (iniciada em {dataInicio})"

### DataJud Health Indicator
- **D-10:** Custom Spring Boot HealthIndicator that sends a lightweight DataJud query. UP if 200, DOWN if timeout/error. Visible in /actuator/health. No caching or retry

### Claude's Discretion
- Status transition validation on PATCH /leads/{id}/status — Claude may allow any valid StatusContato enum or constrain transitions
- Exact mock fixture data for integration tests
- DataJud health check query payload

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

No external specs — requirements fully captured in decisions above and in REQUIREMENTS.md (API-10, API-11, API-12).

### Key source files
- `src/main/kotlin/br/com/precatorios/domain/Lead.kt` — Lead entity with score, statusContato, credor, precatorio relations
- `src/main/kotlin/br/com/precatorios/repository/LeadRepository.kt` — Existing findByScoreGreaterThan, needs JOIN FETCH extension
- `src/main/kotlin/br/com/precatorios/exception/GlobalExceptionHandler.kt` — Already handles 6 exception types with ErrorResponse
- `src/main/kotlin/br/com/precatorios/exception/ErrorResponse.kt` — Already has status, message, timestamp (API-12 partially done)
- `src/main/kotlin/br/com/precatorios/domain/enums/StatusContato.kt` — 5 statuses: NAO_CONTACTADO, CONTACTADO, INTERESSADO, CONTRATADO, DESCARTADO
- `src/main/kotlin/br/com/precatorios/domain/Prospeccao.kt` — Prospeccao entity with status field for stale job recovery
- `src/main/kotlin/br/com/precatorios/engine/BfsProspeccaoEngine.kt` — BFS engine for integration test mock setup

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GlobalExceptionHandler` already covers most exception types with `ErrorResponse(status, message, timestamp)` — API-12 is largely done, may just need a catch-all for `TooManyRequestsException` and verification
- `LeadRepository.findByScoreGreaterThan` exists for zero-score exclusion
- `StatusContato` enum already defined with all 5 values
- `LeadSummaryDTO` exists from Phase 4 ProspeccaoStatusDTO — may be reusable or need extension

### Established Patterns
- Controllers use `@RestController` + `@RequestMapping("/api/v1/...")` with DTOs
- All repos extend `JpaRepository` with Spring Data query derivation
- Async execution via `@Async("prospeccaoExecutor")` with `ThreadPoolTaskExecutor`
- Testcontainers with `@ServiceConnection` pattern used in `RepositoryIntegrationTest`

### Integration Points
- New `LeadController` wires into existing `/api/v1/` prefix
- Stale job recovery reads `ProspeccaoRepository` for EM_ANDAMENTO status
- Health indicator calls `DataJudClient` for connectivity check
- Integration test needs `BfsProspeccaoEngine`, `ProspeccaoService`, and all three scrapers

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

*Phase: 05-leads-api-and-hardening*
*Context gathered: 2026-04-06*
