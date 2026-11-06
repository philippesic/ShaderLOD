# Source and correctness checks before 5080 acquisition

These are preparation findings and hypotheses, not RTX5080 timing results.
No acquisition has started while Windows storage remains below reserve.

The canonical source tree has a new maintained `--gl-context` option. Decompiled
NeoForge1.21.1 `Window` requests GL3.2 and can reuse an early-created window.
The explicit context path changes only the major/minor GLFW hints and disables
that early-window reuse through backed-up profile preparation. Actual returned
version must meet the minimum; no capability strings are overridden. Native
selection remains unchanged, frozen qualification refuses overrides, and the
sampler bootstrap admits only enumerated requests with the maintained controller.

Local validation:92 benchmark Python tests (2 platform skips),55 launcher tests,
the Java context-policy and sampler-admission tests, and development compilation
pass. This is not a live Windows context proof; the first native startup/check
must verify the mixin, early-window behavior and actual GL version before timing.

## Required mechanism evidence and regression investigations

| Goal | Required observation / specific issue to investigate |
| --- | --- |
| G01 | Actual copy calls and physical source/destination bank lifetime, not only a selected option. Revisit Complementary identity-pass proof with actual native graphs; do not reuse stale Asahi physical identities. BSL's live water reader cannot be removed solely to fill a cell. |
| G02 | Actual omitted mip/pass work, all live later consumers and fallback overhead. Compare source-only/native-equivalent variants when broad adapter overhead could mask a small saving. |
| G03 | Actual linked CUTOUT submissions and qualifying draw progress. Resolve platform/API and optional-stack refusals with source evidence; a flag or post-sample source declaration is insufficient. |
| G04 | Verify a contributing translucent-material case, source-controlled fast path, and separate water/no-op cases. Honey discovery did not reproduce on Asahi. |
| G05 | Distinguish shader-side projected refinement from MakeUp/BSL nearest-water permission. Exact-window grants are transient, so setup-time zero alone is not inactivity. Diagnose actual query eligibility and guard/driver cost before attributing a slowdown. |
| G06 | Actual qualified half-AO producer/consumer progress on Complementary/BSL; exact attenuation/center reuse is a separate MakeUp implementation. Half-AO source retains native near/HUD/sky and low-confidence fallback; coverage may be too low to repay the extra producer. The source is not an unconditional native-plus-half mix: full native work is conditional. This is a hypothesis to measure, not an isolated cause. |
| G07 | Typed actual depth/sampler ownership and permission progress in the timed window. Driver/GLSL equivalence and per-draw guard overhead may matter; a LOD0 expression is not proof of fewer physical transactions. |
| G08 | Native cloud/lighting eligibility and retained shadow/atlas semantics. Keep shader LOD reuse distinct from the private native-bound variant; small Asahi differences were drift-confounded. |
| G09 | Native POM must actually compile and use real height variation. Keep RP_MODE1 no-POM fallback separate from RP_MODE3/PBR. Diagnose cache codegen and prefix coverage; a source cache whose controlling uniform is optimized away is not proof of executed reuse. |
| G10 | Count exact duplicate-coordinate opportunities and preserve native arithmetic/filtering. Include native-equivalent private controls; tiny source-query opportunities and discovery-only gains do not establish hardware savings. |
| C01 | Real residual occluded geometry, query mode, command/primitive suppression and query/driver overhead. Preserve ordering and per-frame ownership; zero CPU reads are expected for the GPU conditional path. |
| C02 | Observe actual native NV extensions/entry points in GL4.5+ where required. Exercise existing 2×2 bloom tradeoff and draw/restoration witness; determine applicability to other target bloom graphs without pretending that an unsupported backend or absent extension is zero gain. |
| N01 | Real consuming compaction/cache hits and source/DrawID safety, plus CPU/driver time and actual workload bottleneck. Removed command-list entries are not removed GL draws. Use natural terrain rather than only the authored gallery. |
| N02 | Prove an attachment/target has no live/history consumers. Consolidate shared MakeUp deletion work with G01, without additive credit. |
| N03 | Prove terminal-copy liveness including next-frame/history use. Consolidate overlapping G01/N02 work and keep independently executable mechanisms separate. |
| N04 | Observe the exact overwritten/zero-work domain, source control and native fallback. Distinguish possible input eligibility from executed guarded branches and saved queries. |

The Minecraft source calls `RenderSystem.limitDisplayFPS` only when its effective
limit is below260; this runner's260 setting selects the native unlimited branch.
Do not invent a260fps measurement ceiling or change this setting without checking
the actual effective limiter/focus/VSync state.

At native3840×2160 the existing lossless quality recorder remains below its
4096×2160 pixel limit, and three121-frame raw RGBA8 sequences occupy about11.22GiB
before small metadata overhead, below the12GiB quality report cap. Actual capacity
checks and zero-missed-slot rules still apply; this is no justification for
running with the host's current approximately1GiB free.

## Input setup inventory

Native Windows has an active console and existing PrismLauncher Java/game caches
and old ShaderLOD runs. They are protected; existing personal profiles are not
edited or reset. The p-stack native Python is available but lacks `psutil`, so the
benchmark needs its own dependency environment once capacity is available.
Original packs, native sidecars, canonical authored/natural saves and optional
PBR resources will be transferred/reused by exact identity through maintained
storage admission, not copied into a live or unverified personal instance.
