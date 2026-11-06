# Native C01 core-profile state repair

The first GPU conditional functional check (makeup-c01-gpu-native-check-r1)
refused the depth-range/polygon-mode guard. It restored/released/reset completely
and is not a timing result. Source ownership and query creation had passed.

The old code required both elements of a two-slot polygon-mode array to be FILL.
The core specification has one polygon mode for both faces; compatibility has two.
A core driver may write just one element, leaving the second untouched. The new
profile-aware guard reads the actual context profile mask, keeps a two-slot safe
buffer, and checks one core enum or both compatibility enums. Unknown/ambiguous
profiles, non-FILL modes, nonstandard depth ranges and incomplete query output
still refuse. Sentinels prevent stale values from satisfying a failed read.
Last observed raster values are exported without extra status-time GL queries.

This is an API correctness repair, not permission to omit geometry or weaken the
existing source, bounds, current-frame depth and conservative query contracts.
The fresh native retry confirms the cause: profile mask1 (core), depth range
[0,1], polygon array[6914,0] (FILL plus untouched second slot). It exercises
27172 proxy queries and33965 deferred regions with no refusal and zero CPU
result reads in its functional candidate bracket. These are bracketed work
observations, not exact timed suppression counts or a performance result.

The [OpenGL4.6 core state table](https://registry.khronos.org/OpenGL/specs/gl/glspec46.core.pdf#page=614)
defines the single polygon mode.22 relevant C01 Java tests and development
compilation pass on Asahi and Windows. The Windows test cache initially lacked
JUnit; a normal dependency download into the owned Gradle cache resolved it.

Before native source replacement, all5034 build bindings plus scripts/tests/
definitions were verified and preserved; the ZIP is also hash-verified on Asahi.
Only the two C01 source/test files changed on native, with before/after SHA checks.
Active follow-up is the V3 queue: the repaired C01 GPU check, separate untimed primitive
witness, remaining Complementary4.5/4.6 VRS checks and repeated original GL4.6
baselines. No C01 performance or quality conclusion yet.


Both the repaired GPU check and separate primitive diagnostic finish VALID with
configuration restored, whole-profile reset verified, exact runtime release and
zero clients. The32-frame witness sees1124032 expected native primitives and
zero suppressed native primitives in each GPU no-wait, GPU wait and CPU-decision
mode. Each mode issues128 proxy queries; GPU modes read zero CPU results, CPU mode
reads128. This bounded gallery scene provides no occlusion saving even when query
results are waited/read. It is an opportunity limitation, not evidence of a broken
GPU conditional path or general impossibility. Natural search and timings remain
separate pending cases.
