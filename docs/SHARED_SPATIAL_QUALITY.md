# Shared spatial quality controls

2026-10-05. Executable preparation and scoped source fixtures; no client launch,
rendered tier coverage, visual acceptance or speed claim from this contribution.
The active general LOD goal still requires little discernible quality loss and
1.5 × total rendering throughput against matched original Photon. A successful
PNG sequence is capture integrity evidence, not that quality gate.

[Preparer](../scripts/prepare_shared_spatial_quality.py) is a thin camera-only
wrapper around the [frozen runtime preparer](../scripts/prepare_shared_spatial_runtime.py).
It creates four new independent canonical world copies per route, with the same
held forest option map. It never builds, starts Minecraft, edits original worlds,
or writes personal configuration. The sole runtime operator controls all launches.
Its global no-client guard deliberately prevents profile preparation while a
client is live; this contribution does not bypass that guard.

## Pinned inputs and prerequisite

Use [the source freeze](SHARED_SPATIAL_REFERENCE.md) and
[driver controls](SHARED_SPATIAL_RUNTIME.md) unchanged. The preparer actually
qualifies the full archive inventory/bytes, registration/layout, license, complete
held 366 upstream fields (367 for derivatives), source report, settings and world
before copying. It verifies copied worlds and source preservation afterward.
Source report SHA-256 is
`6f7662a31427f0d0863d926909c67a57b0dfba3c7ee2539c857f699c7ce3da5e`;
canonical map is
`4efc7af7742bc445adf2b3b1454aa448a557e1d4b024103dd5b9f5758ed68745`;
canonical stopped world is
`f752339d854ff2f0e0a7dc4e577dd6aedf2d0f69e39a9a7cae9dd0286c7be08b`.
Exact archive/container/member/dependency hashes are retained in each generated
`shared-spatial-preparation.json`, rather than inferred from names.

Constraints: Minecraft 1.21.1 / NeoForge 21.1.253 / Java 21 / Iris
1.8.12-snapshot+mc1.21.1-local / Sodium 0.6.13 / Photon v1.3b.
DH 3.3.3 remains in the source compatibility runtime and is physically removed
only from these fresh noDH copies. This does not establish DH quality/support.
Held forest: feet `(256.5, 95, 768.5)`, yaw 135°, pitch 9°, time 7000,
clear weather, wetness 0, 24 detailed chunks, native framebuffer 2976 × 1672,
window 1488 × 836. Preserve resource packs, FOV, exposure, source locks and
all effective options; do not substitute a coastal options profile.

Root's first original driver image was inspected read-only at
`dev/logs/shared-spatial-driver-r1/original-final.png`, SHA-256
`e525de1291e2f4e0a0c3fc5c69798c89dc6aedf7ccd86cf355b76e3226a4193e`.
It visibly contains foreground grass/opaque ground, distant forest/shoreline,
water and sky. Those are prospective review regions, with no measured receiver
distances or adaptive parity inferred. The top-left dev HUD and top-right tutorial
are also visible. Camera-quality mode takes its screenshot before the dev HUD
and holds hidden GUI in current `ComparisonClient`; confirm actual absence in
the new captures. If overlays remain, retain explicit per-frame native pixel
masks and compare their union across arms. Masks must cover only overlays,
not hide water/terrain/sky defects. Do not compare this ordinary driver screenshot
against a clean quality capture as whole-image pixel parity.

Before any quality launch, the operator must accept original/private OFF actual
driver controls, then private ON/FULL, and establish actual consuming-stage
identity, typed settings, layout/completion and sampler state separately.
The current typed probe alone leaves stage mapping and physical attachments
unverified. A visible image or copied options is not a substitute.

## Bounded preparation and capture

The three routes use the integrated [camera harness](CAMERA_QUALITY_HARNESS.md):
30-second warmup, 20-second path, 10 FPS, 201 slots, lossless PNG level 0,
maximum two pending images, actual `NONE` camera fluid and full-block rejection.
The original pose is restored at the last waypoint. These are proposed paths,
not surveyed collision-free routes or certified distance coverage. The harness
rejects known fluid/full-block failures; partial shapes and between-slot clearance
remain unproved. Stop and retain a failed route before making a new immutable one.

