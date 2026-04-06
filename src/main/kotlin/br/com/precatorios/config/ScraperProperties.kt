package br.com.precatorios.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "scraper")
data class ScraperProperties(
    val esaj: EsajProps = EsajProps(),
    val precatorioPortal: CacProps = CacProps(),
    val datajud: DataJudProps = DataJudProps()
) {
    data class EsajProps(
        val baseUrl: String = "",
        val delayMs: Long = 2000,
        val timeoutMs: Int = 30000,
        val userAgent: String = "",
        val maxRetries: Int = 3
    )

    data class CacProps(
        val baseUrl: String = "",
        val delayMs: Long = 2000,
        val timeoutMs: Int = 30000
    )

    data class DataJudProps(
        val baseUrl: String = "",
        val endpointTjsp: String = "",
        val apiKey: String = "",
        val timeoutMs: Int = 30000,
        val maxResultadosPorPagina: Int = 100
    )
}
