package br.com.precatorios.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableAsync
class AsyncConfig {

    @Bean("prospeccaoExecutor")
    fun prospeccaoExecutor(): ThreadPoolTaskExecutor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 25
            setThreadNamePrefix("prospeccao-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(120)
            initialize()
        }
    }
}
