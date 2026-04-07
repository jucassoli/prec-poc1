package br.com.precatorios.controller

import br.com.precatorios.domain.enums.StatusContato
import br.com.precatorios.dto.AtualizarStatusContatoRequestDTO
import br.com.precatorios.dto.LeadResponseDTO
import br.com.precatorios.service.LeadService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "Leads", description = "Gestão de leads — listagem com filtros, paginação e atualização de status de contato. Leads são credores qualificados descobertos durante a prospecção, cada um com score de 0 a 100.")
class LeadController(private val leadService: LeadService) {

    @GetMapping
    @Operation(
        summary = "Listar leads com filtros opcionais",
        description = "Retorna leads paginados com ordenação padrão por score decrescente. " +
            "Leads com score zero são excluídos por padrão (use incluirZero=true para incluí-los). " +
            "Filtros disponíveis: scoreMinimo (nota mínima), statusContato (NAO_CONTACTADO, EM_CONTATO, CONVERTIDO, DESCARTADO), " +
            "entidadeDevedora (busca parcial por nome da entidade). Todos os filtros operam com lógica AND."
    )
    fun listar(
        @RequestParam(required = false) scoreMinimo: Int?,
        @RequestParam(required = false) statusContato: StatusContato?,
        @RequestParam(required = false) entidadeDevedora: String?,
        @RequestParam(defaultValue = "false") incluirZero: Boolean,
        @PageableDefault(size = 20, sort = ["score"], direction = Sort.Direction.DESC) pageable: Pageable
    ): ResponseEntity<Page<LeadResponseDTO>> {
        val result = leadService.listarLeads(scoreMinimo, statusContato, entidadeDevedora, incluirZero, pageable)
        return ResponseEntity.ok(result)
    }

    @PatchMapping("/{id}/status")
    @Operation(
        summary = "Atualizar status de contato do lead",
        description = "Atualiza o status de contato do lead (NAO_CONTACTADO, EM_CONTATO, CONVERTIDO, DESCARTADO) " +
            "e permite adicionar uma observação textual opcional. Retorna 404 caso o lead não seja encontrado."
    )
    fun atualizarStatus(
        @PathVariable id: Long,
        @RequestBody @Valid request: AtualizarStatusContatoRequestDTO
    ): ResponseEntity<LeadResponseDTO> {
        val updated = leadService.atualizarStatusContato(id, request)
        return ResponseEntity.ok(updated)
    }
}
