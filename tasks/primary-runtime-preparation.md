# Verify the primary native Windows runtime

## Goal
Prepare and verify the native Windows client/world for live optimization work on
the selected RTX 2080 Ti primary machine.

## Authorized
Philip selected RTX 2080 Ti as the default ShaderLOD development machine, with an
Asahi secondary checkout. Bounded development work follows AGENTS.md and runtime
ownership rules. RTX 5080 is not allocated. Source/T3 setup and global policy sync
are complete; this record retains the unfinished runtime portion of machine setup.

## Done when
The intended native Windows runtime, prepared profile/world and maintained commands
are verified on the primary machine, with matching bounded functional evidence.

## State
Both source checkouts use /home/philip/ShaderLOD and the established origin. RTX
2080 Ti is the active pinned T3 project; Asahi is an archived secondary registration.
The portable gate passes in WSL on the primary.

On 2026-11-12 the native Windows side was prepared and exercised
([F48](../FINDINGS.md), [setup](../docs/runtime.md#rtx-2080-ti-windows-setup)): a
virtual 2560×1440 display, a working copy at `C:\ShaderLOD` at commit 0d48e14 made
with `prepare-windows`, `freeze-windows-runtime` and `prepare-profile`, sessions
through `start.ps1`, an uncapped frame capture, the render-group profile, a
three-pair unchanged-shader comparison, lifecycle validation and clean closes. An
ad-hoc `run-benchmark-dh` slot showed that original Reimagined with Distant Horizons
links and renders there. Earlier native assets remain under `C:\ShaderLODBench`,
`C:\ShaderLODNext`, `C:\ShaderLODNextEvidence` and `C:\ShaderLODNextRuntime`; the
new copy still reads its JDK and libraries from `C:\ShaderLODBench`.

Not yet done: the working copy lives outside the checkout, which the storage rule
does not provide for; updating it is a manual file copy; the DH slot is not made by
a maintained command, because `prepare-windows` leaves DH out; and sensitivity rests
on three pairs in one process.

## Next
Decide with Philip whether `C:\ShaderLOD` is the accepted Windows home and whether
2560×1440 or 3840×2160 is the benchmark surface. Then give the copy step and the DH
slot maintained commands, and run a fresh-session unchanged-shader campaign to state
what size of effect the machine resolves. Check that the GPU is idle before each
run; it is shared with Philip's other projects. Do not repeat the known Asahi DH
failure without a new hypothesis.
