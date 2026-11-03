package com.pp.shaderlod.dev;

import java.util.Properties;

/** Launch-fixed diagnostic only. Never changes Iris's internal timer or AA frame counter. */
public final class ShaderAnimationClock {
    public static final String PROPERTY = "shaderlod.dev.fixedShaderAnimationTime";
    public static final String SCOPE = "Diagnostic frameTimeCounter getter only; normal Iris uniform upload; "
            + "frameTime, real clocks and AA frameCounter remain native; no gameplay/visual-equivalence claim";
    private static final Configuration LAUNCH = parse(System.getProperties());

    private ShaderAnimationClock() {}

    // Called by the dev mixin plugin at bootstrap, before any timed phases.
    public static boolean fixed() { return LAUNCH.fixed(); }
    public static Float fixedValue() { return LAUNCH.value(); }
    public static String source() { return fixed() ? PROPERTY + " (launch-fixed)" : "native Iris Timer getter"; }

    record Configuration(boolean fixed, Float value) {}

    static Configuration parse(Properties properties) {
        String raw = properties.getProperty(PROPERTY);
        if (raw == null) return new Configuration(false, null);
        if (properties.getProperty("shaderlod.dev.benchmarkTunerProbe", "").isBlank())
            throw new IllegalArgumentException(PROPERTY + " requires benchmark-dev bootstrap (benchmarkTunerProbe)");
        final float value;
        try { value = Float.parseFloat(raw); }
        catch (NumberFormatException bad) { throw new IllegalArgumentException(PROPERTY + " requires a finite float in [0, 3600)", bad); }
        // Iris's native animation counter wraps at 3600 seconds.
        if (!Float.isFinite(value) || value < 0 || value >= 3600)
            throw new IllegalArgumentException(PROPERTY + " requires a finite float in [0, 3600)");
        return new Configuration(true, value);
    }
}
