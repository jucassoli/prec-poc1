# Roadmap: Precatórios API — TJ-SP Lead Prospecting

## Overview

Five phases deliver a working Kotlin/Spring Boot REST API for automated precatório lead prospecting at TJ-SP. The sequence is dependency-driven: the database schema and async infrastructure must be locked in before any scraping code is written; scrapers must be validated against live TJ-SP portals before the BFS engine is built on top of them; the BFS engine and scoring logic must be stable before REST controllers are finalized; and hardening with integration tests comes last once the full stack is working end-to-end. Each phase produces a coherent, independently verifiable capability.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Foundation** - Project skeleton, DB schema, JPA entities, Docker Compose, async infrastructure
- [ ] **Phase 2: Scraper Layer** - e-SAJ, CAC/SCP, and DataJud data fetchers with live validation and lookup endpoints
- [ ] **Phase 3: Cache and Scoring** - Caffeine read-through cache and configurable scoring engine
- [ ] **Phase 4: BFS Prospection Engine and Prospection API** - Async recursive co-creditor discovery with REST endpoints
- [ ] **Phase 5: Leads API and Hardening** - Leads management endpoints, integration tests, and operational hardening

## Phase Details

### Phase 1: Foundation
**Goal**: A running Spring Boot skeleton with full DB schema, JPA entities, async executor config, Docker Compose, and Flyway migrations — everything needed to test against a real database before any scraping code is written
**Depends on**: Nothing (first phase)
**Requirements**: INFRA-01, INFRA-02, INFRA-03, INFRA-04, INFRA-05, PERS-01, PERS-02, PERS-03, PERS-04, PERS-05, PERS-06
**Success Criteria** (what must be TRUE):
  1. `./gradlew bootRun` starts the application without errors on JVM 21
  2. `docker-compose up` starts API and PostgreSQL; API reaches `/actuator/health` healthy
  3. Flyway applies all migrations automatically on startup; all five tables exist with correct schema
  4. All five JPA entities (Processo, Credor, Precatorio, Prospeccao, Lead) persist and retrieve correctly via repository tests
  5. Swagger UI is reachable at `/swagger-ui.html` (even with no endpoints yet beyond actuator)
**Plans**: 4 plans

Plans:
- [x] 01-01-PLAN.md — Gradle project setup (Kotlin 2.2.0, Spring Boot 3.5.3, all dependencies, JVM 21 toolchain)
- [x] 01-02-PLAN.md — Docker Compose + Flyway migrations (health-checked PostgreSQL 17, V1__create_tables.sql)
- [x] 01-03-PLAN.md — JPA entities + repositories (five entities, five repos, Testcontainers 2.0 integration test)
- [x] 01-04-PLAN.md — Async infrastructure skeleton (AsyncConfig with prospeccaoExecutor, OpenApiConfig, GlobalExceptionHandler, smoke test)

### Phase 2: Scraper Layer
**Goal**: Working e-SAJ, CAC/SCP, and DataJud scrapers validated against live TJ-SP infrastructure, with the four lookup REST endpoints that expose them — no business logic yet, just fetching and returning data
**Depends on**: Phase 1
**Requirements**: ESAJ-01, ESAJ-02, ESAJ-03, ESAJ-04, ESAJ-05, ESAJ-06, ESAJ-07, CAC-01, CAC-02, CAC-03, DATJ-01, DATJ-02, DATJ-03, API-06, API-07, API-08, API-09
**Success Criteria** (what must be TRUE):
  1. `GET /api/v1/processo/{numero}` returns structured process data (parties, incidents) fetched from live e-SAJ for a known TJ-SP process number
  2. `GET /api/v1/precatorio/{numero}` returns precatório data (value, status, chronological position) fetched from live CAC/SCP
  3. `POST /api/v1/datajud/buscar` proxies a DataJud query and returns structured results using the configurable API key
  4. `GET /api/v1/processo/buscar?nome=` returns process search results from e-SAJ
  5. All three scrapers enforce the 2s inter-request delay, retry with exponential backoff on failure, and pause 60s on HTTP 429
  6. CAC/SCP scraper completes a ViewState GET/POST cycle and returns precatório data without session errors
**Plans**: 4 plans

