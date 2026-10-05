# Cloud-transition controls

`prepare_cloud_profiles.py` prepares fresh, matched configuration profiles; it
never launches a client. Its selected candidate is
`cloud-transition-no-atmosphere`, pinned by `prepare_isolations.py` to:

```
build/photon-cloud-transition-no-atmosphere.zip
8fbaa117d87b4f729ea64ecce4d86390cdc56d03cd3b5a02ec2be9ffe2213235
```

The derivative preserves Photon's license and base/bridge provenance. The tool
reuses isolation preparation's exact archive checks, upstream-default equality,
source effective-option evidence, source/config fingerprints, stopped-save
locks, copied-save fingerprints and atomic fresh-output reservation. It refuses
an existing output and does not replace or modify source profiles. The source
control preset is retained; no preset or original sample count is raised.

## Arms and requested values

All arms use the same requested `CLOUDS_TEMPORAL_UPSCALING` value, selected as
1 (full spatial resolution) or 4 (upstream default quarter linear resolution).
The high arm selects original Photon and the runner uses ShaderLOD absent. All
other arms select the exact candidate and load ShaderLOD.

| Profile leaf | Mod enable | Cloud switch | Cloud mode | Overhead protection |
| --- | --- | --- | ---: | --- |
| `high` | Absent | Original | Original | Original |
| `patched-off` | False | True | 0 | False |
| `alloff` | True | False | 0 | False |
| `cloud-current` | True | True | 0 | False |
| `cloud-independent` | True | True | 1 | True |
| `cloud-smooth` | True | True | 2 | True |
| `cloud-independent-unprotected` | True | True | 1 | False |

Mode is `SHADERLOD_CLOUD_MODE`; protection is `SHADERLOD_CLOUD_OVERHEAD`
(mode0 ignores it). Mode0 is the existing shared-dither tier selection, mode1
uses independent transition sampling, and mode2 uses deterministic smooth
integer budgets. The final unprotected arm separates a change in sampling mode
from overhead protection, so their effects are not conflated.

Every patched arm explicitly requests:

```
SHADERLOD_WATER=false
SHADERLOD_SSR=false
SHADERLOD_FOG=false
SHADERLOD_MATERIALS=false
SHADERLOD_PRESERVE_LIGHTING=true
SHADERLOD_CACHED_SKY=false
SHADERLOD_WATER_ANALYTIC=false
```

Other Photon quality/material/reflection options remain matched to source high,
including its existing SSR settings. Turning ShaderLOD SSR off does not turn
original Photon's reflections off. Isolation preparation supplies the common
128/1024/64 cloud bands. Mod geometry is disabled. The experimental atmosphere early-return guard is absent from this
candidate; original atmospheric scattering remains. This prevents that separate
optimization from explaining cloud-mode results.

`patched-off` keeps cloud code compiled but uses the disabled mod bridge;
`alloff` compiles out all five ShaderLOD effect switches with the mod enabled.
These expose infrastructure/compile-path cost before comparing mode0/1/2.
Requested options and mod settings still need runtime validation—preparation is
not evidence that Iris loaded them or that a shader rendered.

## Prepare only after stopping source clients

First validate the shoreline source and exact archive without copying saves:

```sh
python3 scripts/prepare_cloud_profiles.py \
  --source-proof-root run-workloads/effects-ssr-shoreline \
  --output run-workloads/cloud-shoreline-controls \
  --scenes coastalwater --cloud-upscaling 4 --without-dh --dry-run
```

Remove `--dry-run` only after source clients are stopped. It creates seven
verified save copies in a fresh directory; do not copy during a timed run.
The default multi-scene source is `run-workloads/photon-proof-v2`. For a
full-resolution cloudstorm matrix:

```sh
python3 scripts/prepare_cloud_profiles.py \
  --source-proof-root run-workloads/photon-proof-v2 \
  --output run-workloads/cloudstorm-transition-controls \
  --scenes cloudstorm --cloud-upscaling 1 --dry-run
```

Omitting `--without-dh` preserves the pinned DH jar from the source, if present;
this does not create DH caches or imply loaded distant terrain. Preserve the
same DH choice and runtime stack across paired arms. The tool installs no new
optimization mods and does not change scene cameras or source presets.

Manifest fields record the candidate hash, per-arm requested mode/protection and
effect flags, inherited source/runtime/option provenance, exact common cloud
resolution, and verified copied-world fingerprints. Geometry and analytic water
remain disabled. Output is marked incomplete while profile expansion occurs;
errors preserve a failed checkpoint for diagnosis.

## Runner and measurement expectations

Runner cases are `water-high`, `water-patched-off`, `water-alloff`, and
`water-cloud-current/independent/smooth/independent-unprotected` for coastalwater.
Cloudstorm uses the corresponding `cloud-` prefix. Original high runs
`runClientLodOff`; patched arms run `runClientLodOn`. The runner owns execution,
strict loaded-option checks, copied-save restore and serial ordering.

Use repeated matched serial blocks and identical timing overhead. Compare original
high to patched-off and alloff before interpreting candidate gains. Keep cloud
spatial resolution equal within each matrix; a full-resolution matrix cannot be
reported as a speedup over the quarter-resolution original. Inspect overhead
cloud structure, transitions, movement, temporal reconstruction and reflections,
not just frame time. Reporting a lower global cloud resolution as a practical
alternative is useful but must be a separate control.

Static tool checks:

```sh
python3 -m unittest discover -s tests -p test_cloud_profiles.py -v
```

Six pure-fixture tests cover exact recipes, the single-knob protection control,
legal declared values, fresh output/path separation, inherited-preset policy and
a dry-run that forbids world hashing/copying/writes. Real source/archive dry-run
validation passed for the SSR shoreline source with upscale4/DH absent. No
worlds were copied, clients launched or timings measured by this tool-validation
step.
