# Photon temporal inputs and deterministic capture proposal

Source/bytecode audit checkpoint: 2026-10-04, Photon v1.3b and pinned Iris 1.8.12.
Research only while the main matrix is live. Only this document was written;
no shader/mod/profile edits, compilers or clients were run. The repeated premium
cloud active/optimized captures differ (62.77 versus 79.51 FPS), but this audit
cannot identify which unmeasured state caused that difference.

## The cloud clock is world age, not frameTimeCounter

Photon `shaders.properties` defines:

```text
world_age = ((worldDay % 128) * 24000.0 + worldTime) / 20.0
```

Cloud coverage, volumetric Worley detail, altocumulus, congestus and cirrus wind
use this custom `world_age`. No cloud-density or weather include reads
`frameTimeCounter`; the cloud programs declare it but the density functions
consume world age. `WorldTimeUniforms` bytecode derives worldTime from client
level day time modulo 24000 and worldDay from the same value divided by 24000.
Current ComparisonClient applies `time set 1800` and `doDaylightCycle false` for
the storm, which should fix worldDay 0 and world_age 90 once acknowledged by the
client. That is a source-based expectation; exported day_time 1800 does not
currently record the actual Iris/custom uniform values.

With original resolved CLOUDS_SCALE 10 and default speeds:

| Layer | Shader-coordinate wind speed per world_age second | Equivalent world-block displacement |
| --- | ---: | ---: |
| Cumulus | 25 at 30 degrees | 2.5 blocks/second; x≈2.165, z=1.25 |
| Altocumulus | 25 at 60 degrees | 2.5 blocks/second; x=1.25, z≈2.165 |
| Congestus | 20 at 30 degrees | 2 blocks/second |
| Cirrus | 40 at 90 degrees | 4 blocks/second |

These are coordinate mappings, not measured live motion. With frozen day time,
world_age should not advance merely because the client runs longer. A changed
worldDay by one would change world_age by 1200 seconds and displace cumulus
coverage 3000 world blocks, despite equal time-of-day. Capture full day time and
actual worldDay/world_age to exclude it. Existing raw reports have full
`day_time=1800`, so they already make that day mismatch less plausible here.

Cumulus coverage uses `pos + cameraPosition.xz*10 + wind*world_age`; its noise
UV frequencies are 0.000002 and0.000027 divided by CLOUDS_CUMULUS_SIZE. Default
world-age-driven UV rates therefore have vector magnitudes0.00005 and0.000675
per world-age second. Bubbly/swirl detail sees the camera-shifted position plus
1.2/1.4 times wind respectively, at frequencies0.0009/0.005; default UV-speed
magnitudes are0.027 and0.175 per world-age second. Congestus coverage also adds
`50*squared(altitude_fraction)` to world_age locally; that is spatial shear,
not a wall-clock dependency. These paths can change actual density work if
world age differs, but freezing frameTimeCounter alone does not freeze them.

## Uncaptured state that can change cloud work

The most concrete additional input is **Iris wetness**, separate from the
Minecraft rain value exported by the harness. `CommonUniforms` registers
wetness as a SmoothedFloat driven by rainStrength, using pack wetness/dryness
half-life values. Pinned defaults are 600/200; `SmoothedFloat` multiplies each
by 0.1 before applying exponential decay. The effective default half-lives are
60 seconds when increasing and 20 seconds when decreasing. Photon declares no
override in its GLSL sources. The smoother initializes from its first observed
rainStrength and updates using Iris Timer.getLastFrameTime.

An idealized immediate rain step from 0 to 1 gives wetness about 0.333 after 35
seconds and 0.470 after 55; gradual Minecraft rain ramps change those values.
The current35-second warmup therefore does not prove wetness convergence.
A reused storm save may begin already raining on the next JVM, initializing
wetness differently from its initial copied forest save. That mechanism is
plausible, not demonstrated by the current reports: initial rain/wetness and
actual wetness during measurement were not captured. A fully wet steady state
would produce the same wetness across runs; do not assume every run starts dry.

Photon's `include/weather/core.glsl` adds wetness directly to humidity and
0.33 * wetness to wind before clamp01. Random weather temperature/humidity use
world_age at `0.37*golden_ratio/600` times their speed options; wind uses
`0.5*golden_ratio/600`. Daytime and smoothed biome values modify these fields.
`include/weather/clouds.glsl` converts them into layer coverage, stratus blend,
shape detail, altitude scale, extinction and congestus blend. RainStrength also
changes extinction/scattering and cirrus coverage directly. More density can
increase lighting samples or reach opacity termination earlier, so the sign of
its timing effect cannot be predicted simply as "more clouds means slower".

Additional fields to record rather than assume matched:

- Actual wetness and rainStrength, worldDay/worldTime/world_age and moonPhase.
- Smoothed biome_temperature/humidity/category weights and biome_may_rain/snow;
  the properties use smooth histories. Equal final camera position does not
  expose their initial state or prove identical convergence.
- world_age_changed and daylight_cycle_enabled, derived through smooth
  expressions; changes invalidate cloud temporal history or alter wind
  compensation in reprojection.
- Actual frameCounter, its phase at measurement start/end, frameTimeCounter,
  real frameTime and cloud history age. The cloud accumulation limit is 20 frames.
- Actual resolved cloud parameters or a CPU equivalent derived from captured
  uniform inputs, plus rain/thunder and last camera matrices. No GPU readback is
  needed merely to capture values before they are uploaded.

