# Running benchmarks

Use the maintained `python3 -m shaderlod_host` entry point. Keep one owned client and a prepared working world through short comparisons and profiling. Start fresh JVMs for independent qualification sessions; reuse the same prepared profile and world. No world/profile copy happens per arm or per run.

| Workflow | Use | Cost and conclusion |
| --- | --- | --- |
| `./check`, `./check --full` | Ordinary edits; portable checks | Seconds warm; no runtime claim |
| `validate --session …` | Relevant live lifecycle changes | Ready-session checks took 12 seconds on RTX 2080 Ti and 19 seconds on Asahi. Tests loading, real activation, Off, Cancel and failed Apply; no speed/quality verdict |
| `compare --session …` | Development feedback | Ten seconds per arm plus an eight-second warm-up, selection and restoration: about 25 seconds per window on Asahi. Exploratory FPS/frame-time timelines; no retention |
| `profile --session …` | Explain timing variation | Usually 10–20 seconds capture; optional sampled CPU/GPU stages. See [profiling](profiling.md) |
| `qualify --spec …` | Frozen contribution/release evidence | Explicit fresh-session matrix and budget. Breadth, repeats, quality, sustained cases and interactions are chosen for the contribution; no universal automatic certificate |

Execution commands return 0 for a complete/valid workflow, 1 for failed or incomplete work, and 2 for refused input. Campaign pause returns 3. Offline `qualify --policy` returns 1 when its recommendation is `do_not_promote`, including an otherwise valid inconclusive or slow result; that is not permission to retry until it passes.

Cold startup, shader reload and world preparation are separate costs. The one-minute loop assumes a ready client. A slow, failed or non-settling attempt stays in the evidence. Do not replace the maintained harness with a custom timing wrapper.

## Can this window show a speed change?

Every capture summary carries two readouts. Read them before the FPS.

`frame_limit` compares the median frame interval with the game's frame limiter and vsync. `limited` means the window sat at that limit: faster rendering cannot raise its throughput, so it cannot show a speedup, and a slowdown read from it is a lower bound. `unobserved` means the limiter state was not recorded. `qualify --policy` promotes neither. A comparison result counts affected windows in its own `frame_limit` field. A benchmark session sets the game's frame limiter to Unlimited and vsync off when it prepares the client, and restores both at close; the profile's saved options are not rewritten. A window that still reads `limited` is being capped outside the game, for example by a driver setting. Before that control existed, the profiles' `maxFps:60` bound every retained RTX 2080 Ti window at 1024×768 ([F48](../FINDINGS.md)). Speed also needs a surface where the GPU is the bottleneck.

`half_window_drift` is the throughput change from the first half of a window to the second. Selecting an arm reloads the shader pipeline, and on Asahi a window opened one second later ran about 8% fast for four to five seconds before settling. The tracked development comparisons therefore wait eight seconds. Keep `warmup_ms` long enough to cover the reload on the machine in use, and treat a large drift on a fixed or slowly panning scene as an unsettled window, not an effect. A route through changing scenery drifts for legitimate reasons: compare its arms, not its halves.

Asahi held no-change pairs within about 0.25% for about the first two minutes of sustained capture and became erratic afterwards. Keep its comparisons short and let it rest between sessions; a long campaign there needs its own stability evidence first.

## Prepare and own a session

Build current classes and JAR before opening a client. Use the allocated display and host. Existing input/ownership checks refuse concurrent cooperating runs and executable drift. They do not detect every unrelated application using the GPU.

```
./check --full
JAVA_HOME="$PWD/.local/tools/jdks/jdk-21.0.13+9" ./gradlew --no-daemon writeFunctionalLaunch -PfunctionalProbe
python3 -m shaderlod_host session-open --launch build/moddev/functional-launch.json --profile run-client-absent-dh --world ShaderLOD-Smoke --display .local/display/environment.json --ownership-root .local/ownership --session .local/artifacts/fresh-session --timeout 900
```

The foreground command owns the client. In another terminal:

```
python3 -m shaderlod_host session-status --session .local/artifacts/fresh-session --wait-ready --timeout 180
python3 -m shaderlod_host validate --spec benchmarks/validation-asahi.json --plan
python3 -m shaderlod_host validate --spec benchmarks/validation-asahi.json --session .local/artifacts/fresh-session --output-root .local/artifacts/fresh-validation
python3 -m shaderlod_host compare --spec benchmarks/comparisons/asahi-null.json --plan
python3 -m shaderlod_host compare --spec benchmarks/comparisons/asahi-null.json --session .local/artifacts/fresh-session --output-root .local/artifacts/fresh-comparison
python3 -m shaderlod_host session-close --session .local/artifacts/fresh-session
```

