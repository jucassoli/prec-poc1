# Requirements: Precatórios API — TJ-SP Lead Prospecting

**Defined:** 2026-04-04
**Core Value:** Given a seed process number, automatically return a scored list of qualified precatório leads — without any manual court portal browsing.

## v1 Requirements

### Infrastructure

- [ ] **INFRA-01**: Project builds and runs with `./gradlew bootRun` on JVM 21
- [ ] **INFRA-02**: Docker Compose starts the full stack (API + PostgreSQL) with a single `docker-compose up`
- [ ] **INFRA-03**: Flyway applies all DB migrations automatically on startup
- [ ] **INFRA-04**: Swagger UI is accessible at `/swagger-ui.html` with all endpoints documented
- [ ] **INFRA-05**: All scraper configuration (base URLs, delays, DataJud API key) is externalized to `application.yml` and overridable via environment variables

### Data Sources — e-SAJ Scraper

- [ ] **ESAJ-01**: System can fetch process details from e-SAJ by process number using Jsoup (no login required)
- [ ] **ESAJ-02**: System extracts all parties (Reqte, Reqdo, Exequente, Credor) from the process page with their names and counsel
- [ ] **ESAJ-03**: System extracts all linked precatório incidents from a process page
- [ ] **ESAJ-04**: System can search processes by name via e-SAJ public search
- [ ] **ESAJ-05**: e-SAJ scraper enforces a minimum 2s delay between requests
- [ ] **ESAJ-06**: e-SAJ scraper retries on failure with exponential backoff (1s, 2s, 4s; max 3 attempts)
- [ ] **ESAJ-07**: e-SAJ scraper pauses 60s when HTTP 429 or CAPTCHA is detected

### Data Sources — CAC/SCP Scraper

- [ ] **CAC-01**: System can fetch precatório data (value, payment status, chronological position, nature, debtor entity) from the CAC/SCP portal
- [ ] **CAC-02**: CAC/SCP scraper maintains HTTP session and correctly handles ASP.NET ViewState across GET → POST cycle
- [ ] **CAC-03**: CAC/SCP scraper enforces the same rate limiting and retry policy as e-SAJ

### Data Sources — DataJud Client

- [ ] **DATJ-01**: System can query the DataJud CNJ API for process metadata by process number
- [ ] **DATJ-02**: System can query DataJud for precatório-class processes by IBGE municipality code
- [ ] **DATJ-03**: DataJud client uses the configurable API key from `application.yml`

### Persistence

- [ ] **PERS-01**: Processes are persisted in the `processos` table with deduplication by `numero`
- [ ] **PERS-02**: Creditors are persisted in the `credores` table with deduplication by `(nome, processo_id)`
- [ ] **PERS-03**: Precatórios are persisted in the `precatorios` table linked to creditors
- [ ] **PERS-04**: Prospection runs are tracked in the `prospeccoes` table with status, counters, and timing
- [ ] **PERS-05**: Leads are persisted in the `leads` table linking prospection, creditor, precatório, and score
- [ ] **PERS-06**: Raw HTML/JSON payloads are stored in `dados_brutos JSONB` on `processos` and `precatorios` for future reprocessing

### Caching

- [ ] **CACHE-01**: Scraping results are cached in-memory (Caffeine) with a 24h TTL to avoid redundant requests to TJ-SP
- [ ] **CACHE-02**: Cache is keyed by process number / precatório number so identical lookups within TTL are served from cache

### Scoring Engine

- [ ] **SCOR-01**: System scores each lead 0–100 based on five criteria: precatório value (30pts), debtor entity (25pts), payment status (20pts), chronological position (15pts), nature (10pts)
- [ ] **SCOR-02**: Scoring weights and thresholds are fully configurable via `application.yml` without code changes
- [ ] **SCOR-03**: Each lead's score includes a `scoreDetalhes` breakdown showing the contribution of each criterion
- [ ] **SCOR-04**: Leads with a score of 0 (all criteria failed) are still persisted but excluded from default lead list results

### Prospection Engine

