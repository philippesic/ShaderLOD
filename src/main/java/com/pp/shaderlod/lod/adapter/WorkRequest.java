package com.pp.shaderlod.lod.adapter;

import com.pp.shaderlod.lod.EffectPolicy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime inputs. Invalid policy/work/count/distance inputs are handled by full-work fallback. */
public record WorkRequest(AdapterIdentity adapter, Map<String, Integer> protocolVersions, WorkKey work,
                          EffectPolicy policy, boolean globalEnabled, double distanceBlocks,
                          int upstreamWork, int minimumRetainedWork) {
    public WorkRequest {
        // Missing/null runtime versions must reach negotiation's full-work fallback.
        protocolVersions = protocolVersions == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(protocolVersions));
    }
}
