# Design Note — Idempotent Background Transaction Processing Service (FDE L2)

## 1. Architecture

```
[TransactionSource]          Replaceable input (sample in-memory | CSV file)
        |
        v
[TransactionProcessingRunner]  Simulates scheduled / queue-triggered job
        |
        v
[TransactionProcessingService] Orchestrates lifecycle
   |- TransactionValidator     Input validation (isolates bad records)
   |- IdempotencyStore         requestId + transactionId claims
   |- SequenceTracker          Out-of-order gap buffer (Option A)
   |- TransactionBusinessProcessor  Ledger side effects only
   |- TransientFailureSimulator     Retry demo hook
   |- AccountLedger            Balances + ledger entries
   |- TransactionStateStore    Status lifecycle persistence (in-memory)
        |
        v
[ProcessingReportLogger]     Counts by status + per-record outcomes
```

Business logic (ledger posting) is separated from ingestion and orchestration so a real queue consumer, scheduler, or API webhook can replace `TransactionSource` / `ApplicationRunner` without rewriting core rules.

## 2. Idempotency Design

Two keys are tracked:

| Key | Purpose |
|-----|---------|
| `requestId` | Detect exact request retries / wire duplicates |
| `transactionId` | Detect business-level replays with a new `requestId` |

Before a ledger post, the processor **claims** both keys atomically (`tryClaim`). On successful post the claim remains. On transient failure **before** a side effect, the claim is **released** so a retry can re-claim safely.

**Guarantee:** a given `transactionId` produces at most one ledger entry.

## 3. Duplicate Detection Logic

A request is marked `DUPLICATE` when:

1. `requestId` was already claimed, or
2. `transactionId` was already claimed (even with a different `requestId`, e.g. `REQ-2-DUP`), or
3. Sequence is stale (`sequence < nextExpected`) after the slot was already consumed.

Duplicates never call `AccountLedger.post`.

## 4. Out-of-Order Handling Strategy (Option A — Sequence-Based)

Global stream sequence numbers are used (matches the L2 sample where sequences 1–5 span accounts).

| Condition | Action |
|-----------|--------|
| `sequence == nextExpected` | Process now |
| `sequence > nextExpected` | Buffer as `PENDING` |
| `sequence < nextExpected` | `DUPLICATE` / stale |

When a sequence is completed (processed, validation-failed, or retry-exhausted), `nextExpected` advances and contiguous buffered records are drained.

**Trade-off:** strict sequencing prevents inconsistent intermediate balances for ordered financial events, but a permanently missing sequence can stall the pipeline. Production hardening would add gap timeouts, dead-letter alerts, and operator replay tools. Per-account sequences are a natural next step if accounts are independent shards.

## 5. Retry and Failure Handling

- `TransientFailureSimulator` fails configured `transactionId`s for N attempts (L2 sample: `TXN-2005` fails once).
- Status moves to `RETRY_PENDING` while `attemptCount < maxAttempts`.
- After exhaustion → `FAILED`, sequence advances so one poison message does not block the stream.
- Retries never double-post because claims are released only when no ledger entry was written, and re-claimed before the next attempt.

Invalid records (`amount <= 0`, missing fields, bad currency) → `FAILED` immediately; the batch continues.

## 6. Transaction Status Lifecycle

`RECEIVED → PROCESSING → PROCESSED`  
`RECEIVED → PENDING → PROCESSING → PROCESSED` (after gap fill)  
`RECEIVED → DUPLICATE`  
`RECEIVED → FAILED` (validation)  
`PROCESSING → RETRY_PENDING → PROCESSING → PROCESSED | FAILED`

## 7. Observability

- Structured-style logs with `event=` keys: `batch_started`, `out_of_order_buffered`, `duplicate_detected`, `retry_pending`, `processed`, `failed`, `batch_completed`.
- End-of-batch **processing summary**: counts by status, account balances, per-record status/attempts/reason.
- Identifiers only in logs (no extra PII fields).

## 8. AI-Assisted Development Usage

AI was used to accelerate scaffolding (domain model, Spring wiring, test outlines) and to cross-check edge cases against the L2 rubric. Generated structure was reviewed and adjusted for:

- Correct global sequence semantics for the provided sample data
- Claim/release semantics so retries stay idempotent
- Isolation of invalid records without aborting the batch
- Explicit production trade-offs in this design note

## 9. Production Hardening Recommendations

1. **Durable stores** — Replace in-memory maps with DB unique constraints on `(request_id)` / `(transaction_id)` and transactional outbox for ledger writes.
2. **Atomicity** — Persist status + ledger entry in one DB transaction (or saga/outbox) to avoid “claimed but not posted” windows across process crashes.
3. **Concurrency** — Optimistic locking / row locks per account; partition consumers by account or sequence shard.
4. **Queue semantics** — Prefer at-least-once queues with idempotent consumers; configure visibility timeout ≥ max processing time.
5. **Gap management** — Alert on `PENDING` age; DLQ after timeout; admin replay API.
6. **Metrics** — Counters for processed/duplicate/failed/retry; histogram for attempts; gauge for pending gap size.
7. **Security** — Authn/z on ingestion API; encrypt at rest; redact amounts in non-secure log sinks if required by policy.
8. **Exactly-once illusion** — Document that end-to-end is at-least-once + idempotent effects, not distributed XA.
