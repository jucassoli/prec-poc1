package br.com.precatorios.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table(name = "processos")
class Processo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "numero", unique = true, nullable = false, length = 25)
    var numero: String = ""

    @Column(name = "classe", length = 200)
    var classe: String? = null

    @Column(name = "assunto", length = 500)
    var assunto: String? = null

    @Column(name = "foro", length = 200)
    var foro: String? = null

    @Column(name = "vara", length = 200)
    var vara: String? = null

    @Column(name = "juiz", length = 300)
    var juiz: String? = null

    @Column(name = "valor_acao", length = 50)
    var valorAcao: String? = null

    @Column(name = "status", length = 50)
    var status: String? = "PENDENTE"

    @Column(name = "data_coleta")
    var dataColeta: LocalDateTime? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_brutos", columnDefinition = "jsonb")
    var dadosBrutos: String? = null

    @OneToMany(mappedBy = "processo", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var credores: MutableList<Credor> = mutableListOf()
}
