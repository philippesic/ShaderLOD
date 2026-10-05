# MakeUp full-reflection source probes

Prepared 2026-10-05. Existing MakeUp adapter/helper/builder remain frozen and
default off. Root owns all client launches, reloads, driver checks and timings.
This worker wrote a standalone pinned probe builder and source fixtures only.

## Retained runtime observation

Root's `dev/logs/makeup-adapter-pilot-r2` completed original/off/lower/on driver
rendering. One minimal-stack, no-DH shoreline pilot returned:

| Arm | FPS | GPU game-render mean (ms) | CPU thread mean (ms) |
| --- | ---: | ---: | ---: |
| Original raymarch | 150.63 | 6.183 | 6.285 |
| Derivative disabled | 152.04 | 6.325 | 6.258 |
| Native projected reflection | 150.85 | 6.157 | 6.314 |
| Derivative fade/skip active | 119.85 | 7.749 | 7.828 |

These are single-run observations, not an accepted cause or repeatable effect
estimate. Retain all original reports, logs, captures and rejected earlier
attempts. The lower-quality native control is a separate quality setting. The
active arm rendered but regressed in this pilot; do not turn that into a
shader-compilation failure or assume the explicit LOD fetch caused it.

The recorded active endpoint was overworld shoreline 240.5/68/680.5,
yaw -180°, pitch 3°, time 7000, rain/thunder zero, 1488×836 framebuffer,
24 requested/effective chunks, native medium shadow options 2/1,
REFLECTION_SLIDER=2, AA_TYPE=2, no DOF/motion blur, bands 96/160 blocks.
Its report confirms shaders_in_use=true and fallback=false. Fifteen-second
capture and minimal/no-DH settings are recorded. Exact inputs, clocks, options,
artifact fingerprints and warmup remain in the pilot's manifest/reports.

## Exact input and source scope

All probes take the existing built derivative
`build/MakeUp-ShaderLOD-Experimental-9.5g.zip`, SHA256
`6c3f423173ddf12f2ab3f10f6e18f6bc07f304aa70201b5bebe5902587c59671`.
The builder refuses another archive, including a freshly rebuilt derivative
with different documentation/metadata bytes. It does not rebuild the adapter.

Member pins in `patches/makeup/probes.json`:

| Member | SHA256 |
| --- | --- |
| shaders/lib/water.glsl | 5c528242be4f9ea1fdf1b58e387b4345989993abfbbf0637117ef58c98a8f8f5 |
| shaders/lib/shaderlod/water_ssr.glsl | a5c79a6ed1092ac068b8adc37914ea8e188c09dd84e46ea4ceebf99ae6d76f5c |

The water patch replaces only the compile-time enabled `reflection_calc`
function; its disabled branch, original `fastRaymarch`, glass caller, lighting,
shadows, DH/VOXY water, originating vertex code and all options remain unchanged.
The implicit probe additionally restores original depth-fetch syntax in the
copied helper and corrects its explanatory comment. Original LGPL/GPL licenses,
editable source and upstream attribution remain intact. Output includes a new
probe notice, exact patch manifest and changed-member hashes. Retained
`shaderlod-build.json` is explicitly labeled frozen-input provenance; the new
`shaderlod-probe.json` describes final probe source.

All probes default off through the unchanged original option declaration.
Testing their active source requires `SHADERLOD_MAKEUP_WATER_SSR=1` and native
`REFLECTION_SLIDER=2`. No Java/common protocol bridge exists.

## Three source controls

| Probe | Exact active change from full original reflection | Useful comparison | Limit |
| --- | --- | --- | --- |
| duplicate-implicit | Water uses its copied trace helper with original implicit depth/color fetch syntax. Full reflection weight; no distance classifier or skip call. | Disabled/original versus this probe checks duplication/call/code-shape sensitivity under full SSR. | A compiler can inline/remove unused original functions. This does not measure helper duplication merely from file size. |
| duplicate-explicit | Same complete copied trace and reflection function as duplicate-implicit; only the depth and gaux1 fetches use explicit LOD 0. | Implicit versus explicit isolates the two fetch syntax conversions together. | It cannot separate depth from color fetch cost; driver/image equivalence must be checked. |
| classifier-full | Same explicit full trace, plus originating-distance classifier and a conditional whose **both arms call the identical complete trace**. Reflection alpha remains the original border. No far skip, fade, or infinite override. | Explicit versus classifier-full checks retained source/control-flow shape with full reflection intent. | Equal arms permit merging and dead-code elimination of the classifier; source presence does not prove execution. |

