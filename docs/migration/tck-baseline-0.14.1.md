# DSP TCK baseline at EDC 0.14.1

Reference point for the 0.18.1 upgrade (ticket #72, Step 1). Captured **before any
migration work touched the build**, so Step 8 can tell a genuine regression from a
test that never passed. Any test marked PASS here that fails after the upgrade is a
regression to fix, not a result to filter out.

## Run

| | |
|---|---|
| Date | 2026-10-12 |
| `edc.version` | `0.14.1` (`pom.xml:15`) |
| DSP TCK image | `1.0.0-RC6` (`DSP_TCK_VERSION` default, `docker-compose.tck.yml:237`) |
| DSP protocol | `2025-1` (`config/tck/tck.properties`) |
| Command | `./scripts/run-tck.sh` with `TCK_FILTER` unset — the full suite |
| Controlplane | `controlplane-oid4vc`, shaded JAR + `docker/Dockerfile` |
| Result | **65 passed, 0 failed — fully green** |

The runner's own summary:

```
Passed tests: 65
Failed tests: 0
🎉😃🚀All tests passed
```

## The run is only green with one fix applied

The suite does **not** complete on the tree as it stood at the start of this step.
Two attempts hung indefinitely — once at `CN:03-02`, once at `CN:01-02` — against an
unmodified 0.14.1 build.

The cause is in the test harness, not in the connector or in EDC:
`DelayedActionGuard` re-reads its entity under a lease before running the queued
action, and when the state machine still held that lease it logged
`could not lease <id>: ... is already leased` and **dropped the queued entry**. The
entity then stayed `pending = true` forever, no further transition was ever
attempted, and the TCK waited out its timeout. It is a race, which is why the suite
appeared to pass intermittently and why no baseline existed before now.

The guard now re-queues a lease failure on a short delay and only gives up once the
state-machine lease duration has elapsed (commit `fix(test-extension): retry guard
lease instead of stranding the entity`). The table below is the run with that fix in
place; it is the only change between the hanging runs and this one.

Consequence for Step 8: this fix is part of the baseline. Re-running the TCK after
the upgrade must keep it.

## Gaps in the suite, not in this run

- `TP:02-04` and `TP_C:02-04` do not exist in TCK `1.0.0-RC6`. The numbering gap is
  upstream's; nothing was skipped here.
- Five scenario descriptions below read `—`. The runner prints a test's description
  only on its `Started:` line, which the aggregated compose log drops; the verdicts
  are taken from the runner's own `SUCCESSFUL:` / `FAILED:` lines and are complete
  for all 65 tests.

## Results

Group prefixes: `CAT` catalog, `CN` contract negotiation (provider under test),
`CN_C` contract negotiation (consumer under test), `MET` metadata, `TP` transfer
process (provider), `TP_C` transfer process (consumer).

### CAT (3 tests)

| Test | Result | Scenario |
|---|---|---|
| `CAT:01-01` | PASS | — |
| `CAT:01-02` | PASS | — |
| `CAT:01-03` | PASS | — |


### CN (15 tests)

| Test | Result | Scenario |
|---|---|---|
| `CN:01-01` | PASS | Verify contract request, offer received, consumer terminated |
| `CN:01-02` | PASS | Verify contract request, offer received, consumer counter-offer, provider terminated |
| `CN:01-03` | PASS | Verify contract request, offer received, consumer accepted, provider agreement, consumer verified, provider finalized |
| `CN:01-04` | PASS | Verify contract request, provider agreement, consumer verified, provider finalized |
| `CN:02-01` | PASS | Verify contract request, provider terminated |
| `CN:02-02` | PASS | Verify contract request, consumer terminated |
| `CN:02-03` | PASS | Verify contract request, provider agreement, consumer terminated |
| `CN:02-04` | PASS | Verify contract request, offer received, consumer terminated |
| `CN:02-05` | PASS | Verify contract request, offer received, provider terminated |
| `CN:02-06` | PASS | Verify contract request, offer received, consumer accepted, provider terminated |
| `CN:02-07` | PASS | Verify contract request, provider agreement, consumer verified, provider terminated |
| `CN:03-01` | PASS | Verify contract request, provider agreement, consumer verified, provider finalized, invalid consumer terminated |
| `CN:03-02` | PASS | Verify contract request, offer received, invalid consumer verified |
| `CN:03-03` | PASS | — |
| `CN:03-04` | PASS | — |


### CN_C (16 tests)

| Test | Result | Scenario |
|---|---|---|
| `CN_C:01-01` | PASS | Verify contract request, offer received, consumer accepted, provider agreed, consumer verified, provider finalized |
| `CN_C:01-02` | PASS | Verify contract request, offer received, consumer counter-offer, provider terminated |
| `CN_C:01-03` | PASS | Verify contract request, offer received, consumer terminated |
| `CN_C:01-04` | PASS | Verify contract request, provider agreement, consumer verified, provider finalized |
| `CN_C:02-01` | PASS | Verify contract request, provider terminated |
| `CN_C:02-02` | PASS | Verify contract request, consumer terminated |
| `CN_C:02-03` | PASS | Verify contract request, provider agreement, consumer terminated |
| `CN_C:02-04` | PASS | Verify contract request, offer received, provider terminated |
| `CN_C:02-05` | PASS | Verify contract request, offer received, consumer accepted, provider terminated |
| `CN_C:02-06` | PASS | Verify contract request, provider agreement, consumer verified, provider terminated |
| `CN_C:03-01` | PASS | Verify contract request, invalid provider finalized |
| `CN_C:03-02` | PASS | Verify contract request, offer received, invalid provider agreed |
| `CN_C:03-03` | PASS | Verify contract request, offer received, invalid provider finalized |
| `CN_C:03-04` | PASS | Verify contract request, offer received, consumer accepted, invalid provider finalized |
| `CN_C:03-05` | PASS | Verify contract request, offer received, consumer accepted, invalid provider offer |
| `CN_C:03-06` | PASS | Verify contract request, offer received, consumer accepted, provider agreed, invalid provider finalized |


### MET (1 tests)

| Test | Result | Scenario |
|---|---|---|
| `MET:01-01` | PASS | Verify metadata request |


### TP (15 tests)

| Test | Result | Scenario |
|---|---|---|
| `TP:01-01` | PASS | Verify transfer request, provider started, provider terminated |
| `TP:01-02` | PASS | Verify transfer request, provider started, provider completed |
| `TP:01-03` | PASS | Verify transfer request, provider started, provider suspended, provider terminated |
| `TP:01-04` | PASS | Verify transfer request, provider started, provider suspended, provider started, provider completed |
| `TP:01-05` | PASS | Verify transfer request, provider terminated |
| `TP:02-01` | PASS | Verify transfer request, provider started, consumer terminated |
| `TP:02-02` | PASS | Verify transfer request, provider started, consumer completed |
| `TP:02-03` | PASS | Verify transfer request, provider started, consumer suspended, consumer terminated |
| `TP:02-05` | PASS | Verify transfer request, consumer terminated |
| `TP:03-01` | PASS | Verify transfer request, consumer completed |
| `TP:03-02` | PASS | Verify transfer request, consumer suspended |
| `TP:03-03` | PASS | Verify transfer request, provider started, consumer suspended, consumer completed |
| `TP:03-04` | PASS | Verify transfer request, provider started, consumer terminated, consumer started |
| `TP:03-05` | PASS | Verify transfer request, provider started, consumer terminated, consumer suspended |
| `TP:03-06` | PASS | Verify transfer request, provider started, consumer terminated, consumer completed |


### TP_C (15 tests)

| Test | Result | Scenario |
|---|---|---|
| `TP_C:01-01` | PASS | Verify transfer request, provider started, provider terminated |
| `TP_C:01-02` | PASS | Verify transfer request, provider started, provider completed |
| `TP_C:01-03` | PASS | Verify transfer request, provider started, provider suspended, provider terminated |
| `TP_C:01-04` | PASS | Verify transfer request, provider started, provider suspended, provider started, provider completed |
| `TP_C:01-05` | PASS | Verify transfer request, provider terminated |
| `TP_C:02-01` | PASS | Verify transfer request, provider started, consumer terminated |
| `TP_C:02-02` | PASS | Verify transfer request, provider started, consumer completed |
| `TP_C:02-03` | PASS | Verify transfer request, provider started, consumer suspended, consumer terminated |
| `TP_C:02-05` | PASS | Verify transfer request, consumer terminated |
| `TP_C:03-01` | PASS | Verify transfer request, provider completed |
| `TP_C:03-02` | PASS | Verify transfer request, provider suspended |
| `TP_C:03-03` | PASS | Verify transfer request, provider started, provider suspended, provider completed |
| `TP_C:03-04` | PASS | Verify transfer request, provider started, provider terminated, provider started |
| `TP_C:03-05` | PASS | Verify transfer request, provider started, provider terminated, provider suspended |
| `TP_C:03-06` | PASS | Verify transfer request, provider started, provider terminated, provider completed |
