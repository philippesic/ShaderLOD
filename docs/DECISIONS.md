# Decisions

| Date | Decision | Basis | Status |
| --- | --- | --- | --- |
| 2026-10-03 | Start with NeoForge rather than Fabric | Explicit user direction | Accepted |
| 2026-10-03 | Iris required; Photon first shader target | User's architecture and setup request | Accepted |
| 2026-10-03 | DH optional to users and included in dev runtime now | Setup needs DH working; DH-specific LOD belongs to later MVP | Accepted |
| 2026-10-03 | Maintain explicit Photon patches instead of generic GLSL rewriting | User's proposed proof-first architecture | Accepted |
| 2026-10-03 | Use inherited agent models and repository-local roles/checkpoints | Reuse sibling workflow without overriding user settings | Setup choice |
| 2026-10-03 | Target Minecraft 1.21.1, NeoForge 21.1.253, Java 21 | User requested broad adoption first; current large NeoForge packs and cumulative Iris/Sodium/DH downloads support 1.21.1 | Accepted; see VERSION_SELECTION.md |
| 2026-10-03 | Pin stable Iris 1.8.12 + Sodium 0.6.13, DH 3.3.3, Photon v1.3b | Iris's declared Sodium dependency, artifact/API audit, and actual development runtime | Verified reduced Mac profile |
| 2026-10-03 | Start with opaque vanilla PCF sample LOD and a versioned mod uniform bridge | Pinned Photon has explicit adaptive PCF loops; GLSL variants and live debug/enable controls pass | Implemented; performance unmeasured |
| 2026-10-03 | Use a reduced Photon profile for the local Apple M3 Max smoke test | Full initial options exhausted texture units; disabling optional samplers lets Iris/Photon/DH render | Development profile only; standard/Ultra requires desktop GPU validation |

Dependency artifacts are recorded in `dev/runtime.lock.json`. Verify this set
in the development client before calling it supported. Photon upstream does
not support the local Apple GPU; the client attempt must establish and record
the actual hardware result. The full MVP effect set and numerical
performance acceptance gate remain open until the first measured proof.
