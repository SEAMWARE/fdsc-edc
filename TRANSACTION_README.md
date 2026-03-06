# Transactional Consistency for TMFBackedContractNegotiationStore

## Problem Statement

The `TMFBackedContractNegotiationStore.save()` method orchestrates multi-step operations across 5 independent TMForum REST APIs (Quote, Agreement, ProductOrder, ProductInventory, ProductCatalog). Each handler method makes 2–6 HTTP calls, none of which are atomic as a group. If any call fails mid-operation, the TMForum entities are left in an inconsistent state with no rollback.

The EDC framework provides a `TransactionContext` SPI (`org.eclipse.edc.transaction.spi.TransactionContext`) for exactly this purpose. However, the store does not use it — the default `NoopTransactionContext` simply executes blocks immediately. Since the TMForum APIs are REST-based (no ACID transactions), a traditional `TransactionContext` implementation would have no effect. Instead, a **compensation-based (saga) approach** is needed.

## Current Architecture

### TransactionContext Usage

| Location | Usage |
|---|---|
| `TMFBackedContractNegotiationStore` | **Not used.** Multi-step API calls have no transactional guarantee. |
| `TMFLeaseContext` | Wraps in-memory lease operations in `transactionContext.execute()`. With `NoopTransactionContext`, this is a no-op wrapper. |
| `ContractNegotiationTriggerSubscriber` (test-extension) | Wraps `findByIdAndLease` + `save` in a single `transactionContext.execute()` block — shows the intended EDC pattern. |
| `TMFContractNegotiationExtension` | Does not inject or wire `TransactionContext`. |

### Failure Scenarios in `save()`

Each state handler makes multiple API calls. If a later call fails, earlier calls are already committed:

| Handler | API Calls (in order) | Failure Impact |
|---|---|---|
| `handleInitialStates` | 1. findByNegotiationId 2. (optional) updateQuote to cancel old 3. createQuote 4. (optional) updateQuote | Orphaned quote if create succeeds but update fails |
| `handleRequestedState` | 1. findByNegotiationId 2. (optional) terminateQuote 3. createQuote 4. updateQuote | Cancelled quote with no replacement |
| `handleAcceptStates` | 1. findByNegotiationId 2. createQuote (if needed) 3. updateQuote | Quote created but not updated to correct state |
| `handleAgreeStates` | 1. findByNegotiationId 2. updateQuote 3. createAgreement | Quote updated but agreement missing |
| `handleVerificationStates` | 1. findByNegotiationId 2. createProductOrder 3. updateQuote | Order created but quote not updated |
| `handleFinalStates` | 1. findByNegotiationId 2. getProductOrder 3. updateQuote 4. createProduct 5. findAgreement 6. updateAgreement 7. updateProductOrder | Most dangerous: up to 4 writes, any mid-failure leaves inconsistent state |
| `handleTerminationStates` | 1. findByNegotiationId 2. N × (updateQuote + cancelProductOrder) 3. cancelAgreements | Partial cancellation |

### Why Traditional TransactionContext Doesn't Help

TMForum APIs are independent REST services. There is no distributed transaction coordinator (XA/2PC) available. Wrapping calls in `transactionContext.execute()` with the `NoopTransactionContext` (or any JDBC-based implementation) has no effect on HTTP calls. The problem requires **application-level compensation**.

## Proposed Architecture: Saga-Based Compensation

### Core Concept

Implement a **compensating transaction** (saga) pattern: each API write is paired with a compensation action. If a subsequent step fails, previously executed compensations are run in reverse order to undo the partial work.

### New Components

| Class | Role |
|---|---|
| `SagaContext` | Tracks a sequence of executed steps. Each step has a description and a `Runnable` compensation action. On failure, runs compensations in reverse order. |
| `TMFTransactionContext` | Implements EDC's `TransactionContext` interface. Wraps block execution with `SagaContext` lifecycle management. On exception, triggers compensation before re-throwing. |
| (updated) `TMFBackedContractNegotiationStore` | Wraps each `save()` state handler in `transactionContext.execute()`. Registers compensations after each API write. |

### SagaContext Design

```java
public class SagaContext {
    private final Deque<CompensationStep> steps = new ArrayDeque<>();
    private final Monitor monitor;

    /** Register a compensation to run if a later step fails. */
    public void addCompensation(String description, Runnable compensation) {
        steps.push(new CompensationStep(description, compensation));
    }

    /** Run all registered compensations in reverse order. */
    public void compensate() {
        while (!steps.isEmpty()) {
            CompensationStep step = steps.pop();
            try {
                monitor.info("Compensating: " + step.description());
                step.action().run();
            } catch (Exception e) {
                // Log but continue — best-effort compensation
                monitor.severe("Compensation failed: " + step.description(), e);
            }
        }
    }

    private record CompensationStep(String description, Runnable action) {}
}
```

### TMFTransactionContext Design