The supplied null comparison exercises the harness with a dev-only identity provider. Replace its case/arm definitions deliberately for real work. The development target is about one minute, not a requirement to run a full qualification after each edit. `--plan` resolves capture counts/order and the finite budget before launching anything.

Native Windows uses the same Python CLI and control protocol. Invoke it in the logged-in interactive user's console. An SSH/WSL Session 0 process cannot stand in for that display. `benchmarks/windows/start.ps1` submits a bounded, manually started task with no recurring trigger; `session.ps1` invokes the maintained CLI. Supply a unique `ShaderLOD-*` task name, then remove that exact quiescent registration with `finish.ps1`. This is an interactive-session bridge, not a benchmark scheduling service. `-Spec PATH` runs `campaign.ps1` instead of an open session. Its task timeout must cover the specification's total budget and teardown.

`prepare-windows` adopts a preserved native runtime into a fresh prepared slot using capacity-checked copies. `freeze-windows-runtime` preserves and relocates source-checkout-owned platform artifacts and JVM/logging configuration into a fresh `.local/artifacts` runtime snapshot. The target checkout’s local JDK and Gradle artifact caches remain explicit dependencies. `prepare-profile` selects the pinned original development pack (or an explicit original-pack `--spec`) in an owned, quiescent prepared slot and preserves its prior Iris configuration in the local artifact output. It does not edit shader option files, AA or resolution. Do not use setup commands on a personal play profile.

## Worlds and repeatability

A world definition identifies its initial tree and prior exposure. `prepare-world --source-root SOURCE --profile PROFILE --world NAME --spec WORLD.json --output-root .local/artifacts/FRESH --ownership-root OWNERSHIP` makes a frozen original and one working copy, with capacity checks and before/after identity verification. It refuses existing destinations. Current world definitions are harness fixtures, not an unseen or representative release suite.

The controller admits only an integrated world, short routes through loaded chunks, fixed day/weather and declared native surface/dimension. The prepared player must support creative flight. The controller holds flight, gravity and velocity during conditioning and routes; it journals their preimages and restores them with the original player pose at close. It never changes game mode. Camera movement follows simulation ticks, with actual render-camera checkpoints and a progression check. A route that falls behind is invalid. Capture never slows route progression for a writer. Working worlds may still evolve through integrated-server activity; exact world identity at preparation is not bit-for-bit deterministic gameplay. Use paired blocks, independent sessions and relevant adverse cases to address residual variation. Record newly generated terrain/loading as an outcome if the mechanism affects it.

World-condition preimages are journaled before mutation. Normal close restores original weather/daylight/spawning/random-tick settings, player motion/pose, fullscreen state and video mode, the frame limiter and vsync. Shader selection restores its native preimage between comparisons. World conditions remain owned until close. An interrupted world journal must be recovered in that same world before another preparation; `--wait-ready` reconciles a prior journal once the integrated world loads. It does not apply a same-seed journal to a different save path.

## Contribution and release work

Freeze the question, exact candidate, scope, eligibility, settings, primary effect and meaningful gain/harm margins before confirmation. Keep development evidence distinct from confirmation; previously explored scenes are not unseen holdouts. Read [optimization direction](optimization-philosophy.md) and [definition contracts](benchmark-definitions.md).

A contribution normally compares incumbent against incumbent plus the change. Cover an opportunity-rich case, ordinary mixed play, a low-opportunity/adverse case and mechanism-specific stress. Test the development pack, a structurally different active pack and a boundary/refusal pack as appropriate. A fallback-only pack cannot establish a general speedup. Test the actual shipped combination. Four-cell original/incumbent/standalone/combined cases reveal interaction; additional reachable subsets need focused evidence or enforced eligibility/conflicts. Singleton gains cannot be added.

Qualification is not merely a longer development route. Use fixed paired blocks across fresh JVMs, targeted untimed quality captures, sustained cases and compatibility obligations within the actual activation envelope. Temporal changes need disocclusion, cuts/history reset and convergence; deferred work needs completion/backlog/recovery; preparation costs need separate reporting and a declared usage horizon for amortization. DH needs actual rendered DH work, and PBR needs actual material use. Installed/enabled flags do not prove either. Other hardware/runtimes remain separate coverage, not an inferred universal claim.

