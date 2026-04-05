---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Roadmap created, STATE.md initialized — ready to begin Phase 1 planning
last_updated: "2026-04-05T11:04:28.835Z"
last_activity: 2026-04-05
progress:
  total_phases: 5
  completed_phases: 1
  total_plans: 4
  completed_plans: 4
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-03)

**Core value:** Given a seed process number, automatically return a scored list of qualified precatório leads without any manual court portal browsing.
**Current focus:** Phase 01 — foundation

## Current Position

Phase: 2
Plan: Not started
Status: Executing Phase 01
Last activity: 2026-04-05

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**

- Total plans completed: 4
- Average duration: -
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 4 | - | - |

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
