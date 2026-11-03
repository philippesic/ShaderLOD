package com.pp.shaderlod.benchmark.mixin;

import com.pp.shaderlod.benchmark.BenchmarkLiveWork;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/** Version/loader admission only; no file hashes, GL work, runtime polling or optional class loading. */
public final class BenchmarkObservationPlugin implements IMixinConfigPlugin {
    public static boolean admitted(String sodium, String iris, String dh, boolean dhHook) {
        boolean tuple = "0.6.13+mc1.21.1".equals(sodium)
                && "1.8.12-snapshot+mc1.21.1-local".equals(iris);
        return tuple && (!dhHook || "3.3.3".equals(dh));
    }
    private static String version(String id) {
        var list = FMLLoader.getLoadingModList();
        if (list == null) return "";
        return list.getMods().stream().filter(m -> id.equals(m.getModId()))
                .map(m -> m.getVersion().toString()).findFirst().orElse("");
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return admitted(version("sodium"), version("iris"), version("distanthorizons"), (mixin.endsWith("BenchmarkDhDrawMixin") || mixin.endsWith("BenchmarkDhBeaconSleepMixin")));
    }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (mixin.endsWith("BenchmarkDhBeaconSleepMixin")) com.pp.shaderlod.benchmark.BenchmarkDhMaintenance.hookInstalled();
        BenchmarkLiveWork.hooksInstalled(mixin.endsWith("BenchmarkSodiumDrawMixin"),
                mixin.endsWith("BenchmarkSodiumLayerMixin"), mixin.endsWith("BenchmarkDhDrawMixin"));
    }
    @Override public void onLoad(String pkg) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
