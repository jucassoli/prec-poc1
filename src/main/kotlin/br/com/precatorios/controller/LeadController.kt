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
@Tag(name = "Leads", description = "Lead management — list, filter, and update contact status")
class LeadController(private val leadService: LeadService) {

    @GetMapping
    @Operation(
        summary = "List leads with optional filters",
        description = "Returns paginated leads. Default sort: score DESC. Zero-score leads excluded unless incluirZero=true."
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
        summary = "Update lead contact status",
        description = "Updates statusContato and optional observacao note"
    )
    fun atualizarStatus(
        @PathVariable id: Long,
        @RequestBody @Valid request: AtualizarStatusContatoRequestDTO
    ): ResponseEntity<LeadResponseDTO> {
        val updated = leadService.atualizarStatusContato(id, request)
        return ResponseEntity.ok(updated)
    }
}
