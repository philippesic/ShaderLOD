package com.pp.shaderlod.integration;

import java.util.function.Supplier;

/** Test-only access to the exact production observation/refusal policy. */
public final class RendererBackendObservationFixture {
    private RendererBackendObservationFixture() {}
    public static void requireApplySupported(Supplier<String> observation) throws RendererBackend.Refusal {
        RendererBackend.requireApplySupported(observation);
    }
}
