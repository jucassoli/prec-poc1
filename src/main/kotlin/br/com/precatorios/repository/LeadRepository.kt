package br.com.precatorios.repository

import br.com.precatorios.domain.Lead
import br.com.precatorios.domain.enums.StatusContato
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface LeadRepository : JpaRepository<Lead, Long> {
    fun findByProspeccaoId(prospeccaoId: Long): List<Lead>

    // SCOR-04: exclude zero-score leads from default listing (Phase 5 Leads API)
    fun findByScoreGreaterThan(score: Int, pageable: Pageable): Page<Lead>

    @Query(
        value = """
            SELECT l FROM Lead l
            JOIN FETCH l.credor c
            JOIN FETCH l.precatorio p
            WHERE (:scoreMinimo IS NULL OR l.score >= :scoreMinimo)
              AND (:statusContato IS NULL OR l.statusContato = :statusContato)
              AND (:entidadeDevedoraPattern IS NULL OR LOWER(p.entidadeDevedora) LIKE :entidadeDevedoraPattern)
            ORDER BY l.score DESC
        """,
        countQuery = """
            SELECT COUNT(l) FROM Lead l
            JOIN l.precatorio p
            WHERE (:scoreMinimo IS NULL OR l.score >= :scoreMinimo)
              AND (:statusContato IS NULL OR l.statusContato = :statusContato)
              AND (:entidadeDevedoraPattern IS NULL OR LOWER(p.entidadeDevedora) LIKE :entidadeDevedoraPattern)
        """
    )
    fun findLeadsFiltered(
        @Param("scoreMinimo") scoreMinimo: Int?,
        @Param("statusContato") statusContato: StatusContato?,
        @Param("entidadeDevedoraPattern") entidadeDevedoraPattern: String?,
        pageable: Pageable
    ): Page<Lead>
}
