package br.com.precatorios.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

object CacheNames {
    const val PROCESSOS = "processos"
    const val PRECATORIOS = "precatorios"
    const val DATAJUD = "datajud"
}

@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val manager = CaffeineCacheManager(CacheNames.PROCESSOS, CacheNames.PRECATORIOS, CacheNames.DATAJUD)
        manager.setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .maximumSize(1000)
                .recordStats()
        )
        return manager
    }
}
