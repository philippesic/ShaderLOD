# Optional-stack forest results

2026-10-05. Nine serial original-Photon captures completed in exec session34175
(exit0). No meaningful consistent stack gain was established. ShaderLOD's main
mod was absent from every arm; these are optional-stack results, not shader LOD
performance. No optimizer default or recommended stack changes follow.

Minecraft1.21.1 / NeoForge21.1.253 / Iris1.8.12 / Sodium0.6.13 / Photonv1.3b /
DH3.3.3, Apple M3Max / OpenGL4.1 Metal91.7. Locks retain exact artifact hashes.
Forest camera256.5/95/768.5 yaw135 pitch9, clear time7000, detailed24 / DH128,
1488×836 framebuffer. Every run:45s warmup,25s capture, fixed shaderwetness0,
frame-only asynchronous elapsed query one frame in16; CPU layers off. Actual
effective options, world/camera/radii and sampled window/pacing state match.
All observed capture windows focused/visible, unminimized, uncapped260/Vsyncfalse.

## Median independent-run observations

Each row is three JVM launches, not three frame samples. GPU values are medians
of each capture's mean elapsed game-render interval, not inverse FPS.

| Stack | FPS range | Frame median ms | GPU median-of-means ms | Thread median ms |
| --- | ---: | ---: | ---: | ---: |
| minimal | 120.25–121.20 | 8.277 | 7.765 | 7.462 |
| optimized | 109.36–124.21 | 8.183 | 7.635 | 7.391 |
| optimized-culling | 104.55–122.31 | 8.407 | 7.746 | 7.413 |

## Paired block reductions

Positive is faster. Partial Williams cycle: order is interleaved, not fully
position-balanced. Frame reductions change sign; no confidence/significance
claim is supported by three correlated-host blocks.

| Baseline → candidate | Frame reductions by block | GPU mean reductions by block |
| --- | --- | --- |
| minimal → optimized | +3.18%, -10.48%, +0.83% | +6.03%, -9.20%, +1.68% |
| minimal → optimized-culling | -15.02%, -1.57%, +0.91% | -11.83%, -0.39%, +1.50% |
| optimized → optimized-culling | -18.80%, +8.07%, +0.08% | -19.00%, +8.06%, -0.18% |

## Artifact qualification and scope

Strict raw-archive comparison rejects the varying embedded fabric_api_base
identity. The qualified analysis explicitly accepts only the audited exact pair
on this pinned runtime:17 identical class files and identical resources except
a satisfied NeoForge minimum-version dependency bound. Raw identities and
provider variants remain in every report/comparison. This is a qualified
comparison, not byte-identical whole runtime or deterministic provider selection.
See CULLING_EMBEDDED_PROVENANCE.md and dev/runtime-artifact-equivalences.json.

All optional parent versions/hashes loaded correctly, including EntityCulling
1.11.2, TRansition1.0.25 and TRender1.0.17. Root inspected baseline/culling PNGs:
no gross terrain/cloud/shadow loss in the fixed forest; DH VBOs rendered. This
does not validate entity-rich occlusion, out-of-bounds block entities, moving
shadow casters or shader UI. Sheep/world simulation and animation clocks
advance; positions are not frozen. Culling is not aimed at this terrain-heavy
scene and no general culling benefit is inferred.

Development class directories were not byte-fingerprinted by these reports.
Loaded jars are hashed; dev source did not change during the matrix, but raw
reports do not independently prove class-directory byte identity. After the
matrix, the overlay was updated for pack-neutral labels; future captures must
not be pooled with these solely from the unchanged dev version string.

Raw retained evidence: dev/logs/takeover-stack-forest-r1, runner console next
to it. The failed strict-analysis log remains alongside successful qualified
stack-analysis-qualified.json. All screenshots/UUIDs/failures are retained.
Next: entity-rich compatibility if pursuing culling, plus matched shader
experiments under a held-constant stack. Ordinary Photon≥15%/second-workload
performance gate remains unmet.
