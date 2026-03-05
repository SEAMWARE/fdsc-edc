# Distributed Lease Mechanism for TMFBackedContractNegotiationStore

## Problem Statement

The current lease mechanism in `TMFBackedContractNegotiationStore` uses an in-memory `HashMapLeaseHolder` to prevent concurrent modifications to contract negotiations. This works for a single instance but fails in a multi-instance deployment:

- **Lease state is lost on restart** — the `HashMap` is volatile, so all leases disappear when the process restarts.
- **No cross-instance visibility** — two instances can both acquire a lease on the same negotiation ID because each has its own independent `HashMap`.
- **The `LockManager` (`AutomaticUnlockingLockManager`) uses a JVM-local `ReentrantReadWriteLock`** — it coordinates threads within a single process, not across instances.

Meanwhile, `ContractNegotiationState` already has an `isLeased` boolean field that is serialized into the TMForum Quote's `contractNegotiation` JSON object. This field is never set to `true` anywhere in the codebase — it is always written as `false` (or left at default). The infrastructure for distributed leasing through the TMForum API is present but unused.

## Current Architecture

### Components

| Class | Role |
|---|---|
| `LeaseHolder` (interface) | Defines `acquireLease`, `freeLease`, `isLeased`, `isLeasedBy` |
| `HashMapLeaseHolder` | In-memory implementation using `HashMap<String, Lease>`. Default lease TTL: 60s. Checks expiry via `Clock`. |
| `LockManager` (interface) | Defines `readLock` / `writeLock` for thread-level synchronization |
| `AutomaticUnlockingLockManager` | JVM-local `ReentrantReadWriteLock` with timeout (10.5s) and emergency auto-unlock (10s) |
| `TMFLeaseContext` | Separate lease mechanism for TransferProcess (not ContractNegotiation). Also in-memory. |
| `ContractNegotiationState` | Domain model with `isLeased` field, serialized into Quote JSON |

### Flow Through the Store

1. **`nextNotLeased(max, criteria)`** — acquires the write lock, fetches all negotiations from the TMForum Quote API, filters out those already leased (via `HashMapLeaseHolder`), acquires leases on the remaining ones, returns up to `max`.

2. **`findByIdAndLease(id)`** — acquires the write lock, looks up the negotiation by ID, attempts to acquire a lease in the `HashMapLeaseHolder`.

3. **`save(negotiation)`** — acquires a lease (or re-acquires if already held by this lock ID), performs the state transition (creating/updating quotes, agreements, orders), then **always** frees the lease in a `finally` block.

### Key Observation

When saving, the store builds a `ContractNegotiationState` for the quote update:

```java
ContractNegotiationState contractNegotiationState =
    new ContractNegotiationState()
        .setControlplane(controlplane)
        .setPending(contractNegotiation.isPending())
        .setState(contractNegotiation.stateAsString())
        .setCorrelationId(contractNegotiation.getCorrelationId())
        .setCounterPartyAddress(contractNegotiation.getCounterPartyAddress());
```

`setLeased()` is never called — so `isLeased` is always `false` in the JSON written to the TMForum API.

## Proposed Architecture

### Approach: Optimistic Locking with TMForum-Persisted Lease State

Use the existing `ContractNegotiationState.isLeased` field plus two new fields (`leasedBy` and `leaseExpiry`) to implement distributed leases through the TMForum Quote API. The TMForum API becomes the single source of truth for lease state.

### New Fields on `ContractNegotiationState`

```java
public class ContractNegotiationState {
    private boolean isLeased;
    private String leasedBy;       // the lockId (UUID) of the instance holding the lease
    private long leaseExpiry;      // epoch millis when the lease expires
    // ... existing fields unchanged
}
```

### New Implementation: `TMFBackedLeaseHolder`

A new `LeaseHolder` implementation that reads and writes lease state through the TMForum Quote API:

```java
public class TMFBackedLeaseHolder implements LeaseHolder {

    private static final Duration DEFAULT_LEASE_TIME = Duration.ofSeconds(60);

    private final QuoteApiClient quoteApi;
    private final String controlplane;
    private final Clock clock;
    private final Monitor monitor;

    public TMFBackedLeaseHolder(
            QuoteApiClient quoteApi, String controlplane, Clock clock, Monitor monitor) {
        this.quoteApi = quoteApi;
        this.controlplane = controlplane;
        this.clock = clock;
        this.monitor = monitor;
    }

    @Override
    public void acquireLease(String negotiationId, String lockId, Duration leaseTime) {
        List<ExtendableQuoteVO> quotes = quoteApi.findByNegotiationId(negotiationId);

        // Find the newest quote owned by this controlplane
        ExtendableQuoteVO activeQuote = quotes.stream()
                .filter(q -> q.getContractNegotiationState() != null)
                .filter(q -> q.getContractNegotiationState().getControlplane().equals(controlplane))
                .reduce((a, b) -> b) // last = newest
                .orElseThrow(() -> new IllegalStateException(
                        "No quote found for negotiation " + negotiationId));

        ContractNegotiationState currentState = activeQuote.getContractNegotiationState();

        // Check if already leased by someone else (and not expired)
        if (currentState.isLeased()
                && !lockId.equals(currentState.getLeasedBy())
                && currentState.getLeaseExpiry() > clock.millis()) {
            throw new IllegalStateException(
                    String.format("Negotiation %s is already leased by %s until %d",
                            negotiationId, currentState.getLeasedBy(),
                            currentState.getLeaseExpiry()));
        }

        // Write the lease into the quote
        long expiry = clock.millis() + leaseTime.toMillis();
        ContractNegotiationState updatedState = new ContractNegotiationState()
                .setControlplane(currentState.getControlplane())
                .setPending(currentState.isPending())
                .setState(currentState.getState())
                .setCorrelationId(currentState.getCorrelationId())
                .setCounterPartyAddress(currentState.getCounterPartyAddress())
                .setLeased(true)
                .setLeasedBy(lockId)
                .setLeaseExpiry(expiry);

        ExtendableQuoteUpdateVO updateVO = new ExtendableQuoteUpdateVO();
        updateVO.setContractNegotiationState(updatedState);
        quoteApi.updateQuote(activeQuote.getId(), updateVO);

        monitor.info(String.format("Acquired lease on %s by %s until %d",
                negotiationId, lockId, expiry));
    }

    @Override
    public void acquireLease(String negotiationId, String lockId) {
        acquireLease(negotiationId, lockId, DEFAULT_LEASE_TIME);
    }

    @Override
    public boolean isLeased(String negotiationId) {
        List<ExtendableQuoteVO> quotes = quoteApi.findByNegotiationId(negotiationId);
        return quotes.stream()
                .filter(q -> q.getContractNegotiationState() != null)
                .filter(q -> q.getContractNegotiationState().getControlplane().equals(controlplane))
                .anyMatch(q -> {
                    ContractNegotiationState state = q.getContractNegotiationState();
                    return state.isLeased() && state.getLeaseExpiry() > clock.millis();
                });
    }

    @Override
    public boolean isLeasedBy(String negotiationId, String lockId) {
        List<ExtendableQuoteVO> quotes = quoteApi.findByNegotiationId(negotiationId);
        return quotes.stream()
                .filter(q -> q.getContractNegotiationState() != null)
                .filter(q -> q.getContractNegotiationState().getControlplane().equals(controlplane))
                .anyMatch(q -> {
                    ContractNegotiationState state = q.getContractNegotiationState();
                    return state.isLeased()
                            && lockId.equals(state.getLeasedBy())
                            && state.getLeaseExpiry() > clock.millis();
                });
    }

    @Override
    public void freeLease(String negotiationId, String reason) {
        List<ExtendableQuoteVO> quotes = quoteApi.findByNegotiationId(negotiationId);
        quotes.stream()
                .filter(q -> q.getContractNegotiationState() != null)
                .filter(q -> q.getContractNegotiationState().getControlplane().equals(controlplane))
                .filter(q -> q.getContractNegotiationState().isLeased())
                .forEach(q -> {
                    ContractNegotiationState currentState = q.getContractNegotiationState();
                    ContractNegotiationState updatedState = new ContractNegotiationState()
                            .setControlplane(currentState.getControlplane())
                            .setPending(currentState.isPending())
                            .setState(currentState.getState())
                            .setCorrelationId(currentState.getCorrelationId())
                            .setCounterPartyAddress(currentState.getCounterPartyAddress())
                            .setLeased(false)
                            .setLeasedBy(null)
                            .setLeaseExpiry(0);

                    ExtendableQuoteUpdateVO updateVO = new ExtendableQuoteUpdateVO();
                    updateVO.setContractNegotiationState(updatedState);
                    quoteApi.updateQuote(q.getId(), updateVO);
                });

        monitor.info(String.format("Freed lease on %s because: %s", negotiationId, reason));
    }
}
```

