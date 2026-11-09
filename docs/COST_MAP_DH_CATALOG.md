# DH semantic joins — pinned source review, 2026-11-08

This is profiler implementation evidence, not stage 6 optimization review or
machine qualification. `cost-map-catalog.json` pins all archive bytes and source
members; `cost_map_features.py` contains the mixed feature labels. Runtime binding
requires that archive, Overworld, the owned selected/preprocessed graph and its
actual effective options. A filename/family guess cannot activate tags. Unknown
archives/dimensions remain unmapped. Labels describe potential features in a mixed
program, including conditional code; they do not time individual shader functions.

| Family / originals | Dedicated world raster joins | Shared DH consumers reviewed in original source |
| --- | --- | --- |
| Reimagined r5.9.3 and Unbound r5.9.3 | `dh/world/opaque` → `dh_terrain`; translucent → `dh_water` | deferred1 LOD depth/shadow/AO/atmosphere; composite1 volumetric light/water fog; composite3 depth/fog; composite4/5 depth-aware post; TAA helpers consume DH depth |
| BSL 10.1.8 | Same dedicated terrain/water joins | deferred AO on DH depth; deferred1 LOD lighting/shadows/AO/cloud/outline consumers; composite fog/light shafts/depth; composite7 TAA DH depth |
| MakeUp 9.5g | Same dedicated terrain/water joins | deferred DH depth for fog/cloud/lighting/AO; water helpers consume DH depth; composite/post labels stay mixed and are not automatically DH consumers |

The actual selected preprocessed fragment source must contain a DH depth/color
texture read before the report labels that program a DH consumer. A uniform
alone does not qualify. Included helper code can be optimized away by the driver;
this is source-consumer evidence, not a claim of active uniform use or incremental
DH cost. Native fog/lighting/AO consumers stay mixed with vanilla. Dedicated
`dh_terrain`/`dh_water` never relabel Sodium's opaque+cutout group as DH.

Main/deferred and world/shadow come from the physical hook context independently
of semantic tags. None of these four archives supplies world0 `dh_shadow`.
Absence of that program does not prove Iris cannot render DH through a fallback;
unobserved DH shadows remain unknown. Render-list CPU, worker tasks and upload
CPU are separate lanes, not features inside a deferred shader. Parent wrappers,
children and shared consumers must never be summed into a DH tax.

Transfers retain actual Iris copy/blit/clear/mip scopes. The pinned
`DHCompatInternal.copyTranslucents(width,height)` hook now identifies the DH terrain
stored-depth → pre-translucency depth texture handoff, including source/destination
IDs, extent, CPU event/frame ownership and allocation/fast-copy branch wrapper.
It is compiled and fixture-tested; the final live attempt hit the maintained
storage reserve before mutation. It is not live-qualified. General shared operations
stay mixed. GLBuffer CPU upload timing/bytes do not expose asynchronous internal
driver DMA execution or hardware traffic; those durations remain unknown.

Static four-pack fixtures check evidence activation/refusal and source inventory.
Asahi original MakeUp and BSL passed live semantic joins. Both Complementary
originals failed enabled-DH linking at composite1 with “Too many fragment shader
texture samplers”; their runtime cells remain blocked, with logs retained in the
coverage experiment. No shader edits/settings workaround was used.
