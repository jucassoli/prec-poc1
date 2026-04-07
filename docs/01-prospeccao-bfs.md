# Prospeccao BFS — Fluxo Completo

Fluxo principal do sistema: inicia uma prospeccao recursiva a partir de um processo-semente, descobre co-credores em profundidade, consulta precatorios, aplica scoring e persiste leads qualificados.

```mermaid
sequenceDiagram
    actor Cliente
    participant Controller as ProspeccaoController
    participant Service as ProspeccaoService
    participant DB as PostgreSQL
    participant Engine as BfsProspeccaoEngine
    participant ESAJ as e-SAJ (TJ-SP)
    participant CAC as CAC/SCP (TJ-SP)
    participant DataJud as DataJud (CNJ)
    participant Cache as Caffeine Cache
    participant Scoring as ScoringService
    participant Helper as PersistenceHelper

    Note over Cliente,Helper: POST /api/v1/prospeccao

    Cliente->>Controller: POST /api/v1/prospeccao<br/>{processoSemente, profundidadeMaxima, ...}
    Controller->>Service: criar(processoSemente, profundidade, maxCredores)
    Service->>DB: INSERT prospeccao (status=EM_ANDAMENTO)
    DB-->>Service: prospeccao.id
    Service-->>Controller: Prospeccao

    Controller->>Engine: start(prospeccaoId, ...) [ASYNC]
    Controller-->>Cliente: HTTP 202 Accepted<br/>{prospeccaoId}

    Note over Engine,Helper: Execucao assincrona no prospeccaoExecutor<br/>(ThreadPool core=2, max=4)

    loop BFS — para cada processo na fila (profundidade 0..N)
        Engine->>Cache: processos.get(numero)?
        alt Cache HIT
            Cache-->>Engine: ProcessoScraped (cached)
        else Cache MISS
            Engine->>ESAJ: POST consulta processo<br/>(rate limit: 1 req/2s)
            Note over Engine,ESAJ: Retry 3x, backoff exponencial<br/>Pausa 60s em HTTP 429
            ESAJ-->>Engine: HTML da pagina do processo
            Engine->>Engine: Jsoup parse:<br/>partes, incidentes, classe, foro
            Engine->>Cache: processos.put(numero, resultado)
        end

        Engine->>DB: UPSERT Processo

        loop Para cada parte do processo
            Engine->>DB: UPSERT Credor (unique: nome + processoId)

            loop Para cada incidente (precatorio)
                Engine->>Cache: precatorios.get(numero)?
                alt Cache HIT
                    Cache-->>Engine: PrecatorioScraped (cached)
                else Cache MISS
                    Engine->>CAC: GET formulario (obter ViewState)
                    CAC-->>Engine: __VIEWSTATE, __EVENTVALIDATION
                    Engine->>CAC: POST com ViewState + numeroPrecatorio<br/>(rate limit: 1 req/2s)
                    Note over Engine,CAC: Retry 3x, backoff exponencial<br/>Renovacao de sessao max 1x
                    CAC-->>Engine: HTML resultado precatorio
                    Engine->>Engine: Parse: valor, entidade, natureza,<br/>status pgto, posicao cronologica
                    Engine->>Cache: precatorios.put(numero, resultado)
                end

                Engine->>DB: UPSERT Precatorio

                Engine->>Engine: Aplicar filtros:<br/>entidadeDevedora, valorMinimo,<br/>apenasAlimentar, apenasPendentes

                alt Passou nos filtros
                    Engine->>Scoring: score(precatorio, credor)
                    Note over Scoring: Pontuacao 0-100:<br/>valor, entidade, natureza,<br/>status pgto, posicao cronologica
                    Scoring-->>Engine: ScoredResult (score + detalhes)

                    Engine->>Helper: persistirLead(prospeccao, credor, precatorio, score)
                    Note over Helper: @Transactional(REQUIRES_NEW)
                    Helper->>DB: INSERT Lead (score, scoreDetalhes JSON)
                end
            end

            alt Profundidade < maxima (expansao BFS)
                Engine->>ESAJ: buscarPorNome(parte.nome)<br/>(rate limit: 1 req/2s)
                ESAJ-->>Engine: Lista de processos relacionados
                Engine->>Engine: Enfileirar processos nao visitados<br/>na profundidade + 1
            end
        end

        Engine->>Helper: atualizarContadores(prospeccao)
        Helper->>DB: UPDATE processosVisitados,<br/>credoresEncontrados, leadsQualificados
    end

    alt Sucesso
        Engine->>Helper: finalizarProspeccao(status=CONCLUIDA)
        Helper->>DB: UPDATE status=CONCLUIDA, dataFim=now()
    else Erro fatal
        Engine->>Helper: finalizarProspeccao(status=ERRO, mensagem)
        Helper->>DB: UPDATE status=ERRO, erroMensagem, dataFim=now()
    end
```
