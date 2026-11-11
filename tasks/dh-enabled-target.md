# DH-enabled live target

## Goal
Pass live functional verification on the required target: original Complementary Reimagined r5.9.3 with Distant Horizons enabled, original options, native resolution and AA.

## Authorized
Bounded `./verify --runtime` attempts on the Asahi MacBook Air within an allocated display window. Not authorized: the RTX 5080 or any other machine, lowering shader settings or samplers, editing the pack, or changing what counts as a pass.

## Done when
A DH-enabled receipt passes the same probe steps as the no-DH run in F10, with rendered DH coverage observed, and is recorded in `FINDINGS.md`.

## State
Blocked. With DH enabled, composite1 fails with "Too many fragment shader texture samplers" on Asahi, with and without ShaderLOD (F9, F10). The no-DH path passes. ShaderLOD fails before activation, because the native graph never submits a frame.

## Next
Philip decides between a different compatible machine, a different first pack, or a reviewed native compatibility change that alters shader behavior. Until then, do not retry on Asahi without a new hypothesis.