```java
public class TMFTransactionContext implements TransactionContext {
    private final Monitor monitor;
    private final ThreadLocal<SagaContext> currentSaga = new ThreadLocal<>();

    @Override
    public void execute(TransactionBlock block) {
        SagaContext saga = new SagaContext(monitor);
        currentSaga.set(saga);
        try {
            block.execute();
        } catch (Exception e) {
            saga.compensate();
            throw e;
        } finally {
            currentSaga.remove();
        }
    }

    /** Called by store methods to register compensations. */
    public SagaContext current() {
        return currentSaga.get();
    }
}
```

### Compensation Actions per Handler

| Handler | Write Operation | Compensation |
|---|---|---|
| `handleInitialStates` | `createQuote(q)` | `updateQuote(q.id, state=CANCELLED)` |
| `handleInitialStates` | `updateQuote(q, IN_PROGRESS)` | `updateQuote(q.id, previousState)` |
| `handleRequestedState` | `terminateQuote(old)` | `updateQuote(old.id, previousState)` — partial, old state may not be fully recoverable |
| `handleRequestedState` | `createQuote(q)` | `updateQuote(q.id, state=CANCELLED)` |
| `handleAgreeStates` | `updateQuote(q, ACCEPTED)` | `updateQuote(q.id, previousState)` |
| `handleAgreeStates` | `createAgreement(a)` | `updateAgreement(a.id, status=REJECTED)` |
| `handleVerificationStates` | `createProductOrder(po)` | `updateProductOrder(po.id, state=CANCELLED)` |
| `handleVerificationStates` | `updateQuote(q)` | `updateQuote(q.id, previousState)` |
| `handleFinalStates` | `updateQuote(q, ACCEPTED)` | `updateQuote(q.id, previousState)` |
| `handleFinalStates` | `createProduct(p)` | No TMForum delete API; mark product as `SUSPENDED` or leave orphaned (document limitation) |
| `handleFinalStates` | `updateAgreement(a, AGREED)` | `updateAgreement(a.id, status=IN_PROCESS)` |
| `handleFinalStates` | `updateProductOrder(po, COMPLETED)` | `updateProductOrder(po.id, state=IN_PROGRESS)` |
| `handleTerminationStates` | `updateQuote(q, CANCELLED)` | `updateQuote(q.id, previousState)` — but logically, termination is intentional |
| `handleTerminationStates` | `cancelProductOrder(po)` | `updateProductOrder(po.id, previousState)` |
| `handleTerminationStates` | `cancelAgreements(a)` | `updateAgreement(a.id, previousStatus)` |

### Limitations

1. **Compensation is best-effort.** If the compensation HTTP call itself fails, the system logs a severe error but continues compensating remaining steps. This is inherent to sagas — they do not guarantee atomicity, only eventual consistency.
2. **No TMForum delete APIs.** Created entities (quotes, products, orders, agreements) cannot be deleted, only updated to a terminal/cancelled state. This means compensation for creation = marking as cancelled/rejected.
3. **Idempotency.** The TMForum PATCH (update) operations are idempotent for the same payload, which makes retrying compensations safe.
4. **`handleTerminationStates` is a special case.** Compensation on termination is questionable — if we're cancelling a negotiation and a later cancel fails, should we "un-cancel" the earlier ones? Likely not. Termination should be treated as a "best-effort complete" operation rather than a strict saga.
5. **State snapshots.** To compensate an `updateQuote`, we need the previous state. The handler methods currently don't capture pre-update state. We need to snapshot before each write.

## Implementation Steps

### Step 1: Create SagaContext

Create `SagaContext` with `addCompensation(description, Runnable)` and `compensate()`. Unit tests for ordering (LIFO), exception handling during compensation, and empty saga.

**Files:**
- `tmf-extension/src/main/java/org/seamware/edc/store/SagaContext.java`
- `tmf-extension/src/test/java/org/seamware/edc/store/SagaContextTest.java`

### Step 2: Create TMFTransactionContext

Implement `TransactionContext` using `SagaContext` per thread via `ThreadLocal`. Expose `current()` for saga registration. Handle nested `execute()` calls (reuse existing saga or create new one).

**Files:**
- `tmf-extension/src/main/java/org/seamware/edc/store/TMFTransactionContext.java`
- `tmf-extension/src/test/java/org/seamware/edc/store/TMFTransactionContextTest.java`

### Step 3: Wire TMFTransactionContext in the Extension

Register `TMFTransactionContext` as the `TransactionContext` implementation in `TMFContractNegotiationExtension`. Inject it into the store constructor.

**Files:**
- `tmf-extension/src/main/java/org/seamware/edc/TMFContractNegotiationExtension.java`
- `tmf-extension/src/main/java/org/seamware/edc/store/TMFBackedContractNegotiationStore.java` (add constructor parameter)

### Step 4: Add Compensation to State Handlers — Quote Operations

Refactor `updateQuote()`, `createQuote()`, and `terminateQuote()` to:
1. Snapshot the pre-update state (for `updateQuote`/`terminateQuote`)
2. Register a compensation action after each successful write
3. Return the created/updated entity for use in subsequent compensations

