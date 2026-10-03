# Minecraft version selection

Decision on 2026-10-03: target **Minecraft 1.21.1 / NeoForge 21.1.253 / Java 21**
first. The user requested the most widely used modding version before committing
to the initial version choice. Reuse the neighboring mod's build infrastructure
without inheriting its Minecraft target.

There is no comprehensive public active-player census by modded Minecraft version.
The evidence below supports this target for the NeoForge/Iris audience, rather
than claiming an exact ranking across every loader and historical version.

Actively maintained large NeoForge packs still target 1.21.1:
[ATM10](https://www.curseforge.com/minecraft/modpacks/all-the-mods-10) lists
22,097,502 total downloads and a September 22, 2026 release on 1.21.1;
[Better MC BMC5](https://www.curseforge.com/minecraft/modpacks/better-mc-neoforge-bmc5)
also targets 1.21.1. These are project downloads, not unique current players.

Direct Modrinth API version metadata provided another adoption proxy. Summing
downloads of Iris version files labeled NeoForge gave:

| Minecraft | Cumulative NeoForge Iris downloads |
| --- | ---: |
| 1.21.1 | 8,889,857 |
| 1.21.11 | 1,039,290 |
| 26.1.2 | 804,422 |
| 26.2 | 710,703 |
| 26.3 | 30,454 |

NeoForge Sodium and DH releases also had higher cumulative totals on 1.21.1
than the listed newer candidates. Full counts, source endpoints, date, and
method are recorded in [`dev/version-adoption.json`](../dev/version-adoption.json).
[Iris versions](https://modrinth.com/mod/iris/versions) and
[Modrinth version API](https://api.modrinth.com/v2/project/iris/version) are the
underlying sources.

Cumulative totals favor older releases and count repeated downloads. A file
supporting several game versions contributes to each supported-version total,
so those rows must not be added together. Modpack popularity and downloads
provide corroborating evidence, not an exact market-share estimate. Forge
1.20.1 remains outside this NeoForge/Iris target decision.

The initial 26.1.2 scaffold built successfully but was migrated before accepting
the runtime. The pinned stack now uses stable Iris 1.8.12, Sodium 0.6.13,
DH 3.3.3, and Photon v1.3b; see `dev/runtime.lock.json`. Newer Minecraft ports
can follow after the first optimization has measured value.
