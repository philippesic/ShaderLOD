package com.pp.shaderlod.integration.makeupquad;
import java.security.MessageDigest;
import java.util.HexFormat;
/** Optional mixin bootstrap: JDK only, never links Iris/Sodium/Minecraft classes. */
public final class MakeUpQuadApiPins {
    private MakeUpQuadApiPins() {}
    private static final String[][][] PIN_SETS = {
        {
            {"net/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch.class","a64ef72a497d126388992e9d52dab9c4cb5b0e18a5eb2b8e227be613cd0bea60"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","d8ec37130555f3e9670760fc06f94a0c93e268aee8d218df1ba91f7522586e9a"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer.class","66996a71cd179d0a97d65d14213fe82badab252983a6686fc805e1de10a2b7ff"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexType.class","4ccf07b8f830cbc84ef77d61a33f7d7ee065628f87ffe37ed043f89ace31c39e"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexType$2.class","1b69dfd875789687e4cccd6aa037cc9f1d77b8bf60a2f5e6847a3002255766c5"},
            {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","dde8eb4bc65bc6da23f2031477b22c410cdd0bf13b70309fe5f8198b1e383717"},
            {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","5175b6dc1422df3450a142447a4e0570c7bfcd61ebeb12b05cacb4366fb4d2f5"},
            {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","94e4d8e715e8b05c1a04006e70aacfb23366679b09583f4c9792175aa19b9396"},
            {"net/irisshaders/iris/compat/sodium/mixin/MixinDefaultChunkRenderer.class","86b03c81632ec14151b452eaa746fcd6a3be67113acff4a9b7e61099b4456067"},
            {"net/irisshaders/iris/shaderpack/option/ShaderPackOptions.class","6a147b6da07959b0eaf4084e5d13379c6ab9cef3b53f76b43b265b0b997606b7"},
            {"net/irisshaders/iris/shaderpack/option/values/MutableOptionValues.class","202a4d6bc280585614e802f785dec6d6735a877b1595054a98e103b53f99b29f"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataUnsafe.class","2a1a4538d8282141cc9214ab697e8363a006c3517576759f890f5c82bf9b6d13"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/DefaultTerrainRenderPasses.class","0f16663d97db3eb5d6f99f0a9f358c01792eac16479f3ca8167d9942d5caf6dd"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderList.class","6810faf8bc5a9b5a53c83438afa5fb109aa90d8db2c7279c9ab567e07ca1a788"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkMeshFormats.class","c75a46977916b531c81d81a3cc4ba2f3133453f3173b4a181554d3867eb3b481"},
            {"net/irisshaders/iris/vertices/sodium/terrain/XHFPTerrainVertex.class","c00e05d3bb6fb294a2d19e11d4345c2efe54408fa13ddfe1d4321d070a83145d"}
        }
    };
    private static Boolean pinned;
    public static boolean exactApi() {
        if (pinned != null) return pinned;
        return pinned = resourcesMatch(MakeUpQuadApiPins.class.getClassLoader());
    }
    public static boolean resourcesMatch(ClassLoader loader) {
        for (var pins : PIN_SETS) {
            boolean match = true;
            try {
                for (var pin : pins) try (var in = loader.getResourceAsStream(pin[0])) {
                    if (in == null || !pin[1].equals(HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())))) { match=false; break; }
                }
                if (match) return true;
            } catch (Exception | LinkageError unavailable) { /* next complete pinned variant */ }
        }
        return false;
    }
}
