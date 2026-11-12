# Development machine

## Goal
Choose and prepare the long-term machine for optimization development and testing.

## Authorized
Philip authorized benchmark implementation and qualification on Asahi and RTX 2080 Ti,
including bounded native Windows execution and required runtime preparation/transfers.
Selecting the permanent development workspace and granting future campaign allocations
remain separate decisions. RTX 5080 is not allocated.

## Done when
Philip selects the ongoing development machine and allocation, and its intended
source checkout, maintained commands and required live assets are verified there.

## State
Philip selected RTX 2080 Ti (WSL) as the primary T3/development machine, with
Asahi secondary. Both source checkouts use /home/philip/ShaderLOD and the same
established origin. T3 registration, icon and source synchronization are verified. Asahi and Mac
registrations are archived secondary/history; RTX 2080 Ti is the active pinned
project. Python 3.13.2 is installed inside the primary checkout. Its cold portable
check exposed a copied-venv shared-library failure; the maintained bootstrap now
uses symlinks and the corrected cold path is being checked.
This selection does not establish a newly prepared or qualified live runtime. Native Windows supervision, prepared
runtime/profile reuse and cross-host evidence transfers are implemented and exercised.
Benchmark controls passed on RTX 2080 Ti at its observed native 1024x768 surface and
Asahi M1 at 2560x1600; see the [workflow qualification](../results/benchmark-workflows/result.md).
These are scoped harness results, not all-pack/hardware performance qualification.

Original Reimagined with enabled DH is blocked on Asahi by fragment sampler limits;
the no-DH functional path passes. This is not a reason to lower native quality or a
requirement to keep Asahi. Source remote synchronization does not select the future
hardware or provision every live asset automatically.

## Next
Verify the selected primary machine’s actual checkout,
runtime and prepared world/profile there using maintained commands. Reuse applicable
qualification and exercise affected paths after a move. Preserve required evidence
and inputs; do not repeat the known DH failure without a new hypothesis.
