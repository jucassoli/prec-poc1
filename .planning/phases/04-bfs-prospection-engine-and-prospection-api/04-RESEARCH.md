# Phase 4: BFS Prospection Engine and Prospection API - Research

**Researched:** 2026-04-06
**Domain:** Kotlin/Spring Boot async BFS engine + REST polling API
**Confidence:** HIGH

## Summary

Phase 4 wires together all prior phase infrastructure (async executor, scrapers, scoring, repositories) into a working BFS traversal engine. The codebase is already well-structured: `AsyncConfig` exposes `prospeccaoExecutor`, all three scrapers are live with Resilience4j guards, `ScoringService` is ready, and the JPA entities and repositories are in place. The primary implementation work is the BFS loop itself, the filter application logic, and the five REST endpoints with their 202/polling contract.

The single schema gap to address up front: `Prospeccao` entity and `V1__create_tables.sql` are missing the `leads_qualificados` column. The SPEC.md research document and the CONTEXT.md decision D-14 both call for `leadsQualificados` as a third counter in the GET status response. A Flyway migration `V2__add_leads_qualificados.sql` must be created before the BFS engine can increment it. The entity field and DB column are required by plans 04-01 and 04-03.

The async dispatch pattern is straightforward: `@Async("prospeccaoExecutor")` on a `BfsProspeccaoEngine.start()` method, called from the controller after persisting the initial `Prospeccao` record and flushing its ID. The controller can then return 202 with the ID before the engine thread begins work.

**Primary recommendation:** Start plan 04-01 with the Flyway migration, then implement BFS core with a well-isolated try/catch per process node, committing lead persistence with `@Transactional(propagation = Propagation.REQUIRES_NEW)` and updating the Prospeccao counters with a short-lived outer `@Transactional(propagation = Propagation.REQUIRES_NEW)` counter-update helper. Build REST endpoints in 04-03 against the repository methods that already exist.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**BFS Traversal Strategy**
- D-01: Classic level-by-level BFS — process all nodes at current depth before going deeper. ArrayDeque as the queue. profundidadeMaxima is respected naturally by depth tracking.
- D-02: BFS expansion via creditor name search: seed process -> extract creditors -> buscarPorNome for each creditor -> find their other processes -> repeat at next depth level.
- D-03: Cap name search results at 10 processes per creditor name — prevents common names from exploding the queue. Configurable in application.yml.
- D-04: One lead per (creditor, precatorio) pair — same creditor can produce multiple leads if they have precatorios from different processes.
- D-05: Job-local visited set (Set<String> of process numbers) prevents cycles and redundant scraping within a single prospection run.

**Failure Isolation**
- D-06: When a scraper call fails for one process: log the failure, skip that branch, continue BFS with remaining queue items.
- D-07: Prospection final status is CONCLUIDA even when some scraper calls failed — failures logged in erroMensagem as aggregated list.
- D-08: Each lead is persisted with @Transactional(REQUIRES_NEW) so a single persist failure doesn't roll back the entire run.

**Prospection Filters**
- D-09: Filters applied during BFS traversal — leads that don't match are not scored or persisted.
- D-10: Filters only affect lead creation, NOT BFS expansion — all branches are explored regardless of filters.
- D-11: credoresEncontrados counter counts only leads that pass filters and are persisted.

**API Response Contract**
- D-12: POST accepts flat JSON: `{"processoSemente": "...", "profundidadeMaxima": 2, "maxCredores": 50, "entidadesDevedoras": [...], "valorMinimo": 50000, "apenasAlimentar": false, "apenasPendentes": true}`.
- D-13: POST returns HTTP 202 with `{"prospeccaoId": <id>}`. CNJ validation on processoSemente returns 400 synchronously before async dispatch.
- D-14: GET /{id} returns status with three counters: processosVisitados, credoresEncontrados, leadsQualificados. Includes `Retry-After: 10` header while EM_ANDAMENTO.
- D-15: When CONCLUIDA, GET response embeds `leads[]` array inline with full lead data.
- D-16: When ERRO, GET response includes erroMensagem describing the failure.
- D-17: GET /api/v1/prospeccao lists all runs with pagination, filterable by status query parameter.

