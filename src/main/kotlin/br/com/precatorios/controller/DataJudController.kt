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
@Tag(name = "DataJud", description = "DataJud CNJ API proxy")
class DataJudController(private val dataJudClient: DataJudClient) {

    @PostMapping("/buscar")
    @Operation(summary = "Search DataJud by process number or municipality")
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
