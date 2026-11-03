package com.pp.shaderlod.optimization.complementarysampler;

import com.pp.shaderlod.CoreUniformFrame;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowScopePins;
import com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaContract;
import com.pp.shaderlod.optimization.unboundearlyalpha.UnboundEarlyAlphaContract;
import com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource;
import com.pp.shaderlod.optimization.sourcecache.NativePassSamplerCache;
import java.util.*;
import java.util.function.Predicate;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.*;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.*;

/**
 * Observations last for one admitted native render invocation, never a frame.
 * This is not source admission, permission admission, or framebuffer proof.
 * Each caller must still pass its current purpose-specific native receipt and
 * all live config/queue/stack/resource/uniform/viewport/ownership guards.
 */
public final class ComplementarySamplerPass {
    private ComplementarySamplerPass() {}
    public enum Family {
        REIMAGINED("complementary-reimagined", "SHADERLOD_REIMAGINED_"),
        UNBOUND("complementary-unbound", "SHADERLOD_UNBOUND_");
        final String id, prefix;
        Family(String id, String prefix) { this.id=id; this.prefix=prefix; }
    }
    public enum Purpose {
        RECEIVER(OptimizationId.SHADOW_RECEIVER_FOOTPRINT), TEMPORAL(OptimizationId.TEMPORAL_SHADOW_FILTER);
        final OptimizationId flag;
        Purpose(OptimizationId flag) { this.flag=flag; }
    }
    private static final boolean DISABLED=Boolean.getBoolean("shaderlod.dev.disableShadowSamplerReuse");
    private static final Set<OptimizationId> ALLOWED=Set.of(
        OptimizationId.SHADOW_RECEIVER_FOOTPRINT, OptimizationId.TEMPORAL_SHADOW_FILTER,
        OptimizationId.BINDING_CERTIFICATE_CACHE, OptimizationId.SHADOW_ADMISSION_CACHE,
        OptimizationId.QUAD_COMMAND_COMPACTION, OptimizationId.INDEX_SCALAR_CACHE);
    public static final Lane<ReimaginedEarlyAlphaContract.Sampler> REIMAGINED_RECEIVER=new Lane<>(Family.REIMAGINED,Purpose.RECEIVER);
    public static final Lane<ReimaginedEarlyAlphaContract.Sampler> REIMAGINED_TEMPORAL=new Lane<>(Family.REIMAGINED,Purpose.TEMPORAL);
    public static final Lane<UnboundEarlyAlphaContract.Sampler> UNBOUND_RECEIVER=new Lane<>(Family.UNBOUND,Purpose.RECEIVER);
    public static final Lane<UnboundEarlyAlphaContract.Sampler> UNBOUND_TEMPORAL=new Lane<>(Family.UNBOUND,Purpose.TEMPORAL);
    private static final List<Lane<?>> LANES=List.of(REIMAGINED_RECEIVER,REIMAGINED_TEMPORAL,UNBOUND_RECEIVER,UNBOUND_TEMPORAL);
    private static final ThreadLocal<TerrainRenderPass> PASS=new ThreadLocal<>();

