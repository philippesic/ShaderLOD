# Finite Bliss renderer benchmark storage

The next-attempt entry point is `scripts/renderer_average_storage.py`. It adapts
exactly the current original/candidate/original-return Bliss renderer recipe at
`dev/logs/speed-replication-r1/renderer-averages/run_bliss_renderer.py`, whose SHA256
is `162f479eecfdd357b8e7d8d9b90dc694dc9b28634446a15510e609ee730ef340`.
Historical scripts and existing clones are never edited or adopted for deletion.
This is storage maintenance, not new rendering or performance evidence.

Input → preparation → consumption → release:

1. Read the three saved plans/freezes/options/control/core inputs from
   `dev/logs/speed-replication-r1/renderer-averages/bliss-renderer-r2` and use the
   canonical `run-workloads/complementary-shadow-sequence-r11/original/client/saves/ShaderLOD-Landscape`.
2. Before copies, inspect capacity on each **destination** filesystem. First
   reject an inadequate 512 MiB report reserve without source traversal; then
   count actual world/config/mod/options/pack bytes with at most 100,000 metadata
   entries per tree. Shared destination devices reserve 1 GiB plus all three
   copies. No APFS clone savings are assumed. This is conservative admission,
   not a guarantee against concurrent disk use or later capture growth.
3. `prepare` holds the existing client lane lock and requires no clients; checks
   the actual canonical world and original frozen files/build; copies only fresh
   profile inputs; writes new plans/freezes and `benchmark-storage.json`. No
   existing profile/report root is overwritten. Copies use portable copytree,
   not APFS reflinks, so the admission does not depend on reclaim estimates.
4. `run` consumes the same frozen scientific runner and its existing finite
   dev-client supervisor, quiet/CANCEL checks, original/candidate/return ordering,
   quality/measurement validators and failure retention. Only the root/report
   location and exact `shutil.rmtree(world)` call are adapted. A changed frozen
   runner hash refuses the adapter and requires a new review.
5. World clones are **preserved by default**. `run --cleanup-world-clones`
   authorizes only this newly manifested run. After each successful arm, export
   its release/world map and keep owner/console/measurement/still evidence.
   Reacquire and hold the lane lock; require recorded child and launcher PIDs
   absent, no Minecraft clients or launcher process group, and an obtainable
   world session lock. Check manifest owner/root/clone filesystem identities,
   symlinks, containment, canonical overlap and evidence existence. Record exact
   clone paths/logical bytes and retained evidence hashes **before deletion**.
   Delete only the current `client/saves/ShaderLOD-Landscape`; retain configs,
   source, canonical world, reports, images, captures, failures and logs. Any
   failed guard retains the clone and closes the finite attempt without retry.

There is no standalone cleanup, discovery of older runs, age threshold or
“clean all” action. Separate human authorization for older cleanup does not grant
this adapter authority over those clones. A missing manifest/release/ownership
proof cannot be repaired by guessing. No general source/build task uses this
capacity gate.

Read-only inventory (choose fresh, unused destinations):

```sh
python3 scripts/renderer_average_storage.py inventory \
  --template dev/logs/speed-replication-r1/renderer-averages/bliss-renderer-r2 \
  --world run-workloads/complementary-shadow-sequence-r11/original/client/saves/ShaderLOD-Landscape \
  --profiles run-workloads/speed-replication-next/bliss-renderer-averages \
  --output dev/logs/speed-replication-r1/renderer-averages/bliss-renderer-next
```

The parent must choose the actual next authorized scientific attempt; this guide
does not authorize repeating a closed trial. For that attempt, use the same argv
with action `prepare`, then action `run` (world/profiles omitted). The existing
`quiet.json` and `CANCEL` in the report parent continue to govern the runtime
lane. `--cleanup-world-clones` is optional on `run` only; omitting it preserves
clones. This does not replace the existing supervised stop procedure.

Verification: tiny stdlib fixtures exercise disk API admission, all cleanup
refusals and real temporary-directory deletion. Read-only inventory of the
current authentic Bliss inputs and compilation of the SHA-pinned adapted runner
exercise the real input/consumer boundary. They do not prove Java launch,
rendering, timing, APFS behavior, or native runtime cleanup.
