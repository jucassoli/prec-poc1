package br.com.precatorios.repository

import br.com.precatorios.domain.Lead
import org.springframework.data.jpa.repository.JpaRepository

interface LeadRepository : JpaRepository<Lead, Long> {
    fun findByProspeccaoId(prospeccaoId: Long): List<Lead>
}
