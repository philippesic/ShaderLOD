# Retire the old checkout and bulk history

## Goal
Finish removing the retired local checkout and bulk historical archives without
losing current work, compact evidence or Git recovery.

## Authorized
Philip approved publishing the rebuilt codebase, preserving both histories, extracting
historical knowledge, and renaming the old checkout to prepare it for deletion.
The new checkout is now ShaderLOD. Actual deletion remains the next requested action;
do not infer permission to remove new runtime profiles, new evidence or Git bundles.
The earlier one-time metadata force-push exception is complete, not standing authority.

## Done when
The old checkout and superseded bulk history are retired after confirming no new work,
and this completed task is removed.

## State
Canonical checkout: /home/philip/ShaderLOD, branch main, established GitHub origin.
Old checkout: /home/philip/ShaderLOD-old-delete, still intact on rtx5080-audit.
There is no ShaderLOD-next source directory. The existing T3 project path
/home/philip/ShaderLOD now resolves to the new checkout.

Active external data: /home/philip/.local/share/shaderlod. The old shaderlod-next
data path is a compatibility symlink, preserving immutable receipts without
duplicating storage. Current commands, Gradle project naming and guidance use
ShaderLOD/shaderlod. Historical records keep their original source identities.

Historical extraction, independent source recovery and isolated fresh-clone checks
are complete; see results/historical-evidence-extraction/result.md. Its evidence does
not require the old checkout or bulk history. All16 candidate cards match their
complete campaign tables; the legacy Mac61 program remains separate. Before rename,
both source checkouts were clean and no Java game client was running.

Small Git recovery bundles remain under the canonical data directory's evidence/
repository-cutover-fkco2lv0 and evidence/history-extraction-IWuDeKc0 directories.
Keep those, new benchmark evidence and prepared profiles. Long-term machine and
independent backup choices remain separate.

External-location audit found a relocation defect: the active user service
~/.config/systemd/user/shaderlod-display.service invokes
/home/philip/ShaderLOD/scripts/headless_display.py, which is absent from the new
checkout. The legacy helper remains in the retired checkout and preserved Git
history. Its in-memory running process does not establish restart readiness.
Repair the maintained display helper/service wiring before declaring runtime
relocation complete. The lid-inhibit service and shaderlod-display state also live
outside the checkout. Philip is considering repository-local ignored storage;
no storage redesign or shared machine configuration edit is authorized by that
discussion alone.

## Next
Relocation checks pass: regenerated launcher/argument files use the canonical path;
existing comparison receipt integrity and all historical evidence verify; the full
portable gate passes (88 tasks executed/11 reused; 104 Python tests executed). No
new game or native Windows run was needed for these path changes. These checks did
not exercise display-service restart; repair and verify that newly found dependency.
On Philip's deletion
request, confirm no new work or old-checkout processes, then remove
/home/philip/ShaderLOD-old-delete and the superseded bulk archive under
/home/philip/.local/share/shaderlod/evidence/history. Keep the compatibility data
symlink while immutable records use it. Remove this task after that cleanup.
