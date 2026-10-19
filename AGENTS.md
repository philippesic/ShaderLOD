# ShaderLOD notes

- For campaign or remote work, consult [STATUS](docs/STATUS.md) for current scope, pauses and allocation; use [Operations](docs/AGENT_OPERATIONS.md) for client/build/GPU procedures.
- Use pinned shader-family adapters, preserving ancestry, licenses and native quality/AA. Avoid universal GLSL rewriting.
- Identify the selected preset, dimension and active render graph before changing an adapter.
- Preserve restoration for original/Off/Quality/Cancel, failed Apply and mismatched configurations.
- Iris is required. DH is optional for users, but DH and PBR compatibility evidence are required. Support for one family/loader/version tuple does not establish another.
- Use matched total-frame measurements for performance claims; see [Evaluation](docs/EVALUATION.md).
- Keep worlds, downloads, builds, captures and raw logs out of Git. Commits use short subjects with no body or coauthors.
