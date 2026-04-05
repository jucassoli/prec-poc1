# Feature Landscape — Precatórios Lead Prospecting API

**Domain:** Court data scraping + lead qualification API (internal law firm tool)
**Researched:** 2026-04-03
**Context:** FUNPREC internal tool. Kotlin/Spring Boot REST API. No frontend. Users are internal advisors and integration clients. Direct competitor reference: JUDIT Miner (the only known commercial precatório prospecting platform in Brazil).

---

## Table Stakes

Features users expect. Missing any of these means the tool is unusable or untrustworthy.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Async prospection with 202 + polling | Each run takes minutes due to 2s rate limiting between requests. A synchronous endpoint would timeout every client. | Medium | Already in SPEC: `POST /api/v1/prospeccao` returns 202 with `prospeccaoId`. Non-negotiable. |
| Status endpoint with machine-readable states | Callers need to know when to stop polling and whether to retry. Returning opaque strings is not enough. | Low | States must be a closed enum: `EM_ANDAMENTO`, `CONCLUIDA`, `ERRO`. SPEC has this. |
| Progress counters in status response | A run could take 3–10 minutes. Without counters (processes visited, creditors found), the caller cannot distinguish "working" from "stuck". | Low | SPEC exposes `processosVisitados`, `credoresEncontrados` in status response. Critical for trust. |
| Seed process validation before async dispatch | If the seed process number is malformed or the e-SAJ lookup fails immediately, returning a 202 and then a silent `ERRO` state is a bad UX. Fail fast with 400/422 synchronously. | Low | Not explicitly in SPEC. Should validate CNJ format before dispatching async job. |
| Scored lead list as primary output | The entire value proposition. Without a ranked score, the tool returns raw data, not leads. Score breakdown per lead is required so advisors can understand why a lead ranked high. | Medium | SPEC has `score` (0–100) and `scoreDetalhes` breakdown. Both required. |
| Configurable scoring weights | FUNPREC's business rules will change. Hardcoded weights require a code deploy to tune. | Low | SPEC already externalizes to `application.yml`. This is the right call. |
| Filterable lead list endpoint | Advisors will run `GET /api/v1/leads?scoreMinimo=70&entidade=CAMPINAS&status=NAO_CONTACTADO`. Without filters, they get noise. | Low | SPEC has this. Minimum required filters: `scoreMinimo`, `statusContato`, `entidadeDevedora`. |
| Lead contact status tracking | The tool generates leads but FUNPREC must track outreach. Without `PATCH /leads/{id}/status`, advisors use a spreadsheet alongside — defeating the purpose. | Low | SPEC has `statusContato` enum and `PATCH` endpoint. Required. |
| Pagination on all list endpoints | A single prospection run can produce 50+ leads; the full leads table can hold thousands. Unbounded list responses are not acceptable. | Low | SPEC has `page`/`size` on all lists. Standard Spring Data Page response. |
| Deduplication of creditors across runs | Running two prospections from related seed processes will discover the same creditors. Without deduplication, advisors contact the same person twice. | Medium | SPEC uses `UNIQUE(nome, processo_id)` on `credores`. This only deduplicates within a process. Cross-run dedup by CPF/CNPJ is needed when the field is populated. |
| In-memory scraping cache (24h TTL) | Without cache, re-running prospections hammers TJ-SP for already-visited processes. Repeat hits risk IP blocks. | Low | SPEC specifies Caffeine with 24h TTL. Required for operational safety. |
| Structured error response on scraping failures | When e-SAJ or CAC/SCP is down, the API must return a useful error, not a 500 with a stack trace. | Low | SPEC has `GlobalExceptionHandler` and `ScrapingException`. Must include source name, URL attempted, retry count. |
| Swagger UI for endpoint exploration | The API has no frontend. Without Swagger, each endpoint must be manually documented externally. Internal tools rot quickly without self-documentation. | Low | SPEC has SpringDoc OpenAPI at `/swagger-ui.html`. Required. |
| Docker Compose deployment | Single-command local and staging deployment. Without this, onboarding a new advisor or spinning up a test environment requires manual steps. | Low | SPEC has Dockerfile + docker-compose.yml with PostgreSQL. Required. |

---

## Differentiators

