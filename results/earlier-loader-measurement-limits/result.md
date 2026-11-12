# Older loader experiments separated real rendering progress from unsupported speed claims

The Fabric 1.21.11 MakeUp comparison repaired its rate observations but retained a 117% return-control drift. The NeoForge 26.2 binding probe reached actual native rendering bodies but did not qualify scene completeness or speed. Neither experiment establishes support or performance on the current NeoForge 1.21.1 implementation.

| Historical experiment | Actual observation | Meaning |
| --- | --- | --- |
| Fabric 1.21.11 original → candidate → original | 5,107 / 4,047 / 2,350 completed renders in approximately 30-second windows; 5.885 / 7.414 / 12.790 ms per render | No stable causal candidate gain; effective cap/focus controls do not eliminate other drift |
| NeoForge 26.2 original-world binding probe | 32,641 descriptor queries, 1,679 GL entries and 3,273 native encoder-body returns | Actual native calls occurred; nested calls are not frame counts, GPU completion or avoided work |
| NeoForge 26.2 captured scene | 2.041 seconds settled; distant terrain visibly incomplete | Startup observation cannot establish full geometry or motion quality; cause remained unverified |

## Conditions and limitations

Evidence level: source-inspected historical import. Both used native MakeUp 9.5g/native AA2, M3 Max/macOS, OpenGL 4.1 Metal 91.7 and 1920×1200. Their Minecraft, loader, Iris, Sodium and JDK versions differ and remain separate in the retained source reports. These are historical version labels, not new supported targets. No new game run, screenshot review or compatibility test was performed.

The Fabric comparison measured elapsed time per completed world render, not GPU completion. Stage counters included warmup/reload and cannot be substituted for timed-window command savings. The NeoForge result explicitly recorded no GPU completion or validation-policy mutation; scope repair preserved native behavior, rather than weakening validation to obtain a pass.

## Decision

Retain the compatibility and measurement lessons. Any future loader/pack extension must independently establish actual consuming source, activation, complete scenes, restoration and stable comparisons. Do not import old launch instructions or claim success from counters alone.

## Evidence and executed source

[Compact observations](result.json) sha256:76ac291c428ab137acac41c3a476e1108f23780a97856a9e36521f7273d99c3f

[12111-fabric-makeup-speed-r2-review.md](../sources/history/earlier-compatibility-01.json) sha256:d8aff7f2e0519b06f59fc99dda73410ace2988215e052053a6897ac90b600bc5

[262-neoforge-binding-runtime-20261011-r5.md](../sources/history/earlier-compatibility-01.json) sha256:edf979ff17aed5cb8d8901610b15e2ebea549c24b3d2270bb59dc2ce13116182

Exact original artifacts, stages, inputs and missing coverage remain in those records. [Historical catalog](../historical-evidence-extraction/catalog.md).