### Changes to `ContractNegotiationState`

```java
public class ContractNegotiationState {

    private boolean isPending;
    private boolean isLeased;
    private String leasedBy;        // NEW: lockId of the lease holder
    private long leaseExpiry;       // NEW: epoch millis expiry timestamp
    private String controlplane;
    private String state;
    private String correlationId;
    private String counterPartyAddress;

    // ... existing getters/setters ...

    public String getLeasedBy() {
        return leasedBy;
    }

    public ContractNegotiationState setLeasedBy(String leasedBy) {
        this.leasedBy = leasedBy;
        return this;
    }

    public long getLeaseExpiry() {
        return leaseExpiry;
    }

    public ContractNegotiationState setLeaseExpiry(long leaseExpiry) {
        this.leaseExpiry = leaseExpiry;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContractNegotiationState that = (ContractNegotiationState) o;
        return isPending == that.isPending
            && isLeased == that.isLeased
            && leaseExpiry == that.leaseExpiry
            && Objects.equals(leasedBy, that.leasedBy)
            && Objects.equals(controlplane, that.controlplane)
            && Objects.equals(state, that.state)
            && Objects.equals(correlationId, that.correlationId)
            && Objects.equals(counterPartyAddress, that.counterPartyAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            isPending, isLeased, leasedBy, leaseExpiry,
            controlplane, state, correlationId, counterPartyAddress);
    }
}
```

### Changes to `TMFBackedContractNegotiationStore.save()`

Every place in `save()` that builds a `ContractNegotiationState` for quote updates/creates must propagate the lease fields. Since `save()` already calls `leaseHolder.acquireLease()` at the top and `leaseHolder.freeLease()` in `finally`, the lease is already written to the TMForum API before and after the save operation.

However, the intermediate `updateQuote` and `createQuote` calls within `save()` also build `ContractNegotiationState` objects. These must **preserve the lease state** to avoid overwriting it:

```java
// In updateQuote() and createQuote() — add lease fields:
ContractNegotiationState contractNegotiationState =
    new ContractNegotiationState()
        .setControlplane(controlplane)
        .setPending(contractNegotiation.isPending())
        .setState(contractNegotiation.stateAsString())
        .setCorrelationId(contractNegotiation.getCorrelationId())
        .setCounterPartyAddress(contractNegotiation.getCounterPartyAddress())
        .setLeased(true)          // we hold the lease during save
        .setLeasedBy(lockId)      // this instance's lock ID
        .setLeaseExpiry(/* current lease expiry */);
```

This requires making `lockId` accessible to the methods that build `ContractNegotiationState`, and tracking the current lease expiry. The simplest approach: store the expiry as an instance field set during `acquireLease` or pass it through a thread-local/context object.

### Changes to `nextNotLeased()`

