# Bliss sky elision — independent early cost review

Reviewed October 10, 2026, approximately 13:52 PDT by `/root/makeup_average_review`.
Root owns the lane; reviewer changed only this report and ignored `bliss/review/`.

**No useful original-relative total-render gain established; reject retention of
this fixed candidate.** Candidate throughput is below both original arms. The
original return drifted **4.71% in elapsed ms/frame**, limiting any exact causal
regression claim. A lower sampled composite1 time does not satisfy the total-frame
outcome or prove dead-code elimination/execution causality.

| Arm | Frames / elapsed seconds | Elapsed ms/frame | Throughput FPS | Game-render GPU mean / median / p95 ms (n) | Composite1 GPU mean / median / p95 ms (n) |
| --- | --- | --- | --- | --- | --- |
| Original | 3294 / 30.039994750 | 9.119610 | 109.6538 | 7.847645 / 7.854271 / 8.383583 (94) | 0.411804 / 0.407980 / 0.476375 (94) |
| Control | 3326 / 30.045262834 | 9.033452 | 110.6996 | 7.782642 / 7.783708 / 8.216042 (95) | 0.400440 / 0.398292 / 0.481874 (95) |
| Candidate | 3135 / 30.041367708 | 9.582573 | 104.3561 | 8.411185 / 8.390436 / 8.831791 (90) | 0.317543 / 0.313374 / 0.396292 (89) |
| Original return | 3147 / 30.051049042 | 9.549110 | 104.7218 | 8.294044 / 8.199583 / 8.842501 (90) | 0.433904 / 0.431479 / 0.529916 (90) |

All windows warmed 40 seconds, then measured approximately 30 seconds. Independently
recomputed counters and the two GPU scopes match every release exactly. Elapsed
ms/frame is `1000*elapsed_seconds/render_frames`, including all rendered frames
within the measurement window. Throughput is frames/seconds. GPU mean/median/p95
are samples from elapsed scope rotation every five render frames; p95 is nearest
rank `ceil(.95*n)`. They do not describe all displayed frames. Candidate was 5.08%
higher ms/frame than first original and 0.35% higher than return; these are observed
comparisons, not an isolated regression estimate.

## Checked evidence and authentic consumer

Raw root: `dev/logs/speed-replication-r1/renderer-averages/bliss/`.
Inspected sequence, all plans/freezes/owners/releases/preflights, runtime reports,
console observations and representative original/candidate/return clean PNGs.
Reports/PNGs: `run-workloads/speed-replication-r1/bliss-sky/{original,control,candidate,return}/client/`.
All raw report hashes and actual archive hashes match releases/plans.

Original/return use Bliss 2.1.2 SHA `f41db92a…47b2c`, main mod absent; control
`29fcbdfb…2039` and candidate `6ab63cbb…43b7` have main mod present. Native463
options match exactly across all four. Control/candidate have only one extra
option, `SHADERLOD_BLISS_SKY_ELISION`, respectively 1/2. Expected option maps
match loaded maps. Frozen build/canonical-world records match.

All start/end states match coast `(256.5,95,768.5)`, yaw135/pitch9, Overworld
time7000, clear weather, requested/effective24 chunks; shaders active, no
fallback/DH or terrain-radius cap. All four capture windows have602 observations,
all focused/visible/non-iconified at1920×1200 fullscreen, vsync false, configured
limit260. Startup focus precedes measurement; brief unobserved changes remain
possible. Hardware/logs match Apple M3 Max/OpenGL4.1 Metal91.7, MC1.21.1,
NeoForge21.1.253, Iris1.8.12 snapshot local and Sodium0.6.13; observer in every arm.

Saved launcher plan → finite supervised profile → selected archive/options and
main config → Iris `world0/composite1.fsh` → `/dimensions/composite.fsh` → sky
branch before `SSAO(...)` → native sky overwrite/output. Mode2 has compile-time
Iris/MC/Mac/Overworld/noDH/noVoxy gates plus runtime bridge2/enabled/preset guards.
For sky depth it initializes `(1,0)` before the costly SSAO call; geometry or
runtime mismatch falls through to SSAO, and native sky overwrite stays present.
Mode1 retains the full call. These source paths and successful rendering do not
prove linked-uniform execution or the driver's optimization decisions.

## Recovery, quality boundary and live state

Each owner records PID/birth/argv, finite140-second deadline and exact session
stop command. All four exit0 with no live client PIDs, complete reports/PNGs
before deadlines; releases report clients absent/lock free. Independent process
snapshot at **20:50:24 UTC** found no ShaderLOD client/Gradle launcher. Reviewer
has no jobs and launched/stopped none.

Representative clean stills show no conspicuous missing geometry or shading loss.
Entity/water/temporal states differ naturally; **quality parity and motion are
not established**. No rigid drift gate is imposed under current sanity policy,
but drift, equal timer overhead and unmeasured host clocks/thermals remain explicit.
Direct startup/return does not establish Apply/Off UI restoration. DH/PBR,
ports, other scenes and packaging remain unchecked. No additional framework,
shader-driver attribution campaign or candidate tuning is justified by this
negative early total-render screen.

Independent calculation artifact: ignored
`dev/logs/speed-replication-r1/renderer-averages/bliss/review/recomputed.json`.
