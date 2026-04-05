package br.com.precatorios.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import jakarta.servlet.http.HttpServletRequest

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ProcessoNaoEncontradoException::class)
    fun handleProcessoNaoEncontrado(
        ex: ProcessoNaoEncontradoException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(404, ex.message ?: "Processo nao encontrado"))
    }

    @ExceptionHandler(ScrapingException::class)
    fun handleScrapingException(
        ex: ScrapingException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse(503, ex.message ?: "Erro ao acessar fonte de dados"))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(500, "Internal server error"))
    }
}
