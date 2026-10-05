# Photon candidate isolation preparation

`scripts/prepare_isolations.py` prepares fresh matched inputs without launching
Minecraft. Use it only after the coordinator's current matrix finishes and all
source/target clients stop. The script has not been run against real saves as
part of its implementation; tiny-fixture tests cover planning and guards.

The preserved candidates isolate different source changes:

| Selector | Archive | Required SHA256 |
| --- | --- | --- |
| `analytic` | `build/photon-analytic-candidate.zip` | `7cae01ced084885d6e66102b26803077fb0ed2093f854fe791b1ac8327abba19` |
| `structural` | `build/photon-structural-candidate.zip` | `999eae1c4c3551e0f02d45ae06b517c87f966b87b7ca6a8e779a16566c6a003b` |
| Original control | Pinned `dev/upstream/photon_v1.3b.zip` | `120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead` |

An alternate `--archive-path` changes only its location: the selected exact hash
still applies. Derivative metadata must identify the pinned original Photon
version/SHA512 and bridge version 2. Archives must retain the original license
bytes; duplicate or unsafe entries fail validation. This never rebuilds or
changes the preserved packs.

## Isolated inputs

For each selected scene, the source is
`SOURCE_PROOF_ROOT/{forest,cloudstorm,coastalwater}/high`. Every output scene has
`high`, `patched-off`, and `adaptive` profiles, compatible with the existing
proof runner's case names. Original high has original Photon and no main mod in
its run task; patched-off and adaptive use the same chosen candidate, with the
main mod present and live config false/true respectively.

The preparer prefers the stopped original-high measurement's complete resolved
Iris options. It verifies that current requested values agree with that capture,
then writes the same upstream settings into all three profiles. A missing
measurement falls back to matched requested overrides over identical upstream
source defaults, explicitly labeled static/unverified. Normalized option-file
bytes are not treated as complete effective values. Candidate archives may not
change upstream source defaults for these controls.

Camera, time, weather, requested radius, Minecraft/Sodium/DH settings and source
world remain matched. Scene control drops enabled/terrain overrides, so it
cannot inadvertently enable the disabled arm. Geometry is explicitly disabled;
lighting is protected, cached sky disabled, generic mod bands fixed at 32/96/8,
cloud bands fixed at 128/1024/64. Water knobs are independent:

- `--analytic on/off` toggles `SHADERLOD_WATER_ANALYTIC` on the same archive.
- `--water-bands current` explicitly writes 96/256/32.
- `--water-bands old` explicitly writes 32/96/8, matching the earlier generic
  bands while retaining this archive's other source changes.

The old-band option does **not** reconstruct the previous shader source. That
remaining difference, including helper changes, must be labeled when interpreting
results. See [PHOTON_REGRESSION_AUDIT.md](PHOTON_REGRESSION_AUDIT.md).

## Preparation commands

After the live matrix and source clients have stopped:

```sh
# No world copies/hashes: validate archive/config provenance and print operations.
python3 scripts/prepare_isolations.py --output run-workloads/isolation-analytic --archive analytic --analytic on --dry-run
# Same source scene/camera settings; active water-only code.
python3 scripts/prepare_isolations.py --output run-workloads/isolation-analytic --archive analytic --analytic on
# Same water/cloud policy with the additional atmosphere guard.
python3 scripts/prepare_isolations.py --output run-workloads/isolation-structural --archive structural --analytic on
# Same analytic archive and bands, finite-difference normals instead of approximation.
python3 scripts/prepare_isolations.py --output run-workloads/isolation-finite --archive analytic --analytic off
# Same finite-difference candidate source, old water distance policy.
python3 scripts/prepare_isolations.py --output run-workloads/isolation-old-bands --archive analytic --analytic off --water-bands old
```

Default sources are `run-workloads/photon-proof-v2`; change with
`--source-proof-root`. Restrict copying with `--scenes forest coastalwater` if
storm is unnecessary. `--without-dh` removes the DH jar from prepared profiles;
this requires matched `--dh absent` timing later. No optional optimizer jars are
added: runtime stack selection remains a runner/Gradle control. Unmanaged source
profile mod jars fail preparation instead of contaminating later comparisons.

Default behavior refuses any existing output. `--backup-existing` requires a
previous isolation manifest, locks existing worlds and preserves the entire old
root in a timestamped sibling before reserving a fresh directory. Source/output
containment is rejected. A failed copy stays marked failed with the partial tree
and any backup preserved; do not benchmark it. Source worlds must be closed,
and file locks remain held throughout copying. Saves are hashed before/after
copy and compared against each target; source config/control/report hashes are
also checked for changes. Preparation can require substantial disk space and
hashing time, so do not do it while another matrix measures the GPU/CPU.

Each scene's `workload-manifest.json` retains raw source hashes, copied-world
hashes, prior provenance, exact archive metadata and common options.
`isolation-manifest.json` tracks the full operation and completion/failure.
No captures, jars, worlds or raw reports belong in Git.

## Later measurements

Run one matrix root at a time, using the existing tool without edits:

```sh
python3 scripts/run_proof_matrix.py forest-high forest-patched-off forest-adaptive water-high water-patched-off water-adaptive --profiles-root run-workloads/isolation-analytic --stacks minimal --repeats 3 --warmup 35 --duration 20 --tag isolation-analytic-r3
```

Equivalent commands select the structural/finite/old-band roots and fresh tags.
Within-root high/off/active pairing measures each variant's incremental effect.
Across-root active comparisons remain separate experiments with their own order
and thermal drift; compare effects against each root's original baseline instead
of treating them as directly paired or simultaneous. Preparation proves matching
inputs and copies, not compilation, visual fidelity or performance. Actual Iris
options, fallback and timing guards still apply at runtime.