Plans:
- [x] 02-01-PLAN.md — Resilience4j dependencies, ScraperProperties config binding, ResilienceConfig with per-scraper RateLimiter/Retry beans
- [x] 02-02-PLAN.md — EsajScraper with centralized EsajSelectors, graceful degradation, Resilience4j integration, unit tests with HTML fixtures
- [x] 02-03-PLAN.md — CacScraper with ViewState session management and DataJudClient with WebClient, unit tests
- [x] 02-04-PLAN.md — Lookup endpoints: ProcessoController, PrecatorioController, DataJudController with DTOs, input validation, controller tests

### Phase 3: Cache and Scoring
**Goal**: Caffeine read-through cache wired to all three scrapers (24h TTL, no negative caching), and a fully configurable scoring engine that produces a 0-100 score with per-criterion breakdown for any precatorio
**Depends on**: Phase 2
**Requirements**: CACHE-01, CACHE-02, SCOR-01, SCOR-02, SCOR-03, SCOR-04
**Success Criteria** (what must be TRUE):
  1. A second identical request to e-SAJ or CAC/SCP within 24h is served from Caffeine cache without any outbound HTTP call
  2. Cache is keyed by process/precatorio number so two different callers looking up the same number share the cached result
  3. ScoringService returns a score 0-100 with a `scoreDetalhes` map showing the contribution of each of the five criteria (value, debtor entity, payment status, chronological position, nature)
  4. Changing a scoring weight in `application.yml` and restarting changes the score without any code modification
  5. Leads scoring 0 across all criteria are persisted but excluded from default lead list results
**Plans**: 2 plans

Plans:
- [x] 03-01-PLAN.md — CacheConfig with CaffeineCacheManager (three named caches, 24h TTL), @Cacheable on three scraper fetch methods, cache integration test
- [x] 03-02-PLAN.md — ScoringProperties config, ScoringService with five-criterion scoring, application.yml scoring section, unit tests, LeadRepository score filter

### Phase 4: BFS Prospection Engine and Prospection API
**Goal**: A working async BFS prospection engine that starts from a seed process, recursively discovers co-creditors up to configurable depth, scores each lead, and persists results — exposed via the five prospection REST endpoints with 202/polling contract
**Depends on**: Phase 3
**Requirements**: PROS-01, PROS-02, PROS-03, PROS-04, PROS-05, PROS-06, PROS-07, PROS-08, API-01, API-02, API-03, API-04, API-05
**Success Criteria** (what must be TRUE):
  1. `POST /api/v1/prospeccao` returns HTTP 202 with a `prospeccaoId` within 500ms even when scrapers are slow (async dispatch confirmed)
  2. An invalid CNJ process number returns HTTP 400 synchronously before any async dispatch
  3. `GET /api/v1/prospeccao/{id}` returns status `EM_ANDAMENTO` with incrementing `processosVisitados` and `credoresEncontrados` counters while the job runs
  4. `GET /api/v1/prospeccao/{id}` returns status `CONCLUIDA` with a full scored lead list once the BFS completes
  5. A prospection with a partial scraping failure (one process unreachable) completes with `CONCLUIDA` status and logs the failure in `erro_mensagem` — it does not abort entirely
  6. `GET /api/v1/prospeccao` lists all runs with pagination; filterable by status
**Plans**: 3 plans

Plans:
- [x] 04-01-PLAN.md — Flyway V2 migration (leadsQualificados), ProspeccaoLeadPersistenceHelper with REQUIRES_NEW transactions, ProspeccaoService, BFS engine core with @Async dispatch and partial-failure isolation
- [x] 04-02-PLAN.md — BFS filters (entidadesDevedoras, valorMinimo, apenasAlimentar, apenasPendentes), depth control (profundidadeMaxima), maxCredores early-exit, maxSearchResults config
- [x] 04-03-PLAN.md — ProspeccaoController with POST (202 + id), GET status (Retry-After, counters, leads on CONCLUIDA, erroMensagem on ERRO), GET list with pagination and status filter; DTOs, CNJ validation

