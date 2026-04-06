package br.com.precatorios.config

import br.com.precatorios.exception.ProcessoNaoEncontradoException
import br.com.precatorios.exception.TooManyRequestsException
import io.github.resilience4j.core.IntervalBiFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class ResilienceConfig {

    // ---------------------------------------------------------------------------
    // Rate limiters
    // ---------------------------------------------------------------------------

    @Bean
    fun esajRateLimiter(): RateLimiter = RateLimiter.of(
        "esaj",
        RateLimiterConfig.custom()
            .limitForPeriod(1)
            .limitRefreshPeriod(Duration.ofSeconds(2))
            .timeoutDuration(Duration.ofSeconds(5))
            .build()
    )

    @Bean
    fun cacRateLimiter(): RateLimiter = RateLimiter.of(
        "cac",
        RateLimiterConfig.custom()
            .limitForPeriod(1)
            .limitRefreshPeriod(Duration.ofSeconds(2))
            .timeoutDuration(Duration.ofSeconds(5))
            .build()
    )

    @Bean
    fun datajudRateLimiter(): RateLimiter = RateLimiter.of(
        "datajud",
        RateLimiterConfig.custom()
            .limitForPeriod(5)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofSeconds(3))
            .build()
    )

    // ---------------------------------------------------------------------------
    // Retry beans — exponential backoff with 60s pause on HTTP 429
    // ---------------------------------------------------------------------------

    private fun buildRetry(name: String): Retry {
        val intervalBiFunction = IntervalBiFunction<Any> { attempt, result ->
            val throwable = result.swap().getOrNull()
            if (throwable is TooManyRequestsException) {
                Duration.ofSeconds(60).toMillis()
            } else {
                // exponential backoff: 1s * 2^(attempt-1)
                Duration.ofSeconds(1).toMillis() * (1L shl (attempt.toInt() - 1))
            }
        }

        val config = RetryConfig.custom<Any>()
            .maxAttempts(3)
            .intervalBiFunction(intervalBiFunction)
            .ignoreExceptions(ProcessoNaoEncontradoException::class.java)
            .build()

        return Retry.of(name, config)
    }

    @Bean
    fun esajRetry(): Retry = buildRetry("esaj")

    @Bean
    fun cacRetry(): Retry = buildRetry("cac")

    @Bean
    fun datajudRetry(): Retry = buildRetry("datajud")
}
