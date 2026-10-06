# Explicit Photon consumer of the shared spatial kernel

2026-10-05. Bounded source freeze, **not runtime or performance acceptance**.
Photon v1.3b is the pinned input adapter; the general mechanism remains in the
unchanged common `work_policy`, `spatial_plan`, `surface_region_continuity` and
`spatial_pipeline` helpers. The pack supplies original buffer decoding, eligible
material factorization and original lighting callbacks. It contains no second
spatial planner, region classifier or reconstruction algorithm. The frozen old
2 × 2 prototype, production volume consumer, Java/runtime and upstream ZIP are
unchanged by this contribution.

Source: [builder](../scripts/build_photon_shared_spatial.py),
[manifest](../patches/photon-experiments/shared-spatial-lod.json),
[callback](../shaderpacks/photon-shared-spatial/shaders/include/shaderlod/photon_spatial_adapter.glsl),
[fixtures](../tests/test_photon_shared_spatial.py).
Photon's original attribution and LICENSE are preserved; original copied sky and
lighting bodies name SixthSurge. Common helper copies match their declared source
hashes exactly and preserve existing notices.

The upstream ZIP SHA-256 is
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Manifest SHA-256 is
`bd9fbf3f70bb117a1825f630cc3a20e34089bd7bf9f8dd14377e2cc72ac582fa`.
The final deterministic containers are:

| Arm | SHA-256 |
| --- | --- |
| Original source, metadata only | `c4e956dfee9ceebb6df245ea4792f5626cbc2d0ff995ccf9b49766d2286b57f1` |
| Private OFF | `a8c004f5be0b341ed117220da0d2e2a0dadeefd62fd899ff64a3100c19a54ceb` |
| Private ON | `c2790b61bd4d096e24eedbf9ca800d35541ca81e8ec5efeecec122b764dbf6f0` |

`build/photon-shared-spatial-freeze.json` contains the complete registration
handoff for each arm, with 25 consuming member pins in the derivative,
protocol requirements, source container digest, exact required Iris options,
operation `shaderlod:opaque_spatial_irradiance` (opaque lighting, RATE), legal
fractions `{1, 0.25}` and export `shaderlodOpaqueSpatialAvailable`. This is a
registration declaration; root must install it through the general extension
API and validate the actual layout before advertising availability. No Java
reference registration or runtime connection has been performed here.

## Consuming graph and policy

Entry points are `world0/deferred4.fsh` →
`program/shaderlod_spatial_prepass.fsh`, followed by
`world0/deferred5.fsh` → original `program/d4_deferred_shading.fsh` with the
explicit adapter callback. Vertex sources retain original deferred4 semantics.
The numbered graph is also allocated in `world1` and `world-1`, where the
producer copies original input0 and writes invalid cache, and the consumer
executes the original shader. There is no distance optimization in those worlds.

`SHADERLOD_SHARED_SPATIAL` defaults OFF. The initial reviewed graph requires
normal/specular maps, colored/VPS shadows, colored lights, cloud shadows, SH
skylight, TAAU, blocky clouds, environment/sky reflections, galaxy and border fog
OFF, and no DH/VOXY projection. These are explicit eligibility limits; their
unsupported configurations keep the original graph. No upstream option is
silently changed. Native forest's full effective map is pinned by its report
SHA-256 `6f7662a31427f0d0863d926909c67a57b0dfba3c7ee2539c857f699c7ce3da5e`
and canonical map digest
`4efc7af7742bc445adf2b3b1454aa448a557e1d4b024103dd5b9f5758ed68745`.
These are source fixture inputs, not a new rendered run.

The shader requires core2/global1/effects2/capabilities1/availability1 and calls
`shaderlod_spatial_plan_decide` with rate mask3. INHERIT is deliberately unresolved
and FULL retains original work. ADAPTIVE consumes the five original opaque band
and ratio values plus mode; endpoints must be exactly1 or0.25. Unsupported ratios,
protocols, fields, source/options, quality, receiver or cache retain full work.
Distances are radial Euclidean camera-relative view-space Minecraft **blocks**;
minimum distance includes all four receivers. The protected distance is the
centered near-band start, clamped to at least0.001blocks. All classification,
tier decisions, certificate validation and final linear reconstruction delegate
to the shared kernel.

Full↔quarter uses the common centered convex transition. A full basis evaluation
and one quarter representative can both execute in the band; requested retention
is not a physical work cap. Phase is fixed tile corner `(0,0)`, with no history,
rotating phase, density/noise tuning, changed shadow samples or preset selection.

