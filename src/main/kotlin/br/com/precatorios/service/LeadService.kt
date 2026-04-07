package br.com.precatorios.service

import br.com.precatorios.domain.enums.StatusContato
import br.com.precatorios.dto.AtualizarStatusContatoRequestDTO
import br.com.precatorios.dto.LeadResponseDTO
import br.com.precatorios.exception.LeadNaoEncontradoException
import br.com.precatorios.repository.LeadRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class LeadService(
    private val leadRepository: LeadRepository,
    private val objectMapper: ObjectMapper
) {

    fun listarLeads(
        scoreMinimo: Int?,
        statusContato: StatusContato?,
        entidadeDevedora: String?,
        incluirZero: Boolean,
        pageable: Pageable
    ): Page<LeadResponseDTO> {
        val effectiveScoreMinimo = if (incluirZero) {
            scoreMinimo
        } else {
            maxOf(scoreMinimo ?: 0, 1)
        }

        val entidadeDevedoraPattern = entidadeDevedora?.lowercase()?.let { "%$it%" }

        return leadRepository.findLeadsFiltered(
            effectiveScoreMinimo,
            statusContato,
            entidadeDevedoraPattern,
            pageable
        ).map { LeadResponseDTO.fromEntity(it, objectMapper) }
    }

    fun atualizarStatusContato(id: Long, request: AtualizarStatusContatoRequestDTO): LeadResponseDTO {
        val lead = leadRepository.findById(id)
            .orElseThrow { LeadNaoEncontradoException("Lead id=$id nao encontrado") }

        lead.statusContato = request.statusContato
        lead.observacao = request.observacao

        return LeadResponseDTO.fromEntity(leadRepository.save(lead), objectMapper)
    }
}
