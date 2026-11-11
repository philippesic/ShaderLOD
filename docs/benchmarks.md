# Running benchmarks

Use the maintained `python3 -m shaderlod_host` entry point. Keep one owned client and a prepared working world through short comparisons and profiling. Start fresh JVMs for independent qualification sessions; reuse the same prepared profile and world. No world/profile copy happens per arm or per run.

| Workflow | Use | Cost and conclusion |
| --- | --- | --- |
| `./check`, `./check --full` | Ordinary edits; portable checks | Seconds warm; no runtime claim |
| `validate --session …` | Relevant live lifecycle changes | The exercised Asahi ready-session check took 19 seconds. Tests loading, real activation, Off, Cancel and failed Apply; no speed/quality verdict |
| `compare --session …` | Development feedback | Ten seconds per arm plus selection/conditioning/restoration. Ready-session pilots took 32–48 seconds. Exploratory FPS/frame-time timelines; no retention |
| `profile --session …` | Explain timing variation | Usually 10–20 seconds capture; optional sampled CPU/GPU stages. See [profiling](profiling.md) |
| `qualify --spec …` | Frozen contribution/release evidence | Explicit fresh-session matrix and budget. Breadth, repeats, quality, sustained cases and interactions are chosen for the contribution; no universal automatic certificate |

Cold startup, shader reload and world preparation are separate costs. The one-minute loop assumes a ready client. A slow, failed or non-settling attempt stays in the evidence. Do not replace the maintained harness with a custom timing wrapper.

## Prepare and own a session

Build current classes and JAR before opening a client. Use the allocated display and host. Existing input/ownership checks refuse concurrent cooperating runs and executable drift. They do not detect every unrelated application using the GPU.

```
./check --full
python3 -m shaderlod_host session-open --launch build/moddev/functional-launch.json --profile run-client-absent-dh --world ShaderLOD-Smoke --display /run/user/1000/shaderlod-display/environment.json --ownership-root /run/user/1000/shaderlod-ownership --session /external/fresh-session --timeout 900
```

The foreground command owns the client. In another terminal:

```
python3 -m shaderlod_host session-status --session /external/fresh-session --wait-ready --timeout 180
python3 -m shaderlod_host validate --spec benchmarks/validation-asahi.json --session /external/fresh-session --output-root /external/fresh-validation
python3 -m shaderlod_host compare --spec benchmarks/comparisons/asahi-null.json --plan
python3 -m shaderlod_host compare --spec benchmarks/comparisons/asahi-null.json --session /external/fresh-session --output-root /external/fresh-comparison
python3 -m shaderlod_host session-close --session /external/fresh-session
```

The supplied null comparison exercises the harness with a dev-only identity provider. Replace its case/arm definitions deliberately for real work. The development target is about one minute, not a requirement to run a full qualification after each edit. `--plan` resolves capture counts/order and the finite budget before launching anything.

Native Windows uses the same Python CLI and control protocol. Invoke it in the logged-in interactive user's console. An SSH/WSL Session 0 process cannot stand in for that display. `benchmarks/windows/start.ps1` submits a bounded, manually started task with no recurring trigger; `session.ps1` invokes the maintained CLI. Supply a unique `ShaderLODNext-*` task name, then remove that exact quiescent registration with `finish.ps1`. This is an interactive-session bridge, not a benchmark scheduling service. `-Spec PATH` runs `campaign.ps1` instead of an open session. Its task timeout must cover the specification's total budget and teardown.

`prepare-windows` adopts a preserved native runtime into a fresh prepared slot using capacity-checked copies. `freeze-windows-runtime` preserves and relocates source-checkout-owned platform artifacts and JVM/logging configuration into a fresh external runtime snapshot. Shared JDK and Gradle artifact caches remain explicit dependencies. `prepare-profile` selects the pinned original development pack in an owned, quiescent prepared slot and preserves its prior Iris configuration externally. It does not edit shader option files, AA or resolution. Do not use setup commands on a personal play profile.

## Worlds and repeatability

A world definition identifies its initial tree and prior exposure. `prepare-world --source-root SOURCE --profile PROFILE --world NAME --spec WORLD.json --output-root EXTERNAL --ownership-root OWNERSHIP` makes a frozen original and one working copy, with capacity checks and before/after identity verification. It refuses existing destinations. Current world definitions are harness fixtures, not an unseen or representative release suite.

The controller admits only an integrated world, short routes through loaded chunks, fixed day/weather and declared native surface/dimension. Camera movement follows simulation ticks, with actual render-camera checkpoints and a progression check. A route that falls behind is invalid. Capture never slows route progression for a writer. Working worlds may still evolve through integrated-server activity; exact world identity at preparation is not bit-for-bit deterministic gameplay. Use paired blocks, independent sessions and relevant adverse cases to address residual variation. Record newly generated terrain/loading as an outcome if the mechanism affects it.

