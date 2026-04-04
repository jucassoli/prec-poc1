# Especificação Técnica — API de Prospecção de Precatórios (TJ-SP)

## 1. Objetivo

Construir uma API REST em Kotlin/Spring Boot que automatiza a prospecção de credores de precatórios no Tribunal de Justiça de São Paulo. O sistema recebe um processo-semente, descobre outros credores no mesmo processo, analisa os precatórios desses credores, classifica por interesse e retorna uma lista de leads qualificados. Tudo via JSON, sem interface frontend.

## 2. Stack Tecnológica

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Kotlin (JVM 21) |
| Framework | Spring Boot 3.x |
| Build | Gradle (Kotlin DSL) |
| HTTP Client | Spring WebClient (reativo, non-blocking) para chamadas externas |
| HTML Parser | Jsoup (parsing de HTML do e-SAJ) |
| JSON/REST Client | Spring RestClient ou WebClient para API DataJud |
| Banco de dados | PostgreSQL (armazenar credores, processos, resultados de prospecção) |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Cache | Caffeine (in-memory) para evitar re-scraping de processos já visitados |
| Container | Docker + Docker Compose |
| Documentação API | SpringDoc OpenAPI (Swagger UI) |
| Logging | SLF4J + Logback |
| Testes | JUnit 5 + MockK + Testcontainers (PostgreSQL) |

## 3. Fontes de Dados Externas

### 3.1 e-SAJ — Consulta Processual Pública (1º Grau)

- **URL base**: `https://esaj.tjsp.jus.br/cpopg/`
- **Acesso**: Público, sem login
- **Endpoints usados**:
  - Busca: `GET /cpopg/search.do?cbPesquisa={tipo}&dadosConsulta.valorConsulta={valor}&cdForo={foro}`
    - `cbPesquisa` = `NMPART` (nome), `DOCPART` (CPF/CNPJ), `NUMPROC` (número processo)
  - Detalhes: `GET /cpopg/show.do?processo.numero={numero}&processo.foro={foro}`
- **Dados extraídos via Jsoup**:
  - Dados básicos: classe, assunto, foro, vara, juiz, valor da ação
  - Partes: tipo (Reqte/Reqdo/Exequente/Credor), nome, advogado (seletores: `#tablePartesPrincipais`, `#tableTodasPartes`)
  - Incidentes de precatório vinculados (seletores: `#incidentes`, links contendo "Precatório" ou "RPV")
  - Andamentos processuais (seletores: `#tabelaTodasMovimentacoes`)
- **Rate limiting**: delay de 2 segundos entre requests. Usar header User-Agent de browser real.
- **Parsing**: Jsoup com CSS selectors. Tratar variações de estrutura HTML com fallbacks.

### 3.2 Portal de Precatórios TJ-SP (CAC/SCP)

- **URL base**: `https://www.tjsp.jus.br/cac/scp/`
- **Acesso**: Público, sem login
- **Complexidade**: ASP.NET com ViewState — requer manter sessão HTTP e enviar ViewState/EventValidation nos POSTs
- **Endpoints**:
  - Menu: `GET /webmenupesquisa.aspx`
  - Pesquisa de precatórios: `POST /pesquisainternetv2.aspx` (form ASP.NET)
  - Pagamentos: `GET /webrelpubliclstpagprecatefetuados.aspx`
  - Pendentes: `GET /webRelPublicLstPagPrecatPendentes.aspx`
- **Dados extraídos**: número do precatório, entidade devedora, valor, status de pagamento, posição cronológica, natureza (alimentar/comum)
- **Implementação**: Primeiro fazer GET para obter ViewState, depois POST com os campos do formulário incluindo ViewState. Usar Jsoup para parsing.

### 3.3 API DataJud (CNJ)

- **URL**: `POST https://api-publica.datajud.cnj.jus.br/api_publica_tjsp/_search`
- **Autenticação**: Header `Authorization: APIKey cDZHYzlZa0JadVREZDJCendQbXY6SkJlTzNjLV9TRENyQk1RdnFKZGRQdw==`
  - NOTA: Esta chave é pública e pode mudar. Tornar configurável via `application.yml`.
- **Formato**: Elasticsearch Query DSL no body
- **Consultas úteis**:
  - Por número de processo: `{"query": {"match": {"numeroProcesso": "XXXXX"}}}`
  - Por classe (precatório = 12078) e município: `{"query": {"bool": {"must": [{"match": {"classe.codigo": 12078}}, {"match": {"orgaoJulgador.codigoMunicipioIBGE": 3509502}}]}}}`
  - Paginação via `search_after` + `sort`