Features that set this tool apart from a generic scraper. Not expected baseline, but add real value once table stakes are covered.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Recursive co-creditor discovery | JUDIT Miner requires direct input of creditor identifiers. This tool starts from a single seed process and expands the network by discovering all co-creditors, then their other processes. This is the core differentiator — exponential lead surface from a single input. | High | SPEC's recursive BFS prospection algorithm (`profundidadeMaxima` parameter). The unique insight is that co-creditors in the same process cluster are likely to have similar precatório profiles. |
| Multi-source data fusion | Combining e-SAJ (process parties) + CAC/SCP (precatório value, position) + DataJud (process metadata) per lead. Each source alone gives incomplete data. Fusion produces a richer lead profile than any single-source tool. | High | SPEC already specifies all three scrapers. The `dados_brutos JSONB` columns preserve raw payloads for reprocessing without re-scraping. |
| Configurable prospection depth | `profundidadeMaxima: 1` gives direct creditors from the seed. `profundidadeMaxima: 2` expands to co-creditors' other processes. This lets FUNPREC control scope vs. runtime tradeoff per use case. | Low | Already in SPEC. Tuning this is how FUNPREC will balance lead volume against API runtime cost. |
| `dados_brutos JSONB` preservation | Storing raw HTML/JSON alongside structured data means scoring rules can be re-applied retroactively without re-scraping. If FUNPREC changes the scoring formula, past prospections can be re-scored in-place. | Medium | SPEC has `dados_brutos JSONB` on both `processos` and `precatorios`. This is underrated — it future-proofs the data model against rule changes. |
| Separate leads list endpoint with cross-prospection view | JUDIT Miner likely scopes results per search session. `GET /api/v1/leads` across all prospections lets FUNPREC build a unified pipeline view, not per-run silos. | Low | SPEC has this. Pair it with `sort=score,desc` as default to make it immediately actionable. |
| Scoring transparency via `scoreDetalhes` breakdown | Black-box scores reduce trust. A breakdown showing `valorScore: 30, entidadeScore: 25` lets advisors override or challenge the score. This is a differentiator vs. tools that return only a final number. | Low | SPEC has `score_detalhes JSONB`. Critical for advisor adoption. |
| Configurable entity target list | Different FUNPREC practice areas may focus on different debtor entities (Estado SP vs. Campinas vs. other municipalities). Externalizing the entity list to config means no code change to pivot focus. | Low | SPEC has `entidades-alvo` in `application.yml`. Extend with a future admin endpoint to change targets without redeploy. |
| Prospection history and re-run capability | `GET /api/v1/prospeccao` lists all runs. FUNPREC can audit what was run, when, and with what parameters. This also enables re-running the same seed process after 30 days to catch newly filed precatórios. | Low | SPEC has the list endpoint. Re-run would just be a new `POST` with the same seed. |

---

## Anti-Features

Things to deliberately NOT build in v1. These create complexity that exceeds their value at this stage.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Frontend dashboard / UI | FUNPREC confirmed "backend API only" — they will integrate separately. Building a UI duplicates effort, slows delivery, and adds a full frontend stack to maintain. | Swagger UI at `/swagger-ui.html` is the only UI surface needed for v1. |
| Authentication / OAuth on the API | This is an internal tool. Adding OAuth2 or JWT auth adds implementation time and operational overhead (token management, key rotation) that is not justified for a single-team internal tool. | Deploy behind internal network or VPN only. Add API key auth as a single env-var check if network controls are insufficient. |
| Real-time webhooks for prospection completion | Webhooks require the caller to expose an HTTPS endpoint and handle retries, signature verification, and idempotency. For a polling pattern over a minutes-long job, polling is simpler and sufficient. | Polling with `Retry-After` header is enough. Add webhooks only if a caller demonstrates a specific need. |
| WebSocket progress streaming | Same rationale as webhooks. The incremental overhead of implementing SSE or WebSocket for a job that takes 3–10 minutes and is non-interactive is not worth it. | A polling interval of 10–15s against the status endpoint is adequate for this latency class. |
| CSV/Excel export endpoint | FUNPREC's immediate need is API consumption and CRM integration, not spreadsheet exports. An export endpoint adds content negotiation complexity and is quickly superseded by CRM integration. | If FUNPREC needs ad-hoc spreadsheets, `GET /api/v1/leads` + a tool like Postman or a simple script covers 80% of use cases without a dedicated endpoint. Add in v2 if requested. |
| WhatsApp / outreach automation | Out of scope per PROJECT.md. Automating outreach touches OAB advertising compliance rules. FUNPREC must validate legal permissibility before any outreach automation. | The lead list with `statusContato` tracking is the handoff point. Outreach is a separate system. |
| Scheduled/recurring prospections (cron) | Without usage data, we don't know how often FUNPREC wants to re-run. Premature scheduling adds state management (job scheduler, run deduplication) without validated need. | Let advisors trigger re-runs manually via `POST /api/v1/prospeccao`. Scheduling is a v2 feature when frequency patterns are known. |
| Prospection job cancellation endpoint | `DELETE /api/v1/prospeccao/{id}` cancellation requires interrupting a running async task gracefully (thread interrupt, partial persistence, partial result marking). For a POC, the complexity is not warranted. | Let jobs run to completion or timeout. Add cancellation in v2 if long-running abandoned jobs become an operational problem. |
| Multi-court support (TRT, STJ, federal) | The SPEC is explicitly scoped to TJ-SP. Each court has a different portal, different HTML structure, and different rate limiting behavior. Expanding scope before TJ-SP is proven stable is a classic scope creep trap. | Architect scrapers behind an interface (`EsajScraper`, `PrecatorioPortalScraper`) so adding a new court later is a new implementation, not a rewrite. |
| CPF/CNPJ lookup as a prospection entry point | Starting prospection from a creditor identity (CPF/CNPJ) rather than a process number requires e-SAJ person search, which returns multiple processes and is harder to bound. The seed process model is simpler to control. | Implement process-number seed only for v1. Evaluate CPF-based entry in v2 after observing how advisors actually use the tool. |

