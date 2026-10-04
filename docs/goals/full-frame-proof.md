# Complete frame attribution and adaptive rendering proof

Created: 2026-10-04. Status: active.

## Objective

Determine the dominant CPU/GPU rendering cost, compare useful original high,
original lower, and adaptive quality workloads, and implement changes targeting
the measured bottleneck. Geometry/submission changes are authorized if evidence
justifies them. Preserve NeoForge/Iris/Photon scope and optional DH. Reopen the
final comparison with honest CPU/GPU/FPS/quality evidence.

## Acceptance and decision gates

- Instrument vanilla terrain, DH, entities/translucency where applicable, and
  render/frame CPU wall/thread costs in addition to current GPU pass intervals.
  Keep elapsed-query non-nesting and asynchronous retrieval correct on Apple.
- Identify measured workload fractions, timing overhead/limitations, and CPU
  versus GPU limits. Do not label unaccounted GPU timeline time as busy overhead.
- Run realistic expensive supported scenes/presets with matched high/lower/
  adaptive controls, generation/upload warmup excluded and source/config hashes.
- Seek roughly15–20% GPU-render savings in a justified workload as a proof target,
  without artificially inflating only the baseline or discarding important
  lighting for negligible cost. Explain ceilings/results when not achievable.
- Optimize shader execution, geometry, or CPU submission according to evidence,
  validate relevant checks/actual driver behavior and compare against a simple
  lower-quality preset. Document unsuccessful hypotheses rather than hide them.
- Leave final matched windowed comparison running with relevant timing metrics
  for user inspection; preserve configs, saves and generated logs outside Git.

## Constraints and ownership

Pinned Minecraft1.21.1/NeoForge21.1.253/Iris1.8.12/Sodium0.6.13/DH3.3.3/Photonv1.3b.
MacM3Max/GL4.1 has elapsed32-bit queries and no useful timestamp counter; full
desktop features need a suitable host. An optional host question is pending;
continue Mac work independently and do not guess external credentials/hosts.
No external push/publishing/new user-owned chat is authorized by agent files.

Coordinator owns launches, GPU access, benchmark integration and final evidence.
Bounded agents own extended timing, workload preparation and research/optimization
review in explicit file partitions. Do not stop/reload another owner's client.

## Initial checkpoint

Current aggressive preview loses tree shadows for roughly0.44ms savings and is
not an accepted default. Earlier four target passes were small relative to the
game-render interval. Existing code/global cached-sky mode remain preserved.
Next: map timing scopes and prepare high/lower/adaptive controls before cuts.
