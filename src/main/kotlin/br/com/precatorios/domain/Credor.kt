package br.com.precatorios.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "credores",
    uniqueConstraints = [UniqueConstraint(columnNames = ["nome", "processo_id"])]
)
class Credor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "nome", nullable = false, length = 500)
    var nome: String = ""

    @Column(name = "cpf_cnpj", length = 20)
    var cpfCnpj: String? = null

    @Column(name = "advogado", length = 500)
    var advogado: String? = null

    @Column(name = "tipo_participacao", length = 50)
    var tipoParticipacao: String? = null

    @Column(name = "data_descoberta")
    var dataDescoberta: LocalDateTime? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processo_id")
    var processo: Processo? = null
}
