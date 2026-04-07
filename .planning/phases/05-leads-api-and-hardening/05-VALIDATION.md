---
phase: 5
slug: leads-api-and-hardening
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-06
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (via spring-boot-starter-test 3.5.3) + MockK 1.14.3 |
| **Config file** | None — JUnit Platform auto-discovered; `useJUnitPlatform()` in build.gradle.kts |
| **Quick run command** | `./gradlew test --tests "br.com.precatorios.controller.LeadControllerTest"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~45 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "br.com.precatorios.controller.LeadControllerTest"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 1 | API-10 | — | N/A | unit (@WebMvcTest) | `./gradlew test --tests "br.com.precatorios.controller.LeadControllerTest"` | ❌ W0 | ⬜ pending |
| 05-01-02 | 01 | 1 | API-10 | — | N/A | unit (@WebMvcTest) | `./gradlew test --tests "br.com.precatorios.controller.LeadControllerTest"` | ❌ W0 | ⬜ pending |
| 05-01-03 | 01 | 1 | API-11 | T-5-01 | Invalid enum returns 400 not 500 | unit (@WebMvcTest) | `./gradlew test --tests "br.com.precatorios.controller.LeadControllerTest"` | ❌ W0 | ⬜ pending |
| 05-02-01 | 02 | 1 | API-12 | T-5-02 | No stack traces in 500 responses | unit (@WebMvcTest) | `./gradlew test --tests "br.com.precatorios.controller.*"` | Partial | ⬜ pending |
| 05-02-02 | 02 | 1 | API-12 | — | N/A | unit | `./gradlew test --tests "br.com.precatorios.controller.*"` | Partial | ⬜ pending |
| 05-03-01 | 03 | 2 | D-05/D-06 | — | N/A | integration (@SpringBootTest) | `./gradlew test --tests "br.com.precatorios.integration.FullStackProspeccaoIntegrationTest"` | ❌ W0 | ⬜ pending |
| 05-03-02 | 03 | 2 | D-08/D-09 | — | N/A | unit | `./gradlew test --tests "br.com.precatorios.startup.StaleJobRecoveryRunnerTest"` | ❌ W0 | ⬜ pending |
| 05-03-03 | 03 | 2 | D-10 | — | N/A | unit | `./gradlew test --tests "br.com.precatorios.health.DataJudHealthIndicatorTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/br/com/precatorios/controller/LeadControllerTest.kt` — stubs for API-10, API-11, API-12
- [ ] `src/test/kotlin/br/com/precatorios/integration/FullStackProspeccaoIntegrationTest.kt` — stubs for D-05, D-06
- [ ] `src/test/kotlin/br/com/precatorios/startup/StaleJobRecoveryRunnerTest.kt` — stubs for D-08, D-09
- [ ] `src/test/kotlin/br/com/precatorios/health/DataJudHealthIndicatorTest.kt` — stubs for D-10
- [ ] `src/main/resources/db/migration/V3__add_lead_observacao.sql` — required for Lead.observacao field (API-11)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| DataJud health indicator shows DOWN when DataJud is unreachable | D-10 | Requires network failure simulation | 1. Stop DataJud mock/endpoint 2. Hit /actuator/health 3. Verify DataJud component shows DOWN |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 45s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
