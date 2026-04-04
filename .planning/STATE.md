# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-03)

**Core value:** Given a seed process number, automatically return a scored list of qualified precatório leads without any manual court portal browsing.
**Current focus:** Phase 1 — Foundation

## Current Position

Phase: 1 of 5 (Foundation)
Plan: 0 of 4 in current phase
Status: Ready to plan
Last activity: 2026-04-03 — Roadmap and state initialized

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: -
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**
- Last 5 plans: -
- Trend: -

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Phase 1: Use `@Async` with named `prospeccaoExecutor` ThreadPoolTaskExecutor (not Kotlin coroutines) for BFS jobs
- Phase 1: Short `@Transactional(REQUIRES_NEW)` per-persist inside BFS — do not wrap entire run in one transaction
- Phase 2: CAC/SCP requires Jsoup.newSession() for cookie/ViewState persistence; HtmlUnit 4.21.0 is fallback if POST fails
- Phase 2: Phase 2 requires live validation spike against TJ-SP before declaring scrapers done

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 2: e-SAJ CSS selectors (#tablePartesPrincipais, #incidentes, etc.) are unvalidated hypotheses — must fire real requests in Phase 2 smoke test before building BFS on top
- Phase 2: CAC/SCP ViewState session behavior against live TJ-SP portal is unconfirmed — validate before declaring CacScraper done

## Session Continuity

Last session: 2026-04-03
Stopped at: Roadmap created, STATE.md initialized — ready to begin Phase 1 planning
Resume file: None
