# Benchmark definitions

Specifications live in `benchmarks/`. Paths to profiles, displays and artifacts are host inputs, supplied at execution; they are not portable workload identities. Run `python3 -m shaderlod_host compare --spec PATH --plan` before execution. Unknown fields and unsupported combinations refuse.

A `shaderlod-comparison-v1` definition contains:

| Field | Meaning |
| --- | --- |
| `scope` | `development`, `contribution`, or `release`; this label never grants acceptance |
| `cases` | Distinct IDs, original pack filename and SHA-256, five-coordinate `from`/`to` poses, dimension and native surface (one surface per comparison; the session switches the fullscreen mode to it and refuses if the monitor does not offer it); optional `day_time` (default 6000) and `weather` (`clear`, `rain`, `thunder`) |
| `arms` | Distinct IDs, exact module lists, and explicit `delay_ms` test control (normally zero) |
| `blocks` | Complete paired blocks within this process; not independent JVM samples |
| `window_ms`, `warmup_ms` | Up to 60 seconds per development window or 300 seconds per contribution/release window. Fixed arm-symmetric measurement and conditioning, with bounded readiness separately recorded. The warm-up must outlast the shader reload that precedes each window |
| `budget_ms` | Entire comparison, including reserved restoration time; no automatic retry |
| `seed` | Frozen randomized case/arm order; alternating block order |
| `instrument` | Optional `frames` (default), `stages`, or `memory-control` (no durable spool, observer control); diagnostics do not enter confirmation |
| `quality_cadence_ms` | Optional 100–1000 ms; zero disables images. Nonzero requires a separate development capture no longer than ten seconds |

Each route is limited to 64 blocks, starts within 64 blocks of the prepared player, and refuses unloaded chunks. Movement follows client simulation ticks at 20 Hz. The measured result must show the expected tick progression and actual player/render-camera checkpoints. Frames are not the route clock. A machine unable to maintain the declared progression produces an invalid workload, not a conveniently slowed benchmark. Build a separately declared lower-demand workload if appropriate.

Captures preserve consecutive application frame-start intervals. Throughput is completed frames divided by elapsed interval time. Each summary also states whether the window sat at the frame limiter (`frame_limit`) and how far throughput moved between its halves (`half_window_drift`); [the workflow guide](benchmarks.md#can-this-window-show-a-speed-change) explains how to read them. Tails are descriptive; frames within a process are dependent. The timeline does not measure input-to-photon latency or guarantee presented frames. GPU stage intervals remain optional diagnostics with explicit pending/lost/invalid queries and no invented zeroes.

A `shaderlod-workflow-v1` profile may declare `surface` (width and height), `pose` (five coordinates, at noon in clear weather) and `warmup_ms` (at most 300 000). The profile switches the session to that fullscreen mode, moves the player, waits while polling status, captures at that pose, and refuses a capture whose surface differs. Without them it measures whatever the session currently shows.

`shaderlod-campaign-v1` freezes an explicit `sessions` array of comparison definitions, a `policy`, startup allowance and total budget. Each session starts a new owned JVM while reusing the prepared profile/world. Policy cases and paired arms must occur in every session. Startup, comparison and close allowances must fit before anything launches. A failed session remains in the denominator; the runner does not retry until a preferred answer appears.

`shaderlod-qualification-policy-v1` contains reference and candidate IDs, fixed independent `sessions`, `confidence`, `minimum_gain`, `maximum_harm`, required `cases`, `benefit_cases`, and `quality_criteria`. All fractions are relative to reference throughput. Benefit cases must bound gain above the useful margin; other cases must bound harm below the tolerated margin. Margins and perceptual criteria are contribution decisions, not harness defaults. The order-statistic median interval uses independent session effects, with a conservative correction across declared cases. Too few independent sessions give an inconclusive interval. A larger frame count cannot fix that.

A `shaderlod-quality-review-v1` records reviewer/date, policy identity, exact comparison result identities, covered cases, `approved`, and limitations. Approval is an explicit perceptual judgment against the frozen criteria. Image variation or a matching checksum is not that judgment. Additional mechanism-specific evidence, compatibility obligations and implementation review still matter; the numerical assessment is a bounded policy result, not a universal release certificate.