This is the largest step. The private `updateQuote()` method needs access to `SagaContext` (either via field or passed as parameter).

**Compensation strategy:**
- `createQuote` → compensate by PATCHing quote to `CANCELLED`
- `updateQuote` → compensate by PATCHing quote back to previous `QuoteStateTypeVO` and `ContractNegotiationState`
- `terminateQuote` → compensate by PATCHing quote back to previous state

**Files:**
- `tmf-extension/src/main/java/org/seamware/edc/store/TMFBackedContractNegotiationStore.java`

### Step 5: Add Compensation to State Handlers — Agreement Operations

Add compensation to `createAgreement()` and agreement updates in `handleFinalStates()` / `handleTerminationStates()`.

**Compensation strategy:**
- `createAgreement` → compensate by updating status to `REJECTED`
- `updateAgreement` (status → AGREED) → compensate by reverting to `IN_PROCESS`
- `cancelAgreements` → compensate by reverting to previous status

**Files:**
- `tmf-extension/src/main/java/org/seamware/edc/store/TMFBackedContractNegotiationStore.java`

### Step 6: Add Compensation to State Handlers — ProductOrder and Product Operations

Add compensation to `createProductOrder()`, `updateProductOrder()`, and `createProduct()` in `handleVerificationStates()` and `handleFinalStates()`.

**Compensation strategy:**
- `createProductOrder` → compensate by PATCHing to `CANCELLED`
- `updateProductOrder` (→ COMPLETED) → compensate by reverting to previous state
- `createProduct` → compensate by PATCHing to `SUSPENDED` (document: TMForum has no delete)

**Files:**
- `tmf-extension/src/main/java/org/seamware/edc/store/TMFBackedContractNegotiationStore.java`

### Step 7: Wrap `save()` in TransactionContext

Wrap the `save()` method body in `transactionContext.execute()`. On exception, `TMFTransactionContext` will automatically trigger compensation before re-throwing. The existing `finally` block for lease release remains outside the transaction block.

```java
@Override
public void save(ContractNegotiation contractNegotiation) {
    try {
        leaseHolder.acquireLease(contractNegotiation.getId(), lockId);
        transactionContext.execute(() -> {
            ContractNegotiationStates negotiationState =
                ContractNegotiationStates.from(contractNegotiation.getState());
            switch (negotiationState) {
                // ... state handlers register compensations ...
            }
        });
    } catch (Exception e) {
        // compensation already ran inside TMFTransactionContext
        throw new EdcPersistenceException(..., e);
    } finally {
        leaseHolder.freeLease(contractNegotiation.getId(), "Finally saved.");
    }
}
```

**Files:**
- `tmf-extension/src/main/java/org/seamware/edc/store/TMFBackedContractNegotiationStore.java`

### Step 8: Tests for Compensating Behavior

Write integration-style tests using Mockito that verify:
1. Successful save — no compensations run
2. Mid-handler failure — compensations for prior writes are called in reverse order
3. Compensation failure — logged but remaining compensations still run
4. Each handler's specific compensation actions (quote state reverted, agreement rejected, order cancelled)

**Files:**
- `tmf-extension/src/test/java/org/seamware/edc/store/TMFBackedContractNegotiationStoreTransactionTest.java`

## Interaction with Existing Lease Mechanism

The lease mechanism (from the previous implementation) and the transaction mechanism are complementary:

- **Lease** prevents concurrent access: only one instance processes a negotiation at a time.
- **Transaction (saga)** provides consistency within a single save operation: if a step fails, previous steps are compensated.

The `save()` flow becomes:
1. **Acquire lease** (distributed, via TMFBackedLeaseHolder)
2. **Execute transaction** (saga, via TMFTransactionContext)
   - Each API write registers its compensation
   - On failure: compensations run in reverse
3. **Release lease** (in `finally` block)

## Open Questions

1. **Should `handleTerminationStates` participate in compensation?** Termination is a destructive operation. If cancelling the 3rd of 5 quotes fails, should the first 2 be un-cancelled? The pragmatic answer is likely "no" — log the partial failure and let an operator resolve it.

2. **Should `nextNotLeased` and `findByIdAndLease` also be wrapped in transactions?** These are read-heavy operations with lease acquisition. If `acquireLease` fails after reads, there's nothing to compensate. The transaction wrapper would be a no-op.

3. **Should `TMFTransactionContext` replace `TMFLeaseContext`'s usage of `TransactionContext`?** Yes — once `TMFTransactionContext` is registered as the `TransactionContext` implementation, `TMFLeaseContext` will automatically use it. Since `TMFLeaseContext` is in-memory only, the saga wrapper adds negligible overhead.

4. **Thread-safety of SagaContext.** Since `save()` holds the write lock via `LockManager`, and `SagaContext` is per-thread via `ThreadLocal`, concurrent access to the same `SagaContext` is not possible. No additional synchronization is needed.
