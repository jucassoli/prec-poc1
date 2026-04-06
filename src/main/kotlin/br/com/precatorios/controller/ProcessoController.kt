package br.com.precatorios.controller

import br.com.precatorios.dto.BuscaProcessoItemDTO
import br.com.precatorios.dto.BuscaProcessoResponseDTO
import br.com.precatorios.dto.IncidenteDTO
import br.com.precatorios.dto.ParteDTO
import br.com.precatorios.dto.ProcessoResponseDTO
import br.com.precatorios.scraper.EsajScraper
import br.com.precatorios.scraper.ProcessoScraped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/processo")
@Validated
@Tag(name = "Processos", description = "e-SAJ process lookup")
class ProcessoController(private val esajScraper: EsajScraper) {

    @GetMapping("/{numero}")
    @Operation(summary = "Fetch process details from e-SAJ")
    fun getProcesso(
        @PathVariable
        @Pattern(
            regexp = "^[0-9]{7}-[0-9]{2}\\.[0-9]{4}\\.[0-9]\\.[0-9]{2}\\.[0-9]{4}$",
            message = "Formato CNJ invalido. Esperado: NNNNNNN-DD.AAAA.J.TR.OOOO"
        )
        numero: String
    ): ResponseEntity<ProcessoResponseDTO> {
        val scraped = esajScraper.fetchProcesso(numero)
        return ResponseEntity.ok(toDTO(scraped))
    }

    @GetMapping("/buscar")
    @Operation(summary = "Search processes by name in e-SAJ")
    fun buscar(
        @RequestParam(required = false) @Size(min = 3, message = "Nome deve ter pelo menos 3 caracteres") nome: String?,
        @RequestParam(required = false) cpf: String?,
        @RequestParam(required = false) numero: String?
    ): ResponseEntity<BuscaProcessoResponseDTO> {
        val searchTerm = nome ?: cpf ?: numero
            ?: throw IllegalArgumentException("Informe ao menos um parametro: nome, cpf ou numero")
        val results = esajScraper.buscarPorNome(searchTerm)
        return ResponseEntity.ok(
            BuscaProcessoResponseDTO(
                resultados = results.map { BuscaProcessoItemDTO(it.numero, it.classe, it.assunto, it.foro) },
                total = results.size
            )
        )
    }

    private fun toDTO(scraped: ProcessoScraped): ProcessoResponseDTO {
        return ProcessoResponseDTO(
            numero = scraped.numero,
            classe = scraped.classe,
            assunto = scraped.assunto,
            foro = scraped.foro,
            vara = scraped.vara,
            juiz = scraped.juiz,
            valorAcao = scraped.valorAcao,
            partes = scraped.partes.map { ParteDTO(it.nome, it.tipo, it.advogado) },
            incidentes = scraped.incidentes.map { IncidenteDTO(it.numero, it.descricao, it.link) },
            missingFields = scraped.missingFields,
            dadosCompletos = scraped.missingFields.isEmpty()
        )
    }
}
