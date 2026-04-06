---
status: partial
phase: 02-scraper-layer
source: [02-VERIFICATION.md]
started: 2026-04-06T00:00:00Z
updated: 2026-04-06T00:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Live e-SAJ process fetch
expected: GET /api/v1/processo/{numero} returns parsed process data with correct CSS selectors against real TJ-SP HTML
result: [pending]

### 2. Live CAC/SCP precatorio fetch
expected: GET /api/v1/precatorio/{numero} completes ViewState session cycle and returns precatorio data
result: [pending]

### 3. Live DataJud query
expected: POST /api/v1/datajud/buscar connects to CNJ Elasticsearch API and returns results
result: [pending]

### 4. Live e-SAJ search
expected: GET /api/v1/processo/buscar?nome= returns search results using #listagemDeProcessos selector
result: [pending]

### 5. Rate limiting enforcement
expected: RateLimiter enforces 2s inter-request timing under live load
result: [pending]

### 6. HTTP 429 pause behavior
expected: 60s IntervalBiFunction pause activates correctly on 429 responses
result: [pending]

## Summary

total: 6
passed: 0
issues: 0
pending: 6
skipped: 0
blocked: 0

## Gaps
