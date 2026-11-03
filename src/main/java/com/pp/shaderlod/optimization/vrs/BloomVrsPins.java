package com.pp.shaderlod.optimization.vrs;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Exact Iris compiler and quad ABI, independent of OS/vendor capability policy. */
public final class BloomVrsPins {
    private BloomVrsPins() {}
    private static final Map<String,String> PINS = Map.of(
            "net/irisshaders/iris/pipeline/CompositeRenderer.class","710afa36c1d800b5f83f0ae69cbc52d9707332436c2c9fd32f443490e3eabfbd",
            "net/irisshaders/iris/gl/program/ProgramBuilder.class","f2b09d4fe167409349529666ffb33864ac8d473321a3bb271234011cd5afa6aa",
            "net/irisshaders/iris/gl/shader/ProgramCreator.class","0fe4f9c64a4bdd8f544c23099cee9117ad91b0a73caed6429c38e2c560f18e2e",
            "net/irisshaders/iris/gl/shader/GlShader.class","3c53fa0c3d704ae0383c56b95d1c653eac152069d646b95444c32f46e6b998db",
            "net/irisshaders/iris/pipeline/transform/TransformPatcher.class","2cc6d66298b0694e1a03c50da198d4d59ad3f63759183909ded86703af36fc47",
            "net/irisshaders/iris/pathways/FullScreenQuadRenderer.class","7595d1408a17a4203af0c66b7d8a7f66c7f28e4a7b9ac7243893800e9d5a4d22");
    private static final boolean EXACT = inspect();
    public static boolean exact() { return EXACT; }
    public static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static boolean inspect() {
        try {
            for (var pin : PINS.entrySet())
                try (var in = BloomVrsPins.class.getClassLoader().getResourceAsStream(pin.getKey())) {
                    if (in == null || !pin.getValue().equals(sha(in.readAllBytes()))) return false;
                }
            return true;
        } catch (Exception | LinkageError unavailable) { return false; }
    }
}
