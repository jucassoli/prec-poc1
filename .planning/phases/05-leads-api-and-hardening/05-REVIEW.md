---
phase: 05-leads-api-and-hardening
reviewed: 2026-04-06T23:15:00Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - src/main/kotlin/br/com/precatorios/controller/LeadController.kt
  - src/main/kotlin/br/com/precatorios/dto/AtualizarStatusContatoRequestDTO.kt
  - src/main/kotlin/br/com/precatorios/dto/LeadResponseDTO.kt
  - src/main/kotlin/br/com/precatorios/exception/GlobalExceptionHandler.kt
  - src/main/kotlin/br/com/precatorios/exception/LeadNaoEncontradoException.kt
  - src/main/kotlin/br/com/precatorios/health/DataJudHealthIndicator.kt
  - src/main/kotlin/br/com/precatorios/repository/LeadRepository.kt
  - src/main/kotlin/br/com/precatorios/service/LeadService.kt
  - src/main/kotlin/br/com/precatorios/startup/StaleJobRecoveryRunner.kt
  - src/main/resources/application.yml
  - src/main/resources/db/migration/V3__add_lead_observacao.sql
findings:
  critical: 1
  warning: 3
  info: 2
  total: 6
status: issues_found
---

# Phase 5: Code Review Report

**Reviewed:** 2026-04-06T23:15:00Z
**Depth:** standard
**Files Reviewed:** 11 source files (6 test files also read for context but not flagged)
**Status:** issues_found

## Summary

Phase 5 introduces the Leads API (list/filter/update endpoints), stale job recovery on startup, a DataJud health indicator, and supporting exception handlers. The code is well-structured with clean separation between controller, service, and repository layers. Test coverage is solid.

Key concerns: (1) non-null assertions on nullable JPA entity fields in DTO mapping will crash at runtime if data integrity invariants are violated, (2) the generic exception handler swallows errors without logging, making production debugging difficult, (3) the PATCH endpoint silently clears the observacao field when it is not provided in the request body.

## Critical Issues

### CR-01: NullPointerException from non-null assertions on nullable JPA entity fields

**File:** `src/main/kotlin/br/com/precatorios/dto/LeadResponseDTO.kt:37-38`
**Issue:** `lead.credor!!` and `lead.precatorio!!` use the `!!` (non-null assertion) operator on fields typed as `Credor?` and `Precatorio?` in the `Lead` entity. If a Lead row exists in the database without a corresponding credor or precatorio FK (which the schema allows since there are no NOT NULL constraints visible), this will throw a `KotlinNullPointerException` at runtime, returning an unstructured 500 error. Line 41 (`lead.id!!`) has the same issue but is lower risk since JPA-managed entities always have an ID after persistence.
**Fix:**
```kotlin
fun fromEntity(lead: Lead, objectMapper: ObjectMapper): LeadResponseDTO {
    val scoreDetalhes = lead.scoreDetalhes?.let {
        objectMapper.readValue(it, object : TypeReference<Map<String, Any?>>() {})
    }
    val credorEntity = lead.credor
        ?: throw IllegalStateException("Lead id=${lead.id} has no credor association")
    val precatorioEntity = lead.precatorio
        ?: throw IllegalStateException("Lead id=${lead.id} has no precatorio association")

    return LeadResponseDTO(
        id = lead.id ?: 0L,
        // ... rest unchanged
    )
}
```
This converts a crash-with-no-context into a meaningful error caught by the GlobalExceptionHandler (which handles IllegalArgumentException, and could handle IllegalStateException similarly).

## Warnings

### WR-01: Generic exception handler swallows errors without logging

**File:** `src/main/kotlin/br/com/precatorios/exception/GlobalExceptionHandler.kt:93-101`
**Issue:** The catch-all `handleGeneric` handler returns a generic "Internal server error" message (correctly hiding internals from the client) but does not log the exception. In production, this means unexpected errors will silently disappear -- no stack trace, no error message in logs. Debugging becomes impossible.
**Fix:**
```kotlin
private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

@ExceptionHandler(Exception::class)
fun handleGeneric(
    ex: Exception,
    request: HttpServletRequest
): ResponseEntity<ErrorResponse> {
    log.error("Unhandled exception on {} {}", request.method, request.requestURI, ex)
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse(500, "Internal server error"))
}
```

### WR-02: PATCH endpoint silently clears observacao when not provided

**File:** `src/main/kotlin/br/com/precatorios/service/LeadService.kt:47`
**Issue:** `lead.observacao = request.observacao` unconditionally overwrites the existing observacao. When the client sends `{"statusContato": "CONTACTADO"}` without an `observacao` field, the DTO defaults `observacao` to `null`, which clears any previously stored note. This is likely unintentional data loss -- a PATCH operation should only modify fields that are explicitly provided.
**Fix:**
```kotlin
lead.statusContato = request.statusContato
if (request.observacao != null) {
    lead.observacao = request.observacao
}
```
Or if clearing should be explicit, use a sentinel or separate endpoint. Document the behavior either way.

### WR-03: Hardcoded ORDER BY in JPQL conflicts with Pageable sort

**File:** `src/main/kotlin/br/com/precatorios/repository/LeadRepository.kt:26`
**Issue:** The `findLeadsFiltered` JPQL query contains `ORDER BY l.score DESC` hardcoded in the query string. Spring Data JPA also appends sorting from the `Pageable` parameter. When the controller passes `@PageableDefault(sort = ["score"], direction = Sort.Direction.DESC)` or a user-provided `?sort=dataCriacao,desc`, the resulting SQL may contain conflicting or duplicate ORDER BY clauses, leading to unpredictable sort behavior.
**Fix:** Remove the hardcoded `ORDER BY` from the JPQL and rely solely on the Pageable sort parameter:
```kotlin
@Query(
    value = """
        SELECT l FROM Lead l
        JOIN FETCH l.credor c
        JOIN FETCH l.precatorio p
        WHERE (:scoreMinimo IS NULL OR l.score >= :scoreMinimo)
          AND (:statusContato IS NULL OR l.statusContato = :statusContato)
          AND (:entidadeDevedoraPattern IS NULL OR LOWER(p.entidadeDevedora) LIKE :entidadeDevedoraPattern)
    """,
    countQuery = """..."""
)
fun findLeadsFiltered(...): Page<Lead>
```
The default sort is already configured via `@PageableDefault` on the controller.

## Info

### IN-01: Hardcoded database credentials in application.yml

**File:** `src/main/resources/application.yml:9-10`
**Issue:** Database username and password are hardcoded as `precatorios`/`precatorios`. The DataJud API key also has a default fallback value on line 37. While this is a POC and the values are likely for local development, they should be externalized via environment variables for any non-local deployment.
**Fix:** Use environment variable placeholders:
```yaml
spring:
  datasource:
    username: ${DB_USERNAME:precatorios}
    password: ${DB_PASSWORD:precatorios}
```

### IN-02: Health indicator makes unbounded API calls on every check

**File:** `src/main/kotlin/br/com/precatorios/health/DataJudHealthIndicator.kt:19`
**Issue:** Each health check invocation makes a real HTTP call to the DataJud API via `doBuscarPorNumero`. Spring Actuator health endpoints can be polled frequently (by monitoring tools, load balancers, Kubernetes probes). This could consume DataJud rate limit quota. Consider caching the health result for a short TTL (e.g., 30-60 seconds).
**Fix:** Add a time-based cache or use Spring's `@Cacheable` with a short TTL, or configure the health check group to only run on specific actuator paths.

---

_Reviewed: 2026-04-06T23:15:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