---

## Feature Dependencies

```
Async prospection (POST /prospeccao) 
  └── requires: Status endpoint with progress counters
  └── requires: Seed process validation (fail fast)
  └── requires: Scraping cache (Caffeine) to avoid duplicate requests
  └── requires: Rate limiting / retry logic in scrapers

Scored lead list (GET /leads)
  └── requires: Prospection run completion (CONCLUIDA)
  └── requires: Scoring engine with configurable weights
  └── requires: Multi-source data fusion (e-SAJ + CAC/SCP + DataJud)
  └── requires: Lead status tracking (PATCH /leads/{id}/status)

Scoring engine
  └── requires: Precatório data (valor, natureza, status, posicao_cronologica)
  └── requires: Configurable rules in application.yml

Recursive co-creditor discovery (differentiator)
  └── requires: Process lookup (GET /api/v1/processo/{numero})
  └── requires: Creditor extraction from process parties
  └── requires: BFS/queue logic with visitados deduplication
  └── requires: profundidadeMaxima bound to prevent runaway recursion

Cross-prospection deduplication
  └── requires: CPF/CNPJ field populated on credores table
  └── blocked-by: CPF/CNPJ is optional in e-SAJ public pages — may not always be available
```

---

## Async Prospection API — Status and Progress Patterns

