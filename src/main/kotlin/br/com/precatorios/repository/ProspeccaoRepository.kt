package br.com.precatorios.repository

import br.com.precatorios.domain.Prospeccao
import br.com.precatorios.domain.enums.StatusProspeccao
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ProspeccaoRepository : JpaRepository<Prospeccao, Long> {
    fun findByStatus(status: StatusProspeccao, pageable: Pageable): Page<Prospeccao>
}
