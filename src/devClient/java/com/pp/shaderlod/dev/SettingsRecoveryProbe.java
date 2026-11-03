package com.pp.shaderlod.dev;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.ShaderLodConfig;
import com.pp.shaderlod.ShaderLodSettings;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import com.pp.shaderlod.optimization.CompileOptionControl;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import net.irisshaders.iris.Iris;

/** Development-only fault after the real forward reload; uses the ordinary core recovery caller. */
public final class SettingsRecoveryProbe {
    private SettingsRecoveryProbe() {}
    public record CancelResult(String feature, boolean optimizationCancel, boolean settingsCancel,
            boolean selectionUnchanged, boolean flagsUnchanged, boolean settingsUnchanged) {}
    /** Press the actual staged controls and actual Cancel buttons on both product screens. */
    public static CancelResult cancel(String key) throws Exception {
        var id=java.util.Arrays.stream(com.pp.shaderlod.optimization.OptimizationId.values())
            .filter(v->v.key().equals(key)).findFirst().orElseThrow();
        if(id!=com.pp.shaderlod.optimization.OptimizationId.TRANSLUCENT_FASTPATH
                &&id!=com.pp.shaderlod.optimization.OptimizationId.AO_ATTENUATION
                &&id!=com.pp.shaderlod.optimization.OptimizationId.DEAD_MIP_CHAIN
                &&id!=com.pp.shaderlod.optimization.OptimizationId.EARLY_ALPHA
                &&id!=com.pp.shaderlod.optimization.OptimizationId.DEAD_FRAME_COPY)
            throw new IllegalArgumentException("Exact singleton feature required");
        var client=net.minecraft.client.Minecraft.getInstance();
        if(client.screen!=null||client.level==null||Iris.isFallback())throw new IllegalStateException("Focused settled world required");
        var flags=com.pp.shaderlod.optimization.OptimizationConfig.snapshot();
        if(!flags.enabled(id))throw new IllegalStateException("Requested feature must be active");
        var pack=Iris.getCurrentPack().orElseThrow();var pipeline=Iris.getPipelineManager().getPipelineNullable();
        String name=Iris.getCurrentPackName();var options=com.pp.shaderlod.optimization.mips.DeadMipAdmission.effective(pack);
        var generation=com.pp.shaderlod.optimization.OptimizationConfig.generation();
        boolean enabled=ShaderLodConfig.ENABLED.get();var preset=ShaderLodConfig.PRESET.get();
        var queue=java.util.Map.copyOf(Iris.getShaderPackOptionQueue());
        try {
            var screen=new com.pp.shaderlod.OptimizationScreen(null);
            var selected=screen.getClass().getDeclaredField("selected");selected.setAccessible(true);selected.setInt(screen,id.ordinal());
            client.setScreen(screen);
            press(screen,"Requested: On");
            var staged=screen.getClass().getDeclaredField("staged");staged.setAccessible(true);
            if(((com.pp.shaderlod.optimization.OptimizationSnapshot)staged.get(screen)).enabled(id))throw new IllegalStateException("Staged toggle failed");
            press(screen,"Cancel");
            if(client.screen!=null||!flags.equals(com.pp.shaderlod.optimization.OptimizationConfig.snapshot()))throw new IllegalStateException("Optimization Cancel published edits");
            var settings=new com.pp.shaderlod.ShaderLodSettingsScreen(null,null);client.setScreen(settings);
            var toggle=settings.children().stream().filter(v->v instanceof net.minecraft.client.gui.components.CycleButton<?> b&&b.getMessage().getString().startsWith("LOD:")).findFirst().orElseThrow();
            ((net.minecraft.client.gui.components.CycleButton<?>)toggle).onPress();
            var pending=settings.getClass().getDeclaredField("enabled");pending.setAccessible(true);
            if(pending.getBoolean(settings)==enabled)throw new IllegalStateException("Staged settings toggle failed");
            press(settings,"Cancel");
            boolean selection=client.screen==null&&Iris.getCurrentPack().orElse(null)==pack
                &&Iris.getPipelineManager().getPipelineNullable()==pipeline&&name.equals(Iris.getCurrentPackName())
                &&options.equals(com.pp.shaderlod.optimization.mips.DeadMipAdmission.effective(pack))
                &&queue.equals(Iris.getShaderPackOptionQueue());
            boolean sameFlags=flags.equals(com.pp.shaderlod.optimization.OptimizationConfig.snapshot())
                &&generation==com.pp.shaderlod.optimization.OptimizationConfig.generation();
            boolean sameSettings=ShaderLodConfig.ENABLED.get()==enabled&&ShaderLodConfig.PRESET.get()==preset;
            if(!selection||!sameFlags||!sameSettings)throw new IllegalStateException("Cancel changed live state");
            return new CancelResult(key,true,true,selection,sameFlags,sameSettings);
        }finally{client.setScreen(null);}
    }
    private static void press(net.minecraft.client.gui.screens.Screen screen,String label) {
        var matches=screen.children().stream().filter(v->v instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(label)).toList();
        if(matches.size()!=1)throw new IllegalStateException("Expected one actual button: "+label);
        ((net.minecraft.client.gui.components.Button)matches.getFirst()).onPress();
    }

