package com.pp.shaderlod.benchmark;

/** Explicit benchmark context request; never a capability or version override. */
public record GlContextRequest(int major, int minor) {
    public static GlContextRequest parse(String value, boolean ownedController) {
        if (value == null || value.equals("native")) return null;
        var request = switch (value) {
            case "3.2" -> new GlContextRequest(3, 2);
            case "4.3" -> new GlContextRequest(4, 3);
            case "4.5" -> new GlContextRequest(4, 5);
            case "4.6" -> new GlContextRequest(4, 6);
            default -> throw new IllegalArgumentException("Unsupported benchmark GL context request: " + value);
        };
        if (!ownedController) throw new IllegalArgumentException("Explicit GL context requires the owned benchmark controller");
        return request;
    }

    public static GlContextRequest configured() {
        return parse(System.getProperty("shaderlod.dev.benchmarkGlContext", "native"),
                !System.getProperty("shaderlod.dev.benchmarkControlProbe", "").isBlank());
    }
}
