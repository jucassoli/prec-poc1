package br.com.precatorios.dto

import br.com.precatorios.domain.Lead
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.LocalDateTime

data class LeadResponseDTO(
    val id: Long,
    val score: Int,
    val scoreDetalhes: Map<String, Any?>?,
    val statusContato: String,
    val observacao: String?,
    val dataCriacao: LocalDateTime?,
    val credor: CredorSummaryDTO,
    val precatorio: PrecatorioSummaryDTO
) {
    data class CredorSummaryDTO(
        val id: Long,
        val nome: String
    )

    data class PrecatorioSummaryDTO(
        val id: Long,
        val numero: String?,
        val valorAtualizado: BigDecimal?,
        val entidadeDevedora: String?,
        val statusPagamento: String?
    )

    companion object {
        fun fromEntity(lead: Lead, objectMapper: ObjectMapper): LeadResponseDTO {
            val scoreDetalhes = lead.scoreDetalhes?.let {
                objectMapper.readValue(it, object : TypeReference<Map<String, Any?>>() {})
            }
            val credorEntity = lead.credor!!
            val precatorioEntity = lead.precatorio!!

            return LeadResponseDTO(
                id = lead.id!!,
                score = lead.score,
                scoreDetalhes = scoreDetalhes,
                statusContato = lead.statusContato.name,
                observacao = lead.observacao,
                dataCriacao = lead.dataCriacao,
                credor = CredorSummaryDTO(
                    id = credorEntity.id!!,
                    nome = credorEntity.nome
                ),
                precatorio = PrecatorioSummaryDTO(
                    id = precatorioEntity.id!!,
                    numero = precatorioEntity.numeroPrecatorio,
                    valorAtualizado = precatorioEntity.valorAtualizado,
                    entidadeDevedora = precatorioEntity.entidadeDevedora,
                    statusPagamento = precatorioEntity.statusPagamento
                )
            )
        }
    }
}
