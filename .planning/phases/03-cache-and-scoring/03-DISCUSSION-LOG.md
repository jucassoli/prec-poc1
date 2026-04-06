# Phase 3: Cache and Scoring - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-06
**Phase:** 03-cache-and-scoring
**Areas discussed:** Scoring thresholds, Cache scope, Score details format, Missing data handling

---

## Scoring Thresholds

### Value Criterion (30pts)

| Option | Description | Selected |
|--------|-------------|----------|
| Tiered brackets | Define value ranges in application.yml (>R$1M=30, R$500k-1M=22, etc.) | ✓ |
| Linear interpolation | Linear scale between configurable min/max bounds | |
| You decide | Claude picks during implementation | |

**User's choice:** Tiered brackets
**Notes:** None

### Debtor Entity Criterion (25pts)

| Option | Description | Selected |
|--------|-------------|----------|
| Entity whitelist in config | Map specific entity names to scores in application.yml | ✓ |
| Category-based tiers | Group by type (Estado, Municipio grande, Autarquia, Outros) | |
| You decide | Claude picks based on TJ-SP patterns | |

**User's choice:** Entity whitelist in config
**Notes:** None

### Payment Status Criterion (20pts)

| Option | Description | Selected |
|--------|-------------|----------|
| Status keyword map | Map each CAC/SCP status string to a score in config | ✓ |
| Binary (pending vs paid) | Any unpaid=20, any paid=0 | |
| You decide | Claude picks based on CacScraper output | |

**User's choice:** Status keyword map
**Notes:** User asked for explanation of what payment status means in TJ-SP context before answering. Explained PENDENTE/EM PROCESSAMENTO/PARCIALMENTE PAGO/PAGO lifecycle.

### Chronological Position Criterion (15pts)

| Option | Description | Selected |
|--------|-------------|----------|
| Tiered brackets | Position ranges in config (1-100=15, 101-500=12, etc.) | ✓ |
| Inverse linear | Formula: 15 × (1 - position/maxPosition) | |
| You decide | Claude picks based on TJ-SP queue sizes | |

**User's choice:** Tiered brackets
**Notes:** User asked for explanation of what chronological position means. Explained TJ-SP precatorio payment queue ordering — lower position = closer to payment.

### Nature Criterion (10pts)

| Option | Description | Selected |
|--------|-------------|----------|
| Alimentar/Comum binary | Alimentar=10, Comum=4 | ✓ |
| Keyword map | Map each natureza string to a score | |
| You decide | Claude picks based on CacScraper output | |

**User's choice:** Alimentar/Comum binary
**Notes:** None

---

## Cache Scope

### Which Methods Get @Cacheable

| Option | Description | Selected |
|--------|-------------|----------|
| Primary fetches only | fetchProcesso, fetchPrecatorio, buscarPorNumeroProcesso | ✓ |
| All methods | Cache everything including search methods | |
| You decide | Claude decides per method | |

**User's choice:** Primary fetches only
**Notes:** None

### buscarPorNome Caching

| Option | Description | Selected |
|--------|-------------|----------|
| Cache with shorter TTL | 6h TTL for name searches | |
| Don't cache | Name searches always go to e-SAJ | ✓ |
| Same 24h TTL | Same TTL as primary fetches | |

**User's choice:** Don't cache
**Notes:** None

---

## Score Details Format

### JSON Structure

| Option | Description | Selected |
|--------|-------------|----------|
| Flat map | Simple key-value: {"valor": 22, "total": 80} | ✓ |
| Rich per-criterion objects | Nested objects with pontos, maximo, valorBruto, regra | |
| You decide | Claude picks best format | |

**User's choice:** Flat map
**Notes:** User reviewed preview of both formats before selecting.

---

## Missing Data Handling

### Missing Criterion Score

| Option | Description | Selected |
|--------|-------------|----------|
| Zero the criterion | Missing data = 0 points | ✓ |
| Middle default | 50% of max points for missing criteria | |
| Exclude and rescale | Remove criterion and rescale to 100 | |

**User's choice:** Zero the criterion
**Notes:** None

### Missing Data Indicator in scoreDetalhes

| Option | Description | Selected |
|--------|-------------|----------|
| Null value in map | Use null instead of 0 for missing criteria | ✓ |
| Just use 0 | No distinction between scored-0 and missing | |
| You decide | Claude picks | |

**User's choice:** Null value in map
**Notes:** None

---

## Claude's Discretion

- CacheConfig bean implementation details
- ScoringProperties nested class structure
- Default threshold values for initial deployment
- Unit test structure for scoring

## Deferred Ideas

None — discussion stayed within phase scope.
