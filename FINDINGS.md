# Findings

Current answers within their stated scope. Read the linked result before applying a conclusion to new work. Historical results are not qualification of the current runtime. No optimization is accepted.

| ID | Question | Current conclusion | Result |
| --- | --- | --- | --- |
| F1 | Which earlier optimizations qualified? | None of the sixteen optimizations from the original repository met the keep bar; their implementations remain in the original repository | [Result](results/f1/result.md) |
| F2 | Can the closed-lid setup resolve small gains? | Unchanged-shader null runs varied from −9.70% to +10.24%, so the calibration failed; a ~3% effect cannot be detected with that setup | [Result](results/f2/result.md) |
| F3 | Did the earlier DH observation hooks work? | Bounded tasks, uploads, residency, hooks and restoration worked in original-pack runs | [Result](results/f3/result.md) |
| F4 | Did the earlier GPU capture explain frame cost? | GPU timing covered only 39 of 162 frame intervals, with unknown health, 43 unresolved queries and one imbalanced scope; it is not a cost breakdown | [Result](results/f4/result.md) |
| F5 | Did the original Complementary packs link with DH? | Reimagined and Unbound originals failed to link with DH enabled: "Too many fragment shader texture samplers" | [Result](results/f5/result.md) |
| F6 | Did disabling DH generation produce a steady scene? | Turning DH generation off was restored correctly, but leftover loading kept the scene from reaching a steady state | [Result](results/f6/result.md) |
| F7 | Was the DH depth handoff live-validated? | The DH depth handoff passed CPU tests only; live use, conversion and shadow paths were never exercised | [Result](results/f7/result.md) |
| F8 | What failed before the sampler-index correction? | Before the sampler index correction, the original Reimagined pack failed native linking with DH, and ran out of texture units without DH; settings restoration still succeeded | [Result](results/f8/result.md) |
| F9 | Does ShaderLOD cause the Asahi DH linking failure? | The same composite1 sampler failure happens with ShaderLOD absent, so ShaderLOD does not cause it | [Result](results/f9/result.md) |
| F10 | Which current functional behavior has live evidence? | Without DH, the shipping JAR passes native selection, real frames, settings-screen Cancel/Off, the identity provider and failed-apply restoration. With DH, the original pack still exceeds the fragment sampler limit | [Result](results/f10/result.md) |
