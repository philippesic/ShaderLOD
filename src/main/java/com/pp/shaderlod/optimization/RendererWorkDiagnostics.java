package com.pp.shaderlod.optimization;

import java.util.Map;

/** Explicitly armed client-thread diagnostics; command entries are not GL draw calls. */
public final class RendererWorkDiagnostics {
    public static final boolean ENABLED = Boolean.getBoolean("shaderlod.dev.rendererWorkCounters");
    private static boolean armed;
    private static long compactionEligibleBatches, commandEntriesBefore, commandEntriesAfter,
            compactionReducedBatches, invalidCompactionMetadata,
            scalarAdmittedQueries, scalarHits, scalarMisses, scalarAvoidedScanEntries;

    private RendererWorkDiagnostics() {}

    /** Begin a new window, discarding all preceding counts. Client/render thread only. */
    public static void beginWindow() {
        if (!ENABLED) return;
        compactionEligibleBatches = commandEntriesBefore = commandEntriesAfter = 0;
        compactionReducedBatches = invalidCompactionMetadata = 0;
        scalarAdmittedQueries = scalarHits = scalarMisses = scalarAvoidedScanEntries = 0;
        armed = true;
    }

    /** Idempotent; retain the completed window for explicit reporting. */
    public static void endWindow() { armed = false; }

    /** Called only after an eligible consumer has actually returned from compaction. */
    public static void compactionReturned(int before, int after) {
        if (!ENABLED || !armed) return;
        compactionEligibleBatches++;
        if (before < 0 || after < 0 || after > before) {
            invalidCompactionMetadata++;
            return;
        }
        commandEntriesBefore += before;
        commandEntriesAfter += after;
        if (after < before) compactionReducedBatches++;
    }

    /** Admission means the existing non-shadow/runtime gate passed, not that reuse succeeded. */
    public static void scalarQuery(boolean hit, int nativeSize) {
        if (!ENABLED || !armed) return;
        scalarAdmittedQueries++;
        if (hit) {
            scalarHits++;
            if (nativeSize > 0) scalarAvoidedScanEntries += nativeSize;
        } else {
            scalarMisses++;
        }
    }

    /** Allocation occurs only at the caller's report boundary, outside rendering. */
    public static Map<String, Object> snapshot() {
        return Map.ofEntries(
            Map.entry("enabled", ENABLED), Map.entry("armed", armed),
            Map.entry("compaction_eligible_batches", compactionEligibleBatches),
            Map.entry("command_entries_before", commandEntriesBefore),
            Map.entry("command_entries_after", commandEntriesAfter),
            Map.entry("command_entries_removed", commandEntriesBefore - commandEntriesAfter),
            Map.entry("compaction_reduced_batches", compactionReducedBatches),
            Map.entry("invalid_compaction_metadata", invalidCompactionMetadata),
            Map.entry("scalar_admitted_queries", scalarAdmittedQueries),
            Map.entry("scalar_hits", scalarHits), Map.entry("scalar_misses", scalarMisses),
            Map.entry("scalar_avoided_native_scan_entries", scalarAvoidedScanEntries),
            Map.entry("scope", "Client-thread window; command entries are not GL draw calls; counters do not establish speed"));
    }
}
