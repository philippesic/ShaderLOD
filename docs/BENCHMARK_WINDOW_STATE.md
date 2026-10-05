# Benchmark window observations

`WorkloadSession` now adds `window_state` to its report. The read-only tracker
samples once per existing workload tick during warmup and capture: three GLFW
window attributes (`FOCUSED`, `ICONIFIED`, `VISIBLE`) plus cached Minecraft window
dimensions, framebuffer dimensions and pacing options. It does not query GL,
read pixels, pump events, invoke host commands, or alter focus, visibility,
resolution, pacing or shader settings. No new render/pass hook is added.

This can distinguish observed focus/visibility/size changes from apparently
matched endpoints. It does not establish a cause for frame-rate differences.
GLFW visibility does not prove that another window is not covering the game,
and these observations do not measure host clock frequencies, thermal state,
GPU utilization or window-server scheduling.

## Report schema

`window_state.schema` is `1`; `source` and `note` describe the observation method
and limits. `warmup` and `capture` each contain:

- `observations`, `available_observations`, `unavailable_observations`.
- `start` and `end`: first/last observed boundary, containing
  `elapsed_workload_seconds`, `frame_id` and `state`. No observations leaves these
  null/absent; a missing phase is not a confirmed stable window.
- `focused`, `iconified`, `visible`: each has `true_observations`,
  `false_observations`, `unknown_observations`. Unknown is separate from false.
- `window_size_counts` and `framebuffer_size_counts`: maps of `WIDTHxHEIGHT` to
  observation counts; nonpositive cached dimensions use `unknown_or_nonpositive`.
- `transition_counts`: counts by changed field, independently of the detail cap.
- `transitions`: up to 128 chronological changes, each with
  `elapsed_workload_seconds`, `frame_id`, `changed_fields`, `from`, `to`.
  Further detail events increment `dropped_transitions`; aggregate counts remain.
- `unavailable_reasons` and `max_observation_gap_seconds`.

Boundary and transition `state` fields:

```json
{
  "availability": "available",
  "focused": "true",
  "iconified": "false",
  "visible": "true",
  "window_width": 1280,
  "window_height": 720,
  "framebuffer_width": 2560,
  "framebuffer_height": 1440,
  "pacing": {
    "pause_on_lost_focus": false,
    "fullscreen": false,
    "configured_framerate_limit": 260,
    "vsync": false
  },
  "note": "GLFW attributes and cached Minecraft sizes"
}
```

Flags are strings `true`, `false`, or `unknown`. A missing handle or unavailable
GLFW call reports `availability: unavailable`, explicit unknown flags and a
reason; it does not manufacture an unfocused/hidden state. Cached dimensions and
options can still be present when native attributes are unavailable. Fullscreen
is Minecraft's window flag; the frame-rate value is the configured option, not
the actual rate or proof that an OS limiter is inactive. The tracker does not
read GLFW's error queue or infer success from timing counters.

At the warmup→capture tick, the same single window read becomes the capture start
sample. It is not counted twice. First and last samples bracket observed ticks,
not every rendered frame. Times are relative to workload construction and use
the existing workload monotonic timestamp; no extra CPU/GL timing queries are
introduced. Normal sampling is roughly the existing client tick rate. A brief
focus/size change between ticks can be missed; a pause can leave a sampling gap.
Counts are observation counts, not elapsed-time fractions. A stationary cached
framebuffer size while iconified is not proof of active rendering.

The report addition is telemetry only. It does not introduce benchmark rejection
rules or retroactively classify older reports without `window_state`. The
benchmark assessment worker and coordinator own exclusion/interpretation policy.

## Checks and runtime evidence

`WindowStateTrackerTest` uses pure synthetic observations; it never calls GLFW
or creates a window. It checks separate warmup/capture counts, known/unknown
flags, logical versus framebuffer dimensions, pacing and size transitions,
phase boundaries, observation gaps, immutable snapshots, backward-clock rejection
and the 128-detail cap while retaining all transition counts.

The tracker and fixture compile/run independently against the pinned LWJGL Java
classpath. The coordinator owns the full development build and subsequent
runtime verification. Successful fixture checks do not establish actual GLFW
state sampling or overhead in a running game; record those separately once a
completed workload report contains the new field.
