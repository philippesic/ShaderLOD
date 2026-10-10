# BSL temporal shadow candidate — independent early cost review

Reviewed October 10, 2026, approximately14:25 PDT by `/root/makeup_average_review`.
Only this report and ignored `bsl/review/recomputed.json` written; root owns the lane.

**No useful original-relative total-render gain; reject retention of this fixed
candidate.** Candidate throughput is below both originals. Original-return elapsed
ms/frame drifts **5.68%**; retain that uncertainty rather than asserting an exact
causal regression. Execution admission/cue and source work do not override this
negative total-render screen. No cadence/motion quality acceptance is established.

| Arm | Frames / elapsed seconds | Elapsed ms/frame | Throughput FPS | Sampled game-render mean / median / p95 ms (n) |
| --- | --- | --- | --- | --- |
| Original | 3090 / 30.035606541 | 9.720261 | 102.8779 | 8.242646 / 7.725875 / 11.611499 (88) |
| Control | 3189 / 30.034214667 | 9.418067 | 106.1789 | 7.968591 / 7.377625 / 11.062624 (91) |
| Candidate | 2829 / 30.050094250 | 10.622161 | 94.1428 | 9.255591 / 8.740959 / 12.185209 (80) |
| Original return | 2925 / 30.045806167 | 10.272070 | 97.3514 | 8.714335 / 8.365375 / 11.914625 (83) |

All arms warm40s, measure30s. Independently recomputed all released GPU statistics
and total arithmetic exactly. Elapsed ms/frame=`1000*elapsed_seconds/render_frames`;
FPS=frames/seconds. Denominator is rendered frames during the measured window,
not instantaneous FPS readings. GPU samples rotate elapsed scope categories every5
render frames; p95 is nearest rank `ceil(.95*n)`, not all displayed-frame tails.
Candidate observed ms/frame is9.28% above first original and3.41% above return;
these comparisons do not isolate a regression caused by reduced filtering.

| Arm | World opaque/cutout GPU mean / median / p95 ms (n) | World translucent GPU mean / median / p95 ms (n) |
| --- | --- | --- |
| Original | 2.776711 / 2.603105 / 4.473126 (88) | 0.366164 / 0.373874 / 0.436375 (88) |
| Control | 2.772459 / 2.617125 / 5.111333 (91) | 0.366715 / 0.362375 / 0.473458 (91) |
| Candidate | 3.227082 / 2.923958 / 4.488208 (81) | 0.490332 / 0.484250 / 0.650958 (81) |
| Return | 2.996818 / 2.834375 / 4.290251 (84) | 0.463684 / 0.449000 / 0.588626 (84) |

These are complete sampled rendering scopes, not isolated shadow-filter costs.
No sum of pass samples is substituted for the measured total window.

## Matched evidence and authentic consumer

Evidence root: `dev/logs/speed-replication-r1/renderer-averages/bsl/`.
Inspected real sequence, plans/launch plans/freezes/owners/releases, loaded reports,
postrun disposal records and original/candidate/return clean PNGs. Reports/PNGs:
`run-workloads/speed-replication-r1/bsl-temporal/{original,control,candidate,return}/client/`.
Raw report SHA values and actual archive hashes match plans/releases.

Original/return archive SHA `36b0a50f…930b5`, native384 options, main mod absent.
Control `869b080d…c8f99`, candidate `f95cac27…78412`, main present. Every native
option is identical across arms; the only extra is `SHADERLOD_BSL_SPEED_R1=1/2`.
Loaded maps match expected maps/final artifact options. Build freezes match all963
entries, including329 source/input entries; canonical-world records match. Loaded
candidate main class-directory identity is recorded in its raw artifact inventory.

Start/end camera `(256.5,95,768.5)`, yaw135/pitch9, Overworld time7000, clear
weather, requested/effective24chunks all match. Shaders active, fallback false,
DH absent, no terrain cap. Each arm has602 focused/visible/non-iconified capture
observations at1920×1200 fullscreen, vsync false/limit260; warmup also matches.
Sampling cannot exclude brief window changes. Stack is MC1.21.1/NeoForge21.1.253,
Iris1.8.12 snapshot local/Sodium0.6.13 on Apple M3 Max/OpenGL4.1 Metal91.7, with
dev observer in all arms. No diagnostic-only report or cloud probe is enabled.

Saved launcher/selected zip/options/core config → Iris Overworld terrain fragment
→ `program/gbuffers_terrain.glsl` → forward lighting → `shadows.glsl:GetShadow`
→ native9/private3 filter → native composition/output. Inspected candidate guards:
bridge2/enabled1/Performance1 and valid distances precede distance/Bayer selection.
Near/mismatched runtime uses native9; candidate chooses one complete kernel with
native frame-counter phase, control retains native9. Compile-time nativeTAA/
filtered-shadow/noDH/noVoxy and other supported-path gates remain. This source
trace does not assert an isolated driver cost. The separately admitted broad
magenta execution cue is untimed evidence; it is not present in the clean Mode2
cost images and does not establish cadence quality.

## Recovery and boundaries

All owners exit0/no live client PIDs after complete reports/PNGs before their
140-second deadlines; exact PID/birth/argv and session stop commands are recorded.
Releases record clients absent/lock free. Every disposed completed profile world
has238 postrun file hashes retained; measurements/images/configs remain. Deleted
clone bytes cannot be independently rehashed. Reviewer process snapshot at
**21:22:51 UTC** found no ShaderLOD client/Gradle launcher; no owned jobs launched/stopped.

Three clean stills show no conspicuous missing geometry/shading loss, with natural
entity/water differences. This is not quality parity, temporal cadence or motion
acceptance. Timer overhead, laptop thermal/clocks/shared-host uncertainty and
5.68% drift remain. Direct startup/mod-absent return does not prove Apply/Off UI;
DH/PBR, other scenes/ports and packaging are unverified. No candidate tuning or
additional attribution framework is earned by this negative early screen.

Independent calculations: ignored
`dev/logs/speed-replication-r1/renderer-averages/bsl/review/recomputed.json`.