Users of async prospection APIs expect the following (synthesized from REST API design standards and this project's specific latency profile):

### What the Status Response Must Contain

| Field | Why Required | In SPEC? |
|-------|--------------|---------|
| `status` (closed enum) | Machine-parseable terminal/non-terminal state. Callers loop on `EM_ANDAMENTO`, stop on `CONCLUIDA` or `ERRO`. | Yes |
| `processosVisitados` (counter) | Proof of progress. Without this, callers cannot distinguish "working slowly" from "stuck". | Yes |
| `credoresEncontrados` (counter) | Secondary progress signal. Tells caller whether the seed process was productive. | Yes |
| `leadsQualificados` (counter) | Tells the caller whether to bother fetching the full lead list after completion. | Yes |
| `tempoExecucaoSegundos` | Enables FUNPREC to tune `profundidadeMaxima` and `maxCredores` based on observed runtime. | Yes (on completed response) |
| `dataInicio` | Audit trail. Required for re-run decisions ("was this run within the last 30 days?"). | Partial — on list endpoint |
| `erroMensagem` (on ERRO status) | Actionable error. Without this, advisors cannot know whether to retry or escalate. | Yes |
| Link to leads (`/api/v1/leads?prospeccaoId=X`) | Once complete, the caller needs to know where the results are. A `Location`-style hint reduces coupling. | Not in SPEC — recommend adding |

### Polling Guidance

- Return `Retry-After: 10` header on 202 response and on EM_ANDAMENTO status responses. This prevents aggressive polling.
- The status endpoint must return 200 (not 202) on all subsequent polls, including while still in progress. 202 is only for the initial dispatch.
- On ERRO state: include `erroMensagem` and a boolean `reexecutavel` (whether the same parameters are safe to retry, e.g., TJ-SP was temporarily down vs. seed process does not exist).

### What to NOT Add (async anti-patterns)

- Do NOT return partial lead results in the status response while `EM_ANDAMENTO`. Streaming partial results creates consistency problems (score may not be final until all creditors are processed). Return full results only on `CONCLUIDA`.
- Do NOT use HTTP 200 for the initial dispatch. The 202 Accepted contract signals "accepted for processing but not yet complete" — it is the standard and callers expect it.

---

## Data Export and Integration Features for Law Firms

Based on research into law firm CRM integration patterns and FUNPREC's specific context:

### Priority Order for Integration (v1 → v2)

| Integration | Priority | Rationale |
|-------------|----------|-----------|
| JSON REST API (already built) | v1 required | FUNPREC will consume directly. Zero additional work. |
| Filterable + sortable `GET /api/v1/leads` with full pagination metadata | v1 required | Enables any downstream system to page through results without custom exports. |
| `dados_brutos JSONB` fields accessible via API | v1 nice-to-have | Enables FUNPREC to build custom views without re-scraping. |
| CSV export (`Accept: text/csv` content negotiation) | v2 | Useful for ad-hoc analysis. Only build when an advisor explicitly requests it. |
| Webhook on prospection completion | v2 | Enables push-based CRM integration (e.g., auto-create leads in Pipedrive). Build when a specific CRM integration is scoped. |
| XLS export | v2 | JUDIT Miner offers this. FUNPREC may request it for Excel-based reporting workflows. Lower priority than webhook. |

### Minimum Integration Surface (v1)

The JSON API with the following response envelope on `GET /api/v1/leads` is sufficient for v1 CRM integration:

- Standard Spring Data `Page` response with `content`, `totalElements`, `totalPages`, `number`
- Sort by `score,desc` as default (highest-value leads first)
- Filter by `scoreMinimo`, `statusContato`, `entidadeDevedora`, `prospeccaoId`
- Full lead payload including creditor name, precatório number, debtor entity, value, nature, payment status, chronological position, score, and score breakdown

This covers: manual review workflows, scripted CSV extraction via `curl | jq`, and eventual CRM integration without requiring a dedicated export feature.

---

## MVP Recommendation

Build in this order — each item unlocks the next:

1. **Data scrapers + persistence** (e-SAJ, CAC/SCP, DataJud) — without working scrapers, nothing else matters
2. **Scoring engine** — the value-add layer over raw scraped data
3. **Async prospection engine** (BFS + rate limiting + cache) — the core workflow
4. **Status endpoint with progress counters** — makes async safe to use
5. **Filtered lead list endpoint** — delivers the output to users
6. **Lead contact status tracking** — closes the outreach loop

Defer until v2:
- **Cancellation endpoint** — complexity exceeds POC value
- **CSV/XLS export** — manual `curl | jq` covers v1 needs
- **Webhooks** — polling is sufficient at this latency class
- **Scheduled/recurring prospections** — needs usage data first
- **Authentication** — internal tool behind network controls

---

## Sources

- SPEC.md and PROJECT.md (project context — primary inputs)
- [JUDIT Miner — Brazilian precatório prospecting platform](https://judit.io/en/) (competitive reference, MEDIUM confidence — product page 404'd, details from search result snippets)
- [Zuplo: Asynchronous Operations in REST APIs](https://zuplo.com/learning-center/asynchronous-operations-in-rest-apis-managing-long-running-tasks) (async patterns, HIGH confidence)
- [Microsoft Azure: Asynchronous Request-Reply Pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/asynchronous-request-reply) (async patterns, HIGH confidence)
- [RESTful API: Design for Long-Running Tasks](https://restfulapi.net/rest-api-design-for-long-running-tasks/) (async patterns, HIGH confidence)
- [Tyk: API Design Guidance — Long-Running Background Jobs](https://tyk.io/blog/api-design-guidance-long-running-background-jobs/) (cancellation patterns, MEDIUM confidence)
- [Law Ruler: Import Leads from CSV](https://support.lawruler.com/hc/en-us/articles/360045429833-Import-Leads-Contacts-Uploading-Data-from-CSV-Spreadsheets) (law firm data integration patterns, MEDIUM confidence)
- [abjur.github.io: Web scraping best practices for Brazilian courts](https://abjur.github.io/r4jurimetrics/melhores-praticas-para-web-scraping.html) (TJ-SP scraping context, MEDIUM confidence)
- [courtsbr/tjsp GitHub](https://github.com/jjesusfilho/tjsp) (TJ-SP scraping community tools, MEDIUM confidence)
