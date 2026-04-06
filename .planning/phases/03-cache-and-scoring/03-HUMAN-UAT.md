---
status: partial
phase: 03-cache-and-scoring
source: [03-VERIFICATION.md]
started: 2026-04-06T15:30:00Z
updated: 2026-04-06T15:30:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Live cache HTTP suppression
expected: Start the app, call the same `GET /api/v1/processo/{numero}` twice, confirm second call produces no outbound HTTP via `/actuator/caches` hit counts and application logs.
result: [pending]

## Summary

total: 1
passed: 0
issues: 0
pending: 1
skipped: 0
blocked: 0

## Gaps
