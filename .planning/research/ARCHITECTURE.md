# Architecture Patterns — Precatórios API

**Domain:** Web scraping + REST API, recursive graph traversal, async job lifecycle
**Researched:** 2026-04-03
**Overall confidence:** HIGH (Spring Boot patterns), MEDIUM (ASP.NET ViewState scraping specifics)

---

## Recommended Architecture

A layered monolith with a clearly separated scraper tier. The application has five distinct concerns that map to five layers:

```
HTTP Client (Swagger / FUNPREC frontend)
        |
        v
┌─────────────────────────────────────────┐
│         Controller Layer                │  REST API boundary; thin; no business logic
│  ProspeccaoController                   │
│  ProcessoController / PrecatorioController │
│  DataJudController / LeadController     │
└──────────────┬──────────────────────────┘
               │  DTOs only (no entities cross boundary)
               v
┌─────────────────────────────────────────┐
│         Service Layer                   │  Business rules, orchestration, async dispatch
│  ProspeccaoService  (BFS engine)        │
│  ScoringService     (stateless rules)   │
│  LeadService        (CRUD + filtering)  │
│  ProcessoService    (process lookup)    │
└──────┬──────────────────┬───────────────┘
       │ calls            │ reads/writes
       v                  v
┌──────────────┐   ┌──────────────────────┐
│ Scraper Layer│   │   Repository Layer   │
│ EsajScraper  │   │  ProcessoRepository  │
│ CacScraper   │   │  CredorRepository    │
│ DataJudClient│   │  PrecatorioRepository│
└──────┬───────┘   │  ProspeccaoRepository│
       │           │  LeadRepository      │
       v           └──────────┬───────────┘
┌──────────────┐              │
│ Cache Layer  │              v
│ Caffeine TTL │   ┌──────────────────────┐
│ (wraps scraper│  │     PostgreSQL       │
│  results)    │  └──────────────────────┘
└──────────────┘
```

---

## Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| `ProspeccaoController` | Accept POST to start job; return 202 + job ID; serve GET polling endpoint | `ProspeccaoService` |
| `ProspeccaoService` | Dispatch BFS job to thread pool; update `Prospeccao` status; orchestrate scraper calls | `EsajScraper`, `CacScraper`, `DataJudClient`, `ScoringService`, all Repositories |
| `EsajScraper` | Fetch e-SAJ pages via Jsoup; extract parties, incidents, movements | Caffeine cache (read-through); HTTP only |
| `CacScraper` | Maintain ASP.NET session state; GET/POST cycle for ViewState; extract precatorio data | Caffeine cache; HTTP only |
| `DataJudClient` | POST Elasticsearch DSL queries to DataJud REST API; handle pagination | Caffeine cache; HTTP only |
| `ScoringService` | Stateless function: `score(Precatorio, Filtros) -> Int`; reads rules from config | No I/O; pure function |
| `LeadService` | List/filter leads; update `status_contato` | `LeadRepository`, `CredorRepository`, `PrecatorioRepository` |
| Repository layer | Spring Data JPA interfaces; no business logic | PostgreSQL via Hibernate |
| Caffeine cache | Key-value store keyed on process/precatorio number; 24h TTL | Held in JVM heap |

### Boundary rules (enforce strictly)
- Controllers never hold JPA entities — always map to/from DTOs before crossing the controller boundary.
- Scrapers never talk to repositories directly — they return domain objects to the service layer, which persists.
- `ScoringService` is a pure function — no Spring cache, no DB access, fully unit-testable without a container.

---

## Data Flow

### Async Prospection Flow

```
POST /api/v1/prospeccao
  -> Controller validates request DTO
  -> ProspeccaoService.iniciar(request)
       -> INSERT prospeccoes (status=EM_ANDAMENTO) -> return prospeccaoId
       -> Submit ProspeccaoTask to @Async executor (fire-and-forget)
  -> Return 202 Accepted { prospeccaoId }

[Background thread — ProspeccaoTask.executar()]
  -> BFS loop (see BFS section below)
     -> UPDATE prospeccoes (credores_encontrados, processos_visitados) as work progresses
  -> On completion: UPDATE prospeccoes (status=CONCLUIDA, data_fim)
  -> On exception: UPDATE prospeccoes (status=ERRO, erro_mensagem)

GET /api/v1/prospeccao/{id}
  -> SELECT prospeccoes JOIN leads JOIN credores JOIN precatorios WHERE id=?
  -> Map entities -> response DTO (includes partial results if still EM_ANDAMENTO)
  -> Return 200 (any status — client polls until CONCLUIDA or ERRO)
```

