# BSL / MakeUp speed replication R1 source handback

One new private BSL temporal shadow-filter candidate is ready for parent-controlled
rendering and an early averaged-frame screen. No shader gain, rendering or quality
result is claimed here. MakeUp's renderer bracket belongs to the parent; this
worker did not duplicate it. No owned live jobs remain.

BSL10.1.8 and MakeUp9.5g both contain native temporal AA but neither authentic
archive contains a native scene-resolution/upscaler option analogous to Photon's
TAAU. TAA presence alone does not establish a valid resolution-remapping route.
BSL archive SHA256 `36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5`;
MakeUp archive SHA256 `0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8`.
Both remain unchanged at `dev/upstream/other-pack-research/`.

[BSL original budget](bsl-original-pbr-gpu-budget-r2.md) measures whole
opaque/cutout terrain mean3.743478ms and whole deferred1 mean1.129436ms against
separately sampled game-render mean9.618206ms. These are rotating scope samples,
not additive costs or PCF attribution. [MakeUp original budget](makeup-original-cost-r1.md)
records terrain mean3.945292ms, deferred mean0.123010ms and separately sampled
frame mean7.132403ms. Its deferred AO alone has insufficient measured scope to
explain the requested approximately9% practical gain. Closed BSL PCF footprint,
raw-field, caches, rough gather and MakeUp alpha/cloud/MRT/fade trials stay closed.
The initially considered existing rough-gather artifact was found closed in its
actual runtime review; no attempt to reuse or tune it was made.

## Fixed BSL candidate

[Builder](../../patches/experiments/speed-replication-r1/bsl/build.py) and
[checker](../../patches/experiments/speed-replication-r1/bsl/check.py) operate on
pinned authentic originals and write only new ignored private artifacts. The
builder refuses an existing output. It preserves BSL's Capt Tatsu credit,
https://capttatsu.com, all309 original members, and adds a private notice. Four
upstream members change;305 remain byte-identical. BSL's All Rights Reserved
boundary is retained; no publication/distribution grant is inferred.

Final artifacts are in `dev/logs/speed-replication-r1/bsl/final/`:

| Arm | Archive SHA256 |
| --- | --- |
| Original | `36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5` |
| Disabled | `f27adc66ddc7d8e60715f5566ba618015d3d80dd66d36bf5ceeb878616c496d4` |
| Native-work control | `869b080d82a25c6833c1579bb818373c3ad1b5ddb7f3906b0c2fdef6a62c8f99` |
| Candidate | `f95cac272bbe97de95026baace1ff9f249b57307a9d5ee3e82fc02a618178412` |
| Untimed execution cue | `6d638a10c7c698fec808b69005ec93ba463bf11334fd0c2d0472bba9e7a5985f` |

Filenames are `BSL-speed-replication-r1-{arm}-private.zip`, where native-work
control uses `control`. Each has an adjacent `.zip.txt` and `{arm}-options.json`.
[Manifest](../../dev/logs/speed-replication-r1/bsl/final/manifest.json) pins every
member, archive, sidecar, authentic option input, builder and mod transport source.
Initial and source-ready variants outside `final/` are superseded, retained,
never launched. The intermediate source-ready variant used explicitLOD0 color
sampling; final restores the exact native texture2D call expression.

Entry is native `world0/gbuffers_terrain.fsh` →
`program/gbuffers_terrain.glsl` → `forwardLighting.glsl` →
`shadows.glsl:GetShadow` → native9 or private3 `SampleFilteredShadow` equivalent
role. The complete native9 helper remains verbatim. The reduced helper uses
native positions `phase, phase+3, phase+6`, with phase=`frameCounter mod3`.
Three frames partition all9 native positions exactly once. Position offsets,
hardware comparison calls, native colored sample call, color-layer conditional,
caustic multiplier, subsurface composition and final clamp are preserved in role;
normalization becomes3 for the3-sample approximation. Native SSS12, shadow
production, POM, AO, reflections, AA, textures, history and other passes stay native.

Runtime admission requires existing typed Bridge2/Enabled1/Performance1 and
finite ordered positive `shaderlodNear`/`shaderlodFar` (far<=4096). At distance
<=Near the exact native9 executes. Beyond Far private3 executes; between them,
native Bayer8 coordinates compare against smoothstep(Near,Far,distance) to choose
one complete kernel. The transition never executes both9 and3. There is no new
world-distance constant, random seed, history or cadence of shadow-map rendering.
This stochastic spatial transition approximates the mixture; native TAA is
unchanged. The native counter chooses the3-position phase without freezing time.

