# Findings

Current answers within their stated scope. Read the linked result before applying a conclusion to new work. Historical results are not qualification of the current runtime. No optimization is accepted.

| ID | Conclusion | Result |
| --- | --- | --- |
| F1 | None of the sixteen optimizations from the original repository met the keep bar; their implementations remain in the original repository | [Result](results/historical-optimization-retention/result.md) |
| F2 | Unchanged-shader null runs varied from −9.70% to +10.24%, so the calibration failed; a ~3% effect cannot be detected with that setup | [Result](results/asahi-null-calibration/result.md) |
| F3 | Bounded tasks, uploads, residency, hooks and restoration worked in original-pack runs | [Result](results/dh-observation-hooks/result.md) |
| F4 | GPU timing covered only 39 of 162 frame intervals, with unknown health, 43 unresolved queries and one imbalanced scope; it is not a cost breakdown | [Result](results/incomplete-gpu-cost-capture/result.md) |
| F5 | Reimagined and Unbound originals failed to link with DH enabled: "Too many fragment shader texture samplers" | [Result](results/complementary-dh-link-failures/result.md) |
| F6 | Turning DH generation off was restored correctly, but leftover loading kept the scene from reaching a steady state | [Result](results/dh-loading-after-generation-disabled/result.md) |
| F7 | The DH depth handoff passed CPU tests only; live use, conversion and shadow paths were never exercised | [Result](results/dh-depth-handoff-validation/result.md) |
| F8 | Before the sampler index correction, the original Reimagined pack failed native linking with DH, and ran out of texture units without DH; settings restoration still succeeded | [Result](results/asahi-sampler-index-failure/result.md) |
| F9 | The same composite1 sampler failure happens with ShaderLOD absent, so ShaderLOD does not cause it | [Result](results/asahi-dh-native-control/result.md) |
| F10 | Without DH, the shipping JAR passes native selection, real frames, settings-screen Cancel/Off, the identity provider and failed-apply restoration. With DH, the original pack still exceeds the fragment sampler limit | [Result](results/asahi-native-selection-restoration/result.md) |