### Scraper Read-Through Cache Flow

```
Service needs process data for number "X"
  -> Call EsajScraper.fetchProcess("X")
     -> Caffeine.get("esaj:X")
          HIT  -> return cached ProcessoDto
          MISS -> HTTP GET to e-SAJ
                  -> parse HTML with Jsoup
                  -> rate-limit delay (2 000 ms)
                  -> on failure: exponential backoff (1s, 2s, 4s; max 3 attempts)
                  -> store result in Caffeine (TTL 24h)
                  -> return ProcessoDto
```

### ViewState (CAC/SCP) Session Flow

```
CacScraper.fetchPrecatorio(numero)
  -> Check Caffeine cache (key "cac:numero")  — HIT: return immediately
  -> MISS:
     Step 1: GET /webmenupesquisa.aspx
               -> parse __VIEWSTATE, __VIEWSTATEGENERATOR, __EVENTVALIDATION
               -> store cookies from response (Jsoup Connection.cookieStore())
     Step 2: POST /pesquisainternetv2.aspx
               -> include: all hidden fields from Step 1 + search term
               -> include: cookies from Step 1
               -> parse result HTML
     -> Store parsed PrecatorioDto in Caffeine (24h TTL)
     -> Return PrecatorioDto
```

---

## Async Job Execution Pattern

### Recommendation: `@Async` with named `ThreadPoolTaskExecutor` (not Kotlin coroutines, not virtual threads — yet)

**Rationale:**
- The prospection job is a single long-running sequential task (rate-limited BFS), not a fan-out of concurrent requests. The bottleneck is the mandatory 2-second delay between HTTP requests, not thread count.
- `@Async` with a dedicated executor is simpler, easier to debug, and gives explicit control over the thread pool. Kotlin coroutines would add complexity without a concrete throughput benefit here.
- Virtual threads (JVM 21 + `spring.threads.virtual.enabled=true`) are worth enabling for the HTTP request handling layer (Tomcat), but **not** for the scraping executor — Jsoup operations are blocking and the controlled rate-limiting delay pattern maps better to platform threads.

### AsyncConfig.kt

```kotlin
@Configuration
@EnableAsync
class AsyncConfig {

    @Bean("prospeccaoExecutor")
    fun prospeccaoExecutor(): ThreadPoolTaskExecutor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 4          // 4 concurrent prospections max
            maxPoolSize = 4           // no burst; queue instead
            queueCapacity = 20        // backlog of pending jobs
            setThreadNamePrefix("prospeccao-")
            setWaitForTasksToCompleteOnShutdown(true)
            awaitTerminationSeconds = 120
            initialize()
        }
    }
}
```

**Thread pool sizing rationale:** Each scraping thread makes one HTTP request per 2 seconds. 4 threads = 2 requests/second total = safe rate against TJ-SP. Increasing beyond 4 risks rate-limit blocks. The SPEC already specifies `prospeccao.thread-pool-size: 4`.

### ProspeccaoService dispatch

```kotlin
@Service
class ProspeccaoService(
    private val prospeccaoRepository: ProspeccaoRepository,
    private val bfsEngine: BfsProspeccaoEngine
) {
    @Transactional
    fun iniciar(request: ProspeccaoRequest): Long {
        val prospeccao = Prospeccao(
            processoSemente = request.processoSemente,
            status = StatusProspeccao.EM_ANDAMENTO,
            profundidadeMax = request.profundidadeMaxima ?: 2,
            maxCredores = request.maxCredores ?: 50
        )
        val saved = prospeccaoRepository.save(prospeccao)
        bfsEngine.executarAsync(saved.id!!, request)  // dispatches to prospeccaoExecutor
        return saved.id!!
    }
}

@Component
class BfsProspeccaoEngine(...) {
    @Async("prospeccaoExecutor")
    fun executarAsync(prospeccaoId: Long, request: ProspeccaoRequest) {
        // BFS loop here — see BFS section
    }
}
```

---

