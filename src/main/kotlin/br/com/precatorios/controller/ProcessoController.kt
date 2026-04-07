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
@Tag(name = "Processos", description = "Consulta de processos no e-SAJ do TJ-SP. Permite buscar detalhes de um processo pelo número CNJ ou pesquisar processos por nome, CPF ou número. Retorna partes envolvidas, incidentes (precatórios vinculados), classe, assunto, foro, vara e juiz.")
class ProcessoController(private val esajScraper: EsajScraper) {

    @GetMapping("/{numero}")
    @Operation(
        summary = "Consultar detalhes de processo no e-SAJ",
        description = "Busca informações completas de um processo no portal e-SAJ do TJ-SP pelo número no formato CNJ " +
            "(NNNNNNN-DD.AAAA.J.TR.OOOO). Retorna classe processual, assunto, foro, vara, juiz, valor da ação, " +
            "lista de partes com seus advogados e incidentes vinculados (incluindo precatórios). " +
            "O campo 'dadosCompletos' indica se todos os campos foram extraídos com sucesso."
    )
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
    @Operation(
        summary = "Pesquisar processos por nome, CPF ou número no e-SAJ",
        description = "Realiza busca no e-SAJ do TJ-SP por nome da parte (mínimo 3 caracteres), CPF ou número do processo. " +
            "É obrigatório informar pelo menos um parâmetro de busca. Retorna lista de processos encontrados " +
            "com número, classe, assunto e foro de cada resultado."
    )
    fun buscar(
        @RequestParam(required = false) @Size(min = 3, message = "Nome deve ter pelo menos 3 caracteres") nome: String?,
        @RequestParam(required = false) cpf: String?,
        @RequestParam(required = false) numero: String?
    ): ResponseEntity<BuscaProcessoResponseDTO> {
        val results = when {
            numero != null -> esajScraper.buscarPorNumero(numero)
            cpf != null -> esajScraper.buscarPorCpf(cpf)
            nome != null -> esajScraper.buscarPorNome(nome)
            else -> throw IllegalArgumentException("Informe ao menos um parametro: nome, cpf ou numero")
        }
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