A practical starting matrix is up to three pack roles × four cases × three paired blocks × two arms × 30 seconds, or 36 minutes of capture. Reload/readiness, targeted quality, two five-minute sustained arms and a two-case interaction supplement make roughly 1.5–2.5 hours of primary contribution work; release with a distinct original/incumbent/candidate is usually longer. These are planning estimates, not fixed counts or authorization to exceed a budget. The executable campaign lists actual sessions/cases/windows and includes startup, close and lifecycle allowances. Pilot variance and sensitivity determine whether that allocation can answer the chosen margins. Too little precision remains inconclusive.

```
python3 -m shaderlod_host qualify --spec benchmarks/contributions/NAME.json --plan
python3 -m shaderlod_host qualify --spec benchmarks/contributions/NAME.json --launch LAUNCH --profile PROFILE --world WORLD --display DISPLAY --ownership-root OWNERSHIP --output-root .local/artifacts/fresh-campaign
python3 -m shaderlod_host qualify --policy POLICY --runs .local/artifacts/campaign/comparison-1/result.json .local/artifacts/campaign/comparison-2/result.json --review REVIEW --output-root .local/artifacts/fresh-assessment
python3 -m shaderlod_host history --runs .local/artifacts/first/result.json .local/artifacts/later/result.json --output-root .local/artifacts/fresh-history
```

The tracked sensitivity campaign is a harness control, not a product acceptance policy. Confirmation freezes the independent-session count. Frames and paired windows within one JVM do not count as independent trials. The conservative median interval can be inconclusive with small samples even when a point estimate looks good. Failed/missing cases, incompatible contexts and absent quality review prevent promotion. No lack-of-significance shortcut establishes equivalence or bounded harm.

For a long campaign, `--control PATH` accepts an explicit `{"action":"pause","after_sessions":N}` request only at a completed, closed session boundary. Exit 3 means paused; it is not failure or completion. Resume with the same command/output plus `--resume`, omitting the old pause request. Executed source, profile, world, protocol and completed prefix must match. Unknown/failed admitted work is not replayed, and spent reservations are not refunded. Verify the finished campaign directly with `verify --result .local/artifacts/campaign/result.json`; assess it with `qualify --policy POLICY --result .local/artifacts/campaign/result.json --review REVIEW --output-root .local/artifacts/fresh-assessment`.

`installation --spec benchmarks/comparisons/asahi-installation-makeup.json --launch LAUNCH --profile PROFILE --world WORLD --display DISPLAY --ownership-root OWNERSHIP --output-root .local/artifacts/fresh-installation` compares a genuinely absent main mod against installed-Off in separate processes. Prepare that exact original pack first. Common measurement helpers remain in both arms; this isolates registration/mixin overhead, not absence of every shared class. Native pack failure is a compatibility outcome, not permission to reduce quality. This control is separate from ordinary candidate comparisons.

After a campaign, `report --result .local/artifacts/campaign/result.json --output-root .local/artifacts/fresh-campaign-report` builds an offline view of the recorded policy decision and chronological compatible-series charts. It performs no new measurement or approval.

Inspect the within-route timeline, chronological session placement and cross-comparison history. Preserve baseline drift and every repeat. Histories partition incompatible contexts; they do not connect unrelated devices/settings. Sessions run with the frame limiter off, so results describe rendering headroom, not throughput delivered under a player's cap.

## Failures and closeout

Commands publish once, in sequence. A submitted command without an outcome is uncertain; never replay it. Wait for its eventual receipt or end the owned session and recover. A known terminal controller failure can be followed by `session-recover --session PATH` or `session-close --session PATH`; its original record remains. Malformed commands with unknown execution state refuse further ordinary work. Watchdog deadline/parent loss stops only the owned process group/job. Dead-owner profile artifacts recover only after host, boot and process-birth identities establish quiescence; foreign edits and missing identity refuse.

Inspect `closed.json`: process exit, unchanged executable inputs, artifact restoration and remaining journals are separate facts. An acknowledged close is not finalization by itself. Idle clients close after 120 seconds without commands; active work and the total deadline remain bounded. Do not leave an open client while editing loaded source or binaries. Working input snapshots, immutable chunks, telemetry, raw observations, receipts and offline reports stay outside Git. HTML reports alone are not supporting numerical evidence.

Investigation closeout follows the [result specification](results.md): extract and verify compact supporting data and figure-generation inputs into results/, then remove superseded bulky outputs within scope. Runtime receipts and offline verify/report commands require their referenced files; do not present a compact result as an intact runtime bundle after deleting those files. Retain only what the result's claims still require, rather than every generated output.

Meaningful investigations close through [results](results.md) and `close-results`: verify claims, retain compact numerical/figure inputs, update current docs, assess code disposition and update FINDINGS. Routine runs need no new result page.
