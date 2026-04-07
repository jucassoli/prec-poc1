package br.com.precatorios.exception

import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()
    private val request = mockk<HttpServletRequest>(relaxed = true)

    @Test
    fun `TooManyRequestsException returns 429`() {
        val response = handler.handleTooManyRequests(TooManyRequestsException("Rate limit exceeded"), request)
        assertThat(response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(response.body!!.status).isEqualTo(429)
        assertThat(response.body!!.message).contains("Rate limit exceeded")
        assertThat(response.body!!.timestamp).isNotNull()
    }

    @Test
    fun `HttpMessageNotReadableException returns 400`() {
        val ex = HttpMessageNotReadableException("bad json")
        val response = handler.handleHttpMessageNotReadable(ex, request)
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!.status).isEqualTo(400)
        assertThat(response.body!!.message).isEqualTo("Corpo da requisicao invalido")
        assertThat(response.body!!.timestamp).isNotNull()
    }

    @Test
    fun `ScrapingException returns 503`() {
        val response = handler.handleScrapingException(ScrapingException("scrape fail"), request)
        assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        assertThat(response.body!!.status).isEqualTo(503)
        assertThat(response.body!!.timestamp).isNotNull()
    }

    @Test
    fun `ProcessoNaoEncontradoException returns 404`() {
        val response = handler.handleProcessoNaoEncontrado(ProcessoNaoEncontradoException("1234"), request)
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body!!.status).isEqualTo(404)
        assertThat(response.body!!.timestamp).isNotNull()
    }

    @Test
    fun `ProspeccaoNaoEncontradaException returns 404`() {
        val response = handler.handleProspeccaoNaoEncontrada(ProspeccaoNaoEncontradaException(99L), request)
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body!!.status).isEqualTo(404)
        assertThat(response.body!!.timestamp).isNotNull()
    }

    @Test
    fun `ConstraintViolationException returns 400`() {
        val violations = mockk<jakarta.validation.ConstraintViolationException>(relaxed = true)
        io.mockk.every { violations.constraintViolations } returns emptySet()
        val response = handler.handleConstraintViolation(violations)
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!.status).isEqualTo(400)
        assertThat(response.body!!.timestamp).isNotNull()
    }

    @Test
    fun `IllegalArgumentException returns 400`() {
        val response = handler.handleIllegalArgument(IllegalArgumentException("bad argument"))
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!.status).isEqualTo(400)
        assertThat(response.body!!.message).contains("bad argument")
        assertThat(response.body!!.timestamp).isNotNull()
    }

    @Test
    fun `MethodArgumentNotValidException returns 400`() {
        val bindingResult = mockk<org.springframework.validation.BindingResult>(relaxed = true)
        io.mockk.every { bindingResult.fieldErrors } returns emptyList()
        val ex = org.springframework.web.bind.MethodArgumentNotValidException(
            mockk(relaxed = true),
            bindingResult
        )
        val response = handler.handleMethodArgumentNotValid(ex)
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!.status).isEqualTo(400)
        assertThat(response.body!!.timestamp).isNotNull()
    }

    @Test
    fun `generic Exception returns 500 without stack trace`() {
        val response = handler.handleGeneric(RuntimeException("secret error details"), request)
        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body!!.status).isEqualTo(500)
        assertThat(response.body!!.message).isEqualTo("Internal server error")
        assertThat(response.body!!.message).doesNotContain("secret error details")
        assertThat(response.body!!.timestamp).isNotNull()
    }
}
