package br.com.precatorios.controller

import br.com.precatorios.dto.PrecatorioResponseDTO
import br.com.precatorios.scraper.CacScraper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/precatorio")
@Validated
@Tag(name = "Precatórios", description = "Consulta de precatórios no portal CAC/SCP do TJ-SP. Retorna dados completos do precatório incluindo valor original, valor atualizado, entidade devedora, natureza (alimentar/comum), status de pagamento, posição cronológica e data de expedição.")
class PrecatorioController(private val cacScraper: CacScraper) {

    @GetMapping("/{numero}")
    @Operation(
        summary = "Consultar dados de precatório no CAC/SCP",
        description = "Busca informações detalhadas de um precatório no portal CAC/SCP do TJ-SP pelo número do precatório. " +
            "Retorna valor original, valor atualizado, entidade devedora, natureza, status de pagamento, " +
            "posição cronológica e data de expedição. O campo 'dadosCompletos' indica se todos os campos foram extraídos " +
            "com sucesso; caso contrário, 'missingFields' lista os campos que não puderam ser obtidos."
    )
    fun getPrecatorio(
        @PathVariable numero: String
    ): ResponseEntity<PrecatorioResponseDTO> {
        val scraped = cacScraper.fetchPrecatorio(numero)
        return ResponseEntity.ok(
            PrecatorioResponseDTO(
                numeroPrecatorio = scraped.numeroPrecatorio,
                numeroProcesso = scraped.numeroProcesso,
                entidadeDevedora = scraped.entidadeDevedora,
                valorOriginal = scraped.valorOriginal,
                valorAtualizado = scraped.valorAtualizado,
                natureza = scraped.natureza,
                statusPagamento = scraped.statusPagamento,
                posicaoCronologica = scraped.posicaoCronologica,
                dataExpedicao = scraped.dataExpedicao,
                missingFields = scraped.missingFields,
                dadosCompletos = scraped.missingFields.isEmpty()
            )
        )
    }
}
