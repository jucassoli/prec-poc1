package br.com.precatorios.controller

import br.com.precatorios.dto.DataJudBuscarRequestDTO
import br.com.precatorios.dto.DataJudBuscarResponseDTO
import br.com.precatorios.dto.DataJudResultadoDTO
import br.com.precatorios.scraper.DataJudClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/datajud")
@Tag(name = "DataJud", description = "Proxy para a API pública do DataJud (CNJ). Permite consultar metadados de processos judiciais via Elasticsearch do Conselho Nacional de Justiça, incluindo classe, assunto, órgão julgador e data de ajuizamento.")
class DataJudController(private val dataJudClient: DataJudClient) {

    @PostMapping("/buscar")
    @Operation(
        summary = "Buscar processos no DataJud por número ou município",
        description = "Consulta a API Elasticsearch do DataJud (CNJ) para obter metadados de processos judiciais. " +
            "Aceita busca por número do processo (formato CNJ) ou código do município IBGE. " +
            "Retorna classe processual, assunto, órgão julgador e data de ajuizamento de cada resultado encontrado. " +
            "É obrigatório informar pelo menos um dos parâmetros: numeroProcesso ou codigoMunicipioIBGE."
    )
    fun buscar(@RequestBody request: DataJudBuscarRequestDTO): ResponseEntity<DataJudBuscarResponseDTO> {
        val result = when {
            request.numeroProcesso != null -> dataJudClient.buscarPorNumeroProcesso(request.numeroProcesso)
            request.codigoMunicipioIBGE != null -> dataJudClient.buscarPorMunicipio(request.codigoMunicipioIBGE)
            else -> throw IllegalArgumentException("Informe numeroProcesso ou codigoMunicipioIBGE")
        }
        return ResponseEntity.ok(
            DataJudBuscarResponseDTO(
                total = result.total,
                resultados = result.hits.map {
                    DataJudResultadoDTO(it.numeroProcesso, it.classe, it.assunto, it.orgaoJulgador, it.dataAjuizamento)
                }
            )
        )
    }
}
