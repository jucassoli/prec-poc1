package br.com.precatorios.repository

import br.com.precatorios.domain.Precatorio
import org.springframework.data.jpa.repository.JpaRepository

interface PrecatorioRepository : JpaRepository<Precatorio, Long> {
    fun findByCredorId(credorId: Long): List<Precatorio>
}
