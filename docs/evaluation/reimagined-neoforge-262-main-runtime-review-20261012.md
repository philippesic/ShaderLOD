# Reimagined NeoForge26.2 MAIN runtime review

Accepted for the bounded rendering and public control/recovery claim. No blocking
finding in the reviewed operation. This is independent review of the completed
raw artifacts, not a new client launch or performance qualification.

Evidence root: `dev/logs/concept-transfer-r1/reimagined-neoforge-main/runtime-r1/`.
Read `runtime-report.json`, `run.py`, producer/live-consumer records, source
launcher retirement, release and admission records, and the
[prepared input contract](reimagined-neoforge-262-main-input-preparation-20261011.md).
Reviewed against current EVALUATION and AGENT_OPERATIONS guidance.

## Actual observations

Apple M3 Max, OpenGL4.1 Metal91.7, NeoForge26.2.0.88, Iris1.11.4+mc26.2,
Sodium0.9.2+mc26.2; DH absent. All four stages were focused native1920×1200
fullscreen, selected pack equals loaded pack, shader in use, no Iris fallback.

| Stage | UTC capture 2026-10-13 | Loaded options | Selected graph |
| --- | --- | --- | --- |
| Original | 05:59:51.180767 | 398 | Native Reimagined r5.9.3 |
| Balanced On | 06:00:24.578428 | 402 | Generated maintained recipe |
| Quality | 06:00:57.641211 | 398 | Native Reimagined r5.9.3 |
| Off | 06:01:30.768444 | 398 | Native Reimagined r5.9.3 |

Independent read-only Python verification compared the complete four loaded maps
against `expected-options.json`; On is exactly native398 plus four declared
owned/helper options. TAA=true, style1 and RP1 remain unchanged. Recomputed MAIN
JAR SHA256 matches the delivered a92a2853 artifact. Reassembled every expected
archive member using the recipe and additions inside that actual JAR, then
compared every generated member byte and complete member set. Only the two
selection metadata additions are outside that assembled source set. Marker
recipe hash and actual generated SHA match; terminal selection state is inactive.
All237 canonical world files independently still match the frozen hashes.

Every stage has nine positive MAIN counter deltas, with DEV corrected queries
zero, family=reimagined, native validation unchanged, descriptor1→16 and identical
slot0 identity. Counters are cumulative host query/GL-entry/draw-body observations;
stage deltas use the previous stage observation (zero for Original). They are
neither sample counts nor GPU timings. Five public consumer class origins and
the compatibility facade point to the delivered MAIN JAR, separately from DEV
observation classes. Saved kernel arguments bind that JAR as MAIN and the
compiled observation outputs as DEV.

## Consumer and recovery trace

The authentic producer binds the ModDev inputs, then the finite caller checks
source/artifact freezes and preflight before supervision. NeoForgeRoundtrip's
volume stage calls `ShaderLodSettings.apply(stageEnabled(), preset)` directly.
That public entry preflights the backend, prepares PackSelectionTransaction,
persists configuration, reloads Iris and rejects fallback. Transaction and
PackSelectionService validate original/recipe/state before their writes; Quality
and Off choose the native graph and clear active selection ownership. Their
public Apply calls actually returned true here. Off's full loaded map and Core
config equal Original. Cancel presses the actual settings-screen button after
staging ON/Quality, returns to the world and leaves configuration/options/pack
selection unchanged. Physical keyboard/F8 input was not exercised.

Inspected the failure branch: known admission refusal preserves the current
selection; other exceptions restore previous Core values and owned selection
preimages, then attempt guarded native recovery reload and finalize ownership.
Those exception branches were not triggered by this successful runtime trial;
the prior accepted source/CPU recovery evidence remains its basis.

Compatibility's cheap render-thread/pass/world/fallback/queue/API/resource/backend
and exact-stack guards precede source certificate work. Exact complete option
family selection is followed by archive/recipe/state certification. Native draw
validation is retained; context is restored in nested draw wrappers' finally
blocks. Source stamp hashing remains intrinsic overhead and was not timed here.
Reused the seven saved authentic launcher mutation refusals rather than rerunning
a broad suite or launching a second operation.

## Quality and release limits

Viewed the actual Original and On PNGs. Both show the same landscape, clouds,
water and terrain without an obvious gross visual failure. Temporal cloud,
foliage and entity changes prevent treating these stills as a pixel-equivalence
test. No motion inspection, linked uniform readback, executed volumetric sample
reduction, GPU completion, speed, PBR or DH claim is accepted from this operation.
Exact prelaunch sidecar-byte restoration also remains unproven; loaded option
restoration is proven. Prompt captures may contain a prior framebuffer.

`release.json` records zero clients, free lock, no owned processes and no errors
at06:01:35.586281UTC. The retirement record follows exact launcher86086 through
natural exit to absence, without a signal or broad kill. Independent fresh `ps`
for recorded client86116 and launcher86086 found neither present during review.
This checks the completed Reimagined identities; ownership of subsequent work
remains the parent's responsibility. No jobs were started or stopped by review.

Next decisive evidence for optimization is actual consuming work and an early
matched performance/quality comparison; successful rendering and public recovery
alone do not show original-relative gain.