- **Dados retornados**: número do processo, classe, assuntos, órgão julgador, município, data de ajuizamento, movimentações, grau, formato (eletrônico/físico)
- **Limite**: até 10.000 registros por página

## 4. Modelo de Dados (PostgreSQL)

### 4.1 Tabela `processos`

```sql
CREATE TABLE processos (
    id              BIGSERIAL PRIMARY KEY,
    numero          VARCHAR(25) NOT NULL UNIQUE,  -- formato CNJ: NNNNNNN-DD.AAAA.J.TR.OOOO
    classe          VARCHAR(200),
    assunto         VARCHAR(500),
    foro            VARCHAR(200),
    vara            VARCHAR(200),
    juiz            VARCHAR(300),
    valor_acao      VARCHAR(50),
    status          VARCHAR(50) DEFAULT 'PENDENTE',  -- PENDENTE, ATIVO, EXTINTO, PAGO
    data_coleta     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    dados_brutos    JSONB       -- HTML/dados extras para reprocessamento
);
```

### 4.2 Tabela `credores`

```sql
CREATE TABLE credores (
    id                  BIGSERIAL PRIMARY KEY,
    nome                VARCHAR(500) NOT NULL,
    cpf_cnpj            VARCHAR(20),
    advogado            VARCHAR(500),
    processo_id         BIGINT REFERENCES processos(id),
    tipo_participacao   VARCHAR(50),   -- Reqte, Exequente, Credor
    data_descoberta     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(nome, processo_id)
);
```

### 4.3 Tabela `precatorios`

```sql
CREATE TABLE precatorios (
    id                      BIGSERIAL PRIMARY KEY,
    numero_precatorio       VARCHAR(30),
    numero_processo         VARCHAR(25),
    credor_id               BIGINT REFERENCES credores(id),
    entidade_devedora       VARCHAR(300),
    valor_original          DECIMAL(15,2),
    valor_atualizado        DECIMAL(15,2),
    natureza                VARCHAR(20),      -- ALIMENTAR, COMUM
    status_pagamento        VARCHAR(30),      -- PENDENTE, PAGO, PARCIAL, EXTINTO
    posicao_cronologica     INTEGER,
    data_expedicao          DATE,
    data_coleta             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    dados_brutos            JSONB
);
```

### 4.4 Tabela `prospeccoes`

```sql
CREATE TABLE prospeccoes (
    id                  BIGSERIAL PRIMARY KEY,
    processo_semente    VARCHAR(25) NOT NULL,
    status              VARCHAR(20) DEFAULT 'EM_ANDAMENTO',  -- EM_ANDAMENTO, CONCLUIDA, ERRO
    profundidade_max    INTEGER DEFAULT 2,
    max_credores        INTEGER DEFAULT 50,
    credores_encontrados INTEGER DEFAULT 0,
    processos_visitados INTEGER DEFAULT 0,
    data_inicio         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data_fim            TIMESTAMP,
    erro_mensagem       TEXT
);
```

### 4.5 Tabela `leads`

