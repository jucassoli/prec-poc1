---
phase: 01-foundation
plan: 03
subsystem: persistence
tags: [jpa, hibernate, testcontainers, flyway, postgresql, kotlin]
dependency_graph:
  requires: [01-01, 01-02]
  provides: [domain-entities, spring-data-repositories, persistence-layer]
  affects: [02-scrapers, 03-scoring, 04-api]
tech_stack:
  added: []
  patterns:
    - JPA entities with kotlin-jpa plugin (no manual no-arg constructors)
    - Hibernate 6 @JdbcTypeCode(SqlTypes.JSON) for JSONB fields
    - @Enumerated(EnumType.STRING) for enum persistence
    - Testcontainers 2.0 @ServiceConnection for zero-boilerplate DataSource wiring
    - @Transactional on test class for automatic rollback
key_files:
  created:
    - src/main/kotlin/br/com/precatorios/domain/enums/StatusProspeccao.kt
    - src/main/kotlin/br/com/precatorios/domain/enums/StatusContato.kt
    - src/main/kotlin/br/com/precatorios/domain/Processo.kt
    - src/main/kotlin/br/com/precatorios/domain/Credor.kt
    - src/main/kotlin/br/com/precatorios/domain/Precatorio.kt
    - src/main/kotlin/br/com/precatorios/domain/Prospeccao.kt
    - src/main/kotlin/br/com/precatorios/domain/Lead.kt
    - src/main/kotlin/br/com/precatorios/repository/ProcessoRepository.kt
    - src/main/kotlin/br/com/precatorios/repository/CredorRepository.kt
    - src/main/kotlin/br/com/precatorios/repository/PrecatorioRepository.kt
    - src/main/kotlin/br/com/precatorios/repository/ProspeccaoRepository.kt
    - src/main/kotlin/br/com/precatorios/repository/LeadRepository.kt
    - src/test/kotlin/br/com/precatorios/repository/RepositoryIntegrationTest.kt
    - src/test/kotlin/br/com/precatorios/migration/MigrationTest.kt
  modified: []
decisions:
  - "Used var fields throughout all entities — Hibernate requires mutability for proxy-based lazy loading"
  - "All JSONB columns typed as String? in Kotlin, mapped via @JdbcTypeCode(SqlTypes.JSON) + columnDefinition=jsonb"
  - "@Transactional on RepositoryIntegrationTest class for automatic rollback between tests"
metrics:
  duration_minutes: 12
  completed_date: "2026-04-04"
  tasks_completed: 3
  files_created: 14
---

# Phase 01 Plan 03: JPA Entities and Repositories Summary

**One-liner:** Five Kotlin JPA entities with JSONB mapping and Testcontainers integration tests proving persistence against real PostgreSQL 17.

## What Was Built

All five JPA entities (Processo, Credor, Precatorio, Prospeccao, Lead), their Spring Data repositories with deduplication queries, and two integration tests (repository persistence and Flyway migration) that run against a real PostgreSQL 17 database via Testcontainers 2.0.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create enum classes and all five JPA entities | c9d7094 | 7 files (2 enums + 5 entities) |
| 2 | Create repositories and Testcontainers integration test | 0deec10 | 6 files (5 repos + 1 test) |
| 3 | Create Flyway migration integration test (INFRA-03) | c31d1a8 | 1 file (MigrationTest.kt) |

## Verification Results

- `./gradlew compileKotlin` — EXIT 0
- `./gradlew test --tests "*.RepositoryIntegrationTest"` — EXIT 0, all 8 tests passed
- `./gradlew test --tests "*.MigrationTest"` — EXIT 0, test passed
- `grep -r "@Entity" src/main/kotlin/br/com/precatorios/domain/ | wc -l` — 5
- `grep -r "JpaRepository" src/main/kotlin/br/com/precatorios/repository/ | wc -l` — 10
- `grep -r "@JdbcTypeCode" src/main/kotlin/br/com/precatorios/domain/ | wc -l` — 3 (Processo, Precatorio, Lead)

## Decisions Made

1. **JSONB as String?**: All JSONB columns are typed as `String?` in Kotlin with `@JdbcTypeCode(SqlTypes.JSON)` and `columnDefinition = "jsonb"`. Hibernate 6 handles serialization transparently.

2. **@Transactional on test class**: Applied to `RepositoryIntegrationTest` for automatic rollback between tests, keeping test isolation without manual cleanup.

3. **var fields**: All entity fields use `var` (not `val`) to satisfy Hibernate's proxy requirements for lazy loading and update tracking.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all entities are fully wired with database-backed persistence.

## Threat Flags

None — no new network endpoints, auth paths, or file access patterns introduced. This plan adds persistence layer only.

## Self-Check: PASSED

- src/main/kotlin/br/com/precatorios/domain/Processo.kt — FOUND
- src/main/kotlin/br/com/precatorios/domain/Credor.kt — FOUND
- src/main/kotlin/br/com/precatorios/domain/Precatorio.kt — FOUND
- src/main/kotlin/br/com/precatorios/domain/Prospeccao.kt — FOUND
- src/main/kotlin/br/com/precatorios/domain/Lead.kt — FOUND
- src/main/kotlin/br/com/precatorios/repository/ProcessoRepository.kt — FOUND
- src/test/kotlin/br/com/precatorios/repository/RepositoryIntegrationTest.kt — FOUND
- src/test/kotlin/br/com/precatorios/migration/MigrationTest.kt — FOUND
- Commit c9d7094 — FOUND
- Commit 0deec10 — FOUND
- Commit c31d1a8 — FOUND