- [ ] **PROS-01**: System executes a BFS recursive prospection starting from a seed process number
- [ ] **PROS-02**: Prospection respects `profundidadeMaxima` (default 2) to bound recursion depth
- [ ] **PROS-03**: Prospection respects `maxCredores` (default 50) and stops when the limit is reached
- [ ] **PROS-04**: Prospection tracks visited process numbers to prevent cycles and redundant scraping
- [ ] **PROS-05**: Prospection supports filters: `entidadesDevedoras`, `valorMinimo`, `apenasAlimentar`, `apenasPendentes`
- [ ] **PROS-06**: Prospection runs asynchronously — the `POST /api/v1/prospeccao` endpoint returns HTTP 202 immediately
- [ ] **PROS-07**: Seed process number is validated (CNJ format) before async dispatch — invalid seeds return HTTP 400 synchronously
- [ ] **PROS-08**: Partial scraping failures do not abort the entire prospection — failures are logged and recorded in `erro_mensagem`

### REST API — Prospection

- [ ] **API-01**: `POST /api/v1/prospeccao` accepts seed process number and optional filters, returns 202 with `prospeccaoId`
- [ ] **API-02**: `GET /api/v1/prospeccao/{id}` returns current status with progress counters (`processosVisitados`, `credoresEncontrados`, `leadsQualificados`) and full lead list when `CONCLUIDA`
- [ ] **API-03**: Status response includes `Retry-After: 10` header while `EM_ANDAMENTO`
- [ ] **API-04**: Status response on `ERRO` includes `erroMensagem` describing the failure
- [ ] **API-05**: `GET /api/v1/prospeccao` lists all prospection runs with pagination, filterable by `status`

### REST API — Process & Precatório Lookup

- [ ] **API-06**: `GET /api/v1/processo/{numero}` returns structured process data fetched from e-SAJ
- [ ] **API-07**: `GET /api/v1/processo/buscar` searches processes by `?nome=`, `?cpf=`, or `?numero=`
- [ ] **API-08**: `GET /api/v1/precatorio/{numero}` returns precatório data fetched from CAC/SCP
- [ ] **API-09**: `POST /api/v1/datajud/buscar` proxies a DataJud query and returns structured results

### REST API — Leads

- [ ] **API-10**: `GET /api/v1/leads` returns paginated leads with filters: `scoreMinimo`, `statusContato`, `entidadeDevedora`; default sort `score DESC`
- [ ] **API-11**: `PATCH /api/v1/leads/{id}/status` updates lead contact status with an optional `observacao` note
- [ ] **API-12**: All API errors return structured JSON with `status`, `message`, and `timestamp` fields (no stack traces)

## v2 Requirements

### Async Enhancements

- **ASYNC-01**: `DELETE /api/v1/prospeccao/{id}` cancels a running prospection gracefully
- **ASYNC-02**: Webhook notification when prospection completes (configurable callback URL per request)

### Export & Integration

- **EXPRT-01**: `GET /api/v1/leads` supports `Accept: text/csv` for spreadsheet export
- **EXPRT-02**: XLS export endpoint (`GET /api/v1/leads/export.xlsx`)

### Scheduling

- **SCHED-01**: Prospections can be configured to re-run on a schedule (cron) for a given seed process
- **SCHED-02**: API exposes endpoint to list and manage scheduled prospections

### Advanced Search

- **SRCH-01**: Prospection can be initiated by CPF/CNPJ (in addition to process number) as entry point
- **SRCH-02**: Cross-prospection creditor deduplication by CPF/CNPJ when available

### Security

- **SEC-01**: API key authentication via `X-API-Key` header for all endpoints
- **SEC-02**: Per-key rate limiting on the API itself

## Out of Scope

| Feature | Reason |
|---------|--------|
| Frontend / dashboard UI | Backend API only — FUNPREC integrates separately; Swagger UI suffices for v1 |
| WhatsApp / outreach automation | OAB advertising compliance not yet confirmed; lead list is the handoff point |
| OAuth / JWT auth on the API | Internal tool — network controls are sufficient for v1 |
| Multi-court support (TRT, STJ, federal) | Each court has different HTML and rate limits; TJ-SP must be proven first |
| CPF/CNPJ prospection entry point | Process-number seed is simpler to bound; evaluate after observing usage patterns |
| Job cancellation | Complex thread interrupt logic not warranted for POC |
| Real-time webhooks / SSE | Polling with Retry-After is sufficient for 3–10 min jobs |
| Scheduled/recurring prospections | Needs usage data before adding scheduling complexity |
| CSV/XLS export | `curl \| jq` covers v1 needs; export is v2 when pattern is confirmed |
| Login-required e-SAJ features | PDFs, detailed liquidation accounts, petitioning require lawyer credentials |

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
- v1 requirements: 50 total
- Mapped to phases: 50
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-04*
*Last updated: 2026-04-03 after roadmap creation — traceability expanded to per-requirement rows*