```sql
CREATE TABLE leads (
    id                  BIGSERIAL PRIMARY KEY,
    prospeccao_id       BIGINT REFERENCES prospeccoes(id),
    credor_id           BIGINT REFERENCES credores(id),
    precatorio_id       BIGINT REFERENCES precatorios(id),
    score               INTEGER DEFAULT 0,    -- 0-100, calculado pelo scoring engine
    score_detalhes      JSONB,                -- breakdown do scoring
    status_contato      VARCHAR(30) DEFAULT 'NAO_CONTACTADO',
    data_criacao        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 5. Endpoints da API

### 5.1 Prospecção

#### `POST /api/v1/prospeccao`
Inicia uma prospecção recursiva a partir de um processo-semente.

**Request:**
```json
{
  "processoSemente": "1234567-89.2020.8.26.0053",
  "profundidadeMaxima": 2,
  "maxCredores": 50,
  "filtros": {
    "entidadesDevedoras": ["FAZENDA DO ESTADO DE SAO PAULO", "MUNICIPIO DE CAMPINAS"],
    "valorMinimo": 10000.00,
    "apenasAlimentar": false,
    "apenasPendentes": true
  }
}
```

**Response (202 Accepted):**
```json
{
  "prospeccaoId": 42,
  "status": "EM_ANDAMENTO",
  "processoSemente": "1234567-89.2020.8.26.0053",
  "mensagem": "Prospecção iniciada. Consulte o status em /api/v1/prospeccao/42"
}
```

**Lógica**: A prospecção roda de forma assíncrona (coroutine ou `@Async`). O endpoint retorna imediatamente com o ID para polling.

#### `GET /api/v1/prospeccao/{id}`
Consulta o status e resultado parcial de uma prospecção.

**Response (200):**
```json
{
  "prospeccaoId": 42,
  "status": "CONCLUIDA",
  "processoSemente": "1234567-89.2020.8.26.0053",
  "estatisticas": {
    "processosVisitados": 15,
    "credoresEncontrados": 38,
    "leadsQualificados": 12,
    "tempoExecucaoSegundos": 180
  },
  "leads": [
    {
      "leadId": 101,
      "credor": {
        "nome": "MARIA DA SILVA",
        "processoOrigem": "1234567-89.2020.8.26.0053"
      },
      "precatorio": {
        "numero": "0001234-56.2019.8.26.0053",
        "entidadeDevedora": "FAZENDA DO ESTADO DE SAO PAULO",
        "valorAtualizado": 85000.50,
        "natureza": "ALIMENTAR",
        "statusPagamento": "PENDENTE",
        "posicaoCronologica": 234
      },
      "score": 87,
      "scoreDetalhes": {
        "valorScore": 30,
        "entidadeScore": 25,
        "statusScore": 20,
        "cronologiaScore": 12
      }
    }
  ]
}
```

#### `GET /api/v1/prospeccao`
Lista todas as prospecções realizadas.

**Query params**: `?status=CONCLUIDA&page=0&size=20`

**Response (200):**
```json
{
  "content": [
    {
      "prospeccaoId": 42,
      "processoSemente": "1234567-89.2020.8.26.0053",
      "status": "CONCLUIDA",
      "credoresEncontrados": 38,
      "leadsQualificados": 12,
      "dataInicio": "2025-12-20T10:30:00"
    }
  ],
  "totalElements": 5,
  "totalPages": 1,
  "number": 0
}
```

### 5.2 Consulta de Processos

#### `GET /api/v1/processo/{numero}`
Consulta um processo específico no e-SAJ e retorna dados estruturados.

**Response (200):**
```json
{
  "numero": "1234567-89.2020.8.26.0053",
  "classe": "Cumprimento de Sentença contra a Fazenda Pública",
  "assunto": "Pagamento",
  "foro": "Foro Central - Fazenda Pública/Acidentes",
  "vara": "1ª Vara da Fazenda Pública",
  "juiz": "Dr. Fulano de Tal",
  "valorAcao": "R$ 150.000,00",
  "partes": [
    {
      "tipo": "Reqte",
      "nome": "JOAO DA SILVA",
      "advogado": "Dr. Advogado (OAB/SP 123456)"
    },
    {
      "tipo": "Reqdo",
      "nome": "FAZENDA DO ESTADO DE SAO PAULO",
      "advogado": ""
    }
  ],
  "incidentesPrecatorio": [
    {
      "numero": "0001234-56.2019.8.26.0053",
      "tipo": "Precatório"
    }
  ],
  "ultimosAndamentos": [
    {
      "data": "15/10/2024",
      "descricao": "Expedido ofício requisitório"
    }
  ],
  "fonte": "E_SAJ",
  "dataColeta": "2025-12-20T10:35:00"
}
```

#### `GET /api/v1/processo/buscar`
Busca processos por nome, CPF ou número.

**Query params**: `?nome=JOAO+DA+SILVA` ou `?cpf=12345678900` ou `?numero=1234567-89.2020.8.26.0053`

**Response (200):**
```json
{
  "resultados": [
    {
      "numero": "1234567-89.2020.8.26.0053",
      "urlEsaj": "https://esaj.tjsp.jus.br/cpopg/show.do?processo.numero=..."
    }
  ],
  "totalResultados": 3
}
```

### 5.3 Consulta de Precatórios

#### `GET /api/v1/precatorio/{numero}`
Consulta dados de um precatório específico no portal CAC/SCP.

**Response (200):**
```json
{
  "numeroPrecatorio": "0001234-56.2019.8.26.0053",
  "entidadeDevedora": "FAZENDA DO ESTADO DE SAO PAULO",
  "valorAtualizado": 85000.50,
  "natureza": "ALIMENTAR",
  "statusPagamento": "PENDENTE",
  "posicaoCronologica": 234,
  "dataExpedicao": "2019-06-15",
  "fonte": "CAC_SCP",
  "dataColeta": "2025-12-20T10:40:00"
}
```

### 5.4 Consulta DataJud

#### `POST /api/v1/datajud/buscar`
Proxy para a API DataJud do CNJ com tratamento dos dados.

**Request:**
```json
{
  "numeroProcesso": "1234567-89.2020.8.26.0053"
}
```

Ou busca em massa:

```json
{
  "classeCodigo": 12078,
  "municipioIBGE": 3509502,
  "maxResultados": 50
}
```

**Response (200):**
```json
{
  "totalEncontrado": 1250,
  "resultados": [
    {
      "numeroProcesso": "1234567-89.2020.8.26.0053",
      "classe": "Precatório",
      "assuntos": ["Obrigações"],
      "orgaoJulgador": "DEPRE - Diretoria de Execuções de Precatórios",
      "municipioIBGE": 3509502,
      "dataAjuizamento": "2019-06-15",
      "grau": "G1",
      "numMovimentacoes": 25
    }
  ]
}
```

### 5.5 Leads

#### `GET /api/v1/leads`
Lista leads qualificados com filtros.

**Query params**: `?scoreMinimo=70&status=NAO_CONTACTADO&entidade=CAMPINAS&page=0&size=20&sort=score,desc`

**Response (200):**
```json
{
  "content": [
    {
      "leadId": 101,
      "credor": "MARIA DA SILVA",
      "processoOrigem": "1234567-89.2020.8.26.0053",
      "precatorio": "0001234-56.2019.8.26.0053",
      "entidadeDevedora": "FAZENDA DO ESTADO DE SAO PAULO",
      "valorAtualizado": 85000.50,
      "score": 87,
      "statusContato": "NAO_CONTACTADO"
    }
  ],
  "totalElements": 12,
  "totalPages": 1
}
```

#### `PATCH /api/v1/leads/{id}/status`
Atualiza status de contato de um lead.

**Request:**
```json
{
  "statusContato": "CONTACTADO",
  "observacao": "Ligou para o credor, demonstrou interesse"
}
```

## 6. Motor de Scoring

O scoring classifica cada lead de 0 a 100 pontos, baseado nos critérios do escritório:

| Critério | Peso | Lógica |
|----------|------|--------|
| Valor do precatório | 30 pts | < R$10k = 0, R$10-50k = 15, R$50-200k = 25, > R$200k = 30 |
| Entidade devedora | 25 pts | Estado SP = 25, Campinas = 25, Outros municípios = 10, Outros = 0 |
| Status pagamento | 20 pts | Pendente = 20, Parcial = 10, Pago = 0, Extinto = 0 |
| Posição cronológica | 15 pts | Top 100 = 15, Top 500 = 10, Top 1000 = 5, Resto = 0 |
| Natureza | 10 pts | Alimentar = 10, Comum = 5 |

Implementar como `ScoringService` com regras configuráveis via `application.yml`:

```yaml
scoring:
  regras:
    valor:
      peso: 30
      faixas:
        - min: 0
          max: 10000
          pontos: 0
        - min: 10000
          max: 50000
          pontos: 15
        - min: 50000
          max: 200000
          pontos: 25
        - min: 200000
          max: 999999999
          pontos: 30
    entidade-devedora:
      peso: 25
      entidades-alvo:
        - nome: "FAZENDA DO ESTADO DE SAO PAULO"
          pontos: 25
        - nome: "MUNICIPIO DE CAMPINAS"
          pontos: 25
