# Phase 4: BFS Prospection Engine and Prospection API - Context

**Gathered:** 2026-04-06
**Status:** Ready for planning

<domain>
## Phase Boundary

A working async BFS prospection engine that starts from a seed process, recursively discovers co-creditors up to configurable depth, scores each lead, and persists results — exposed via five prospection REST endpoints with 202/polling contract.

</domain>

<decisions>
## Implementation Decisions

### BFS Traversal Strategy
- **D-01:** Classic level-by-level BFS — process all nodes at current depth before going deeper. ArrayDeque as the queue. profundidadeMaxima is respected naturally by depth tracking.
- **D-02:** BFS expansion via creditor name search: seed process -> extract creditors -> buscarPorNome for each creditor -> find their other processes -> repeat at next depth level.
- **D-03:** Cap name search results at 10 processes per creditor name — prevents common names (e.g., "Fazenda do Estado") from exploding the queue. Configurable in application.yml.
- **D-04:** One lead per (creditor, precatorio) pair — same creditor can produce multiple leads if they have precatorios from different processes. Richer data, matches the Lead entity model (credor_id + precatorio_id).
- **D-05:** Job-local visited set (Set<String> of process numbers) prevents cycles and redundant scraping within a single prospection run.

### Failure Isolation
- **D-06:** When a scraper call fails for one process during BFS: log the failure, skip that branch, continue BFS with remaining queue items. Resilience4j retry/circuit-breaker already handles transient failures at the scraper level.
- **D-07:** Prospection final status is CONCLUIDA even when some scraper calls failed — failures are logged in erroMensagem as an aggregated list. Matches PROS-08.
- **D-08:** Each lead is persisted with @Transactional(REQUIRES_NEW) so a single persist failure doesn't roll back the entire run.

### Prospection Filters
- **D-09:** Filters (entidadesDevedoras, valorMinimo, apenasAlimentar, apenasPendentes) are applied during BFS traversal — leads that don't match filters are not scored or persisted. Saves time and DB space.
- **D-10:** Filters only affect lead creation, NOT BFS expansion — all branches are explored regardless of filters. A filtered-out creditor's other processes are still visited, because those processes may contain other creditors whose precatorios DO match.
- **D-11:** credoresEncontrados counter counts only leads that pass filters and are persisted.

### API Response Contract
- **D-12:** POST /api/v1/prospeccao accepts flat JSON: `{"processoSemente": "...", "profundidadeMaxima": 2, "maxCredores": 50, "entidadesDevedoras": [...], "valorMinimo": 50000, "apenasAlimentar": false, "apenasPendentes": true}`. All fields optional except processoSemente.
- **D-13:** POST returns HTTP 202 with `{"prospeccaoId": <id>}`. CNJ format validation on processoSemente returns 400 synchronously before async dispatch.
- **D-14:** GET /api/v1/prospeccao/{id} returns status with three incrementing counters: processosVisitados, credoresEncontrados, leadsQualificados. Includes `Retry-After: 10` header while EM_ANDAMENTO.
- **D-15:** When CONCLUIDA, GET response embeds `leads[]` array inline with full lead data (score, scoreDetalhes, credor info, precatorio info).
- **D-16:** When ERRO, GET response includes erroMensagem describing the failure.
- **D-17:** GET /api/v1/prospeccao lists all prospection runs with pagination, filterable by status query parameter.

### Claude's Discretion
- BfsProspeccaoEngine internal implementation details (queue management, depth tracking)
- ProspeccaoRequest DTO field validation annotations
- ProspeccaoResponse DTO structure (how leads[] is nested)
- CNJ process number regex pattern
- How erroMensagem aggregates multiple failures (newline-separated, JSON array, etc.)
- Search result ordering from buscarPorNome (which 10 to keep if more than 10 results)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project context
- `.planning/PROJECT.md` — Core value, requirements, constraints, key decisions
- `.planning/REQUIREMENTS.md` — Requirement IDs PROS-01 through PROS-08, API-01 through API-05
- `.planning/ROADMAP.md` §Phase 4 — Success criteria, plan structure (04-01 BFS core, 04-02 filters, 04-03 REST endpoints)

### Prior phase context
- `.planning/phases/02-scraper-layer/02-CONTEXT.md` — Scraper design decisions (graceful degradation, Resilience4j integration, centralized selectors)
- `.planning/phases/03-cache-and-scoring/03-CONTEXT.md` — Cache scope decisions, scoring thresholds, scoreDetalhes format

### Existing code (BFS dependencies)
- `src/main/kotlin/br/com/precatorios/config/AsyncConfig.kt` — `@Async("prospeccaoExecutor")` ThreadPoolTaskExecutor already configured
- `src/main/kotlin/br/com/precatorios/domain/Prospeccao.kt` — Entity with processoSemente, status, profundidadeMax, maxCredores, counters
- `src/main/kotlin/br/com/precatorios/domain/Lead.kt` — Entity with score, scoreDetalhes, prospeccao/credor/precatorio FK links
- `src/main/kotlin/br/com/precatorios/domain/enums/StatusProspeccao.kt` — EM_ANDAMENTO, CONCLUIDA, ERRO enum

### Existing code (scraper + scoring integration)
- `src/main/kotlin/br/com/precatorios/scraper/EsajScraper.kt` — fetchProcesso() and buscarPorNome() for BFS expansion
- `src/main/kotlin/br/com/precatorios/scraper/CacScraper.kt` — fetchPrecatorio() for precatorio data
- `src/main/kotlin/br/com/precatorios/scraper/DataJudClient.kt` — buscarPorNumeroProcesso() for enrichment
- `src/main/kotlin/br/com/precatorios/service/ScoringService.kt` — score() for lead scoring
- `src/main/kotlin/br/com/precatorios/repository/ProspeccaoRepository.kt` — Existing repository

### Existing code (patterns to follow)
- `src/main/kotlin/br/com/precatorios/controller/ProcessoController.kt` — Controller pattern for ProspeccaoController
- `src/main/kotlin/br/com/precatorios/exception/GlobalExceptionHandler.kt` — Error handler to extend for 400/404

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AsyncConfig` with `prospeccaoExecutor` — async dispatch infrastructure ready
- `Prospeccao` entity with all fields needed (processoSemente, status, counters, profundidadeMax, maxCredores)
- `Lead` entity with prospeccao/credor/precatorio FK links — matches D-04 (one lead per creditor+precatorio pair)
- `ScoringService` — ready to score each lead in the BFS loop
- `ProspeccaoRepository` — existing JPA repository for Prospeccao
- Three scrapers with @Cacheable — BFS benefits from cache hits on revisited data

### Established Patterns
- `@Service` + `@Repository` layering from Phase 1
- `@ConfigurationProperties` for externalizing config (ScraperProperties, ScoringProperties)
- `@Transactional(REQUIRES_NEW)` per-persist pattern already decided in Phase 1
- Resilience4j per-scraper rate limiters from Phase 2
- Controller + DTO pattern from Phase 2 lookup endpoints

### Integration Points
- BfsProspeccaoEngine calls all three scrapers + ScoringService in the BFS loop
- ProspeccaoController dispatches to BfsProspeccaoEngine via @Async
- Lead persistence uses existing LeadRepository with REQUIRES_NEW transactions
- Status updates on Prospeccao entity (processosVisitados, credoresEncontrados counters)

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

*Phase: 04-bfs-prospection-engine-and-prospection-api*
*Context gathered: 2026-04-06*
