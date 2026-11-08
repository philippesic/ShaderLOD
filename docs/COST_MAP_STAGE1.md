# Cost-map stage 1 implementation

2026-11-08. Implements only [COST_MAP section 11 stage 1](COST_MAP.md#11-delivery-sequence-and-acceptance).
No Minecraft client, GPU workload, benchmark, live capture or machine qualification.

`src/devClient/java/com/pp/shaderlod/dev/timing/costmap/` contains:

- `CostMapRecords`: schema version 1, immutable capture/cohort, DH identity, frame,
  scope/invocation, CPU/GPU event, coverage/health and aggregate records. Capture
  metadata has required typed keys; unavailable values require an explicit fact
  and evidence/reason. DH settings retain exact keys with evidence, alongside typed
  artifact/loader/compatibility/distance/residency identity. Scope feature links
  are metadata only, not attribution or an implemented catalog. No aggregate is
  computed here. Raw durations stay integer nanoseconds. Unknown counts and
  unavailable thread CPU remain null. A measured zero is valid; structural absence
  uses a status/reason with null duration, including absent DH `NOT_APPLICABLE`.
- `ScopeRegistry`: one owner, stable positive integer IDs keyed by graph, stage,
  program, operation and discriminator, with names solely for display. Re-registering
  the same key is idempotent only with identical metadata. Each invocation gets a
  capture/cohort/window/epoch/frame/graph identity, event and parent IDs, execution
  ordinal, thread/context and independent DH stage/world-shadow/opacity context.
  Invocation `observed` describes execution evidence; entry outcomes describe
  measurement results. Every entry has separate explicit CPU/GPU outcomes; a
  missing payload is `UNKNOWN` for that dimension, never implicit zero. Observations
  join by event and dimension; a CPU-only record does not overwrite a GPU result.
  `endMissing` retains an executed but unmeasured invocation;
  `missing` records explicit per-scope absence/eligibility reasons. At boundaries,
  unclassified registered scopes in that graph get `UNKNOWN`, never implicit zero
  or inferred nonexecution. Counts without observation remain null.
- `CounterDelta`: pure unsigned nanosecond-counter decoding, including one bounded
  modular wrap. The caller must independently establish a maximum interval below
  the modulus and change clock generation on reset/disjoint. Raw ticks alone cannot
  distinguish wrap, reset or multiple wraps. No GL query is issued or retrieved.

Limits independently bound registered scopes (including old graphs), active depth,
pending results and combined buffered entries/coverage. Overflow throws
`CapacityException`, stops the collector permanently, retains buffered evidence and
exposes loss/reason through out-of-band health even when no event slot remains.
No eviction or automatic restart occurs. A drain removes only completed buffered
entries; it never resets IDs, loss counters or pending ownership. Consumers must
retain the scope dictionary and health with every batch. Pending/open identities
remain bounded and their health counts remain visible after a capacity stop;
they are incomplete, not measured zero. Rejected records count as lost events;
byte sizes, query-pool limits and live timer diagnostics are null until observed.

Boundary changes invalidate open CPU scopes with a reason. Frame counter restart
requires a greater epoch; graph generations cannot regress and old scope IDs cannot
be used in the new graph. GPU pending and terminal records are append-only state
transitions joined by invocation/event ID, not independent samples to sum. Late
results retain original issue identity plus separate retrieval identity. Close
emits dropped unresolved-tail records without polling or waiting, invalidates open
scopes and disallows further results. It does not mutate previously drained data.
Per-frame records encode warmup, transitions, partial start/end, eligibility,
scope-set IDs and root endpoints; stage 2 will supply actual boundary observations.

## CPU-only acceptance

Run from the checkout with a full JDK 21 (the test helper also finds cached Gradle
JDKs; `SHADERLOD_TEST_JDK` can select one):

```sh
python3 -m unittest discover -s tests -p test_cost_map_contract.py -v
```

The maintained runner compiles only the dependency-free contract and selected
pure timing sources into a disposable temporary directory. It runs
`CostMapContractTest`, `GpuSamplingPolicyTest` and `TimingStatisticsTest` without
Minecraft, OpenGL, native libraries or a display. Missing JDK is a prerequisite
skip, not acceptance evidence.

The synthetic fixtures cover repeated names and recursive/repeated invocations;
balanced, mismatched and duplicate ends; graph changes; late query returns across
epoch/graph boundaries and duplicate/foreign returns; unsigned wrap and reset;
partial frame/root endpoints, open scopes and unresolved tails; scope, event,
active and pending overflow; malformed schema, fields, durations, clocks and
identities. Every missingness status and DH state is represented. A CPU thread
clock may be unavailable; large wall/thread inconsistencies are rejected, with
clock-resolution tolerance. The contract fixture passed 82 checks across the eight acceptance groups; both
adjacent pure timing test mains also passed. Production `jar` also built
and its entries were inspected to exclude dev classes/resources. No runtime
compatibility or cost/overhead claim follows.

## Deferred seams (not authorization)

[COST_MAP stages 2+](COST_MAP.md#11-delivery-sequence-and-acceptance) own live hooks:

- Existing `GpuTimers`, `CpuTimers` and `BottleneckProbe` are unchanged. A later
  capture owner supplies immutable capture/cohort identity and actual frame fences,
  registers graph scopes, calls begin/end or explicit missingness, and preserves
  health. Register the graph inventory before collecting frames to make complete
  missingness meaningful. Unknown/unregistered work requires the later coverage
  ledger; stage 1 cannot discover renderer work by itself.
- A later backend supplies clock capability/generation, bounded counter evidence,
  GPU issue/retrieval records and drain policy. This registry has no query pool,
  query polling, native handles, context access or automatic clock correlation.
- Worker/lifecycle ownership and marshaling, DH observations, mixin changes,
  actual coverage/catalog mappings, capture serialization, report rendering,
  machine validation and Philip's four-pack review remain later work. Settings or
  graph changes must create the appropriate new cohort/epoch/graph identity at
  that boundary; the contract never silently reassigns prior events.
