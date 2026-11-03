package com.pp.shaderlod.integration.makeupquad;
import java.security.MessageDigest;
import java.util.HexFormat;
/** Optional mixin bootstrap: JDK only, never links Iris/Sodium/Minecraft classes. */
public final class MakeUpQuadApiPins {
    private MakeUpQuadApiPins() {}
    private static final String[][][] PIN_SETS = {
        {
            {"net/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch.class","9a6cbab56d5f700347953396acdfd32a58b275bfb2f51bed503cee644ae06065"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","6b907ff387fe928561fed89b78c17503fddfd6a51adb092a39f83dd872ed9ba4"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer.class","58ec28661486626ad4086b0481038f6e22b04da312aba4c216a4123793e59bb0"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexType.class","a16628a672be82cc6123a8800be22303f2758caa254fe48a73fcde592b873746"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexType$2.class","3c4a79049c5edd181e1f57a7bf26bd482b717a184a55205a981694bb5773ab05"},
            {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","dc02ac12e10c5c2862ed2d2a719dcbdaab2212157b0893a30ece886e54e69f8c"},
            {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","b41b371736edda913f735c37e01be07acadc11379a560a4ef6eac1ea81803c18"},
            {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","8a9736586a37082cf1ba21d742d20542662faabd1e97756e8bf9e06bff6605ac"},
            {"net/irisshaders/iris/compat/sodium/mixin/MixinDefaultChunkRenderer.class","8f021d9d8e0517832c1942cf00d659d8a2e4aff14503356bb34e251e4210aca7"},
            {"net/irisshaders/iris/shaderpack/option/ShaderPackOptions.class","bec6e7825307767a3b6a29093a86d6cd04156d0a4dd3a359bee59a932dc8e319"},
            {"net/irisshaders/iris/shaderpack/option/values/MutableOptionValues.class","d1047ca1facbaac769c1b5853b7dba0a0a938dfc2fc9fee9cb03fd12f5ae97fb"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataUnsafe.class","822d4ae73560c12564aae06c742889f9a83d3b00a29123659495b06327f10245"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/DefaultTerrainRenderPasses.class","8527f805c6e760cbbe80a83808129ff6ebceaca0408d4030be28e602fc5d3dfc"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderList.class","5b5012af10cd13bd5c73878caf4a3323fa6c4a77adc24a803b59b6de540571b3"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkMeshFormats.class","25626adde7ee361ff1f2f17aac63b40f7cebed9dc126d26272c3e4e5cb63b430"},
            {"net/irisshaders/iris/vertices/sodium/terrain/XHFPTerrainVertex.class","837205b9c2f6339c65b272d53d562ee0bd02a1ad1969f8b2c60facfd6040d1d6"}
        },
        {
            {"net/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch.class","9a6cbab56d5f700347953396acdfd32a58b275bfb2f51bed503cee644ae06065"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","414ef07e6511cf1aabd67cec292e9d27725acad740cbdc8015cc49f90f44f292"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer.class","58ec28661486626ad4086b0481038f6e22b04da312aba4c216a4123793e59bb0"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexType.class","a16628a672be82cc6123a8800be22303f2758caa254fe48a73fcde592b873746"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexType$2.class","3c4a79049c5edd181e1f57a7bf26bd482b717a184a55205a981694bb5773ab05"},
            {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","a1a8c3b59e50ce2cfea5e62263632f60a9a93f44417b9d9086ed12882a2238ae"},
            {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","c9098ede6d1e24c7f85c9bdad6e5a255d0bf1abcb94e3614aa8255fe8ff76b4d"},
            {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","8a9736586a37082cf1ba21d742d20542662faabd1e97756e8bf9e06bff6605ac"},
            {"net/irisshaders/iris/compat/sodium/mixin/MixinDefaultChunkRenderer.class","8f021d9d8e0517832c1942cf00d659d8a2e4aff14503356bb34e251e4210aca7"},
            {"net/irisshaders/iris/shaderpack/option/ShaderPackOptions.class","bec6e7825307767a3b6a29093a86d6cd04156d0a4dd3a359bee59a932dc8e319"},
            {"net/irisshaders/iris/shaderpack/option/values/MutableOptionValues.class","d1047ca1facbaac769c1b5853b7dba0a0a938dfc2fc9fee9cb03fd12f5ae97fb"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataUnsafe.class","822d4ae73560c12564aae06c742889f9a83d3b00a29123659495b06327f10245"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/DefaultTerrainRenderPasses.class","4edd7e8c4c9113a3e2912e1e8c3ad7d99267c20e65607ec61ded39daac8f7536"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderList.class","acc43a435c8b9ce58b4d3923df5dd05ae98f096f28235e5c59c17a9814e03905"},
            {"net/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkMeshFormats.class","25626adde7ee361ff1f2f17aac63b40f7cebed9dc126d26272c3e4e5cb63b430"},
            {"net/irisshaders/iris/vertices/sodium/terrain/XHFPTerrainVertex.class","d719fef782239c4401232d46d2dc84f9b910d14f2ad17c0b16697c2757b7af64"}
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
