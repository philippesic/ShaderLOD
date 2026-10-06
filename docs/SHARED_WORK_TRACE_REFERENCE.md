# Photon shared work-trace diagnostic reference

2026-10-05. Owner: `work_trace_adapter`; parent owns the idle GPU. This is an
explicit consumer of the [general work-trace API](WORK_TRACE_API.md), separate
from the production Photon reference. The corrected r2 diagnostic archive freeze is
complete. The r1 runtime failed downstream framebuffer size validation before
capture. Corrected driver/pipeline acceptance, device counts, quality and
performance remain unverified. No client, shader reload, GPU run or main/dev Gradle build was run.
No live processes remain owned by this adapter task.

## Inputs and scope

Immutable inputs:

| Source | Archive | SHA-256 |
| --- | --- | --- |
| Original Photon v1.3b | `dev/upstream/photon_v1.3b.zip` | `120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead` |
| Shared production reference | `build/Photon-VolumetricLOD-disabled.zip` | `5de7561dd24cdca80860a1d5f2c60400e74163a489cc163e916f3fd47feb27de` |

[Builder](../scripts/build_photon_work_trace.py) verifies the archive SHA,
complete file-member inventory, license and reviewed replacement/helper hashes.
[Manifest](../patches/photon-experiments/shared-work-trace.json) defines the
exact replacements and diagnostic contract; SHA-256:
`23ae2d36851b71a4a8f60981a428a535f3687420da34b471c17b3a43c45a4a2f`.
The frozen helper is `bbc6f94e14d9a25df4d4dee56d55551aa337604e06b6b523ff5a189712c40fa3`.

Each derivative changes only settings, `program/d1_clouds.fsh`, cumulus,
coverage-map, buffer-format declarations and buffer-size properties. Both add
the general trace helper. Shared adds a diagnostic copy of the volume adapter;
its original helper members and production metadata remain byte-identical.
Stripping the copy's guarded counter/metadata additions yields the exact
production adapter. Neither production archive, original manifest nor shared
library body was modified.

Photon v1.3b is by Benjamin Stott (SixthSurge). Original attribution and LICENSE
are preserved; license hash:
`514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5`.

## Entry points, counters and units

Trace is private/default OFF. The selected code requires
`SHADERLOD_WORK_TRACE`, Overworld `PROGRAM_DEFERRED1`, and absence of
`ATMOSPHERE_TRANSMITTANCE_LUT`. Entry is
`shaders/world0/deferred1.fsh` → `program/d1_clouds.fsh` → `draw_clouds` →
`draw_cumulus_clouds`. Original/global-OFF fallback uses Photon cumulus; adapted
execution uses `shaderlod_volume_draw_cumulus` in the
[diagnostic adapter copy](../shaderpacks/photon-shared-work-trace/shaders/include/shaderlod/photon_volume_trace_adapter.glsl).

The common trace is initialized once per fragment. Increments occur at reached
operations, including early density returns; they do not derive work from the
requested cap, density strength, final radiance or history.

| Counter | Exact unit |
| --- | --- |
| Primary iterations | Reached primary loop body after the transmittance-break check, before adapted partial-cell rejection |
| Density evaluations | Cumulus density function entries, both primary and secondary, including shell/coverage/altitude early zero returns |
| Secondary visits | Reached optical-depth loop bodies, lighting and sky; excludes the eight-iteration scattering approximation |
| Texture evaluations | Reached texture-value calls in cumulus density/local coverage: local coverage map, both Worley fields, or two procedural coverage reads |

Texture counts exclude `textureSize`, depth/dither samples, other cloud layers,
crepuscular rays, atmosphere and post-lighting work. They are source-level
texture evaluations, not hardware fetches or instruction counts. The secondary
unit is deliberately narrower than all secondary arithmetic.

Page 1 records the original primary budget and the actual selected draw body's
continuous target. `requested_work` means the target supplied to that draw,
including existing footprint/protection restoration; it does not expose the
unprotected policy ratio or preflight request. Original uses N/N/N; adapted
uses N/target/target. Coverage-zero exits occur before upstream computes N and
retain initialized zero budgets, while the known selected path is recorded.
Path 0 means no cumulus draw; 1 means original/fallback; 2 means adapted; 3 is
reserved and unused. Reason 0 is the only emitted reason: individual wrapper
fallback causes are not classified. This boundary is explicit in the metadata.

