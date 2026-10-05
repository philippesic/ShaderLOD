# MakeUp isolated core protocol 2 probe

Source experiment prepared 2026-10-05. Both new shader options default off.
The existing MakeUp adapter, all shared protocol helpers, Java code and
CommonUniformsMixin remain unchanged. Root owns clients, shader reloads,
configuration tests, quality captures and GPU measurements. No runtime transport,
quality acceptance, support or speedup is established by this source handoff.

This archive is a derivative of **MakeUp Ultra Fast 9.5g**, Javier Garduño,
under its retained LGPL v3 notices, with the incorporated GPL v3 text supplied.
[Official source](https://github.com/javiergcim/MakeUpUltraFast),
[official releases](https://modrinth.com/shader/makeup-ultra-fast-shaders).
Original attribution, README, LICENSE, LICENSE.GPL-3.0, editable shader source and
source patches are retained. The new overlay uses the same LGPL v3 terms.

## Reproducible scope

Exact frozen input derivative SHA256:
`6c3f423173ddf12f2ab3f10f6e18f6bc07f304aa70201b5bebe5902587c59671`,
`build/MakeUp-ShaderLOD-Experimental-9.5g.zip`. Its upstream release SHA256 is
`0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8`,
source revision `ad2d9327ad485429e4eaf2b3f98cd738ada609de`. A rebuild with another
archive hash is rejected, even if only documentation changed.

`patches/makeup/bridge2-probe.json` anchors exact original member hashes and
literal replacements. Modified shader members: `shaders/lib/water.glsl`,
`shaders/lib/config.glsl`, `shaders/shaders.properties`, `shaders/lang/en_US.lang`.
The new overlay is `shaders/lib/shaderlod/core_bridge_probe.glsl`. Existing water
ray helper, original fastRaymarch, glass, DH/VOXY water, vertex position, lighting
and shadows remain unchanged. No shared helper or protocol file is modified.

Entry point: vanilla `water_shader`/`reflection_calc`, reached through
`common/water_blocks_fragment.glsl` by gbuffers_water and gbuffers_hand_water
in root/world0/world1/world-1. The diagnostic returns color only inside
water_shader; glass and DH use other functions. Other water effects outside
this function still run. No universal source rewriting or pack support claim.

## Core contract and default baseline

The existing `src/main/java/com/pp/shaderlod/mixin/CommonUniformsMixin.java`
registers seven core fields unconditionally at the tail of Iris
generalCommonUniforms, with PER_FRAME frequency and version sentinel 2. Read-only
source inspection and its hash are recorded in the manifest. This probe declares
only the four fields it uses, and only when opted in:

| Existing uniform | Type | Probe meaning |
| --- | --- | --- |
| shaderlodBridgeVersion | int | Accept exactly 2 |
| shaderlodEnabled | int | Zero selects full original water SSR; nonzero selects distance fade/skip |
| shaderlodNear | float | Radial fade start in Minecraft blocks |
| shaderlodFar | float | Radial fade end in Minecraft blocks |

`shaderlodPreset`, `shaderlodTransition`, `shaderlodDebug` are not declared or
consumed. There is no per-effect live setting or preset behavior in this probe.
The original Java comment describes Photon; this independent consumer is an
experiment, not a change to the transport API or product scope.

Options in the existing advanced water ShaderLOD screen:
`SHADERLOD_MAKEUP_CORE_BRIDGE=0` and `SHADERLOD_MAKEUP_BRIDGE_CUE=0` by default.
With both zero, no bridge uniform declarations or new helper are included;
the complete pre-existing MakeUp reflection selection and water output body
remain selected. The original pack water SSR switch also remains default zero.

| Core option | Version | Global enable | Rendering with cue off |
| --- | --- | --- | --- |
| 0 | Any | Any | Original pack controls; no uniforms consumed |
| 1 | 2 | 0 | Complete original reflection function, independent of legacy pack LOD switch |
| 1 | 2 | Nonzero | Water fade/skip controlled by transported near/far, independent of legacy pack LOD switch |
| 1 | Absent/0/1/unknown, including 3 | Any | Original pack water LOD switch and pack fade controls |

Opting in makes global enable authoritative for this adapter's water effect;
it does not add a mod effect field. Absent-mod uniforms initialize to zero,
which is not accepted. Old protocol 1 and future protocol 3 are intentionally
unrecognized here and fall back to pack controls. Even if a future version
retains the core names, its compatibility is unverified and must be explicitly
reviewed before accepting it. A registration log alone is not a shader consumer
or driver connection check.

Native REFLECTION_SLIDER=2 is required for water raymarch/fade behavior. Native
reflection-off/projected modes remain their original behavior. Diagnostic color
may still show transport in those modes; it cannot prove SSR work changed.

## Distance and disabled behavior

The same originating camera/view-relative fragpos is used as in the existing
adapter, with radial distance `sqrt(dot(fragpos, fragpos))` in block units. For
recognized protocol 2, near maps to full-quality-through distance, far maps to
the end of one smoothstep fade. Start clamps to zero; end clamps to at least
start + 1 block. The pack 96/160 defaults remain selected whenever bridge opt-in
or version acceptance is absent. Generic mod defaults 32/96 therefore produce
different water bands only after valid opt-in; root should set 96/160 explicitly
when comparing the old adapter's quality.

Between start/end, original ten-step tracing runs and reflection alpha fades.
After end, the existing explicit-LOD active function skips before depth/color
fetches, with native sky/cloud fallback and infinite=1 sun-glint behavior. This
still removes distant reflected scene content and retains the existing quality
risks; no optimized-quality recommendation follows.

With recognized global disable, reflection_calc makes a dynamically uniform
decision based only on uniform core fields (or fallback compile-time controls),
and returns a renamed function containing the full original reflection body.
It calls the unchanged original fastRaymarch and retains original texture2D
fetches. That branch occurs before the active function's classifier or trace;
disabled does not merely give its active classifier full weight. Uniform branch
selection introduces no distance-divergent implicit sampling. The active path
retains explicit LOD 0 depth/color sampling in its divergent skip. GLSL uses
120-compatible int/bool/float operations and the already enabled texture LOD
extension. Actual driver compilation and disabled-image equivalence remain
runtime checks.

## Independent diagnostic cue

`SHADERLOD_MAKEUP_BRIDGE_CUE=1` is a separate compile-time diagnostic inside
vanilla water_shader. It returns a color before reflection work in that function;
normals, lighting, clouds and other caller work may already have run. Keep it
off for quality comparisons and all GPU evidence. It has no relationship to
the transported debug flag and is not a production visualization.

| Visible water cue | State |
| --- | --- |
| Gray | Core bridge option off; no bridge uniforms declared |
| Magenta | Core option on but version absent/unrecognized |
| Yellow | Accepted protocol 2, global enable zero |
| Green | Accepted/enabled, originating water within transported near |
| Green-to-blue gradient | Accepted/enabled, between transported near/far |
| Blue | Accepted/enabled, originating water at/beyond transported far |

Fog, exposure and temporal postprocessing can tint the cue. It is still water
only and the global statuses use distinct channels. Root should verify the
same camera/distance under at least two near/far settings and enabled on/off,
then disable the cue and inspect actual reflected content. A cue changing color
proves consumed state reached shader pixels; it does not prove avoided work,
correct visual quality, support or performance. Far skips require rendered
behavior/source verification and paired timings separately.

## Builder and stopping boundary

After the root-owned GPU experiment is terminal:

```
python3 scripts/build_makeup_bridge_probe.py
python3 -m unittest discover -s tests -p test_makeup_bridge_probe.py -v
```

Default output: ignored `build/MakeUp-ShaderLOD-CoreBridge2-Probe-9.5g.zip`.
Builder verifies exact base/member/overlay hashes, unique anchors, archive safety,
retained source/licenses, no overlay collisions and no base overwrite; output
ordering, timestamps and permissions are deterministic. New
`shaderlod-bridge-probe.json` records final source/overlay hashes and unverified
runtime state. Retained shaderlod-build.json describes the frozen input only.
The complete bridge source patch and this notice accompany editable source.

This worker stops at source/fixture evidence: no real archive build, compiler,
shader reload, Java changes, client or GPU capture. All default paths stay off.

Source checks: **8 tests passed**. Small fixture ZIPs reproduced deterministically;
tests cover the exact original disabled function body, uniform-only dispatch,
version/option guards, unchanged pack defaults, cue separation, radial band
mapping and archive/member/overlay guards. Read-only checks applied the patch in
memory to the actual pinned derivative, confirming unchanged original trace,
glass, DH and existing helper source, and inspected the actual Java registrations.
No shader parser/compiler or runtime connection is implied by these checks.

Root runtime sequence: original derivative/cue off, new probe defaults off,
mod absent with opt-in (magenta and pack fallback), mod present with opt-in and
global disable (yellow; cue-off original reflection), then enabled with near/far
96/160 and a distinctly different pair such as 32/64. Capture cue status/bands,
actual config/option maps and source/artifact hashes, repeat config reload
without pack source changes, and inspect actual reflected-content transitions
with cue off. Preserve failed connection or compile attempts. Unknown version
fallback has static evidence only until a version-controlled runtime probe is
authorized; do not mutate the main transport merely to manufacture it here.

Use locked Minecraft 1.21.1/NeoForge21.1.253/Iris1.8.12/Sodium0.6.13, no DH
first, native raymarch, settled matched shoreline inputs. DH compatibility is
separate and untouched DH water may expose a boundary mismatch. Only after
rendering, changed-distance transport and quality checks should repeated balanced
serial GPU original/bridge-disabled/bridge-active captures begin, cue off and
matching bands. Retain GPU medians/tails plus CPU/frame clocks; pass-level water
GPU measurement is preferred when available. No new runtime or timing evidence
exists for this bridge probe at source handoff.


## Actual core connection evidence: 2026-10-05

Rootassembled standalone archive SHA256 73c494eff4ed08a80bd0b6e529789ede66a9498c1d3cfe3cdd49de0645bfc430.
exec35659 terminalexit0/allfouraccepted, minimal/noDH/locked1.21.1 runtime,
25swarm10scapture, sameinspectedshoreline1488×836. GPUqueriesdisabled and
waterdiagnostic1; displayedFPS is never adapterperformance evidence.
Rawdev/logs/makeup-core-bridge-cue-r1 retainsarchive/options/config/class/jar
fingerprints, loadedversions, fourPNGhashes and nofallback Iris in-world logs.

RootinspectedallfourPNGs: absentmod magenta, acceptedmoddisabled yellow,
acceptedmodenabled96/160 near green/far blue, changedmod8/16 predominantlyblue
withnearforegroundgradient. Samearchive andcore/cue options1 for all; only
mainmodpresence or existingloadedglobalenabled/near/far changed. This proves
the exactexistingprotocol2 corefields reachwater shaderpixels on this host.
It doesnot proveavoidedSSRwork or speedup, and the diagnosticbypassesnormal
waterreflection. Otherprotocolversions have sourcefallback fixtures only.

No mainconfig/transport/sharedhelper changes. Existingcore globalenable and
radialnear/far consumed; per-effect/preset/transition/debug unconsumed. Pack
support stillunpromoted. Next cue-off original/disabled/active images, motion
and repeatedGPU captures, modabsentbehavior, DH compatibility and reloads.