| Route | Executed request | Purpose and boundary |
| --- | --- | --- |
| still | Fixed original forest pose for 20 s | Repeated original noise floor, original/OFF and FULL parity; no transition motion |
| pan | Yaw 135 → 141 → 129 → 141 → 135° at 0/5/10/15/20 s | Subpixel/tile and thin-edge stability; no radial-distance sweep |
| approach-return | 24 blocks forward along yaw 135°, return; midpoint `(239.52943725152286, 95, 751.5294372515228)` at 10 s | Candidate radial sweep of already identified receivers; travel alone proves no tier |

Prepare each route into a fresh ignored directory only after all clients stop:

```sh
python3 scripts/prepare_shared_spatial_quality.py --route still --output run-workloads/shared-spatial-quality-still-r1
python3 scripts/prepare_shared_spatial_quality.py --route pan --output run-workloads/shared-spatial-quality-pan-r1
python3 scripts/prepare_shared_spatial_quality.py --route approach-return --output run-workloads/shared-spatial-quality-approach-r1
```

Do not launch with the old driver `command` in the nested runtime ledger.
Use the exact argv in `shared-spatial-quality-preparation.json` for each arm:
`original`, `privateOFF`, `privateON-FULL`, then `privateON-ADAPTIVE`.
It activates `cameraQuality` and `cameraQualityOnly`, disables all existing
timers/diagnostics/benchmark activation, removes workload activation, and excludes
Java/resource compilation. The copied old workload request is removed only from
these new profiles. All arms consume identical immutable request bytes and
`forest/control.json`. The quality ledger pins their hashes, requested policy,
full expected options, archive, source registration and copied non-world inputs.
The nested ledger pins source/world/dependency identity. A later new build
requires a new operator fingerprint freeze before launch.

Each sequence has approximately 4 GB of uncompressed native images per arm;
prepare/run one route at a time and verify disk capacity before capture. Avoid
unnecessary duplicate PNGs. A second original noise-control sequence needs a
fresh preparation from the same canonical source, with identical request bytes,
not a reused world changed by a previous capture. Preserve every failed UUID.

The sole operator acquires `dev/logs/.proof-matrix.lock`, records exact launcher
PID/session, Java PID, command, log, world/pack and stop procedure; runs arms
serially with stable focus/visible/non-iconified window and frozen artifacts;
then verifies actual stopped identities before releasing ownership. This script
owns no client or GPU process. It is not a runner and does not create processes.

## Receiver coverage and review matrix

The requested diagnostic policy is near 32 / far 96 / transition width 8 blocks,
medium retention 1 / far retention 0.25. Distances are Euclidean view-space
Minecraft blocks. [Common work policy](../shaderpacks/common/shaders/include/shaderlod/work_policy.glsl)
uses centered widths; [spatial plan](../shaderpacks/common/shaders/include/shaderlod/spatial_plan.glsl)
supplies convex weights. Thus far transition is 92–100 blocks; full rate below
92 and quarter above 100 are conditional on all region, material and cache
guards. The near guard begins at 28 blocks, but the 28–36 near transition has
unity retention on both sides here: it is a protected/full-path control, not a
rate reduction. Minimum distance covers all four receivers in a 2 × 2 tile.
Named bands do not prove a pixel was actually eligible or reconstructed.

Retain a separate annotated `quality-review.json` per route. For each reviewed
region record source arm/UUID, slot/file/hash, native pixel rectangle or mask,
feature description, supporting world/surface positions and rendered camera,
receiver distance range, expected guard/tier and the evidence for actual eligible
use or fallback. Pair by actual observed pose and path time, retaining tick/partial
and Iris clock fields; the same slot number alone is insufficient. Record each
row as `observed`, `rejected`, `uncovered` or `unverified`, with reason.

