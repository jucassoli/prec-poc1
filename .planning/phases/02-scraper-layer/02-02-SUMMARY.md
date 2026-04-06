---
phase: 02-scraper-layer
plan: 02
subsystem: scraper
tags: [esaj, jsoup, scraping, resilience4j, html-parsing, tdd]
dependency_graph:
  requires: [02-01]
  provides: [EsajScraper, EsajSelectors]
  affects: [02-04, phase-04-bfs-engine]
tech_stack:
  added: []
  patterns:
    - Programmatic Resilience4j decoration (RateLimiter.decorateCheckedSupplier + Retry.decorateCheckedSupplier)
    - Centralized CSS selector constants (EsajSelectors object)
    - Graceful degradation: null fields + missingFields list on selector miss
    - Internal parsing methods (parseProcesso, parsePartes, parseIncidentes, parseBusca) for testability without mocking Jsoup.connect
key_files:
  created:
    - src/main/kotlin/br/com/precatorios/scraper/EsajSelectors.kt
    - src/main/kotlin/br/com/precatorios/scraper/EsajScraper.kt
    - src/test/kotlin/br/com/precatorios/scraper/EsajScraperTest.kt
    - src/test/resources/fixtures/esaj_processo.html
    - src/test/resources/fixtures/esaj_busca.html
  modified: []
decisions:
  - "Exposed internal parse methods (parseProcesso, parsePartes, parseIncidentes, parseBusca) as package-internal for direct testing without mocking Jsoup.connect — avoids complex HTTP mock setup while keeping parsing logic covered"
  - "Used .get() not .apply() for CheckedSupplier invocation — Kotlin's built-in apply extension conflicted with Resilience4j CheckedSupplier.apply() SAM method"
  - "EsajScraper.fetchProcesso and buscarPorNome wrap inner doFetch/doBuscar methods with programmatic Resilience4j decoration, keeping Jsoup.connect calls isolated inside those inner methods only"
metrics:
  duration: "~20 minutes"
  completed_date: "2026-04-06"
  tasks_completed: 2
  files_created: 5
requirements: [ESAJ-01, ESAJ-02, ESAJ-03, ESAJ-04]
---

# Phase 02 Plan 02: e-SAJ Scraper with Selectors and Tests Summary

**One-liner:** EsajScraper with centralized CSS selectors, programmatic Resilience4j rate limiting/retry, graceful degradation to missingFields list, and 24 unit tests against HTML fixtures.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | EsajSelectors constants and EsajScraper service | `dcee6b4` | EsajSelectors.kt, EsajScraper.kt |
| 2 | EsajScraper unit tests with HTML fixtures | `77a8e0d` | EsajScraperTest.kt, esaj_processo.html, esaj_busca.html |

## What Was Built

### EsajSelectors.kt
Centralized `object EsajSelectors` with all CSS selector constants used by the scraper:
- URL paths: `SHOW_PATH`, `SEARCH_PATH`
- Process header fields: `CLASSE`, `ASSUNTO`, `FORO`, `VARA`, `JUIZ`, `VALOR_ACAO`
- Parties: `PARTES_TABLE`, `PARTE_NOME`
- Incidents: `INCIDENTES_TABLE`
- Search: `SEARCH_RESULT_TABLE`, `SEARCH_NUMERO`

### EsajScraper.kt
`@Service` class with:
- `fetchProcesso(numero: String): ProcessoScraped` — fetches and parses process details
- `buscarPorNome(nome: String): List<BuscaResultado>` — searches by free-text name
- Programmatic Resilience4j: `Retry.decorateCheckedSupplier` wrapped by `RateLimiter.decorateCheckedSupplier`
- HTTP 429 detection: throws `TooManyRequestsException`
- Graceful degradation: null fields + `missingFields: List<String>` with log.warn per missing selector
- Internal parse methods exposed as package-internal for testability: `parseProcesso`, `parsePartes`, `parseIncidentes`, `parseBusca`
- Data classes: `ProcessoScraped`, `ParteScraped`, `IncidenteScraped`, `BuscaResultado`

### HTML Fixtures
- `esaj_processo.html`: realistic fixture with classeProcesso, assuntoProcesso, foroProcesso, varaProcesso, juizProcesso, valorAcaoProcesso, tablePartesPrincipais (3 rows with .nomeParteEAdvogado), incidentes table (2 rows with links)
- `esaj_busca.html`: search results fixture with listagemDeProcessos table (2 result rows)

### EsajScraperTest.kt
24 unit tests covering:
- All 6 header field parsers (classe, assunto, foro, vara, juiz, valorAcao)
- Party extraction: count (>=2), nome, tipo, advogado
- Incident extraction: count, numero, descricao
- Graceful degradation: null fields + missingFields populated on empty HTML
- rawHtml stored in result
- Search results: non-empty, correct numero, correct classe, count, empty-page case

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] CheckedSupplier.apply() conflicts with Kotlin built-in apply extension**
- **Found during:** Task 1 compilation
- **Issue:** `Retry.decorateCheckedSupplier(...)` returns a `CheckedSupplier<T>`. Calling `.apply()` on it in Kotlin resolves to Kotlin's built-in `apply` extension (which takes a block parameter), not the Resilience4j `CheckedSupplier.apply()` SAM method. Caused "No value passed for parameter 'block'" compile error.
- **Fix:** Changed `.apply()` to `.get()` which invokes the `CheckedSupplier` as a `java.util.function.Supplier`.
- **Files modified:** `EsajScraper.kt`, `CacScraper.kt` (same bug in parallel plan's file)
- **Commit:** `dcee6b4`

**2. [Rule 3 - Blocking] CacScraper.kt had same apply() bug blocking compileKotlin**
- **Found during:** Task 1 verification (./gradlew compileKotlin)
- **Issue:** `CacScraper.kt` from a parallel agent (02-03) had the same `.apply()` issue, blocking compilation of the entire scraper package.
- **Fix:** Changed `.apply()` to `.get()` in CacScraper.kt.
- **Files modified:** `CacScraper.kt`
- **Commit:** `dcee6b4`

## Threat Model Verification

| Threat ID | Verification |
|-----------|-------------|
| T-02-02 | Grep confirms `.data("processo.codigo"` pattern at line 105 — no URL string concatenation |
| T-02-03 | rawHtml stored in ProcessoScraped.rawHtml, not exposed in API responses (accepted) |
| T-02-04 | All Jsoup.connect calls at lines 104 and 122 are inside `doFetchProcesso` and `doBuscarPorNome` — both called only through the rate-limiter-decorated path |

## Known Stubs

None. EsajSelectors CSS selectors are noted in STATE.md as "unvalidated hypotheses" against live TJ-SP — but they are not stubs in the code sense; they are real selector strings that require live validation in Plan 02-04. The `properties.esaj.baseUrl` is configured externally via `application.yml`.

## Self-Check: PASSED

- `src/main/kotlin/br/com/precatorios/scraper/EsajSelectors.kt` — FOUND
- `src/main/kotlin/br/com/precatorios/scraper/EsajScraper.kt` — FOUND
- `src/test/kotlin/br/com/precatorios/scraper/EsajScraperTest.kt` — FOUND
- `src/test/resources/fixtures/esaj_processo.html` — FOUND
- `src/test/resources/fixtures/esaj_busca.html` — FOUND
- Commit `dcee6b4` (Task 1) — FOUND
- Commit `77a8e0d` (Task 2) — FOUND
- `./gradlew compileKotlin` — PASSED
- `./gradlew test --tests "*EsajScraperTest*"` — PASSED (24/24 tests)
