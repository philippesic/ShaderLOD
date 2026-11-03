package com.pp.shaderlod.integration.makeupquad;
import java.security.MessageDigest;
import java.util.HexFormat;
/** Optional mixin bootstrap: JDK only, never links Iris/Sodium/Minecraft classes. */
public final class MakeUpQuadApiPins {
    private MakeUpQuadApiPins() {}
    private static final String[][][] PIN_SETS = {
        {
            {"net/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch.class","6a413a36bb80c5487596b2d7785e53337355fb5fdbb6eaa6285ac6027f254c10"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","c3af45983587f78f75d3cd7f1d3ca0867b4df09441ee3823c873890a73007dbd"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer.class","5eec620d58ec706e073e86555b676f37277b4f2486a6662ea95b73f5f1666090"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexType.class","068da36d58c9c9ea622c905abc4dd05ef1c25fca870076ac491e6c77b0c4414b"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexType$2.class","a7b0f470ecf74dd2a63951aa3c06b3e626601eeea2eb038ac723238fc6f4dc62"},
            {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","09aacd566bb2d1585ea32561447c23567d44c82f12b9beb6a7c5f1d1246832b9"},
            {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","313783af9ce511c40fcb39d246c4322de0120c1031c0cfd2fe3685fdde7c71ca"},
            {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","02c54dff4a695ad6b233cafb95a0d6a095b829be901fab1a0734a790815844d2"},
            {"net/irisshaders/iris/compat/sodium/mixin/MixinDefaultChunkRenderer.class","b446360084634e6be1d150be933d8d02558e3b48a93e37565a531d95a8c81535"},
            {"net/irisshaders/iris/shaderpack/option/ShaderPackOptions.class","a6126e1e10013467efd930676756e1fd883f331217bfffd184a5ddb4670334c3"},
            {"net/irisshaders/iris/shaderpack/option/values/MutableOptionValues.class","8b49420d63b778bc1295db53881694114b8a98cff3f026499c4175cdb125bc9e"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataUnsafe.class","15c2ca6714cfba1a1774b0fb7c27ebc869a89e84d381310f61c1576add0ba258"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/DefaultTerrainRenderPasses.class","0c9eee0cdbdba23212f4fd1c289d985613558122f2dcd03192e7b3450f646817"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderList.class","e1fb247d8eb9d6f266e482ee670cf9901ac33516c1276134c879d81f5fd12c59"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkMeshFormats.class","2ab1501fa0b34a49acb4a115c4b6e6e8e2eb08b42ad2d4c8ceb0fd08438f0786"},
            {"net/irisshaders/iris/vertices/sodium/terrain/XHFPTerrainVertex.class","e3b02f1140a9810d34c66c47e03747f7ddd5507f371024be1e5de80a65e08d68"}
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
