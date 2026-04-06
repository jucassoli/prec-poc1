# Phase 4: BFS Prospection Engine and Prospection API - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-06
**Phase:** 04-bfs-prospection-engine-and-prospection-api
**Areas discussed:** BFS traversal strategy, Failure isolation, Prospection filters, API response contract

---

## BFS Traversal Strategy

### Creditor deduplication

| Option | Description | Selected |
|--------|-------------|----------|
| Deduplicate by name | Same creditor name = one Lead linking to first encounter | |
| One lead per (creditor, precatorio) pair | Same creditor can produce multiple leads from different processes | ✓ |
| You decide | Claude picks based on data model | |

**User's choice:** One lead per (creditor, precatorio) pair
**Notes:** Richer data, matches Lead entity FK model (credor_id + precatorio_id).

### Queue ordering

| Option | Description | Selected |
|--------|-------------|----------|
| Level-by-level BFS | Process all nodes at current depth before going deeper | ✓ |
| Continuous FIFO | Simpler but harder to enforce depth limits | |
| You decide | Claude picks | |

**User's choice:** Level-by-level BFS (Recommended)

### BFS expansion source

| Option | Description | Selected |
|--------|-------------|----------|
| Name search (buscarPorNome) | Seed -> creditors -> name search -> other processes -> repeat | ✓ |
| Only follow precatorio incidents | Narrower, faster, fewer leads | |
| Both | Maximum coverage, most requests | |

**User's choice:** Yes — name search expansion (Recommended)

### Search result cap

| Option | Description | Selected |
|--------|-------------|----------|
| Cap at 10 per creditor | Prevents common name explosion | ✓ |
| Cap at 5 per creditor | More conservative | |
| No cap | Maximum coverage, risk of runaway | |
| You decide | Claude picks sensible default | |

**User's choice:** Cap at 10 (Recommended)

---

## Failure Isolation

### Failure handling during BFS

| Option | Description | Selected |
|--------|-------------|----------|
| Log, skip, continue | Record failure, skip branch, keep processing | ✓ |
| Retry once then skip | One extra retry before giving up | |
| You decide | Claude picks based on Resilience4j setup | |

**User's choice:** Log, skip, continue (Recommended)

### Final status with partial failures

| Option | Description | Selected |
|--------|-------------|----------|
| CONCLUIDA with erroMensagem | Standard completion, failures in log | ✓ |
| CONCLUIDA_PARCIAL | New enum value for partial success | |
| You decide | Claude picks | |

**User's choice:** CONCLUIDA with failures in erroMensagem (Recommended)

---

## Prospection Filters

### Filter timing

| Option | Description | Selected |
|--------|-------------|----------|
| During BFS — prune early | Skip scoring/persisting non-matching leads | ✓ |
| After BFS — score everything | Filters are query-time only | |
| Hybrid — prune but persist with flag | Fast runs, data preserved | |

**User's choice:** During BFS — prune early (Recommended)

### Filter scope on BFS expansion

| Option | Description | Selected |
|--------|-------------|----------|
| Filters only affect lead creation | All branches explored regardless | ✓ |
| Filters also prune BFS expansion | Skip exploring filtered creditors' processes | |

**User's choice:** Filters only affect lead creation (Recommended)

---

## API Response Contract

### Lead list format

| Option | Description | Selected |
|--------|-------------|----------|
| Embedded in status response | leads[] inline when CONCLUIDA | ✓ |
| Separate endpoint | GET /prospeccao/{id}/leads | |
| You decide | Claude picks | |

**User's choice:** Embedded (Recommended)

### Progress info

| Option | Description | Selected |
|--------|-------------|----------|
| Counter-based | processosVisitados, credoresEncontrados, leadsQualificados | ✓ |
| Counter + percentage | Add estimated percentage | |
| You decide | Claude picks | |

**User's choice:** Counter-based (Recommended)

### Request body shape

| Option | Description | Selected |
|--------|-------------|----------|
| Flat JSON | All fields at top level, only processoSemente required | ✓ |
| Nested JSON | Separate config and filtros objects | |
| You decide | Claude picks | |

**User's choice:** Flat JSON (Recommended)

---

## Claude's Discretion

- BfsProspeccaoEngine internal implementation details
- DTO structure and validation annotations
- CNJ process number regex
- erroMensagem aggregation format
- Search result ordering from buscarPorNome

## Deferred Ideas

None — discussion stayed within phase scope.
