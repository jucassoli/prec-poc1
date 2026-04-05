package br.com.precatorios

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.assertj.core.api.Assertions.assertThat

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ApplicationSmokeTest {

    companion object {
        @Container
        @JvmStatic
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }

    @Autowired
    lateinit var context: ApplicationContext

    @Autowired
    @Qualifier("prospeccaoExecutor")
    lateinit var executor: ThreadPoolTaskExecutor

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `context loads successfully`() {
        assertThat(context).isNotNull()
    }

    @Test
    fun `prospeccaoExecutor bean exists with correct config`() {
        assertThat(executor).isNotNull()
        assertThat(executor.corePoolSize).isEqualTo(2)
        assertThat(executor.maxPoolSize).isEqualTo(4)
        assertThat(executor.threadNamePrefix).isEqualTo("prospeccao-")
    }

    @Test
    fun `swagger UI is accessible via HTTP`() {
        val response = restTemplate.getForEntity("/swagger-ui.html", String::class.java)
        assertThat(response.statusCode).isIn(HttpStatus.OK, HttpStatus.FOUND)
    }
}