### Phase 5: Leads API and Hardening
**Goal**: The leads management endpoints (filtered list, contact status updates, structured error responses) are live; the full stack is covered by Testcontainers integration tests; operational concerns (stale job recovery, DataJud health check) are addressed
**Depends on**: Phase 4
**Requirements**: API-10, API-11, API-12
**Success Criteria** (what must be TRUE):
  1. `GET /api/v1/leads` returns a paginated list sorted by score descending, filterable by `scoreMinimo`, `statusContato`, and `entidadeDevedora`, with no N+1 queries
  2. `PATCH /api/v1/leads/{id}/status` updates contact status and optional note; the change is immediately visible in subsequent GET /leads responses
  3. All API errors (404, 400, 500, scraping failures) return structured JSON with `status`, `message`, and `timestamp` — no stack traces in responses
  4. Testcontainers integration test starts PostgreSQL, runs migrations, executes a full prospection with mock scrapers, and asserts scored leads in the database
  5. On application startup, any `EM_ANDAMENTO` jobs left from a previous crashed run are reset to `ERRO` with a recovery message
**Plans**: 3 plans

Plans:
- [ ] 05-01-PLAN.md — Leads REST endpoints (LeadController GET /leads with JOIN FETCH, pagination, filters, PATCH /leads/{id}/status)
- [ ] 05-02-PLAN.md — Error handling hardening (TooManyRequestsException 429, HttpMessageNotReadableException 400, comprehensive tests)
- [ ] 05-03-PLAN.md — Integration tests and operational hardening (Testcontainers full-stack test, StaleJobRecoveryRunner, DataJudHealthIndicator)

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4 -> 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation | 0/4 | Not started | - |
| 2. Scraper Layer | 0/4 | Not started | - |
| 3. Cache and Scoring | 0/2 | Not started | - |
| 4. BFS Prospection Engine and Prospection API | 0/3 | Not started | - |
| 5. Leads API and Hardening | 0/3 | Not started | - |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| INFRA-01 | Phase 1 | Pending |
| INFRA-02 | Phase 1 | Pending |
| INFRA-03 | Phase 1 | Pending |
| INFRA-04 | Phase 1 | Pending |
| INFRA-05 | Phase 1 | Pending |
| PERS-01 | Phase 1 | Pending |
| PERS-02 | Phase 1 | Pending |
| PERS-03 | Phase 1 | Pending |
| PERS-04 | Phase 1 | Pending |
| PERS-05 | Phase 1 | Pending |
| PERS-06 | Phase 1 | Pending |
| ESAJ-01 | Phase 2 | Pending |
| ESAJ-02 | Phase 2 | Pending |
| ESAJ-03 | Phase 2 | Pending |
| ESAJ-04 | Phase 2 | Pending |
| ESAJ-05 | Phase 2 | Pending |
| ESAJ-06 | Phase 2 | Pending |
| ESAJ-07 | Phase 2 | Pending |
| CAC-01 | Phase 2 | Pending |
| CAC-02 | Phase 2 | Pending |
| CAC-03 | Phase 2 | Pending |
| DATJ-01 | Phase 2 | Pending |
| DATJ-02 | Phase 2 | Pending |
| DATJ-03 | Phase 2 | Pending |
| API-06 | Phase 2 | Pending |
| API-07 | Phase 2 | Pending |
| API-08 | Phase 2 | Pending |
| API-09 | Phase 2 | Pending |
| CACHE-01 | Phase 3 | Pending |
| CACHE-02 | Phase 3 | Pending |
| SCOR-01 | Phase 3 | Pending |
| SCOR-02 | Phase 3 | Pending |
| SCOR-03 | Phase 3 | Pending |
| SCOR-04 | Phase 3 | Pending |
| PROS-01 | Phase 4 | Pending |
| PROS-02 | Phase 4 | Pending |
| PROS-03 | Phase 4 | Pending |
| PROS-04 | Phase 4 | Pending |
| PROS-05 | Phase 4 | Pending |
| PROS-06 | Phase 4 | Pending |
| PROS-07 | Phase 4 | Pending |
| PROS-08 | Phase 4 | Pending |
| API-01 | Phase 4 | Pending |
| API-02 | Phase 4 | Pending |
| API-03 | Phase 4 | Pending |
| API-04 | Phase 4 | Pending |
| API-05 | Phase 4 | Pending |
| API-10 | Phase 5 | Pending |
| API-11 | Phase 5 | Pending |
| API-12 | Phase 5 | Pending |

**Coverage:**
- v1 requirements: 50 total (47 functional + INFRA-04 and INFRA-05 counted individually above)
- Mapped: 50/50
- Unmapped: 0

---
*Roadmap created: 2026-04-03*
*Last updated: 2026-04-06 after Phase 4 planning*
