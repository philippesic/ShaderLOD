> Historical foundation decision. Current scope and any superseded mechanisms are governed by [the approved porting plan](../PORTING_PLAN.md) and [policy changes](../../checks/policy-changes.md).

# Source-set boundaries

One Gradle project: empty main and devClient source sets, dependency-neutral benchmarkCore contracts and test. No mod classes. ArchUnit validates compiled boundaries and provider ownership. Descriptors named *PackSpec, *Workload and *ScopeSpec are discovered automatically; no central catalog edits.
