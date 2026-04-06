package br.com.precatorios.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.math.BigDecimal

data class ProspeccaoRequestDTO(
    @field:NotBlank(message = "processoSemente e obrigatorio")
    @field:Pattern(
        regexp = "^[0-9]{7}-[0-9]{2}\\.[0-9]{4}\\.[0-9]\\.[0-9]{2}\\.[0-9]{4}$",
        message = "Formato CNJ invalido. Esperado: NNNNNNN-DD.AAAA.J.TR.OOOO"
    )
    val processoSemente: String = "",

    @field:Min(0) @field:Max(10)
    val profundidadeMaxima: Int? = null,

    @field:Min(1) @field:Max(500)
    val maxCredores: Int? = null,

    val entidadesDevedoras: List<String>? = null,
    val valorMinimo: BigDecimal? = null,
    val apenasAlimentar: Boolean? = null,
    val apenasPendentes: Boolean? = null
)
