package br.com.precatorios.domain

import br.com.precatorios.domain.enums.StatusProspeccao
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "prospeccoes")
class Prospeccao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "processo_semente", nullable = false, length = 25)
    var processoSemente: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    var status: StatusProspeccao = StatusProspeccao.EM_ANDAMENTO

    @Column(name = "profundidade_max")
    var profundidadeMax: Int = 2

    @Column(name = "max_credores")
    var maxCredores: Int = 50

    @Column(name = "credores_encontrados")
    var credoresEncontrados: Int = 0

    @Column(name = "processos_visitados")
    var processosVisitados: Int = 0

    @Column(name = "data_inicio")
    var dataInicio: LocalDateTime? = null

    @Column(name = "data_fim")
    var dataFim: LocalDateTime? = null

    @Column(name = "erro_mensagem", columnDefinition = "TEXT")
    var erroMensagem: String? = null
}