    public record Result(boolean injected, boolean applied, String forwardPack, String recoveredPack) {}
    public static Result check(boolean enabled, LodPreset preset) throws Exception {
        var pack=Iris.getCurrentPack().orElseThrow();
        var options=com.pp.shaderlod.optimization.mips.DeadMipAdmission.effective(pack);
        String family=com.pp.shaderlod.optimization.OptimizationConfig.activeFamily();
        boolean candidate=("makeup-ultra-fast-shaders".equals(family)
                && ((com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.profile(options)
                    && ("1".equals(options.get(CompileOptionControl.MAKEUP_TRANSLUCENT_FASTPATH))||"1".equals(options.get(CompileOptionControl.MAKEUP_AO_ATTENUATION))))
                    || "1".equals(options.get(CompileOptionControl.MAKEUP_DEAD_FRAME_COPY))
                    || ("1".equals(options.get(CompileOptionControl.MAKEUP_EARLY_ALPHA))
                        && com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.profile(options))))
            || ("bsl-shaders".equals(family)
                && "1".equals(options.get(CompileOptionControl.BSL_DEAD_MIP))
                && com.pp.shaderlod.optimization.mips.DeadMipAdmission.c6Capability(pack))
            || ("bsl-shaders".equals(family)
                && com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlpha.singleton(com.pp.shaderlod.optimization.OptimizationConfig.snapshot())
                && com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlpha.active());
        if(!ShaderLodConfig.ENABLED.get()||ShaderLodConfig.PRESET.get()!=LodPreset.PERFORMANCE
                || !candidate
                || (enabled&&preset!=LodPreset.QUALITY)||(!enabled&&preset!=LodPreset.PERFORMANCE))
            throw new IllegalStateException("Active G01, exact singleton G03 or experimental BSL c6+c7 Performance graph and Off/Quality transition required");
        boolean nativeAvailable=MakeUpQuadRuntime.nativeGraphAvailable();
        boolean[] injected={false};String[] forward={null};
        // Access the existing test seam without adding a production fault-injection API or
        // introducing a split package between the main and developer named modules.
        var reloadType=Class.forName("com.pp.shaderlod.ShaderLodSettings$Reload");
        var forwardType=Class.forName("com.pp.shaderlod.ShaderLodSettings$ForwardReload");
        var reload=Proxy.newProxyInstance(reloadType.getClassLoader(),new Class<?>[]{reloadType},(proxy,method,args)->{
            Iris.reload();return null;
        });
        var forwardReload=Proxy.newProxyInstance(forwardType.getClassLoader(),new Class<?>[]{forwardType},(proxy,method,args)->{
                CompileOptionControl.reloadSettings((PackSelectionTransaction)args[0],Iris::reload);
                if(Iris.isFallback())throw new IllegalStateException("Forward graph unexpectedly fell back");
                forward[0]=Iris.getCurrentPackName();injected[0]=true;
                throw new IOException("Intentional development fault after completed forward settings reload");
            });
        var apply=ShaderLodSettings.class.getDeclaredMethod("apply",boolean.class,LodPreset.class,
            Supplier.class,reloadType,BooleanSupplier.class,forwardType);
        apply.setAccessible(true);
        final boolean applied;
        try {
            applied=(Boolean)apply.invoke(null,enabled,preset,(Supplier<PackSelectionTransaction>)()->PackSelectionTransaction.iris(nativeAvailable),
                reload,(BooleanSupplier)Iris::isFallback,forwardReload);
        }catch(InvocationTargetException failure){
            if(failure.getCause() instanceof Exception cause)throw cause;
            if(failure.getCause() instanceof Error cause)throw cause;
            throw failure;
        }
        if(applied||!injected[0])throw new IllegalStateException("Requested recovery fault was not exercised");
        return new Result(true,false,forward[0],Iris.getCurrentPackName());
    }
}