### Claude's Discretion
- BfsProspeccaoEngine internal implementation details (queue management, depth tracking)
- ProspeccaoRequest DTO field validation annotations
- ProspeccaoResponse DTO structure (how leads[] is nested)
- CNJ process number regex pattern
- How erroMensagem aggregates multiple failures (newline-separated, JSON array, etc.)
- Search result ordering from buscarPorNome (which 10 to keep if more than 10 results)

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PROS-01 | BFS recursive prospection from seed process number | BfsProspeccaoEngine with ArrayDeque queue; all three scrapers and ScoringService are ready |
| PROS-02 | Respect profundidadeMaxima (default 2) | Depth tracking in BFS queue items as `Pair<String, Int>`; configurable from ProspeccaoRequest |
| PROS-03 | Respect maxCredores (default 50) and stop when limit reached | Early-exit check before BFS expansion loop; maxCredores is already on Prospeccao entity |
| PROS-04 | Track visited process numbers to prevent cycles | Job-local `mutableSetOf<String>()` visited set; add to set before processing each node |
| PROS-05 | Filters: entidadesDevedoras, valorMinimo, apenasAlimentar, apenasPendentes | Applied inside BFS loop after fetching Precatorio; filter helper method referencing request fields |
| PROS-06 | Run asynchronously — POST returns 202 immediately | @Async("prospeccaoExecutor") on engine.start(); controller persists Prospeccao and returns 202 before engine runs |
| PROS-07 | CNJ format validation returns 400 synchronously | @Pattern annotation on DTO field (same regex as ProcessoController); or manual pre-dispatch check |
| PROS-08 | Partial failures do not abort — logged in erro_mensagem | Per-node try/catch; error list accumulated; at BFS completion, join errors and update erroMensagem |
| API-01 | POST /api/v1/prospeccao returns 202 with prospeccaoId | ProspeccaoController.iniciar(); ResponseEntity.accepted().body(ProspeccaoIniciadaDTO(id)) |
| API-02 | GET /{id} returns status, counters, full lead list when CONCLUIDA | ProspeccaoController.getStatus(); LeadRepository.findByProspeccaoId() when CONCLUIDA |
| API-03 | Retry-After: 10 header while EM_ANDAMENTO | httpResponse.header("Retry-After", "10") in controller when status == EM_ANDAMENTO |
| API-04 | ERRO status includes erroMensagem | erroMensagem field on ProspeccaoStatusDTO; populated from Prospeccao.erroMensagem |
| API-05 | GET /api/v1/prospeccao lists all with pagination + status filter | ProspeccaoController.listar(); ProspeccaoRepository.findByStatus(status, pageable) already defined |
</phase_requirements>

---

## Standard Stack

