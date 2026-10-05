# User and agent lessons

Record only demonstrated coordination problems or corrected misunderstandings.
Use context, evidence, agent contributions, and a practical lesson. Keep the
record respectful; uncertainty and product disagreements are not confirmed
user errors.

## Choose the target ecosystem before inheriting build versions

On 2026-10-03, the agent initially adopted the neighboring mod's Minecraft
26.1.2 target because the runtime dependencies existed. The user then clarified
that broad adoption should determine the first target. Current pack releases
and version-download evidence supported NeoForge 1.21.1, so setup was migrated
before acceptance. Reuse build infrastructure independently of its version pins;
check the audience criterion before selecting dependencies. Compatibility alone
does not establish popularity. This was an agent assumption corrected by the
user, not a user mistake.

## Separate a visual preview from a performance sanity check

The first side-by-side scene demonstrated controls and quality zones, but its
sparse geometry, substantial sky, and Photon early-outs made it a weak PCF
performance workload. When the user reported equal/slightly lower LOD FPS,
the source audit found little medium-tier work to remove. Explain effect scope
and actual baseline work before presenting a performance comparison. Label the
right-hand client accurately: it retained ShaderLOD with LOD disabled after the
user requested restoring the previous windows; the mod-absent baseline was not
completed. Keep live windows available while investigating read-only.

## Prioritize visual value over the number of effects modified

On2026-10-04 the user observed missing shadows on distant trees and rejected
the aggressive profile's roughly0.5ms saving as poor value. The agent had noticed
flatter distant foliage in captures but emphasized implemented effects and timing
results without clearly flagging that visual cost. Far AO was bypassed despite
its whole pass costing only0.057ms in the earlier profile; screen-space shadows
also became extremely sparse. Preserve important low-frequency lighting cues,
use effect-specific importance criteria, and evaluate quality alongside time.
Passing compilation/runtime checks and measuring a gain do not establish a
worthwhile optimization or a shipping-quality preset.

## Verify effective and temporal shader inputs

During the sustained goal on 2026-10-04, the initial repeated runner compared
option-file bytes. Iris normalized those files by removing defaults and adding
comments, so the guard rejected a semantically unchanged baseline. The benchmark
worker corrected verification to read loaded Iris option values and retain raw
hashes as separate evidence. Preserve failed captures instead of silently
relabeling them as performance results.

Further source auditing by the shader worker found that fixed rain/day time do
not fully specify Photon clouds: Iris smooths wetness and Photon uses it for
weather humidity and wind. Fresh clear saves switched to rain can initialize
differently from reused rainy saves. Actual wetness was not logged in the first
36-run matrix, so the proposed explanation remains unverified in those captures.
Record effective options and relevant temporal uniforms, inspect initialization,
and verify steady inputs before claiming a matched cloud workload. Frame count
alone and a fixed warmup duration do not prove that every shader input settled.


## Restarting completed native workers

During the frustum replay handoff, the coordinator sent a CPU-window release with
send_message after the worker had completed its turn. That queued information but
did not start execution. Checking process/output state exposed the idle handoff;
followup_task started the authorized replay. Use followup_task for new execution
by completed workers, and send_message for steering an already-running worker.
This was a coordinator error, not a missing user authorization or user mistake.
