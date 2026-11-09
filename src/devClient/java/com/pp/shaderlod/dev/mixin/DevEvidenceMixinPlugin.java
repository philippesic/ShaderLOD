package com.pp.shaderlod.dev.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Keeps the diagnostic draw wrapper out of ordinary development timing runs. */
public final class DevEvidenceMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { com.pp.shaderlod.dev.ShaderAnimationClock.fixed(); }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if(mixinClassName.endsWith(".DhTransferTimingMixin") || mixinClassName.endsWith(".DhWorkerTimingMixin") || mixinClassName.endsWith(".DhUploadTimingMixin"))return Boolean.getBoolean("shaderlod.dev.costMap");
        if(mixinClassName.endsWith(".CostMapNativeMixin"))return Boolean.getBoolean("shaderlod.dev.costMap");
        if(mixinClassName.endsWith(".CostMapOperationsMixin") || mixinClassName.endsWith(".CostMapPresentationMixin"))return Boolean.getBoolean("shaderlod.dev.costMap");
        if(mixinClassName.endsWith(".SildurLeanDrawMixin"))return Boolean.getBoolean("shaderlod.dev.sildurLeanAlpha")
            &&com.pp.shaderlod.optimization.sildurearlyalpha.mixin.SildurEarlyAlphaPlugin.exactApi();
        if(mixinClassName.contains(".MipFamily"))return Boolean.getBoolean("shaderlod.dev.mipFamilyRuntime")&&com.pp.shaderlod.optimization.mips.BslMipWitnessPins.exactApi();
        if(mixinClassName.contains(".G08Bound")) {
            if(!Boolean.getBoolean("shaderlod.dev.g08BoundRuntime"))return false;
            try{return (Boolean)Class.forName("com.pp.shaderlod.optimization.vrs.BloomVrsPins").getMethod("exact").invoke(null);}
            catch(ReflectiveOperationException|LinkageError unavailable){return false;}
        }
        if(mixinClassName.contains(".G08Cloud")) {
            if(!Boolean.getBoolean("shaderlod.dev.g08CloudRuntime"))return false;
            try{return (Boolean)Class.forName("com.pp.shaderlod.optimization.vrs.BloomVrsPins").getMethod("exact").invoke(null);}
            catch(ReflectiveOperationException|LinkageError unavailable){return false;}
        }
        if(mixinClassName.contains(".G10Bloom")) {
            if(!Boolean.getBoolean("shaderlod.dev.g10BenchmarkRuntime"))return false;
            try{return (Boolean)Class.forName("com.pp.shaderlod.optimization.vrs.BloomVrsPins").getMethod("exact").invoke(null);}
            catch(ReflectiveOperationException|LinkageError unavailable){return false;}
        }
        if (mixinClassName.endsWith(".G01GraphFinalMixin") || mixinClassName.endsWith(".G01TemporalMixin") || mixinClassName.endsWith(".G01PayloadDrawMixin")) return Boolean.getBoolean("shaderlod.dev.g01ExposureProbe");
        if (mixinClassName.endsWith(".BslMipWitnessMixin")) {
            if (!Boolean.getBoolean("shaderlod.dev.bslMipWitnessRuntime")) return false;
            try { return (Boolean)Class.forName("com.pp.shaderlod.optimization.mips.BslMipWitnessPins")
                    .getMethod("exactApi").invoke(null); }
            catch (ReflectiveOperationException | LinkageError absentOrUnsupported) { return false; }
        }
        if (mixinClassName.endsWith(".NativeSubmitEntryMixin") || mixinClassName.endsWith(".NativeSubmitChainEntryMixin") || mixinClassName.endsWith(".NativeSubmitScopeMixin")) {
            if (!Boolean.getBoolean("shaderlod.dev.nativeSubmitObserver") || System.getProperty("shaderlod.dev.benchmarkTunerProbe", "").isBlank()) return false;
            boolean replacement=com.pp.shaderlod.optimization.nativechain.NativeChainAdmission.useReplacement();
            return mixinClassName.endsWith(".NativeSubmitScopeMixin") || (mixinClassName.endsWith(".NativeSubmitChainEntryMixin") == replacement);
        }
        if (mixinClassName.contains(".HalfAoUv")) return !System.getProperty("shaderlod.dev.benchmarkTunerProbe", "").isBlank();
        // Checking the launch flag must not initialize the optional observer: its
        // static linkage evidence belongs to the production mod, absent in clientLodOff.
        if (mixinClassName.contains(".NativeMacro")) return Boolean.getBoolean("shaderlod.dev.nativeMacroInspection");
        if (mixinClassName.endsWith(".ShaderAnimationClockMixin")) return com.pp.shaderlod.dev.ShaderAnimationClock.fixed();
        if (mixinClassName.endsWith(".PhysicalResolutionTargetsMixin") || mixinClassName.endsWith(".PhysicalResolutionPipelineMixin"))
            return Boolean.getBoolean("shaderlod.dev.physicalResolution");
        if (mixinClassName.endsWith(".PixelHistoryFrameMixin")) return Boolean.getBoolean("shaderlod.dev.pixelHistoryCapture");
        if (mixinClassName.endsWith(".SpatialPassAccessor") || mixinClassName.endsWith(".SpatialCompositeMixin")
                || mixinClassName.endsWith(".SpatialQuadMixin") || mixinClassName.endsWith(".SpatialFrameMixin")) {
            return (!mixinClassName.endsWith(".SpatialFrameMixin") && !System.getProperty("shaderlod.dev.benchmarkTunerProbe", "").isBlank())
                    || (!mixinClassName.endsWith(".SpatialFrameMixin") && !System.getProperty("shaderlod.dev.makeupMrtProbe", "").isBlank())
                    || Boolean.getBoolean("shaderlod.dev.spatialLayoutProbe")
                    || (!mixinClassName.endsWith(".SpatialFrameMixin")
                        && !System.getProperty("shaderlod.dev.settledConsumerUniforms", "").isBlank())
                    || Boolean.getBoolean("shaderlod.dev.pixelCoverage")
                    || Boolean.getBoolean("shaderlod.dev.pixelHistoryCapture")
                    || (!mixinClassName.endsWith(".SpatialFrameMixin")
                        && !System.getProperty("shaderlod.dev.blissChainWitness", "").isBlank())
                    || (!mixinClassName.endsWith(".SpatialFrameMixin")
                        && !System.getProperty("shaderlod.dev.sildurReceiverWitness", "").isBlank())
                    || (!mixinClassName.endsWith(".SpatialFrameMixin")
                        && !System.getProperty("shaderlod.dev.makeupCloudWitness", "").isBlank())
                    || (!mixinClassName.endsWith(".SpatialFrameMixin")
                        && !System.getProperty("shaderlod.dev.bslMipWitness", "").isBlank())
                    || (!mixinClassName.endsWith(".SpatialFrameMixin")
                        && !System.getProperty("shaderlod.dev.bslMipParity", "").isBlank())
                    || (!mixinClassName.endsWith(".SpatialFrameMixin")
                        && !System.getProperty("shaderlod.dev.compositeCopyWitness", "").isBlank())
                    || (!mixinClassName.endsWith(".SpatialFrameMixin")
                        && !System.getProperty("shaderlod.dev.complementaryCopyGraph", "").isBlank())
                    || (!mixinClassName.endsWith(".SpatialFrameMixin")
                        && !System.getProperty("shaderlod.dev.rasterActivationProbe", "").isBlank());
        }
        if (mixinClassName.endsWith(".WorkTraceReadbackMixin")) {
            return Boolean.getBoolean("shaderlod.dev.workTrace");
        }
        if (mixinClassName.endsWith(".RasterSamplerStateMixin")) {
            return Boolean.getBoolean("shaderlod.dev.cloudSamplerProbe")
                    || !System.getProperty("shaderlod.dev.rasterActivationProbe", "").isBlank();
        }
        return !mixinClassName.endsWith(".DhDrawEvidenceMixin")
                || Boolean.getBoolean("shaderlod.dev.dhDrawEvidence");
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
