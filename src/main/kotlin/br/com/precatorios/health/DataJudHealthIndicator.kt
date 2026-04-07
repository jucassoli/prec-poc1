package br.com.precatorios.health

import br.com.precatorios.config.ScraperProperties
import br.com.precatorios.scraper.DataJudClient
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class DataJudHealthIndicator(
    private val dataJudClient: DataJudClient,
    private val properties: ScraperProperties
) : HealthIndicator {

    override fun health(): Health {
        return try {
            // D-10: bypass @Cacheable by calling internal method directly
            // Use a match-none query via bogus number -- returns 0 results, minimal server load
            dataJudClient.doBuscarPorNumero("0000000-00.0000.0.00.0000")
            Health.up().withDetail("url", properties.datajud.baseUrl).build()
        } catch (e: Exception) {
            Health.down().withDetail("error", e.javaClass.simpleName).build()
        }
    }
}
