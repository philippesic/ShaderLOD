package com.pp.shaderlod.optimization.framecopy;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Raw class resources checked once, outside rendering. Qualifies observation only. */
public final class FrameCopyPins {
    private FrameCopyPins() {}
    private static final Map<String, String> CLASSES = Map.ofEntries(
        Map.entry("net/irisshaders/iris/pipeline/IrisRenderingPipeline.class", "95b3eb4425306964328cf771c7cc79b8bfffdf90e15bce813ad1a7bd38f34579"),
        Map.entry("net/irisshaders/iris/pipeline/FinalPassRenderer.class", "b94abf03c8541649e2fac542e3f0544ddc557f46ec1a82508d34bda6c35d7696"),
        Map.entry("net/irisshaders/iris/pipeline/FinalPassRenderer$SwapPass.class", "31be595730350aafc99d3c9feb74a774ab1091c49ade481744f3e0f7b5962161"),
        Map.entry("net/irisshaders/iris/targets/RenderTarget.class", "73f7a90b778e839797799be73007db16f9b8481b424537180b926b3f71e827c8"),
        Map.entry("net/irisshaders/iris/targets/RenderTargets.class", "8a981c44321f7b0a4ef61d86e94fe3a9c0cdd5c651645b4f3ffb0bbd66847b6a"),
        Map.entry("net/irisshaders/iris/gl/framebuffer/GlFramebuffer.class", "6b5bd7283f6eb50b2aaebfe17142b209c7012b7d12c1481f91cfc5371f377d6f"),
        Map.entry("net/irisshaders/iris/samplers/IrisSamplers.class", "9ca8e284dd3a0394a90c273e2a2422b1c569e664654e85843d06d5b5a33f18e4"),
        Map.entry("net/irisshaders/iris/samplers/IrisImages.class", "9ac0801b492e2f39726d2636ae7c44a3bc790a17569d87163f5d3d263178db33"),
        Map.entry("net/irisshaders/iris/gl/texture/InternalTextureFormat.class", "55e7e195979cbcbe3e0787a74065ce1a77d1c785845625a29ee6ae705ef86ffb"),
        Map.entry("net/irisshaders/iris/pipeline/CompositeRenderer.class", "710afa36c1d800b5f83f0ae69cbc52d9707332436c2c9fd32f443490e3eabfbd"));
    private static final boolean EXACT = check();
    public static boolean exactApi() { return EXACT; }
    private static boolean check() {
        try {
            for (var pin : CLASSES.entrySet()) {
                try (var in = FrameCopyPins.class.getClassLoader().getResourceAsStream(pin.getKey())) {
                    if (in == null || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(in.readAllBytes())).equals(pin.getValue())) return false;
                }
            }
            return true;
        } catch (Exception | LinkageError unavailable) { return false; }
    }
}
