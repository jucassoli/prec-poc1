package br.com.precatorios.repository

import br.com.precatorios.domain.Credor
import org.springframework.data.jpa.repository.JpaRepository

interface CredorRepository : JpaRepository<Credor, Long> {
    fun existsByNomeAndProcessoId(nome: String, processoId: Long): Boolean
    fun findByNomeAndProcessoId(nome: String, processoId: Long): Credor?
    fun findByProcessoId(processoId: Long): List<Credor>
}
