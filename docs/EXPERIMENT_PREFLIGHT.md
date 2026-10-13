# Offline experiment-plan preflight

For fresh generated-pack option preparation, reuse
`scripts/discover_iris_options.py` with explicit original/generated ZIPs, the
observed native map, and pinned Iris/JDK dependencies. It derives added declarations
through Iris's actual parser and preserves native values; see the
[tested invocation and limits](evaluation/developer-cycle-r2-20261012.md).
`candidateExpectedOptions` is preparation evidence. Broad declarations include
unused options, so neither output replaces actual loaded-map equality. This is
optional reuse for changed option boundaries, not another universal preflight gate.

Before a finite dev-client probe, inspect the actual saved `plan.json` or the
`plan` object in its existing `owner.json`:

```sh
python3 scripts/check_experiment_plan.py /absolute/run/owner.json \
  --freeze /absolute/run/freeze.json --check-built
```

The standalone read-only checker batches independent cheap defects. It uses
existing `dev_client.inspect_pack`, path/property representations, runtime pins
and optional `validate_built` without changing the launcher. It checks finite
absolute deadline/readiness margin, profile/world/Iris files, safe ZIP descriptor,
selected pack/options, actual Gradle argv/property consistency (including performanceStack names from
the existing lock), minimal/DH mods,
scene/control inputs, expected loaded map/count, selected archive hash,
sourceReview against existing `freeze.files`, and named capture/original archive
requirements, including a writable/traversable directory (or nearest existing
ancestor) for capture output. Only the sourceReview entry in freeze is hashed here; this does
not replace the experiment's full frozen-input guards. Optional current build
validation hashes the existing source/classes/resources receipt and never runs
a build. No worlds are traversed or modified. Report errors together, fix the
actual future input once, then rerun affected checks.

For retained history, expiry and existing named receipts are expected:

```sh
python3 scripts/check_experiment_plan.py /absolute/old-run/owner.json \
  --freeze /absolute/old-run/freeze.json --historical \
  --receipt /absolute/old-run/captures/name.json
```

Historical mode is static inspection, never admission to relaunch. The optional
receipt check reads actual `ControlCaptureReceipt` fields: saved-observations,
pack/hash/options, native viewport and focus. It does not call Minecraft or
claim that loaded options are linked uniforms or executed work. Full source,
attached-program/frame/contents identity, JVM argument-file consumption,
rendering, avoided work, quality and performance remain separate boundaries.
The packaged UI helper's actual argument-file admission stays in
`scripts/dev_client_ui.py`; this checker neither replaces nor relaxes it.

A clean static result does not reserve a lane or authorize a launch. Parent must
verify resource ownership, actual built/source pins, finite supervision/stop,
quiet-window terms and the specific user-authorized runtime operation using the
existing launch/control APIs. This maintenance task starts no clients/builds.

The authentic regression fixture preserves the failed Reimagined plan unchanged:
its pre-supervision null deadline and copied Photon sourceReview are rejected.
The owner wrapper supplies the real finite deadline, but the stale reference
still fails its frozen pin. Historical provenance correction remains a separate
record; it is not silently applied to original evidence. Tests use tiny temporary
filesystem stand-ins plus a projection of the real 399-option receipt. Passing
unit tests verifies this offline boundary, not improved runtime or agent autonomy.