## Preservation and diagnostic phase

Counters and metadata are the only additions inside the original/copied march.
Texture expressions, grid positions, secondary sampling, optical normalization,
density, normalization and early-exit operations remain literal. The existing
adapted path's explicit LOD-0 sampling remains as frozen; original still has its
implicit samples. This diagnostic does not introduce a new derivative-dependent
sampling pattern or move existing work behind a late branch.

The shared policy still uses camera-relative block distances (atmosphere units
are divided by `CLOUDS_SCALE`), centered smooth transition bands, minimum eight
samples, partial-cell integration and the existing overhead restoration over
absolute ray Y 0.35–0.65. INHERIT/unity, global OFF, invalid protocol/capability,
near/protected/inside/above-volume paths retain original work. Trace OFF restores
the ordinary original or shared shader token stream; nonselected cloud stages
and the LUT control retain it even with trace ON.

One explicit diagnostic-only input change freezes checkerboard offset index and
`r1` frame phase to zero, identically for traced original, shared global OFF and
shared adaptive. Trace OFF preserves ordinary frameCounter use. This does not
freeze wind/world age, camera, projection, TAA offset, terrain depth, weather,
coverage maps or sampler state: the runtime operator must match those inputs and
record them. The archives are unsuitable for ordinary appearance or timing.

## Same-draw exact storage and registration

The cloud draw uses three simultaneous MRT pages:

| Output location / attachment | Photon target | Raw RGBA32F values |
| --- | --- | --- |
| 0 | `colortex9` | primary, density, secondary, texture counters |
| 1 | `colortex10` | original, requested, selected work; path |
| 2 | `colortex11` | schema 1, reason 0, valid 1, reserved 0 |

Invalid/overflow/schema-mismatch output is all −1. Counters 0–16,777,215 are
exact binary32 integers, never normalized. Format overrides use Photon-style
commented const directives, private guarded and last in `buffers.glsl`.
`colortex11`, `colortex12` and `colortex15` mirror the existing 9/10 size in all
four temporal-upscale branches. This keeps downstream cloud resolve MRT 11/12
and its optional LOD MRT 11/12/15 coherent; no upstream pass elsewhere writes
12 or 15 together with a full-size target. These diagnostic history/combined-depth
sizes do not change primary cloud projection, density or sampling positions.
The actual pinned Iris directive parser confirms all three final format values
are RGBA32F. Actual GL allocation, viewport/dimensions, draw-buffer mapping and
absence of fallback still require device verification.

The attachment output replaces clouds, apparent-distance data and cloud history.
Readback must occur immediately after this draw, before temporal resolve/history,
filtering, tonemapping or screenshots. Later images cannot decode work and cannot
serve as quality evidence. Format/dimension validation must reject a mismatch.

Every archive gets an adjacent `.registration.json`: a separate diagnostic
adapter identity, core/effects/capability protocol requirements, count operations,
archive/member pins and required effective options. The runtime must register it
before Iris pipeline sealing through the generic extension API. Only the new
`shaderlodWorkTraceSchema` export is registered; the existing volume availability
export is reused. Its uniform is consumed by output validation and must read 1.
The marker alone does not prove source binding, format or counts.

Required options are `SHADERLOD_WORK_TRACE=true`, `CLOUDS_CUMULUS=true`,
`BLOCKY_CLOUDS=false`; shared additionally requires
`SHADERLOD_VOLUMETRIC_INTEGRATION_LOD=true`. Shared private defaults remain OFF,
including in the trace-default-ON archive. Runtime option sidecars must activate
that gate before comparing global OFF against ADAPTIVE. Registration metadata is
one source per diagnostic JVM; duplicate trace export registration is not allowed.

## Frozen artifacts and checks

All binaries and generated metadata remain in ignored `build/`.
[Complete ledger](../build/photon-work-trace-freeze.json) contains exact archive,
registration, member and option pins.

