# Terminal loaded-water scene survey

`WaterSceneSurvey.captureTerminalSnapshot(Minecraft client)` is an opt-in,
development-only read-only survey. It can help choose source-surveyed camera
anchors for targeted water tests. It does not establish glints, unobstructed
views, continuous water depth, shader quality, performance or safe underwater
camera placement. Compilation, actual survey execution and image/scene checks
belong to the root runtime owner. The author launched no client.

## Fixed cells, load guards and bounds

The candidate grid is exactly x `[240,300,400,500,650]`, z `[640,660,680]`,
y `[40..80]` inclusive: 15 columns and 615 candidate cells. Coordinates are
world block units; camera position is actual main-camera doubles and rotations
are degrees. No x260 column was added; x300/x400 may provide usable surveyed
alternatives, but that remains unverified until the report and scene are read.

Every world-state read first checks `client.level.hasChunkAt(pos)` **and**
`client.level.getChunkSource().hasChunk(x >> 4, z >> 4)`. This double guard is
necessary: Minecraft 1.21.1 `ClientLevel.hasChunk(int,int)` returns true
unconditionally. `ChunkSource.hasChunk` delegates to
`ClientChunkCache.getChunk(..., ChunkStatus.FULL, false)`, which returns null
for an absent/out-of-range cache entry without loading or generating terrain.
The survey never directly calls `getChunk`, requests server chunks, or uses a
heightmap. Missing chunks/out-of-build-range cells are unknown, never synthetic
AIR/water/empty terrain.

A loaded-only `BlockGetter` facade provides a cache shared by column cells,
camera context and any neighbor-dependent fluid-height/solid/collision getters.
It permits at most 4096 direct `ClientLevel.getBlockState` calls; each cached
immutable state also supplies its fluid state. All neighbor reads receive the
same double guard and budget. Block-entity-dependent context is unknown because
the facade deliberately declines that lookup. A known basic block/fluid record
remains known if a contextual shape/height field becomes unknown. The report
exposes actual direct read and unique cached-position counts, and budget status.
Biome sampling is omitted to avoid hidden neighboring lookups.

Actual `Camera.getFluidInCamera()` is called at most once. The exact cached
source reads the eye cell, up to five near-plane cells, and water/lava height
checks may read the cell above each. Before calling it, the survey guards and
preloads all six possible cells plus their above cells: at most 12 unique
positions. Its internal repeated block/fluid lookups are not instrumented;
17 is a conservative source-derived upper bound. The total world-state getter
call ceiling is therefore 4096 direct facade misses +17 camera-internal reads
=4113, not a silently claimed 615 total reads. All possible camera lookup
positions have been preloaded into the same bounded cache first. No FogType
query runs if its positions are missing/unavailable or the camera is not
initialized in the current camera-entity client level.
The camera-internal bound applies to the inspected Minecraft Camera method and
vanilla water/lava height implementations in the pinned development stack;
mods replacing those implementations require a fresh audit.

## Fields and interpretation

The terminal JSON records dimension, game/day time, day-time modulo 24000,
sky-light/ceiling flags, rain/thunder values at interpolation partial tick zero,
and current rain/thunder booleans. These are world inputs, not shader wetness
supplier reevaluations or a sun-glint forecast. Main-camera position/rotation,
initialized/detached state, configured camera type/first-person flag, actual
FogType, and its loaded cell's block/fluid/solid/collision flags are included.
The camera's `detached` flag and configured camera type are separately reported;
player feet coordinates are not substituted for the eye position.

Each sampled cell records registry block/fluid names, AIR, can-occlude, fluid
empty/water/source/amount, fluid height, solid-render and empty-collision-shape
flags. A waterlogged collision block may have water and nonempty collision
together; water does not imply safe camera placement. Each column records
observed water/nonempty-fluid counts, range completeness and the greatest
observed water-cell top in y40..80 when a height is known. That bounded maximum
is not proof of the entire column's surface or a measured continuous depth.
Missing cells and failed contexts remain explicit unknown objects.

Tentative west-facing glint anchor `(400.5,68,680.5)` and older x240 shoreline
remain proposals. The survey reads integer cell `(400,y,680)` but does not move
the camera, set yaw/time/weather or validate those proposals. Root must inspect
loaded-water continuity/collision and actual near/far/glint/underwater captures
before adopting camera anchors or making quality claims.

## Cached Minecraft 1.21.1 API inspection

Inspected sources:
`build/moddev/artifacts/neoforge-21.1.253-sources.jar`, SHA-256
`abf3bed3623112c03cd16602f8cbe3ba09c7566e4aa610fd310530f7b4013d83`.
This is the local mapped/patched Minecraft 1.21.1 source bundle, not source from
the separate 26.1.2 runtime. Relevant paths/anchors inside the jar:

- `client/multiplayer/ClientLevel.java:337`: unconditional `hasChunk` result.
- `world/level/LevelReader.java:181–187`: `hasChunkAt` delegates to `hasChunk`.
- `world/level/chunk/ChunkSource.java:27–28`: loaded-only false-creating check.
- `client/multiplayer/ClientChunkCache.java:73–81`: in-range valid cache entry
  or null for `false`; no client generation.
- `client/Camera.java:134–174`: cached pose/initialized/detached getters and
  pure near-plane offsets; `177–213`: actual FogType lookup positions.
- `world/level/material/FlowingFluid.java:468–479`: height checks the above
  cell; `FluidState.java:37–58`: source/empty/height/amount accessors.
- `world/level/block/state/BlockBehaviour.java:628–658`: solid/collision APIs;
  `world/level/BlockGetter.java`: facade state/fluid/block-entity and dimension
  height accessor contract. The facade's `getHeight` reports dimension height,
  not terrain height or a heightmap query.

## Root integration and run boundary

Root forwards `-PwaterSceneSurvey=true` as JVM property
`shaderlod.dev.waterSceneSurvey=true`, and adds the returned Gson `JsonObject`
to a terminal workload report after measurement ends, while the owned world is
still open on the client/render thread. The first call freezes the snapshot for
that JVM; later calls return copies without re-surveying. Disabled/absent-world
calls return unknown and do not query world state. No periodic observer is
installed. The utility performs no file writing; root saves reports in ignored
run/log directories and preserves prior evidence.

Use a fresh owned scene-survey profile, e.g. the already prepared coastal world,
with the forwarded survey property and `-PgpuTiming=false`. Record the actual
launch task, exact pack/options/config, world/camera/time, component hashes,
GPU/driver, logs and process handle. The source author has run no full compiler,
client, GPU check or world mutation. Root must compile after all workers freeze,
reserve the sole client/GPU slot, inspect the report and then prepare any new
camera profiles from verified loaded geometry. No new mixin is needed.
