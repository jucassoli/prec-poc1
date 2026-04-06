---
phase: 4
slug: bfs-prospection-engine-and-prospection-api
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-06
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + MockK + springmockk 4.0.2 |
| **Config file** | build.gradle.kts — `tasks.withType<Test> { useJUnitPlatform() }` |
| **Quick run command** | `./gradlew test --tests "br.com.precatorios.engine.*" --tests "br.com.precatorios.controller.ProspeccaoControllerTest"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "br.com.precatorios.engine.*" --tests "br.com.precatorios.controller.ProspeccaoControllerTest"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 04-01-01 | 01 | 1 | PROS-01 | — | N/A | unit | `./gradlew test --tests "br.com.precatorios.engine.BfsProspeccaoEngineTest"` | ❌ W0 | ⬜ pending |
| 04-01-02 | 01 | 1 | PROS-04 | — | N/A | unit | `./gradlew test --tests "br.com.precatorios.engine.BfsProspeccaoEngineTest.visited set"` | ❌ W0 | ⬜ pending |
| 04-01-03 | 01 | 1 | PROS-08 | — | N/A | unit | `./gradlew test --tests "br.com.precatorios.engine.BfsProspeccaoEngineTest.partial failure"` | ❌ W0 | ⬜ pending |
| 04-02-01 | 02 | 1 | PROS-02 | T-04-01 | maxSearchResults caps queue growth | unit | `./gradlew test --tests "br.com.precatorios.engine.BfsProspeccaoEngineTest.depth control"` | ❌ W0 | ⬜ pending |
| 04-02-02 | 02 | 1 | PROS-03 | T-04-01 | maxCredores early-exit prevents DoS | unit | `./gradlew test --tests "br.com.precatorios.engine.BfsProspeccaoEngineTest.max credores"` | ❌ W0 | ⬜ pending |
| 04-02-03 | 02 | 1 | PROS-05 | — | N/A | unit | `./gradlew test --tests "br.com.precatorios.engine.BfsProspeccaoEngineTest.filters"` | ❌ W0 | ⬜ pending |
| 04-03-01 | 03 | 2 | PROS-06, API-01 | — | N/A | @WebMvcTest | `./gradlew test --tests "br.com.precatorios.controller.ProspeccaoControllerTest.returns 202"` | ❌ W0 | ⬜ pending |
| 04-03-02 | 03 | 2 | PROS-07 | T-04-02 | @Pattern rejects non-CNJ before async | @WebMvcTest | `./gradlew test --tests "br.com.precatorios.controller.ProspeccaoControllerTest.invalid CNJ"` | ❌ W0 | ⬜ pending |
| 04-03-03 | 03 | 2 | API-02, API-03 | — | N/A | @WebMvcTest | `./gradlew test --tests "br.com.precatorios.controller.ProspeccaoControllerTest.status in progress"` | ❌ W0 | ⬜ pending |
| 04-03-04 | 03 | 2 | API-04 | — | N/A | @WebMvcTest | `./gradlew test --tests "br.com.precatorios.controller.ProspeccaoControllerTest.erro response"` | ❌ W0 | ⬜ pending |
| 04-03-05 | 03 | 2 | API-05 | — | N/A | @WebMvcTest | `./gradlew test --tests "br.com.precatorios.controller.ProspeccaoControllerTest.list with pagination"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/br/com/precatorios/engine/BfsProspeccaoEngineTest.kt` — stubs for PROS-01 through PROS-08
- [ ] `src/test/kotlin/br/com/precatorios/controller/ProspeccaoControllerTest.kt` — stubs for API-01 through API-05

*Existing infrastructure: JUnit 5 + MockK + springmockk already in build.gradle.kts — no new test framework installation needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| POST returns 202 within 500ms under slow scrapers | PROS-06 | Timing-sensitive; unit test mocks don't capture real latency | Start app, mock scraper with 5s delay, POST and measure response time |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
