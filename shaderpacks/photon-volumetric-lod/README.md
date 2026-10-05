# Photon primary volumetric integration experiment

Private, default-off source modules for the pinned Photon v1.3b reference adapter.
Assemble a runnable derivative with
`python3 scripts/build_photon_volumetric_lod.py --arm disabled`.
The original Photon ZIP and LICENSE remain pinned; generated archives stay in
ignored `build/`. The `enabled` arm turns on only the private shader option and
still requires valid ShaderLOD core2 and effect extension1/2 controls.

See `docs/VOLUMETRIC_LOD_IMPLEMENTATION.md` for actual entry points, units,
normalization, eligibility, fallback, static evidence and pending driver/quality/
GPU acceptance. No measured performance claim is made.
