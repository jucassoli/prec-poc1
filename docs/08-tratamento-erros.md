# Tratamento de Erros — GlobalExceptionHandler

Fluxo de tratamento centralizado de erros. Todas as excecoes sao capturadas pelo @ControllerAdvice e retornam respostas estruturadas sem expor stack traces.

```mermaid
sequenceDiagram
    actor Cliente
    participant Controller as Qualquer Controller
    participant Handler as GlobalExceptionHandler<br/>(@ControllerAdvice)

    Note over Cliente,Handler: Cenarios de erro e seus tratamentos

    rect rgb(255, 235, 235)
        Note over Cliente,Handler: HTTP 400 — Requisicao Invalida
        Cliente->>Controller: Request com JSON invalido<br/>ou enum inexistente
        Controller-->>Handler: HttpMessageNotReadableException
        Handler-->>Cliente: HTTP 400<br/>{status: 400,<br/>message: "Corpo da requisicao invalido",<br/>timestamp: "2026-04-07T..."}
    end

    rect rgb(255, 235, 235)
        Note over Cliente,Handler: HTTP 400 — Validacao
        Cliente->>Controller: Request com campo invalido<br/>(ex: nome < 3 caracteres)
        Controller-->>Handler: MethodArgumentNotValidException
        Handler-->>Cliente: HTTP 400<br/>{status: 400,<br/>message: "campo: mensagem de validacao",<br/>timestamp: "..."}
    end

    rect rgb(255, 245, 230)
        Note over Cliente,Handler: HTTP 404 — Nao Encontrado
        Cliente->>Controller: GET /api/v1/processo/inexistente
        Controller-->>Handler: ProcessoNaoEncontradoException
        Handler-->>Cliente: HTTP 404<br/>{status: 404,<br/>message: "Processo nao encontrado",<br/>timestamp: "..."}
    end

    rect rgb(255, 245, 230)
        Note over Cliente,Handler: HTTP 404 — Lead Nao Encontrado
        Cliente->>Controller: PATCH /api/v1/leads/999/status
        Controller-->>Handler: LeadNaoEncontradoException
        Handler-->>Cliente: HTTP 404<br/>{status: 404,<br/>message: "Lead 999 nao encontrado",<br/>timestamp: "..."}
    end

    rect rgb(255, 240, 240)
        Note over Cliente,Handler: HTTP 429 — Rate Limit
        Cliente->>Controller: Requisicoes em excesso
        Controller-->>Handler: TooManyRequestsException
        Handler-->>Cliente: HTTP 429<br/>{status: 429,<br/>message: "Taxa de requisicoes excedida.<br/>Tente novamente mais tarde.",<br/>timestamp: "..."}
    end

    rect rgb(240, 240, 255)
        Note over Cliente,Handler: HTTP 503 — Scraping Indisponivel
        Cliente->>Controller: Falha ao acessar portal
        Controller-->>Handler: ScrapingException
        Handler-->>Cliente: HTTP 503<br/>{status: 503,<br/>message: "Erro ao acessar fonte de dados",<br/>timestamp: "..."}
    end

    rect rgb(240, 240, 240)
        Note over Cliente,Handler: HTTP 500 — Erro Generico
        Cliente->>Controller: Erro inesperado interno
        Controller-->>Handler: Exception (qualquer)
        Handler-->>Cliente: HTTP 500<br/>{status: 500,<br/>message: "Erro interno do servidor",<br/>timestamp: "..."}
        Note over Handler: Mensagem generica fixa.<br/>Stack trace NUNCA exposto<br/>ao cliente (STRIDE T-05-05).
    end
```
