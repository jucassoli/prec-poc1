package br.com.precatorios.repository

import br.com.precatorios.domain.Lead
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface LeadRepository : JpaRepository<Lead, Long> {
    fun findByProspeccaoId(prospeccaoId: Long): List<Lead>

    // SCOR-04: exclude zero-score leads from default listing (Phase 5 Leads API)
    fun findByScoreGreaterThan(score: Int, pageable: Pageable): Page<Lead>
}
