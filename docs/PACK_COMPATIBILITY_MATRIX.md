# Pack integration evidence

Updated 2026-10-05. Runtime evidence below is limited to the pinned Minecraft
1.21.1 / NeoForge21.1.253 / Iris1.8.12 / Sodium0.6.13 / Java21 stack on
Apple M3 Max, OpenGL4.1 Metal91.7. Exact artifact hashes are in
`dev/runtime.lock.json` and `dev/shader-candidates.lock.json`; individual reports
retain actual loaded artifacts. A diagnostic consumer is not a supported adapter.

| Pack | Actual rendering | Distance/config connection | Performance and quality status |
| --- | --- | --- | --- |
| Photonv1.3b | Reduced Mac profile rendered with and without DH3.3.3; native SSR coastal path uses DH absent | Existing pinned B15 adapter consumed live global controls in fixed-option same-JVM phases. Main/shared protocol remains owned by the original chat | Forest and coastal positive global-control signals below15%; serial regressions retained. Native shoreline, glint and underwater triplets each completed603 verified PNGs. SSR-only far skipping reproduced a bright water band; private full/reduced trace controls removed it at the inspected pose, but serial cost regressed. Full motion/transition/travel quality and repeated original-baseline acceptance remain |
| MakeUp9.5g | Original and experimental water paths rendered without DH. Original with DH linked its programs and submitted real opaque/translucent world draws; distant land/water inspected | Legacy option-only water fade and isolated core2 consumer. Actual water cues proved global enable plus radial near/far. Preset, transition, debug and per-effect fields unconsumed | Initial active option-only pilot regressed. Normal core-consumer pilot established no useful gain. Corrected2FPS shoreline captures are bounded evidence; high-rate/glint/underwater and adapted DH boundary remain |
| Miniature2.19 | Original and native global-two-refinement control rendered without DH | Actual diagnostic water cues proved core2/global enable/radial near/far. Separate default-off4/3/2 refinement probe assembled, requiring exact sampler/profile evidence; full-four explicit and distance modes now rendered; normal GPU readback proved core2/global enable/near96/far160 | Native two-refinement single pilot showed a small quality-dependent difference. Diagnostic preserves original SSR work. Full-four explicit pilot regressed; distance-mode pilot had flat GPU time. Transition quality and avoided-work evidence remain |
| Complementary Reimagined r5.9.3 | Original HIGH compiled and rendered noDH, with no Iris fallback at native resolution | Native HIGH defaults observed; sky/Potato reflection cost controls pending; no ShaderLOD consumer | Single original smoke64.24FPS/14.07ms GPU, no gain claim. New-biome/End uniform warnings retained; motion/DH/dimensions unverified |
| BSL10.1.8 | Untouched HIGH/defaults rendered without DH, including ordinary shadows/reflections; default-off voxel feature stayed off | Native options only; no ShaderLOD consumer or derivative produced | Native water-parallax-off single pilot lowered GPU interval2.81% at different quality. Motion/DH/other configurations unverified; derivative distribution permission not established |

Sources: [Photon native controls](PHOTON_NATIVE_RESOLUTION.md),
[MakeUp rendering](MAKEUP_RUNTIME_RESULTS.md),
[MakeUp core connection](MAKEUP_CORE_BRIDGE_PROBE.md),
[Miniature original plan and runtime](MINIATURE_RUNTIME_PLAN.md),
[Miniature core connection](MINIATURE_CORE_BRIDGE_PROBE.md),
[Miniature refinement experiment](MINIATURE_REFINEMENT_PROBE.md),
[BSL runtime](BSL_RUNTIME_PLAN.md),
[Complementary original plan](COMPLEMENTARY_RUNTIME_PLAN.md),
[SSR-only cost control](NATIVE_INPROCESS_SSR_COST.md), [DH submissions](DH_RENDER_EVIDENCE.md),
[quality capture](CAMERA_QUALITY_HARNESS.md).

Original source inspection/download pins for Bliss and Super Duper Vanilla
establish no driver rendering here. See
[other-pack research](OTHER_SHADER_RESEARCH.md). Optional performance-mod stacks
were actually loaded and measured, but did not establish consistent benefit in
the forest; see [stack results](STACK_FOREST_RESULTS.md).

Pack options remain distinct from mod configuration. A field is connected only
where actual pixels or runtime observations prove consumption; shader options,
registered uniform names and successful compilation alone do not prove it.
Experimental switches remain off by default. Broader configurable pack support,
full-quality desktop hardware, reload/restoration cases, quality acceptance and
ordinary-scene performance gates remain unfinished.
