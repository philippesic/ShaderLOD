> Historical foundation decision. Current scope and any superseded mechanisms are governed by [the approved porting plan](../PORTING_PLAN.md) and [policy changes](../../checks/policy-changes.md).

# Missing is not zero

Measured values require a positive count, including a true measured zero. Missing values have a reason and no numeric payload. Health is PASS or FAIL; UNKNOWN is rejected. Coverage and noise analyzers wait for stage 4.
