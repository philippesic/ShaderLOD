# Conservative normal-error budget helper

2026-10-05. Source owner `procedural_lod`; bounded mathematical review by
`procedural_lod/parallax_math`. New reusable helper only:
`shaderpacks/common/shaders/include/shaderlod/normal_error_budget.glsl`, scalar/
parser fixtures in `tests/test_normal_error_budget.py`. The frozen procedural
adapter, its builder, manifests, pack options and config transport were not edited.
This helper has **no shader entry-point consumer yet**. It establishes a conservative
quality constraint, not runtime acceptance or a performance gain.

The precise current limit is useful: at Photon’s ordinary three octaves, normalized
amplitudes and normal influence0.04, a0.5° universal normal budget permits about7.20%
attenuation of the last octave but **cannot omit an entire octave**. Partial
attenuation still evaluates that octave’s noise/trigonometry. Do not count it as
work avoidance.

## General policy and units

For a normal reconstructed as `normalize(vec3(height_differences * influence, h))`,
keep the original positive third component `h` and original full-count amplitude
normalization. If omitted/attenuated work introduces a raw XY vector error radius
at mostB, the normalized angular difference is bounded by

\[
\theta \le 2\arctan\!\left(\frac{B}{2h}\right),\qquad
B_{\mathrm{allowed}}=2h\tan(\theta_{\mathrm{allowed}}/2).
\]

`h` and raw-error radius have the same reconstruction-vector units. Height bounds
are in the caller’s height units; derivative bounds are height/coordinate unit.
Angles passed/returned by the helper are **radians**. No camera-distance units,
sampler state or shader pack are implied by the common helper.

Rotate the displacement onto one XY axis, placing the two raw vectors at
`(a-d,b,h)` and `(a+d,b,h)` with2d=displacement length. Their angle is
`atan2(2d*sqrt(b*b+h*h), a*a+b*b+h*h-d*d)`. It is maximal at a=b=0; equality occurs
at opposite XY slopes±d. This is an exact real-arithmetic bound, not a small-angle
linear approximation. Counterexample/equality fixtures independently evaluate
angles using cross/dot `atan2`.

The helper provides:

- `shaderlod_normal_height_error_bound`: componentwise finite-difference error for
  one centered bounded wave replaced by its mean. Invalid inputs returnnegative
  sentinel bounds, requiring full original work.
- `shaderlod_normal_error_radius`: convert componentwise height bounds into a raw
  XY error radius with the original influence. Invalid/overflowed input returns−1.
- `shaderlod_normal_error_budget`: convert an angular tolerance into a raw budget.
  Disabled, near/full, unstable/salient, nonfinite/singular inputs returnzero budget.
- `shaderlod_normal_removal_cap`: cap a requested0..1 omission fraction using the
  remaining budget and already accepted error radii. Zero budget retains full work.
- `shaderlod_normal_angle_bound`: diagnostic angular certificate; invalid/singular
  input receivesπ, the maximum angular bound.

No function evaluates an omitted octave, samples a texture or computes derivatives.
An adapter allocates from smallest-error/highest-frequency octaves, updates the
sum of accepted radius×removal terms, and skips a sample/trig kernel only when a
complete omission is allowed. The first structural octave remains protected by
adapter policy. Proposed fractions must remain **constant across the three height
samples of a reconstructed normal**. Spatially changing the fraction inside those
samples introduces another product-difference term absent from this bound.

Near/full fallback is an explicit boolean resolved by the adapter before work;
this helper does not add a distance policy or consume mod config. Grazing/unstable/
salient risk is also a caller responsibility. The supplied absolute flat-view
cosine is protected through`grazing_floor`; allowable error smoothly increases
between that floor and twice the floor. At floor0.15, all views≤0.15 retain original
work and views≥0.30 use the full angular budget. This avoids seeking work savings
from precisely the grazing region where parallax/reflection mappings can become
unstable. Invalid cosine/floor also keeps full work.

Two explicit1/4096 safety margins cover ordinary error accumulation/allocation
rounding in the host binary32 fixtures. Real-arithmetic formulas plus these fixtures
are not a formal guarantee of every GLSL implementation’s floating-point rounding.
Driver execution remains unverified. Maximum allowed angle inputs are strictly
between0 and3radians to avoid the tangent singularity; this mathematical domain
check does not endorse a large perceptual tolerance.

## Pinned Photon amplitude and slope bounds

