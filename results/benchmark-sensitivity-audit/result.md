# The retained benchmark controls could not show a speedup, and two harness causes are now fixed

An audit asked whether the benchmark can detect the size of effect an optimization would produce. On the primary RTX 2080 Ti it could not: every retained window without an injected delay sat at the game's 60 FPS frame limiter. On Asahi it could, briefly: once the warm-up covers the shader reload, unchanged shaders agree to about a quarter of a percent, for about the first two minutes of sustained capture.

| Question | Observation |
| --- | --- |
| Can the retained RTX 2080 Ti setup show a speedup? | No. All 20 windows without an injected delay were frame-limited (59.5–59.8 FPS at a limit of 60). The GPU spans in a staged capture summed to 5.7 ms of a 16.7 ms frame |
| What did its sensitivity control establish? | That a 100 ms per-frame delay is rejected. That is an 85% slowdown measured from a limited reference, so a lower bound; no smaller effect and no speedup was exercised |
| Why did Asahi no-change pairs disagree by 0.4–1.9%, with outliers of −15% and +65%? | Windows opened one second after a shader reload. A fixed scene held 21.2–21.3 FPS for 20 seconds, while a post-reload window began near 22.6 FPS and took four to five seconds to reach 21.0. The two outliers came from four-second windows running at 9–17 FPS, well below the settled rate; their cause was not recorded |
| Does a longer warm-up fix it? | Yes, for a while. With eight seconds, the first two pairs differed by −0.02% and −0.23%. The next two, from about two minutes in, differed by −1.76% and +3.12% as throughput fell from 21.0 to 19.2 FPS and its worst frame grew from 52 to 121 ms |
| Is the late degradation the Java heap ceiling? | Not established. The heap reached its 1408 MiB default ceiling as degradation began, but a session with a pinned 2560 MiB heap was erratic from its first window (pairs −7.3%, +2.3%, +1.9%, +1.7%) |

## What changed

