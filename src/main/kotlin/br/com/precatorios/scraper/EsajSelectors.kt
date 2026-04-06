package br.com.precatorios.scraper

/**
 * Centralized CSS selector constants for e-SAJ (TJ-SP) HTML pages.
 *
 * All selectors are based on public e-SAJ HTML analysis (assumed — must be validated
 * in live spike during Plan 02-04). See STATE.md blocker note on selector calibration.
 */
object EsajSelectors {

    // ---------------------------------------------------------------------------
    // URL paths
    // ---------------------------------------------------------------------------

    const val SHOW_PATH = "/cpopg/show.do"
    const val SEARCH_PATH = "/cpopg/search.do"

    // ---------------------------------------------------------------------------
    // Process header fields (ASSUMED — verify in live spike)
    // ---------------------------------------------------------------------------

    const val CLASSE = "#classeProcesso"
    const val ASSUNTO = "#assuntoProcesso"
    const val FORO = "#foroProcesso"
    const val VARA = "#varaProcesso"
    const val JUIZ = "#juizProcesso"
    const val VALOR_ACAO = "#valorAcaoProcesso"

    // ---------------------------------------------------------------------------
    // Parties table
    // ---------------------------------------------------------------------------

    const val PARTES_TABLE = "#tablePartesPrincipais"
    const val PARTE_NOME = ".nomeParteEAdvogado"

    // ---------------------------------------------------------------------------
    // Incidents
    // ---------------------------------------------------------------------------

    const val INCIDENTES_TABLE = "#incidentes"

    // ---------------------------------------------------------------------------
    // Search page
    // ---------------------------------------------------------------------------

    const val SEARCH_RESULT_TABLE = "#listagemDeProcessos"
    const val SEARCH_NUMERO = "#numeroProcesso"
}