Reference source is Photonv1.3b ZIP SHA256
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
`shaders/include/surface/water_normal.glsl` retains SixthSurge/Belmu attribution;
this new common helper copies no Photon GLSL implementation or texture asset.
Pinned settings use three octaves and height variation; original finite-difference
step is0.1blocks in procedural tangent coordinates. Existing runtime controls
remain independent from these source pins.

With persistence p=0.5×WATER_WAVE_PERSISTENCE and N original octaves,

\[
A_i=p^i\frac{1-p}{1-p^N},\quad i=0..N-1.
\]

Require finite nonnegative amplitudes and0≤p<1. Photon’s p=1 case has a singular
original normalization. The helper does not replace that baseline with a repaired
normalization or invent parity. Unsupported/singular sources retain original work.

Photon waveG=(0.5sinφ+0.5)² lies in[0,1], with uniform-phase mean3/8 and exact
maximum derivative|dG/dφ|=3√3/8. For a fixed omitted fractionr and nonnegative
height modulationV, the omitted error field is

\[
E_i(q)=A_i r\,V(q)(G_i(q)-3/8).
\]

Pinned normalized LINEAR/repeat512² noisetex gives a per-axis UV slope bound512.
The pinned PNG/mcmeta/source state is verified by existing source/runtime records;
this helper itself performs no sampler readback. For coordinate-axisj,

\[
L_{G_i,j}=\frac{3\sqrt3}{8}\frac{2\pi}{\lambda_i}
  \left(f_i|d_{i,j}|+2n_i\,512\right),
\]

where f starts0.7×WATER_WAVE_FREQUENCY and grows by1.7×WATER_WAVE_LACUNARITY,
λ starts1 and grows by1.5, and noise frequency n starts0.007 and grows by2.5.
Flow direction/rotation must match the actual primitive; no assumed unit direction
replaces the caller’s actual coefficients. Repeat seams remain continuous bilinear
pieces and satisfy the same Lipschitz bound.

Height variation is`max(0.4, 2noise-0.5)`, giving Vmin0.4/Vmax1.5 and per-axis
L_V=2×0.001×512=1.024. Without variation, V=1 and L_V=0. For a finite-difference
coordinate step h, one component is bounded by

\[
|\Delta E_{i,j}|\le A_i r\min\left[
 V_{\max},\;
 V_{\max}\min(1,hL_{G_i,j})+
 \frac58\min(V_{\max}-V_{\min},hL_{V,j})
\right].
\]

The first term follows from the range of the signed centered wave times
nonnegativeV. The second follows by splitting the product difference as
V₁(G₁−G₀)+(V₁−V₀)(G₀−3/8). Sum nonnegative octave bounds, multiply by absolute
normal influence, then form an XY radius. Radius sums are a valid triangle bound;
an adapter may retain componentwise sums for a tighter bound with separate proof.

At the current512 texture and ordinary parameters, even the first octave’s
hL_G exceeds1, so the tighter slope expression saturates. The per-octave height
error bound becomes1.5A_i on each axis. Using guessed smaller noise slopes would
allow more skipping but would invalidate the quality certificate.

## Exact usefulness limit

The following uses an illustrative0.5° tangent-normal tolerance, nongrazing views,
p=0.5, h=0.1, original amplitudes and proposed complete omission of the highest
frequency octaves. This is a quality test threshold, **not an accepted product
default**, and no actual adapter consumes it.

For N3/influence0.04, the third octave has A₂=1/7. Its full omission gives raw
B=0.012121830534626527 and angular bound6.936811545849245°. The0.5° raw limit is
0.0008726701641403134. Ideal allowed fraction is0.0719916155936592; implemented
headroom allows0.0719564677286423, still requiring the entire expensive evaluation.
Complete omission requires influence≤0.002879664623746368 before safety margins.
Grazing damping may mathematically lower influence that far, but the explicit
protection policy retains original work in that risky region.

| Original octaves | Influence | Height variation | Complete protected-fine skips at0.5° |
| ---: | ---: | :---: | ---: |
| 3 | 0.04 | Yes | 0 |
| 8 | 0.04 | Yes | 1 |
| 8 | 0.10 flowing | Yes | 1 |
| 8 | 0.19 bright/rain | Yes | 0 |
| 8 | 0.38 doubled strength | Yes | 0 |
| 8 | 0.04 | No | 2 |

These are conservative bounds on source work candidates, not executed GPU savings.
Eight-octave support offers some bound-compatible complete omissions; current
ordinary three-octave Photon does not. Another shader with genuinely smaller
amplitudes/modulation or a proved lower slope bound may have more headroom through
the same helper, but no other shader support is implemented here.

## Cross-check of earlier angle fixtures and actual source domain