The current implementation filters out leased negotiations using the in-memory `HashMapLeaseHolder`. With the TMF-backed approach, the lease state is already part of the quote data fetched from the API:

```java
@Override
public @NotNull List<ContractNegotiation> nextNotLeased(int max, Criterion... criteria) {
    try {
        return lockManager.writeLock(
            () -> {
                // getNegotiations fetches quotes from TMForum API
                // Each quote's ContractNegotiationState.isLeased + leaseExpiry is populated
                return getNegotiations(Arrays.asList(criteria)).stream()
                    .filter(e -> !leaseHolder.isLeased(e.getId()))
                    .filter(cn -> {
                        try {
                            leaseHolder.acquireLease(cn.getId(), lockId);
                            return true;
                        } catch (Exception e) {
                            monitor.info(String.format("Was not able to lease %s", cn.getId()), e);
                            return false;
                        }
                    })
                    .limit(max)
                    .toList();
            });
    } catch (Exception e) {
        monitor.warning("Failed to get", e);
        throw new EdcPersistenceException(e);
    }
}
```

The structure stays the same. The behavioral change is that `leaseHolder.isLeased()` and `leaseHolder.acquireLease()` now go through the TMForum API instead of the local HashMap. This means:

- `isLeased()` makes an API call to check the quote's current state.
- `acquireLease()` reads the quote, checks for an existing valid lease, and writes the lease back.

**Performance note:** `nextNotLeased` fetches all negotiations and then calls `isLeased` + `acquireLease` per negotiation, each of which fetches quotes again. This is an N+1 problem. An optimization is to check the `isLeased`/`leaseExpiry` fields from the already-fetched quote data rather than calling the API again. This can be achieved by either:
1. Passing the already-fetched `ContractNegotiationState` through to the filter, or
2. Adding a cache layer in `TMFBackedLeaseHolder` that is populated during the initial fetch.

Option 1 is recommended for correctness and simplicity. This would require a slight refactor: instead of filtering on `!leaseHolder.isLeased(e.getId())`, filter based on the lease state already present in the mapped `ContractNegotiation` (which was read from the quote). The `acquireLease` call would still go to the API.

### Race Condition Mitigation

The TMForum API does not provide atomic compare-and-swap operations. Two instances could read the same quote, see it as unleased, and both attempt to write their lease. The last writer wins, and one instance would silently lose its lease.

**Mitigation: Read-after-write verification**

After writing the lease, immediately re-read the quote and verify that the written `leasedBy` value matches this instance's `lockId`:

```java
// In acquireLease, after the updateQuote call:
ExtendableQuoteVO written = quoteApi.updateQuote(activeQuote.getId(), updateVO);

// Verify we won the race
ContractNegotiationState writtenState = written.getContractNegotiationState();
if (!lockId.equals(writtenState.getLeasedBy())) {
    throw new IllegalStateException(
        String.format("Lost lease race on %s: expected %s, got %s",
            negotiationId, lockId, writtenState.getLeasedBy()));
}
```

Since `updateQuote` returns the updated quote as response, we can verify from the response directly. If the TMForum API serializes the full object back (including the fields we just wrote), this check confirms our write landed. For stronger guarantees, do a separate GET after the PATCH.

### `LockManager` Keeps Its Role

The `AutomaticUnlockingLockManager` continues to provide JVM-local thread synchronization. It prevents two threads in the same instance from entering `nextNotLeased` or `findByIdAndLease` simultaneously. The distributed lease (via TMForum) prevents two instances from working on the same negotiation.

### Wiring

In the extension that creates the `TMFBackedContractNegotiationStore`, replace:

```java
// Before:
LeaseHolder leaseHolder = new HashMapLeaseHolder(monitor, Clock.systemUTC());

// After:
LeaseHolder leaseHolder = new TMFBackedLeaseHolder(quoteApiClient, controlplane, Clock.systemUTC(), monitor);
```