| Archive | SHA-256 |
| --- | --- |
| `Photon-WorkTrace-original-off.zip` | `ac159c201aad81c87b2b41d28de59a6ecbeec49fbd5a5e819d20ed76cd2f78ac` |
| `Photon-WorkTrace-original-on.zip` | `89e0a379b9b6a4eeda07fa564c209fdd8487420e114a1e3aa4ffa4620ddd1b70` |
| `Photon-WorkTrace-shared-off.zip` | `b0f976fb485abbf84e10de4fc3406950659b73d0a2c0f0296180ea05fc95fdaa` |
| `Photon-WorkTrace-shared-on.zip` | `e8519e91eb463379ec8ecc17658fb22f4130ae9ca72971b900f2ebb343970088` |

Reproduce with `python3 scripts/build_photon_work_trace.py --source original`
and `--source shared`; add `--trace` only for explicit trace-default-ON variants.

[Six fixtures](../tests/test_photon_work_trace.py) passed, zero skips:
`python3 -m unittest discover -s tests -p test_photon_work_trace.py -v`.
[Repair suite log](../build/photon-work-trace-repair-tests.log) records 270 complete pinned
Iris Jcpp/EnhancedParser grammar parses, 90 ordinary-OFF token comparisons,
nonselected-stage/LUT ON isolation, two actual Overworld trace-output graphs,
Iris option discovery, final format-directive extraction, reached-site checks,
exact copied-body restoration, frozen-input scope checks and deterministic
archive/registration assembly. The additional actual Iris layout regression
checks 2,656 pass/profile cases and 528 multi-target groups across both source
families, upscale 1–4 and no LOD/DH branches. It uses pinned
`PropertiesPreprocessor`, `TextureScaleOverride` and `CommentDirectiveParser`,
reproduces the old 427×240 versus 1708×960 MRT 11/12 mismatch, verifies every
repaired MRT group, and requires ordinary-OFF dimensions to equal upstream.
[Portable layout rerun](../build/photon-work-trace-repair-layout-tests.log)
reconstructs the prior property recipe from the repaired source plus an exact
old fingerprint. Scoped whitespace check passed.

Capture owner separately accepted registration metadata with no-GL fixtures;
see its [log](../build/work-trace-readback-tests.log). That is metadata/readback
implementation evidence, not native device readback or driver compilation.

Root/runtime operator must next establish corrected pipeline creation, actual
linked options/capabilities,
three matching RGBA32F attachments and same-draw readback, then matched original,
shared global OFF and shared ADAPTIVE count snapshots. Work evidence may show
fewer or more operations because density occupancy and early exits change.
Production speed requires separate matched, quiet, repeated original/OFF/ON
GPU and total-frame measurements with counters/readbacks absent, plus independent
still/motion quality checks. No diagnostic timing row is performance evidence.
The 1.5× throughput goal is not established by these artifacts.


## Retained r1 failure and repair boundary

The original diagnostic launch failed before work capture: Iris rejected later
`deferred2` drawbuffers 11/12 because 11 was 427×240 and 12 remained 1708×960.
See the preserved [console](../dev/logs/work-trace-runtime-r1/original-console.log)
lines 173–176 and 265–268, [raw header](../dev/logs/work-trace-runtime-r1/original-raw-work-trace.json),
and [terminal audit](../dev/logs/work-trace-runtime-r1/work-trace-terminal-audit.json).
The adapter's initial source/grammar checks omitted downstream framebuffer
layout validation; that omission is corrected by the new actual Iris layout
fixture. No count or speed result is accepted from that launch.

The exact held option sidecar is
`run-workloads/work-trace-runtime-r1/coastalwater/original/shaderpacks/Photon-WorkTrace-original-on.zip.txt`:
BORDER_FOG, CLOUD_SHADOWS, SHADOW_COLOR, SHADOW_VPS and SH_SKYLIGHT false;
otherwise archive defaults, including CLOUDS_TEMPORAL_UPSCALING 4. The held
control file is sibling `coastalwater/control.json`. Its camera/time/preset are
retained provenance; this repair changes no preset, ray or density input.

All four r1 archives/registrations, original manifest and ledger were preserved
under [build/photon-work-trace-r1-failed](../build/photon-work-trace-r1-failed/photon-work-trace-freeze.json)
before the r2 outputs were written. Failed runtime files and run-workload copies
remain unchanged. The new pins supersede r1 only for future diagnostic launches;
the r1 failure is retained. Capture marker, page codec and attachment 0/1/2
mapping remain unchanged. No Java source/class, client or GPU operation was
part of this bounded repair; root must accept the freeze before a runtime retry.
