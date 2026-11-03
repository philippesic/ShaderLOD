package com.pp.shaderlod.optimization.g07;

import java.nio.file.Files;
import java.nio.file.Path;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import com.pp.shaderlod.optimization.framecopy.FrameCopyPins;
import com.pp.shaderlod.optimization.sourcecache.EffectiveOptions;
import com.pp.shaderlod.optimization.SourceArchiveDigest;
import com.pp.shaderlod.optimization.OptimizationRuntimeContext;
import com.pp.shaderlod.optimization.OptimizationId;
import com.pp.shaderlod.LodPreset;

/** Fail closed on new pack, queued edits, changed resources, DH or unknown stack. No GL calls. */
public final class G07SamplingRuntime {
    private static final OptimizationId[] IDS = OptimizationId.values();
    private static Object checkedPack;
    private static String checkedName;
    private static boolean checkedSource;
    private static Boolean checkedStack;
    private static java.util.Map<String,String> checkedOptions;
    private static boolean optionsAccepted;
    private G07SamplingRuntime() {}

    public static int admitted() {
        try {
            var core = OptimizationRuntimeContext.core();
            if (core == null || !core.settings().enabled() || core.preset() != LodPreset.PERFORMANCE) return 0;
            for (var id : IDS) if (core.optimizations().enabled(id)) return 0;
            var pack = Iris.getCurrentPack().orElse(null);
            if (pack == null || Iris.isFallback() || !Iris.getShaderPackOptionQueue().isEmpty()) return 0;
            var effective = EffectiveOptions.current(pack.getShaderPackOptions());
            // Native and unrelated packs do not incur source, loader or resource census.
            // EffectiveOptions revalidates mutable overrides before returning its immutable snapshot.
            // A mutation returns the empty map; a reload creates a new snapshot identity.
            if (effective != checkedOptions) {
                optionsAccepted = G07SamplingAdmission.options(effective);
                checkedOptions = effective;
            }
            if (!optionsAccepted) return 0;
            var client = Minecraft.getInstance();
            if (client.level == null || !client.level.dimension().location().toString().equals("minecraft:overworld")) return 0;
            if (!G07SamplingAdmission.resources(client.getResourcePackRepository().getSelectedIds())) return 0;
            // ModList is immutable after client startup. Resource and queued-option guards remain live.
            if (checkedStack == null) checkedStack = FrameCopyPins.exactApi()
                && G07SamplingAdmission.stack(MakeUpQuadRuntime.loadedStack());
            if (!checkedStack) return 0;
            if (ModList.get().isLoaded("distanthorizons") || ModList.get().isLoaded("voxy")) return 0;
            String name = Iris.getIrisConfig().getShaderPackName().orElse(null);
            if (name == null) return 0;
            if (pack != checkedPack || !name.equals(checkedName)) {
                checkedSource = false;
                checkedPack = pack;
                checkedName = name;
                Path root = Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
                Path selected = root.resolve(name).normalize();
                if (selected.startsWith(root) && !selected.equals(root) && Files.isRegularFile(selected))
                    checkedSource = G07SamplingAdmission.ARCHIVE_SHA.equals(SourceArchiveDigest.sha256(selected));
            }
            return checkedSource ? 1 : 0;
        } catch (Exception | LinkageError unavailable) {
            return 0;
        }
    }
}