The two inspected active/optimized raw captures (`003-b1` and `017-b2` under
`dev/logs/sustained-v3-main`) match camera, day_time 1800, rain1, thunder 0, 35s
warmup and all relevant resolved cloud options. They contain 1256 versus 1591
frames over 20s. Their input snapshots record day_time but none of the uniform
fields above; they cannot prove equal shader wetness or temporal initialization.

## Actual march/noise dependence

Cumulus maximum primary count is `uint(mix(40,20,abs(ray_dir.y)))` in the current
profile. ShaderLOD caps that count using the entry-distance tier. Lighting starts
at 6 steps and ambient at 2; Performance medium budgets are roughly half with
minimum lighting 2/ambient 1 and primary 4. Those caps do not describe executed
steps. The primary loop breaks when transmittance falls below 0.075; empty-density
samples skip secondary lighting/scattering. Later layers can be omitted when
combined transmittance falls below 1e-3. Cloud weather and density thus control
branch/loop work even with the same maximum settings.

`d1_clouds` computes spatial noise and applies `r1(frameCounter/checkerboard_area,
seed)`, where r1 is `fract(seed + n/phi1)` and checkerboard_area is upscale squared.
Premium upscale 1 advances that seed every rendered frame; default upscale 4
advances its outer sequence per 16 frames and cycles checkerboard offsets.
The same dither also chooses probabilistic distance tiers inside transition
bands. Secondary optical rays use hashes of jittered sample position.
Starting at a different frameCounter changes sampled density and tier choices.
Across enough frames this should average many phases, but equal wall durations
sample different counts and do not establish an identical phase sequence.

Do not freeze frameCounter for the performance proof: that would disable its
normal stochastic/temporal behavior and could create a special rendering
workload. Keep it advancing, log phase/count, and retain paired repetitions.

## Exact Iris seam for an optional static animation clock

The relevant class is `net.irisshaders.iris.uniforms.SystemTimeUniforms`, not
IrisTimeUniforms (the latter publishes calendar/currentDate/currentTime fields).
Its `addSystemTimeUniforms` binds PER_FRAME:

```text
frameCounter     <- SystemTimeUniforms.COUNTER (IntSupplier)
frameTime        <- TIMER.getLastFrameTime()
frameTimeCounter <- TIMER.getFrameTimeCounter()
```

`SystemTimeUniforms$Timer.beginFrame(long)` derives integer milliseconds from
elapsed nanoseconds, divides by1000 into seconds, updates lastFrameTime and adds
it to frameTimeCounter, wrapping at 3600 seconds. Its public getter is an exact
small seam for a future **devClient-only**, opt-in return-value mixin:
`getFrameTimeCounter()F`, remap false, cancellable HEAD or RETURN, require 1.
When a finite `shaderlod.dev.shaderClockSeconds` property is present, publish
that chosen constant; otherwise preserve the real getter. Do not mutate
beginFrame, lastFrameTime, real elapsed timing or counter state. Restrict behavior
to the development harness, exclude the mixin from the released mod and record
mode/value in fingerprints and measurement exports.

Apply the static value from in-world warmup onward, not at capture start, to
avoid a clock jump after histories have converged. Keep frameCounter and actual
frameTime advancing for TAA/checkerboard/noise and motion metrics. Freezing the
getter also does **not** freeze wetness: SmoothedFloat reads getLastFrameTime,
which deliberately remains real. This seam is useful for directly animated
water waves, aurora, air-fog noise and other frameTimeCounter consumers, but
cannot alone settle the cloud density question.

For the cloud static control, first record wetness. A separate future dev-only
wetness uniform-supplier override, before upload in CommonUniforms registration,
can publish a shared0 or 1 value for both original and ShaderLOD profiles. Keep
real rainStrength unchanged and clearly name the control as fixed shader
wetness; it selects an explicitly artificial steady shader weather state.
Do not override all SmoothedFloat getters, which would corrupt biome/eye/history
smoothers. Preserve the existing supplier and install a narrow wetness-only
registration wrapper when the optional property is absent. That seam needs
separate bytecode/application tests and logged actual supplied values.

## Small follow-up experiment after the live matrix

1. Add export-only temporal inputs first. Repeat original high, patched-off and
   active on the same storm with at least three balanced pairs, fresh initial
   saves or explicit recorded startup weather, real shader clocks. Determine
   whether wetness/world-age histories differ in the problematic runs.
2. If they differ, repeat those same arms with narrowly fixed shader wetness 1
   and matched world_age 90/day 0, leaving frameCounter/frameTime real. Preserve
   all original march settings. Compare repeat variance and images; this tests
   weather initialization rather than claiming an optimization.
3. Separately test the optional frameTimeCounter constant (for example 10 seconds)
   on fixed forest/coastal water and storm, fixed from warmup. Compare against
   real-clock runs and at least one other static phase. Improvement only at one
   phase cannot establish general rendering benefit.
4. Keep a normal-clock camera-motion/weather-transition session as separate
   fidelity acceptance. A static animation clock is benchmark instrumentation,
   not a production feature, and must not mask temporal shimmer, ghosting or
   conservative water-band costs.

Only matched actual-input evidence can support a cause. The current variable
captures remain valid observations of that harness, with unmeasured temporal
inputs acknowledged. No timing result here is attributed to wetness or clock
phase, and no production shader change follows from this audit alone.
