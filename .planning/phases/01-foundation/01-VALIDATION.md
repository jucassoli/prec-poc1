---
phase: 1
slug: foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-04
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Testcontainers 2.0 + MockK |
| **Config file** | `src/test/kotlin/br/com/precatorios/` |
| **Quick run command** | `./gradlew test --tests "*.repository.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~60 seconds (Testcontainers PostgreSQL startup) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew build -x test` (compilation check)
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|------|-------------|-----------|-------------------|--------|
| 01-01-* | 01 | 1 | INFRA-01 | build | `./gradlew build -x test` | ⬜ pending |
| 01-02-* | 02 | 1 | INFRA-02, INFRA-03 | integration | `docker-compose up -d && ./gradlew test` | ⬜ pending |
| 01-03-* | 03 | 2 | PERS-01..06 | integration | `./gradlew test --tests "*.repository.*"` | ⬜ pending |
| 01-04-* | 04 | 2 | INFRA-04, INFRA-05 | build + smoke | `./gradlew bootRun &` then `curl /swagger-ui.html` | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/br/com/precatorios/repository/ProcessoRepositoryTest.kt` — stubs for PERS-01
- [ ] `src/test/kotlin/br/com/precatorios/repository/CredorRepositoryTest.kt` — stubs for PERS-02
- [ ] `src/test/kotlin/br/com/precatorios/repository/PrecatorioRepositoryTest.kt` — stubs for PERS-03
- [ ] `src/test/kotlin/br/com/precatorios/repository/ProspeccaoRepositoryTest.kt` — stubs for PERS-04
- [ ] `src/test/kotlin/br/com/precatorios/repository/LeadRepositoryTest.kt` — stubs for PERS-05

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Swagger UI renders all stubs | INFRA-04 | HTTP browser check | Start app, open `/swagger-ui.html`, verify page loads |
| `docker-compose up` full stack healthy | INFRA-02 | Docker runtime required | Run `docker-compose up`, check `/actuator/health` returns `{"status":"UP"}` |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
