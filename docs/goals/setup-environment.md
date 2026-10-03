# Set up the ShaderLOD development environment

Created: 2026-10-03. Status: complete.

## Objective

Create a reproducible NeoForge client mod environment with Iris required,
Photon and DH working in the development client, a basic explicit distance LOD
foundation, and repository-local agent/docs/skill infrastructure ready for the
next implementation stage.

## Scope and boundaries

Reuse appropriate NeoForge infrastructure from `../brewing-and-baking` and
agent operations patterns from `../binary-eagle-decoding`. Adapt project
identities, remove unrelated research and remote GPU settings, and inherit the
user's model choices. Keep DH optional for the mod; installing it in development
does not mean DH-specific LOD is implemented.

Implement enough LOD groundwork to make the next Photon rendering experiment
concrete. Do not build a generic shader rewriter, a Fabric edition, the full
multi-effect MVP, or a remote publishing workflow in this goal.

## Acceptance evidence

- NeoForge skeleton builds and relevant checks pass.
- Exact runtime artifacts and Photon source revision are pinned and reproducible.
- Iris selects/compiles Photon and renders an in-world scene.
- DH loads and renders distant terrain in that compatible environment.
- Initial LOD foundation has checks appropriate to its implementation; report
  separately whether actual modified shader behavior has rendered.
- Agent profiles/config parse, the checkpoint hook executes, and the local
  start-goal-team skill validates.
- Launch/reproduction instructions and known hardware or runtime limits exist.

Acceptance applies to the recorded reduced Mac profile. Other Photon presets
and hardware need their own validation before broader support claims.

## Ownership during setup

The orchestrator owns integration, client/GPU access, the native Goal, and final
checkpoint. Bounded workers own NeoForge/build files, Photon/runtime preparation,
and agent/docs infrastructure in explicit partitions. Workers report evidence
to the orchestrator; they do not overwrite each other's acceptance status.

## Current checkpoint

Final target: Minecraft 1.21.1, NeoForge 21.1.253, Java 21, stable Iris 1.8.12,
Sodium 0.6.13, DH 3.3.3, and Photon v1.3b. The user redirected version selection
toward broad adoption; public evidence and limits are in `docs/VERSION_SELECTION.md`.
Artifacts and hashes are pinned in `dev/runtime.lock.json`.

- `./gradlew build compileDevClientJava` passed. Two JUnit methods check distance
  boundaries, invalid settings, and disabled behavior. Production jar inspection
  confirms it includes the license and excludes `shaderlod_dev`/the smoke harness.
- `python3 -m unittest discover -s tests -v` passed all six tests, including 48
  glslangValidator fragment-program compilations and guarded, deterministic
  Photon packaging. All untouched DH entrypoints and upstream license are preserved.
- `./gradlew runClient -PsmokeTest=true` rendered the seeded scene under Iris
  with DH loaded. Runtime reports `shadersInUse=true`, `fallback=false`, `DH=true`.
  DH's visible/total VBO counts reached `159/448` and `154/448`; distant terrain
  is visible in horizon captures.
- `./gradlew runClientIris -PsmokeTest=true` rendered the same scene with `DH=false`.
  Both runs captured normal LOD, green/amber/red distance zones, and all-green
  disabled-LOD debug output. The changing images prove live uniform transport.
- Five Codex TOML files parse, the hook executes startup/resume/compact,
  and skill-creator's validator reports the local skill valid.

The first local Photon attempt exhausted texture units; the reproducible reduced
Mac option set resolved this. Upstream general Apple compatibility and full/Ultra
rendering remain unverified. No GPU frame-time improvement is claimed. The
first implemented effect is opaque vanilla shadow PCF sample LOD; DH keeps its
upstream rendering path. See `docs/SETUP.md` and `docs/PHOTON_INTEGRATION.md`.

Workers `agent_setup`, `photon_lod`, and `runtime_review` finished their bounded
assignments. No separate user-owned chat, remote host operation, external push,
or publishing action occurred. The repository is initialized locally on `main`.

At handoff, the combined development client remains running for inspection:
`./gradlew runClient -PsmokeTest=true`, game directory `run/`, Java PID `79079`,
tool session `99698`, console `dev/logs/final-client-console.log`. The Iris-only
client was stopped after its checks. Quit the game normally to save its world;
if necessary, verify that recorded PID still belongs to this checkout before
terminating it. PID/session identifiers become stale after a relaunch.

## Next action

Start the next user-requested goal around the PCF optimization kill test.
Use a representative normal-terrain scene, DH absent, fixed camera/options,
desktop-GPU timings, and visual inspection. Expand into SSR/POM and DH-specific
LOD only after the first optimization shows useful quality/performance behavior.