World-condition preimages are journaled before mutation. Normal close restores original weather/daylight/spawning/random-tick settings and fullscreen state. Shader selection restores its native preimage between comparisons. World conditions remain owned until close. An interrupted world journal must be recovered in that same world before another preparation; `--wait-ready` reconciles a prior journal once the integrated world loads. It does not apply a same-seed journal to a different save path.

## Contribution and release work

Freeze the question, exact candidate, scope, eligibility, settings, primary effect and meaningful gain/harm margins before confirmation. Keep development evidence distinct from confirmation; previously explored scenes are not unseen holdouts. Read [optimization direction](optimization-philosophy.md) and [definition contracts](benchmark-definitions.md).

A contribution normally compares incumbent against incumbent plus the change. Cover an opportunity-rich case, ordinary mixed play, a low-opportunity/adverse case and mechanism-specific stress. Test the development pack, a structurally different active pack and a boundary/refusal pack as appropriate. A fallback-only pack cannot establish a general speedup. Test the actual shipped combination. Four-cell original/incumbent/standalone/combined cases reveal interaction; additional reachable subsets need focused evidence or enforced eligibility/conflicts. Singleton gains cannot be added.

Qualification is not merely a longer development route. Use fixed paired blocks across fresh JVMs, targeted untimed quality captures, sustained cases and compatibility obligations within the actual activation envelope. Temporal changes need disocclusion, cuts/history reset and convergence; deferred work needs completion/backlog/recovery; preparation costs need separate reporting and a declared usage horizon for amortization. DH needs actual rendered DH work, and PBR needs actual material use. Installed/enabled flags do not prove either. Other hardware/runtimes remain separate coverage, not an inferred universal claim.

A practical starting matrix is up to three pack roles × four cases × three paired blocks × two arms × 30 seconds, or 36 minutes of capture. Reload/readiness, targeted quality, two five-minute sustained arms and a two-case interaction supplement make roughly 1.5–2.5 hours of primary contribution work; release with a distinct original/incumbent/candidate is usually longer. These are planning estimates, not fixed counts or authorization to exceed a budget. The executable campaign lists actual sessions/cases/windows and includes startup, close and lifecycle allowances. Pilot variance and sensitivity determine whether that allocation can answer the chosen margins. Too little precision remains inconclusive.

```
python3 -m shaderlod_host qualify --spec /authored/contribution.json --plan
python3 -m shaderlod_host qualify --spec /authored/contribution.json --launch LAUNCH --profile PROFILE --world WORLD --display DISPLAY --ownership-root OWNERSHIP --output-root /external/fresh-campaign
python3 -m shaderlod_host qualify --policy POLICY --runs /external/campaign/comparison-1/result.json /external/campaign/comparison-2/result.json --review REVIEW --output-root /external/fresh-assessment
python3 -m shaderlod_host history --runs /external/first/result.json /external/later/result.json --output-root /external/fresh-history
```

The tracked sensitivity campaign is a harness control, not a product acceptance policy. Confirmation freezes the independent-session count. Frames and paired windows within one JVM do not count as independent trials. The conservative median interval can be inconclusive with small samples even when a point estimate looks good. Failed/missing cases, incompatible contexts and absent quality review prevent promotion. No lack-of-significance shortcut establishes equivalence or bounded harm.

Inspect the within-route timeline, chronological session placement and cross-comparison history. Preserve baseline drift and every repeat. Histories partition incompatible contexts; they do not connect unrelated devices/settings. A capped result concerns delivered throughput. An uncapped headroom diagnostic needs a separate declared operating mode.

## Failures and closeout

Commands publish once, in sequence. A submitted command without an outcome is uncertain; never replay it. Wait for its eventual receipt or end the owned session and recover. A known terminal controller failure can be followed by an acknowledged `recover` or `close`; its original record remains. Malformed commands with unknown execution state refuse further ordinary work. Watchdog deadline/parent loss stops only the owned process group/job. Dead-owner profile artifacts recover only after host, boot and process-birth identities establish quiescence; foreign edits and missing identity refuse.

Inspect `closed.json`: process exit, unchanged executable inputs, artifact restoration and remaining journals are separate facts. An acknowledged close is not finalization by itself. Idle clients close after 120 seconds without commands; active work and the total deadline remain bounded. Do not leave an open client while editing loaded source or binaries. Input snapshots, immutable chunks, telemetry, raw observations, receipts and offline reports stay outside Git. Reports are human views of the same structured data, not substitutes for raw records.

Meaningful investigations close through [results](results.md) and `close-results`: verify claims, retain compact numerical/figure inputs, update current docs, assess code disposition and update FINDINGS. Routine runs need no new result page.
