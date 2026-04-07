package br.com.precatorios.startup

import br.com.precatorios.domain.enums.StatusProspeccao
import br.com.precatorios.repository.ProspeccaoRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class StaleJobRecoveryRunner(
    private val prospeccaoRepository: ProspeccaoRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(StaleJobRecoveryRunner::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val stale = prospeccaoRepository.findByStatus(StatusProspeccao.EM_ANDAMENTO, Pageable.unpaged())
        stale.forEach { prospeccao ->
            val msg = "Interrompida por reinicio (iniciada em ${prospeccao.dataInicio})"
            prospeccao.status = StatusProspeccao.ERRO
            prospeccao.erroMensagem = msg
            prospeccao.dataFim = LocalDateTime.now()
            prospeccaoRepository.save(prospeccao)
            log.warn("Recovered stale prospeccao id={}: {}", prospeccao.id, msg)
        }
        if (stale.totalElements > 0) {
            log.info("Stale job recovery complete -- {} jobs marked ERRO", stale.totalElements)
        }
    }
}
