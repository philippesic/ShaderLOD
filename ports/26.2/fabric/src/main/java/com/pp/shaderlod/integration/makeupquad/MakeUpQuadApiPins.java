package com.pp.shaderlod.integration.makeupquad;
import java.security.MessageDigest;
import java.util.HexFormat;
/** Exact 26.2 fabric Iris/Sodium consumer classes; JDK-only optional mixin bootstrap. */
public final class MakeUpQuadApiPins {
    private MakeUpQuadApiPins() {}
    private static final String[][] PINS = {
        {"com/mojang/blaze3d/opengl/GlCommandEncoder.class","817def104e86530ea2ec7eb6d23eaf4ade18e8ffbb6f5a551db47cecceb890ed"},
        {"com/mojang/blaze3d/opengl/GlRenderPass.class","7ad9eed06cc24d3a5c8db45a57d9bb6691cf354b07774036316046cb3acc10fa"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexFormat$1.class","1f179fb399c259cc2d31157364fcc31744e192243c70207f0f1e72951c41756b"},
        {"net/caffeinemc/mods/sodium/client/gpu/device/batch/MultiDrawBatch.class","cb7319f18c58716ec1543cf5f54e59c594f02b487b21dc0deec4eea0361222dd"},
        {"net/caffeinemc/mods/sodium/client/gpu/device/batch/GLDrawBatch.class","b4ecc8215e59c4302099e97e58a0061f4f2bf98a716af703e8feed391a5f1ff8"},
        {"net/caffeinemc/mods/sodium/client/gpu/device/context/DrawContext.class","fb0cc6d592cf070f444494ab54732613f173fa80e4f89f726405a3ce1676a2b3"},
        {"net/caffeinemc/mods/sodium/client/gpu/device/context/GLDrawContext.class","6fde290971d00ce4465aac6d8a7f3df28d370afb2098fab3cc75827b8ac6a339"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","0488fed4e8c51431266ede394c2e02f3ddd867719d5fa02bac92577609e119b4"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer.class","a44ec64cacf6ea1e94a1b5a1bc4d9b015618bad02597d19b41c35c772fddb155"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexFormat.class","38264aa3c93f1598082cc7be15c5154849f5541a809ef55a9b2fb70a6fe262ac"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion.class","9685d9f741dfe97ea93b23aeda207f0bfd160d70da8414d03ebccb87b0532b02"},
        {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","bc9352887ef09159c4680b759b498c7bc80531660b4f4ac893f3f060392064c4"},
        {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","06f5c63c3d50c07a364164745d1cfc038cf28fa4157a5323726a578b4fc977be"},
        {"net/irisshaders/iris/compat/sodium/mixin/MixinDefaultChunkRenderer.class","9d9f276509e766afa4093b5818b7d623441f9279bb9191a9a1d96c5fbb53051a"},
        {"net/irisshaders/iris/shaderpack/option/ShaderPackOptions.class","6a147b6da07959b0eaf4084e5d13379c6ab9cef3b53f76b43b265b0b997606b7"},
        {"net/irisshaders/iris/shaderpack/option/values/MutableOptionValues.class","202a4d6bc280585614e802f785dec6d6735a877b1595054a98e103b53f99b29f"}
    };
    // Complete Loom development tuple; class hashes are never selected independently.
    private static final String[][] LOOM_DEVELOPMENT_PINS = {
        {"net/caffeinemc/mods/sodium/client/gpu/device/batch/MultiDrawBatch.class","cb7319f18c58716ec1543cf5f54e59c594f02b487b21dc0deec4eea0361222dd"},
        {"net/caffeinemc/mods/sodium/client/gpu/device/batch/GLDrawBatch.class","b4ecc8215e59c4302099e97e58a0061f4f2bf98a716af703e8feed391a5f1ff8"},
        {"net/caffeinemc/mods/sodium/client/gpu/device/context/DrawContext.class","fb0cc6d592cf070f444494ab54732613f173fa80e4f89f726405a3ce1676a2b3"},
        {"net/caffeinemc/mods/sodium/client/gpu/device/context/GLDrawContext.class","6fde290971d00ce4465aac6d8a7f3df28d370afb2098fab3cc75827b8ac6a339"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","0488fed4e8c51431266ede394c2e02f3ddd867719d5fa02bac92577609e119b4"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer.class","a44ec64cacf6ea1e94a1b5a1bc4d9b015618bad02597d19b41c35c772fddb155"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexFormat.class","38264aa3c93f1598082cc7be15c5154849f5541a809ef55a9b2fb70a6fe262ac"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion.class","9685d9f741dfe97ea93b23aeda207f0bfd160d70da8414d03ebccb87b0532b02"},
        {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","bc9352887ef09159c4680b759b498c7bc80531660b4f4ac893f3f060392064c4"},
        {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","06f5c63c3d50c07a364164745d1cfc038cf28fa4157a5323726a578b4fc977be"},
        {"net/irisshaders/iris/compat/sodium/mixin/MixinDefaultChunkRenderer.class","9d9f276509e766afa4093b5818b7d623441f9279bb9191a9a1d96c5fbb53051a"},
        {"net/irisshaders/iris/shaderpack/option/ShaderPackOptions.class","6a147b6da07959b0eaf4084e5d13379c6ab9cef3b53f76b43b265b0b997606b7"},
        {"net/irisshaders/iris/shaderpack/option/values/MutableOptionValues.class","202a4d6bc280585614e802f785dec6d6735a877b1595054a98e103b53f99b29f"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexFormat$1.class","1f179fb399c259cc2d31157364fcc31744e192243c70207f0f1e72951c41756b"},
        {"com/mojang/blaze3d/opengl/GlRenderPass.class","860b03f274462526634a2021c9b521cb17fe5991e9388b7b3d94753d05d1d6c2"},
        {"com/mojang/blaze3d/opengl/GlCommandEncoder.class","16359d35b89008f6a90691c44e2fce4c1c01390d107a2dd42c9852594b9d0054"}
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
