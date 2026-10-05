# Experimental frame-local shadow frustum coherence

Candidate implemented in isolated worktree
`/Users/pippo/.codex/worktrees/frustum-coherence/shaderlod` from `5a06ccd`.
Main checkout is untouched. After the coordinator released a CPU window,
`./gradlew build compileDevClientJava` passed with all 29 JUnit tests, including
nine new coherence fixtures. No game or benchmark was launched by this worker.
This is a default-off independent option, with no performance claim.

## What is avoided

Several Sodium section AABBs can occupy the same small spatial neighborhood
during one frustum traversal. A proven containing coarse box permits its valid
contained children to return visible without repeating all plane FMAs and
rejecting distance-box tests. Partial/outside/unproved coarse boxes still use
the exact original per-child path. No section is dropped, no shader budget or
radius is changed, and every visibility graph traversal still runs.

`ShadowFrustumCoherence` uses 64 fixed direct-mapped slots. The cell key comes
from each child minimum endpoint on a 64-block grid; its containing proof box
extends 96 blocks along each axis. Every call verifies actual float containment
and child finite/ordered endpoints before consulting a proof. Large boxes that
do not fit, NaNs/infinities, inverted endpoints and unknown/malformed planes
fall back. Hash collisions compare all three cell coordinates and never reuse
another cell's proof. Negative proof entries only avoid repeated coarse proof;
they never reject an individual child.

The helper owns three `int[64]` key arrays and one `byte[64]` proof array: 832
raw bytes plus headers/object fields, roughly 1 KiB per initialized frustum,
subject to actual JVM layout. It is lazily allocated on the first enabled
candidate test. There are no per-child allocations, reflection, config reads
or GL calls. New-frustum initialization still allocates this bounded storage,
which can be a material cost if Iris creates frusta every frame.

## Strict proof, rather than trusting Iris's INSIDE code

Pinned Iris's positive acceptance evaluates

`fma(px,x, fma(py,y, pz*z)) >= -pw`

while its negative/fully-inside expression folds `pw` into a third nested FMA.
Those have different rounding near the boundary. Its return value 1 cannot be
used as a general float-safe containment proof.

A concrete counterexample is plane `(1e20f,-1e20f,-1f,-0.5f)`, coarse AABB
`(1,0,0)..(2,1,1)`, and its contained point `(1,1,1)`. The original full
classifier can call the coarse box fully inside while rejecting the point,
under both FMA and separate arithmetic. A written test captures that behavior.

This candidate instead evaluates the **original positive expression** at the
coarse box's sign-selected minimum and maximum vertices, with the original
FMA policy and grouping. It requires finite plane coefficients and finite
results at both extrema, plus minimum result `>= -pw`. Finite contained child
positive vertices are bounded componentwise in the coefficient-selected order.
Float multiplication/addition/FMA with fixed finite coefficients preserve
that weak order; finite extreme evaluations exclude intermediate overflow/NaN
cases that could break the acceptance implication. Signed zero comparisons
remain Java `<0`, not raw sign-bit tests, and tangency remains inclusive.
Thus every accepted cached child satisfies its original positive acceptance
on every plane. Unproved values retain original behavior through fallback.

The coarse box must also lie **fully inside every rejecting distance box**:
each minimum at least `-maxDistance` and each maximum at most `maxDistance`.
Merely calling `BoxCuller.isCulledSodium(coarse)==false` would prove overlap,
not containment. The accessor reads the immutable pinned `maxDistance` field
without reflection. Missing primary cullers use an unbounded constraint;
NaN/negative limits cannot create a fast proof.

For reversed frusta, `distanceCuller` is rejecting, but the inherited primary
box is an **early acceptance**. A plane proof only needs the rejecting distance
constraint. When no proof exists, the original order remains distance reject,
alternate-box accept, then original classifier (or the separately enabled
old math experiment). The new option does not enable that older option.

## Lifetime and source integration

`ShadowCullingFrustumMixin` retains the pinned testAab fallback and adds a
coherence attempt before it only when the separate cached controller is active.
The base-injected interface exposes that helper to the reversed subclass,
avoiding inherited-shadow declarations in its child mixin.

The pinned `prepare` body first updates its box-culler position, then x/y/z.
The overwrite preserves that exact order and invalidates all slots. The pinned
private `addPlane(float[])` is the sole plane-array insertion/count mutator;
its overwrite preserves assignment/increment and invalidates proofs. Count,
plane-array reference, FMA policy or rejecting-limit changes also reset the
helper's context. Existing plane arrays are only populated during construction
in the inspected pinned implementation. Arbitrary external reflection or
third-party writes into private plane coefficients without these hooks are
not claimed supported; any such mutation producer would need explicit
invalidation before enabling the candidate in that combination.

No CallbackInfo/Operation wrapper is introduced by these overwrites. Inactive
paths add a cached boolean check and prepare/addPlane a nullable helper check;
that overhead is still unmeasured. No observer or timing-arm counters were added.

`rendering.shadowFrustumCoherence=false` is independent of
`rendering.optimizeShadowCulling=false`. The existing client tick performs
configuration/Photon/in-world/fallback checks for both and caches eligibility.
There is no temporal camera/sun key: prepare discards frame-local proofs, so
moving daylight does not depend on a previous frame's geometry. New frustum
objects start with no helper. `ShadowBoxDistanceAccessor` is registered against
the exact pinned Iris BoxCuller layout.

## Pending tests and acceptance

Nine written tests cover contained-child reuse, partial distance overlap,
invalidation/plane-limit-policy reset, direct-map collisions, finite/inverted/
NaN/infinity/signed-zero/extreme fallback, NaN/negative constraints, the INSIDE
counterexample, randomized original-classifier comparisons and reversed prefix
behavior. All nine passed. The property fixture exercises 5,000 plane sets × eight children ×
two arithmetic policies, and requires real fast accepts rather than vacuous
all-fallback success. The explicit INSIDE counterexample passed under both math
policies. These tests use an independent full pinned arithmetic oracle and
distance-box predicate; they do not establish runtime Mixin transformation or
actual scene plane/caster coverage.

Pinned-JAR signature inspection confirmed x/y/z are declared on the base target,
`maxDistance` is a private final double, and the sole addPlane/prepare bodies
match the copied ordering plus explicit invalidation. Production jar inspection
confirmed the helper/accessor are present and developer classes excluded.
Then the coordinator must validate actual
Mixin application and original-versus-enabled ordered section/caster sets in
the same frame, motion, plane tangency, day/night, reloads and optional DH/stack
variants. Default off, coherence on, and old math on are separate controls.

Only then benchmark repeated serial frame/CPU results with original Photon
quality and radius unchanged. Coarse misses/collisions, first-proof work,
allocation per new frustum and containment/hash overhead can outweigh avoided
plane math. A source operation reduction or passing property fixture is not
proof of a useful frame gain. No shadow list/temporal traversal reuse is enabled
by this independent experiment.