## BFS Graph Traversal Design

### Thread-safe visited set — critical correctness requirement

The BFS loop runs in a single thread per prospection job (single `@Async` dispatch), so the visited set does **not** need to be concurrent. Use a plain `mutableSetOf<String>()` local to the job execution. Do not share state between concurrent prospection jobs.

```
fun executarAsync(prospeccaoId: Long, request: ProspeccaoRequest) {
    val visited = mutableSetOf<String>()
    val queue: ArrayDeque<Pair<String, Int>> = ArrayDeque()  // (processoNumero, depth)
    queue.add(request.processoSemente to 0)

    while (queue.isNotEmpty()) {
        val (numero, depth) = queue.removeFirst()

        if (numero in visited || depth > profundidadeMax) continue
        visited.add(numero)

        val processo = try {
            esajScraper.fetchProcess(numero)
        } catch (e: ScrapingException) {
            log.warn("Falha ao buscar processo $numero: ${e.message}")
            continue  // do not abort entire run on single failure
        }

        persistirProcesso(processo)

        for (parte in processo.partes.filter { it.isCredor() }) {
            val credor = persistirCredor(parte, numero)

            for (incidente in processo.incidentes) {
                val precatorio = cacScraper.fetchPrecatorio(incidente.numero)
                val enriched = dataJudClient.enrich(precatorio)
                persistirPrecatorio(enriched, credor)

                val score = scoringService.calcular(enriched, request.filtros)
                if (score > 0) persistirLead(prospeccaoId, credor, enriched, score)
            }

            // Enqueue creditor's other processes at next depth
            val outrosProcessos = esajScraper.searchByName(credor.nome)
            outrosProcessos.forEach { queue.add(it to depth + 1) }
        }

        atualizarProgresso(prospeccaoId, visited.size)
        delay(scraperConfig.esaj.delayMs)  // rate-limit
    }

    finalizarProspeccao(prospeccaoId)
}
```

**Depth explosion guard:** A creditor can appear in hundreds of processes. Enforce `maxCredores` check inside the loop: if `leads.size >= maxCredores` break early. Depth is already bounded by `profundidadeMax`.

**Partial-result safety:** `persistirLead` commits after each lead. If the JVM crashes mid-run, the partial data is queryable. The `status=EM_ANDAMENTO` record tells the client the run is not yet final.

---

## Retry and Backoff Pattern

### Recommendation: Resilience4j Retry (not Spring Retry, not manual loops)

Resilience4j integrates cleanly with Spring Boot 3.x, supports exponential backoff with jitter, and is testable without Spring context overhead.

```yaml
resilience4j:
  retry:
    instances:
      scraper:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2.0
        retryExceptions:
          - java.io.IOException
          - br.com.precatorios.exception.ScrapingException
        ignoreExceptions:
          - br.com.precatorios.exception.ProcessoNaoEncontradoException
```

For HTTP 429 detection (rate limit):
```kotlin
@Retry(name = "scraper")
fun fetchProcess(numero: String): ProcessoDto {
    val response = httpClient.get(url)
    if (response.statusCode() == 429) {
        log.warn("Rate limited by TJ-SP, sleeping 60s")
        Thread.sleep(60_000)
        throw ScrapingException("Rate limited — retry")
    }
    return parseResponse(response)
}
```

---

## Repository Layer Pattern

### Spring Data JPA with Kotlin — key rules

**Use `JpaRepository<Entity, Long>` directly.** Do not wrap repositories in another repository-pattern layer. Spring Data JPA is the repository pattern.

