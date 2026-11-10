# Native sampler index compatibility

The old development runtime's SamplerUnitBudgetMixin is a necessary compatibility correction, not a parked optimization. Its omission reproduced native allocation failure without DH. Fragment-stage limits count active sampler resources; binding indices use the combined texture-unit namespace. Iris reserves index holes, so fifteen active samplers can legitimately need binding index sixteen while remaining within a sixteen-resource fragment limit.

The pinned SamplerLimits constructor feeds allocation and cleanup from the same bound. Use the lesser of the combined namespace and Minecraft's tracked array, and refuse if this cannot preserve native capacity. GL linking continues to enforce per-stage resource counts. This changes no shader archive bytes, options, resolution or AA. DH shader-link resource failures remain a distinct issue.

The required expression selector is pinned to Iris 1.8.12. Its exact unused-method directive represents an injected caller. CPU tests independently exercise reserved holes and the tracked-array bound. Runtime observations expose all three limits; successful linking and restoration still require live evidence. This correction creates a new runtime cohort; earlier uncorrected failures remain retained.

Primary reference: [Khronos glActiveTexture](https://registry.khronos.org/OpenGL-Refpages/gl4/html/glActiveTexture.xhtml). Historical derivation and evidence remain read-only in ShaderLOD/docs/BENCHMARK_CANDIDATES.md and the sampler-repair evidence under its 2026-11-04 audit.
