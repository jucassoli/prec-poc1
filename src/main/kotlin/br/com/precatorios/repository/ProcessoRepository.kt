package br.com.precatorios.repository

import br.com.precatorios.domain.Processo
import org.springframework.data.jpa.repository.JpaRepository

interface ProcessoRepository : JpaRepository<Processo, Long> {
    fun findByNumero(numero: String): Processo?
    fun existsByNumero(numero: String): Boolean
}