## Linear basis, full-resolution work and limitations

Quantity key1 declares canonical-white original diffuse RGB coefficient in scene
linear lighting units, plus normalized AO × shadow highlight visibility in alpha.
The eligible material is mask0, nonmetal, zero emission/SSS/sheen/porosity and
nonreflective under the declared material guard. Original diffuse's final albedo
multiplication can be factored with these nonlinear terms excluded; the adapter
sets canonical white **after** original material decoding, without dividing
final shaded RGB by albedo. Full recipient albedo/material decoding, specular
highlight BRDF, edge highlight, fog, clouds in front of terrain, rainbow and final
composition remain original full-resolution operations.

The kernel applies the same convex weights to the declared four coefficient
components, then the recipient multiplies RGB by its own material albedo and
uses alpha as highlight visibility. It does not cache final terrain RGB. The
original AO reconstruction union is inspected before expensive evaluation,
rejecting invalid taps and AO spread above0.10. Region classification covers
all members, material/normal/lightmap/plane continuity, near and protection.
Neither this guard nor the certificate proves cast-shadow continuity. Diffuse
shadow/AO/bent-normal lighting and highlight visibility are spatial approximations;
thin cast/contact shadows and moving boundaries can still change. No still or
motion quality evidence has been accepted.

Shadow sampling in the divergent evaluation callback uses the reviewed explicit
LOD0 forms; original shadow count/filter math and SSRT body are unchanged. The
actual no-mip/filter/compare sampler state still needs runtime proof. Surface and
cache callbacks use integer texelFetch. Sky's exact original atmosphere/cloud
prelude remains **before any depth/tile divergence** in producer main, preserving
its implicit-sampling scope; only the original sky branch is placed in its helper.
Sky is evaluated at full resolution independently of mod policy and cache readiness.

## Storage, pass groups and feedback

The producer has a full surface viewport and MRT logical0/2. Logical2 alone is
changed to full-size RGBA32F; no dimensions or formats of other upstream targets
change. In each2 × 2 tile, `(0,0)` stores the quantity and `(1,0)` the certificate;
the other two texels explicitly store invalid. Metadata classification repeats
shared all-member preparation/plan, with no expensive lighting evaluation. The
quantity lane calls `shaderlod_spatial_produce` and evaluates at most once.
Every complete or partial viewport texel is written each frame. Odd-edge tiles
are rejected by the shared kernel before any out-of-bounds surface/cache fetch.
The full viewport and duplicated classification are real overhead, not savings.

The certificate texel is `(epoch,258,tile_index,minimum_distance_blocks)`:

- Epoch is current frameCounter, guarded to integer `[0,2^24]`.
- Token258 represents exactly key1/side2; other tokens are rejected.
- Tile index is `x + ceil(render_width/2) * y`, in `[0,2^24-1]` for dimensions
  `[2,8192]` per axis. It reconstructs exact x/y with integer remainder/division.
- Minimum distance is stored directly in binary32, without half-float integers,
  bit reinterpretation or filtering.

All integer values in the bounded domain are exactly representable in binary32.
Consumer fetch is nearest integer texelFetch, and actual target dimensions must
match rendered dimensions. Quantity alpha has valid domain `[0,1]` and sentinel
`-1` for failed evaluation. Thus metadata-valid/quantity-failed lanes cannot
silently turn failed lighting into zero irradiance. Shared validation additionally
rejects nonfinite quantities, wrong key/epoch/side/tile/distance and incomplete
regions. Source/options/policy/surface snapshots must be immutable across draws.
Epoch reset/wrap cannot authorize stale entries because the entire producer image
is overwritten each frame before consumption; resize/reload/pass ordering still
need actual driver evidence.

Logical0 retains original `R11F_G11F_B10F`, three-channel GENERAL scene RGB, not
metadata or a spatial basis. The producer preserves its original skytextured
input and writes full-resolution original sky; the consumer reads completed sky0
and draws logical0/3 under actual Minecraft12101. Logical3 retains RGBA8, four-channel
GENERAL overlay-clear output. Original finite sky RGB is encoded in the original
format; texelFetch and re-encoding a representable finite value preserve that
representation. This is a storage argument, not a rendered parity result.

