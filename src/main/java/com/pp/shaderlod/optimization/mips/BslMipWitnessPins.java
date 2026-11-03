package com.pp.shaderlod.optimization.mips;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Exact raw Iris 1.8.12 NeoForge/MC1.21.1 resources; called only at observer control boundaries. */
public final class BslMipWitnessPins {
    private BslMipWitnessPins() {}
    public static final Map<String, String> CLASSES = Map.ofEntries(
        Map.entry("net/irisshaders/iris/pipeline/programs/SodiumShader.class", "33e49ddf69f01a86a64ad2e2d33595b81a22a859ba1feda0dda65813e663f356"),
        Map.entry("net/irisshaders/iris/pipeline/programs/SodiumPrograms.class", "9fe2bf60a9c6140c90bcf09aeae23e38365f029660385d4dae919fbe6e7481cf"),
        Map.entry("net/caffeinemc/mods/sodium/client/gl/shader/GlProgram.class", "f4439d38f4733c65c08f5b575a387835725605c43d8860f3e652cb4fd92c7ec0"),
        Map.entry("net/irisshaders/iris/pipeline/IrisRenderingPipeline.class", "95b3eb4425306964328cf771c7cc79b8bfffdf90e15bce813ad1a7bd38f34579"),
        Map.entry("net/irisshaders/iris/pipeline/CompositeRenderer.class", "710afa36c1d800b5f83f0ae69cbc52d9707332436c2c9fd32f443490e3eabfbd"),
        Map.entry("net/irisshaders/iris/pipeline/CompositeRenderer$Pass.class", "e0487ee63567a3224315e15aa37eb46529306d31e71c60c2d4136b9654adc8f3"),
        Map.entry("net/irisshaders/iris/pipeline/FinalPassRenderer.class", "b94abf03c8541649e2fac542e3f0544ddc557f46ec1a82508d34bda6c35d7696"),
        Map.entry("net/irisshaders/iris/pipeline/FinalPassRenderer$Pass.class", "e67fcf2826ef837c0ce6c700298b3dd2cb77a7a36d7df27af6ae165ab1a2c1ee"),
        Map.entry("net/irisshaders/iris/pipeline/FinalPassRenderer$SwapPass.class", "31be595730350aafc99d3c9feb74a774ab1091c49ade481744f3e0f7b5962161"),
        Map.entry("net/irisshaders/iris/targets/RenderTargets.class", "8a981c44321f7b0a4ef61d86e94fe3a9c0cdd5c651645b4f3ffb0bbd66847b6a"),
        Map.entry("net/irisshaders/iris/targets/RenderTarget.class", "73f7a90b778e839797799be73007db16f9b8481b424537180b926b3f71e827c8"),
        Map.entry("net/irisshaders/iris/gl/framebuffer/GlFramebuffer.class", "6b5bd7283f6eb50b2aaebfe17142b209c7012b7d12c1481f91cfc5371f377d6f"),
        Map.entry("net/irisshaders/iris/gl/program/Program.class", "8252cffe8d7322a3d64fad8036938e07b871cbd5667c0dafec0a3d48fd610d49"),
        Map.entry("net/irisshaders/iris/gl/program/ProgramSamplers.class", "431566fc2b9233c62c4413dbe1231600275bd1a31562e62355f0e3d75ce387f7"),
        Map.entry("net/irisshaders/iris/gl/sampler/SamplerBinding.class", "f3b15536d1c4edb697a1c686bccb939223a17adbc428f3b8c548b849fc355fd9"),
        Map.entry("net/irisshaders/iris/gl/sampler/GlSampler.class", "75a3f4b37c1b2ed65573eb8d070106902cc2b7205189c8f971748fff8635d33f"),
        Map.entry("net/irisshaders/iris/gl/IrisRenderSystem.class", "1782e30fb9c25e3189ecd2215747321d7af7083ec9f74a5de657c879f0cbc71d"),
        Map.entry("net/irisshaders/iris/samplers/IrisSamplers.class", "9ca8e284dd3a0394a90c273e2a2422b1c569e664654e85843d06d5b5a33f18e4"),
        Map.entry("net/irisshaders/iris/pathways/FullScreenQuadRenderer.class", "7595d1408a17a4203af0c66b7d8a7f66c7f28e4a7b9ac7243893800e9d5a4d22"),
        Map.entry("net/irisshaders/iris/shaderpack/programs/ProgramSource.class", "a1d142aac769ad65f77761f5f8275c7f22972f2e967c09a19074433eea117893"),
        Map.entry("net/irisshaders/iris/shaderpack/properties/ProgramDirectives.class", "f73338f164c05bb1c471718771685be1b964d4df100d480ecf2339a1ee477675"));
    public static boolean exactApi() {
        try {
            for (var pin : CLASSES.entrySet()) {
                try (var input = BslMipWitnessPins.class.getClassLoader().getResourceAsStream(pin.getKey())) {
                    if (input == null || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(input.readAllBytes())).equals(pin.getValue())) return false;
                }
            }
            return true;
        } catch (Exception | LinkageError failure) { return false; }
    }
}