**Entity design for Kotlin requires:**
1. All JPA entities need a no-arg constructor — use the `kotlin-jpa` plugin (already in SPEC's `build.gradle.kts`).
2. Mark entity classes `open` or use the `kotlin-allopen` plugin.
3. Use `var` (not `val`) for mutable fields; use nullable types (`Long?`) for generated IDs.

```kotlin
@Entity
@Table(name = "processos")
class Processo(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(unique = true, nullable = false)
    var numero: String,

    // ... other fields

    @OneToMany(mappedBy = "processo", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var credores: MutableList<Credor> = mutableListOf()
)
```

**Avoid N+1 queries:** The lead listing endpoint (`GET /api/v1/leads`) joins across `leads → precatorios → credores`. Use `@Query` with `JOIN FETCH` or a projection DTO to avoid N+1:

```kotlin
@Query("""
    SELECT l FROM Lead l
    JOIN FETCH l.credor c
    JOIN FETCH l.precatorio p
    WHERE (:scoreMinimo IS NULL OR l.score >= :scoreMinimo)
    AND (:statusContato IS NULL OR l.statusContato = :statusContato)
""")
fun findLeadsFiltered(
    @Param("scoreMinimo") scoreMinimo: Int?,
    @Param("statusContato") statusContato: StatusContato?,
    pageable: Pageable
): Page<Lead>
```

**Transaction boundaries:** `ProspeccaoService.iniciar()` uses `@Transactional` only for the INSERT into `prospeccoes`. The BFS `executarAsync()` runs outside that transaction (different thread). Each `persistir*` call inside BFS should use `@Transactional(propagation = REQUIRES_NEW)` so failures on individual items do not roll back the entire run.

---

## Cache Layer Design

### Caffeine configuration — two named caches

The SPEC specifies 24h TTL. Define two named caches: one for e-SAJ processes, one for CAC/SCP precatórios. Different size limits because process pages are larger.

```kotlin
@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(): CaffeineCacheManager {
        return CaffeineCacheManager().apply {
            setCacheNames(listOf("processos", "precatorios", "datajud"))
            setCaffeineSpec("maximumSize=1000,expireAfterWrite=24h")
        }
    }
}
```

Apply at the scraper method level:

```kotlin
@Cacheable(value = ["processos"], key = "#numero")
fun fetchProcess(numero: String): ProcessoDto { ... }

@Cacheable(value = ["precatorios"], key = "#numero")
fun fetchPrecatorio(numero: String): PrecatorioDto { ... }
```

**Cache eviction is not needed in v1** — 24h TTL is the eviction. A manual `@CacheEvict` endpoint can be added later if FUNPREC needs to force a refresh.

---

## Event-Driven vs Polling for Job Status

**Decision: Polling. No events in v1.**

**Rationale:**
- FUNPREC is an internal tool with no frontend requirement in v1 — a human or system making periodic GET requests is sufficient.
- Event-driven approaches (SSE, WebSocket, or message queues) add infrastructure complexity (either a WebSocket upgrade or a broker like Redis Pub/Sub).
- The DB table `prospeccoes` is already the job status store. A simple `SELECT` is the polling mechanism — zero additional infrastructure.
- Polling interval recommendation to expose to callers: poll every 5–10 seconds; SPEC says runs take ~180 seconds, so 20–30 polls per run is negligible.

If real-time push is needed in v2, SSE via `SseEmitter` is the lowest-friction upgrade path — no broker required.

---

## Scalability Considerations

| Concern | Current (v1 single instance) | If scaling is needed |
|---------|------------------------------|----------------------|
| Concurrent scraping jobs | 4 threads max (rate limit safe) | Keep at 4; add more instances only if needed |
| In-memory cache consistency | Single JVM — always consistent | Multi-instance requires Redis; out of scope |
| DB connections | HikariCP default (10 connections) matches 4 scraping threads | Increase with replicas |
| Job handoff on restart | Job in-progress → marked EM_ANDAMENTO forever | Add startup recovery: reset stale EM_ANDAMENTO jobs to ERRO |

---

## Suggested Build Order (Phase Dependencies)

```
Phase 1: Foundation
  - Gradle + Kotlin + Spring Boot skeleton
  - PostgreSQL + Flyway migration (V1__create_tables.sql)
  - JPA entities + repositories (all 5 tables)
  - Docker Compose
  Enables: everything else can be tested with a real DB

Phase 2: Scraper Layer (no service logic yet)
  - EsajScraper (Jsoup, rate-limit, retry)
  - CacScraper (ViewState session cycle)
  - DataJudClient (WebClient)
  - Caffeine cache wiring
  Reason: scrapers are the highest-risk components (external HTML structure unknown);
          isolate and validate before building on top

Phase 3: Service + BFS Engine
  - ScoringService (pure, fully testable)
  - ProspeccaoService + BfsProspeccaoEngine (@Async + BFS)
  - AsyncConfig (thread pool)
  Depends on: Phase 1 (repositories) + Phase 2 (scrapers)

Phase 4: Controllers + API
  - All REST controllers
  - DTOs + mapper functions
  - GlobalExceptionHandler
  - SpringDoc / Swagger
  Depends on: Phase 3 (services)

Phase 5: Hardening
  - Resilience4j retry config
  - Integration tests (Testcontainers)
  - CSS selector calibration against live TJ-SP HTML
  - Startup recovery for stale EM_ANDAMENTO jobs
```

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Synchronous prospection endpoint
**What:** Block the HTTP thread during the entire BFS run.
**Why bad:** BFS run takes 2–5 minutes. HTTP clients (Nginx, load balancers, API gateways) time out at 30–60 seconds.
**Instead:** Always dispatch async, return 202 + job ID, poll for results.

### Anti-Pattern 2: Shared visited set across concurrent jobs
**What:** Using a single static `visitedProcessos` set for all running prospection jobs.
**Why bad:** Cross-contamination between runs; earlier run's visited nodes suppress nodes in later run.
**Instead:** Visited set is local to each `executarAsync()` invocation.

### Anti-Pattern 3: Transactional BFS wrapper
**What:** Wrapping the entire BFS loop in a single `@Transactional` method.
**Why bad:** Holds a DB connection open for the entire 2–5 minute run; connection pool exhaustion.
**Instead:** Short `@Transactional(REQUIRES_NEW)` per persist operation inside BFS.

### Anti-Pattern 4: Fetching full entities for lead listing
**What:** Calling `leadRepository.findAll()` and lazily loading credores/precatorios.
**Why bad:** N+1 query explosion on the `GET /api/v1/leads` endpoint.
**Instead:** `JOIN FETCH` projection query as shown in repository section.

### Anti-Pattern 5: Hardcoding CSS selectors across scraper methods
**What:** Inline string literals like `"#tablePartesPrincipais"` scattered through 10+ methods.
**Why bad:** When TJ-SP updates HTML structure, finding all affected locations is manual and error-prone.
**Instead:** Centralize all selectors in a `EsajSelectors` object or constants file. The SPEC explicitly calls this out.

### Anti-Pattern 6: Re-using Jsoup `Connection` state across CAC/SCP requests without a cookie store
**What:** Creating a new `Jsoup.connect()` per POST without threading cookies from the GET.
**Why bad:** ASP.NET session is tied to the `ASP.NET_SessionId` cookie. Without it, ViewState is rejected (HTTP 500 or redirect to start page).
**Instead:** Use `Jsoup.newSession()` (introduced in Jsoup 1.14.x) to get a `Connection.Session` that persists cookies automatically.

---

## Sources

- Spring Boot Task Execution docs: https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html
- Spring Boot @Async guide (Baeldung): https://www.baeldung.com/spring-async
- ThreadPoolTaskExecutor sizing (Trendyol Tech): https://medium.com/trendyol-tech/spring-boot-async-executor-management-with-threadpooltaskexecutor-f493903617d
- Kotlin Coroutines + Spring Boot (Baeldung): https://www.baeldung.com/kotlin/spring-boot-kotlin-coroutines
- Virtual Threads in Spring Boot 3.2 (Spring blog): https://spring.io/blog/2023/09/09/all-together-now-spring-boot-3-2-graalvm-native-images-java-21-and-virtual/
- Resilience4j Retry + Exponential Backoff (Baeldung): https://www.baeldung.com/resilience4j-backoff-jitter
- Spring Data JPA + Kotlin (JetBrains blog): https://blog.jetbrains.com/idea/2026/03/using-spring-data-jpa-with-kotlin/
- JPA + Kotlin best practices (Baeldung): https://www.baeldung.com/kotlin/jpa
- Spring Boot Caffeine cache (Baeldung): https://www.baeldung.com/spring-boot-caffeine-cache
- ASP.NET ViewState scraping approach (Trickster Dev): https://www.trickster.dev/post/scraping-legacy-asp-net-site-with-scrapy-a-real-example/
- IO-bound thread pool sizing (Zalando Engineering): https://engineering.zalando.com/posts/2019/04/how-to-set-an-ideal-thread-pool-size.html
- Multithreaded BFS crawler pattern (Hello Interview): https://www.hellointerview.com/community/questions/multithreaded-web-crawler/cmbsl2nhd005107adsfu8ohme
