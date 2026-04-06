---
phase: 02-scraper-layer
plan: "03"
subsystem: scraper
tags: [cac, datajud, jsoup, webclient, resilience4j, viewstate, elasticsearch]
one_liner: "CAC/SCP ViewState scraper with session renewal and DataJud Elasticsearch client with APIKey auth"
dependency_graph:
  requires: [02-01]
  provides: [CacScraper.fetchPrecatorio, DataJudClient.buscarPorNumeroProcesso, DataJudClient.buscarPorMunicipio]
  affects: [02-04-lookup-endpoints, phase-04-bfs-engine]
tech_stack:
  added:
    - com.squareup.okhttp3:mockwebserver:4.12.0 (test)
  patterns:
    - Jsoup.newSession() GET/POST ViewState cycle for ASP.NET portal
    - WebClient.exchangeToMono() for status-aware HTTP error handling
    - Resilience4j programmatic decoration: RateLimiter.decorateCheckedSupplier wrapping Retry.decorateCheckedSupplier
    - Bounded session renewal (sessionRenewCount <= 1 per request)
key_files:
  created:
    - src/main/kotlin/br/com/precatorios/scraper/CacScraper.kt
    - src/main/kotlin/br/com/precatorios/scraper/DataJudClient.kt
    - src/test/kotlin/br/com/precatorios/scraper/CacScraperTest.kt
    - src/test/kotlin/br/com/precatorios/scraper/DataJudClientTest.kt
    - src/test/resources/fixtures/cac_form.html
    - src/test/resources/fixtures/cac_resultado.html
  modified:
    - build.gradle.kts
decisions:
  - "Use WebClient.exchangeToMono() instead of retrieve().onStatus() to avoid body-read timeout when handling 429 and other error status codes"
  - "Enqueue 3 MockWebServer responses for 429 tests to cover Resilience4j retry attempts"
  - "CacScraper session renewal bounded to sessionRenewCount=1 per request to prevent infinite loop (T-02-07)"
  - "T-02-05: API key never appears in exception messages or log output — only status codes logged"
metrics:
  duration_minutes: 45
  completed_date: "2026-04-06"
  tasks_completed: 2
  files_created: 7
requirements: [CAC-01, CAC-02, DATJ-01, DATJ-02, DATJ-03]
---

# Phase 02 Plan 03: CAC/SCP Scraper and DataJud Client Summary

**One-liner:** CAC/SCP ViewState scraper with session renewal and DataJud Elasticsearch client with APIKey auth

## What Was Built

### Task 1: CacScraper (commit `359e9bc`)

`CacScraper` is a `@Service` that fetches precatório data from the TJ-SP CAC/SCP ASP.NET portal using Jsoup's connection API for a ViewState GET/POST session cycle:

- **GET form page** at `scraper.precatorio-portal.base-url`
- **Extract ViewState** fields: `__VIEWSTATE`, `__VIEWSTATEGENERATOR`, `__EVENTVALIDATION`
- **POST** with ViewState + `numeroPrecatorio` parameter, maintaining cookies
- **Blank-form detection**: checks for `.resultadoPesquisa` in result page; if absent, renews session (bounded to 1 renewal per request — T-02-07 mitigation)
- **Parse** result page: `entidadeDevedora`, `valorOriginal`, `valorAtualizado`, `natureza`, `statusPagamento`, `posicaoCronologica`, `dataExpedicao` with graceful degradation (null + `missingFields` list)
- **Rate limiting and retry** via `cacRateLimiter` and `cacRetry` beans with Resilience4j programmatic decoration

`PrecatorioScraped` data class returned with all parsed fields and `rawHtml`.

HTML fixtures created for tests:
- `cac_form.html`: ASP.NET-style form with all three ViewState hidden fields
- `cac_resultado.html`: Result page with CSS-class-keyed precatório data elements

### Task 2: DataJudClient (commits `828dc4b` + `11caff5`, TDD)

`DataJudClient` is a `@Service` that queries the DataJud CNJ Elasticsearch API via Spring WebClient:

- **WebClient** built with `baseUrl` and `Authorization: APIKey <key>` default header from `ScraperProperties`
- `buscarPorNumeroProcesso(numero)`: POST `{"query":{"match":{"numeroProcesso":"..."}},"size":N}` to `/api_publica_tjsp/_search`
- `buscarPorMunicipio(codigoIbge)`: POST `{"query":{"bool":{"must":[{"term":{"codigoMunicipioIBGE":"..."}},{"match":{"classeProcessual":"Precatorio"}}]}},"size":N}`
- **429 handling**: `exchangeToMono` checks status before body read — throws `TooManyRequestsException` immediately with body released
- **Empty response**: throws `ScrapingException("DataJud returned empty response")`
- **Rate limiting and retry** via `datajudRateLimiter` and `datajudRetry` beans
- **Synchronous**: `.block(Duration.ofMillis(timeoutMs))` — safe from Tomcat thread
- **Jackson parsing**: typed internal response model for Elasticsearch hits

