package br.com.precatorios.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class LeadSummaryDTO(
    val id: Long,
    val score: Int,
    val scoreDetalhes: Map<String, Any?>?,
    val credorNome: String?,
    val credorCpfCnpj: String?,
    val precatorioNumero: String?,
    val entidadeDevedora: String?,
    val valorAtualizado: BigDecimal?,
    val natureza: String?,
    val statusPagamento: String?,
    val statusContato: String,
    val dataCriacao: LocalDateTime?
)
