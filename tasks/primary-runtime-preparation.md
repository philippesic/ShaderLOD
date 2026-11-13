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
The shared favicon is tracked. The primary checkout has project-local Python 3.13.2
and pinned tools. Its full portable gate passed: 109 Python tests; 38 Gradle tasks
executed, 61 reused. Cold acquisition exposed missing dependency metadata checksums;
verified official Maven metadata was added without changing versions or existing pins.

These are WSL source/build checks, not native Windows GPU qualification. Earlier
native Windows controls passed at their observed 1024x768 surface; see the
[workflow result](../results/benchmark-workflows/result.md). The new source checkout
has not acquired a freshly validated native Windows client/world in this setup.
Asahi's original Reimagined with enabled DH remains sampler-limited; no-DH evidence
does not replace it. Every earlier RTX 2080 Ti window without an injected delay
sat at the 60 FPS frame limiter, and the GPU was about a third busy at 1024×768
([F48](../FINDINGS.md)), so that configuration cannot show a speedup.

## Next
Before the first live optimization run, inspect current native Windows assets and
ownership, prepare the intended slot through maintained benchmark commands, and run
bounded lifecycle validation. Set the prepared profile's frame limiter to Unlimited
with vsync off, choose a surface where the GPU is the bottleneck, and run an
unchanged-shader comparison to learn what size of effect the machine resolves. Keep
persistent project inputs/artifacts inside its
checkout and preserve useful originals during adoption. Reuse applicable historical
qualification with explicit limits; do not repeat the known Asahi DH failure without
a new hypothesis.
