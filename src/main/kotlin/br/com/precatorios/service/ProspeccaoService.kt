package br.com.precatorios.service

import br.com.precatorios.domain.Prospeccao
import br.com.precatorios.domain.enums.StatusProspeccao
import br.com.precatorios.repository.ProspeccaoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ProspeccaoService(
    private val prospeccaoRepository: ProspeccaoRepository
) {

    @Transactional
    fun criar(
        processoSemente: String,
        profundidadeMaxima: Int?,
        maxCredores: Int?
    ): Prospeccao {
        val prospeccao = Prospeccao().apply {
            this.processoSemente = processoSemente
            this.profundidadeMax = profundidadeMaxima ?: 2
            this.maxCredores = maxCredores ?: 50
            this.status = StatusProspeccao.EM_ANDAMENTO
            this.dataInicio = LocalDateTime.now()
        }
        return prospeccaoRepository.save(prospeccao)
    }
}
