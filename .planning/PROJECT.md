# Precatórios API — TJ-SP Lead Prospecting

## What This Is

A Kotlin/Spring Boot REST API that automates the discovery and qualification of precatório creditors at the São Paulo State Court of Justice (TJ-SP). Built for FUNPREC law firm: given a seed court case, the system recursively discovers co-creditors, fetches their precatório data from 3 public sources, scores each lead, and returns a ranked list ready for outreach.

## Core Value

Given a seed process number, automatically return a scored list of qualified precatório leads — creditors with pending, valuable precatórios from target debtors (State of SP, City of Campinas) — without any manual court portal browsing.

## Requirements

### Validated

- [x] Persist processes, creditors, precatórios, prospection runs, and leads in PostgreSQL via JPA/Flyway — *Validated in Phase 1: Foundation*
- [x] Expose Swagger UI at `/swagger-ui.html` for endpoint exploration — *Validated in Phase 1: Foundation*
- [x] Package with Docker Compose (API + PostgreSQL) — *Validated in Phase 1: Foundation*
- [x] Run prospections asynchronously with polling endpoint for status/results — *Async executor validated in Phase 1; endpoint pending Phase 4*
- [x] Cache scraping results in-memory (Caffeine, 24h TTL) to avoid redundant requests — *Validated in Phase 3: Cache and Scoring*
- [x] Score leads 0–100 based on precatório value, debtor entity, payment status, chronological position, and nature — *Validated in Phase 3: Cache and Scoring*
- [x] Scoring rules configurable via `application.yml` — *Validated in Phase 3: Cache and Scoring*

### Active

- [ ] Accept a seed process number and run recursive prospecting up to configurable depth
- [ ] Scrape e-SAJ (public) for process parties, precatório incidents, and procedural updates using Jsoup
- [ ] Scrape CAC/SCP portal (ASP.NET/ViewState) for precatório value, payment status, and chronological position
- [ ] Query DataJud API (CNJ Elasticsearch) for process metadata enrichment
- [ ] Expose REST endpoints for prospection (async), process lookup, precatório lookup, DataJud proxy, and lead management
- [ ] Respect rate limits: 2s delay between requests, exponential backoff on failures (3 retries), 60s pause on HTTP 429

### Out of Scope

- Frontend/dashboard UI — backend API only; FUNPREC will integrate separately
- Login-required e-SAJ features (digital case PDFs, detailed liquidation accounts, petitioning) — public endpoints only
- WhatsApp outreach integration — lead list output is sufficient for v1; integration is a separate concern
- OAuth/authentication on the API itself — internal tool, not public-facing in v1
- Real-time scraping (webhooks, polling loops) — on-demand prospection only

## Context

- FUNPREC law firm prospects precatório creditors for acquisition/representation. Manual process today — advisors browse TJ-SP portals to find leads.
- Three public data sources: e-SAJ (case search, HTML), CAC/SCP (precatório portal, ASP.NET), DataJud (CNJ REST API).
- e-SAJ is static HTML → Jsoup is sufficient. CAC/SCP uses ViewState and requires session maintenance — may need HtmlUnit if Jsoup isn't enough.
- DataJud API key is public and documented: `cDZHYzlZa0JadVREZDJCendQbXY6SkJlTzNjLV9TRENyQk1RdnFKZGRQdw==` — should be made configurable.
- CSS selectors for e-SAJ (`#tablePartesPrincipais`, `#incidentes`, etc.) are based on public HTML analysis — will need calibration with real requests.
- Rate limiting and CAPTCHA are real risks. Mitigation: delay, backoff, off-hours scheduling.
- Legal/ethical consideration: prospecting active creditors — FUNPREC should confirm compliance with OAB advertising rules.

## Constraints

- **Tech Stack**: Kotlin (JVM 21) + Spring Boot 3.x + PostgreSQL — already decided, FUNPREC team familiarity
- **No browser headless**: Jsoup preferred; HtmlUnit as fallback for CAC/SCP — avoids Playwright/Selenium complexity
- **Async execution**: Prospections must be async (minutes per run due to rate limiting) — no sync blocking endpoints for prospection
- **Rate limiting**: 2s minimum between requests to TJ-SP endpoints — risk of IP block
- **DataJud API**: Public key may change — must be externalized to config, not hardcoded

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Kotlin + Spring Boot over Python | JVM ecosystem, type safety, Spring async/coroutines, team preference | — Pending |
| Jsoup over Playwright/Selenium for e-SAJ | e-SAJ is static HTML; Jsoup is lighter, faster, JVM-native | — Pending |
| Async prospection with polling | Each run takes minutes due to rate limiting; blocking would timeout clients | — Pending |
| Caffeine in-memory cache (not Redis) | Single-instance deployment; Redis adds operational overhead for v1 | — Pending |
| Scoring rules in application.yml | Allows FUNPREC to tune weights without code changes | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-06 after Phase 3: Cache and Scoring complete*
