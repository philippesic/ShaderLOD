# ShaderLOD

- Current scope, task and hardware availability: [STATUS](docs/STATUS.md). Its current direction supersedes historical campaign closure/pause records.
- For optimization work, search [experiment history](docs/EXPERIMENTS.md) for relevant prior attempts. Record meaningful outcomes with their scope and evidence.
- Run optimization tests using the maintained command in the [benchmark guide](docs/BENCHMARK.md); fix harness defects in the harness, not through custom wrappers.
- Preserve original shader packs, native screen resolution and AA, and restoration on Off, Cancel and failed Apply. Identify the settings, preset, dimension and render graph. Iris is required; DH and PBR need compatibility evidence.
- Reuse prepared client slots; profile/world copies must use `run_storage.copytree` and its capacity checks. Keep worlds, captures, raw logs and builds out of Git.
- The canonical checkout is `/home/philip/ShaderLOD` on Asahi. Mac copies and the
  lowercase historical `shaderlod` checkout are not interchangeable with it.
  Use the actual target environment and its current allocation for runtime work.
- When independent review is requested, give the reviewer a focused question,
  the user's scope and the evidence. Address concrete findings; a scoped stopping
  decision is not production qualification. No named role or automatic review loop
  is required. Preserve any explicit model/effort choice in the user's request.

- Git-visible source/docs and ignored artifact preservation: [Git and artifacts](docs/GIT_AND_ARTIFACTS.md). Ignored files are not automatically disposable; untracked files are not yet saved in Git.