Near, transition and far reflection math/coverage/counts are unchanged in these
full-quality controls. They still execute the original ten-step exponential
search/refinement and hit/fallback logic. Explicit sampling near equivalence is
the documented pinned sampler inference from MAKEUP_ADAPTER_EXPERIMENT.md, not
a measured pixel claim. The implicit probe introduces **no new distance-based
divergent texture branch**: its texture lookup and internal search branches are
the original upstream pattern. Classifier-full uses explicit LOD 0 depth/color
fetches, so its diagnostic distance branch adds no implicit-derivative reliance.

The classifier uses the original camera/view-relative originating fragpos in
radial blocks and retains the 96/160 smoothstep calculation. Its value affects
which identical call site is used, not reflection weight or far quality. This
is intentionally a no-skip control and makes no work-saving claim. If driver
optimization merges the calls, a null result cannot prove the original active
classifier was free. Forcing classifier execution with a visible diagnostic,
new uniform, atomics or changed ray math would mix another variable into this
bounded full-quality comparison and is outside this handoff.

The original active fade/skip arm remains a separate quality-changing endpoint.
Comparing it to duplicate-explicit combines classification, branch/early-return
behavior, altered far pixels and shader compiler effects; those differences
cannot be attributed to classification alone.

## Assembly and static evidence

After the root Photon matrix is terminal:

```
python3 scripts/build_makeup_probe.py --probe duplicate-implicit
python3 scripts/build_makeup_probe.py --probe duplicate-explicit
python3 scripts/build_makeup_probe.py --probe classifier-full
```

Outputs are ignored renamed ZIPs under
`build/MakeUp-ShaderLOD-Probe-{probe}-9.5g.zip`. Each build verifies the entire
base archive, exact source members and unique literal anchors before changing
source. The existing safe archive reader rejects duplicate/unsafe members;
probe metadata collisions and input overwrite fail closed. Ordering, timestamps,
permissions and compression are fixed. The original archive is never modified.

Worker check: **6 tests passed** using
`python3 -m unittest discover -s tests -p test_makeup_probe.py -v`.
Fixtures check exact reflection math after removing the controlled helper/fetch
changes, both equal classifier arms, unchanged off/glass/options/origin paths,
member/archive/hash/anchor guards, source notices and deterministic small ZIPs.
Read-only checks applied all three patches in memory to the actual pinned
derivative and verified bounded changed members. No real probe ZIP was built,
no GLSL compiler ran and no client or reload was launched by this worker.

## Root acceptance and measurements still needed

First verify source fingerprints and actual effective options; record all
shader compile/link logs and a rendered shoreline capture per probe. Inspect
near and far reflection pixels, thin bank/tree silhouettes, sun glints,
offscreen borders, glass, refraction and a moving camera. Expected full-reflection
quality equivalence is a prerequisite for treating these as source controls.
Retain any mismatch and do not silently exclude it from evidence.

Where Iris's final compiled-source export is available, compare actual expanded
sources and their hashes, including helper calls/fetch syntax and whether the
classifier source remains. Expanded-source presence still does not prove driver
instruction execution; vendor program/capture tooling would be needed to prove
that. An equal-call compiler optimization is allowed, not a failed test.

Then run balanced repeated serial off/duplicate-implicit/duplicate-explicit/
classifier-full controls with the same original scene, options, framebuffer,
stack, no-DH status and settled inputs. Interleave original and the existing
active arm as appropriate to establish whether the pilot regression repeats.
Measure GPU game-render median/tails, CPU clocks and frame timing; water-pass
GPU scope is preferable if available. Preserve all repeats, warmup, full raw
reports and captures. Only after those controls can the sampler/code-shape
hypotheses be assessed. No probe driver rendering, quality result or GPU timing
is available yet; no causal or performance conclusion follows from these tests.
