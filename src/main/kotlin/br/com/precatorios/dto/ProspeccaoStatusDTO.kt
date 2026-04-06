package br.com.precatorios.dto

import java.time.LocalDateTime

data class ProspeccaoStatusDTO(
    val id: Long,
    val processoSemente: String,
    val status: String,
    val profundidadeMaxima: Int,
    val maxCredores: Int,
    val processosVisitados: Int,
    val credoresEncontrados: Int,
    val leadsQualificados: Int,
    val dataInicio: LocalDateTime?,
    val dataFim: LocalDateTime?,
    val erroMensagem: String?,
    val leads: List<LeadSummaryDTO>?  // populated only when CONCLUIDA
)
