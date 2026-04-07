package br.com.precatorios.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Precatórios API — Prospecção de Leads TJ-SP")
                .description(
                    """
                    API REST para prospecção automatizada de credores de precatórios no Tribunal de Justiça
                    do Estado de São Paulo (TJ-SP). Desenvolvida para o escritório FUNPREC.

                    **Funcionalidades principais:**
                    - Prospecção BFS recursiva a partir de um processo-semente, descobrindo co-credores em profundidade configurável
                    - Consulta a 3 fontes públicas: e-SAJ (processos), CAC/SCP (precatórios) e DataJud (API CNJ)
                    - Scoring automático de 0 a 100 pontos baseado em valor, entidade devedora, status de pagamento, posição cronológica e natureza
                    - Gestão de leads com filtros, paginação e atualização de status de contato
                    - Cache inteligente (Caffeine, TTL 24h) para evitar requisições redundantes aos portais
                    - Rate limiting integrado (2s entre requisições, backoff exponencial, pausa em HTTP 429)

                    **Fluxo típico de uso:**
                    1. Inicie uma prospecção via `POST /api/v1/prospeccao` com um número de processo-semente
                    2. Acompanhe o progresso via `GET /api/v1/prospeccao/{id}` (polling com Retry-After)
                    3. Ao concluir, consulte os leads qualificados via `GET /api/v1/leads` com filtros e ordenação por score
                    4. Atualize o status de contato dos leads via `PATCH /api/v1/leads/{id}/status`
                    """.trimIndent()
                )
                .version("1.0.0")
                .contact(
                    Contact()
                        .name("FUNPREC — Equipe de Desenvolvimento")
                )
        )
}