`DataJudResult` and `DataJudHit` data classes returned.

## Decisions Made

| Decision | Rationale |
|----------|-----------|
| `exchangeToMono()` over `retrieve().onStatus()` | `onStatus()` triggers body timeout when status-only (no body) responses come from MockWebServer and live 429 responses; `exchangeToMono` handles status before body read |
| 3 MockWebServer enqueued responses for 429 test | Resilience4j `datajudRetry` retries up to 3 times; single enqueued response caused timeout on retry 2 |
| Session renewal bounded at 1 per `doFetchPrecatorio` call | Prevents infinite ViewState renewal loop per Pitfall 3 in RESEARCH.md and T-02-07 threat |
| API key via `"APIKey ${properties.datajud.apiKey}"` default header | Centralized in WebClient construction — not duplicated in query methods |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Incorrect Resilience4j decoration syntax**
- **Found during:** Task 1 compilation
- **Issue:** Plan specified single-argument lambda form `RateLimiter.decorateCheckedSupplier(cacRateLimiter) { ... }` which does not compile — the method requires `(RateLimiter, CheckedSupplier)` two-argument form
- **Fix:** Used two-argument form matching EsajScraper pattern: `val retryDecorated = Retry.decorateCheckedSupplier(...) { ... }` then `RateLimiter.decorateCheckedSupplier(rateLimiter, retryDecorated)`
- **Files modified:** CacScraper.kt, DataJudClient.kt
- **Commit:** 359e9bc

**2. [Rule 1 - Bug] WebClient `onStatus` body timeout on 429 response**
- **Found during:** Task 2 GREEN phase
- **Issue:** `retrieve().onStatus({ it == 429 }) { Mono.error(...) }` caused a 5s read timeout because WebFlux still tried to read the response body after the status handler signaled an error — MockWebServer (and real 429 responses) often have no/small body
- **Fix:** Replaced with `exchangeToMono { response -> when { status == 429 -> response.releaseBody().then(Mono.error(...)) ... } }` which handles status before body consumption
- **Files modified:** DataJudClient.kt
- **Commit:** 11caff5

**3. [Rule 1 - Bug] 429 test timeout due to Resilience4j retry with single queued response**
- **Found during:** Task 2 GREEN phase
- **Issue:** Single `MockResponse().setResponseCode(429)` was enqueued but `datajudRetry` retries up to 3 times — retry attempts 2 and 3 received no response causing `IllegalStateException: Timeout`
- **Fix:** Enqueued 3 identical 429 responses (with body to avoid connection hang) in `DataJudClientTest`
- **Files modified:** DataJudClientTest.kt
- **Commit:** 11caff5

## Threat Mitigations Applied

| Threat ID | Mitigation | Verified |
|-----------|-----------|---------|
| T-02-05 | API key never appears in `throw`/`log` statements in DataJudClient.kt or CacScraper.kt | Grep confirmed — only `properties.datajud.apiKey` in header construction, not in error paths |
| T-02-07 | `sessionRenewCount` bounded to 1 per request in CacScraper — throws non-retryable `ScrapingException` after second blank form | Present in `doFetchWithRenewal` recursive call with counter |

## Test Results

```
./gradlew test --tests "*CacScraperTest*"     → BUILD SUCCESSFUL (11 tests)
./gradlew test --tests "*DataJudClientTest*"  → BUILD SUCCESSFUL (9 tests)
./gradlew compileKotlin                        → BUILD SUCCESSFUL
```

## Known Stubs

None — all data class fields are wired to real parsed values from HTML/JSON. `PrecatorioScraped.numeroProcesso` is `null` because CAC/SCP does not return a process number in the result page (the precatorio number is the input, not the output).

## Self-Check: PASSED

- `src/main/kotlin/br/com/precatorios/scraper/CacScraper.kt` — FOUND
- `src/main/kotlin/br/com/precatorios/scraper/DataJudClient.kt` — FOUND
- `src/test/kotlin/br/com/precatorios/scraper/CacScraperTest.kt` — FOUND
- `src/test/kotlin/br/com/precatorios/scraper/DataJudClientTest.kt` — FOUND
- `src/test/resources/fixtures/cac_form.html` — FOUND
- `src/test/resources/fixtures/cac_resultado.html` — FOUND
- Commit `359e9bc` — FOUND (CacScraper feat)
- Commit `828dc4b` — FOUND (DataJudClient test RED)
- Commit `11caff5` — FOUND (DataJudClient feat GREEN)
