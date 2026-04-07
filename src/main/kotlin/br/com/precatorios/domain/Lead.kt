package br.com.precatorios.domain

import br.com.precatorios.domain.enums.StatusContato
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table(name = "leads")
class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "score")
    var score: Int = 0

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_detalhes", columnDefinition = "jsonb")
    var scoreDetalhes: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status_contato", length = 30)
    var statusContato: StatusContato = StatusContato.NAO_CONTACTADO

    @Column(name = "data_criacao")
    var dataCriacao: LocalDateTime? = null

    @Column(name = "observacao", columnDefinition = "TEXT")
    var observacao: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prospeccao_id")
    var prospeccao: Prospeccao? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credor_id")
    var credor: Credor? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "precatorio_id")
    var precatorio: Precatorio? = null
}
