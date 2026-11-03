package com.pp.shaderlod.integration.makeupquad;
import java.security.MessageDigest;
import java.util.HexFormat;
/** Optional mixin bootstrap: JDK only, never links Iris/Sodium/Minecraft classes. */
public final class MakeUpQuadApiPins {
    private MakeUpQuadApiPins() {}
    private static final String[][] PINS = {
        {"net/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch.class","8b355f8255d0c6d1fd5e9bf1a89d9dacb2b494cabacdfbb1059201cb0dde3d8c"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","e57ee69430ac4a79ab4608e2a3341b5af2ce08c5ecc84406502a9d0379a6333f"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer.class","e567edab9e84facef6da76fe71ad1d4de3d765a9f57e12b624c935b49964a3a2"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexType.class","d1448c4dfa83c03d9a291f375fb5893478efe1ceb7fc07b1b11321e1236cf1b2"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexType$2.class","846f1fa106f3e35c0790922cc624c5e990ed49aa05f0b9c939e50530bc347f09"},
        {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","9fe2bf60a9c6140c90bcf09aeae23e38365f029660385d4dae919fbe6e7481cf"},
        {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","95b3eb4425306964328cf771c7cc79b8bfffdf90e15bce813ad1a7bd38f34579"},
        {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","17bbfb7cc7bd5da043f508924c6485c2bc550a123dad94165cc033610803f8ae"},
        {"net/irisshaders/iris/compat/sodium/mixin/MixinDefaultChunkRenderer.class","dc0564352c37ba3a01919bbe185fb3e894998837950d32fe8f8e68837370c51f"},
        {"net/irisshaders/iris/shaderpack/option/ShaderPackOptions.class","a6126e1e10013467efd930676756e1fd883f331217bfffd184a5ddb4670334c3"},
        {"net/irisshaders/iris/shaderpack/option/values/MutableOptionValues.class","8b49420d63b778bc1295db53881694114b8a98cff3f026499c4175cdb125bc9e"}
    };
    private static Boolean pinned;
    public static boolean exactApi() {
        if (pinned != null) return pinned;
        return pinned = resourcesMatch(MakeUpQuadApiPins.class.getClassLoader());
    }
    public static boolean resourcesMatch(ClassLoader loader) {
        try {
            for (var pin : PINS) try (var in = loader.getResourceAsStream(pin[0])) {
                if (in == null || !pin[1].equals(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())))) return false;
            }
            return true;
        } catch (Exception | LinkageError unavailable) { return false; }
    }
}