## Untested Parts of the Current Implementation

The following aspects of the current lock and lease mechanism lack test coverage or have incomplete coverage:

### 1. `AutomaticUnlockingLockManager` — No Unit Tests

No dedicated tests exist. The scheduled auto-unlock (10s emergency release) and timeout behavior (10.5s) are untested.

**Note:** The `writeLock` method's emergency unlock (lines 81-88) calls `lock.readLock().unlock()` instead of `lock.writeLock().unlock()` — this is likely a bug. The scheduled task tries to unlock a read lock when it should be unlocking a write lock.

**Tests to write:**

```java
class AutomaticUnlockingLockManagerTest {

    @Test void readLock_executes_work_and_returns_result()
    @Test void writeLock_executes_work_and_returns_result()
    @Test void readLock_throws_LockException_on_timeout()
    @Test void writeLock_throws_LockException_on_timeout()
    @Test void readLock_allows_concurrent_readers()
    @Test void writeLock_blocks_concurrent_writers()
    @Test void writeLock_blocks_concurrent_readers()
    @Test void emergency_unlock_triggers_after_stall()
    @Test void emergency_unlock_cancelled_on_normal_completion()
}
```

### 2. Lease Not Freed on Exception During `save()` State Handling

The `save()` method always frees the lease in the `finally` block, which is correct. However, there is no test verifying that the lease is freed when one of the `handle*States()` methods throws. The persistence tests verify `acquireLease` and `freeLease` are called once each for the happy path, but:

**Tests to write:**

```java
// In TMFBackedContractNegotiationStorePersistenceTest:

@Test void save_frees_lease_when_handleInitialStates_throws()
@Test void save_frees_lease_when_handleRequestedState_throws()
@Test void save_frees_lease_when_handleAgreeStates_throws()
@Test void save_frees_lease_when_handleFinalStates_throws()
@Test void save_frees_lease_when_acquireLease_throws()
```

### 3. `nextNotLeased` — Lease Not Freed on Partial Failure

If `nextNotLeased` acquires leases on negotiations 1 and 2, but the stream processing fails after that (e.g., during `.limit()` or after), the already-acquired leases are never freed. The leases will only expire after the 60s TTL.

**Tests to write:**

```java
@Test void nextNotLeased_leases_persist_after_partial_stream_failure()
// This test documents the current behavior. Whether to fix it is a design decision.
```

### 4. `save()` Acquires Lease Even When Already Held

In the flow `nextNotLeased` -> process -> `save`, the negotiation is already leased by `nextNotLeased`. Then `save()` calls `acquireLease` again. Because `HashMapLeaseHolder.acquireLease` allows re-acquisition by the same `lockId`, this works. But it resets the lease expiry, which could mask timing issues.

**Tests to write:**

```java
@Test void save_reacquires_lease_that_was_already_held_by_same_instance()
@Test void save_fails_when_lease_held_by_different_instance()
```

### 5. `isLeased` Field in `ContractNegotiationState` Ignored

The `isLeased` field is read from the TMForum Quote JSON when deserializing, but the store's `nextNotLeased` and `findByIdAndLease` methods never check it — they only check the `HashMapLeaseHolder`. A quote could have `isLeased: true` in TMForum and the store would treat it as unleased.

**Tests to write:**

```java
@Test void nextNotLeased_ignores_isLeased_field_from_quote()
// This documents the current behavior and will change with the new implementation.
```

### 6. `TMFLeaseContext` — Minimal Coverage

`TMFLeaseContext` (used for TransferProcess, not ContractNegotiation) has no tests. Its `scheduleBreak` reschedules on failure, which could loop indefinitely.

**Tests to write:**

```java
class TMFLeaseContextTest {
    @Test void acquireLease_succeeds_when_not_leased()
    @Test void acquireLease_throws_when_already_leased()
    @Test void breakLease_removes_lease()
    @Test void lease_auto_breaks_after_timeout()
    @Test void acquireLease_succeeds_after_auto_break()
}
```

