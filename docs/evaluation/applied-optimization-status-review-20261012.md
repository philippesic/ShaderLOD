# Independent review: applied optimization status, NeoForge 26.2

Current accepted delivery is **r5**; the final supplement below records the
empty-selection repair. The r4 review is retained as the unchanged base evidence.

Accept final r4 as a reviewed source/CPU candidate for the requested one-loader
slice. It distinguishes verified original Off/Quality, shader controls applied,
certified native-renderer route, inactive controls, unavailable evidence and
failure/refusal. Requested screen controls remain pending until Apply succeeds.
No open blocking finding remains. Parent owns integration; this report does not
claim runtime adoption, GPU work reduction or project completion.

The [five-file patch](../../dev/logs/offline-delivery-sprint-20261012/settings/candidate-r4/integration.patch)
SHA256 is `60cbb91f7e0916fadd93953fddadce223d5e3c36232d815b5589f98573760c4f`.
The [independent receipt](../../dev/logs/offline-delivery-sprint-20261012/settings-independent-review/receipt.json)
records exact original/candidate hashes and fresh standalone Javac/JUnit output.
Original sources still match all five preimages; no sealed source was edited.

## Findings resolved before acceptance

The initial r1 observer called Iris `location(name, INT)` after `uniform1i`
already registered that name. This is a mutating registration operation, not
a read-only location/type query: pinned `ProgramUniforms.Builder.location`
rejects duplicates, so the observation would always miss the linked bridge and
log duplicate-uniform warnings. The independent review confirmed this against
the actual local Iris1.11.4+mc26.2 JAR bytecode, saved in
[API evidence](../../dev/logs/offline-delivery-sprint-20261012/settings-independent-review/iris-uniform-api-javap.txt).
Actual type validation occurs later in `buildUniforms`, which removes suppliers
whose provided type differs from the linked program. r4 has no extra location
calls and instead observes existing supplier reads.

Other corrected boundaries are retained in tests: Photon uses its actual
`SHADERLOD_LIVE_PIXEL_BUDGET` key rather than a non-Photon recipe kind lookup;
committed Off/Quality cannot overrule an observed still-On bridge; enabled
generated controls with no consumed bridge stay unavailable even for Off/Quality;
unknown/private sources do not become claimed originals just because settings
are Off; disk-original versus loaded budget-option/bridge evidence is refused;
and the screen explicitly requires a world for active-pipeline status.

These were source/preparation findings resolved before any game run. r1/r2
artifacts remain historical; this acceptance is only for pinned r4.

## Actual input, consumer and recovery path

`PackSelectionService.inspectSelection` reads the selected archive once when
settings opens. It uses the shared exact source catalog and existing marker,
state and recipe checks; it neither changes selection nor generates files.
Current originals and assembled/legacy exact derivatives remain distinct;
unknown, malformed, tampered and stale-state cases cannot provide shader-route
success. A marker-free manual derivative can be described only if its full
shader identity is already recognized. Arbitrary private experiments remain
unverified.

The NeoForge26.2 screen joins that descriptor to the same live pack object/name,
Iris selected name, loaded declared option values, current pipeline/notifier,
world presence, committed config, fallback flag and the existing renderer
certificate. It does not use the queued option map or pending screen fields to
assert an applied state. A pack change requests reopening; shader fallback wins
over success. Cancel still only closes the screen; pending On/preset changes are
not saved. Last Apply failure/refusal survives reopening and clears only on a
successful Apply, while the separate status describes current evidence.

Public Apply captures native renderer availability before entering the suspended
Apply lifecycle. This fixes the earlier ordering that could ask availability
only after suspension had forced it false. Transaction creation then receives
the captured Boolean through the existing `iris(boolean)` entry. The shared
selection decision still selects eligible Performance's original graph and
Balanced's generated graph; Quality/Off, owned option restoration and fallback
recovery retain their previous operations and semantics. The patch only adds
failure feedback and the correctly ordered snapshot around that existing path.

