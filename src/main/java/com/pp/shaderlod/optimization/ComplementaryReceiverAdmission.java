package com.pp.shaderlod.optimization;

import java.util.function.BooleanSupplier;

/** Cheap refusal before source I/O; eligible draws always run their fresh proof. */
public final class ComplementaryReceiverAdmission {
    private ComplementaryReceiverAdmission() {}

    public static boolean spatialActive(boolean enabled, boolean requested, String loaded) {
        return enabled && requested && "1".equals(loaded);
    }

    public static boolean validate(String shadowQuality, boolean activeSpatial, BooleanSupplier freshProof) {
        return "2".equals(shadowQuality) && !activeSpatial && freshProof.getAsBoolean();
    }
}
