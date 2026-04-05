---
phase: 01-foundation
plan: 02
subsystem: infrastructure
tags: [docker, postgresql, flyway, schema, migration]
dependency_graph:
  requires: []
  provides: [docker-compose, db-schema]
  affects: [01-03, 01-04]
tech_stack:
  added: [postgres:17-alpine, Flyway V1 migration]
  patterns: [Docker Compose service_healthy ordering, Flyway versioned migration, JSONB for raw data storage]
key_files:
  created:
    - docker-compose.yml
    - src/main/resources/db/migration/V1__create_tables.sql
  modified: []
decisions:
  - PostgreSQL 17-alpine chosen over SPEC's 16-alpine for improved JSONB performance (per research)
  - No `version:` key in docker-compose.yml — deprecated in Compose V2+
  - pg_isready targets specific db (-U precatorios -d precatorios) not just TCP
  - start_period:10s gives PostgreSQL initial startup window before healthcheck failures count
metrics:
  duration: 1m 9s
  completed: 2026-04-05T01:08:19Z
  tasks_completed: 2
  files_created: 2
  files_modified: 0
---

# Phase 01 Plan 02: Docker Compose and Flyway V1 Migration Summary

## One-liner

Docker Compose with postgres:17-alpine health-checked startup and Flyway V1 migration creating all five tables (processos, credores, precatorios, prospeccoes, leads) with JSONB columns, FK constraints, unique constraints, and performance indexes.

## What Was Built

### Task 1: Docker Compose (commit 1578f10)

Created `docker-compose.yml` at project root with:

- `postgres:17-alpine` service with `pg_isready -U precatorios -d precatorios` healthcheck
- `start_period: 10s` to give PostgreSQL initial startup grace window
- `api` service with `depends_on: postgres: condition: service_healthy` for ordered startup
- Spring datasource URL using Docker network hostname `postgres:5432`
- Named volume `pgdata` for persistent PostgreSQL data
- No deprecated `version:` key (Compose V2+)

### Task 2: Flyway V1 Migration (commit ba8f823)

Created `src/main/resources/db/migration/V1__create_tables.sql` with:

| Table | Key Features |
|-------|-------------|
| `processos` | `numero VARCHAR(25) NOT NULL UNIQUE`, `dados_brutos JSONB` |
| `credores` | `UNIQUE(nome, processo_id)` composite dedup constraint, FK to processos |
| `precatorios` | `dados_brutos JSONB`, FK to credores, `DECIMAL(15,2)` value columns |
| `prospeccoes` | `status DEFAULT 'EM_ANDAMENTO'`, BFS tracking counters |
| `leads` | `score_detalhes JSONB`, `status_contato DEFAULT 'NAO_CONTACTADO'` |

Performance indexes:
- `idx_processos_numero` — on numero (also backed by UNIQUE constraint)
- `idx_credores_processo_id` — FK lookup
- `idx_precatorios_credor_id` — FK lookup
- `idx_leads_prospeccao_id` — FK lookup
- `idx_leads_score DESC` — for sorted lead retrieval
- `idx_prospeccoes_status` — for status filtering

## Verification Results

| Check | Expected | Actual | Status |
|-------|----------|--------|--------|
| `grep -c "CREATE TABLE"` | 5 | 5 | PASS |
| `grep -c "JSONB"` | 3 | 3 | PASS |
| `grep "UNIQUE"` | processos.numero + credores composite | Both present | PASS |
| `grep "service_healthy"` | present | present | PASS |
| `grep "pg_isready"` | present | present | PASS |
| No `version:` key | absent | absent | PASS |

## Commits

| Task | Commit | Message |
|------|--------|---------|
| 1 | 1578f10 | feat(01-02): create Docker Compose with health-checked PostgreSQL |
| 2 | ba8f823 | feat(01-02): add Flyway V1 migration with all five tables |

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written.

**One deliberate upgrade:** PostgreSQL version changed from 16-alpine (SPEC section 9) to 17-alpine, as specified in the plan's action section citing research recommendation for JSONB performance. This is per-plan specification, not a deviation.

## Known Stubs

None — both artifacts are complete and contain no placeholder content.

## Threat Flags

None — no new network endpoints or auth paths introduced. docker-compose.yml exposes PostgreSQL port 5432 only for local development (documented in SPEC section 9). Hardcoded credentials (`precatorios/precatorios`) are development defaults only, externalized via environment variables — acceptable for local Docker Compose.

## Self-Check

Files exist:
- docker-compose.yml: FOUND
- src/main/resources/db/migration/V1__create_tables.sql: FOUND

Commits exist:
- 1578f10: FOUND
- ba8f823: FOUND

## Self-Check: PASSED
