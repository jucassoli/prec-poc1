---
phase: 01-foundation
plan: 01
subsystem: infra
tags: [kotlin, spring-boot, gradle, flyway, postgresql, jsoup, springdoc, testcontainers, mockk, caffeine]

# Dependency graph
requires: []
provides:
  - Gradle Kotlin DSL build with all Phase 1 dependencies resolved
  - Spring Boot 3.5.3 application entry point (PrecatoriosApiApplication.kt)
  - Externalized application.yml with all scraper, prospeccao, springdoc, and actuator config sections
  - application-docker.yml profile override for Docker hostname resolution
  - Multi-stage Dockerfile targeting eclipse-temurin:21
affects: [01-02, 01-03, 01-04, all future phases]

# Tech tracking
tech-stack:
  added:
    - Spring Boot 3.5.3 (web, data-jpa, webflux, validation, cache, actuator)
    - Kotlin 2.2.0 (jvm, plugin.spring, plugin.jpa)
    - Gradle 8.12 (Kotlin DSL)
    - Jsoup 1.21.1
    - Flyway 10+ (flyway-core + flyway-database-postgresql separate artifact)
    - PostgreSQL JDBC driver
    - Caffeine (in-memory cache)
    - SpringDoc OpenAPI 2.8.6 (Swagger UI)
    - MockK 1.14.3
    - Testcontainers 2.0.4 (testcontainers-postgresql, junit-jupiter)
    - spring-boot-testcontainers
  patterns:
    - BOM version overrides via extra["kotlin.version"] and extra["testcontainers.version"]
    - Spring Boot externalized config with ${ENV_VAR:default} syntax for all sensitive/overridable values
    - Docker multi-stage build (JDK for build, JRE for runtime)
    - Spring profile activation via ENTRYPOINT arg (--spring.profiles.active=docker)

key-files:
  created:
    - build.gradle.kts
    - settings.gradle.kts
    - gradle/wrapper/gradle-wrapper.properties
    - gradlew
    - src/main/kotlin/br/com/precatorios/PrecatoriosApiApplication.kt
    - src/main/resources/application.yml
    - src/main/resources/application-docker.yml
    - Dockerfile
    - .gitignore
  modified: []

key-decisions:
  - "Added repositories { mavenCentral() } to build.gradle.kts — required in Gradle Kotlin DSL (plan omitted it, causing FAILED dependency resolution)"
  - "Flyway 10+ requires both flyway-core and flyway-database-postgresql as separate artifacts — applied per plan note"
  - "testcontainers BOM override to 2.0.4 — Spring Boot BOM ships 1.21.2 which conflicts with plan spec"

patterns-established:
  - "Gradle Kotlin DSL always requires explicit repositories { mavenCentral() } block"
  - "Spring Boot config uses YAML with ${ENV_VAR:default} for all externalized secrets and URLs"
  - "Docker entrypoint activates docker profile via --spring.profiles.active=docker"

requirements-completed: [INFRA-01, INFRA-04, INFRA-05]

# Metrics
duration: 5min
completed: 2026-04-05
---

# Phase 01 Plan 01: Project Skeleton Summary

**Gradle Kotlin DSL project with Spring Boot 3.5.3, Kotlin 2.2.0, all Phase 1 dependencies resolved, externalized scraper/datajud/prospeccao config in application.yml, and JVM 21 multi-stage Dockerfile**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-04-05T01:07:00Z
- **Completed:** 2026-04-05T01:12:08Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments

- Gradle 8.12 build with all Phase 1 dependencies (Spring Boot 3.5.3, Kotlin 2.2.0, Jsoup, Flyway, PostgreSQL, Caffeine, SpringDoc, MockK, Testcontainers) compiling cleanly
- Spring Boot application entry point with `@SpringBootApplication` and correct package `br.com.precatorios`
- `application.yml` with all scraper config keys externalized — DATAJUD_API_KEY and all scraper URLs overridable via environment variables
- Multi-stage Dockerfile using eclipse-temurin:21 JDK for build, JRE for runtime

## Task Commits

Each task was committed atomically:

1. **Task 1: Create Gradle build files and project skeleton** - `58fb187` (feat)
2. **Task 2: Create Spring Boot application, configuration files, and Dockerfile** - `7cb90b8` (feat)

## Files Created/Modified

- `build.gradle.kts` - Gradle Kotlin DSL build with Spring Boot 3.5.3, all dependencies, BOM overrides
- `settings.gradle.kts` - Project name `precatorios-api`
- `gradle/wrapper/gradle-wrapper.properties` - Gradle 8.12 wrapper config
- `gradlew` - Gradle wrapper script
- `src/main/kotlin/br/com/precatorios/PrecatoriosApiApplication.kt` - Spring Boot entry point
- `src/main/resources/application.yml` - Full externalized config (scraper, prospeccao, springdoc, actuator)
- `src/main/resources/application-docker.yml` - Docker profile with postgres hostname
- `Dockerfile` - Multi-stage eclipse-temurin:21 build
- `.gitignore` - Gradle/IDE/env exclusions

## Decisions Made

- Added `repositories { mavenCentral() }` to `build.gradle.kts` — the plan's example omitted this block, but Gradle Kotlin DSL requires explicit repository declarations for dependency resolution
- Kept Flyway split as `flyway-core` + `flyway-database-postgresql` per plan note (required since Flyway 10+)
- `testcontainers.version` override to `2.0.4` per plan (Spring Boot BOM ships 1.21.2)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Added missing repositories { mavenCentral() } block to build.gradle.kts**
- **Found during:** Task 1 (Create Gradle build files)
- **Issue:** Plan's build.gradle.kts example omitted the `repositories` block. Without it, all dependencies showed FAILED in Gradle dependency resolution tree.
- **Fix:** Added `repositories { mavenCentral() }` block between `version` declaration and `extra[...]` overrides
- **Files modified:** `build.gradle.kts`
- **Verification:** `./gradlew dependencies --configuration compileClasspath` exits 0 and shows full resolved dependency tree
- **Committed in:** 58fb187 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 bug — missing repositories declaration)
**Impact on plan:** Essential fix for Gradle dependency resolution. No scope creep.

## Issues Encountered

None beyond the missing repositories block described above.

## Known Stubs

None — this plan creates infrastructure only. No data flows or UI rendering.

## Next Phase Readiness

- Build infrastructure is ready for all subsequent Phase 1 plans (01-02 entities/Flyway, 01-03 scrapers, 01-04 async/config)
- `./gradlew compileKotlin` passes — Kotlin compilation confirmed working
- All scraper config keys present in application.yml and overridable via env vars
- Dockerfile ready for Docker Compose integration in later phase

## Self-Check: PASSED

All created files confirmed present on disk. Both task commits (58fb187, 7cb90b8) confirmed in git log.

---
*Phase: 01-foundation*
*Completed: 2026-04-05*
