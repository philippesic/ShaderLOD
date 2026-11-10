# Selected source disposition

This is a structural refactor of selected behavior. The old repositories remain read-only references and are not runtime dependencies.

| Historical responsibility | New owner | Disposition and evidence |
| --- | --- | --- |
| PackSelectionService archive identity/path checks | storage.PackArchive | Reimplemented bounded ZIP/member admission, exact archive+member hashes, immutable original, deterministic derived output; traversal/duplicate/size and byte-vector CPU tests |
| PackSelectionTransaction selection/queued options/restore | engine.SelectionTransaction + integration.IrisSelection | Refactored native optional selection/enabled/queue/effective snapshot; durable preimage/intended-write journal; original queue must be empty at live admission; CPU state-machine recovery tests; actual Iris serialization is journaled through required pinned mixins; absent-DH live UI/native settings restoration passed; required DH graph remains blocked |
| OptimizationEditTransaction and central candidate enums | engine.OptimizationRegistry + OptimizationProvider | Replaced with ServiceLoader discovery, explicit applicability/conflicts/preimage checks; plant/weed child-classloader tests and external packaged-JAR plant/weed round trip; no old candidate imported |
| Iris reload/fallback and option observation | integration.IrisSelection | Compiled against pinned Iris APIs; requested/prepared/observed/restored are distinct; loaded archive/generation and later submitted-frame witnesses are independently joined; packaged no-DH receipt retained |
| DH presence/config | integration.DhObservation + DhApiObservation | Optional class boundary; read-only delayed API observation; absence and configured/enabled values do not prove rendered coverage |
| run_storage capacity/reclaim/process ownership | shaderlod_host storage/runtime/worker | Narrow fresh implementation; classified owned file reclaim, dependency guards, atomic receipts, source pinning, watchdog and cumulative attempts tested on Linux; Windows supervision explicitly unavailable |
| Bounded profiler buffers | devClient BoundedProfiler/DevelopmentProfiler | Bounded CPU frame collector and nonblocking GPU timestamp companion with frame/generation ownership, pending/loss/invalid health and diagnostic export; per-pass coverage and comparison methodology remain unavailable |
| Old dev SamplerUnitBudgetMixin | integration.SamplerIndexPolicy + required native mixin | Necessary binding-index compatibility ported; resource count and combined index namespace remain distinct; no-DH native linking corrected without changing shader bytes/options; DH fragment resource limit remains |
| Native Iris property/option serialization | storage.NativeWriteScope + required native writer mixins | Exact bytes/deletions announced before atomic mutation; foreign edits refuse; CPU native-writer and live failed-reload/Off restoration proofs |
| Active renderer observation | integration.FrameWitness + engine.SelectionTransaction | Required pinned final-level-render return; LINKED before ACTIVE; stale generation/context invalidates; no GPU-completion or visual-equivalence claim |
| Parked optimization graph, CompileOptionControl, campaign launchers, auto-tuning | Old repository only | Intentionally excluded; no gains or historical qualification transferred |

Static source and synthetic tests verify structural properties and selected independent contracts. They do not establish full historical behavior parity or live package D. Changed or unsupported semantics stay explicit rather than silently importing native defects.