The [pure layout API](LOD_LAYOUT_CONTRACT.md) declares full-size/full-viewport
producer0/2 and consumer0/3 groups, atlas2's LINEAR_QUANTITY4,
EXACT_BINARY32_BOUNDED_INTEGER_METADATA3 (maximum2^24) and
BINARY32_MINIMUM_DISTANCE1 views, and FLOAT_TEXEL_FETCH reads. Logical0 overlap
requires DISTINCT_IMAGES_EXTERNALLY_VERIFIED, with distinct main/alternate physical
images. Read-only pinned Iris bytecode shows sampler selection reads the snapshot
side, `createColorFramebuffer` attaches the opposite side, and each written target
flips unless explicitly false. Private properties add no deferred4/5 false flips
for0/2. This establishes the reviewed source routing; actual GL attachment/texture
IDs, producer completion and subsequent sampler bindings remain runtime obligations.

## Checks and remaining acceptance

`python3 -m unittest discover -s tests -p test_photon_shared_spatial.py -v` passed
**5tests**, zero failures/skips, in41.496s. Raw acceptance:
`build/photon-shared-spatial-source-freeze-final.log`. It covers deterministic
three-arm assembly/scope/licenses/shared hashes; source factorization/kernel
calls; actual callback codec + actual kernel C++ host execution (split-lane failure,
nonfinite/wrong certificates, transition and recipient albedo); literal sky
prelude/body extraction and pinned renderer bytecode; actual Iris option discovery,
Jcpp, grammar, directives and all upstream graphics groups.

The complete fixture parsed1008 Iris translation units, checked332 original/privateOFF
token streams, and validated1352 upstream fragment-group/profile layouts across
all three worlds/cloud scales1–4/DH on/off. Thirteen unsupported option controls
plus DH retain original producer-stage tokens. A malformed active callback is
rejected. Two complete real Photon producer/consumer translation units pass offline
GLSL semantic compilation. Actual sky prelude/body hashes are recorded in the
manifest, and their literal source extraction is tested.

Pinned Iris outer JAR is `3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`;
embedded transformer `b8ca5e61b41ae18ddd5d4d5d6785cb9acd8fd7ac0c5c0b0689b872b039c11b9f`;
ANTLR `54665d2838cc66458343468efc539e454fc95b46a8a04b13c6ac43fc9be63505`.
Final builder/fixture hashes are
`b7f95ecae35d30d4c3ac159b2c548764799a4bb7d04dcd84501e578da7a1c844` /
`8edad38527b169dc56804529d8f76a49ffa69815f418f67073ccd479a353123c`.

Offline glslang reflection in
`build/photon-shared-spatial-producer-reflection.txt` and
`build/photon-shared-spatial-consumer-reflection.txt` reports **11/12 active
sampler names** under the exact held map. Inspected Iris composite reserves units1/2,
leaving14 dynamic slots on the recorded16-unit Apple context. This is source
headroom evidence, not an actual GL link/allocation proof.

Initial harness failures and the sampler qualification investigation remain in
`build/photon-shared-spatial-tests*.log` and
`build/photon-shared-spatial-default-*-reflection.txt`. In particular, the effective
map reports USE_SEPARATE_ENTITY_DRAWS=true, but pinned source defines that macro
only for MC_VERSION≥260100, so it is inactive under12101. An external forced macro
was removed; no version or upstream feature was changed. A partly selected
pre-decomposition source reflected15 consumer samplers. The final graph preserves
the original computed-macro behavior and original sky operation, with source
headroom11/12. An intermediate sky-only helper also moved implicit sampling under
a new branch; source review repaired it by retaining the unconditional original
prelude before the final acceptance run.

Private OFF proves original source tokens and original graph/format; new deferred5
is disabled. Private ON/global OFF, FULL, INHERIT or invalid policy retains full
lighting but still incurs the extra producer/pass/layout overhead and reviewed
sampling forms. It is not a zero-overhead original baseline.

Root owns registration/build integration and the idle GPU/client. No Minecraft,
shader reload, Gradle/production/dev class build or GPU run occurred here; no owned
live process remains. Before runtime acceptance, check typed controls/capability
at actual consuming stages; original/OFF image parity; producer0/2 and consumer0/3
physical attachments/viewport/formats; opposite0 texture IDs and cache2 completion;
all active sampler units/targets/filter/compare/LOD state; representative valid and
invalid outputs; changing settings/source/world/dimensions/epoch and odd edges;
near/far/transition motion, albedo detail, foliage, thin shadows, water exclusion,
reflections and optional-DH original fallback. Then use repeated matched original,
privateOFF and active full-frame GPU and total-frame timings in quiet workloads,
including full viewport, sky staging, classification, cache traffic, fallback and
transition costs. No performance claim or project-goal completion follows from
these source checks or nominal one-in-four evaluation rate.
