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

On 2026-11-13 Philip accepted `C:\ShaderLOD` as the Windows home and chose
3840×2160 as the benchmark surface with 2560×1440 as a secondary check. The code
now has `sync-windows` (refresh the copy from a clean WSL checkout),
`prepare-windows-dh` (a maintained DH slot) and session-selected fullscreen modes;
the RTX 2080 Ti definitions name 3840×2160, with `rtx2080ti-null-1440p.json` and
`validation-rtx2080ti-dh.json` added, and a six-session no-change campaign
`rtx2080ti-null-sessions.json`. None of this has run on the machine yet: the GPU
was handed to Philip's vLLM work first.

## Next
When the 2080 Ti is free: sync the copy, replace the ad-hoc `run-benchmark-dh`
with `prepare-windows-dh` plus `prepare-profile`, validate both slots at
3840×2160, then run `rtx2080ti-null-sessions.json` and the 1440p check and record
what size of effect the machine resolves. Check that the GPU is idle before each
run; it is shared with Philip's other projects. Do not repeat the known Asahi DH
failure without a new hypothesis.
