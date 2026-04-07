package br.com.precatorios.controller

import br.com.precatorios.domain.enums.StatusProspeccao
import br.com.precatorios.dto.LeadSummaryDTO
import br.com.precatorios.dto.ProspeccaoIniciadaDTO
import br.com.precatorios.dto.ProspeccaoListItemDTO
import br.com.precatorios.dto.ProspeccaoRequestDTO
import br.com.precatorios.dto.ProspeccaoStatusDTO
import br.com.precatorios.engine.BfsProspeccaoEngine
import br.com.precatorios.exception.ProspeccaoNaoEncontradaException
import br.com.precatorios.repository.LeadRepository
import br.com.precatorios.repository.ProspeccaoRepository
import br.com.precatorios.service.ProspeccaoService
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/prospeccao")
@Tag(name = "Prospecção", description = "Motor de prospecção BFS (Busca em Largura). Permite iniciar prospecções recursivas a partir de um processo-semente, acompanhar o progresso em tempo real e listar prospecções anteriores. Cada prospecção descobre co-credores, consulta precatórios e gera leads qualificados automaticamente.")
class ProspeccaoController(
    private val prospeccaoService: ProspeccaoService,
    private val bfsProspeccaoEngine: BfsProspeccaoEngine,
    private val prospeccaoRepository: ProspeccaoRepository,
    private val leadRepository: LeadRepository,
    private val objectMapper: ObjectMapper
) {

    @PostMapping
    @Operation(
        summary = "Iniciar prospecção BFS a partir de processo-semente",
        description = "Cria e inicia uma prospecção assíncrona usando busca em largura (BFS). " +
            "A partir do número do processo-semente, o sistema descobre recursivamente co-credores, " +
            "consulta precatórios no CAC/SCP, enriquece dados via DataJud e gera leads com score automático. " +
            "Retorna HTTP 202 (Accepted) com o ID da prospecção para acompanhamento via polling. " +
            "Parâmetros opcionais permitem limitar profundidade, número máximo de credores, " +
            "filtrar por entidade devedora, valor mínimo, natureza alimentar e status de pagamento pendente."
    )
    fun iniciar(@RequestBody @Valid request: ProspeccaoRequestDTO): ResponseEntity<ProspeccaoIniciadaDTO> {
        val prospeccao = prospeccaoService.criar(
            request.processoSemente,
            request.profundidadeMaxima,
            request.maxCredores
        )
        bfsProspeccaoEngine.start(
            prospeccaoId = prospeccao.id!!,
            processoSemente = request.processoSemente,
            profundidadeMaxima = request.profundidadeMaxima ?: prospeccao.profundidadeMax,
            maxCredores = request.maxCredores ?: prospeccao.maxCredores,
            entidadesDevedoras = request.entidadesDevedoras,
            valorMinimo = request.valorMinimo,
            apenasAlimentar = request.apenasAlimentar,
            apenasPendentes = request.apenasPendentes
        )
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(ProspeccaoIniciadaDTO(prospeccaoId = prospeccao.id!!))
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Consultar status de prospecção",
        description = "Retorna o status atual da prospecção, incluindo contadores de progresso " +
            "(processos visitados, credores encontrados, leads qualificados). " +
            "Quando a prospecção está EM_ANDAMENTO, a resposta inclui o header Retry-After=10 para orientar o polling. " +
            "Quando CONCLUIDA, inclui a lista completa de leads qualificados com score e detalhes do precatório."
    )
    fun getStatus(@PathVariable id: Long): ResponseEntity<ProspeccaoStatusDTO> {
        val prospeccao = prospeccaoRepository.findById(id)
            .orElseThrow { ProspeccaoNaoEncontradaException(id) }

        val leads = if (prospeccao.status == StatusProspeccao.CONCLUIDA) {
            leadRepository.findByProspeccaoId(id).map { lead ->
                LeadSummaryDTO(
                    id = lead.id!!,
                    score = lead.score,
                    scoreDetalhes = lead.scoreDetalhes?.let {
                        objectMapper.readValue(it, object : TypeReference<Map<String, Any?>>() {})
                    },
                    credorNome = lead.credor?.nome,
                    credorCpfCnpj = lead.credor?.cpfCnpj,
                    precatorioNumero = lead.precatorio?.numeroPrecatorio,
                    entidadeDevedora = lead.precatorio?.entidadeDevedora,
                    valorAtualizado = lead.precatorio?.valorAtualizado,
                    natureza = lead.precatorio?.natureza,
                    statusPagamento = lead.precatorio?.statusPagamento,
                    statusContato = lead.statusContato.name,
                    dataCriacao = lead.dataCriacao
                )
            }
        } else null

        val dto = ProspeccaoStatusDTO(
            id = prospeccao.id!!,
            processoSemente = prospeccao.processoSemente,
            status = prospeccao.status.name,
            profundidadeMaxima = prospeccao.profundidadeMax,
            maxCredores = prospeccao.maxCredores,
            processosVisitados = prospeccao.processosVisitados,
            credoresEncontrados = prospeccao.credoresEncontrados,
            leadsQualificados = prospeccao.leadsQualificados,
            dataInicio = prospeccao.dataInicio,
            dataFim = prospeccao.dataFim,
            erroMensagem = prospeccao.erroMensagem,
            leads = leads
        )

        return if (prospeccao.status == StatusProspeccao.EM_ANDAMENTO) {
            ResponseEntity.ok()
                .header("Retry-After", "10")
                .body(dto)
        } else {
            ResponseEntity.ok(dto)
        }
    }

    @GetMapping
    @Operation(
        summary = "Listar todas as prospecções",
        description = "Retorna lista paginada de todas as prospecções realizadas, com filtro opcional por status " +
            "(EM_ANDAMENTO, CONCLUIDA, ERRO). Cada item inclui contadores de progresso e datas de início/fim."
    )
    fun listar(
        @RequestParam(required = false) status: StatusProspeccao?,
        pageable: Pageable
    ): ResponseEntity<Page<ProspeccaoListItemDTO>> {
        val page = if (status != null) {
            prospeccaoRepository.findByStatus(status, pageable)
        } else {
            prospeccaoRepository.findAll(pageable)
        }
        return ResponseEntity.ok(page.map { p ->
            ProspeccaoListItemDTO(
                id = p.id!!,
                processoSemente = p.processoSemente,
                status = p.status.name,
                processosVisitados = p.processosVisitados,
                credoresEncontrados = p.credoresEncontrados,
                leadsQualificados = p.leadsQualificados,
                dataInicio = p.dataInicio,
                dataFim = p.dataFim
            )
        })
    }
}
