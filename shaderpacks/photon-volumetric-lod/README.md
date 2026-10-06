# Photon shared-work volume reference

Private, default-off consumer of the general ShaderLOD work policy, based on
pinned Photon v1.3b. Build with
`python3 scripts/build_photon_volumetric_lod.py --arm disabled`.
Generated archives remain under ignored `build/`; original Photon LICENSE and
Benjamin Stott (SixthSurge) attribution are retained.

The existing march is unchanged. Shared policy supplies distance/work decisions;
the explicit Photon adapter supplies physical entry/interval, count/floor and
quality inputs. INHERIT resolves safe unity; ADAPTIVE consumes the six general
Volumetrics controls. Exact capability schema1 and operation availability1,
core2/effects1or2 and effective private-on/cumulus-on/blocky-off options are required.
Missing/unknown/disabled inputs select the original function. The private option
defaults off; configuration or registration alone cannot add LOD to a shader.

See [implementation/evidence](../../docs/VOLUMETRIC_LOD_IMPLEMENTATION.md) for
exact source/archive pins, static checks and required driver/quality/GPU evidence.
No measured speed or accepted visual-quality claim is made.
