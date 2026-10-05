# Footprint-aware lighting LOD: source decision

2026-10-05. Bounded source investigation for the
[general 50% goal](goals/general-lod-50-percent.md). Only this document is owned
by `effect_api`. No shader/config changes, tests, compiler, archive assembly,
client, encoder or GPU measurement ran. Runtime ownership remains with root's
assigned operator. Shadow/AO protection and INHERIT defaults remain unchanged.

**Decision:** reject a new distance-only PCF/SSRT/AO sample cut. A projected
penumbra gate is useful eligibility information, but cannot by itself preserve
thin shadow coverage. A small, explicitly bounded PCF comparison-reuse experiment
is mathematically plausible under verified sampler conditions; current Photon
source predicts little headroom, so do not implement it before demonstrating
eligible coverage and useful filter cost. This is a rejection of immediate
implementation, not evidence that all lighting LOD is impossible.

## Pinned source and existing adaptation

Read original members directly from local `dev/upstream/photon_v1.3b.zip`, the
maintained `patches/photon/v1.3b.json`, shared Photon helper, STATUS/active goal,
USER_LESSONS, FULL_FRAME_PROOF, GENERAL_LOD_BUDGET_AUDIT and AO runtime evidence.
Photon v1.3b's recorded archive pin is
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Member hashes below were calculated during this text-source inspection. Preserve
Photon's license and [attribution](../patches/photon/ATTRIBUTION.md) for any later
derivative. This plan inspected sources, not actual driver sampler state.

| Original member | SHA-256 |
| --- | --- |
| `shaders/include/lighting/shadows/pcss.glsl` | `c12e98fa923306687bfa5ac2b00214d45b1614ebafb719e22839f3420750a76b` |
| `shaders/include/lighting/shadows/common.glsl` | `d0e0715e2edecaf8852cdead9c98dfd9a756f68fb4f74d4e1d727ea0e799e3a6` |
| `shaders/include/lighting/shadows/ssrt.glsl` | `ffb29cc8892ebcf9bfd697467a5adc8239030a3913fac8a8315a4ab39db674b8` |
| `shaders/include/lighting/shadows/distortion.glsl` | `df662f93f8760950889bd6f6b91ddb90766187b235dcf0d2e306cc2e8bc9d4fc` |
| `shaders/include/lighting/ao/gtao.glsl` | `0889647aa827357ba9e5101431b4294299a078055f7f3537468a94fb6430b0d3` |
| `shaders/include/lighting/ao/ssao.glsl` | `e6107912799a850a49c2c62138d2f2daf542c3dcd3ca7b0e2de15cfc85e04a0c` |

PCF already uses `max(penumbra_size, min_filter_radius)`, where the minimum radius
is two shadow-map pixel sizes times local distortion. Its count is
`min(int(MIN + SCALE * (radius/min_radius)^2), MAX)` (`pcss.glsl:100–110`). The
first four Vogel-disc comparisons are unconditional; four fully lit or fully
dark results return early (`:117–158`). Remaining comparisons preserve the disc
and normalize before sharpening (`:160–181`). Thus uniform regions already cost
four comparisons, and remaining work concentrates around mixed coverage. Four
agreeing taps are an existing heuristic, not proof that no thin caster lies
between them; do not add a stronger early exit using that same evidence.

With VPS enabled, blocker search already uses three samples unless subsurface
scattering requests `SSS_STEPS`; it determines both penumbra and SSS depth
(`:29–61`, `:230–250`). With VPS disabled, penumbra depends on map pixel size and
SSS (`:251–257`). Local ordinary forest sidecars disable VPS and colored shadows.
Their unspecified settings inherit MIN=6, MAX=12, SCALE=1 and penumbra scale=1:
an ordinary non-SSS receiver has the minimum radius, a count of seven, and often
the four-tap exit. SSS can increase its radius/count. This is source-derived
headroom, not a physical comparison count measured in a frame.

