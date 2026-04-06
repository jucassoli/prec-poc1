---
status: partial
phase: 04-bfs-prospection-engine-and-prospection-api
source: [04-VERIFICATION.md]
started: 2026-04-06T00:00:00Z
updated: 2026-04-06T00:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. End-to-End BFS Run Against Live TJ-SP
expected: POST a real CNJ seed, poll until CONCLUIDA, verify non-empty leads array with real creditor/precatorio data
result: [pending]

### 2. Counter Visibility Under Concurrency
expected: Poll GET /{id} while BFS runs and confirm processosVisitados/leadsQualificados increment between polls (proving REQUIRES_NEW transactions are visible to concurrent readers)
result: [pending]

### 3. Partial Failure Isolation at Runtime
expected: Block CAC access mid-run and confirm CONCLUIDA status with erroMensagem populated while other leads are still persisted
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps
