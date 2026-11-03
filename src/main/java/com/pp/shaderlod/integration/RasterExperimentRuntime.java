package com.pp.shaderlod.integration;

import net.minecraft.SharedConstants;
import net.neoforged.fml.ModList;

/** Exact local NeoForge runtime admission for selection only; not a rendering certificate. */
public final class RasterExperimentRuntime {
    private RasterExperimentRuntime() {}
    public static RasterExperimentSelection.RuntimePins current() {
        try {
            // Check presence before touching config or core classes. Missing mod is native fallback.
            var mods = ModList.get();
            if (!mods.isLoaded("shaderlod") || !mods.isLoaded("neoforge")) return null;
            String neo = mods.getModContainerById("neoforge").orElseThrow()
                    .getModInfo().getVersion().toString();
            String minecraft = SharedConstants.getCurrentVersion().getName();
            if (!"21.1.253".equals(neo) || !"1.21.1".equals(minecraft)) return null;
            return new RasterExperimentSelection.RuntimePins(true, "neoforge", minecraft, neo,
                    artifactHash("iris"), artifactHash("sodium"));
        } catch (Exception | LinkageError unavailable) { return null; }
    }
    private static String artifactHash(String id) throws Exception {
        // SecureJar class CodeSource URLs can use union:, so use the actual loader artifact
        // locator, matching the existing LoadedArtifactEvidence/ComplementaryCopyGraph method.
        String result = null;
        int matches = 0;
        for (var file : ModList.get().getModFiles()) {
            if (file.getMods().stream().noneMatch(mod -> mod.getModId().equals(id))) continue;
            matches++;
            result = RasterExperimentSelection.hashFile(file.getFile().getFilePath());
        }
        return matches == 1 ? result : null;
    }
}
