package br.com.precatorios.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Precatorios API")
                .description("TJ-SP precatorio lead prospecting API")
                .version("1.0.0")
                .contact(
                    Contact()
                        .name("Precatorios Team")
                )
        )
}