Shadow-map generation is a separate geometry pass (`program/shadow.vsh/.fsh`):
animated casters, cutout alpha rejection, depth and optional colored transmission.
Changing PCF does not avoid those draws or update work. Keep caster geometry,
map resolution/range, sunlight and map cadence unchanged. The existing fade uses
both receiver distance and shadow-map edges (`common.glsl:5–15`); deferred lighting
blends PCF into SSRT/lightmap shadows (`d4_deferred_shading.fsh:483–536`). An extra
far fade could erase precisely the distant coverage SSRT supplies.

SSRT already shortens its trace with view distance, distributes samples
geometrically with ratio two, and stops on a hit (`ssrt.glsl:20–99`). It retains
different combined/DH depth projections around the vanilla boundary (`:128–173`).
Lowering its count changes sample locations throughout that geometric schedule;
thin blockers can disappear even if the total ray interval is retained. Identical
depth texels are not identical ray-depth/SSS intersection evaluations. There is
no source-certified no-occluder bound that permits removing those samples.

GTAO already reduces its radius very near the camera and expands it for DH
(`gtao.glsl:91–101`). It samples both horizon directions for every slice, then
integrates AO, ambient SSS and bent normals. Current defaults are two slices and
three horizon steps. Its AO pass is half linear resolution with temporal history;
deferred reconstruction is depth-aware. SSAO caps projected radius, preserves a
world-radius falloff and normalizes then cubes visibility. These paths already
contain spatial/distance controls; count reduction is distinct from changing AO
radius, strength, output resolution or normal/SSS channels.

## Why the earlier cuts lost structure

The maintained unprotected PCF patch caps medium at four/two taps and far at one.
Far uses a center offset and replaces the initial-four test with the reduced
count. One bilinear comparison covers its local texel footprint rather than the
original disc: it can miss nearby thin caster coverage and replace an area-average
penumbra with a binary-looking result. Receiver distance or a subpixel projected
penumbra does not bound that difference. TAA may reduce stochastic noise; it does
not guarantee reconstruction of removed coverage.

Aggressive far GTAO returns AO=1/ambient SSS=0 with the view normal as bent normal;
SSAO returns 1. These are missing lighting contributions, not just fewer samples
of the same estimate. Sparse SSRT misses occluders in its larger gaps. The user
observed missing distant tree shadows and rejected roughly 0.5 ms saved; the
agent had underemphasized flatter foliage. The cited earlier AO pass interval was
only 0.057 ms and is workload/instrumentation qualified, not a universal budget.
See [the recorded lesson](USER_LESSONS.md). Do not attribute the observed failure
to PCF alone: several lighting paths changed together.

The recent exact GTAO depth-reuse pilot retained arithmetic but regressed in its
single, qualified forest block. It gives no accepted gain and warns against
assuming texture-fetch reuse beats native caching/register costs. Its focus and
possible external CPU overlap qualifications remain in
[AO runtime evidence](PHOTON_AO_REUSE_RUNTIME.md).

## Reusable eligibility and error rule

A future explicit adapter should carry three pieces of information locally:
camera-relative distance, the **projected filter footprint**, and a bound on
the **lighting-result error**. Distance gates importance; footprint describes
sampling; error determines whether removing work is permissible. None is a
replacement for the other two. No capability registry or new uniform protocol is
needed to investigate this rule.

For a flat receiver, analytically project two receiver-tangent offsets into
display pixels and distorted shadow texels, obtaining two 2×2 Jacobians. Their
composition maps a display-pixel displacement into shadow texels. Invert it to
estimate the filter disc's major/minor radius in display pixels. Use the actual
viewport/FOV/TAAU scale and light projection; do not treat block distance or map
resolution as a screen-footprint proxy. Invalid/singular transforms, grazing
receivers, large Jacobian variation over the filter, depth/material discontinuities
or missing DH projection support select the original path. Capture derivatives
before divergent control flow if derivatives replace the analytic construction.

