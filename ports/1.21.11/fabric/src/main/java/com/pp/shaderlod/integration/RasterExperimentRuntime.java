package com.pp.shaderlod.integration;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
/** Actual Fabric container origins. Native1.21.1 experiment pins remain enforced by selection. */
public final class RasterExperimentRuntime {
    private RasterExperimentRuntime() {}
    public static RasterExperimentSelection.RuntimePins current() {
        try {
            var loader=FabricLoader.getInstance();
            if (!loader.isModLoaded("shaderlod")) return null;
            return new RasterExperimentSelection.RuntimePins(true,"fabric",
                SharedConstants.getCurrentVersion().name(),null,artifactHash("iris"),artifactHash("sodium"));
        } catch (Exception | LinkageError unavailable) { return null; }
    }
    private static String artifactHash(String id) throws Exception {
        var origin=FabricLoader.getInstance().getModContainer(id).orElseThrow().getOrigin();
        if (origin.getKind()!=net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH) return null;
        var paths=origin.getPaths();
        return paths.size()==1 ? RasterExperimentSelection.hashFile(paths.getFirst()) : null;
    }
}