| Region/control | Inspect in still and sampled motion | Reject or leave pending |
| --- | --- | --- |
| Protected near / original OFF / ON FULL | Original albedo, AO/contact edges, illumination and shadows, matched source graph | Visible unexplained change; unknown ordinary parity blocks adaptive attribution |
| Eligible far opaque surfaces >100 blocks | Recipient texture/albedo detail, fine alternating color, lighting gradients and cast shadows | Loss of recognizable texture detail, blurred/shifted shadow cues or repeated 2 × 2 patterns |
| Same opaque receivers through 92–100 blocks | Follow individual lighting/shadow boundaries during approach and return | Discernible popping, flicker, edge crawl, phase pattern or view-direction hysteresis |
| Foliage and thin geometry | Silhouettes/alpha edges and leaf/branch shadows **on opaque receivers** | Changed salient silhouette/contact/cast-shadow cues; foliage is not assumed eligible |
| Thin cast shadows/contact lighting | Narrow tree/fence shadows in continuous surface regions | Shared continuity excludes geometry discontinuities but does not certify cast shadows; thinning/disappearance fails |
| Water and shore boundary | Surface shading, glint band, reflected objects, underwater/shore seam if present | Spatial sampling of water or visible changed reflection/border; forest without visible water is uncovered |
| Reflective receivers | Object reflection position/detail and illumination, eligible neighboring opaque surfaces | Guard violation or visible reflection displacement/loss; absent reflective objects are uncovered |
| Sky / horizon | Separate unchanged-sky control, exposure/context and temporal variance | Unexplained full-path sky change remains a regression; temporal mismatch must not be called adaptive error |

[Photon eligibility](../shaderpacks/photon-shared-spatial/shaders/include/shaderlod/photon_spatial_adapter.glsl)
requires mask 0, nonmetal/nonreflective zero emission/SSS/sheen/porosity and
roughness 0.75–1.0, with the full normal/material/lightmap/plane and AO guards.
Recipient albedo/material, specular, fog and sky remain full resolution.
Water/reflection checks are exclusion/fallback controls under that source
contract. They are not a claim that reflections may safely use the cached basis.
Do not enable previously excluded environment/sky reflection or other source
options to make a forest scene pass coverage; that changes the eligibility test.

## Capture artifacts, noise and rejection

Retain raw `camera-quality/<UUID>/request.json`, `manifest.json`, all PNGs,
console, quality/runtime preparation ledgers, actual Java/mod/dev source
fingerprints and independent driver evidence. Require `complete`, 201 requested
and written/callback slots, zero skipped slots, final pending images/callbacks 0,
at most 2 pending images, one writer, physical queue at most 1; verify every
on-disk PNG size/hash and 2976 × 1672 RGBA8 header, request/control/source/options
hashes, unchanged loaded artifacts, first-person/loaded-camera context and actual
NONE/no-full-block observations. Compare the full actual 366/367 options maps,
not only their private boolean. Any input/focus/window/reload/fallback/drain or
camera-context failure invalidates that sequence as acceptance evidence.

Native PNGs are display-encoded 8-bit captures. Keep them unscaled for close
inspection and ordinary viewing; do not infer linear irradiance error from byte
differences. Retain exact still crops, paired full frames and a native-size sampled
motion flipbook. Produce pixel differences only as diagnostic aids, with original
image paths/hashes and masks attached, outside timing windows. Do not invent
PSNR/SSIM/per-pixel thresholds or use spatial/temporal averaging to erase a
visible defect. No numerical visual-quality gate is sourced for this goal.

Ordinary source token parity is already source-tested; ordinary image parity
remains a separate driver control. First compare repeated ORIGINAL with itself,
then original/OFF, then OFF/ON FULL, and finally FULL/ADAPTIVE. Record cloud/sky,
water/waving foliage and stable opaque receiver regions separately, without
discarding inconvenient differences. Sky is full resolution but Iris time,
frameCounter, jitter and histories advance normally; these captures record rather
than synchronize them. A different cloud or jitter sample is not causal evidence
of spatial error. Conversely, an unexplained stable-surface or sky difference in
FULL is not acceptable merely because clocks differ. Repeat the control or keep
the decision unverified. The original-versus-adaptive comparison remains required;
FULL controls cannot replace the original goal baseline.

Little-discernible-quality acceptance requires representative still and motion
review with no discernible new loss of salient lighting/shadows/material detail,
popping/flicker, water/reflection faults or full-path regressions. A reproducible
visible defect rejects the candidate regardless of its speed. Missing coverage
or unpaired temporal inputs remains pending, not a passing row. 10 FPS omits
most rendered frames; it cannot establish absence of higher-frequency shimmer.
Capture wall time/FPS cannot count toward the independent performance gate.

## Checks and next experiment

### Bounded driver screenshot inspection

Four root/operator driver images were opened individually with `view_image`.
Their native PNG headers are 2976 × 1672; the displayed tool previews were resized
to 2048 × 1151. This is a gross full-scene review, not native pixel parity.
Evidence is under `dev/logs/shared-spatial-driver-r1/`:

