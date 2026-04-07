package br.com.precatorios.dto

import br.com.precatorios.domain.enums.StatusContato
import jakarta.validation.constraints.NotNull

data class AtualizarStatusContatoRequestDTO(
    @field:NotNull val statusContato: StatusContato,
    val observacao: String? = null
)