    public record Observation<S>(S zero, S one, S color) {}
    private record Grant(Key key, int location) {}
    /** Executable/resource references are identity-bound; immutable core settings compare by value. */
    public record Key(Object program, Object link, Object pipeline, Object pack, CoreUniformFrame.Core core, Object context,
            Object pass, Object targets, Object shadows, Object depth0, Object depth1, Object color0,
            int programId, int depth0Id, int depth1Id, int color0Id, int resolution, int width, int height, long epoch) {
        @Override public boolean equals(Object value) {
            return value instanceof Key k && program==k.program && link==k.link && pipeline==k.pipeline
                && pack==k.pack && Objects.equals(core,k.core) && context==k.context && pass==k.pass && targets==k.targets
                && shadows==k.shadows && depth0==k.depth0 && depth1==k.depth1 && color0==k.color0
                && programId==k.programId && depth0Id==k.depth0Id && depth1Id==k.depth1Id && color0Id==k.color0Id
                && resolution==k.resolution && width==k.width && height==k.height && epoch==k.epoch;
        }
        @Override public int hashCode() { return System.identityHashCode(program); }
    }
    public static boolean allowedFlags(Predicate<OptimizationId> requested, Predicate<OptimizationId> enabled) {
        for(var id:OptimizationId.values())if(!ALLOWED.contains(id)&&(requested.test(id)||enabled.test(id)))return false;
        return true;
    }
    /** Use actual captured values: receiver Q2 does not inherit temporal's Q2..5 normalization. */
    public static boolean compiledProfile(Family family, Purpose purpose, Map<String,String> actual) {
        if(actual==null || !"1".equals(actual.get("RP_MODE")))return false;
        String quality=actual.get("SHADOW_QUALITY");
        if(purpose==Purpose.RECEIVER ? !"2".equals(quality) : !Set.of("2","3","4","5").contains(quality==null?"":quality))return false;
        for(String name:List.of("DEPTH_PRIMING","AO_HALF_RES","DEAD_MIP_CHAIN","SPATIAL_REUSE","SPATIAL_RATE","PIXEL_GRAPH"))
            if(!"0".equals(actual.get(family.prefix+name)))return false;
        return true;
    }
    private static boolean allowed(CoreUniformFrame.Core core, Family family, Purpose purpose) {
        return !DISABLED && core!=null && core.settings().enabled() && core.preset()==LodPreset.PERFORMANCE
            && family.id.equals(OptimizationConfig.activeFamily()) && MakeUpShadowScopePins.exact()
            && MakeUpShadowScopePins.bootstrapSafe() && OptimizationConfig.requested(purpose.flag)
            && core.optimizations().enabled(purpose.flag)
            && allowedFlags(OptimizationConfig::requested,core.optimizations()::enabled);
    }
    public static boolean matchesPass(SodiumPrograms.Pass compiled, TerrainRenderPass actual) {
        return matchesPass(compiled,actual,DefaultTerrainRenderPasses.SOLID,DefaultTerrainRenderPasses.CUTOUT);
    }
    /** Identity law, separable from Minecraft's static render-type initialization for regression tests. */
    public static boolean matchesPass(SodiumPrograms.Pass compiled, Object actual, Object solid, Object cutout) {
        return actual!=null && (compiled==SodiumPrograms.Pass.TERRAIN && actual==solid
            || compiled==SodiumPrograms.Pass.TERRAIN_CUTOUT && actual==cutout);
    }
    public static final class Lane<S> {
        private final Family family;
        private final Purpose purpose;
        private final NativePassSamplerCache<Key,Observation<S>> cache=new NativePassSamplerCache<>();
        private final ThreadLocal<Grant> grant=new ThreadLocal<>();
        private long captures,reuses,entries,invalidations;
        private final java.util.function.BiPredicate<Key,Integer> revoker;
        private Lane(Family family, Purpose purpose) { this(family,purpose,ComplementarySamplerPass::revoke); }
        private Lane(Family family, Purpose purpose, java.util.function.BiPredicate<Key,Integer> revoker) {
            this.family=family;this.purpose=purpose;this.revoker=revoker;
        }
        /** Called only after the caller's live native source/program guard succeeded. */
        public Key key(GlProgram<?> program, IrisCompiledSource.Certificate source, SodiumPrograms.Pass pass,
                int id, CoreUniformFrame.Core core, Object pipeline, ShaderPack pack,
                RenderTargets targets, ShadowRenderTargets shadows, long epoch, int depth0Id, int depth1Id) {
            if(!cache.active() || program==null || source==null || !allowed(core,family,purpose)
                    || !matchesPass(pass,PASS.get()) || !compiledProfile(family,purpose,source.input().options().captured().stringOptions())
                    || source.epoch()!=epoch || source.set().getPack()!=pack || program.handle()!=id)return null;
            // Direct program-uniform revocation preserves the current binding, including exception paths.
            // The existing draw permission remains available on other APIs, with full sampler observation.
            if(!GL.getCapabilities().OpenGL41)return null;
            return new Key(program,source,pipeline,pack,core,GL.getCapabilities(),pass,targets,shadows,
                shadows.getDepthTexture(),shadows.getDepthTextureNoTranslucents(),shadows.get(0),id,
                depth0Id,depth1Id,
                shadows.getColorTextureId(0),shadows.getResolution(),targets.getCurrentWidth(),targets.getCurrentHeight(),epoch);
        }
        public Observation<S> reuse(Key key) {
            if(key==null){invalidate();return null;}
            var value=cache.reuse(key);if(value!=null)reuses++;return value;
        }
        public void remember(Key key, Observation<S> value) { if(key!=null)cache.remember(key,value); }
        public void captured() { captures++; }
        public void granted(Key key, int location) {
            if(key!=null){grant.set(new Grant(key,location));cache.granted(()->revokeGranted());}
        }
        /** Clear the exact granted object even if another program became current while unwinding. */
        public int revokeGranted() {
            var value=grant.get();
            if(value==null){cache.revoked();return 0;}
            final boolean cleared;
            try { cleared=revoker.test(value.key(),value.location()); }
            catch(RuntimeException|Error failure) {
                // A failed writer must not consume the only cleanup record. Outer scope/next
                // draw cleanup retries the exact object; the mutation remains aborted.
                cache.granted(()->revokeGranted());throw failure;
            }
            grant.remove();cache.revoked();return cleared ? value.key().programId() : 0;
        }
        public void invalidate() { if(cache.active())invalidations++;cache.invalidate(); }
        public Map<String,Long> counters() { return Map.of("captures",captures,"reuses",reuses,"entries",entries,"invalidation_calls",invalidations); }
    }
    /** Runs before a writer, while the original object is live. Never targets a reused integer name. */
    private static boolean revoke(Key key, int location) {
        if(location<0 || GL.getCapabilities()!=key.context())return false;
        var program=(GlProgram<?>)key.program();
        // A deletion hook revoked before handle invalidation. Do not write to a replacement program.
        try { if(program.handle()!=key.programId())return false; }
        catch(IllegalStateException deleted) { return false; }
        GL41.glProgramUniform1i(key.programId(),location,0);
        return true;
    }
    public static final class Scope implements AutoCloseable {
        private final List<AutoCloseable> scopes;
        private final TerrainRenderPass previous;
        private boolean closed;
        private Scope(List<AutoCloseable> scopes, TerrainRenderPass previous) { this.scopes=scopes;this.previous=previous; }
        @Override public void close() {
            if(closed)return;closed=true;
            try { closeAll(scopes); }
            finally { if(previous==null)PASS.remove();else PASS.set(previous); }
        }
    }
    public static Scope open(TerrainRenderPass pass) {
        var previous=PASS.get();var scopes=new ArrayList<AutoCloseable>(4);
        try {
            var core=OptimizationRuntimeContext.core();
            boolean nativePass=(pass==DefaultTerrainRenderPasses.SOLID || pass==DefaultTerrainRenderPasses.CUTOUT)
                && !net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered();
            for(var lane:LANES) {
                boolean eligible=nativePass && allowed(core,lane.family,lane.purpose);
                // open(false) still poisons an existing invocation on reentry.
                scopes.add(lane.cache.open(eligible));if(eligible)lane.entries++;
            }
            PASS.set(pass);return new Scope(scopes,previous);
        } catch(RuntimeException|Error failure) {
            try { closeAll(scopes); } catch(RuntimeException|Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    private static void closeAll(List<AutoCloseable> scopes) {
        Throwable first=null;
        for(int i=scopes.size()-1;i>=0;i--)try { scopes.get(i).close(); }
        catch(Throwable failure) { if(first==null)first=failure;else first.addSuppressed(failure); }
        if(first instanceof Error error)throw error;
        if(first instanceof RuntimeException runtime)throw runtime;
        if(first!=null)throw new IllegalStateException(first);
    }
    public static void arm() { for(var lane:LANES)lane.cache.arm(); }
    public static void invalidate() {
        // A throwing revoke must not leave the other family's/purpose's grant outstanding.
        Throwable first=null;
        for(var lane:LANES)try { lane.invalidate(); }
        catch(RuntimeException|Error failure) { if(first==null)first=failure;else first.addSuppressed(failure); }
        if(first instanceof Error error)throw error;
        if(first instanceof RuntimeException runtime)throw runtime;
    }
}
