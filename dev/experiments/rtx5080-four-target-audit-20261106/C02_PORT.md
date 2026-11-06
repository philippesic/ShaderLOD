# Native bloom VRS coverage

BSL now passes native RTX5080 functional activation/restoration in both real
GL4.5 and4.6. Both Complementary targets also pass these functional checks. Performance and quality
validation remain unmeasured; context creation alone is not work proof.

The original BSL contract remains intact. Both Complementary originals also have
a separate composite4 bloom-only output, target3 rather than BSL target1. The
current native4K graph exports show the relevant targets at3840×2160 (BSL RGB8,
Complementary RGBA8). MakeUp composite has outputs0/1/6, while composite1 writes
full scene color after adding bloom. The existing single-output bloom scope cannot
coarsen those MakeUp passes without changing additional work.

The finite extension binds each original ZIP to its complete native384/398 map;
a different archive/profile, PBR map, option edit, missing field or foreign key
refuses admission. Source replay still uses the actual pinned Iris inputs and
must match the consumed ProgramSource and its sole declared output. Actual
transformed shader objects, successful link, renderer/pipeline/context/resources,
selected pack/options and physical FBO attachment are required. The original
shader archive and AA/options are unchanged. No generalized family-name bypass
or shader-name-only permission was added.

The runtime now selects the output certified during compilation, instead of
assuming target1 for every family. It also refuses coverage/sample-mask states
and per-primitive shading-rate selection that would undermine the viewport0
contract. Existing unsupported-context, failure, resize, exact-state restoration
and foreign-context/resource custody checks remain. Source fragments with sample
mask or other excluded side effects are refused. This remains a2×2 bloom quality
tradeoff, not image equivalence or default enablement.

The maintained runner admits both balanced orders for C02 and explicitly disables
the VRS request before its final native/MasterOff return. Per-window C02 controls
remain MasterOn with empty public requests and VRS Off. Their identity must not be
misreported as the ordinary public MasterOff controls. A separate functional
quality-motion check is now allowed for this exact route; other private protocols
remain excluded. Recording is never timing evidence.

Local validation:98 benchmark Python tests pass (2 Asahi platform skips);
14 relevant Java tests pass, including authentic original/profile cross-binding,
all required capability/function refusals with zero NV calls, failed-body/setup/
restoration, resize, prior foreign enable/image/palette preservation and context
custody. Development compilation passes. The original-baseline queue is now terminal/restored/released. Its5034 build
bindings were verified and5269 files preserved before native source replacement. Live checks will use both
real4.5 and4.6 requests; actual returned versions and capabilities govern admission.

The [NV extension specification](https://registry.khronos.org/OpenGL/extensions/NV/NV_shading_rate_image.txt)
requires desktop GL4.5 and the mixed-samples extension, defines indexed viewport
enables/palettes and a2×2 rate, and describes sample-state adjustments. These
requirements were checked against the existing backend rather than inferred from
the GPU model. Native driver behavior still needs live validation.


BSL native functional evidence: bsl-c02-gl46-check-r1 and
bsl-c02-gl45-check-r1 both complete VALID with full configuration/profile
restoration, release and zero clients. The NV extension/function requirements
are available. Each candidate owns one certified bloom program and advances
actual activated/restored scopes together; native controls advance neither.
The counters bracket functional work, not isolated timed-window invocation counts.
- 4.6.0 NVIDIA 617.42: candidate activated scopes 4366→6975, restored=6975, backend failed=False.
- 4.5.0 NVIDIA 617.42: candidate activated scopes 4500→7101, restored=7101, backend failed=False.

No performance, image-equivalence or quarter-fragment-invocation conclusion follows
from these short functional captures. Per-window controls retain MasterOn/VRSOff.


Both Complementary targets now also complete VALID/restored/reset/released in
real4.5 and4.6: reimagined-c02-gl46-check-r1/gl45 and
unbound-c02-gl46-check-r1/gl45. Exact native profiles/sole output3 are consumed,
required NV capabilities are present, qualified candidate scopes advance and
restore, and native controls advance neither. All six BSL/Complementary checks
remain functional evidence only; scored balanced comparisons and quality are
pending.
