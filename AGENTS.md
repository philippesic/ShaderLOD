# ShaderLOD agent rules

ShaderLOD is a NeoForge client mod and an explicit Photon shader integration.
Read `docs/STATUS.md` and the active goal before substantial work. Use
`docs/PROJECT_OVERVIEW.md` for scope, `docs/EVALUATION.md` for evidence, and
`docs/AGENT_OPERATIONS.md` for teams and handoffs. Small edits need only the
relevant guidance.

- Iris is required. Photon is the first supported shader pack. Distant Horizons
  is optional for users, but present in the development compatibility runtime.
  Do not add a Fabric implementation to the current goal.
- Prove distance LOD in Photon before expanding the mod framework. Modify
  explicit, pinned shader sources or patches; do not invent a universal GLSL
  rewriting system. Distinguish mod config, shader options, and an actually
  verified connection between them.
- Verify Minecraft, NeoForge, Iris, Sodium, Photon, and DH versions together.
  Record exact revisions and artifact hashes. Downloads and source inspection
  do not establish that a shader compiled or rendered successfully.
- Keep one active project goal in `docs/STATUS.md` and `docs/goals/`. Start a
  new native goal only when the user requests it. Checkpoint actual results
  before a handoff; chat memory alone is not the project record.
- Delegate independent, bounded work to the project roles when useful. Name
  each worker's files, acceptance evidence, and stopping boundary. Use native
  subagents for subtasks; create a separate Codex chat only when the user
  explicitly asks for a new chat. Inherit current model settings by default.
- Avoid parallel edits to the same file. Give one owner the interactive client,
  shader reloads, and GPU measurements at a time. Use isolated worktrees when
  they help prevent conflicts; explicit file partitioning is fine for setup.
- Treat GPU time, visual quality, shader compilation, and compatibility as
  separate checks. FPS alone does not prove a shader optimization. Label static
  checks, automated tests, runtime checks, and unverified assumptions clearly.
- The user owns major product forks. Record pending choices in
  `docs/DECISIONS.md`; continue useful independent work. Preserve current user
  authorization and do not infer remote pushes, publishing, or messaging from
  this file.
- Keep generated clients, mod jars, downloaded upstream sources, world saves,
  captures, and raw logs out of Git. Preserve licenses and attribution for any
  distributed shader derivative. Commit messages are short, with no coauthors.
- Record demonstrated coordination problems or corrected misunderstandings
  factually in `docs/USER_LESSONS.md`, including agent contributions. Do not
  turn disagreement or speculation into a confirmed user mistake.

## Current acceptance and recurring failure modes

- Resolve current direction from STATUS and the latest active-goal checkpoint.
  The current general-LOD goal requires 1.5x total rendering throughput against
  matched original Photon with little discernible quality loss. The older 15%
  target is superseded. Do not treat these defaults as overriding later steering.
- Parallelize independent implementation and source/quality analysis automatically
  when permitted. Usually use at most four active workers; parent alone schedules
  new tracks. Serialize client/GPU work. Quiet measurement windows also exclude
  builds, tests, encoders, and unrelated CPU activity unless qualified by protocol.
- Preserve distant lighting/shadow cues. Check representative still and motion
  scenes for popping, flicker, foliage, water, and reflections. A small millisecond
  saving with conspicuous visual loss does not satisfy the goal.
- Verify loaded Iris options and actual consuming shader paths, temporal inputs,
  camera/time synchronization, resolution and focus. Source switches, config bytes,
  or mod-side readbacks alone do not prove effective shader behavior. Keep shader
  compilation, effective option discovery, runtime linkage, quality, and speed
  as distinct acceptance checks. Retain contaminated/failed captures as qualified
  evidence; do not silently relabel or weaken fingerprint guards.