An initial investigational gate would keep full original work through the entire
near band and permit scrutiny only beyond the far band's end (100 blocks for
32/96/8), with projected major radius below 0.5 display pixel. These are conservative
starting thresholds, not accepted optima. Even when that gate passes, an additional
error certificate is required. The original filter radius, all sample positions,
weights, sharpening and comparison depth stay fixed. Do not shrink a disc to
manufacture a subpixel footprint or substitute a center sample.

One concrete certificate is possible **if** the actual opaque comparison sampler
performs bounded bilinear interpolation of four depth-comparison results at a
fixed reference depth. In shadow-texel units, that scalar result has slopes bounded
by one in each axis. Reusing a fetched value at another coordinate therefore has
ideal interpolation error at most the coordinate L1 separation `delta_i`.
Require complete valid footprints, fixed map/reference depth, verified filtering/
comparison/wrap modes and a separately established hardware precision allowance
`eta`; otherwise use the original fetch. Same texel cell alone is insufficient.

Keep the original first four comparisons and early-exit decision byte-identical.
For each remaining comparison, reuse a previous fetched value only while
`sum(delta_i + eta) <= N * epsilon / G`, where `N` is the unchanged original count
and `G=1/(1-2*a)` is the actual sharpening gain. Photon clamps its sharpening
threshold `a` to at most 0.4, so `G<=5`. Normalize by the original N and run the
original sharpening. This bounds the ideal scalar visibility deviation by
epsilon even for an arbitrary thin caster pattern; empirical hardware precision
and final lit-color perception still need separate acceptance. A fresh fetch
resets the cached coordinate/value, not the cumulative per-filter error budget.

Start with an investigational epsilon no larger than 1/255 in visibility, full
near/original fallback and default-off source selection. That value is not a
perceptual guarantee: direct-light intensity/specular response can amplify it.
Approximate reuse remains a quality change and must not bypass default lighting
protection. With protection enabled, only genuinely identical coordinates and
reference inputs may qualify for exact reuse; do not declare a nonzero epsilon
safe under the existing protected flag. INHERIT/FULL/unknown extension versions,
missing mod or unsupported sources preserve their defined original behavior.

## Small adaptation boundary and stopping decision

If headroom is later demonstrated, the first patch would touch only the opaque
remaining-PCF fetch site in pinned `pcss.glsl` and its explicit default-off gate.
Retain all loops, disc positions, count, radius, first-four behavior, blocker/SSS
search, colored-shadow path, fade and shadow generation. Exclude colored shadows,
SSS, pixelated shadows, grazing receivers and DH initially. This is comparison
work reuse, not a changed light radius or generic count-cap consumer. The new
`effect_policy.glsl` work ratios alone cannot provide the error certificate.

Current source predicts low eligible coverage. Photon enforces an approximately
one-shadow-texel minimum disc radius after local distortion; seven-to-twelve
Vogel samples normally span appreciable texel fractions. At N=7, even G=1 and
epsilon=1/255 permit only about 0.027 texel of total replacement separation across
the remaining taps, before hardware allowance. Mixed edges are where the original
four-tap exit fails. Strict certified reuse is therefore unlikely to remove enough
work to justify extra footprint/branch/register calculations in the current
reduced Mac profile. This is a source inference, not measured eligibility.

Stop here rather than implement a large lighting framework or a relaxed sampler
cut. Reconsider only after quiet measurements establish both material filter cost
and eligible footprint coverage; a large deferred pass is not an isolated PCF
budget. If that happens, first validate actual sampler state and bound adversarial
thin/alternating coverage, then compile/render a single explicit patch. Require
original/mod-absent, patched-disabled and enabled controls, repeated whole-frame
GPU/total-time evidence, near/far transitions, moving foliage/shadows, contact/
silhouette boundaries and later DH-present/absent checks. Failed lighting/precision
guards or flat/regressive timings terminate the candidate. No result here advances
the unachieved 1.5× throughput target.