```

## 7. Estrutura do Projeto

```
precatorios-api/
├── build.gradle.kts
├── settings.gradle.kts
├── Dockerfile
├── docker-compose.yml
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── br/com/precatorios/
│   │   │       ├── PrecatoriosApiApplication.kt
│   │   │       ├── config/
│   │   │       │   ├── WebClientConfig.kt          # Beans de WebClient para e-SAJ, DataJud
│   │   │       │   ├── CacheConfig.kt              # Caffeine cache
│   │   │       │   ├── AsyncConfig.kt              # Thread pool para prospecção
│   │   │       │   └── OpenApiConfig.kt             # Swagger
│   │   │       ├── controller/
│   │   │       │   ├── ProspeccaoController.kt
│   │   │       │   ├── ProcessoController.kt
│   │   │       │   ├── PrecatorioController.kt
│   │   │       │   ├── DataJudController.kt
│   │   │       │   └── LeadController.kt
│   │   │       ├── service/
│   │   │       │   ├── ProspeccaoService.kt         # Orquestra a prospecção recursiva
│   │   │       │   ├── ScoringService.kt            # Motor de scoring
│   │   │       │   ├── LeadService.kt
│   │   │       │   └── ProcessoService.kt
│   │   │       ├── scraper/
│   │   │       │   ├── EsajScraper.kt               # Scraping do e-SAJ com Jsoup
│   │   │       │   ├── PrecatorioPortalScraper.kt   # Scraping do CAC/SCP com Jsoup
│   │   │       │   └── DataJudClient.kt             # Client REST para API DataJud
│   │   │       ├── model/
│   │   │       │   ├── entity/                      # JPA Entities
│   │   │       │   │   ├── Processo.kt
│   │   │       │   │   ├── Credor.kt
│   │   │       │   │   ├── Precatorio.kt
│   │   │       │   │   ├── Prospeccao.kt
│   │   │       │   │   └── Lead.kt
│   │   │       │   ├── dto/                         # DTOs de request/response
│   │   │       │   │   ├── ProspeccaoRequest.kt
│   │   │       │   │   ├── ProspeccaoResponse.kt
│   │   │       │   │   ├── ProcessoResponse.kt
│   │   │       │   │   ├── PrecatorioResponse.kt
│   │   │       │   │   ├── DataJudRequest.kt
│   │   │       │   │   ├── DataJudResponse.kt
│   │   │       │   │   ├── LeadResponse.kt
│   │   │       │   │   └── LeadStatusUpdate.kt
│   │   │       │   └── enums/
│   │   │       │       ├── StatusProspeccao.kt
│   │   │       │       ├── StatusPagamento.kt
│   │   │       │       ├── NaturezaPrecatorio.kt
│   │   │       │       ├── TipoParticipacao.kt
│   │   │       │       └── StatusContato.kt
│   │   │       ├── repository/
│   │   │       │   ├── ProcessoRepository.kt
│   │   │       │   ├── CredorRepository.kt
│   │   │       │   ├── PrecatorioRepository.kt
│   │   │       │   ├── ProspeccaoRepository.kt
│   │   │       │   └── LeadRepository.kt
│   │   │       └── exception/
│   │   │           ├── GlobalExceptionHandler.kt    # @ControllerAdvice
│   │   │           ├── ProcessoNaoEncontradoException.kt
│   │   │           └── ScrapingException.kt
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-docker.yml
│   │       └── db/migration/
│   │           └── V1__create_tables.sql
│   └── test/
│       └── kotlin/
│           └── br/com/precatorios/
│               ├── scraper/
│               │   ├── EsajScraperTest.kt           # Testar com HTML mockado
│               │   └── DataJudClientTest.kt
│               ├── service/
│               │   ├── ScoringServiceTest.kt
│               │   └── ProspeccaoServiceTest.kt
│               └── controller/
│                   └── ProspeccaoControllerTest.kt
```

## 8. application.yml

```yaml
server:
  port: 8080

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
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration

