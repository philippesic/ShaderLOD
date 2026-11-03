package com.pp.shaderlod.integration;

import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.SharedConstants;

/** Authentic Fabric admission inputs; existing NeoForge-only research certificates still refuse them. */
public final class RasterExperimentRuntime {
    private RasterExperimentRuntime() {}
    public static RasterExperimentSelection.RuntimePins current() {
        try {
            var mods = FabricLoader.getInstance();
            if (!mods.isModLoaded("shaderlod") || !mods.isModLoaded("fabricloader")) return null;
            String loader = mods.getModContainer("fabricloader").orElseThrow()
                    .getMetadata().getVersion().getFriendlyString();
            String minecraft = SharedConstants.getCurrentVersion().getName();
            if (!"0.19.5".equals(loader) || !"1.21.1".equals(minecraft)) return null;
            return new RasterExperimentSelection.RuntimePins(true, "fabric", minecraft, loader,
                    artifactHash("iris"), artifactHash("sodium"));
        } catch (Exception | LinkageError unavailable) { return null; }
    }
    private static String artifactHash(String id) throws Exception {
        var mod = FabricLoader.getInstance().getModContainer(id).orElse(null);
        if (mod == null || mod.getOrigin().getKind() != ModOrigin.Kind.PATH) return null;
        var paths = mod.getOrigin().getPaths();
        if (paths.size() != 1 || !Files.isRegularFile(paths.getFirst())) return null;
        return RasterExperimentSelection.hashFile(paths.getFirst());
    }
}
