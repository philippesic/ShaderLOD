package com.pp.shaderlod.optimization.framecopy.rotation;

import com.pp.shaderlod.optimization.framecopy.FrameCopyPins;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Raw pins are necessary, never sufficient for transformed shader or live graph ownership. */
public final class HistoryRotationPins {
    private HistoryRotationPins() {}
    private static final Map<String, String> CLASSES = Map.ofEntries(
        Map.entry("net/irisshaders/iris/gl/program/ProgramSamplers.class", "431566fc2b9233c62c4413dbe1231600275bd1a31562e62355f0e3d75ce387f7"),
        Map.entry("net/irisshaders/iris/gl/sampler/SamplerBinding.class", "f3b15536d1c4edb697a1c686bccb939223a17adbc428f3b8c548b849fc355fd9"),
        Map.entry("net/irisshaders/iris/gl/program/ProgramImages.class", "b1297a2235e8e2a56c17256f9535d771efe1fb759d5e3c2f561f35e17daf1db3"),
        Map.entry("net/irisshaders/iris/gl/image/ImageBinding.class", "cd87466f158c91199f24e9072a891f7aeb3ba0b8ed843786b8cc17a57d4f04a6"),
        Map.entry("net/irisshaders/iris/pipeline/CompositeRenderer$Pass.class", "e0487ee63567a3224315e15aa37eb46529306d31e71c60c2d4136b9654adc8f3"),
        Map.entry("net/irisshaders/iris/pipeline/FinalPassRenderer$Pass.class", "e67fcf2826ef837c0ce6c700298b3dd2cb77a7a36d7df27af6ae165ab1a2c1ee"),
        Map.entry("net/irisshaders/iris/targets/ClearPassCreator.class", "49a7ab24dcbd69dc344a759974b2d70c026d42ba4ac26fde0973aa16cbaca64e"),
        Map.entry("net/irisshaders/iris/targets/ClearPass.class", "db5c57bf6df724e2e4f82b1d016252d86e31fd2fb96728c8adaaaee855f60887"),
        Map.entry("net/irisshaders/iris/pipeline/programs/ExtendedShader.class", "d84ce2ac8f90da4d0d001da76bfc0c5a8aadb4a4018c0946c2d469da477a3a0a"),
        Map.entry("net/irisshaders/iris/pipeline/programs/FallbackShader.class", "ce9e6c0a32e9018856f4747d8b99293f46e5a719cb6f46c2d601002102386579"),
        Map.entry("net/irisshaders/iris/targets/BufferFlipper.class", "6743caf75f8c77b75ee0a00442b749e9ed65b38168d4c3ea8e8288568e999787"),
        Map.entry("net/irisshaders/iris/pipeline/CompositeRenderer.class", "710afa36c1d800b5f83f0ae69cbc52d9707332436c2c9fd32f443490e3eabfbd"),
        Map.entry("net/irisshaders/iris/pipeline/FinalPassRenderer.class", "b94abf03c8541649e2fac542e3f0544ddc557f46ec1a82508d34bda6c35d7696"),
        Map.entry("net/irisshaders/iris/pipeline/IrisRenderingPipeline.class", "95b3eb4425306964328cf771c7cc79b8bfffdf90e15bce813ad1a7bd38f34579"),
        Map.entry("net/irisshaders/iris/pipeline/programs/SodiumPrograms.class", "9fe2bf60a9c6140c90bcf09aeae23e38365f029660385d4dae919fbe6e7481cf"),
        Map.entry("net/irisshaders/iris/samplers/IrisImages.class", "9ac0801b492e2f39726d2636ae7c44a3bc790a17569d87163f5d3d263178db33"),
        Map.entry("net/irisshaders/iris/samplers/IrisSamplers.class", "9ca8e284dd3a0394a90c273e2a2422b1c569e664654e85843d06d5b5a33f18e4"),
        Map.entry("net/irisshaders/iris/shadows/ShadowCompositeRenderer.class", "11f3aa7cb28b25e752d11df82b1a6a0cecaee35f5a09d8d7ccea06edfade00fc"),
        Map.entry("net/irisshaders/iris/shadows/ShadowRenderer.class", "fb80f74fa044378605df7d9e7408574f1972709dac1ed17452b0e2f6f9372b59"),
        Map.entry("net/irisshaders/iris/shadows/ShadowRenderTargets.class", "4d6f1b3294365738dbcacac691021105a8141b2893441d919aff8b148ff093c5"),
        Map.entry("net/irisshaders/iris/targets/RenderTarget.class", "73f7a90b778e839797799be73007db16f9b8481b424537180b926b3f71e827c8"),
        Map.entry("net/irisshaders/iris/targets/RenderTargets.class", "8a981c44321f7b0a4ef61d86e94fe3a9c0cdd5c651645b4f3ffb0bbd66847b6a"),
        Map.entry("net/irisshaders/iris/pathways/FullScreenQuadRenderer.class", "7595d1408a17a4203af0c66b7d8a7f66c7f28e4a7b9ac7243893800e9d5a4d22"),
        Map.entry("net/irisshaders/iris/gl/program/Program.class", "8252cffe8d7322a3d64fad8036938e07b871cbd5667c0dafec0a3d48fd610d49"),
        Map.entry("net/irisshaders/iris/gl/program/ProgramSamplers$CustomTextureSamplerInterceptor.class", "325ad1e876cd1dd3311d5d1f5e585d1d235e5346eb3bcd8c60ee998eaaaec17b"),
        Map.entry("net/irisshaders/iris/shaderpack/properties/PackRenderTargetDirectives.class", "a5e5ad3ebd18483e5b024465bd67529a7653a5c26895cececed4668906f1c8b3"),
        Map.entry("net/irisshaders/iris/shaderpack/properties/PackDirectives.class", "f00e7809b1bc017fb24632a4c5983773ab57fd7eb515193f45e27d3a868cb824"),
        Map.entry("net/irisshaders/iris/shaderpack/properties/ProgramDirectives.class", "f73338f164c05bb1c471718771685be1b964d4df100d480ecf2339a1ee477675"),
        Map.entry("net/irisshaders/iris/gl/framebuffer/ViewportData.class", "984ba5c7f07cd71eb223a22dc95f9bccbf923f3cd78fb753aa6ff10fabc251e5"),
        Map.entry("net/irisshaders/iris/gl/program/ProgramSamplers$Builder.class", "a993597623cc809e88b73c3a38b6ce218465f12752cba26a2021247e4c7bee86"),
        Map.entry("net/irisshaders/iris/gl/program/ProgramImages$Builder.class", "af9f18078b7417b5b6f19f7fe664ccfae45a09c83dec2872a30707b32ed7f546"),
        Map.entry("net/irisshaders/iris/pipeline/transform/transformer/CompositeTransformer.class", "e67965a33e2c78ab57b95a2bfbcd118297fe2b0e7e530c5bc4d58482ca2f53fd"),
        Map.entry("net/irisshaders/iris/uniforms/builtin/BuiltinReplacementUniforms.class", "0f12e9706269332947a490b291bd15ab436bca9cbb30d3f6167406a7b8160007"),
        Map.entry("net/irisshaders/iris/gl/IrisRenderSystem.class", "1782e30fb9c25e3189ecd2215747321d7af7083ec9f74a5de657c879f0cbc71d"));
    private static final Set<String> HOOKS = new HashSet<>();
    private static final boolean EXACT = check();
    public static boolean exactApi() { return EXACT; }
    public static synchronized void hookVerified(String name) { HOOKS.add(name); }
    public static synchronized boolean hooksVerified() {
        return HOOKS.containsAll(Set.of("HistoryRotationCompositeMixin", "HistoryRotationSamplerMixin",
            "HistoryRotationFinalMixin", "HistoryRotationTargetsMixin", "HistoryRotationPipelineMixin", "HistoryRotationFrameMixin", "HistoryRotationDrawMixin"));
    }
    private static boolean check() {
        if (!FrameCopyPins.exactApi()) return false;
        try {
            for (var pin : CLASSES.entrySet()) {
                try (var in = HistoryRotationPins.class.getClassLoader().getResourceAsStream(pin.getKey())) {
                    if (in == null || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(in.readAllBytes())).equals(pin.getValue())) return false;
                }
            }
            return true;
        } catch (Exception | LinkageError e) { return false; }
    }
}
