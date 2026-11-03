# ShaderLOD

- Current objective and pause: [STATUS](docs/STATUS.md).
- For optimization work, search [experiment history](docs/EXPERIMENTS.md) for relevant prior attempts. Record meaningful outcomes with their scope and evidence.
- Run optimization tests using the maintained command in the [benchmark guide](docs/BENCHMARK.md); fix harness defects in the harness, not through custom wrappers.
- Preserve original shader packs, native quality and AA, and restoration on Off, Cancel and failed Apply. Use pinned family adapters and identify the preset, dimension and render graph. Iris is required; DH and PBR need compatibility evidence.
- Reuse prepared client slots; profile/world copies must use `run_storage.copytree` and its capacity checks. Keep worlds, captures, raw logs and builds out of Git.
