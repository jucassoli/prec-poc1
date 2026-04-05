package br.com.precatorios.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "precatorios")
class Precatorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "numero_precatorio", length = 30)
    var numeroPrecatorio: String? = null

    @Column(name = "numero_processo", length = 25)
    var numeroProcesso: String? = null

    @Column(name = "entidade_devedora", length = 300)
    var entidadeDevedora: String? = null

    @Column(name = "valor_original", precision = 15, scale = 2)
    var valorOriginal: BigDecimal? = null

    @Column(name = "valor_atualizado", precision = 15, scale = 2)
    var valorAtualizado: BigDecimal? = null

    @Column(name = "natureza", length = 20)
    var natureza: String? = null

    @Column(name = "status_pagamento", length = 30)
    var statusPagamento: String? = null

    @Column(name = "posicao_cronologica")
    var posicaoCronologica: Int? = null

    @Column(name = "data_expedicao")
    var dataExpedicao: LocalDate? = null

    @Column(name = "data_coleta")
    var dataColeta: LocalDateTime? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_brutos", columnDefinition = "jsonb")
    var dadosBrutos: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credor_id")
    var credor: Credor? = null
}