The existing `MakeUpQuadRuntime.enabled()` supplies the renderer status; despite
its historical class name it checks the allowed family routes, exact stack/API,
native source/options, dimension/resources, pipeline and capabilities. This
review inspected that unchanged source. CPU tests model its availability result
explicitly; **no live GPU capability/source certificate was evaluated**.

## Shader transport observation

One `BridgeObservation` is allocated per uniform-holder/program registration.
The existing three version/enabled/preset suppliers retain their returned values
and update a three-bit observation tied to the exact immutable current frame.
Only all three reads from that one observer and one frame publish consumed
transport. Different programs, old frames or a single active uniform cannot
combine into a false positive. `appliedBridge` additionally requires the current
notifier, begun valid world frame, same frame identity and unchanged world.

Pinned Iris bytecode confirms that absent locations do not install suppliers,
wrong uniform types are removed before program updates, constructors do not
sample them, and retained per-frame suppliers are called from uniform updates.
The replacement avoids interfering with uniform registration. This is evidence
of the mod's coherent values reaching retained program suppliers; it is not a
GL uniform readback, proof that a particular sample budget reduced its count,
proof of a draw/dispatch, or FPS evidence.

For a recognized generated graph, actual adapter option and Photon AA/TAAU
conditions must permit the mechanism, and observed bridge On/preset must agree
with committed settings before SHADER is shown. Missing or conflicting evidence
is explicit. For a recognized original graph, live renderer eligibility plus
Performance gives RENDERER; otherwise original/Off/Quality text remains accurate.
An original descriptor accompanied by a generated budget option or consumed
bridge reports a loaded/source mismatch, covering same-name file replacement
before screen opening without adding a filesystem watcher.

## Verification actually run

I independently regenerated final r4 into
`dev/logs/offline-delivery-sprint-20261012/settings-independent-review/candidate-r4-reproduction`,
matched all five output source hashes to the frozen candidate, compiled with the
existing actual NeoForge26.2/Minecraft/Iris/Sodium dependencies and current
consumer sources, and executed **42 JUnit tests, zero skips/failures**.
There are zero stub dependency classes. Existing mod-config persistence and
event bus are exercised through real temporary FML configuration objects;
world identities, reload callbacks and renderer availability are explicit test
inputs, not live Minecraft behavior.

The executed cases include real public selection/Apply consumers over authentic
pack archives, the nine-family original→generated→Off inspection matrix,
Performance renderer versus generated fallback, Balanced, Quality and Off,
Photon AA fallback, pre-suspension availability ordering, option queues,
failed Apply and shader fallback restoration, preservation of independently
edited files, refusal without reload, stable config fields, persistent failure
messages, mixed program/frame observations, world/notifier changes, missing
bridge Off/Quality, unknown source and replaced-file contradictions. Existing
core-frame and backend-refusal consumers were included. Expected injected
failure logs and JVM dependency warnings are preserved in raw output.

The worker also ran four focused Python preparation tests; I inspected their
source and receipts rather than repeat unchanged preparation cases. They cover
exact preimage refusal, output containment/no overwrite and ambiguous anchors.
My independent `git apply --check` returned0 for final r4. Actual commands/logs
are beside the independent receipt. No Gradle, client, GPU, remote or native Goal
operation ran.

| Final source | SHA256 |
|---|---|
| Shared PackSelectionService | `95f872c4a1044550ee4f3727bd7a4ae9fe00c7393a6b7362d4f0773bff013645` |
| Shared CoreUniformFrame | `4936dc77367282cc9d12f3cf8e831fd022a3f57be0183b87f459435145722f2b` |
| NF26.2 CommonUniformsMixin | `7bb8ca876660230b769b933eb776ba53a20f33e181526404409d46112cd9d601` |
| NF26.2 ShaderLodSettings | `0c81625e563bb6c9e866a59bf97bc5c00fe06f61db0446a7149acfd8989d0d86` |
| NF26.2 ShaderLodSettingsScreen | `cba28a882073cd2c9d5d67b2296ea74c64626b63dffa91f9defc84cf5e0cbba6` |

