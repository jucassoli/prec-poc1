# Phase 1: Foundation — Research

**Researched:** 2026-04-03
**Domain:** Kotlin/Spring Boot 3.5 project scaffolding — Gradle, Flyway, JPA entities, Docker Compose, async infrastructure
**Confidence:** HIGH (all versions registry-verified; patterns from official Spring/Testcontainers docs)

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INFRA-01 | Project builds and runs with `./gradlew bootRun` on JVM 21 | Gradle Kotlin DSL setup with Spring Boot 3.5.3, Kotlin 2.2.0, JVM 21 toolchain |
| INFRA-02 | Docker Compose starts full stack (API + PostgreSQL) with single `docker-compose up` | Docker Compose `service_healthy` pattern with `pg_isready` healthcheck |
| INFRA-03 | Flyway applies all DB migrations automatically on startup | Flyway 11.x with `flyway-core` + `flyway-database-postgresql` (separate artifact required since Flyway 10+) |
| INFRA-04 | Swagger UI accessible at `/swagger-ui.html` with all endpoints documented | SpringDoc OpenAPI 2.8.6 for Spring Boot 3.x |
| INFRA-05 | All scraper configuration externalized to `application.yml`, overridable via env vars | Standard Spring Boot `@ConfigurationProperties` pattern |
| PERS-01 | Processes persisted in `processos` table with deduplication by `numero` | JPA `@Column(unique=true)` + `@Table` + `kotlin-jpa` plugin for no-arg constructor |
| PERS-02 | Creditors persisted in `credores` table with deduplication by `(nome, processo_id)` | JPA `@UniqueConstraint` on composite key |
| PERS-03 | Precatórios persisted in `precatorios` table linked to creditors | JPA `@ManyToOne` with `FetchType.LAZY` |
| PERS-04 | Prospection runs tracked in `prospeccoes` table with status, counters, timing | JPA entity with `@Enumerated(EnumType.STRING)` for status |
| PERS-05 | Leads persisted in `leads` table linking prospection, creditor, precatório, and score | JPA `@ManyToOne` to three parent entities |
| PERS-06 | Raw HTML/JSON payloads stored in `dados_brutos JSONB` on `processos` and `precatorios` | Hibernate 6 `@JdbcTypeCode(SqlTypes.JSON)` annotation for JSONB |
</phase_requirements>

---

## Summary

Phase 1 delivers the project skeleton that every subsequent phase builds on: a working Gradle build, a PostgreSQL schema managed by Flyway, all five JPA entities with repository tests against a real database, Docker Compose for single-command startup, and the async executor and OpenAPI config stubs. There is no scraping, no business logic, and no REST endpoints beyond Actuator in this phase — the goal is a solid infrastructure baseline that passes its own integration tests before a single line of scraping code is written.

