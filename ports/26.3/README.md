# ShaderLOD Minecraft 26.3 port

Both loader modules target Java25 and the exact published dependency pins in
`dependencies.lock.json`. See [port evidence](../../docs/PORT_263.md) for actual
checks and unverified runtime boundaries.

From repository root run `python3 ports/26.3/setup_runtime.py`,
`python3 ports/26.3/setup_gradle.py`, then `python3 ports/26.3/verify_iris_api.py`.
Use `python3 ports/26.3/build_port.py` for sequential builds with a scoped
Java25 environment and a two-CPU cap inherited by every child JVM. Manual
equivalent from each module directory:

```
JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 JAVA_HOME=/Users/pippo/github/shaderlod/dev/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home ../runtime/gradle-9.7.1/bin/gradle test jar --no-daemon --max-workers=2
```

Then from repository root run `python3 ports/26.3/audit_artifacts.py`.
Coordinate builds and tests with the client/GPU owner before running.
