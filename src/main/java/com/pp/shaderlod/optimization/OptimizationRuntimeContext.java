package com.pp.shaderlod.optimization;
import com.pp.shaderlod.CoreUniformFrame;
import java.util.function.Supplier;
/** Explicit loader-provided core snapshot; absent/unloaded config admits no compile-time reduction. */
public final class OptimizationRuntimeContext {
    @FunctionalInterface public interface Preflight {void run() throws Exception;}
    private static Supplier<CoreUniformFrame.Core> core;
    private static Preflight preflight=()->{};
    private static final java.util.Map<OptimizationId,java.util.function.Supplier<String>> implementations=new java.util.EnumMap<>(OptimizationId.class);
    private static final java.util.Map<OptimizationId,java.util.function.BooleanSupplier> admissions=new java.util.EnumMap<>(OptimizationId.class);
    private static final java.util.Map<String,java.util.Map<OptimizationId,java.util.function.Supplier<String>>> familyImplementations=new java.util.HashMap<>();
    private OptimizationRuntimeContext() {}
    public static void configure(Supplier<CoreUniformFrame.Core> supplier) {core=supplier;}
    public static void configure(Supplier<CoreUniformFrame.Core> supplier,Preflight check) {core=supplier;preflight=check;}
    public static void requireApplySupported() throws Exception {preflight.run();}
    /** Real producer/API setup admission, separate from per-frame shader qualification uniforms. */
    public static void configureAdmission(OptimizationId id,java.util.function.BooleanSupplier supplier) {admissions.put(id,supplier);}
    public static boolean admitted(OptimizationId id) {
        try {var supplier=admissions.get(id);return supplier!=null&&supplier.getAsBoolean();}catch(RuntimeException unavailable){return false;}
    }
    public static void configureImplementation(OptimizationId id,java.util.function.Supplier<String> supplier) {implementations.put(id,supplier);}
    /** Shader implementations are scoped to an exact recognized source family; shared CPU routes retain global metadata. */
    public static void configureImplementation(String family,OptimizationId id,java.util.function.Supplier<String> supplier) {
        java.util.Objects.requireNonNull(family);java.util.Objects.requireNonNull(id);java.util.Objects.requireNonNull(supplier);
        familyImplementations.computeIfAbsent(family,ignored->new java.util.EnumMap<>(OptimizationId.class)).put(id,supplier);
    }
    public static String implementation(String family,OptimizationId id) {
        var scoped=familyImplementations.get(family);var supplier=scoped==null?null:scoped.get(id);
        if(supplier==null)return id==OptimizationId.AO_RENDER_SCALE?"family implementation not reported":implementation(id);
        try{return supplier.get();}catch(RuntimeException unavailable){return "implementation unavailable: "+unavailable.getClass().getSimpleName();}
    }
    public static String implementation(OptimizationId id) {
        try {var supplier=implementations.get(id);return supplier==null?"implementation not reported":supplier.get();}
        catch(RuntimeException unavailable){return "implementation unavailable: "+unavailable.getClass().getSimpleName();}
    }
    public static CoreUniformFrame.Core core() {
        try {return core==null?null:core.get();}catch(RuntimeException unavailable){return null;}
    }
}
