---
phase: 03
slug: cache-and-scoring
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-06
---

# Phase 03 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (via spring-boot-starter-test 3.5.3) + MockK 1.14.3 |
| **Config file** | `build.gradle.kts` — `tasks.withType<Test> { useJUnitPlatform() }` |
| **Quick run command** | `./gradlew test --tests "*.ScoringServiceTest" -x integrationTest` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "*.ScoringServiceTest" -x integrationTest`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 1 | CACHE-01 | — | N/A | Integration (MockK spy) | `./gradlew test --tests "*.CacheConfigTest"` | ❌ W0 | ⬜ pending |
| 03-01-02 | 01 | 1 | CACHE-02 | — | N/A | Integration (MockK spy) | `./gradlew test --tests "*.CacheConfigTest"` | ❌ W0 | ⬜ pending |
| 03-02-01 | 02 | 1 | SCOR-01 | — | N/A | Unit | `./gradlew test --tests "*.ScoringServiceTest"` | ❌ W0 | ⬜ pending |
| 03-02-02 | 02 | 1 | SCOR-02 | — | N/A | N/A | Integration (@SpringBootTest) | `./gradlew test --tests "*.ScoringPropertiesTest"` | ❌ W0 | ⬜ pending |
| 03-02-03 | 02 | 1 | SCOR-03 | — | N/A | Unit | `./gradlew test --tests "*.ScoringServiceTest"` | ❌ W0 | ⬜ pending |
| 03-02-04 | 02 | 1 | SCOR-04 | — | N/A | Unit (repo method) | `./gradlew test --tests "*.LeadRepositoryTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/br/com/precatorios/service/ScoringServiceTest.kt` — covers SCOR-01, SCOR-03 (pure unit, no Spring)
- [ ] `src/test/kotlin/br/com/precatorios/config/CacheConfigTest.kt` — covers CACHE-01, CACHE-02 (MockK spy on scraper, @SpringBootTest)
- [ ] `src/test/kotlin/br/com/precatorios/config/ScoringPropertiesTest.kt` — covers SCOR-02 (mirrors ScraperPropertiesTest pattern)
- [ ] `src/test/kotlin/br/com/precatorios/repository/LeadRepositoryTest.kt` — covers SCOR-04 (findByScoreGreaterThan)

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
