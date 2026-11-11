# Iris, NeoForge and Distant Horizons integration

The mod binds to pinned Iris 1.8.12, Sodium, NeoForge 21.1.253 and Distant Horizons 3.3.3 through the `integration` package. Versions live in `dependencies/runtime.lock.json`; see [dependencies](dependencies.md) for what to do when they change.

## Selection states

A selection moves through requested, prepared, linked, active and restored. Each state has its own evidence:

- **Linked** means Iris loaded the selected archive and built a pipeline for it.
- **Active** additionally needs a later tick to observe the same pipeline and generation with a submitted-frame witness. `IrisFrameCompletionMixin` records the pipeline whose final level-render method returned.
- Neither state proves GPU completion, visual equivalence or performance.

Reloads, dimension changes and DH transitions invalidate prepared state. Pending native Iris option queues must be applied or cancelled in Iris before ShaderLOD admits a selection.

## Native writer journal

`IrisConfig.load` restores a pending journal before Iris parses its configuration. During an owned reload, Iris's config and option output is buffered. The exact intended bytes, or deletion, are journaled before atomic publication through `storage.NativeWriteScope`. Swallowed native IO failures propagate through that scope. Unknown foreign edits make recovery refuse instead of overwriting them. Failed restoration keeps the journal and refuses a new selection.

## Required mixin entry points

The mixins in `integration/mixin` use exact-descriptor injection and redirect selectors pinned to Iris 1.8.12. A selector that stops matching fails startup. Error Prone cannot see their reflective callers, so each mixin method has one exact `UnusedMethod` directive and each required `CallbackInfo` parameter an exact `UnusedVariable` directive, listed in `checks/exceptions.json`. No lint is disabled globally.

## Sampler binding indices

`SamplerUnitBudgetMixin` and `SamplerIndexPolicy` correct how Iris sizes texture binding indices. Fragment-stage limits count active sampler resources, but binding indices use the combined texture-unit namespace, and Iris leaves holes in that namespace. Fifteen active samplers can therefore need binding index sixteen while staying within a sixteen-resource fragment limit.

The policy uses the lesser of the combined namespace and Minecraft's tracked array, and refuses if it cannot preserve native capacity. GL linking still enforces per-stage resource counts. Shader bytes, options, resolution and AA are unchanged. Without this correction the original Reimagined pack fails native allocation on Asahi even without DH ([F10](../FINDINGS.md)). Reference: [Khronos glActiveTexture](https://registry.khronos.org/OpenGL-Refpages/gl4/html/glActiveTexture.xhtml).

## Distant Horizons

DH is optional. `DhObservation` keeps DH classes behind an optional boundary, and `DhApiObservation` reads presence and configuration through the DH API after startup. Configured or enabled values do not prove that DH terrain was rendered. With DH enabled, the original Reimagined pack exceeds the fragment sampler limit on Asahi, with or without ShaderLOD ([F9, F10](../FINDINGS.md)).
