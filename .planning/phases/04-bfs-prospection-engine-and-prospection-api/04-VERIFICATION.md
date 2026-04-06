---
phase: 04-bfs-prospection-engine-and-prospection-api
verified: 2026-04-06T20:30:00Z
status: human_needed
score: 13/13 must-haves verified
human_verification:
  - test: "POST /api/v1/prospeccao with a real CNJ seed against a live TJ-SP process"
    expected: "Returns HTTP 202 with prospeccaoId; engine runs asynchronously and GET /{id} transitions from EM_ANDAMENTO to CONCLUIDA with non-empty leads"
    why_human: "BFS depends on live TJ-SP e-SAJ and CAC scrapers; integration cannot be verified without network access and a real PostgreSQL instance"
  - test: "GET /api/v1/prospeccao/{id} while engine is running (EM_ANDAMENTO state)"
    expected: "Response includes Retry-After: 10 header and progressively increasing processosVisitados/credoresEncontrados/leadsQualificados counters as the async BFS runs"
    why_human: "Counter visibility requires live DB writes from REQUIRES_NEW transactions visible to concurrent polling — cannot be verified without runtime"
  - test: "Trigger partial CAC scraper failure during a live run (e.g., disconnect network mid-prospection)"
    expected: "BFS completes with status CONCLUIDA, erroMensagem contains the failed node details, other leads are still persisted"
    why_human: "Failure isolation at runtime requires an actual scraping failure against live endpoints"
---

# Phase 04: BFS Prospection Engine and Prospection API — Verification Report

**Phase Goal:** Build BFS prospection engine with lead discovery across related precatórios and REST API for triggering/querying prospections.
**Verified:** 2026-04-06T20:30:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | BFS engine visits seed process and discovers creditors from scraped parties | VERIFIED | BfsProspeccaoEngine.kt: `esajScraper.fetchProcesso(numero)` in while loop; iterates `scraped.partes` for each creditor |
| 2 | BFS engine expands by searching each creditor name via buscarPorNome and enqueueing their processes | VERIFIED | BfsProspeccaoEngine.kt line 131: `esajScraper.buscarPorNome(parte.nome).take(maxSearchResults)` with visited-set guard and queue.add |
| 3 | Visited set prevents re-scraping the same process number within a single run | VERIFIED | BfsProspeccaoEngine.kt line 49: `val visited = mutableSetOf<String>()` (local var); `result.numero !in visited` check before enqueue; Test #2 confirms dedup behavior |
| 4 | Partial scraper failure does not abort BFS — failed branches are skipped and errors recorded | VERIFIED | BfsProspeccaoEngine.kt: per-incidente try/catch (lines 109-124) and per-node try/catch (lines 148-151); errors appended to local `errors` list; 16 tests pass including partial failure test |
| 5 | Each lead is persisted in its own REQUIRES_NEW transaction | VERIFIED | ProspeccaoLeadPersistenceHelper.kt line 25: `@Transactional(propagation = Propagation.REQUIRES_NEW)` on `persistirLead`; helper is a separate `@Service` bean |
| 6 | Prospection counters are updated in separate REQUIRES_NEW transactions visible to polling clients | VERIFIED | ProspeccaoLeadPersistenceHelper.kt line 44: `@Transactional(propagation = Propagation.REQUIRES_NEW)` on `atualizarContadores`; called after each BFS node (line 146) |
| 7 | BFS engine runs asynchronously via @Async(prospeccaoExecutor) | VERIFIED | BfsProspeccaoEngine.kt line 37: `@Async("prospeccaoExecutor")`; Test #3 (annotation reflection test) confirms presence |
| 8 | BFS respects profundidadeMaxima and does not expand beyond the configured depth | VERIFIED | BfsProspeccaoEngine.kt line 128: `if (depth < profundidadeMaxima)` guards BFS expansion; Tests #6 (depth=1) and #7 (depth=0) pass |
| 9 | BFS stops discovering new leads when credoresEncontrados reaches maxCredores | VERIFIED | BfsProspeccaoEngine.kt line 64 (while-loop top) and line 90 (inner partes loop): `if (credoresEncontrados >= maxCredores) break`; Test #8 confirms |
| 10 | Filters (entidadesDevedoras, valorMinimo, apenasAlimentar, apenasPendentes) are applied during BFS — non-matching leads are not scored or persisted | VERIFIED | BfsProspeccaoEngine.kt: `passaFiltros` private method (lines 202-221) checked before `scoringService.score` and `persistenceHelper.persistirLead`; 4 filter-specific tests pass |
| 11 | POST /api/v1/prospeccao returns HTTP 202 with prospeccaoId in body | VERIFIED | ProspeccaoController.kt line 60-62: `ResponseEntity.status(HttpStatus.ACCEPTED).body(ProspeccaoIniciadaDTO(prospeccaoId = prospeccao.id!!))`; Test #1 asserts `isAccepted()` and `$.prospeccaoId` |
| 12 | Invalid CNJ format on processoSemente returns HTTP 400 synchronously before async dispatch | VERIFIED | ProspeccaoRequestDTO.kt: `@field:Pattern(regexp = "^[0-9]{7}...")` + `@field:NotBlank`; GlobalExceptionHandler handles `MethodArgumentNotValidException` → 400; Tests #2 and #3 confirm |
| 13 | GET /api/v1/prospeccao/{id} returns status with three counters, Retry-After: 10 header when EM_ANDAMENTO, leads[] when CONCLUIDA, erroMensagem when ERRO; GET list is paginated and filterable | VERIFIED | ProspeccaoController.kt getStatus() (lines 65-114) and listar() (lines 116-139); 12 WebMvcTest cases cover all scenarios |

