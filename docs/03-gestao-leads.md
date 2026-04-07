# Gestao de Leads — Listagem e Atualizacao de Status

Fluxos de consulta paginada de leads com filtros e atualizacao de status de contato.

## Listagem com Filtros

```mermaid
sequenceDiagram
    actor Cliente
    participant Controller as LeadController
    participant Service as LeadService
    participant Repo as LeadRepository
    participant DB as PostgreSQL

    Note over Cliente,DB: GET /api/v1/leads?scoreMinimo=50&statusContato=NAO_CONTACTADO&entidadeDevedora=campinas

    Cliente->>Controller: GET /api/v1/leads<br/>?scoreMinimo=50<br/>&statusContato=NAO_CONTACTADO<br/>&entidadeDevedora=campinas<br/>&incluirZero=false<br/>&page=0&size=20&sort=score,desc

    Controller->>Service: listarLeads(50, NAO_CONTACTADO,<br/>"campinas", false, pageable)

    Service->>Service: Calcular effectiveScoreMinimo:<br/>incluirZero=false → max(50, 1) = 50

    Service->>Service: Preparar pattern:<br/>"campinas" → "%campinas%"

    Service->>Repo: findLeadsFiltered(50, NAO_CONTACTADO,<br/>"%campinas%", pageable)

    Repo->>DB: SELECT l FROM Lead l<br/>JOIN FETCH l.credor c<br/>JOIN FETCH l.precatorio p<br/>WHERE l.score >= 50<br/>AND l.statusContato = 'NAO_CONTACTADO'<br/>AND LOWER(p.entidadeDevedora) LIKE '%campinas%'<br/>ORDER BY l.score DESC<br/>LIMIT 20 OFFSET 0

    DB-->>Repo: Page<Lead>

    Repo-->>Service: Page<Lead>

    Service->>Service: Mapear Lead → LeadResponseDTO<br/>com CredorSummaryDTO e PrecatorioSummaryDTO

    Service-->>Controller: Page<LeadResponseDTO>
    Controller-->>Cliente: HTTP 200<br/>{content: [{id, score: 85,<br/>credor: {nome, cpfCnpj},<br/>precatorio: {numero, valor, entidade},<br/>statusContato: "NAO_CONTACTADO"}, ...],<br/>totalElements: 3, totalPages: 1}
```

## Atualizacao de Status de Contato

```mermaid
sequenceDiagram
    actor Cliente
    participant Controller as LeadController
    participant Service as LeadService
    participant Repo as LeadRepository
    participant DB as PostgreSQL

    Note over Cliente,DB: PATCH /api/v1/leads/7/status

    Cliente->>Controller: PATCH /api/v1/leads/7/status<br/>{statusContato: "EM_CONTATO",<br/>observacao: "Ligou, pediu retorno segunda"}

    Controller->>Service: atualizarStatusContato(7, request)

    Service->>Repo: findById(7)
    Repo->>DB: SELECT * FROM leads WHERE id=7
    DB-->>Repo: Lead

    alt Lead nao encontrado
        Repo-->>Service: Optional.empty()
        Service-->>Controller: throw LeadNaoEncontradoException(7)
        Controller-->>Cliente: HTTP 404<br/>{status: 404, message: "Lead 7 nao encontrado"}
    end

    Service->>Service: lead.statusContato = EM_CONTATO<br/>lead.observacao = "Ligou, pediu retorno segunda"

    Service->>Repo: save(lead)
    Repo->>DB: UPDATE leads SET<br/>status_contato='EM_CONTATO',<br/>observacao='Ligou, pediu retorno segunda'<br/>WHERE id=7
    DB-->>Repo: Lead atualizado

    Service->>Service: Mapear → LeadResponseDTO

    Service-->>Controller: LeadResponseDTO
    Controller-->>Cliente: HTTP 200<br/>{id: 7, score: 85,<br/>statusContato: "EM_CONTATO",<br/>observacao: "Ligou, pediu retorno segunda", ...}
```
