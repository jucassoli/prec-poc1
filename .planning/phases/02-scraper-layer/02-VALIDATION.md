---
phase: 2
slug: scraper-layer
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-05
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (via `spring-boot-starter-test`, BOM-managed) + MockK 1.14.3 |
| **Config file** | No separate config — Gradle `useJUnitPlatform()` in `build.gradle.kts` |
| **Quick run command** | `./gradlew test --tests "br.com.precatorios.scraper.*" -x integrationTest` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "br.com.precatorios.scraper.*" --tests "br.com.precatorios.controller.*" -x :test --no-daemon`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 1 | ESAJ-01 | T-02-01 | Process number validated via regex before URL construction | unit (mock HTTP) | `./gradlew test --tests "*EsajScraperTest*"` | ❌ W0 | ⬜ pending |
| 02-01-02 | 01 | 1 | ESAJ-02 | — | N/A | unit (static HTML) | `./gradlew test --tests "*EsajScraperTest*partes*"` | ❌ W0 | ⬜ pending |
| 02-01-03 | 01 | 1 | ESAJ-03 | — | N/A | unit (static HTML) | `./gradlew test --tests "*EsajScraperTest*incidentes*"` | ❌ W0 | ⬜ pending |
| 02-01-04 | 01 | 1 | ESAJ-04 | T-02-02 | Search term min length enforced | unit (static HTML) | `./gradlew test --tests "*EsajScraperTest*buscar*"` | ❌ W0 | ⬜ pending |
| 02-01-05 | 01 | 1 | ESAJ-05 | — | N/A | unit (config check) | `./gradlew test --tests "*ResilienceConfigTest*"` | ❌ W0 | ⬜ pending |
| 02-01-06 | 01 | 1 | ESAJ-06, ESAJ-07 | — | N/A | unit (config check) | `./gradlew test --tests "*ResilienceConfigTest*"` | ❌ W0 | ⬜ pending |
| 02-02-01 | 02 | 1 | CAC-01 | — | N/A | smoke (live) | Manual / live spike | ❌ W0 | ⬜ pending |
| 02-02-02 | 02 | 1 | CAC-02 | — | N/A | unit (static HTML) | `./gradlew test --tests "*CacScraperTest*viewstate*"` | ❌ W0 | ⬜ pending |
| 02-02-03 | 02 | 1 | CAC-03 | — | N/A | unit (config check) | `./gradlew test --tests "*ResilienceConfigTest*"` | ❌ W0 | ⬜ pending |
| 02-03-01 | 03 | 1 | DATJ-01 | T-02-03 | API key not leaked in error responses | unit (MockWebServer) | `./gradlew test --tests "*DataJudClientTest*"` | ❌ W0 | ⬜ pending |
| 02-03-02 | 03 | 1 | DATJ-02 | — | N/A | unit (verify query) | `./gradlew test --tests "*DataJudClientTest*municipio*"` | ❌ W0 | ⬜ pending |
| 02-03-03 | 03 | 1 | DATJ-03 | T-02-03 | API key injected from config, not hardcoded | unit | `./gradlew test --tests "*DataJudClientTest*apikey*"` | ❌ W0 | ⬜ pending |
| 02-04-01 | 04 | 2 | API-06 | T-02-01 | @Pattern on path variable | integration (MockMvc) | `./gradlew test --tests "*ProcessoControllerTest*"` | ❌ W0 | ⬜ pending |
| 02-04-02 | 04 | 2 | API-07 | T-02-02 | @Size(min=3) on search param | integration (MockMvc) | `./gradlew test --tests "*ProcessoControllerTest*buscar*"` | ❌ W0 | ⬜ pending |
| 02-04-03 | 04 | 2 | API-08 | T-02-01 | @Pattern on path variable | integration (MockMvc) | `./gradlew test --tests "*PrecatorioControllerTest*"` | ❌ W0 | ⬜ pending |
| 02-04-04 | 04 | 2 | API-09 | — | N/A | integration (MockMvc) | `./gradlew test --tests "*DataJudControllerTest*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/br/com/precatorios/scraper/EsajScraperTest.kt` — unit tests with HTML fixtures
- [ ] `src/test/kotlin/br/com/precatorios/scraper/CacScraperTest.kt` — ViewState extraction tests
- [ ] `src/test/kotlin/br/com/precatorios/scraper/DataJudClientTest.kt` — MockWebServer-based tests
- [ ] `src/test/kotlin/br/com/precatorios/config/ResilienceConfigTest.kt` — RateLimiter/Retry config assertions
- [ ] `src/test/kotlin/br/com/precatorios/controller/ProcessoControllerTest.kt` — MockMvc + MockK
- [ ] `src/test/kotlin/br/com/precatorios/controller/PrecatorioControllerTest.kt` — MockMvc + MockK
- [ ] `src/test/kotlin/br/com/precatorios/controller/DataJudControllerTest.kt` — MockMvc + MockK
- [ ] `src/test/resources/fixtures/esaj_processo.html` — captured HTML from live smoke test
- [ ] `src/test/resources/fixtures/esaj_busca.html` — captured HTML from search results

**Note:** HTML fixtures must be captured during the live validation spike, not fabricated.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| CAC/SCP live ViewState cycle | CAC-01 | Requires live TJ-SP CAC portal | Run CacScraper.buscar() against known precatório number; verify non-null response |
| e-SAJ live smoke test | ESAJ-01 | Requires live TJ-SP e-SAJ portal | Run EsajScraper.buscar() against known process number; verify structured response |
| HTTP 429 60s pause | ESAJ-07 | Cannot reliably trigger 429 in test | Monitor logs during sustained scraping; verify 60s pause on 429 status |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