**Score:** 13/13 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/resources/db/migration/V2__add_leads_qualificados.sql` | leads_qualificados column migration | VERIFIED | Contains `ALTER TABLE prospeccoes ADD COLUMN leads_qualificados INTEGER NOT NULL DEFAULT 0` |
| `src/main/kotlin/br/com/precatorios/domain/Prospeccao.kt` | leadsQualificados field on entity | VERIFIED | `@Column(name = "leads_qualificados") var leadsQualificados: Int = 0` present |
| `src/main/kotlin/br/com/precatorios/engine/BfsProspeccaoEngine.kt` | BFS traversal engine with @Async start method | VERIFIED | 223 lines; `@Async("prospeccaoExecutor")` on `fun start(`; all BFS logic present |
| `src/main/kotlin/br/com/precatorios/engine/ProspeccaoLeadPersistenceHelper.kt` | REQUIRES_NEW persistence helper | VERIFIED | Three methods all `@Transactional(propagation = Propagation.REQUIRES_NEW)`; `@Service` annotated |
| `src/main/kotlin/br/com/precatorios/service/ProspeccaoService.kt` | Service to create initial Prospeccao entity | VERIFIED | `@Transactional fun criar(...)` sets all required fields |
| `src/main/kotlin/br/com/precatorios/controller/ProspeccaoController.kt` | REST controller with POST, GET /{id}, GET list | VERIFIED | All three endpoints present; calls `prospeccaoService.criar` then `bfsProspeccaoEngine.start` |
| `src/main/kotlin/br/com/precatorios/dto/ProspeccaoRequestDTO.kt` | Request DTO with @Valid CNJ pattern | VERIFIED | `@field:Pattern(regexp = "^[0-9]{7}-[0-9]{2}\\...` and `@field:NotBlank` on processoSemente |
| `src/main/kotlin/br/com/precatorios/dto/ProspeccaoStatusDTO.kt` | Status response with counters and optional leads | VERIFIED | Contains `processosVisitados`, `credoresEncontrados`, `leadsQualificados`, `leads: List<LeadSummaryDTO>?`, `erroMensagem` |
| `src/main/kotlin/br/com/precatorios/dto/ProspeccaoIniciadaDTO.kt` | 202 response with prospeccaoId | VERIFIED | `data class ProspeccaoIniciadaDTO(val prospeccaoId: Long)` |
| `src/main/kotlin/br/com/precatorios/dto/LeadSummaryDTO.kt` | Lead summary DTO | VERIFIED | All fields present: score, scoreDetalhes, credorNome, precatorioNumero, etc. |
| `src/main/kotlin/br/com/precatorios/dto/ProspeccaoListItemDTO.kt` | List item DTO for paginated GET | VERIFIED | Contains id, processoSemente, status, counters, dataInicio, dataFim |
| `src/main/kotlin/br/com/precatorios/exception/ProspeccaoNaoEncontradaException.kt` | 404 exception class | VERIFIED | `class ProspeccaoNaoEncontradaException(id: Long) : RuntimeException(...)` |
| `src/main/resources/application.yml` | prospeccao.max-search-results-per-creditor config | VERIFIED | `max-search-results-per-creditor: 10` under `prospeccao:` section |
| `src/test/kotlin/br/com/precatorios/engine/BfsProspeccaoEngineTest.kt` | Unit tests for BFS core behavior | VERIFIED | 16 test methods covering all required scenarios |
| `src/test/kotlin/br/com/precatorios/controller/ProspeccaoControllerTest.kt` | WebMvcTest for all prospection endpoints | VERIFIED | 12 test methods covering POST, GET /{id}, GET list |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| BfsProspeccaoEngine.start() | EsajScraper.fetchProcesso() + buscarPorNome() | direct method calls in BFS loop | WIRED | Lines 69 and 131 of BfsProspeccaoEngine.kt |
| BfsProspeccaoEngine.start() | ProspeccaoLeadPersistenceHelper | injected dependency | WIRED | `persistenceHelper.persistirLead(` line 116, `persistenceHelper.atualizarContadores(` line 146 |
| BfsProspeccaoEngine.start() | ScoringService.score() | scoring each precatorio before lead persist | WIRED | `scoringService.score(savedPrecatorio)` line 115 |
| BfsProspeccaoEngine.passaFiltros() | Precatorio entity fields | direct field comparison | WIRED | Checks `entidadeDevedora`, `valorAtualizado`, `natureza`, `statusPagamento` |
| ProspeccaoController.iniciar() | ProspeccaoService.criar() | sync persist before async dispatch | WIRED | `prospeccaoService.criar(...)` lines 45-49 before `bfsProspeccaoEngine.start(...)` |
| ProspeccaoController.iniciar() | BfsProspeccaoEngine.start() | @Async dispatch after persist | WIRED | `bfsProspeccaoEngine.start(...)` lines 50-59 |
| ProspeccaoController.getStatus() | ProspeccaoRepository + LeadRepository | findById + findByProspeccaoId | WIRED | `prospeccaoRepository.findById(id)` line 68; `leadRepository.findByProspeccaoId(id)` line 72 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|-------------------|--------|
| ProspeccaoController.getStatus() | `prospeccao` | `prospeccaoRepository.findById(id)` — JPA query | Yes (JPA `findById` hits DB) | FLOWING |
| ProspeccaoController.getStatus() | `leads` | `leadRepository.findByProspeccaoId(id)` — JPA query | Yes (JPA derived query hits DB) | FLOWING |
| ProspeccaoController.listar() | `page` | `prospeccaoRepository.findAll(pageable)` or `findByStatus(...)` — JPA queries | Yes | FLOWING |
| BfsProspeccaoEngine.start() | `scraped` | `esajScraper.fetchProcesso(numero)` — live HTTP scraper | Yes (real TJ-SP scraping) | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED — application requires PostgreSQL and live TJ-SP network access. No standalone runnable entry points exist for command-line verification. Tests serve as the behavioral contract.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| PROS-01 | 04-01 | BFS recursive prospection from seed process | SATISFIED | BfsProspeccaoEngine.start() implements full BFS loop |
| PROS-02 | 04-02 | Respects profundidadeMaxima | SATISFIED | `if (depth < profundidadeMaxima)` guard; Tests #6/#7 |
| PROS-03 | 04-02 | Respects maxCredores stop condition | SATISFIED | Two-point `credoresEncontrados >= maxCredores` check; Test #8 |
| PROS-04 | 04-01 | Tracks visited process numbers to prevent cycles | SATISFIED | Local `visited` mutableSetOf; Test #2 |
| PROS-05 | 04-02 | Filters: entidadesDevedoras, valorMinimo, apenasAlimentar, apenasPendentes | SATISFIED | `passaFiltros()` with all 4 checks; 4 filter tests pass |
| PROS-06 | 04-01 | Runs asynchronously — POST returns 202 immediately | SATISFIED | `@Async("prospeccaoExecutor")` on start(); controller returns 202 before BFS completes |
| PROS-07 | 04-03 | CNJ format validated before async dispatch — invalid returns 400 | SATISFIED | `@field:Pattern` on ProspeccaoRequestDTO; `@Valid` on controller; GlobalExceptionHandler → 400 |
| PROS-08 | 04-01 | Partial failures don't abort prospection — logged in erroMensagem | SATISFIED | Per-incidente and per-node try/catch; errors.joinToString passed to finalizarProspeccao |
| API-01 | 04-03 | POST /api/v1/prospeccao returns 202 with prospeccaoId | SATISFIED | ProspeccaoController.iniciar() returns ACCEPTED + ProspeccaoIniciadaDTO |
| API-02 | 04-03 | GET /api/v1/prospeccao/{id} returns status + counters + leads when CONCLUIDA | SATISFIED | ProspeccaoController.getStatus() with full DTO mapping |
| API-03 | 04-03 | Retry-After: 10 header while EM_ANDAMENTO | SATISFIED | `ResponseEntity.ok().header("Retry-After", "10")` when status == EM_ANDAMENTO |
| API-04 | 04-03 | erroMensagem on ERRO status response | SATISFIED | ProspeccaoStatusDTO includes `erroMensagem`; populated from Prospeccao entity |
| API-05 | 04-03 | GET /api/v1/prospeccao with pagination and status filter | SATISFIED | ProspeccaoController.listar() with Pageable + optional StatusProspeccao filter |

All 13 requirements (PROS-01 through PROS-08, API-01 through API-05) are satisfied. No orphaned requirements found — the REQUIREMENTS.md traceability table maps exactly these 13 IDs to Phase 4.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None | — | No TODOs, FIXMEs, placeholders, or stub implementations detected | — | — |

Specific checks:
- `passaFiltros()` — previously a stub (plan 01) returning `true` always; plan 02 fully implemented all 4 filter checks. Confirmed: no stub remains.
- All mutable BFS state (`visited`, `queue`, `errors`, counters) are local variables inside `start()` — no class-level mutable fields that would corrupt concurrent runs.
- `ProspeccaoLeadPersistenceHelper` is a separate `@Service` bean — REQUIRES_NEW propagation is correctly applied through Spring's proxy.

### Human Verification Required

#### 1. End-to-End BFS Run Against Live TJ-SP

**Test:** POST `{"processoSemente": "<valid-CNJ>"}` to `/api/v1/prospeccao` against a running instance connected to real e-SAJ and CAC endpoints. Then poll GET `/api/v1/prospeccao/{id}` every 10 seconds until status becomes CONCLUIDA.
**Expected:** 202 on POST; counters increase during polling; CONCLUIDA response includes a non-empty `leads[]` array with scored, persisted leads linked to real creditors and precatórios.
**Why human:** BFS correctness at runtime requires live TJ-SP scraping, a real PostgreSQL instance with Flyway migrations applied, and network connectivity. Unit tests mock all scrapers and repositories.

#### 2. Counter Visibility Under Concurrency (Retry-After Polling)

**Test:** Start a prospection on a process with many creditors (depth 2, maxCredores 50). While BFS runs, poll GET `/{id}` repeatedly and verify `processosVisitados` and `leadsQualificados` counters increment between polls.
**Expected:** Counters are visible to the polling client before BFS completes because `atualizarContadores` uses `REQUIRES_NEW` (committed immediately). Retry-After: 10 header present on all EM_ANDAMENTO responses.
**Why human:** REQUIRES_NEW visibility across concurrent transactions requires a real database with proper isolation — cannot be simulated in unit tests.

#### 3. Partial Failure Isolation at Runtime

**Test:** Configure a seed that is known to have precatório incidents, then simulate a CAC scraper failure (e.g., block the CAC host at the OS level or configure an invalid CAC URL for one run).
**Expected:** BFS completes with status CONCLUIDA (not ERRO); `erroMensagem` contains the failed precatório number; leads from other successful incidents are present in GET `/{id}` response.
**Why human:** Per-incidente failure isolation requires an actual scraper failure mid-run against live services.

### Gaps Summary

No gaps found. All 13 must-have truths are verified at all four levels (exists, substantive, wired, data-flowing). The three human verification items above are runtime integration tests that cannot be automated without live infrastructure — they do not represent missing implementation.

---

_Verified: 2026-04-06T20:30:00Z_
_Verifier: Claude (gsd-verifier)_
