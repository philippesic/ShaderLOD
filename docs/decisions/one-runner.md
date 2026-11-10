> Historical foundation decision. Current scope and any superseded mechanisms are governed by [the approved porting plan](../PORTING_PLAN.md) and [policy changes](../../checks/policy-changes.md).

# One runner, no GPU lock

There is no benchmark runner in stage 2. Stage 4 will have one declared launch/option entry and helpers. No GPU leases, semaphores or lock service. A plain refusal when a client is already running is permitted.
