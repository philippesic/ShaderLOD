package com.pp.shaderlod.integration;

import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.lod.adapter.WorkKey;
import com.pp.shaderlod.lod.adapter.WorkKind;

/** Shared operation identities; explicit adapters declare their own legal domains for each. */
public final class LodOperations {
    public static final WorkKey VOLUME_INTEGRATION = new WorkKey(Effect.VOLUMETRICS,
            "shaderlod:volume_integration", WorkKind.COUNT);

    private LodOperations() {}
}