## Implementation Steps

### Step 1: Write Tests for Untested Parts

Implement tests for all untested aspects listed in the "Untested Parts" section:

- `AutomaticUnlockingLockManagerTest` — tests for timeout, concurrent access, emergency unlock
- `TMFLeaseContextTest` — tests for acquire, break, auto-break
- Store lease behavior tests in `TMFBackedContractNegotiationStorePersistenceTest`:
  - Lease freed on exception during each `handle*States()` method
  - Lease re-acquisition behavior in `save()` when already held
  - `nextNotLeased` behavior when `isLeased` field is set in quote data

These tests document current behavior before the refactor and verify the contract that the new implementation must satisfy.

### Step 2: Extend `ContractNegotiationState`

Add `leasedBy` (String) and `leaseExpiry` (long) fields with getters, setters, and inclusion in `equals`/`hashCode`. Update all test data builders that construct `ContractNegotiationState` objects.

### Step 3: Create `TMFBackedLeaseHolder`

Implement `LeaseHolder` using `QuoteApiClient` as described above. Include read-after-write verification for race condition mitigation.

### Step 4: Write Unit Tests for `TMFBackedLeaseHolder`

Test against a `MockWebServer` (already a test dependency) to verify HTTP interactions:

```java
class TMFBackedLeaseHolderTest {
    @Test void acquireLease_writes_lease_to_quote()
    @Test void acquireLease_throws_when_leased_by_other()
    @Test void acquireLease_succeeds_when_lease_expired()
    @Test void acquireLease_succeeds_when_leased_by_same_lockId()
    @Test void isLeased_returns_true_for_active_lease()
    @Test void isLeased_returns_false_for_expired_lease()
    @Test void isLeased_returns_false_when_not_leased()
    @Test void isLeasedBy_checks_lockId()
    @Test void freeLease_clears_lease_fields()
    @Test void freeLease_handles_multiple_quotes()
    @Test void acquireLease_race_detection_via_read_after_write()
}
```

### Step 5: Update `save()` to Propagate Lease State

Modify `updateQuote()` and `createQuote()` private methods in `TMFBackedContractNegotiationStore` to set `isLeased(true)`, `leasedBy(lockId)`, and `leaseExpiry` on the `ContractNegotiationState` they build. The `terminateQuote()` method should also propagate lease state.

Expose `lockId` to these methods (it is already an instance field).

For `leaseExpiry`, either:
- Store the expiry as an instance field updated by `TMFBackedLeaseHolder.acquireLease`, or
- Compute it as `clock.millis() + DEFAULT_LEASE_DURATION` at write time.

### Step 6: Optimize `nextNotLeased` to Avoid N+1 API Calls

Refactor `nextNotLeased` so that the lease state check uses the data already fetched from `getNegotiations()` rather than calling `isLeased()` per negotiation. Only the `acquireLease()` call should hit the API.

This requires either:
- Extending the `ContractNegotiation` object to carry lease metadata (not ideal — it's an EDC type), or
- Maintaining a short-lived cache in `TMFBackedLeaseHolder` populated during the initial bulk fetch, or
- Changing `getNegotiations()` to return a richer type that includes lease state alongside the `ContractNegotiation`.

The simplest approach: add a method to `TMFBackedLeaseHolder` like `isLeased(String negotiationId, ContractNegotiationState knownState)` that can answer from already-fetched data without an API call.

### Step 7: Wire `TMFBackedLeaseHolder` in the Extension

In `TMFContractNegotiationExtension` (or wherever the store is constructed), replace `HashMapLeaseHolder` with `TMFBackedLeaseHolder`.

### Step 8: Fix `AutomaticUnlockingLockManager` Bug

In the `writeLock` method (line 85), the emergency unlock calls `lock.readLock().unlock()` instead of `lock.writeLock().unlock()`. Fix this.