The old22.115413607216066° maximum is reproduced jointly, with every removed
fine octave accounted for. Its case isN3, coord(17.3,−4.7), flowing direction(0.6,0.8),
influence0.38 and time0. The joint bound is119.86536929888378° using raw error radius
0.34547217023685606. Full-N normalization, h0.1, unchanged finite-difference
reconstruction and height variation all match the original/filtered scalar equations.
This validates the generic mathematical theorem for that stress input.

It does **not** establish a reachable Photon setting: flowing Overworld influence
is0.1×WATER_WAVE_STRENGTH×grazing suppression, capped0.2 by supported strength≤2.
The previous fixture combined flowing=true with0.38, an over-range stress coefficient.
An actual-source certificate rejects that coefficient/domain mismatch; it must not
inherit source-equivalence status from a valid generic angular inequality.

A separate oracle derives actual influence from the pinned branches:
Overworld flow0.1; otherwise mix(0.01,0.04+0.15rain,dampen(skylight)), with
`dampen(x)=clamp(x)*(2-clamp(x))`; other dimensions0.04; finally multiply original
strength and smoothstep(0,0.15,absolute flat-view cosine). Original and filtered
coefficient source regions compare equal after stripping only whitespace/comments.
Unsupported/nonfinite contexts or a supplied coefficient differing from the derived
one receive the maximalπ certificate. The common helper remains pack independent;
an eventual adapter must supply these validated actual coefficients itself.

Expanded source-domain fixtures cover sky/rain0/0.5/1, strength0/1/2, grazing/noon
view cosines, all three dimensions’ branch shapes, 1/3/8 octaves and the earlier
coordinate/flow/time samples. Maximum sampled changes are0°/12.143127667783164°/
12.968803156563101° for N1/N3/N8. With default strength1/rain0/sky1/nongrazing
coefficients, sampled maxima are0°/6.151089991832627°/4.470371684286197°.
The expanded global maximum is an eight-octave still-water/rain1/strength2 case;
its joint bound is127.03788450135553°. Every sample passes its joint bound.
These finite scalar samples are not runtime images, perceptual tolerances or
absolute worst-case proofs. Quality remains unaccepted.

The certificate assumes the **same** coordinates and underlying sampler function
for original and filtered heights. It does not account for an additional change
in full parallax coordinates, GPU compiler arithmetic, texture LOD/contents or TBN.
Those require separate source/driver evidence and a proved added error allowance;
scalar full-parallax agreement alone cannot establish GPU bit equivalence.

## Static acceptance and limitations

Executed`python3 -m unittest discover -s tests -p test_normal_error_budget.py -v`:
**14 tests pass, no skips**. Checks cover angular equality and counterexample grids,
scale invariance, near/full/disabled/salient/grazing/singular/invalid fallbacks,
1000×8 host binary32 allocations, exact carrier derivative extremum, product range
bound, pinned actual noise/flow/height variation, source parameters and the stated
three/eight-octave limits. The sampled actual-noise component-error/bound ratio
peaks0.46500789842159024; this is a fixture observation, not the source of the bound.

Actual pinned Iris1.8.12-local `OptionAnnotatedSource` proves no common-helper option
or reference leaks. The Iris-bundled glsl-transformer2.0.1/ANTLR parser parses the
new helper’s full GLSL4.10 syntax. This is a **syntax parser**, not Iris whole-program
transform acceptance, a GL compiler, or a rendered test. A corrected procedural
adapter’s separate runtime parser failure is owned by another worker; no frozen
adapter file was edited in this task.

Tangent-space angular preservation transfers to world space only with an
orthonormal TBN (or another proved transform bound). Unknown/scaled/sheared TBN,
invalid displaced-coordinate footprints, nonlinear reconstruction, differing
finite-difference steps, changed amplitude normalization or mean outside the
supplied centered range require full fallback or a separately derived bound.
Normal-angle control does not bound sun-glint brightness, roughness response,
reflection hit/classification changes or temporal visibility. Small angular
errors can still cross a sharp reflection boundary. Protect salient materials and
review quality/motion/reload independently before any integration/default change.

Cost includes finite checks, scalar min/multiplies, per-octave radius square root
and budget tangent. Uniform/primitive bounds and the target raw limit can be
precomputed, but no such transport integration is implemented. Partial attenuation
adds cost without removing octave evaluation. There is no texture/derivative/
shader-reload/GPU work in this helper and no measured performance claim. Any future
consumer needs original/disabled/enabled driver/quality/compatibility acceptance
and repeated matched GPU/total-frame timings before benefit is promoted.