# Configurações dos scrapers
scraper:
  esaj:
    base-url: https://esaj.tjsp.jus.br
    delay-ms: 2000
    timeout-ms: 30000
    user-agent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
    max-retries: 3
  precatorio-portal:
    base-url: https://www.tjsp.jus.br/cac/scp
    delay-ms: 2000
    timeout-ms: 30000
  datajud:
    base-url: https://api-publica.datajud.cnj.jus.br
    endpoint-tjsp: /api_publica_tjsp/_search
    api-key: "cDZHYzlZa0JadVREZDJCendQbXY6SkJlTzNjLV9TRENyQk1RdnFKZGRQdw=="
    timeout-ms: 30000
    max-resultados-por-pagina: 100

# Configurações de prospecção
prospeccao:
  profundidade-maxima-default: 2
  max-credores-default: 50
  thread-pool-size: 4

# Scoring
scoring:
  regras:
    valor:
      peso: 30
      faixas:
        - { min: 0, max: 10000, pontos: 0 }
        - { min: 10000, max: 50000, pontos: 15 }
        - { min: 50000, max: 200000, pontos: 25 }
        - { min: 200000, max: 999999999, pontos: 30 }
    entidade-devedora:
      peso: 25
      alvos:
        - { nome: "FAZENDA DO ESTADO DE SAO PAULO", pontos: 25 }
        - { nome: "MUNICIPIO DE CAMPINAS", pontos: 25 }
    status-pagamento:
      peso: 20
    posicao-cronologica:
      peso: 15
    natureza:
      peso: 10
```

## 9. Docker

### Dockerfile

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]
```

