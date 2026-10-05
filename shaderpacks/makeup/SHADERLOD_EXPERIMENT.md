# MakeUp-ShaderLOD-Experimental 9.5g

Experimental derivative, modified 2026-10-05. Default off. No runtime support or
performance claim. This shader option is not connected to ShaderLOD Java config
or the common Iris LOD protocol.

Upstream: **MakeUp Ultra Fast**, Javier Garduño.
[Official source](https://github.com/javiergcim/MakeUpUltraFast),
[official release page](https://modrinth.com/shader/makeup-ultra-fast-shaders),
[CurseForge](https://www.curseforge.com/minecraft/customization/makeup-ultra-fast-shader),
[Planet Minecraft](https://www.planetminecraft.com/mod/makeup-ultra-fast-shader/).
Upstream source revision `ad2d9327ad485429e4eaf2b3f98cd738ada609de`;
release archive SHA256
`0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8`.

Original source copyright/license notices and README are retained. `LICENSE`
contains upstream LGPL v3; `LICENSE.GPL-3.0` supplies the incorporated GPL v3
text. Shader modifications and the added GLSL helper retain LGPL v3 terms.
The full editable shader source is in this archive. The exact replacement
source and pins are in `shaderlod-source-patch.json`; `shaderlod-build.json`
records builder inputs. The deterministic builder and patch are maintained at
`scripts/build_makeup_adapter.py` and `patches/makeup/9.5g.json` in ShaderLOD.

Change: vanilla water `reflection_calc` fades screen-space reflection between
96 and 160 camera-relative blocks and skips all SSR trace/depth/scene fetches
beyond 160. Its existing sky/cloud fallback and sun reflection remain. Distance
uses the originating view-space `fragpos`, not the ray hit position. Enabled
trace search/refinement keeps the original ten steps and math, with explicit
LOD 0 sampling. It removes distant reflected scene content, so it is a quality
tradeoff. Fade pixels still execute the complete trace; only pixels beyond the
end skip it. Glass, DH/VOXY water, lighting and shadows are unchanged.

Options: advanced water screen → ShaderLOD water SSR (experimental).
`SHADERLOD_MAKEUP_WATER_SSR=0` selects original reflection code; `1` enables
fade/skip only with native raymarched reflections (`REFLECTION_SLIDER=2`).
`SHADERLOD_MAKEUP_SSR_START` and `SHADERLOD_MAKEUP_SSR_END` are radial blocks.
End values at/below start clamp to start + 1 block. The fade is smoothstep with
no temporal dithering. All modes remain unvalidated in Iris on this derivative.
