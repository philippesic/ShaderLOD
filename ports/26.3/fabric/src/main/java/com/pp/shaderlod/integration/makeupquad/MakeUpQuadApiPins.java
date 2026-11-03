package com.pp.shaderlod.integration.makeupquad;
import java.security.MessageDigest;
import java.util.HexFormat;
/** Exact 26.3 fabric Iris/Sodium consumer classes; JDK-only optional mixin bootstrap. */
public final class MakeUpQuadApiPins {
    private MakeUpQuadApiPins() {}
    private static final String[][] PINS = {
        {"com/mojang/renderpearl/backend/opengl/GlCommandEncoder.class","e116a62ea85ba841c9c1136284be37230c18ad2c77ac4381c6e314a861a47e00"},
        {"com/mojang/renderpearl/backend/opengl/GlRenderPass.class","4b19cf6da39bc0e2c217959778c01950552d1e28497e44b036adc92ca9c1e9a7"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexFormat$1.class","96a080fa86a596ea6f8ddfad207fa42d308811be0de46e42b6a1de23cf048a3a"},
        {"net/caffeinemc/mods/sodium/client/gpu/device/batch/MultiDrawBatch.class","ae460cc7b5d6d9be12990740fa6036b524911e8558c79a9102e76d3d8b65c1f4"},
        {"net/caffeinemc/mods/sodium/client/gpu/device/batch/GLDrawBatch.class","1e0ab3d1f189d05f82b45c79aa1828a1bfc71e602e3850b4cf679e0d92ebfbd1"},
        {"net/caffeinemc/mods/sodium/client/gpu/device/context/DrawContext.class","e1781e0c0040402386aabfff367312650619f7dda8b4d758d92afb43944cab27"},
        {"net/caffeinemc/mods/sodium/client/gpu/device/context/GLDrawContext.class","6e25ad1185af4a1c6c1556fffe915479a971456779fb2f611a827ccf59076899"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","e83545a59ede6438a5fb9e6f2615ff7bdea505b8acfbd3bbf1d34e2303134b55"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer.class","6a381173eac0f1c39a4a8acd9046f2adcb1ee11b5d411c061f1e87675dbcc310"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexFormat.class","00fef39477a89e9b99d4e94fdaf10124f9a68929c6fe4dd36a459c84ea1e02cf"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion.class","81c03765dc66ac52291deb8041ae2f188cf98662cd6e7963df4e3bba317c45e6"},
        {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","53a667ae4114da83f10a19246898afb4ca5e2c1599595a53878559cfa03cee88"},
        {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","11519cea90ba553ad5ef9498d15b6799333cdeb8a29e6e5c1a8f71bdfc105709"},
        {"net/irisshaders/iris/compat/sodium/mixin/MixinDefaultChunkRenderer.class","37905687b7f15d2addc8222e09e361b3d2cb0987dfb559aa1c081d9b0f3d4bf1"},
        {"net/irisshaders/iris/shaderpack/option/ShaderPackOptions.class","6a147b6da07959b0eaf4084e5d13379c6ab9cef3b53f76b43b265b0b997606b7"},
        {"net/irisshaders/iris/shaderpack/option/values/MutableOptionValues.class","202a4d6bc280585614e802f785dec6d6735a877b1595054a98e103b53f99b29f"}
    };
    // Complete Loom development tuple; class hashes are never selected independently.
    private static final String[][] LOOM_DEVELOPMENT_PINS = {
        {"net/caffeinemc/mods/sodium/client/gpu/device/batch/MultiDrawBatch.class","ae460cc7b5d6d9be12990740fa6036b524911e8558c79a9102e76d3d8b65c1f4"},
        {"net/caffeinemc/mods/sodium/client/gpu/device/batch/GLDrawBatch.class","1e0ab3d1f189d05f82b45c79aa1828a1bfc71e602e3850b4cf679e0d92ebfbd1"},
        {"net/caffeinemc/mods/sodium/client/gpu/device/context/DrawContext.class","e1781e0c0040402386aabfff367312650619f7dda8b4d758d92afb43944cab27"},
        {"net/caffeinemc/mods/sodium/client/gpu/device/context/GLDrawContext.class","6e25ad1185af4a1c6c1556fffe915479a971456779fb2f611a827ccf59076899"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","e83545a59ede6438a5fb9e6f2615ff7bdea505b8acfbd3bbf1d34e2303134b55"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer.class","6a381173eac0f1c39a4a8acd9046f2adcb1ee11b5d411c061f1e87675dbcc310"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexFormat.class","00fef39477a89e9b99d4e94fdaf10124f9a68929c6fe4dd36a459c84ea1e02cf"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion.class","81c03765dc66ac52291deb8041ae2f188cf98662cd6e7963df4e3bba317c45e6"},
        {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","53a667ae4114da83f10a19246898afb4ca5e2c1599595a53878559cfa03cee88"},
        {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","11519cea90ba553ad5ef9498d15b6799333cdeb8a29e6e5c1a8f71bdfc105709"},
        {"net/irisshaders/iris/compat/sodium/mixin/MixinDefaultChunkRenderer.class","37905687b7f15d2addc8222e09e361b3d2cb0987dfb559aa1c081d9b0f3d4bf1"},
        {"net/irisshaders/iris/shaderpack/option/ShaderPackOptions.class","6a147b6da07959b0eaf4084e5d13379c6ab9cef3b53f76b43b265b0b997606b7"},
        {"net/irisshaders/iris/shaderpack/option/values/MutableOptionValues.class","202a4d6bc280585614e802f785dec6d6735a877b1595054a98e103b53f99b29f"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexFormat$1.class","96a080fa86a596ea6f8ddfad207fa42d308811be0de46e42b6a1de23cf048a3a"},
        {"com/mojang/renderpearl/backend/opengl/GlRenderPass.class","0db0f92106c35b1efe0e247f86cbbb931de8b8de1c7a74093871fb3637d49921"},
        {"com/mojang/renderpearl/backend/opengl/GlCommandEncoder.class","e8b79c8027869599fcba93dd6731a3a9acae145b5072200210b13c8a10aa21f0"}
    };
    private static Boolean pinned;
    public static boolean exactApi() {
        if (pinned == null) pinned=resourcesMatch(MakeUpQuadApiPins.class.getClassLoader());
        return pinned;
    }
    public static boolean resourcesMatch(ClassLoader loader) {
        try {
            for (var profile:new String[][][] {PINS,LOOM_DEVELOPMENT_PINS}) {
                boolean matches=true;
                for (var pin:profile) try (var in=loader.getResourceAsStream(pin[0])) {
                    if (in==null || !pin[1].equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())))) {
                        matches=false;break;
                    }
                }
                if (matches) return true;
            }
            return false;
        } catch (Exception | LinkageError unavailable) { return false; }
    }
}
