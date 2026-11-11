# Frame capture development

The first frame-capture path is implemented and exercised on Asahi. It is a development facility: comparison execution, repeatable routes, pass mappings and qualification are still being implemented in [the benchmark task](../tasks/benchmark-design.md). A valid stream means its frame sequence and clocks agree; it does not qualify a performance claim.

## Planning and reporting

```
python3 -m shaderlod_host profile --spec benchmarks/profiles/frame.json --plan
python3 -m shaderlod_host compare --spec benchmarks/comparisons/development.json --plan
python3 -m shaderlod_host report --frames /external/frames.jsonl --output-root /external/fresh-report
python3 -m shaderlod_host verify --result /external/fresh-capture/receipt.json
```

The preliminary strict specification identifies scope, workload label, arm labels, blocks, measured window, total budget and instrumentation. It forecasts capture time only. The workload label does not establish a controlled scene; live route and arm admission remain outstanding. Execution of comparisons explicitly refuses until that path exists. Existing CPU requests retain their original contracts.

Reports contain frame-time and FPS views with selectable time bounds, raw spikes, validity and structured analysis. FPS uses completed frames over elapsed time; missing frames or changed generations prevent a complete-stream FPS claim. Imported observations are explicitly unqualified. Raw records remain beside the derived report. A fresh output directory is required. Published files refuse overwrite.

## Reusing a Linux client

Use an allocated display, prepared writable profile and current launch manifest. Do not start another client on the same GPU. Native Windows supervision is not implemented yet. Never edit loaded classes, resources, dependency JARs or host source while the session is active: subsequent commands refuse executable drift. Keep the foreground session command running in its owning terminal.

```
python3 -m shaderlod_host session-open --launch build/moddev/functional-launch.json --profile run-client-absent-dh --display /run/user/1000/shaderlod-display/environment.json --ownership-root /run/user/1000/shaderlod-ownership --session /external/fresh-session --timeout 240
python3 -m shaderlod_host session-status --session /external/fresh-session
python3 -m shaderlod_host profile --spec benchmarks/profiles/frame.json --session /external/fresh-session --output-root /external/fresh-capture
python3 -m shaderlod_host session-close --session /external/fresh-session
```

The prepared profile needs the existing ShaderLOD-Smoke world. A status result must show readiness before capture. The session uses the existing worker/watchdog and GPU advisory lock; its bounded lifetime remains authoritative. The close acknowledgement precedes game shutdown; inspect closed.json for final process completion. The watchdog forcibly stops its owned client on timeout or parent loss. This initial fixed-scene path performs no candidate selection or world reset; its writable world is retained. It does not promise exact world restoration after forced termination.

Commands are sequential and published once. A timed-out command is uncertain: inspect its eventual result or failure record before proceeding. The host refuses a second command while the previous outcome is missing. A controller failure stops further command processing. Receipt and log files are preserved externally.

Frame collection uses consecutive render-frame entry timestamps and a finite 200,000-frame buffer. Overflow and shader-generation changes invalidate the capture; it never rolls old samples away. The buffer is published after the timed window. Durable partial streams on crashes are outstanding, so an interrupted capture is incomplete. Default diagnostic GPU queries are disabled in this frame-only path. Observer overhead, presentation interpretation and stable workload conditions remain to be qualified.