### docker-compose.yml

```yaml
version: '3.8'
services:
  api:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/precatorios
    depends_on:
      db:
        condition: service_healthy
    restart: unless-stopped

  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: precatorios
      POSTGRES_USER: precatorios
      POSTGRES_PASSWORD: precatorios
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U precatorios"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  pgdata:
```

## 10. Dependências Gradle (build.gradle.kts)

```kotlin
plugins {
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.5"
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0"
    kotlin("plugin.jpa") version "2.0.0"
}

group = "br.com.precatorios"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webflux")  // WebClient
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    
    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    
    // Scraping
    implementation("org.jsoup:jsoup:1.18.1")
    
    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    
    // Cache
    implementation("com.github.ben-manes.caffeine:caffeine")
    
    // API Docs
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")
    
    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.testcontainers:postgresql:1.19.8")
    testImplementation("org.testcontainers:junit-jupiter:1.19.8")
}
```

## 11. Lógica de Prospecção Recursiva (pseudocódigo)

```
function prospectar(processoSemente, profundidadeMax, maxCredores, filtros):
    fila = [processoSemente]
    visitados = {}
    leads = []
    profundidadeAtual = 0
    
    while fila não vazia AND leads.size < maxCredores AND profundidadeAtual < profundidadeMax:
        proximaFila = []
        
        for processo in fila:
            if processo in visitados:
                continue
            visitados.add(processo)
            
            // 1. Buscar dados do processo no e-SAJ
            dadosProcesso = esajScraper.extrairDados(processo)
            salvarProcesso(dadosProcesso)
            
            // 2. Extrair credores (partes do tipo Reqte/Exequente/Credor)
            for parte in dadosProcesso.partes:
                if parte.tipo in [Reqte, Exequente, Credor]:
                    credor = salvarCredor(parte, processo)
                    
                    // 3. Para cada incidente de precatório, buscar dados
                    for incidente in dadosProcesso.incidentes:
                        dadosPrecatorio = precatorioScraper.consultar(incidente.numero)
                        
                        // 4. Enriquecer com DataJud se necessário
                        dadosDataJud = dataJudClient.buscar(incidente.numero)
                        
                        precatorio = salvarPrecatorio(dadosPrecatorio, credor)
                        
                        // 5. Calcular score
                        score = scoringService.calcular(precatorio, filtros)
                        
                        if score > 0:
                            lead = salvarLead(credor, precatorio, score)
                            leads.add(lead)
                    
                    // 6. Adicionar processos do credor à fila para próxima profundidade
                    processosDoCredor = esajScraper.buscarPorNome(credor.nome)
                    proximaFila.addAll(processosDoCredor)
            
            sleep(delay)  // rate limiting
        
        fila = proximaFila
        profundidadeAtual++
    
    return leads
```

## 12. Tratamento de Erros

- **Scraping failures**: Retry com backoff exponencial (1s, 2s, 4s). Máx 3 tentativas.
- **Processo não encontrado**: Retornar 404 com mensagem clara.
- **Rate limiting do TJ-SP**: Se detectar HTTP 429 ou CAPTCHA, pausar por 60s e retomar.
- **Timeout**: Configurável por fonte. Default 30s.
- **Dados incompletos**: Continuar prospecção mesmo se um processo falhar. Registrar erro no log e no campo `erro_mensagem` da prospecção.

## 13. Observações para Implementação

1. **Jsoup é a escolha certa para o e-SAJ** — é HTML estático, não precisa de browser headless. Jsoup é nativo Java, leve e rápido.
2. **O portal CAC/SCP (ASP.NET) é mais complexo** — precisa manter sessão, enviar ViewState. Pode ser necessário usar HtmlUnit ou WebClient com cookie jar. Começar com Jsoup e escalar para HtmlUnit se necessário.
3. **Cache é essencial** — muitos processos serão revisitados. Cachear resultados de scraping por pelo menos 24h (Caffeine TTL).
4. **A prospecção assíncrona é obrigatória** — cada prospecção pode levar minutos (dezenas de requests com delay de 2s). Usar `@Async` com pool de threads dedicado ou Kotlin coroutines.
5. **Seletores CSS vão precisar de calibração** — os IDs listados aqui (`#tablePartesPrincipais`, `#incidentes`, etc.) são baseados na análise do HTML público. Precisam ser validados com requests reais e ajustados conforme necessário. Centralizar todos os seletores em constantes para facilitar manutenção.
6. **Swagger UI** em `/swagger-ui.html` para testar os endpoints facilmente.