| File | SHA-256 |
| --- | --- |
| `original-final.png` | `e525de1291e2f4e0a0c3fc5c69798c89dc6aedf7ccd86cf355b76e3226a4193e` |
| `privateOFF-final.png` | `68396c2863074bf9919e512e38eac02063423cd2a513b928038270097d415d8e` |
| `privateON-FULL-final.png` | `0173204eb5d03cb17fb4a767804fc1e3e4d1bc3fe2f79747adea910818d9c1c0` |
| `privateON-ADAPTIVE-final.png` | `b8a3acf011a151c67ff42cc274d22deef8c0a6192ba23959adf94f8478903c6c` |

Observed across the four previews: recognizable grass/opaque terrain detail,
forest silhouettes, terrain/tree shadow cues, shoreline/water and clouded sky
remain visible in the same broad composition. No gross black/corrupt pass,
missing terrain/sky, large illumination shift or obvious new bright water seam
was apparent at this review scale. Sheep positions and dev HUD text vary;
the top-right tutorial is visible. Small differences cannot be attributed without
native region/pose/temporal pairing. This does not establish albedo/shadow fidelity,
ordinary pixel parity, spatial eligibility, actual reconstructed tier/certificate
use, reflection/glint coverage, motion quality or a performance result. HUD FPS
is not analyzed as speed evidence. Raw captures remain unchanged.

### Actual fresh still preparation

After the owner reported all four controls ended, the unchanged process guard
returned `[]`. Actual command, no client launch:

```sh
python3 scripts/prepare_shared_spatial_quality.py --route still --output run-workloads/shared-spatial-quality-still-r1 > build/shared-spatial-quality-prepare-r1.log 2>&1
```

Preparation exited 0 (`exec` session 77888). Four fresh independent STILL arms
are at `run-workloads/shared-spatial-quality-still-r1/forest/`; all retain the
canonical world hash above. The immutable 201-slot request SHA-256 is
`65d4fdd920d85c1588a4430e936ec944544e276a2592f0c12030338a3711e131`;
control SHA-256 is
`786ebfa8f6580d79380c04426ece620d62cbabb72c1ad5606f09ebea498f7592`.

Independent read-only copy audit passed and is retained in
`build/shared-spatial-quality-copy-audit-r1.json`: source report/config/archive/
world hashes unchanged; four equal canonical copied worlds; exact full archives,
source registrations and all recorded copied input hashes; native game options;
other copied configs unchanged; exact new core/effects policy bytes; physical
DH absence in copies; no old workload requests; exact camera commands and common
request/control hashes. Requested option counts are 354/355 and expected complete
maps remain 366/367, retaining the 12 computed/header defaults. These are prepared
maps; actual loaded quality maps are still unverified. Source DH/personal settings
and original saves were preserved. No Minecraft PID remained at audit completion,
and this worker owns no live process.

Quality capture launch is **HOLD** pending root's physical attachment/image/
completion raw-capture integration and acceptance. Preparation does not remove
that gate. PAN/approach collections are not yet prepared or launched.

Actually run: `python3 -m unittest discover -s tests -p test_shared_spatial_quality.py -v`:
four passed, zero failures/skips, including actual pinned archive/forest
qualification, all request bounds/eased speed limits, malformed pose/weather/
route/label rejection, and absence of workload/timing launch activation.
Raw rerun: `build/shared-spatial-quality-tests-r1.log` (four passed in 1.665 s).
Scoped new-file whitespace checks produced no diagnostics.
`--qualify-only --route approach-return` passed against the actual source and
created no profiles. The later actual STILL preparation above created profiles.
No Java/GLSL build, Minecraft launch, matched quality image review or GPU check
ran; only the four existing root/operator driver images were inspected.

Next: after raw layout integration and its acceptance, the sole operator may use
the prepared STILL four-arm collection and run the original capture first,
verify integrity and inspect coverage, then the matched OFF/FULL controls.
Only then proceed to adaptive and motion. Existing harness has no rendered
per-pixel surface-distance/material-eligibility/tier/certificate/actual cache-use
output and no synchronized temporal-clock replay. If exact receiver coverage
cannot be established from surveyed geometry, a separately owned untimed
diagnostic output is needed. Do not add one to ordinary quality/performance
shaders here. A dedicated water/reflection scene preserving this exact forest
map also needs survey/coverage before claiming those rows. Those unresolved
decisions return to root; this worker owns no live process.
