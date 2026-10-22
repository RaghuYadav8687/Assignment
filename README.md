# Idempotent Background Transaction Processing Service

FDE L2 practical assessment: a Spring Boot background job that processes transaction requests **idempotently**, handles **duplicates** and **out-of-order** delivery, retries **transient failures**, and emits a **processing summary**.

## Stack

- Java 21
- Spring Boot 4.x
- Maven
- In-memory stores (no external DB required)

## Quick start

```bash
mvn spring-boot:run
```

On startup the job loads the built-in L2 sample batch, processes it, and prints a summary to the console.

### Optional CSV input

```properties
assessment.processing.input-file=sample-data/transactions.csv
```

CSV columns:

```text
transactionId,requestId,accountId,transactionType,amount,currency,sequenceNumber
```

## Run tests

```bash
mvn test
```

## Expected sample outcomes

| requestId | Expected status | Notes |
|-----------|-----------------|-------|
| REQ-2 | PROCESSED | Buffered (seq 2), then drained after seq 1 |
| REQ-1 | PROCESSED | Fills sequence gap |
| REQ-2-DUP | DUPLICATE | Same `transactionId` as REQ-2 |
| REQ-3 | PROCESSED | |
| REQ-4 | FAILED | amount ≤ 0 |
| REQ-5 | PROCESSED | One simulated transient failure, then success |

Account balances after the sample batch:

- `ACC-201` = 85 (100 − 40 + 25)
- `ACC-203` = 500

## Design

See [DESIGN.md](DESIGN.md) for architecture, idempotency, ordering (Option A), retries, observability, AI usage, and production hardening notes.
