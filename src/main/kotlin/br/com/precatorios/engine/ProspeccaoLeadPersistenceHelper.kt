package br.com.precatorios.engine

import br.com.precatorios.domain.Credor
import br.com.precatorios.domain.Lead
import br.com.precatorios.domain.Precatorio
import br.com.precatorios.domain.Prospeccao
import br.com.precatorios.domain.enums.StatusContato
import br.com.precatorios.domain.enums.StatusProspeccao
import br.com.precatorios.repository.LeadRepository
import br.com.precatorios.repository.ProspeccaoRepository
import br.com.precatorios.service.ScoredResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ProspeccaoLeadPersistenceHelper(
    private val leadRepository: LeadRepository,
    private val prospeccaoRepository: ProspeccaoRepository,
    private val objectMapper: ObjectMapper
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun persistirLead(
        prospeccao: Prospeccao,
        credor: Credor,
        precatorio: Precatorio,
        scored: ScoredResult
    ): Lead {
        val lead = Lead().apply {
            this.score = scored.total
            this.scoreDetalhes = objectMapper.writeValueAsString(scored.detalhes)
            this.statusContato = StatusContato.NAO_CONTACTADO
            this.dataCriacao = LocalDateTime.now()
            this.prospeccao = prospeccao
            this.credor = credor
            this.precatorio = precatorio
        }
        return leadRepository.save(lead)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun atualizarContadores(
        prospeccaoId: Long,
        processosVisitados: Int,
        credoresEncontrados: Int,
        leadsQualificados: Int
    ) {
        val prospeccao = prospeccaoRepository.findById(prospeccaoId)
            .orElseThrow { IllegalArgumentException("Prospecção não encontrada: $prospeccaoId") }
        prospeccao.processosVisitados = processosVisitados
        prospeccao.credoresEncontrados = credoresEncontrados
        prospeccao.leadsQualificados = leadsQualificados
        prospeccaoRepository.save(prospeccao)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun finalizarProspeccao(
        prospeccaoId: Long,
        processosVisitados: Int,
        credoresEncontrados: Int,
        leadsQualificados: Int,
        erroMensagem: String?,
        status: StatusProspeccao
    ) {
        val prospeccao = prospeccaoRepository.findById(prospeccaoId)
            .orElseThrow { IllegalArgumentException("Prospecção não encontrada: $prospeccaoId") }
        prospeccao.processosVisitados = processosVisitados
        prospeccao.credoresEncontrados = credoresEncontrados
        prospeccao.leadsQualificados = leadsQualificados
        prospeccao.status = status
        prospeccao.erroMensagem = erroMensagem
        prospeccao.dataFim = LocalDateTime.now()
        prospeccaoRepository.save(prospeccao)
    }
}
