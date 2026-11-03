# Minecraft 1.21.11 port slice

Isolated NeoForge and Fabric shader-service ports. Both build; runtime validation
is pending. See [acceptance evidence](../../docs/PORT_12111.md) and the exact
[dependency lock](dependencies.lock.json).

From repository root:

```sh
python3 ports/1.21.11/setup_runtime.py
python3 ports/1.21.11/setup_gradle.py
python3 ports/1.21.11/verify_iris_api.py
./gradlew -p ports/1.21.11/neoforge --no-daemon --max-workers=2 build
ports/1.21.11/runtime/gradle-9.4.0/bin/gradle -p ports/1.21.11/fabric --no-daemon --max-workers=2 build
python3 ports/1.21.11/audit_artifacts.py
```

Coordinate CPU builds with the runtime owner. The parent controls client launches,
shader reloads and GPU checks. No client was launched for these build checks.

The shared pure LOD, pack service, selection transaction, source checks, presets
and explicit shader adapter assets are consumed directly from root sources.
Loader-specific bounded copies and API adaptations have recorded ancestry in
[source-provenance.json](source-provenance.json). These read existing uncommitted
project work; this file does not imply a clean Git revision or a frozen branch.
NeoForge renderer experiments/terrain hooks and dev benchmark infrastructure are
outside this initial slice; their config switches are omitted. Fabric retains its
existing INHERIT-only effect transport. Both retain actual pack Apply/rollback,
core/temporal/effect bridges, source recognition, presets and settings screens.

Downloaded artifacts, libraries, Minecraft sources, Gradle distribution, generated
clients/builds and raw evidence stay ignored. Shader adapter resources and original
source notices/licenses are reused unchanged. Main mod JARs contain the ShaderLOD
MIT license and no copied upstream mod JARs.
