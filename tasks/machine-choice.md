# Development machine

## Goal
Choose and prepare the machine on which optimization development and testing will run.

## Authorized
Record the current blocker and prepare the decision. Do not use another machine, transfer artifacts or alter host configuration until Philip selects and allocates it.

## Done when
Philip selects the machine and allocation, and the required development environment is prepared and verified there. Benchmark qualification follows the agreed benchmark design.

## State
This is one of three readiness blockers. Philip may move development to another machine. Existing live evidence is Asahi-specific. Original Reimagined with DH fails on Asahi with and without ShaderLOD because of fragment sampler limits; the no-DH functional path passes. These are scoped findings, not a requirement to keep Asahi or lower native quality. Native Windows execution and cross-host transfer are not implemented in the new runner.

## Next
Ask Philip for the intended machine during the machine discussion. Assess the concrete setup and platform gaps after selection. Do not repeat the known Asahi DH failure without a new hypothesis. Preserve original evidence and inputs during any later authorized move.