### Core (already in build.gradle.kts — no new dependencies needed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.5.3 | Web + JPA + Async + Validation | Already in project [VERIFIED: build.gradle.kts] |
| Kotlin | 2.2.0 | Implementation language | Already in project [VERIFIED: build.gradle.kts] |
| Spring @Async | included in spring-context | Offloads BFS to thread pool | `prospeccaoExecutor` already configured [VERIFIED: AsyncConfig.kt] |
| Spring Data JPA | included in spring-boot-starter-data-jpa | Repository access in BFS loop | All repositories exist [VERIFIED: repository/*.kt] |
| Flyway | included in spring-boot | Schema migration for new column | V1 migration already applied [VERIFIED: V1__create_tables.sql] |
| JUnit 5 + MockK | test scope | Unit testing BFS and controller | Established test pattern [VERIFIED: build.gradle.kts, controller tests] |
| springmockk | 4.0.2 | @WebMvcTest + MockK integration | Used in all controller tests [VERIFIED: ProcessoControllerTest.kt] |

**No new runtime dependencies required for Phase 4.** All infrastructure exists.

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Testcontainers PostgreSQL | 2.0.4 | Integration test with real DB | BFS persistence integration test |
| MockWebServer | 4.12.0 | Stub HTTP responses | Only if testing scraper interactions through BFS |

---

## Architecture Patterns

### Recommended Project Structure (new files for Phase 4)

```
src/main/kotlin/br/com/precatorios/
├── engine/
│   └── BfsProspeccaoEngine.kt      # @Service, @Async("prospeccaoExecutor") start()
├── controller/
│   └── ProspeccaoController.kt     # @RestController POST 202, GET status, GET list
├── dto/
│   ├── ProspeccaoRequestDTO.kt     # flat JSON request with @Valid fields
│   ├── ProspeccaoIniciadaDTO.kt    # {"prospeccaoId": <id>}
│   ├── ProspeccaoStatusDTO.kt      # status + counters + leads[]
│   ├── ProspeccaoListItemDTO.kt    # list item (no leads[])
│   └── LeadSummaryDTO.kt           # inline lead data in CONCLUIDA response
src/main/resources/db/migration/
└── V2__add_leads_qualificados.sql  # ADD COLUMN leads_qualificados INTEGER DEFAULT 0
```

### Pattern 1: Async Dispatch with Pre-persisted Entity

The controller creates and persists the `Prospeccao` entity BEFORE calling the async engine. This ensures the entity has an ID available to return as 202 body.

```kotlin
// Source: Spring @Async pattern — [ASSUMED]
@PostMapping
fun iniciar(@RequestBody @Valid request: ProspeccaoRequestDTO): ResponseEntity<ProspeccaoIniciadaDTO> {
    // Sync: validate already done by @Valid + @Pattern on DTO field
    val prospeccao = prospeccaoService.criar(request)  // persist, get ID
    bfsProspeccaoEngine.start(prospeccao.id!!, request) // @Async fires here, returns immediately
    return ResponseEntity
        .accepted()
        .body(ProspeccaoIniciadaDTO(prospeccaoId = prospeccao.id!!))
}
```

**Critical:** The `@Async` call must happen AFTER the `save()` transaction is committed. Since the controller method is not `@Transactional`, the `prospeccaoService.criar()` call will commit the entity immediately. The engine will be able to load it. [VERIFIED: AsyncConfig.kt shows prospeccaoExecutor is defined; Spring @Async behavior is ASSUMED]

### Pattern 2: BFS Queue Item with Depth Tracking

```kotlin
// Source: standard BFS pattern — [ASSUMED]
data class QueueItem(val processoNumero: String, val depth: Int)

val queue: ArrayDeque<QueueItem> = ArrayDeque()
val visited: MutableSet<String> = mutableSetOf()

queue.add(QueueItem(processoSemente, 0))
visited.add(processoSemente)

while (queue.isNotEmpty()) {
    val item = queue.removeFirst()
    if (item.depth > profundidadeMaxima) break  // or skip, but early break is safe for level-BFS
    
    try {
        val processo = esajScraper.fetchProcesso(item.processoNumero)
        // increment processosVisitados
        // for each creditor -> fetch precatorio -> apply filters -> score -> persist lead
        
        if (item.depth < profundidadeMaxima && credoresEncontrados < maxCredores) {
            // expand: buscarPorNome -> cap at maxSearchResults -> add non-visited to queue
        }
    } catch (e: Exception) {
        // log, add to error list, continue
    }
}
```

### Pattern 3: Per-Lead REQUIRES_NEW Transaction (D-08)

The outer BFS method must NOT be `@Transactional` — it must be transactionless so each helper method with `REQUIRES_NEW` creates its own independent transaction. [VERIFIED: this decision is in STATE.md Phase 1 decisions and CONTEXT.md D-08]

```kotlin
// Inner helper method — separate @Service bean required for REQUIRES_NEW to work through Spring proxy
@Transactional(propagation = Propagation.REQUIRES_NEW)
fun persistirLead(prospeccao: Prospeccao, credor: Credor, precatorio: Precatorio, scored: ScoredResult): Lead {
    val lead = Lead().apply {
        this.prospeccao = prospeccao
        this.credor = credor
        this.precatorio = precatorio
        this.score = scored.total
        this.scoreDetalhes = objectMapper.writeValueAsString(scored.detalhes)
        this.dataCriacao = LocalDateTime.now()
    }
    return leadRepository.save(lead)
}
```

**Proxy trap:** `REQUIRES_NEW` only works if the method is called on a Spring proxy — i.e., on a different bean. If `persistirLead` is on the same class as the BFS loop, `this.persistirLead()` bypasses the proxy and the propagation has no effect. The persist helper must be on a separate `@Service` class, or the BFS engine must inject a self-reference (`@Lazy private val self: BfsProspeccaoEngine`). [ASSUMED — standard Spring AOP self-invocation limitation]

### Pattern 4: Counter Updates with Separate Transaction

Counter updates (`processosVisitados++`, `credoresEncontrados++`) should also use short `REQUIRES_NEW` transactions so progress is visible to polling clients before BFS completes. If the entire BFS ran in one transaction, counters would not be visible until commit.

```kotlin
@Transactional(propagation = Propagation.REQUIRES_NEW)
fun incrementarContadores(prospeccaoId: Long, processosVisitados: Int, credoresEncontrados: Int, leadsQualificados: Int) {
    val p = prospeccaoRepository.findById(prospeccaoId).orElseThrow()
    p.processosVisitados = processosVisitados
    p.credoresEncontrados = credoresEncontrados
    p.leadsQualificados = leadsQualificados
    prospeccaoRepository.save(p)
}
```

### Pattern 5: CNJ Number Validation (PROS-07)

The existing `ProcessoController` uses this regex on a `@PathVariable`:

```
^[0-9]{7}-[0-9]{2}\.[0-9]{4}\.[0-9]\.[0-9]{2}\.[0-9]{4}$
```

[VERIFIED: ProcessoController.kt line 32-37]

For `ProspeccaoRequestDTO`, apply the same `@field:Pattern(regexp = "...")` on the `processoSemente` field. Spring Validation + `@Valid` on the controller method parameter will reject malformed CNJ numbers with a `ConstraintViolationException` mapped by `GlobalExceptionHandler` to 400 before any async dispatch. [VERIFIED: GlobalExceptionHandler.kt handles ConstraintViolationException]

### Pattern 6: Retry-After Header (API-03)

```kotlin
@GetMapping("/{id}")
fun getStatus(@PathVariable id: Long): ResponseEntity<ProspeccaoStatusDTO> {
    val prospeccao = prospeccaoRepository.findById(id)
        .orElseThrow { ProspeccaoNaoEncontradaException(id) }
    
    val builder = ResponseEntity.ok()
    if (prospeccao.status == StatusProspeccao.EM_ANDAMENTO) {
        builder.header("Retry-After", "10")
    }
    return builder.body(toStatusDTO(prospeccao))
}
```

### Anti-Patterns to Avoid

- **Wrapping entire BFS in one `@Transactional`:** Progress counters invisible during run, one failure rolls back all persisted leads.
- **Calling REQUIRES_NEW on same-bean method:** Self-invocation bypasses Spring proxy — propagation is silently ignored. Must use a separate bean.
- **Dispatching @Async inside a @Transactional method:** The transaction may not be committed before the async thread starts, causing the engine to load an entity that doesn't exist yet (LazyInitializationException or entity-not-found). Controller should NOT be @Transactional.
- **Blocking the async thread with reactive operators:** DataJudClient uses WebClient + `.block()` — this is acceptable in a non-reactive thread pool executor context, but should not be mixed with reactor's non-blocking scheduler.
- **Mutable shared state in the BFS engine bean:** The visited set and queue must be local variables inside the `start()` method, not class-level fields. The engine bean is a singleton — concurrent prospections would share state if fields are used.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP retry + rate limit | Manual retry loop | Resilience4j (already on all scrapers) | Handles backoff, circuit-break, 429 pause — already configured |
| Async thread pool | Manual ExecutorService | @Async("prospeccaoExecutor") | Spring manages lifecycle, shutdown, thread naming — already configured |
| In-memory caching of scraped data | Map<String, ProcessoScraped> in BFS | @Cacheable on EsajScraper/CacScraper | BFS re-visits processes across runs; cache survives per-run boundaries |
| Pagination | Manual offset math | Spring Data Pageable + Page<T> | ProspeccaoRepository.findByStatus() already returns Page<Prospeccao> |
| CNJ format check | Custom string parser | @Pattern(regexp=...) via Jakarta Validation | GlobalExceptionHandler already maps ConstraintViolationException to 400 |
| Schema migration | ALTER TABLE in startup code | Flyway V2 migration script | Flyway tracks applied migrations; safe for Docker Compose restarts |

---

## Critical Schema Gap: leadsQualificados Counter

**What:** `Prospeccao` entity and `V1__create_tables.sql` do NOT have a `leads_qualificados` column. The GET response (D-14, API-02) requires this counter. SPEC.md shows it in both the in-progress and CONCLUIDA response shapes. The Phase 1 research (`01-RESEARCH.md` line 380) showed it in the target schema — but it was not included in the executed `V1__create_tables.sql`. [VERIFIED: V1__create_tables.sql line 45-56, Prospeccao.kt lines 1-42]

**Resolution required in plan 04-01:**
1. Create `src/main/resources/db/migration/V2__add_leads_qualificados.sql`:
   ```sql
   ALTER TABLE prospeccoes ADD COLUMN leads_qualificados INTEGER NOT NULL DEFAULT 0;
   ```
2. Add `leadsQualificados: Int = 0` field to `Prospeccao.kt` with `@Column(name = "leads_qualificados")`.
3. Increment this counter alongside `credoresEncontrados` in the BFS loop whenever a lead passes filters and is persisted.

---

## Common Pitfalls

### Pitfall 1: @Async Self-Invocation (Spring AOP Proxy Bypass)

**What goes wrong:** Calling an `@Async` method from within the same bean does not dispatch asynchronously — Spring's proxy is bypassed and the method runs synchronously on the caller's thread.
**Why it happens:** Spring `@Async` works by wrapping the bean in a proxy. Direct `this.method()` calls skip the proxy.
**How to avoid:** The `@Async` method (`start()`) must be called from a different bean (the controller). The BFS engine is a separate `@Service` from the controller — this is already the correct architecture.
**Warning signs:** POST endpoint blocks for >500ms, or the engine runs on the HTTP request thread (`http-nio` thread name prefix).

### Pitfall 2: REQUIRES_NEW on Same Bean Method

**What goes wrong:** `@Transactional(propagation = Propagation.REQUIRES_NEW)` on a private method, or called via `this.`, does not create a new transaction.
**Why it happens:** Same AOP proxy issue as @Async — self-invocation bypasses the transaction proxy.
**How to avoid:** Create a separate `@Service` class (e.g., `ProspeccaoLeadPersistenceHelper`) that the BFS engine injects and calls for lead persistence and counter updates. [ASSUMED — standard Spring limitation]
**Warning signs:** Test shows a single lead persist failure rolling back all previous leads in the same BFS run.

### Pitfall 3: Entity Loaded Before Commit in @Async Thread

**What goes wrong:** The async thread tries to `findById(prospeccaoId)` immediately, but the entity was saved in the controller method, which may not have committed yet.
**Why it happens:** Spring @Transactional controller methods commit at the end of the method. If @Async thread starts before that commit, the entity is not visible.
**How to avoid:** The controller method should NOT be `@Transactional`. The `prospeccaoService.criar()` call must have its own `@Transactional` so it commits before returning. The controller then calls `bfsProspeccaoEngine.start()` after the criar() transaction is committed. [ASSUMED — standard Spring async dispatch pattern]
**Warning signs:** `EntityNotFoundException` or empty result from `findById` at engine start.

### Pitfall 4: BFS Engine Class-Level Mutable State

**What goes wrong:** If `visited` set or `queue` are instance fields on the `BfsProspeccaoEngine` bean, concurrent prospection runs will share the same set/queue.
**Why it happens:** Spring beans are singletons by default.
**How to avoid:** Declare `visited` and `queue` as local variables inside the `start()` method, not as bean-level fields.
**Warning signs:** Process numbers from run A appear in visited set of run B; BFS terminates early on second concurrent run.

### Pitfall 5: maxCredores Check Placement

**What goes wrong:** If `maxCredores` early-exit happens after expansion (enqueueing), the queue may have been inflated before the check triggers. The stop condition must be checked before adding new processes to the queue.
**Why it happens:** Off-by-one logic in loop ordering.
**How to avoid:** Check `credoresEncontrados >= maxCredores` at the top of the BFS while-loop, after dequeueing but before scraping. Do NOT exit mid-loop after persisting a lead — the check should be at node dequeue time to allow the current node's leads to be processed but prevent further expansion.
**Warning signs:** credoresEncontrados exceeds maxCredores in test assertions.

### Pitfall 6: Flyway Migration Order and Naming

**What goes wrong:** A migration file named `V2__add_leads_qualificados.sql` fails if Flyway has already applied `V1__create_tables.sql` and there is a checksum mismatch on V1 (if V1 was altered in a test environment).
**Why it happens:** Flyway validates checksums of previously applied migrations.
**How to avoid:** Never modify V1. Always create new V2, V3 migration files for schema changes. [VERIFIED: V1__create_tables.sql is the only migration file]

---

## Code Examples

### BFS Engine Skeleton (Discretion Area)

```kotlin
// Source: pattern synthesized from AsyncConfig.kt + CONTEXT.md decisions — [ASSUMED]
@Service
class BfsProspeccaoEngine(
    private val esajScraper: EsajScraper,
    private val cacScraper: CacScraper,
    private val scoringService: ScoringService,
    private val prospeccaoRepository: ProspeccaoRepository,
    private val persistenceHelper: ProspeccaoLeadPersistenceHelper,
    @Value("\${prospeccao.max-search-results-per-creditor:10}") private val maxSearchResults: Int
) {
    private val log = LoggerFactory.getLogger(BfsProspeccaoEngine::class.java)

    @Async("prospeccaoExecutor")
    fun start(prospeccaoId: Long, request: ProspeccaoRequestDTO) {
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<String, Int>>() // (processoNumero, depth)
        val errors = mutableListOf<String>()
        var credoresEncontrados = 0
        var processosVisitados = 0
        var leadsQualificados = 0

        queue.add(Pair(request.processoSemente, 0))
        visited.add(request.processoSemente)

        val prospeccao = prospeccaoRepository.findById(prospeccaoId).orElseThrow()

        while (queue.isNotEmpty()) {
            if (credoresEncontrados >= (request.maxCredores ?: prospeccao.maxCredores)) break

            val (numero, depth) = queue.removeFirst()

            try {
                val processo = esajScraper.fetchProcesso(numero)
                processosVisitados++

                // Extract creditors, fetch precatorios, apply filters, score, persist
                for (parte in processo.partes) {
                    if (credoresEncontrados >= (request.maxCredores ?: prospeccao.maxCredores)) break
                    // ... filter application, scoring, persistirLead via persistenceHelper
                    credoresEncontrados++
                    leadsQualificados++ // only if score > 0 (SCOR-04 is Phase 5 but counter still tracks)
                }

                // BFS expansion
                if (depth < (request.profundidadeMaxima ?: prospeccao.profundidadeMax)) {
                    for (parte in processo.partes) {
                        val results = try {
                            esajScraper.buscarPorNome(parte.nome).take(maxSearchResults)
                        } catch (e: Exception) {
                            errors.add("buscarPorNome(${parte.nome}): ${e.message}")
                            emptyList()
                        }
                        for (r in results) {
                            if (r.numero !in visited) {
                                visited.add(r.numero)
                                queue.add(Pair(r.numero, depth + 1))
                            }
                        }
                    }
                }

                persistenceHelper.atualizarContadores(prospeccaoId, processosVisitados, credoresEncontrados, leadsQualificados)

            } catch (e: Exception) {
                log.warn("BFS node {} failed: {}", numero, e.message)
                errors.add("$numero: ${e.message}")
            }
        }

        // Finalize
        persistenceHelper.finalizarProspeccao(
            prospeccaoId = prospeccaoId,
            processosVisitados = processosVisitados,
            credoresEncontrados = credoresEncontrados,
            leadsQualificados = leadsQualificados,
            erroMensagem = if (errors.isEmpty()) null else errors.joinToString("\n"),
            status = StatusProspeccao.CONCLUIDA
        )
    }
}
```

### ProspeccaoController POST — 202 Pattern

```kotlin
// Source: ProcessoController.kt pattern + CONTEXT.md D-12/D-13 — [VERIFIED: ProcessoController.kt]
@PostMapping
@Operation(summary = "Iniciar prospecção BFS a partir de processo-semente")
fun iniciar(@RequestBody @Valid request: ProspeccaoRequestDTO): ResponseEntity<ProspeccaoIniciadaDTO> {
    val prospeccao = prospeccaoService.criar(request)  // persist, commit
    bfsProspeccaoEngine.start(prospeccao.id!!, request) // @Async dispatch — returns CompletableFuture immediately
    return ResponseEntity
        .status(HttpStatus.ACCEPTED)
        .body(ProspeccaoIniciadaDTO(prospeccaoId = prospeccao.id!!))
}
```

### ProspeccaoController GET — Retry-After Header

```kotlin
// Source: CONTEXT.md D-14, API-03 — [VERIFIED: CONTEXT.md]
@GetMapping("/{id}")
fun getStatus(@PathVariable id: Long): ResponseEntity<ProspeccaoStatusDTO> {
    val prospeccao = prospeccaoRepository.findById(id)
        .orElseThrow { ProspeccaoNaoEncontradaException(id) }

    val dto = toStatusDTO(prospeccao)
    return if (prospeccao.status == StatusProspeccao.EM_ANDAMENTO) {
        ResponseEntity.ok()
            .header("Retry-After", "10")
            .body(dto)
    } else {
        ResponseEntity.ok(dto)
    }
}
```

### CNJ Regex on DTO Field

```kotlin
// Source: ProcessoController.kt line 32-37 — [VERIFIED]
data class ProspeccaoRequestDTO(
    @field:NotBlank
    @field:Pattern(
        regexp = "^[0-9]{7}-[0-9]{2}\\.[0-9]{4}\\.[0-9]\\.[0-9]{2}\\.[0-9]{4}$",
        message = "Formato CNJ invalido. Esperado: NNNNNNN-DD.AAAA.J.TR.OOOO"
    )
    val processoSemente: String,

    val profundidadeMaxima: Int? = null,
    val maxCredores: Int? = null,
    val entidadesDevedoras: List<String>? = null,
    val valorMinimo: java.math.BigDecimal? = null,
    val apenasAlimentar: Boolean? = null,
    val apenasPendentes: Boolean? = null
)
```

### Filter Application (D-09, D-10)

```kotlin
// Source: CONTEXT.md D-09/D-10 — [VERIFIED: CONTEXT.md]
private fun passaFiltros(precatorio: Precatorio, request: ProspeccaoRequestDTO): Boolean {
    // entidadesDevedoras: if filter specified, precatorio.entidadeDevedora must match one
    if (!request.entidadesDevedoras.isNullOrEmpty()) {
        val devedora = precatorio.entidadeDevedora?.uppercase() ?: return false
        if (request.entidadesDevedoras.none { devedora.contains(it.uppercase()) }) return false
    }
    // valorMinimo
    val valorMin = request.valorMinimo
    if (valorMin != null && (precatorio.valorAtualizado == null || precatorio.valorAtualizado!! < valorMin)) return false
    // apenasAlimentar
    if (request.apenasAlimentar == true && precatorio.natureza?.uppercase()?.contains("ALIMENTAR") != true) return false
    // apenasPendentes
    if (request.apenasPendentes == true && precatorio.statusPagamento?.uppercase() != "PENDENTE") return false
    return true
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Coroutines for async | Spring @Async + ThreadPoolTaskExecutor | Phase 1 decision | Simpler Spring integration; no coroutine scope management |
| Single outer @Transactional | REQUIRES_NEW per lead persist | Phase 1 decision | Partial failure isolation; progress visible during run |
| Global filter guard at queue entrance | Filters on lead creation, not BFS expansion | CONTEXT.md D-10 | Creditors not matching filters still explored for connected leads |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | @Async self-invocation limitation requires calling @Async method from a different bean | Architecture Patterns - Pattern 1 | BFS engine may block if dispatched incorrectly; easy to catch in smoke test |
| A2 | REQUIRES_NEW on same-class method bypasses Spring proxy (self-invocation) | Architecture Patterns - Pattern 3 | Lead persistence failures could roll back all leads; detectable in integration test |
| A3 | Controller method must NOT be @Transactional to ensure Prospeccao entity is committed before async thread starts | Architecture Patterns - Pattern 1 | EntityNotFoundException in engine; caught immediately in smoke test |
| A4 | erroMensagem format as newline-separated strings is a valid choice for the discretion area | Code Examples | Cosmetic only; callers can split on newline |

---

## Open Questions

1. **leadsQualificados counter semantics relative to SCOR-04**
   - What we know: SCOR-04 says "leads with score 0 are still persisted but excluded from default lead list results" (Phase 5 concern). D-14 says leadsQualificados counter tracks leads that pass filters and are persisted.
   - What's unclear: Should leadsQualificados count zero-score leads (all persisted leads) or only non-zero-score leads?
   - Recommendation: Count all filter-passing persisted leads (i.e., same as credoresEncontrados per D-11). The distinction between 0-score and non-0-score is a display concern handled in Phase 5 leads listing. This avoids an additional scoring check during the BFS just for the counter.

2. **Prospeccao filter fields storage**
   - What we know: ProspeccaoRequest has filter fields. The `Prospeccao` entity does not store them (entidadesDevedoras, valorMinimo, etc.).
   - What's unclear: Should the request filters be stored for auditability (e.g., re-running the prospection)? No requirement explicitly asks for this.
   - Recommendation: Do not store filters on the Prospeccao entity for Phase 4. The API response only needs current counters and status. If needed, store the raw request JSON in a `filtros JSONB` column (v2 concern).

---

## Environment Availability

All dependencies for Phase 4 are already present in the project. No new external tools or services are required.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Spring @Async + ThreadPoolTaskExecutor | PROS-06 | Yes | Spring Boot 3.5.3 | — |
| EsajScraper.fetchProcesso() | BFS node expansion | Yes | existing | — |
| EsajScraper.buscarPorNome() | BFS creditor search | Yes | existing | — |
| CacScraper.fetchPrecatorio() | Lead data | Yes | existing | — |
| ScoringService.score() | Lead scoring | Yes | existing | — |
| ProspeccaoRepository | Persistence | Yes | existing | — |
| LeadRepository | Lead persistence | Yes | existing | — |
| PostgreSQL (via Testcontainers) | Integration tests | Yes | 2.0.4 | — |
| Flyway | V2 migration | Yes | included in Spring Boot | — |

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + MockK + springmockk 4.0.2 |
| Config file | build.gradle.kts — `tasks.withType<Test> { useJUnitPlatform() }` |
| Quick run command | `./gradlew test --tests "br.com.precatorios.engine.*" --tests "br.com.precatorios.controller.ProspeccaoControllerTest"` |
| Full suite command | `./gradlew test` |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PROS-01 | BFS visits seed process and discovers creditor processes | unit | `./gradlew test --tests "br.com.precatorios.engine.BfsProspeccaoEngineTest"` | No — Wave 0 |
| PROS-02 | profundidadeMaxima=1 stops expansion after first level | unit | `./gradlew test --tests "br.com.precatorios.engine.BfsProspeccaoEngineTest.depth control"` | No — Wave 0 |
| PROS-03 | maxCredores=2 stops BFS after 2 leads persisted | unit | `./gradlew test --tests "br.com.precatorios.engine.BfsProspeccaoEngineTest.max credores"` | No — Wave 0 |
| PROS-04 | visited set prevents re-scraping same process number | unit | `./gradlew test --tests "br.com.precatorios.engine.BfsProspeccaoEngineTest.visited set"` | No — Wave 0 |
| PROS-05 | valorMinimo filter excludes low-value precatorios | unit | `./gradlew test --tests "br.com.precatorios.engine.BfsProspeccaoEngineTest.filters"` | No — Wave 0 |
| PROS-06 | POST /api/v1/prospeccao returns 202 within 500ms | @WebMvcTest | `./gradlew test --tests "br.com.precatorios.controller.ProspeccaoControllerTest.returns 202"` | No — Wave 0 |
| PROS-07 | Invalid CNJ returns 400 synchronously | @WebMvcTest | `./gradlew test --tests "br.com.precatorios.controller.ProspeccaoControllerTest.invalid CNJ"` | No — Wave 0 |
| PROS-08 | Partial scraper failure: BFS still CONCLUIDA, erroMensagem populated | unit | `./gradlew test --tests "br.com.precatorios.engine.BfsProspeccaoEngineTest.partial failure"` | No — Wave 0 |
| API-01 | POST returns prospeccaoId in body | @WebMvcTest | `./gradlew test --tests "br.com.precatorios.controller.ProspeccaoControllerTest"` | No — Wave 0 |
| API-02 | GET /{id} returns EM_ANDAMENTO with counters | @WebMvcTest | `./gradlew test --tests "br.com.precatorios.controller.ProspeccaoControllerTest.status in progress"` | No — Wave 0 |
| API-03 | Retry-After: 10 header present while EM_ANDAMENTO | @WebMvcTest | `./gradlew test --tests "br.com.precatorios.controller.ProspeccaoControllerTest.retry after header"` | No — Wave 0 |
| API-04 | ERRO status includes erroMensagem | @WebMvcTest | `./gradlew test --tests "br.com.precatorios.controller.ProspeccaoControllerTest.erro response"` | No — Wave 0 |
| API-05 | GET list returns paginated results, filterable by status | @WebMvcTest | `./gradlew test --tests "br.com.precatorios.controller.ProspeccaoControllerTest.list with pagination"` | No — Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "br.com.precatorios.engine.*" --tests "br.com.precatorios.controller.ProspeccaoControllerTest"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/kotlin/br/com/precatorios/engine/BfsProspeccaoEngineTest.kt` — covers PROS-01 through PROS-08
- [ ] `src/test/kotlin/br/com/precatorios/controller/ProspeccaoControllerTest.kt` — covers API-01 through API-05

*(Existing test infrastructure: JUnit 5 + MockK + springmockk already in build.gradle.kts — no new test framework installation needed.)*

---

## Security Domain

> `security_enforcement` is not explicitly set to false in config.json — treated as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Out of scope for v1 (SEC-01 is v2) |
| V3 Session Management | No | Stateless API; no sessions |
| V4 Access Control | No | Internal tool; no role-based access in v1 |
| V5 Input Validation | Yes | @field:Pattern on processoSemente; @Valid on request DTO; GlobalExceptionHandler maps ConstraintViolationException to 400 |
| V6 Cryptography | No | No secrets or data encryption in BFS engine |

### Known Threat Patterns for Spring @Async BFS

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Unbounded BFS queue — common creditor name causes thousands of process lookups | Denial of Service | maxSearchResults cap (D-03, configurable in application.yml); maxCredores early-exit (D-03, PROS-03) |
| Injecting malformed CNJ numbers to probe scraper responses | Tampering | @Pattern + Jakarta Validation rejects non-CNJ strings before async dispatch (PROS-07) |
| Concurrent prospections consuming all thread pool capacity | Denial of Service | AsyncConfig.queueCapacity = 25 already limits pending jobs; excess requests queue or reject [VERIFIED: AsyncConfig.kt] |

---

## Sources

### Primary (HIGH confidence)
- Codebase — AsyncConfig.kt, Prospeccao.kt, Lead.kt, ProspeccaoRepository.kt, LeadRepository.kt, ProcessoController.kt, GlobalExceptionHandler.kt, EsajScraper.kt, CacScraper.kt, ScoringService.kt, build.gradle.kts, V1__create_tables.sql, application.yml [VERIFIED: read directly]
- CONTEXT.md decisions D-01 through D-17 [VERIFIED: read directly]
- REQUIREMENTS.md — PROS-01 through PROS-08, API-01 through API-05 [VERIFIED: read directly]
- SPEC.md — leadsQualificados counter shape in GET response [VERIFIED: read lines 200-255]

### Secondary (MEDIUM confidence)
- STATE.md — Phase 1 decisions on @Async + REQUIRES_NEW per lead persist [VERIFIED: read directly]
- 01-RESEARCH.md — proposed schema including leads_qualificados column [VERIFIED: read line 380]

### Tertiary (LOW confidence / Assumed)
- Spring @Async self-invocation proxy limitation — well-known Spring AOP behavior [ASSUMED: standard training knowledge]
- REQUIRES_NEW self-invocation bypass — standard Spring limitation [ASSUMED: standard training knowledge]
- Controller must not be @Transactional to ensure commit before async dispatch [ASSUMED: standard Spring async pattern]

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries verified from build.gradle.kts; no new dependencies
- Architecture: HIGH for entity/repository layer (verified); MEDIUM for BFS engine internals (ASSUMED Spring patterns)
- Pitfalls: MEDIUM — Spring proxy/self-invocation limitations are well-known but marked ASSUMED

**Research date:** 2026-04-06
**Valid until:** 2026-05-06 (stable Spring Boot 3.x patterns)
