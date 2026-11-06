# Native bloom VRS coverage

This source/harness extension is prepared and locally tested. Native RTX5080
activation, quality and performance validation remain pending; an actual4.5/4.6
context is separate from observing the required NV capabilities and consuming draw.

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
custody. Development compilation passes. The Windows source copy remains frozen
until its active original-baseline queue is terminal. Live checks will use both
real4.5 and4.6 requests; actual returned versions and capabilities govern admission.

The [NV extension specification](https://registry.khronos.org/OpenGL/extensions/NV/NV_shading_rate_image.txt)
requires desktop GL4.5 and the mixed-samples extension, defines indexed viewport
enables/palettes and a2×2 rate, and describes sample-state adjustments. These
requirements were checked against the existing backend rather than inferred from
the GPU model. Native driver behavior still needs live validation.
