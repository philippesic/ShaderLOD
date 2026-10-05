# Miniature ShaderLOD core diagnostic

Experimental derivative of **Miniature Shader 2.19**, by **Mateus A. Kreuch**.
[Official source](https://github.com/mateuskreuch/minecraft-miniature-shader),
[publisher release](https://modrinth.com/shader/miniature-shader/version/LWmZ94RG).

Copyright (c) 2022 Mateus A. Kreuch. MIT license: full upstream copyright and
permission notice restored in `LICENSE.Miniature`. The publisher release omits
that file; it was recovered without changes from pinned upstream revision
`bdb6e0020b4318d4e845902cbcc3e3552b0e9274`, whose shader files differ from this
release. This derivative patches the exact release, not that source tree.

Release archive SHA256:
`5f224e907657f3ffd95d37e37c27e555deef64fa56da1e13374f2c295241d7f0`.
Restored license SHA256:
`397d80246d261805b953865428e9c244e5a7bd2bbbb6f3b37f16e884e697e4d1`.

Modified 2026-10-05: four bounded shader/option/UI members and one new
texture-free diagnostic helper. Both new options default off. Original SSR
source, sampling budgets, masks, ray/refinement logic, roughness noise, normals,
shadows and lighting are retained. Cue overlays existing qualifying reflection
pixels only and preserves a contribution from their original reflected color.
No Java/common transport changes, optimization, support or performance claim.

Full editable shader source, exact literal source patch, this notice and the
restored MIT notice accompany the derivative. New source modifications use MIT
terms. The deterministic builder is `scripts/build_miniature_bridge_probe.py`;
source contract is `docs/MINIATURE_CORE_BRIDGE_PROBE.md`. Static fixtures and
downloads do not establish driver rendering or actual mod-to-pack transport.
