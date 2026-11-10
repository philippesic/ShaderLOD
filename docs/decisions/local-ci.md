> Historical foundation decision. Current scope and any superseded mechanisms are governed by [the approved porting plan](../PORTING_PLAN.md) and [policy changes](../../checks/policy-changes.md).

# Local CI proof

The hook and workflow each invoke exactly ./check. Local fresh-clone execution proves that command only. Hosted CI, x86 execution and required-review enforcement remain untested. Local only, no remote or push.
