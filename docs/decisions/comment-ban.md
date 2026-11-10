> Historical foundation decision. Current scope and any superseded mechanisms are governed by [the approved porting plan](../PORTING_PLAN.md) and [policy changes](../../checks/policy-changes.md).

# Full comment ban

Owned code, tests, scripts, build files and configuration contain no comments, Javadoc or docstrings. Markdown prose and literal string data are allowed. Exact shebangs, required Apache wrapper notice and narrow tool directives require entries in checks/exceptions.json. No ordinary prose exception is valid.