Modes are `SHADERLOD_BSL_SPEED_R1=0` disabled,1 native-work control,2 candidate,
3 untimed cue. Static supported paths require exact Iris10812/MC12101, Overworld
terrain fragment, native TAA mode0/nonselective, filtered shadows, no DH/Voxy,
curvature or retro mode. Other callers retain native helpers. Core Off/Quality,
missing bridge, missing preset(-1), unknown presets and invalid distances take
native9. The planned complete native384 map has SHADOW_BIAS0, no HALF_LAMBERT,
native TAA+FXAA; protected distance therefore uses unmodified receiver position.
Alternate bias/normal-offset distances are outside this fixed planned map and
have no quality acceptance. Faithful recovery selects the untouched original
or mode0. Product Apply/Cancel/failed-Apply integration remains parent-owned.

Cue3 sets magenta only after the reduced helper actually returns, at the final
terrain color output. It is never a timing/ordinary-quality arm. Absence in a
near-only scene does not establish zero execution elsewhere.

## Expected benefit, risks and authentic launch inputs

Primary depth-comparison sites fall9→3 in fully distant eligible receivers;
conditional colored-layer sampling sites fall18→6 when both colored branches
execute. Phase changes can alter that branch incidence, so physical transactions
are not proven monotonically reduced. The candidate adds uniform checks,
receiver length, smoothstep, Bayer evaluation, a branch, phase arithmetic and
register/code footprint. Native-work control may have unused classification
compiler-eliminated and is not an overhead-equivalent denominator.

Expected total-frame benefit is uncertain. This targets substantive active
forward terrain work; approximately9% FPS would require about8.3% frame-time
reduction (around0.8ms in the9.62ms original budget). The measured terrain wrapper
contains enough cost in principle, but native PCF's fraction and distant coverage
are unknown. No hard9% source gate or prediction is claimed. All overhead and
unaffected terrain/shadow/deferred work remain in the absolute-original cost.

The mean of linear depth-comparison values over3 stationary frames matches
native9 mathematically. Native nonlinear color/subsurface output, moving casters,
phase-dependent colored-layer branches, TAA rejection and flicker do not obey
that equality. Thin casters, colored glass, foliage shadow edges and camera
motion are specific quality risks. Near native cues are preserved by the fixed
Near region. This approximation must earn ordinary still/motion acceptance.

Authentic PBR saved plan is
`dev/logs/cross-shader-performance-r3/bsl-original-pbr-gpu-budget-r2/plan.json`;
its native map is `expected-native-pbr384.json` in that same directory.
Corresponding retained client/world/resource inputs are under
`run-workloads/cross-shader-performance-r3/bsl-original-pbr-gpu-budget-r2/client/`,
with Replete source
`dev/upstream/height-material-input/replete-1.2.0+mc1.20-1.21.4.zip`, SHA256
`6a967ac89bd1f05c42c0d0fa2f606b5b894b3b52bcc624c72c48b33a1d48a2c6`.
Its captured control is
`dev/logs/cross-shader-performance-r3/bsl-original-pbr-gpu-budget-r2/control-off.json`;
freeze/launch/current-source and world ancestry are documented in the original
budget report. That near wall is approximately9blocks from the camera: with a
larger Near value it intentionally cannot exercise distant reduction. Do not
change the fixture or Near merely to manufacture activation. An existing normal
coastal BSL plan is `dev/logs/bsl-sanity-r7/original-plan.json`; it is historical,
not new runtime admission. Parent must reuse whichever already saved scene/map
matches the chosen unchanged benchmark domain, preserving every native option.
Final maps here derive from the authentic PBR384 input, not an invented coast map.

One parent-owned finite compile/render/ordinary check and a short existing
averaged-window original → mode0/native-work control → candidate → original
return screen should decide the trial. Read actual loaded mode/native options,
core and the existing source/cue if needed; no new GPU observer is required.
Measure elapsed window time/rendered-frame count, retain drift, preserve native
fullscreen/AA/scene, and reject conspicuous still or motion artifacts. Do not
rescue a failed fixed trial by changing phase, support, scene, preset or thresholds.

## Checks actually run and remaining boundaries

`python3 patches/experiments/speed-replication-r1/bsl/build.py` and
`python3 patches/experiments/speed-replication-r1/bsl/check.py` passed. Final
[checks receipt](../../dev/logs/speed-replication-r1/bsl/final/checks.json) records25
checks: authentic5-arm assembly/maps/member preservation;17 complete-entry
preprocessed token comparisons for mode0, unsupported Iris/MC, DH/Voxy/curvature,
TAA-off and unchanged caller/dimension/vertex paths;4 complete-shadow-helper
GLSL330 parser cases; native9 identity; complete3-phase support and guarded
execution-cue ordering. Parser/preprocessor checks are source evidence only.
No Gradle, Java client, shader reload, driver rendering, actual consumed uniforms,
still/motion capture, average frame/GPU timing, DH/PBR runtime or port verification
ran in this worker. Parent owns runtime and integration. No live worker jobs.