Capture summaries now state `frame_limit` and `half_window_drift`. `qualify --policy` refuses to promote frame-limited or unrecorded-limit throughput. The tracked timing comparisons wait eight seconds after selection instead of one. [Benchmark guide](../../docs/benchmarks.md#can-this-window-show-a-speed-change).

## Conditions and limitations

Evidence level: live-functional plus bounded live measurement controls on Asahi; source-inspected for the retained RTX 2080 Ti records. The retained records are the closed [benchmark control corpus](../benchmark-workflows/result.md). The live sessions used the Apple M1 MacBook Air (8 GB, fanless) at 2560×1600, original Complementary Reimagined r5.9.3 without Distant Horizons, the `coast-pan` case, four alternating blocks of original against the identity provider, ten-second windows and an eight-second warm-up.

This is one scene, one pack and two sessions. Four pairs per session are not independent trials. The pinned-heap session started about four minutes after the first one ended, so its poor result may reflect a warm machine or memory pressure instead of the heap setting; the experiment does not separate them. No temperature sensor other than the battery's was readable. The RTX 2080 Ti was not run: its checkout has no prepared game client, and its limiter state is read from retained records. Its speed sensitivity with the limiter off is unknown, and at 1024×768 the GPU may not be the bottleneck even then.

The pinned-heap session ended with a JVM segmentation fault in compiled Minecraft server-tick code after its eight windows, before restoration. A fresh session on the same world restored the pending shader and world journals and closed cleanly. One crash does not establish a cause.

## Decision

Revise the measurement setup; retain the two readouts and the longer warm-up. Before optimization work relies on a number:

- RTX 2080 Ti needs the frame limiter off and a surface where the GPU is the bottleneck, then an unchanged-shader control that shows the size of effect it can resolve.
- Asahi supports short comparisons on a rested machine. Sustained campaigns there are unresolved until the late degradation is explained; a repeat of the default-heap session after a measured rest would separate heat from memory.
- F45's qualification of the workflow stands for lifecycle, recovery and slowdown rejection. It does not cover speedup sensitivity on either machine.

## Follow-up on the RTX 2080 Ti, 2026-11-12

Later the same day the 2080 Ti was given a virtual 2560×1440 display and a freshly prepared native Windows client, and sessions began turning the frame limiter off themselves. That removes the limiter problem above and supplies the unchanged-shader control this result asked for.

| Check | Observation |
| --- | --- |
| Uncapped throughput, original Reimagined, no DH | 217.7 FPS over 20 seconds, between 212.6 and 223.3 FPS in every second; frame limit reads unlimited |
| Unchanged shaders: original against the identity copy, three alternating pairs | −0.07%, +0.43% and +0.05%; every window drifted under 1% between its halves |
| Where the time goes | Per sampled frame the GPU intervals summed to 4.8 ms and the render thread to 4.9 ms against a 4.5 ms median frame, so CPU and GPU are close to balanced at this resolution. Deferred, composite and final passes held 58% of GPU time |
| Reimagined with Distant Horizons 3.3.3 | Links and renders without fallback, which Asahi cannot do. 168.7 FPS; the `Terrain solid` group grew from 0.52 to 1.50 ms, which is where DH terrain is currently counted |
| Functional lifecycle validation at 2560×1440 | PASS in 13.3 seconds: activation, Off, Cancel and failed-Apply restoration |

Limits: one scene and one session per check. Three pairs inside one process are not independent trials; a campaign of fresh sessions is still needed before a small effect is claimed. With CPU and GPU balanced, a GPU saving will show less than its full size; 3840×2160 is available on the virtual display. The DH check used an ad-hoc copy of the prepared profile with DH's existing configuration, and how much DH terrain was drawn was not verified. A virtual display has no physical scan-out. [Compact measurements](rtx2080ti-follow-up.json) sha256:153fcbad6aa563433e03bd7713162dbcb83c7743dfee9e2fdd3b4cc88befac8d

## 4K and fresh-session controls on the RTX 2080 Ti, 2026-11-13

The benchmark surface moved to 3840×2160, with 2560×1440 as a secondary check, and sessions now select that fullscreen mode themselves. Both prepared slots, without and with Distant Horizons, were rebuilt through the maintained `sync-windows` and `prepare-windows-dh` commands and passed lifecycle validation at 3840×2160. Each campaign below ran six fresh game processes, each comparing the original pack with the identity provider in alternating blocks.

| Check | Observation |
| --- | --- |
| Throughput at 3840×2160 | 113–116 FPS without DH, about half the 216 FPS at 2560×1440, so the GPU now limits the frame. With DH, 92.6 FPS in one window. No window was frame-limited |
| Six sessions, four blocks, 3840×2160 | Session effects −0.017% to −0.10%; interval for the median, at 96.9% coverage, −0.10% to −0.017%. Single pairs ranged from −0.66% to +0.35% |
| Six sessions, four blocks, 2560×1440 | Session effects −0.16% to +0.06%; interval −0.16% to +0.06%, which contains zero. Single pairs ranged from −0.85% to +0.45% |
| Six sessions, three blocks, 3840×2160 | Session effects −0.05% to −0.19%, every one negative, with pairs alternating in sign by block |

Throughput fell in every session, by about 1.3% over the first two minutes and fastest at the start. With three blocks the identity arm ran later in two of them, which explains the uniformly negative three-block result. `qualify --policy` now refuses to promote sessions whose arms did not lead equally often, and the tracked no-change campaigns use four blocks. Four blocks cancel a steady decline but not one that slows down, and every four-block 4K session began with a pair near −0.4%. The remaining −0.05% at 4K is that residual or a real cost of the identity provider; this experiment does not separate them.

What the machine resolves: six fresh sessions bound the median effect to an interval about 0.1 percentage points wide at 4K and 0.2 points wide at 1440p, an order of magnitude finer than the 1% minimum gain used by the tracked policies. A single pair varies by up to about ±0.6% at 4K and ±0.9% at 1440p. Treat an effect smaller than about 0.1% as unresolved at either surface.

A first four-block 4K campaign stopped after its first session: eight windows at about 25 seconds each, including the shader reload at 4K, exceeded the 220-second session budget, and the eighth window timed out. That session closed and restored cleanly; the budget is now 300 seconds. The 1440p campaign used 220 seconds and completed. All runs are kept in the record.

Limits: one scene, one pack and one machine state; the DH figure is one window, and how much DH terrain was drawn is unverified. The decline over a session is not explained; heat is likely but unmeasured. The 4K and 1440p campaigns ran back to back, not interleaved. The frozen runtime passed log4j a `C:/` path that it reads as a URL, so these clients logged with the default configuration; `freeze-windows-runtime` now writes a file URI, and the existing snapshot is unchanged. [Compact measurements](rtx2080ti-4k-readiness.json) sha256:b9b2fea336533e33d06627372e5c0b654b96713d0f6160c01be3f99613881466

## Evidence and executed source

[Compact measurements](result.json) sha256:db012a2d77895114d35ea4d729106ec85db9ec51bf9bdc9eef05fc1d56a30202

The record holds every live window's throughput, drift, per-second rate, frame-time percentiles, selection time and heap state; the pair effects; each comparison's result and protocol digests; the retained-corpus classification; and the crash summary. Raw session directories were deleted after extraction, so per-frame reanalysis is unavailable. Executed source and the uncommitted heap experiment are identified in the record.