Preparer SHA is `7194f6325f4f9166decbff2c72a21990828018600d906b7b594c5ac341005407`;
test SHA is `45d7f18f256720ce8dd8769f7d67c5d0d87a33cb0e35f71177926380871e2c7c`.

## Limits and next observation

The actual screen and mixin compile against the pinned dependencies, but no
screen was rendered and no runtime mixin transformation, live uniform update,
native renderer certification, reload or GPU execution was observed. Thus layout,
menu interaction and applied text in the real client remain runtime acceptance
work. This one NeoForge26.2 presentation change does not prove other loader/version
screens, even though the shared classifier understands all nine families.

Added host work is bounded: one archive inspection per opened screen, loaded
option collection/status computation while it renders, and three small observer
updates for retained core suppliers. It does not remove GPU work and has no
measured cost or speed benefit. “Optimization applied” describes the selected
and observed mechanism, not a guaranteed nonzero reduction for every preset,
effect, pixel or frame.

Parent's planned scratch integration can join these five accepted sources with
other accepted overlays without changing the paused sealed inputs. The next
actual runtime action remains the existing Reimagined main-JAR rendering and
native Quality/Off/Cancel recovery operation after a future explicit resume.
Testing this new UI later requires a separate built/adopted candidate and a quick
native Performance/generated Balanced/original Quality/Off plus failure/reopen
check; it must not silently substitute for that chosen operation. No owned live
processes remain.

## Final r5 supplement: stable empty selection

Accept r5, which supersedes r4 for source integration. Parent's final review
found that r4's display override always converted a noncurrent/nonfallback state
to “Loaded shader changed; reopen settings,” including opening the screen when
both the inspected and current pack were absent. That state should retain “No
shader pipeline active.” Earlier r4 acceptance missed this empty-selection
presentation edge; its artifact and evidence remain preserved above.

The only r4→r5 product difference is a small helper and its call in
`ShaderLodSettingsScreen`: changed-selection text requires a noncurrent,
nonfallback state with an initially loaded or currently loaded pack. Stable
empty/empty retains the original unavailable detail; appeared/disappeared packs
and identity/name/selected-name divergence with a pack present still request
reopening. An unchanged pack with no world keeps NoPipeline. Shader fallback
detail wins. The existing current predicate, committed versus pending control
separation, classifier, observer, presets, Apply and recovery paths are unchanged.

I independently matched all five r5 source pins, verified the other four product
files are byte-identical to r4, and checked the unchanged sealed preimages. Final
[patch](../../dev/logs/offline-delivery-sprint-20261012/settings/candidate-r5/integration.patch)
SHA is `2e55f9da31ae28261d8ae6aa7efd9f1df9c0333e25f001b5ef3051a53c59e1c5`;
screen SHA is `c6faa21a38762677a17c944cbacbd7766f0c5f222e30083acac06d14e7c93933`.
`git apply --check` passes. Final preparer SHA is
`d3a55b1d06aa6f65fc5bef117d5901e14bc894a5b793af41e5a4b7e2356bd879`;
test SHA is `0d02aca5933b7c4aad142ca491ee1b0a33f10ffbf1bdea3e9a20b9e9709af5f3`.

Worker's final real-dependency run reports43 JUnit tests and four Python tests
passing. I reused the unchanged42-case evidence and independently recompiled the
final sources, then ran only the new actual compiled-screen helper test: **one
JUnit test, seven assertions, zero skips/failures and zero dependency stubs**.
[Independent r5 receipt](../../dev/logs/offline-delivery-sprint-20261012/settings-independent-review/r5/receipt.json)
and adjacent commands/logs retain the pins and result. The static helper executed
without constructing a GUI, starting Minecraft or using a GPU. This verifies the
changed presentation decision, not actual rendering/layout or live screen
interaction; all prior runtime limitations and paused next-action boundaries
still apply. No reviewer-owned live process remains.
