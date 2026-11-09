# DH physical joins reviewed 2026-11-08

This is an implementation review of the pinned DH 3.3.3 / Iris 1.8.12 hooks,
not Philip's stage 6 four-pack review or a performance/compatibility acceptance.
The hooks are independent of the pack's semantic feature catalog.

| Concern | Physical hook / join | Limit |
| --- | --- | --- |
| Vanilla terrain vs DH | Sodium `drawChunkLayer` vs DH `LodRenderer.renderTerrain` | Wrapper intervals, not draw counts or visible pixels |
| Main vs deferred | `LodRenderer.render` and `renderDeferred`; enclosing CPU invocation supplies the stage to terrain/query records | Never sum inclusive total with its terrain children |
| World vs shadow | `ShadowRenderer.ACTIVE` at the pinned hook boundary | No DH shadow invocation was observed in the tested MakeUp scene; missing is unknown |
| Opacity | The exact five-argument `renderTerrain(..., boolean opaque, ...)` overload | That boolean means opaque, not deferred; main/deferred comes from the enclosing wrapper |
| Render-list CPU | `RenderBufferHandler.buildRenderList` | CPU wrapper only; no GPU or worker attribution |
| Generation/loading/conversion/propagation | Named thread CPU endpoint snapshots; required unknown coverage rows | No task tokens or task intervals; cannot assign process/thread deltas to a render frame |
| Uploads | Required unknown coverage row | No driver execution/upload-byte timing claim |
| Shader interactions | Exact archive/source/options/graph/DH catalog gate | Shared fog/lighting/AO remain DH consumers mixed with vanilla; source seeds alone never activate a mapping |
| Workload/residency | Effective DH settings and cumulative submission/index endpoints; heap and thread observations | Generation remained enabled; submission counts do not establish residency, visibility, readiness or generation cost |

`ScopeContext` supplies stage/world-shadow/opacity fields to canonical CPU and
GPU invocations. Unknown context stays unknown. `physical-joins.csv` and the
report's physical-joins view expose those joins without inventing feature tags.
The source catalog retains all four originals and its source/hash fixture passes.
Enabled compiled-graph semantic activation for all four packs remains unreviewed;
BSL/Complementary source seeds are not upgraded by MakeUp runtime evidence.

Live checks used original MakeUp at native resolution and AA, absent DH and
installed enabled DH. Main, deferred, render-list, opaque and translucent hooks
fired; both main/deferred values reached canonical invocations. Rendering was
explicitly toggled off/on in a declared transition test and restored with
getApiValue/setValue/clearValue ownership checks. Disabled and enabled cohorts
were separate. Enabled captures retain ENABLED_NOT_READY: horizon residency
was not established. Absent-DH concern rows remain NOT_APPLICABLE with null
values. See [implementation evidence](COST_MAP_IMPLEMENTATION.md).
