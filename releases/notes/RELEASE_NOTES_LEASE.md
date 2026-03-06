# Release Notes: Distributed Lease Management

## Overview

Replaces the in-memory `HashMapLeaseHolder` with `TMFBackedLeaseHolder`, a distributed lease manager that persists lease state through the TMForum Quote API. This enables multiple EDC controlplane instances to coordinate access to the same contract negotiations without relying on shared in-process memory.

## Changes

### Distributed lease persistence (TMFBackedLeaseHolder)

- New `TMFBackedLeaseHolder` implementation of the `LeaseHolder` interface that stores lease metadata (holder ID, expiry timestamp) on each quote's `ContractNegotiationState` via the TMForum Quote API.
- Supports lease acquisition, renewal, expiry-based reclamation, and release across all active quotes for a negotiation.
- Uses read-after-write verification to detect race conditions during lease acquisition.
- Queries the TMForum API with server-side state filtering (`findByNegotiationIdAndStates`), requesting only quotes in active states (pending, inProgress, approved, accepted) to reduce data transfer.
- Retains client-side controlplane filtering to ensure each instance only manages its own quotes.

### ContractNegotiationState extended with lease fields

- Added `leased` (boolean), `leasedBy` (String), and `leaseExpiry` (long) fields to `ContractNegotiationState`.
- All fields use fluent setters consistent with the existing API style.

### Lease field preservation in quote updates

- Added `preserveLeaseFields()` helper in `TMFBackedContractNegotiationStore` to carry forward lease metadata when updating or terminating quotes, preventing lease state from being silently dropped during state transitions.

### QuoteApiClient: server-side state filtering

- Added `findByNegotiationIdAndStates(String negotiationId, Collection<QuoteStateTypeVO> states)` method that passes a `state` query parameter to the TMForum API for server-side filtering.

### Redundant isLeased() check removed from nextNotLeased

- Removed the `leaseHolder.isLeased()` pre-filter from `nextNotLeased()`. The subsequent `acquireLease()` call already rejects already-leased negotiations, making the pre-check redundant.
- Extracted `fetchQuotesByNegotiationId()` and `toNegotiations()` helpers from `getNegotiations()` to improve readability.

### Extension wiring

- `TMFContractNegotiationExtension` now instantiates `TMFBackedLeaseHolder` instead of `HashMapLeaseHolder`, injecting the `QuoteApiClient`, controlplane ID, clock, and monitor.

### Bug fix: writeLock emergency unlock

- Fixed `AutomaticUnlockingLockManager.writeLock()` emergency timeout calling `lock.readLock().unlock()` instead of `lock.writeLock().unlock()`. The bug meant a stalled write lock would never be released by the timeout, and would throw `IllegalMonitorStateException`.

## Test coverage

- **TMFBackedLeaseHolderTest**: 29 tests covering lease acquisition (including race detection, expiry, cross-controlplane isolation), `isLeased`/`isLeasedBy` queries, lease release, and state field preservation.
- **TMFBackedContractNegotiationStoreLeaseTest**: 12 tests covering lease lifecycle during `save()` (acquire/free on success and failure), partial acquisition failures in `nextNotLeased`, and interaction between TMForum lease flags and in-memory lease state.
- **TMFLeaseContextTest**: 6 tests for the in-memory `TMFLeaseContext` lease coordination.
- **AutomaticUnlockingLockManagerTest**: 14 tests covering read/write lock semantics, timeout behavior, and the emergency unlock fix.

## Files changed

| File | Change |
|------|--------|
| `ContractNegotiationState.java` | Added `leased`, `leasedBy`, `leaseExpiry` fields |
| `TMFBackedLeaseHolder.java` | New distributed lease holder implementation |
| `QuoteApiClient.java` | Added `findByNegotiationIdAndStates()` |
| `TMFBackedContractNegotiationStore.java` | Lease field preservation, extracted quote fetching, removed redundant `isLeased()` |
| `TMFContractNegotiationExtension.java` | Wired `TMFBackedLeaseHolder` |
| `AutomaticUnlockingLockManager.java` | Fixed emergency unlock bug |
