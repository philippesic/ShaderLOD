# Conditional normal variance and roughness transfer

2026-10-05. Owner`procedural_lod`; mathematical advisor`procedural_lod/parallax_math`.
New files only: common`normal_variance.glsl`, `tests/test_normal_variance.py`, this
report. Frozen Photon adapters, builders, manifests, mod config and consumers
remain untouched. **No shader entry point consumes this helper yet.**

A reusable conditional covariance-to-NDF-width helper is implemented and tested.
Its assumptions do **not** currently support a proved, useful Photon water
compensation: missing phase/covariance statistics, strong anisotropy and Photon’s
mirror water reflection path remain unresolved. Keep actual water normal reduction
unaccepted. The helper is an experimental quality mechanism, not a performance
result or a guarantee of highlight energy/width.

## Primary-source basis and model boundary

Han et al. formalize normal filtering as convolution of the normal distribution
with the BRDF; replacing detail by an average normal alone loses shading information.
Their high-frequency representation retains more than one normal lobe when needed.
[Frequency Domain Normal Map Filtering, authors’ paper](https://www.cs.columbia.edu/cg/normalmap/normalmap.pdf).

Olano/Baker’s LEAN mapping keeps first/second slope moments in a common tangent
frame, including anisotropic highlight shape, rather than treating every normal
map as an isotropic scalar roughness. Their posted errata also distinguish variance
combination from the final normalized Beckmann shading expression.
[LEAN Mapping, authors’ page and errata](https://userpages.cs.umbc.edu/olano/papers/lean/).

Trowbridge–Reitz/GGX has long slope tails; its NDF and masking model together
determine the BRDF. Changing a normalized NDF’s width alone does not establish
conserved observed radiance. [PBRT microfacet theory](https://www.pbr-book.org/4ed/Reflection_Models/Roughness_Using_Microfacet_Theory).
Reviewed primary engine code explicitly discusses missing GGX second moments and
uses different approximations for normal filtering and finite-tail conversion;
there is no universal variance coefficient to copy blindly.
[Unity CommonMaterial source](https://github.com/Unity-Technologies/Graphics/blob/master/Packages/com.unity.render-pipelines.core/ShaderLibrary/CommonMaterial.hlsl).

No code was copied from those sources. The Gaussian model and explicit radial
GGX truncation below are stated mathematical models; neither certifies Photon’s
area-light/Smith/Fresnel/clamped response. Research was browsed for this task.
The historical Pixar paper URL redirected to a research-library landing page;
that redirected page is not used as evidence for an unseen equation.

## Helper contract and exact fallbacks

`shaderpacks/common/shaders/include/shaderlod/normal_variance.glsl` takes a verified
removed tangent-slope covariance `(xx,xy,yy)` in **dimensionless slope-squared
units**, where slope=`normal.xy/normal.z` in a common orthonormal frame. It takes
upstream NDF width**alpha**, not alpha² or perceptual roughness. No camera-distance
units or automatically guessed statistics are embedded.

`shaderlod_normal_variance_removed(C,w)` returns `(1-w²)C` for a known independent
centered contribution retained with weightw. This is the loss of field variance;
`(1-w)²C` describes the difference/error field and is a different quantity. For
multiple correlated octaves, the caller must instead provide the complete
C_full−C_retained including cross-covariance. Negative eigenvalues are rejected,
not clamped into artificial roughness.

`shaderlod_normal_variance_alpha(...)` selects one explicit model:

- Mode1, Gaussian/Beckmann slope moment addition: alpha_new²=alpha_old²+2v,
  where v=trace(C_removed)/2. This exactly matches the isotropic Gaussian slope
  width within that model. If anisotropy is merely small rather than zero, replacing
  it by the trace is an approximation controlled by caller eligibility limits.
- Mode2, **opt-in experimental** radial-truncated GGX moment matching. Full GGX
  has divergent slope second moments, so a finite radial massq is explicitly chosen.
  Mode2 is rejected unless`allow_ggx_approximation` is true. Matching that finite
  moment is not the exact convolution of GGX with a filtered normal field.

For normalized isotropic GGX slope density
`p(s)=alpha²/[pi(alpha²+|s|²)²]`, a radial cutoffR retains
q=R²/(alpha²+R²). Direct integration gives conditional per-axis variance

\[
v_q=\alpha^2 c(q),\qquad
c(q)=\frac{-\log(1-q)-q}{2q}.
\]

The experimental mapping uses alpha_new²=alpha_old²+v/c(q). This is a radial,
conditional finite-domain convention. It is not interchangeable with marginal-axis
cutoffs, a quoted retained-mass percentage from a different convention or the
Gaussian factor2. Changing q changes the fitted width. Helper q is restricted to
0.5..0.999; q=1/full variance is rejected.

| Chosen radial massq | v_q/alpha² |
| ---: | ---: |
| 0.50 | 0.1931471805599453 |
| 0.90 | 0.7792139405522478 |
| 0.94 | 0.9964950621064019 |
| 0.99 | 1.8258435282768135 |
| 0.999 | 2.9573349744655335 |

The helper returns the original alpha plus`accepted=false` for disabled/near/full,
unverified moments, unpreserved mean slope, unsupported material, unstable frame,
grazing light/view, nonfinite/singular/invalid covariance, excessive variance,
excessive anisotropy, unsupported model or excessive alpha change. Covariance
must be positive semidefinite; intrinsic per-axis variance ceiling0.01 restricts
this small-slope candidate. Alpha is restricted to0.0001..1. Caller limits can be
stricter; failing them retains original work rather than clamping a supposed
energy correction. Invalid upstream alpha itself is retained with rejection,
not silently repaired into another material.

**On rejection, the adapter must keep original normal detail before skipping it.**
Applying roughness after already discarding rejected detail would violate the
fallback. Eligibility/moments must therefore be resolved before expensive-octave
work. The common helper performs no sampling, derivatives, implicit-gradient
lookup, removed-octave evaluation or uniform/config transport.

Transitions come from continuous removed covariance as normal weights vary.
There is no distance policy in this helper. An adapter must bring its removal
proposal smoothly to zero before grazing/material/variance eligibility boundaries;
the helper’s rejection flag is not a temporal-transition implementation. At a
near/full/disabled boundary, original alpha and original normal work are required.

## Pinned Photon water applicability

Reference Photonv1.3b ZIP SHA256:
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Read-only source pins:

| Upstream member | SHA256 |
| --- | --- |
| lighting/bsdf.glsl | `a98700272c74b3e47f8efc0ce66974435f8e9ce8c14b6ce910d63f1a464f8918` |
| lighting/specular_lighting.glsl | `01d260e65de1dc1766e62044d79adef61dbdee9b75a8d48bd15ff8bd778a15e0` |
| surface/material.glsl | `23b1ab4f304d2c02ffe497006b5feefd649ab3176c01251fe5e8cd79cee66d78` |
| surface/water_normal.glsl | `a6d2dc6bed11d723e5468e22f46b9d8c2fdb090a3e2e1b5131ff1759e89849d3` |

Photon `Material.roughness` is alpha: both highlight and reflection compute its
square for the GGX distribution/Smith terms. Lab material decoding already squares
perceptual smoothness complement. Base water alpha is0.002; water texture highlight
adds0.3×texture_highlight, so0.002 is a base value, not every pixel’s final alpha.
Do not square an already-alpha material again when passing it into this helper.

`get_specular_highlight` bends NoH using a spherical area-light approximation,
uses Fresnel and Smith visibility, and caps output at4 to control bloom. These
nonlinear operations matter to both peak brightness and integrated visible energy.
A pure NDF-width match does not undo their effects after changing normals.

More decisively, rough-SSR VNDF sampling is inside`if (!is_water)`. Water remains
on the mirror ray`reflect(world_dir, normal)` even when rough reflection support
is present. Adjusting material alpha changes some weighting but cannot reconstruct
the distribution of reflected directions lost by removing water normal detail.
When rough-SSR support is absent, crossing roughness0.05 also changes f0/SSR fade.
Thus modifying the shared water material roughness can introduce additional
reflection brightness changes. Direct-highlight-only compensation would still
leave the altered mirror direction and needs separate quality proof.

## Why amplitudes alone do not supply actual moments

For an ideal uniform phase and a **known fixed** phase incrementδ, Photon’s wave
has centered autocovariance

\[
C(\delta)=\tfrac18\cos\delta+\tfrac1{128}\cos2\delta,
\quad
S(\delta)=\tfrac14(1-\cos\delta)+\tfrac1{64}(1-\cos2\delta).
\]

With original normalized amplitudeA_i, constant height multiplierV, influenceI
and finite-difference steph=0.1, per-axis slope variance is
`(I*A_i*V/h)²*S(delta_axis)`. Smallδ approaches the derivative estimate
`S≈(5/32)δ²`; the exact finite-difference formula handles larger frequencies.
The two-axis covariance is C(δx−δy)−C(δx)−C(δy)+C(0).

Actual Photonδ includes the unknown difference of the omitted octave’s noise
sample atq+h andq, not just carrier frequency. Uniform phase, fixed V, independent
phase/noise and cross-octave independence are unverified. Height variation uses
related noise; it changes both covariance and mean-field terms. A carrier-only
prediction therefore cannot set`moments_verified=true`.

For a carrier-only third-octave illustration, N3/persistence0.5/ordinary influence
0.04/V1 gives covariance
`(2.8828907158232084e-05, -5.442597072792424e-05, 0.00012920358593251457)`.
Its anisotropy ratio is0.9369399027097054, so a scalar policy with threshold0.1
rejects it. Ignoring that issue and applying the Gaussian trace formula would raise
base alpha0.002 to0.01272919844651448. This is an **unvalidated model illustration**,
not predicted actual Photon roughness or accepted distant highlight preservation.

Scalar reference sampling of actual pinned noise/height functions exposes the
cross-covariance problem directly. Over48 still-water5×5 patches (step0.125blocks,
N3/p0.5/influence0.04/times0/17/113), full-minus-first-only covariance is indefinite
in17 cases. Example origin(7,13)/time0 gives
`(-4.253866899432137e-06, -3.796483565642477e-05, 0.00014931809433425976)`,
with minimum eigenvalue−1.3126601926179536e-05. Removing detail can increase variance
in an axis because of cross-correlation; positive roughness broadening cannot
represent that tensor. These are finite scalar reference patches, not sampled
GPU pixels or a population estimate. They disprove automatic independent-positive
variance addition for those fields. Removed waves were evaluated **offline in
fixtures** to check the model, not inside the helper’s proposed rendering path.

Second moments also do not fix arbitrary highlight shape: two synthetic isotropic
fields with identical means/covariance but different higher moments give GGX slope
peak responses30.594952535927593 and39792.63624702275, a1300.6275× difference at
alpha0.002. This is an unclipped slope-density counterexample, not Photon output
brightness. It explains why moment matching requires an explicit distribution
approximation and cannot prove peak/energy parity for a non-Gaussian wave field.

## Static acceptance, cost and adapter stopping boundary

Executed`python3 -m unittest discover -s tests -p test_normal_variance.py -v`:
**12 tests passed, no skips**. Fixtures cover Gaussian width addition, correct
weight² variance transfer, finite radial GGX integral/normalization and divergence,
higher-moment peak counterexample, finite/PSD/magnitude/anisotropy/material/grazing/
near/full fallback, finite phase moments and actual pinned water/SSR/alpha source.
Actual pinned Iris option lexer reports no helper references/options; its bundled
GLSL4.10 syntax parser accepts the helper. No full shader-stage consumer transform,
GL driver compilation, linked uniforms, rendering, reload, motion, GPU counters or
performance measurement ran on this track.

Cost includes finite/PSD checks, one covariance discriminant norm, one alpha square
root and, only in opt-in GGX mode, one logarithm. Model scale can be precomputed per
material. No such mod transport/adapter consumption exists. Any statistics capture
or precomputation needed to provide trusted moments has its own cost; this helper
does not hide it behind a late rendering branch.

The bounded next adapter plan is conditional, not authorization to modify the
frozen water probe: establish zero-mean/common-frame statistics and C_full−C_retained
at the actual pixel footprint, including phase/height variation/cross-covariance;
reject unsupported/multimodal/anisotropic/grazing cases before choosing normal LOD;
calibrate direct GGX area-light responses using a separate local material copy;
keep water SSR normal work unless its missing direction distribution is independently
handled. Preserve original near/shadow/caustic/parallax and all disabled fallbacks.
Only after source review and matched still/motion/reload quality evidence could an
explicit private consumer be considered. Repeated original/disabled/enabled GPU
and total-frame measurements are then necessary for any speed claim.

Current source/physical assumptions do not meet that boundary for general Photon
water. The appropriate current result is the tested reusable conditional helper
and precise limit, with actual water variance compensation unintegrated/unaccepted.
