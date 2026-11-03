package com.pp.shaderlod.optimization.nativechain;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
/** Single cached decision used by all seventeen legacy plugins and the replacement plugin. */
public final class NativeChainAdmission {
    private NativeChainAdmission() {}
    // The local wall-time bracket established no benefit; retain the candidate dormant.
    private static final boolean PERFORMANCE_QUALIFIED=false;
    private static String refusal="performance qualification parked";
    private static final boolean REPLACEMENT=PERFORMANCE_QUALIFIED&&admit();
    static { System.out.println("[ShaderLOD native-chain] " + status()); }
    public static boolean useReplacement(){return REPLACEMENT;}
    public static String status(){return REPLACEMENT?"typed17 active":"legacy fallback: "+refusal;}
    private static boolean pin(String path,String expected) throws Exception {
        try(InputStream in=NativeChainAdmission.class.getClassLoader().getResourceAsStream(path)) {
            boolean match=in!=null && HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(expected);
            if(!match)refusal="class pin "+path;
            return match;
        }
    }
    private static boolean booleanMethod(String type,String name) throws Exception {
        return (Boolean)Class.forName(type,false,NativeChainAdmission.class.getClassLoader()).getMethod(name).invoke(null);
    }
    private static String stringMethod(String type,String name) throws Exception {
        return (String)Class.forName(type,false,NativeChainAdmission.class.getClassLoader()).getMethod(name).invoke(null);
    }
    private static boolean admit() {
        try {
            if(NativeChainAdmission.class.getClassLoader().getResource("net/neoforged/fml/loading/FMLLoader.class")==null)return false;
            if(!pin("com/pp/shaderlod/optimization/unboundspatial/mixin/UnboundSpatialDrawMixin.class","764ce85bc105bd674008b71edc8fcd196d9eeefaf02783dda80a275d9198b35f"))return false;
            if(!pin("com/pp/shaderlod/optimization/reimaginedearlyalpha/mixin/ReimaginedAlphaDrawMixin.class","53fb9747acaf2c875c5e6039836495bdcad453feb36e91e532f8b3e77bc37b2b"))return false;
            if(!pin("com/pp/shaderlod/optimization/solasexact/mixin/SolasExactDrawMixin.class","b199dab6183ecd6e84bc8f87ccffce8a7a5791d9abbd78c58026e03e30960b71"))return false;
            if(!pin("com/pp/shaderlod/optimization/blisswater/mixin/ShadowLegacyDrawMixin.class","1e51d58e27cb7a9d095df20a0af55a788d8dff4ea543c364fa2d12aa78975804"))return false;
            if(!pin("com/pp/shaderlod/optimization/unboundearlyalpha/mixin/UnboundAlphaDrawMixin.class","b336ed5a128f7f616a581974f00a4fd7edf123935524bb401d50936502229811"))return false;
            if(!pin("com/pp/shaderlod/optimization/bslexact/mixin/BslExactDrawMixin.class","5ea2fa21d5cd9716bb18204b782c9d245a8b1719e308f6dad195ad9248ad574b"))return false;
            if(!pin("com/pp/shaderlod/optimization/makeupshadow/mixin/ShadowLegacyDrawMixin.class","f163afc6ac5ff52663f8265082867af526338278e7f4e70a0d163f25647bb7c5"))return false;
            if(!pin("com/pp/shaderlod/optimization/reimaginedspatial/mixin/ReimaginedSpatialDrawMixin.class","b5fd9dd0ceb7f0064beba96f5f07b1dde953d6a36f273348d02b98b2bc1c4536"))return false;
            if(!pin("com/pp/shaderlod/optimization/solasfade/mixin/SolasTraceFadeDrawMixin.class","d34d92a69d89caf62e27f5cc346eba171ab904f04a3ebd7789edf1ac7db00673"))return false;
            if(!pin("com/pp/shaderlod/optimization/bslearlyalpha/mixin/BslAlphaDrawMixin.class","5bc76a3d5da3cce3948d8095334ca373bc524603d81223c52f3aa406b1cf6352"))return false;
            if(!pin("com/pp/shaderlod/optimization/solasearlyalpha/mixin/SolasAlphaDrawMixin.class","b475d450f6d1f0c7fc4bb31dff656239facd7814b285675f7697eb9f11154e2d"))return false;
            if(!pin("com/pp/shaderlod/optimization/sildurearlyalpha/mixin/SildurAlphaDrawMixin.class","7bca4d6c1c116a099c7704946d7048cf7e017bdcf64e22919dd2657d3b6719bf"))return false;
            if(!pin("com/pp/shaderlod/optimization/astraearlyalpha/mixin/AstraAlphaDrawMixin.class","685e6dc1cda197ad3bc5d47cd044fadede278e3e545968b08b0cef395c063264"))return false;
            if(!pin("com/pp/shaderlod/optimization/photonearlyalpha/mixin/PhotonAlphaDrawMixin.class","bee854d122bc4c49a080c520611a0424bd21c6c6fd17ed3415d830836a4fe059"))return false;
            if(!pin("com/pp/shaderlod/optimization/makeupearlyalpha/mixin/MakeUpAlphaDrawMixin.class","c37b3e121156d899fe1e67b2a51a80f9dfbc56769b4fdcb9fa8c7a48ea62d209"))return false;
            if(!pin("com/pp/shaderlod/optimization/sildurexact/mixin/SildurExactDrawMixin.class","d4aa9c6634ca0bcc212b61d4ac632b9b581fe2b454f0bd2d441932901f31b7ac"))return false;
            if(!pin("com/pp/shaderlod/optimization/blissearlyalpha/mixin/BlissAlphaDrawMixin.class","f6b18eda10b136dc3665a9b1d635027943d5185da85a6d8479e6a0560f286f9e"))return false;
            if(!pin("net/irisshaders/iris/compat/sodium/mixin/MixinGlRenderDevice.class","fe12fede26a180c23e455b337047241cc36b9b1ffc20c4ac42e423c1e417c8e3"))return false;
            if(!pin("net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice$ImmediateDrawCommandList.class","60d7958e257a4ee5c22e9ca299dd8ca124c2f113d0d970b731526f510dae538b"))return false;
            if(!pin("com/pp/shaderlod/optimization/nativechain/NativeDraw.class","67ab6ef624a990baf9aab4b89e4b5b4ca51a493b9a2717e8f8510f81d9586afd"))return false;
            if(!pin("com/pp/shaderlod/optimization/nativechain/NativeDrawChain.class","3c2f074db72a30588952a2f5b2b95ac214ec538c38a1c5e99426d634735b2ae2"))return false;
            if(!pin("com/pp/shaderlod/optimization/nativechain/mixin/NativeDrawChainMixin.class","3bd1d6bc1392c370c605ffafe825e7a47207d95fb06c822c4f74c0c6718b07cc"))return false;
            if(!pin("com/pp/shaderlod/optimization/blissspatial/BlissSpatialDrawScope.class","60e11026d2de307a2b6c770fa8b819ef3e1f4feba1d0ddc6f33fd9a5572582d3"))return false;
            if(!pin("com/pp/shaderlod/optimization/bslexact/BslExactSsrRuntime.class","b078c9b09e1aa0317fd527d3fef7f9b373cc34fdb4bd76eef18f1f3728c3c673"))return false;
            if(!pin("com/pp/shaderlod/optimization/unboundspatial/UnboundExactSsrRuntime.class","40a31b2ea173ee891fd4965f842b2c5e8bf56f876c77b99ba29f04182d2937f6"))return false;
            if(!pin("com/pp/shaderlod/optimization/reimaginedspatial/ReimaginedExactSsrRuntime.class","73524a605c4cc5d8c2b7d73b85d94af566055d0e433ae35d9176e0e659463e6e"))return false;
            if(!pin("com/pp/shaderlod/optimization/solasexact/SolasExactSsrRuntime.class","5c21fdc4f67160a2ff8c9b89a15708fc942736f6cf884a4819206c6190c1762c"))return false;
            if(!pin("com/pp/shaderlod/optimization/solasexact/SolasReceiverRuntime.class","d0a9d23fdec827b4379ee29295de8540c891dcac89bfc334a86b7ba7745e4aa1"))return false;
            if(!pin("com/pp/shaderlod/optimization/sildurexact/SildurExactSsrRuntime.class","d088e6cfed2cb6cd15df6893aeee3f730ecd31ae9bb1ed1fecfc8e8c8e01850e"))return false;
            if(!pin("com/pp/shaderlod/optimization/makeupshadow/MakeUpReflectionRuntime.class","e42c635b311a8bfe99f00f80004c2769d940b38d71f09e29eb8a426f08c3c46d"))return false;
            if(!pin("com/pp/shaderlod/optimization/makeupshadow/MakeUpShadowRuntime.class","e000f3b32670b92fa18d6a58b86e189ca7736900d86c0c17a37de095f00cf357"))return false;
            if(!pin("com/pp/shaderlod/optimization/reimaginedspatial/ReimaginedSpatialRuntime.class","1e9c47ff9c5f639fbf14d30febfe98c56c9897d8389aa4ecbb14c2d9fe69fc9b"))return false;
            if(!pin("com/pp/shaderlod/optimization/unboundspatial/UnboundSpatialRuntime.class","fde7b5ad2dc987f6ac9033266fa4ed464913f890dd803b6ac14cb9576a94b419"))return false;
            return booleanMethod("com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowPins","exact") &&
                booleanMethod("com.pp.shaderlod.optimization.makeupearlyalpha.mixin.MakeUpEarlyAlphaPlugin","exactApi") &&
                booleanMethod("com.pp.shaderlod.optimization.bslearlyalpha.mixin.BslEarlyAlphaPlugin","exactApi") &&
                booleanMethod("com.pp.shaderlod.optimization.reimaginedearlyalpha.mixin.ReimaginedEarlyAlphaPlugin","exactApi") &&
                booleanMethod("com.pp.shaderlod.optimization.sildurearlyalpha.mixin.SildurEarlyAlphaPlugin","exactApi") &&
                booleanMethod("com.pp.shaderlod.optimization.blissearlyalpha.mixin.BlissEarlyAlphaPlugin","exactApi") &&
                booleanMethod("com.pp.shaderlod.optimization.solasearlyalpha.mixin.SolasEarlyAlphaPlugin","exactApi") &&
                booleanMethod("com.pp.shaderlod.optimization.astraearlyalpha.mixin.AstraEarlyAlphaPlugin","exactApi") &&
                booleanMethod("com.pp.shaderlod.optimization.photonearlyalpha.mixin.PhotonEarlyAlphaPlugin","exactApi") &&
                booleanMethod("com.pp.shaderlod.optimization.unboundearlyalpha.mixin.UnboundEarlyAlphaPlugin","exactApi") &&
                booleanMethod("com.pp.shaderlod.optimization.solasao.SolasAoApiPins","exact") &&
                com.pp.shaderlod.optimization.makeupao.MakeUpAoApiPins.exact("neoforge1.21.1") && com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowPins.exact() && "Legacy".equals(com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowPins.kind()) &&
                booleanMethod("com.pp.shaderlod.optimization.bslexact.mixin.BslExactPlugin","nativeDrawAdmitted") &&
                com.pp.shaderlod.optimization.unboundspatial.UnboundSpatialPins.exact() && com.pp.shaderlod.optimization.unboundprime.mixin.DepthMixinPlugin.exactApi() && com.pp.shaderlod.optimization.unboundao.UnboundAoApiPins.exact() && com.pp.shaderlod.optimization.unboundearlyalpha.mixin.UnboundEarlyAlphaPlugin.exactApi() && com.pp.shaderlod.optimization.blissspatial.BlissSpatialSamplerPins.exact() &&
                com.pp.shaderlod.optimization.reimaginedspatial.ReimaginedSpatialPins.exact() && com.pp.shaderlod.optimization.reimaginedprime.mixin.DepthMixinPlugin.exactApi() && com.pp.shaderlod.optimization.reimaginedao.ReimaginedAoApiPins.exact() && com.pp.shaderlod.optimization.reimaginedearlyalpha.mixin.ReimaginedEarlyAlphaPlugin.exactApi() && com.pp.shaderlod.optimization.blissspatial.BlissSpatialSamplerPins.exact() &&
                booleanMethod("com.pp.shaderlod.optimization.solasexact.mixin.SolasExactPlugin","nativeDrawAdmitted") &&
                booleanMethod("com.pp.shaderlod.optimization.sildurexact.mixin.SildurExactPlugin","nativeDrawAdmitted") &&
                "Legacy".equals(stringMethod("com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowPins","kind"));
        } catch(Exception|LinkageError unavailable) { return false; }
    }
}
