package com.pp.shaderlod.optimization.makeupprime;

/** Private diagnostic host boundary, not public C01 qualification. */
final class C01Platform {
    private C01Platform() {}
    static boolean allowed(String os, String arch, String renderer, String driver) {
        if (os == null) return false;
        String name = os.toLowerCase(java.util.Locale.ROOT);
        if (name.startsWith("mac") || name.equals("darwin")) return true;
        if(name.startsWith("windows"))return
            com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlphaSource.host(os,arch,renderer,driver);
        return name.equals("linux") && "aarch64".equals(arch)
            && "Apple M1 (G13G B1)".equals(renderer)
            && "4.6 (Core Profile) Mesa 25.1.0-asahi20250221".equals(driver);
    }
}
