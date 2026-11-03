package com.pp.shaderlod.optimization;
import net.irisshaders.iris.Iris;
import java.util.*;

/** BSL native0 recovery must run before a LevelRenderer captures its pipeline.
 * Never call this from CompositeRenderer.renderAll or an active old pipeline. */
public final class BslAoNativeRecovery {
    private static boolean requested,recovering;
    private static final AoNativeFallback FALLBACK=new AoNativeFallback();
    private static String reason;
    private BslAoNativeRecovery() {}
    public static void request(String loss) {requested=true;reason=Objects.requireNonNull(loss);}
    public static boolean requested() {return requested;}
    public static String reason() {return reason;}
    public static void foreignReplacement() {requested=false;reason=null;FALLBACK.clear();}
    public static AoNativeFallback.Receipt takeFallback(){return FALLBACK.take();}
    public static void onReload(){if(!recovering)FALLBACK.clear();}
    public static boolean declaredRecovery(net.irisshaders.iris.shaderpack.option.OptionSet options,
            net.irisshaders.iris.shaderpack.option.values.OptionValues values,boolean qualifiedBslSource) {
        return com.pp.shaderlod.optimization.bsl.BslAoOptionGuard.half(options,values,qualifiedBslSource).isPresent();
    }
    @FunctionalInterface public interface Reload {void run() throws Exception;}
    /** Exact queue ownership operation reused by the real Iris entry below. */
    public static void nativeQueueReload(Map<String,String> queue,Reload reload) throws Exception {
        var previous=Map.copyOf(queue);
        previous.forEach((key,value)->queue.remove(key,value));
        queue.put("SHADERLOD_BSL_AO_HALF_RES","0");
        try {reload.run();}
        finally {
            queue.remove("SHADERLOD_BSL_AO_HALF_RES","0");
            previous.forEach((key,value)->{
                // Recovery owns native0; an obsolete pending half1 must not
                // re-enable stale resources. All unrelated user options survive.
                if(!key.equals("SHADERLOD_BSL_AO_HALF_RES"))queue.putIfAbsent(key,value);
            });
        }
    }
    /** Real Iris reload; caller must first revoke family AO setup admission and
     * pass a proven pre-capture/no-active-frame boundary. False is not completion. */
    public static boolean beforePipelineCapture(boolean activeFrame,boolean familyAdmissionRevoked,
            net.irisshaders.iris.shaderpack.materialmap.NamespacedId dimension) throws Exception {
        if(!requested||recovering)return false;
        if(activeFrame||!familyAdmissionRevoked)throw new IllegalStateException("AO recovery is not at a safe native pipeline boundary");
        var loaded=Iris.getCurrentPack().orElse(null);
        if(loaded==null)return false;
        var loadedOptions=loaded.getShaderPackOptions();
        if(!declaredRecovery(loadedOptions.getOptionSet(),loadedOptions.getOptionValues(),
           com.pp.shaderlod.optimization.bsl.BslAoNormalBridge.qualifySelectedSource())){foreignReplacement();return false;}
        recovering=true;
        try {
            var oldPipeline=Iris.getPipelineManager().getPipelineNullable();
            var world=net.minecraft.client.Minecraft.getInstance().level;
            var oldSelected=Iris.getIrisConfig().getShaderPackName().orElse(null);
            long generation=OptimizationConfig.generation();var before=snapshot(loaded);
            boolean emptyQueue=Iris.getShaderPackOptionQueue().isEmpty();String loss=reason;FALLBACK.clear();
            nativeQueueReload(Iris.getShaderPackOptionQueue(),Iris::reload);
            var pack=Iris.getCurrentPack().orElseThrow();
            var options=pack.getShaderPackOptions();
            String value=com.pp.shaderlod.optimization.bsl.BslAoOptionGuard.half(options.getOptionSet(),options.getOptionValues(),
                com.pp.shaderlod.optimization.bsl.BslAoNormalBridge.qualifySelectedSource()).orElseThrow(()->new IllegalStateException("Restored pack is not declared/source-qualified BSL AO"));
            if(!value.equals("0"))throw new IllegalStateException("Native AO graph0 did not load");
            // Bind the actual requested-dimension result before the outer caller captures it.
            // Iris.reload already prepares a loaded world's current dimension; this normally reuses that cache.
            var nativePipeline=Iris.getPipelineManager().preparePipeline(dimension);
            if(nativePipeline==null||nativePipeline!=Iris.getPipelineManager().getPipelineNullable())
                throw new IllegalStateException("Native BSL AO pipeline was not prepared for capture");
            if(generation==OptimizationConfig.generation()&&world==net.minecraft.client.Minecraft.getInstance().level)
                FALLBACK.publish(new AoNativeFallback.Receipt("bsl-shaders",loaded,pack,oldPipeline,
                    Iris.getPipelineManager().getPipelineNullable(),world,dimension,generation,oldSelected,
                    Iris.getIrisConfig().getShaderPackName().orElse(null),before,snapshot(pack),
                    emptyQueue&&Iris.getShaderPackOptionQueue().isEmpty(),loss));
            requested=false;return true;
        } finally {recovering=false;}
    }
    /** Called only after the existing exact selected-source and typed AO declaration proof. */
    private static Map<String,String> snapshot(net.irisshaders.iris.shaderpack.ShaderPack pack) {if(pack==null)return Map.of();return com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(pack.getShaderPackOptions());}
}
