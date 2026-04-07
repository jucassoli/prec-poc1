# Phase 5: Leads API and Hardening - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-06
**Phase:** 05-leads-api-and-hardening
**Areas discussed:** Lead filtering rules, Integration test strategy, Stale job recovery, DataJud health indicator, Lead response shape

---

## Lead Filtering Rules

| Option | Description | Selected |
|--------|-------------|----------|
| AND all filters | scoreMinimo AND statusContato AND entidadeDevedora. Most intuitive for narrowing results. | ✓ |
| OR filters | Any matching filter includes the lead. Broader results but less useful. | |

**User's choice:** AND all filters
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Excluded by default | GET /leads returns score > 0 only. Add ?incluirZero=true to see all. | ✓ |
| Included always | All leads returned, client filters. | |

**User's choice:** Excluded by default
**Notes:** Aligns with SCOR-04 and existing findByScoreGreaterThan

| Option | Description | Selected |
|--------|-------------|----------|
| Score only | Default score DESC, no other sort options. | |
| Score + date | Allow ?sort=dataCriacao,desc for recently discovered leads. | ✓ |

**User's choice:** Score + date
**Notes:** None

---

## Integration Test Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Mock scrapers with stubs | @MockBean for scrapers returning fixed data. Fast, deterministic. | ✓ |
| WireMock recorded responses | Pre-recorded HTML/JSON from real TJ-SP. More realistic but brittle. | |

**User's choice:** Mock scrapers with stubs
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Single-depth BFS | Seed with 2-3 mock parties, depth=1. Full pipeline, under 10s. | ✓ |
| Multi-depth BFS | Depth=2 with branching. More thorough but slower. | |

**User's choice:** Single-depth BFS
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, tagged @IntegrationLive | Separate tag, excluded from CI, run manually. | |
| No live tests | Mock-only. Live validation is manual. | ✓ |

**User's choice:** No live tests
**Notes:** None

---

## Stale Job Recovery

| Option | Description | Selected |
|--------|-------------|----------|
| Startup only | ApplicationRunner marks stale jobs as ERRO on boot. Simple. | ✓ |
| Startup + periodic check | @Scheduled heartbeat every 5min. More robust but complex. | |

**User's choice:** Startup only
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Generic recovery message | "Prospeccao interrompida por reinicio do servidor" | |
| Include timestamp of original start | "Interrompida por reinicio (iniciada em {date})" | ✓ |

**User's choice:** Include timestamp of original start
**Notes:** None

---

## DataJud Health Indicator

| Option | Description | Selected |
|--------|-------------|----------|
| Lightweight ping | Custom HealthIndicator sends small DataJud query. UP/DOWN in /actuator/health. | ✓ |
| Cache last response | Check every 60s, cache result. Kinder to rate limits. | |

**User's choice:** Lightweight ping
**Notes:** None

---

## Lead Response Shape

| Option | Description | Selected |
|--------|-------------|----------|
| Summary with key fields | id, score, scoreDetalhes, statusContato, dataCriacao, credor(id, nome), precatorio summary. | ✓ |
| Minimal — IDs only | id, score, statusContato, credorId, precatorioId. | |
| Full nested objects | Complete Credor and Precatorio with all fields. | |

**User's choice:** Summary with key fields
**Notes:** Requires JOIN FETCH for credor and precatorio to avoid N+1

---

## Claude's Discretion

- Status transition validation on PATCH endpoint
- Exact mock fixture data for integration tests
- DataJud health check query payload

## Deferred Ideas

None — discussion stayed within phase scope.
