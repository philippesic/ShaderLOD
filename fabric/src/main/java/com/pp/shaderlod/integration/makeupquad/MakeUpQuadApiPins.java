package com.pp.shaderlod.integration.makeupquad;
import java.security.MessageDigest;
import java.util.HexFormat;
/** JDK-only Fabric bootstrap. Whole profiles pin release intermediary or inspected Loom named bytes. */
public final class MakeUpQuadApiPins {
    private MakeUpQuadApiPins() {}
    private static final String[][] INTERMEDIARY = {
        {"net/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch.class","b155dbeeebad99b1fac87103816529383eb256b1fb650e59ba5c825687b7acfa"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","571f06982ae05c65ff68b263dde5a2f514d1ffa31276b7dd9c29aff82a2550ab"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer.class","b7996f06c9e2972232b0e3054a759446ec3560f419de6b15a0fce8e287f4bd60"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexType.class","e77f52f70431a207d73a11c47c99c65f084bd396320c2c3927c6f52793f3f4c7"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexType$2.class","a5eabc37c43a17172c342fe78d1358f51087801cb8ba9e360679b42676afc685"},
        {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","7f49c1da2e12807f0680ca894f631c104b2a8846492e4c116014db11dc3e29b6"},
        {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","006906a07a47313efc873289f9f59de5c2ec2030ed85ffaca39491916e8ab91e"},
        {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","f79728bc0f0cded446753da51f61114daed8988eef4f567bca9e351280874b28"},
        {"net/irisshaders/iris/compat/sodium/mixin/MixinDefaultChunkRenderer.class","a22e4aa2d1bdc870ddf9241da83d35361ed0a11d6ba995b36e97621f378b50bc"},
        {"net/irisshaders/iris/shaderpack/option/ShaderPackOptions.class","bec6e7825307767a3b6a29093a86d6cd04156d0a4dd3a359bee59a932dc8e319"},
        {"net/irisshaders/iris/shaderpack/option/values/MutableOptionValues.class","d1047ca1facbaac769c1b5853b7dba0a0a938dfc2fc9fee9cb03fd12f5ae97fb"}
    };
    private static final String[][] NAMED = {
        {"net/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch.class","b155dbeeebad99b1fac87103816529383eb256b1fb650e59ba5c825687b7acfa"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","571f06982ae05c65ff68b263dde5a2f514d1ffa31276b7dd9c29aff82a2550ab"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer.class","b7996f06c9e2972232b0e3054a759446ec3560f419de6b15a0fce8e287f4bd60"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexType.class","e77f52f70431a207d73a11c47c99c65f084bd396320c2c3927c6f52793f3f4c7"},
        {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexType$2.class","a5eabc37c43a17172c342fe78d1358f51087801cb8ba9e360679b42676afc685"},
        {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","4ba26f97a2a9006d3730962e3c7eb26f620c50cc7d98d82b6235720eac4753fb"},
        {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","eea6e6cdbca25786177fd69eba632643df6691afe17257f6cbe580351393f3b4"},
        {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","f79728bc0f0cded446753da51f61114daed8988eef4f567bca9e351280874b28"},
        {"net/irisshaders/iris/compat/sodium/mixin/MixinDefaultChunkRenderer.class","a22e4aa2d1bdc870ddf9241da83d35361ed0a11d6ba995b36e97621f378b50bc"},
        {"net/irisshaders/iris/shaderpack/option/ShaderPackOptions.class","bec6e7825307767a3b6a29093a86d6cd04156d0a4dd3a359bee59a932dc8e319"},
        {"net/irisshaders/iris/shaderpack/option/values/MutableOptionValues.class","d1047ca1facbaac769c1b5853b7dba0a0a938dfc2fc9fee9cb03fd12f5ae97fb"}
    };
    private static Boolean pinned;
    public static boolean exactApi() {
        if (pinned != null) return pinned;
        return pinned = resourcesMatch(MakeUpQuadApiPins.class.getClassLoader());
    }
    public static boolean resourcesMatch(ClassLoader loader) {
        return profileMatches(loader, INTERMEDIARY) || profileMatches(loader, NAMED);
    }
    private static boolean profileMatches(ClassLoader loader, String[][] profile) {
        try {
            for (var pin : profile) try (var in = loader.getResourceAsStream(pin[0])) {
                if (in == null || !pin[1].equals(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())))) return false;
            }
            return true;
        } catch (Exception | LinkageError unavailable) { return false; }
    }
}
