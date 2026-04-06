package br.com.precatorios.config

import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Duration

@SpringBootTest
@Testcontainers
class ResilienceConfigTest {

    companion object {
        @Container
        @JvmStatic
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }

    @Autowired
    @Qualifier("esajRateLimiter")
    lateinit var esajRateLimiter: RateLimiter

    @Autowired
    @Qualifier("cacRateLimiter")
    lateinit var cacRateLimiter: RateLimiter

    @Autowired
    @Qualifier("datajudRateLimiter")
    lateinit var datajudRateLimiter: RateLimiter

    @Autowired
    @Qualifier("esajRetry")
    lateinit var esajRetry: Retry

    @Autowired
    @Qualifier("cacRetry")
    lateinit var cacRetry: Retry

    @Autowired
    @Qualifier("datajudRetry")
    lateinit var datajudRetry: Retry

    @Test
    fun `esajRateLimiter has limitForPeriod 1 and limitRefreshPeriod 2s`() {
        val config = esajRateLimiter.rateLimiterConfig
        assertThat(config.limitForPeriod).isEqualTo(1)
        assertThat(config.limitRefreshPeriod).isEqualTo(Duration.ofSeconds(2))
    }

    @Test
    fun `cacRateLimiter has limitForPeriod 1 and limitRefreshPeriod 2s`() {
        val config = cacRateLimiter.rateLimiterConfig
        assertThat(config.limitForPeriod).isEqualTo(1)
        assertThat(config.limitRefreshPeriod).isEqualTo(Duration.ofSeconds(2))
    }

    @Test
    fun `datajudRateLimiter has limitForPeriod 5 and limitRefreshPeriod 1s`() {
        val config = datajudRateLimiter.rateLimiterConfig
        assertThat(config.limitForPeriod).isEqualTo(5)
        assertThat(config.limitRefreshPeriod).isEqualTo(Duration.ofSeconds(1))
    }

    @Test
    fun `esajRetry has maxAttempts 3`() {
        assertThat(esajRetry.retryConfig.maxAttempts).isEqualTo(3)
    }

    @Test
    fun `cacRetry has maxAttempts 3`() {
        assertThat(cacRetry.retryConfig.maxAttempts).isEqualTo(3)
    }

    @Test
    fun `datajudRetry has maxAttempts 3`() {
        assertThat(datajudRetry.retryConfig.maxAttempts).isEqualTo(3)
    }
}
