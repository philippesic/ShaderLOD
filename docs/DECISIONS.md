# Decisions

| Date | Decision | Basis | Status |
| --- | --- | --- | --- |
| 2026-10-03 | Start with NeoForge rather than Fabric | Explicit user direction | Accepted |
| 2026-10-03 | Iris required; Photon first shader target | User's architecture and setup request | Accepted |
| 2026-10-03 | DH optional to users and included in dev runtime now | Setup needs DH working; DH-specific LOD belongs to later MVP | Accepted |
| 2026-10-03 | Maintain explicit Photon patches instead of generic GLSL rewriting | User's proposed proof-first architecture | Accepted |
| 2026-10-03 | Use inherited agent models and repository-local roles/checkpoints | Reuse sibling workflow without overriding user settings | Setup choice |
| 2026-10-03 | Target Minecraft 1.21.1, NeoForge 21.1.253, Java 21 | User requested broad adoption first; current large NeoForge packs and cumulative Iris/Sodium/DH downloads support 1.21.1 | Accepted; see VERSION_SELECTION.md |
| 2026-10-03 | Pin stable Iris 1.8.12 + Sodium 0.6.13, DH 3.3.3, Photon v1.3b | Iris's declared Sodium dependency, artifact/API audit, and actual development runtime | Verified reduced Mac profile |
| 2026-10-03 | Start with opaque vanilla PCF sample LOD and a versioned mod uniform bridge | Pinned Photon has explicit adaptive PCF loops; GLSL variants and live debug/enable controls pass | Implemented; performance unmeasured |
| 2026-10-03 | Use a reduced Photon profile for the local Apple M3 Max smoke test | Full initial options exhausted texture units; disabling optional samplers lets Iris/Photon/DH render | Development profile only; standard/Ultra requires desktop GPU validation |
| 2026-10-04 | Aggressive preview is not an acceptable default quality/performance tradeoff | User sees missing distant-tree shadows/depth for only roughly0.5ms saved; far AO bypass and very sparse screen-space shadows sacrifice salient structure | Addressed by default lighting protection; broader proof in FULL_FRAME_PROOF.md |

| 2026-10-04 | Sustain agent-team performance development; add shader targets only after meaningful Photon gains | Explicit user grants broad implementation freedom, retains current NeoForge/MC target, and asks for optional performance-mod benchmarks | Active goal: sustained-performance |
| 2026-10-04 | Start with minimal Iris/Sodium versus ModernFix/FerriteCore/ImmediatelyFast; culling separate | Iris requires Sodium; additional optimizer gains must be separated from ShaderLOD gains | Pinned artifacts; runtime verification underway |
| 2026-10-04 | Use repeated run-level comparisons and provisional 15% meaningful-gain gate | Coordinator engineering criterion under user authorization; prior single-run variation prevents broad claims | Pending evidence, adjustable transparently |
| 2026-10-05 | General LOD and adjacent rendering work avoidance; reusable integration interfaces; Photon is the only currently supported shader; consistently use bounded native agent teams | Explicit user direction; supersedes additional-pack implementation work in the previous goal | Active: general-lod-50-percent |
| 2026-10-05 | Target 50% higher total rendering throughput with little discernible quality difference | Explicit user target; coordinator interprets faster as 1.5x throughput / 33.333% less frame time and records both metrics | Target unachieved; replaces provisional 15% gate |

Dependency artifacts are recorded in `dev/runtime.lock.json`. Verify this set
in the development client before calling it supported. Photon upstream does
not support the local Apple GPU; the client attempt must establish and record
the actual hardware result. The full MVP effect set and numerical
performance acceptance gate remain open until the first measured proof.

## Next experiment proposal (2026-10-04)

User is testing the core idea and prioritizes a substantial performance benefit.
Recommended sequence, not a newly approved goal or frozen numerical gate:

1. Attribute missing CPU/GPU cost: vanilla terrain layers, DH terrain/water,
   entities/translucency, frame submission, and existing shader passes. Identify
   GPU effect work versus geometry/bandwidth versus CPU limitation before adding
   more feature cuts. Existing GPU intervals include command-starvation gaps.
2. Test one realistic expensive preset/scene with useful enabled effects and
   identical settings for baseline/adaptive runs. Increasing both sides' settings
   is legitimate for testing the intended use case; artificially inflating only
   the baseline or choosing useless excess work is not evidence of value.
3. Compare original high, original lower quality, and high+adaptive LOD. The
   lower-quality control tests whether adaptive complexity adds value over a
   simple preset change. Target effects with multi-millisecond cost rather than
   collecting reductions in already-cheap effects. The Mac's reduced feature
   set does not establish performance for full desktop Photon.
4. A roughly15–20% GPU-render reduction is a suggested proof-of-concept target,
   subject to user choice and actual baseline budget. If the effect share cannot
   support that, report the ceiling. Extend into geometry/CPU work only after
   profiling establishes its relevance; that changes the project's scope.

Keep live comparison windows intact during analysis. No new experiment was
launched or performance threshold accepted solely by this proposal.


## General-purpose direction reaffirmed

User clarified that configurable support for a wide variety of shader packs is
the eventual product, and questioned sunlight-specific work. Core priorities are
a reusable Iris protocol, per-effect configuration and explicit, verified pack
adapters. Photon remains first. ShaderLOD has not changed sunlight update cadence
or shadow-map refresh frequency. CPU/frustum research is an optional secondary
path, justified by profiling but not the definition of the mod. Do not claim
configuration alone adds distance-aware code to unmodified shaders.

## LOD system scope clarified, 2026-10-05

The user explicitly wants the general, extensible LOD system rather than
shader-specific optimization. Shared distance/work policies, legal shading-rate
tiers, quality guards and explicit adapter contracts define the product. Photon
is only the first consumer used to prove that the system changes rendering work.
Set aside exact fetch-cache/projection tuning and other standalone Photon
optimizations. The existing experiments remain historical evidence, not accepted
product components. A planned standalone 4 × 4 prototype was stopped before
implementation; its reusable region guard may support the shared system.

Continue the existing 50% throughput goal without changing its target or creating
a replacement goal. Add the shared contracts and connect one already implemented
LOD consumer to them. Do not invent universal GLSL rewriting or claim that
registration/configuration alone adds LOD to an unmodified shader. Explicit
adapter compile/render, quality and repeated performance checks remain required.