The technology choices are entirely standard Spring Boot patterns. All non-obvious areas (Flyway's modular PostgreSQL driver, Testcontainers 2.0 coordinate changes, Kotlin JPA entity requirements, `@Async` bean-separation for proxy compliance) have well-documented solutions confirmed against current official sources. The main practical risk in this phase is version drift: the prior research documents (STACK.md) contain several version numbers that do not match what is currently published on Maven Central as of 2026-04-03. The corrected, registry-verified versions are documented in the Standard Stack section below.

The async execution decision is locked: use `@Async` with a named `ThreadPoolTaskExecutor`, not Kotlin coroutines, for the BFS prospection job. This is a deliberate project decision documented in STATE.md and ARCHITECTURE.md. `BfsProspeccaoEngine` must be a separate Spring bean (not `ProspeccaoService`) to avoid the Spring AOP self-invocation trap. The `@Async` annotation goes on `BfsProspeccaoEngine.executarAsync()`.

**Primary recommendation:** Build in plan order (01-01 → 01-02 → 01-03 → 01-04). Each plan is a prerequisite for the next. Plan 01-03 is the most complex (five entities, five repositories, Testcontainers test) and the most likely to surface Kotlin JPA subtleties. Plan 01-04 is intentionally a skeleton — no endpoint logic, just the beans that later phases will fill in.

---

## Project Constraints (from CLAUDE.md)

CLAUDE.md contains no actionable directives beyond a placeholder comment. No project-specific constraints override standard research findings.

---

## Standard Stack

### Version Correction Notice

The prior research documents (`.planning/research/STACK.md`) contain several version numbers that are incorrect relative to what is published on Maven Central as of 2026-04-03. The table below shows registry-verified current versions. **Use the "Verified Version" column in `build.gradle.kts`.**

| Library | STACK.md Claim | Verified Version | Source |
|---------|---------------|------------------|--------|
| Spring Boot | 3.5.9 | **3.5.3** | Maven Central `spring-boot-starter-parent` |
| Kotlin | 2.3.20 | **2.2.0** | Maven Central `kotlin-gradle-plugin`; Spring Boot 3.5.3 BOM manages 1.9.25 — override to 2.2.0 explicitly |
| jsoup | 1.22.1 | **1.21.1** | Maven Central `jsoup` |
| MockK | 1.14.5 | **1.14.3** | Maven Central `mockk-jvm` |
| SpringDoc OpenAPI | 2.8.16 | **2.8.6** | Maven Central `springdoc-openapi-starter-webmvc-ui` |
| Testcontainers | 2.0.4 | **2.0.4** | GitHub releases — 2.0.4 is correct (confirmed) |
| Flyway | managed | **11.8.2** (latest); Spring Boot BOM manages 11.7.x | Maven Central `flyway-database-postgresql` |

**Spring Boot BOM caveat:** Spring Boot 3.5.3's BOM manages Testcontainers at 1.21.2. To use TC 2.0.4 with its new artifact IDs, pin TC version explicitly in `build.gradle.kts` (see Code Examples section).

### Core Dependencies

| Library | Verified Version | Purpose | Notes |
|---------|-----------------|---------|-------|
| `org.springframework.boot` plugin | 3.5.3 | Framework BOM + Spring Boot | Drives all managed versions |
| `io.spring.dependency-management` plugin | 1.1.7 | Imports Spring Boot BOM | Latest as of 2026-04-03 |
| `kotlin("jvm")` plugin | 2.2.0 | Kotlin compiler | Override BOM's 1.9.25 by setting plugin version |
| `kotlin("plugin.spring")` | 2.2.0 | Auto-opens Spring-annotated classes | Required — Kotlin classes are `final` by default |
| `kotlin("plugin.jpa")` | 2.2.0 | Generates no-arg constructors on JPA entities | Required — JPA needs no-arg constructor |
| `spring-boot-starter-web` | via BOM | Tomcat + Spring MVC | HTTP layer |
| `spring-boot-starter-data-jpa` | via BOM | Hibernate 6.6.x + Spring Data JPA | ORM layer |
| `spring-boot-starter-validation` | via BOM | Bean Validation / Jakarta constraints | DTO validation |
| `spring-boot-starter-cache` | via BOM | Spring Cache abstraction | Required to enable `@Cacheable` |
| `spring-boot-starter-actuator` | via BOM | `/actuator/health` endpoint | Docker Compose healthcheck target |
| `spring-boot-starter-webflux` | via BOM | WebClient (DataJud HTTP client) | Pulls in Reactor; needed for Phase 2 |
| `jackson-module-kotlin` | via BOM | Kotlin data class JSON deserialization | Required for Spring MVC + Kotlin |
| `kotlin-reflect` | via BOM | Kotlin reflection support | Required by Spring |
| `kotlinx-coroutines-core` | via BOM | Coroutines (used by WebFlux bridge) | BOM manages version via coroutines-bom |
| `kotlinx-coroutines-reactor` | via BOM | WebFlux coroutines bridge | Needed for suspend WebClient calls |

### Database + Migration

| Library | Verified Version | Purpose | Notes |
|---------|-----------------|---------|-------|
| `org.postgresql:postgresql` | via BOM (runtime) | PostgreSQL JDBC driver | `runtimeOnly` |
| `org.flywaydb:flyway-core` | via BOM (11.7.x) | Flyway migration engine | Both artifacts required |
| `org.flywaydb:flyway-database-postgresql` | via BOM (11.7.x) | Flyway PostgreSQL dialect | **Separate artifact required since Flyway 10+** |
| `com.github.ben-manes.caffeine:caffeine` | via BOM | Caffeine in-memory cache | Phase 3 will configure; Phase 1 adds as dependency |

### API Docs

| Library | Verified Version | Purpose | Notes |
|---------|-----------------|---------|-------|
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | **2.8.6** | Swagger UI at `/swagger-ui.html` | 2.x = Spring Boot 3.x; DO NOT use 3.x (that targets Spring Boot 4.x) |

### Testing

| Library | Verified Version | Purpose | Notes |
|---------|-----------------|---------|-------|
| `spring-boot-starter-test` | via BOM | JUnit 5.12, Mockito, AssertJ | BOM-managed |
| `io.mockk:mockk` | **1.14.3** | Kotlin-first mocking | Use `coEvery`/`coVerify` for suspend functions |
| `org.testcontainers:testcontainers-postgresql` | **2.0.4** | PostgreSQL container for tests | New artifact ID in TC 2.0 (was `postgresql`) |
| `org.testcontainers:testcontainers-junit-jupiter` | **2.0.4** | JUnit 5 TC extensions | New artifact ID in TC 2.0 (was `junit-jupiter`) |
| `spring-boot-testcontainers` | via BOM | `@ServiceConnection` support | Required for zero-boilerplate DataSource wiring |

### Installation (build.gradle.kts)

```kotlin
plugins {
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    kotlin("plugin.jpa") version "2.2.0"
}

group = "br.com.precatorios"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Pin Testcontainers to 2.0.4 — Spring Boot 3.5.3 BOM manages 1.21.2
ext["testcontainers.version"] = "2.0.4"

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")   // Required separately since Flyway 10+

    // Cache (configured in Phase 3, dependency added now)
    implementation("com.github.ben-manes.caffeine:caffeine")

    // API Docs — 2.x for Spring Boot 3.x (NOT 3.x which targets Spring Boot 4)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.14.3")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.4")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.4")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
}
```

---

## Architecture Patterns

### Recommended Project Structure

```
src/
├── main/
│   ├── kotlin/br/com/precatorios/
│   │   ├── PrectatoriosApplication.kt     # @SpringBootApplication entry point
│   │   ├── config/
│   │   │   ├── AsyncConfig.kt             # @EnableAsync + prospeccaoExecutor bean
│   │   │   ├── CacheConfig.kt             # @EnableCaching + Caffeine named caches (stub)
│   │   │   └── OpenApiConfig.kt           # SpringDoc API info + contact details
│   │   ├── domain/
│   │   │   ├── Processo.kt                # JPA entity
│   │   │   ├── Credor.kt                  # JPA entity
│   │   │   ├── Precatorio.kt              # JPA entity
│   │   │   ├── Prospeccao.kt              # JPA entity
│   │   │   ├── Lead.kt                    # JPA entity
│   │   │   └── enums/
│   │   │       ├── StatusProspeccao.kt    # EM_ANDAMENTO, CONCLUIDA, ERRO
│   │   │       └── StatusContato.kt       # Lead contact status enum
│   │   ├── repository/
│   │   │   ├── ProcessoRepository.kt
│   │   │   ├── CredorRepository.kt
│   │   │   ├── PrecatorioRepository.kt
│   │   │   ├── ProspeccaoRepository.kt
│   │   │   └── LeadRepository.kt
│   │   └── exception/
│   │       └── GlobalExceptionHandler.kt  # @ControllerAdvice skeleton
│   └── resources/
│       ├── application.yml
│       └── db/migration/
│           └── V1__create_tables.sql
└── test/
    └── kotlin/br/com/precatorios/
        └── repository/
            └── RepositoryIntegrationTest.kt  # Testcontainers + @ServiceConnection
```

### Pattern 1: Kotlin JPA Entity with `kotlin-jpa` Plugin

The `kotlin-jpa` compiler plugin generates a no-arg constructor for classes annotated with `@Entity`, `@MappedSuperclass`, and `@Embeddable`. The `kotlin-spring` plugin opens classes annotated with `@Component`, `@Service`, `@Repository`, `@Controller`, `@Configuration`.

Without these plugins, JPA throws `InstantiationException` at runtime and Spring cannot create proxies.

**Key rules:**
- Use `var` for mutable entity fields (not `val`) — Hibernate must be able to mutate them
- Use nullable type (`Long?`) for the generated `@Id` field so the entity can be created without an ID
- Relationships use `MutableList<T>` (not `List<T>`) and `FetchType.LAZY` by default
- For JSONB columns, use `@JdbcTypeCode(SqlTypes.JSON)` — Hibernate 6 understands this natively

```kotlin
// Source: Spring Data JPA docs + JetBrains blog on Spring Data JPA with Kotlin
@Entity
@Table(name = "processos")
class Processo(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "numero", unique = true, nullable = false)
    var numero: String = "",

    @Column(name = "dados_brutos", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var dadosBrutos: String? = null,

    @OneToMany(mappedBy = "processo", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var credores: MutableList<Credor> = mutableListOf(),

    @Column(name = "criado_em")
    var criadoEm: LocalDateTime = LocalDateTime.now()
)
```

### Pattern 2: Testcontainers 2.0 with `@ServiceConnection`

`@ServiceConnection` (Spring Boot 3.1+) auto-configures the DataSource from the container — no manual `spring.datasource.url` override required.

**Breaking changes from TC 1.x to 2.0:**

| Change | TC 1.x (old) | TC 2.0 (new) |
|--------|-------------|-------------|
| Artifact ID (PostgreSQL) | `postgresql` | `testcontainers-postgresql` |
| Artifact ID (JUnit 5) | `junit-jupiter` | `testcontainers-junit-jupiter` |
| Package (PostgreSQLContainer) | `org.testcontainers.containers.PostgreSQLContainer` | `org.testcontainers.postgresql.PostgreSQLContainer` |
| JUnit 4 support | included | removed |

```kotlin
// Source: Testcontainers 2.0 migration + Spring Boot testcontainers docs
@SpringBootTest
@Testcontainers
class RepositoryIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }

    @Autowired
    lateinit var processoRepository: ProcessoRepository

    @Test
    fun `should persist and retrieve Processo`() {
        val processo = Processo(numero = "1234567-89.2020.8.26.0053")
        val saved = processoRepository.save(processo)

        assertThat(saved.id).isNotNull()
        assertThat(processoRepository.findByNumero(saved.numero)).isNotNull()
    }
}
```

### Pattern 3: `@Async` with Named Executor — Correct Bean Separation

The `@Async` annotation must be placed on a method in a **different bean** from the caller. If `ProspeccaoService` calls an `@Async` method on itself, Spring AOP cannot intercept it (self-invocation bypasses the proxy), and the method runs synchronously on the HTTP thread.

**Correct structure (required by locked decision in STATE.md):**

```kotlin
// Source: Spring @Async docs + Baeldung Spring Async guide
@Configuration
@EnableAsync
class AsyncConfig {

    @Bean("prospeccaoExecutor")
    fun prospeccaoExecutor(): ThreadPoolTaskExecutor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 4
            queueCapacity = 20
            setThreadNamePrefix("prospeccao-")
            setWaitForTasksToCompleteOnShutdown(true)
            awaitTerminationSeconds = 120
            initialize()
        }
    }
}

// CORRECT: @Async in a SEPARATE bean
@Component
class BfsProspeccaoEngine {
    @Async("prospeccaoExecutor")
    fun executarAsync(prospeccaoId: Long) {
        // BFS runs here — on prospeccao-N thread
    }
}

// ProspeccaoService calls BfsProspeccaoEngine, never calls itself
@Service
class ProspeccaoService(
    private val bfsEngine: BfsProspeccaoEngine,
    private val prospeccaoRepository: ProspeccaoRepository
) {
    @Transactional
    fun iniciar(request: ProspeccaoRequest): Long {
        val saved = prospeccaoRepository.save(Prospeccao(/* ... */))
        bfsEngine.executarAsync(saved.id!!)  // returns immediately (async dispatch)
        return saved.id!!
    }
}
```

### Pattern 4: Flyway Migration for JSONB Tables

Flyway with PostgreSQL since Flyway 10+ requires the separate `flyway-database-postgresql` artifact on the classpath. Without it, Flyway throws `FlywayException: No database found to handle jdbc:postgresql://...`.

```sql
-- Source: Standard PostgreSQL DDL patterns + Flyway docs
-- V1__create_tables.sql

CREATE TABLE processos (
    id           BIGSERIAL PRIMARY KEY,
    numero       VARCHAR(50) NOT NULL UNIQUE,
    dados_brutos JSONB,
    criado_em    TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE credores (
    id          BIGSERIAL PRIMARY KEY,
    processo_id BIGINT NOT NULL REFERENCES processos(id),
    nome        VARCHAR(255) NOT NULL,
    cpf_cnpj    VARCHAR(20),
    tipo_parte  VARCHAR(50),
    criado_em   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (nome, processo_id)
);

CREATE TABLE precatorios (
    id              BIGSERIAL PRIMARY KEY,
    credor_id       BIGINT NOT NULL REFERENCES credores(id),
    numero          VARCHAR(50) NOT NULL UNIQUE,
    valor           NUMERIC(18, 2),
    status_pagamento VARCHAR(100),
    entidade_devedora VARCHAR(255),
    natureza        VARCHAR(100),
    posicao_cronologica INTEGER,
    dados_brutos    JSONB,
    criado_em       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE prospeccoes (
    id                  BIGSERIAL PRIMARY KEY,
    processo_semente    VARCHAR(50) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'EM_ANDAMENTO',
    processos_visitados INTEGER NOT NULL DEFAULT 0,
    credores_encontrados INTEGER NOT NULL DEFAULT 0,
    leads_qualificados  INTEGER NOT NULL DEFAULT 0,
    profundidade_max    INTEGER NOT NULL DEFAULT 2,
    max_credores        INTEGER NOT NULL DEFAULT 50,
    erro_mensagem       TEXT,
    data_inicio         TIMESTAMP NOT NULL DEFAULT NOW(),
    data_fim            TIMESTAMP
);

CREATE TABLE leads (
    id              BIGSERIAL PRIMARY KEY,
    prospeccao_id   BIGINT NOT NULL REFERENCES prospeccoes(id),
    credor_id       BIGINT NOT NULL REFERENCES credores(id),
    precatorio_id   BIGINT NOT NULL REFERENCES precatorios(id),
    score           INTEGER NOT NULL DEFAULT 0,
    score_detalhes  JSONB,
    status_contato  VARCHAR(50) NOT NULL DEFAULT 'NAO_CONTATADO',
    observacao      TEXT,
    criado_em       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_leads_prospeccao_id ON leads(prospeccao_id);
CREATE INDEX idx_leads_score ON leads(score DESC);
CREATE INDEX idx_prospeccoes_status ON prospeccoes(status);
```

### Pattern 5: Docker Compose with `service_healthy` Startup Ordering

Without a `depends_on.condition: service_healthy` check, the API container starts before PostgreSQL is ready to accept connections, causing a connection refused error on boot. The `pg_isready` command tests actual connection readiness — a TCP-only check is not sufficient.

```yaml
# Source: Docker Compose startup ordering docs
version: '3.9'

services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: precatorios
      POSTGRES_USER: precatorios
      POSTGRES_PASSWORD: precatorios
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U precatorios -d precatorios"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s

  api:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/precatorios
      SPRING_DATASOURCE_USERNAME: precatorios
      SPRING_DATASOURCE_PASSWORD: precatorios
    depends_on:
      postgres:
        condition: service_healthy
```

### Pattern 6: SpringDoc OpenAPI Configuration

For Spring Boot 3.x, use SpringDoc 2.x (NOT 3.x — that targets Spring Boot 4). The Swagger UI is auto-configured at `/swagger-ui.html` with no additional code required, but a configuration bean provides project metadata.

```kotlin
// Source: springdoc.org official docs
@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Precatórios API")
                .description("TJ-SP precatório lead prospecting API")
                .version("1.0.0")
        )
}
```

```yaml
# application.yml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
```

### Anti-Patterns to Avoid

- **`@Async` self-invocation:** Do NOT put `@Async` on a method in `ProspeccaoService` and call it from `ProspeccaoService.iniciar()`. Spring AOP proxy cannot intercept self-calls — the method runs synchronously. `@Async` belongs on `BfsProspeccaoEngine.executarAsync()`.

- **Single `@Transactional` wrapping BFS loop:** Do NOT annotate `BfsProspeccaoEngine.executarAsync()` with `@Transactional`. This would hold a database connection for the entire 2–5 minute BFS run, exhausting the HikariCP pool. Each individual persist call inside BFS must use `@Transactional(propagation = REQUIRES_NEW)`.

- **Modifying Flyway migrations after initial commit:** Flyway checksums applied migrations. Editing `V1__create_tables.sql` after it has been applied causes `FlywayException: Validate failed` on the next startup. All schema changes must be new versioned migration files.

- **Using TC 1.x artifact IDs with TC 2.0 JAR:** If the version is pinned to 2.0.x but the artifact ID is the old `org.testcontainers:postgresql`, the JAR will not be found. Use `testcontainers-postgresql` and `testcontainers-junit-jupiter` for TC 2.0.

- **Using SpringDoc 3.x with Spring Boot 3.x:** SpringDoc 3.x is for Spring Boot 4.x only. The import paths changed. Using 3.x with Spring Boot 3.5 causes `ClassNotFoundException` at startup.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| No-arg constructor on Kotlin JPA entities | Manual secondary constructor | `kotlin("plugin.jpa")` compiler plugin | Plugin handles all `@Entity`, `@Embeddable`, `@MappedSuperclass` classes at compile time |
| Open classes for Spring proxies | `open` keyword on every bean class | `kotlin("plugin.spring")` compiler plugin | Plugin auto-opens all Spring-annotated classes |
| PostgreSQL container lifecycle in tests | Manual `@BeforeAll`/`@AfterAll` | `@Testcontainers` + `@Container` + `@ServiceConnection` | TC JUnit 5 extension manages container lifecycle; `@ServiceConnection` auto-wires DataSource |
| Migration checksums for JSONB | Custom DDL validation | Flyway + standard PostgreSQL DDL | Flyway handles checksums; PostgreSQL JSONB is native |
| Async thread pool configuration | `Thread.sleep` in scraper, `CompletableFuture.supplyAsync` | `ThreadPoolTaskExecutor` bean + `@Async` | Named executor gives explicit control; Spring manages lifecycle and graceful shutdown |
| DataSource configuration in tests | Manual `@DynamicPropertySource` | `@ServiceConnection` (Spring Boot 3.1+) | Zero-boilerplate — TC auto-publishes host/port to Spring's environment |

**Key insight:** The `kotlin-jpa` and `kotlin-spring` compiler plugins exist precisely because JPA and Spring require runtime behaviors (no-arg constructors, open classes) that are incompatible with Kotlin's default behavior (all-arg constructors, all-final classes). These plugins are not optional.

---

## Common Pitfalls

### Pitfall 1: Flyway `FlywayException: No database found to handle jdbc:postgresql://`
**What goes wrong:** Application starts, Flyway fails immediately with "No database found" despite the JDBC URL being valid.
**Why it happens:** Since Flyway 10, the PostgreSQL dialect is in a separate artifact (`flyway-database-postgresql`). Without it on the classpath, Flyway's database detection mechanism finds no handler.
**How to avoid:** Always include BOTH `flyway-core` AND `flyway-database-postgresql` in `build.gradle.kts`. Spring Boot BOM manages both versions — no manual version pinning needed.
**Warning signs:** `FlywayException` at startup, `ClassNotFoundException: org.flywaydb.database.postgresql.PostgreSQLDatabase`.

### Pitfall 2: Testcontainers 2.0 `NoSuchMethodError` or `ClassNotFoundException`
**What goes wrong:** Test compilation succeeds but runtime throws `ClassNotFoundException: org.testcontainers.containers.PostgreSQLContainer` or similar.
**Why it happens:** TC 2.0 moved `PostgreSQLContainer` to package `org.testcontainers.postgresql`. Code using the old `org.testcontainers.containers.PostgreSQLContainer` import compiles against the 1.x API but fails at runtime with 2.0.4 JARs — or the old artifact ID `postgresql` is not found at all in TC 2.0.
**How to avoid:** Use artifact `testcontainers-postgresql:2.0.4` and import `org.testcontainers.postgresql.PostgreSQLContainer`. Also pin `ext["testcontainers.version"] = "2.0.4"` in `build.gradle.kts` since Spring Boot 3.5.3 BOM manages 1.21.2 by default.
**Warning signs:** `ClassNotFoundException` in test runtime, not compile-time.

### Pitfall 3: `@Async` method runs synchronously (Spring AOP self-invocation)
**What goes wrong:** `POST /api/v1/prospeccao` blocks for 3+ minutes instead of returning 202 immediately.
**Why it happens:** Calling `this.someAsyncMethod()` from within the same Spring bean bypasses the Spring AOP proxy. The `@Async` annotation is ignored and the method runs on the caller's thread.
**How to avoid:** Place `@Async("prospeccaoExecutor")` on `BfsProspeccaoEngine.executarAsync()` — a different bean from the one that calls it (`ProspeccaoService`). Verify with a test: `POST /prospeccao` with a mock scraper that sleeps for 5 seconds must return 202 in less than 500ms.
**Warning signs:** HTTP thread blocked, response takes the full BFS duration.

### Pitfall 4: Hibernate fails to instantiate Kotlin entity (no no-arg constructor)
**What goes wrong:** `org.hibernate.InstantiationException: No default constructor for entity: Processo`.
**Why it happens:** Kotlin generates only the all-args constructor by default. JPA/Hibernate requires a public or protected no-arg constructor to instantiate entities.
**How to avoid:** Include `kotlin("plugin.jpa")` in the `plugins` block. The plugin generates synthetic no-arg constructors for `@Entity`, `@Embeddable`, and `@MappedSuperclass` classes. Verify with a Spring context test.
**Warning signs:** `InstantiationException` mentioning entity class names.

### Pitfall 5: Flyway checksum failure on migration edit
**What goes wrong:** After editing `V1__create_tables.sql` to fix a typo, the application refuses to start: `FlywayException: Validate failed: Checksum mismatch for migration version 1`.
**Why it happens:** Flyway records a checksum of each applied migration. Any modification to an applied migration file causes the checksum to not match.
**How to avoid:** Treat all applied migrations as immutable. Column additions, index changes, etc. must be new files: `V2__add_index_leads.sql`. In development, `flyway.clean-on-validation-error=true` can be set (do NOT use in production) to allow iterative schema development.
**Warning signs:** `Checksum mismatch` in logs.

### Pitfall 6: Docker Compose API starts before PostgreSQL is ready
**What goes wrong:** API container crashes with `Connection refused` on startup when PostgreSQL is still initializing.
**Why it happens:** `depends_on` without `condition: service_healthy` only waits for the container to start, not for PostgreSQL to accept connections.
**How to avoid:** Use `depends_on.condition: service_healthy` with a `pg_isready` healthcheck (not a TCP check). Set `start_period: 10s` to allow PostgreSQL the initial startup window before healthchecks begin counting failures.
**Warning signs:** Application crashes on first `docker-compose up` but succeeds on second (race condition signature).

### Pitfall 7: JSONB column loses type info — stored as plain String
**What goes wrong:** Entity compiles and saves fine but JSONB column is treated as VARCHAR, causing PostgreSQL type errors on INSERT or silent data corruption.
**Why it happens:** Without `@JdbcTypeCode(SqlTypes.JSON)`, Hibernate maps `String` fields to VARCHAR. The JDBC driver cannot cast VARCHAR to JSONB implicitly in all configurations.
**How to avoid:** Annotate JSONB fields with `@JdbcTypeCode(SqlTypes.JSON)` from `org.hibernate.annotations`. Also add `columnDefinition = "jsonb"` to `@Column` for schema generation consistency (even though Flyway manages the schema).
**Warning signs:** `PSQLException: column "dados_brutos" is of type jsonb but expression is of type character varying`.

---

## Code Examples

### Minimal `application.yml` for Phase 1

```yaml
# Source: Spring Boot reference docs
spring:
  application:
    name: precatorios-api
  datasource:
    url: jdbc:postgresql://localhost:5432/precatorios
    username: precatorios
    password: precatorios
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate              # Flyway manages schema; validate only
    show-sql: false
    open-in-view: false               # Avoid OSIV anti-pattern
    properties:
      hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html

management:
  endpoints:
    web:
      exposure:
        include: health
```

### `GlobalExceptionHandler` Skeleton (Phase 1 stub, filled in Phase 5)

```kotlin
// Source: Spring @ControllerAdvice docs
@ControllerAdvice
class GlobalExceptionHandler {

    data class ErrorResponse(
        val status: Int,
        val message: String,
        val timestamp: LocalDateTime = LocalDateTime.now()
    )

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(500, "Internal server error"))
    }
}
```

### `CacheConfig` Skeleton (Phase 1 stub, configured in Phase 3)

```kotlin
// Source: Spring Boot Caffeine cache docs
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

### Deduplication Query (PERS-01 and PERS-02)

```kotlin
// ProcessoRepository — findByNumero for deduplication check before save
interface ProcessoRepository : JpaRepository<Processo, Long> {
    fun findByNumero(numero: String): Processo?
    fun existsByNumero(numero: String): Boolean
}

// CredorRepository — existsByNomeAndProcessoId for deduplication
interface CredorRepository : JpaRepository<Credor, Long> {
    fun existsByNomeAndProcessoId(nome: String, processoId: Long): Boolean
    fun findByNomeAndProcessoId(nome: String, processoId: Long): Credor?
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact for Phase 1 |
|--------------|------------------|--------------|---------------------|
| `org.testcontainers:postgresql:1.x` | `org.testcontainers:testcontainers-postgresql:2.0.x` | TC 2.0 (Oct 2024) | Must use new artifact ID and import path |
| `import org.testcontainers.containers.PostgreSQLContainer` | `import org.testcontainers.postgresql.PostgreSQLContainer` | TC 2.0 (Oct 2024) | Update imports in test files |
| `flyway-core` alone handles PostgreSQL | `flyway-core` + `flyway-database-postgresql` both required | Flyway 10 (2023) | Two dependencies in build.gradle.kts |
| SpringDoc 1.x | SpringDoc 2.x for Spring Boot 3.x | Spring Boot 3.0 (2022) | Use 2.8.6; do NOT use 3.x |
| `@DynamicPropertySource` for Testcontainers DataSource | `@ServiceConnection` auto-wiring | Spring Boot 3.1 (2023) | No manual property override needed in tests |
| Kotlin `@Entity` class always causes `InstantiationException` | `kotlin-jpa` plugin generates no-arg constructor | Spring Boot 3.0 era | Plugin is required in `plugins` block |

**Deprecated/outdated:**
- `net.sourceforge.htmlunit:htmlunit` — replaced by `org.htmlunit:htmlunit` (HtmlUnit 4.x); not needed in Phase 1 but relevant when Phase 2 falls back to HtmlUnit for CAC/SCP
- `spring.jpa.hibernate.ddl-auto: create-drop` — should not be used in any environment; Flyway owns schema; use `validate`
- `@DynamicPropertySource` for Testcontainers — superseded by `@ServiceConnection` (Spring Boot 3.1+); do not use the old pattern

---

## Open Questions

1. **Spring Boot 3.5.x exact Kotlin BOM version**
   - What we know: Spring Boot 3.5.3 BOM manages `kotlin.version=1.9.25`; Kotlin 2.2.0 is current stable and compatible; STACK.md claimed "2.3.20" which resolves to a Spring Boot 4.x milestone target
   - What's unclear: Whether using Kotlin 2.2.0 plugin with Spring Boot 3.5.3's Kotlin 1.9.x BOM causes any runtime issues with coroutines or reflection
   - Recommendation: Explicitly set `kotlin("jvm") version "2.2.0"` in the plugins block; the Kotlin plugin version overrides the BOM for the compiler. Set `extra["kotlin.version"] = "2.2.0"` to also align runtime JARs.

2. **Testcontainers 2.0 `@ServiceConnection` compatibility with Spring Boot 3.5.3**
   - What we know: Spring Boot 3.5.3 BOM manages TC 1.21.2; TC 2.0.4 is available; `@ServiceConnection` was introduced in Spring Boot 3.1
   - What's unclear: Whether overriding to TC 2.0.4 via `ext["testcontainers.version"] = "2.0.4"` works cleanly with `spring-boot-testcontainers` starter
   - Recommendation: Test with a minimal `@SpringBootTest` in Plan 01-03. If `@ServiceConnection` fails with TC 2.0.4, fall back to `@DynamicPropertySource` for Phase 1 and track as technical debt.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JVM 21 | INFRA-01 | Needs verification | — | None (hard requirement) |
| Docker | INFRA-02 | Needs verification | — | Run PostgreSQL locally, skip Docker Compose test |
| Docker Compose | INFRA-02 | Needs verification | — | None if Docker present |
| Gradle wrapper | INFRA-01 | Generated by `gradle wrapper` | N/A | — |
| PostgreSQL (local) | Tests without Docker | Not assumed | — | Testcontainers provides its own |

Note: The Testcontainers tests spin up their own PostgreSQL container — no pre-installed PostgreSQL is needed for running tests. Docker must be available for Testcontainers to work.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) 5.12 via Spring Boot 3.5.3 BOM |
| Config file | None — Spring Boot auto-configures test context |
| Quick run command | `./gradlew test --tests "*.RepositoryIntegrationTest"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INFRA-01 | `./gradlew bootRun` starts without errors on JVM 21 | smoke | `./gradlew bootRun &` then `curl -f http://localhost:8080/actuator/health` | ❌ Wave 0 |
| INFRA-02 | `docker-compose up` brings API and PostgreSQL healthy | smoke | `docker-compose up -d && docker-compose ps` (manual verify) | ❌ Wave 0 |
| INFRA-03 | Flyway applies V1 migration; all five tables exist | integration | `./gradlew test --tests "*.MigrationTest"` | ❌ Wave 0 |
| INFRA-04 | Swagger UI reachable at `/swagger-ui.html` | smoke | `curl -f http://localhost:8080/swagger-ui.html` | ❌ Wave 0 |
| INFRA-05 | Config values readable from `application.yml` + env override | unit | Verify `@ConfigurationProperties` binding | ❌ Wave 0 |
| PERS-01 | Processo persists with unique `numero` constraint | integration | `./gradlew test --tests "*.ProcessoRepositoryTest"` | ❌ Wave 0 |
| PERS-02 | Credor deduplication by `(nome, processo_id)` | integration | `./gradlew test --tests "*.CredorRepositoryTest"` | ❌ Wave 0 |
| PERS-03 | Precatorio linked to Credor via FK | integration | `./gradlew test --tests "*.PrecatorioRepositoryTest"` | ❌ Wave 0 |
| PERS-04 | Prospeccao status persists as string enum | integration | `./gradlew test --tests "*.ProspeccaoRepositoryTest"` | ❌ Wave 0 |
| PERS-05 | Lead links prospeccao + credor + precatorio | integration | `./gradlew test --tests "*.LeadRepositoryTest"` | ❌ Wave 0 |
| PERS-06 | `dados_brutos` JSONB round-trips correctly | integration | `./gradlew test --tests "*.JsonbPersistenceTest"` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "br.com.precatorios.repository.*"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** All tests green + `./gradlew bootRun` shows `/actuator/health` returning `{"status":"UP"}`

### Wave 0 Gaps

All test files are new (greenfield project):

- [ ] `src/test/kotlin/br/com/precatorios/repository/RepositoryIntegrationTest.kt` — Testcontainers base class with `@ServiceConnection`, covers PERS-01 through PERS-06
- [ ] `src/test/kotlin/br/com/precatorios/migration/MigrationTest.kt` — verifies Flyway applies V1 migration, all five tables exist, covers INFRA-03
- [ ] `src/test/kotlin/br/com/precatorios/ApplicationSmokeTest.kt` — Spring context loads without errors, covers INFRA-01 and INFRA-04

*(No missing framework — Spring Boot test autoconfiguration is provided by `spring-boot-starter-test`)*

---

## Sources

### Primary (HIGH confidence)
- Maven Central registry — direct API queries for Spring Boot, Kotlin, jsoup, MockK, SpringDoc, Flyway, Testcontainers versions (2026-04-03)
- GitHub: `testcontainers/testcontainers-java/releases` — TC 2.0.4 release date (March 19, 2025) and breaking changes confirmed
- [Testcontainers 2.0 migration (OpenRewrite)](https://docs.openrewrite.org/recipes/java/testing/testcontainers/testcontainers2migration) — artifact ID and package changes confirmed
- [Spring Boot 3.5 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.5-Release-Notes) — Flyway 11.7 managed, JUnit 5.12 bundled
- Maven Central: `spring-boot-dependencies:3.5.3` — confirmed `kotlin.version=1.9.25`, `testcontainers.version=1.21.2`

### Secondary (MEDIUM confidence)
- `.planning/research/STACK.md` — architecture decisions and patterns (HIGH confidence); version numbers superseded by registry verification
- `.planning/research/ARCHITECTURE.md` — `@Async` patterns, entity design, BFS design (HIGH confidence; decisions locked by STATE.md)

### Tertiary (LOW confidence — note)
- STACK.md version claims for Spring Boot 3.5.9, Kotlin 2.3.20, jsoup 1.22.1, MockK 1.14.5, SpringDoc 2.8.16 are superseded by Maven Central verification. Those specific versions do not currently exist on Maven Central (as of 2026-04-03). They appear to be future projections.

---

## Metadata

**Confidence breakdown:**
- Standard stack (verified versions): HIGH — every version queried directly from Maven Central registry
- Architecture patterns: HIGH — sourced from official Spring, Hibernate, Flyway, Testcontainers documentation
- Pitfalls: HIGH — all sourced from official docs or confirmed library changelog breaking changes
- Open questions: MEDIUM — TC 2.0 + Spring Boot 3.5.3 `@ServiceConnection` compatibility needs runtime validation

**Research date:** 2026-04-03
**Valid until:** 2026-07-03 for stable dependencies (Spring Boot, Flyway, Testcontainers); 2026-05-03 for fast-moving (SpringDoc, MockK)
