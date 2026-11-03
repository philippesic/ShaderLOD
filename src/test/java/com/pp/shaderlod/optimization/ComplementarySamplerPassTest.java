package com.pp.shaderlod.optimization;

import com.pp.shaderlod.optimization.complementarysampler.ComplementarySamplerPass;
import com.pp.shaderlod.optimization.complementarysampler.ComplementarySamplerPass.*;
import com.pp.shaderlod.optimization.sourcecache.NativePassSamplerCache;
import com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource;
import com.pp.shaderlod.optimization.sourcecache.TemporalProgramReceipts;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComplementarySamplerPassTest {
    private static Map<String,String> profile(Family family) {
        var values=new HashMap<String,String>();values.put("RP_MODE","1");values.put("SHADOW_QUALITY","2");
        for(var name:List.of("DEPTH_PRIMING","AO_HALF_RES","DEAD_MIP_CHAIN","SPATIAL_REUSE","SPATIAL_RATE","PIXEL_GRAPH"))
            values.put("SHADERLOD_"+family.name()+"_"+name,"0");
        return values;
    }
    @Test void receiverQ2AndTemporalQ2Through5RemainDifferentCapabilities() {
        for(var family:Family.values()) {
            var values=profile(family);
            for(var quality:List.of("1","2","3","4","5","6")) {
                values.put("SHADOW_QUALITY",quality);
                assertEquals(quality.equals("2"),ComplementarySamplerPass.compiledProfile(family,Purpose.RECEIVER,values));
                assertEquals(Set.of("2","3","4","5").contains(quality),ComplementarySamplerPass.compiledProfile(family,Purpose.TEMPORAL,values));
            }
            values.remove("SHADOW_QUALITY");
            for(var purpose:Purpose.values())assertFalse(ComplementarySamplerPass.compiledProfile(family,purpose,values));
        }
    }
    @Test void everyCompiledPeerMustBeExplicitZeroAndFamilySpecific() {
        for(var family:Family.values())for(var purpose:Purpose.values()) {
            var values=profile(family);assertTrue(ComplementarySamplerPass.compiledProfile(family,purpose,values));
            assertFalse(ComplementarySamplerPass.compiledProfile(family==Family.REIMAGINED?Family.UNBOUND:Family.REIMAGINED,purpose,values));
            for(String name:new ArrayList<>(values.keySet()))if(name.startsWith("SHADERLOD_")) {
                values.put(name,"1");assertFalse(ComplementarySamplerPass.compiledProfile(family,purpose,values),name);
                values.remove(name);assertFalse(ComplementarySamplerPass.compiledProfile(family,purpose,values),name);
                values.put(name,"0");
            }
        }
    }
    @Test void requestedOrEnabledPeersBothRefuseReuse() {
        var allowed=Set.of(OptimizationId.SHADOW_RECEIVER_FOOTPRINT,OptimizationId.TEMPORAL_SHADOW_FILTER,
            OptimizationId.BINDING_CERTIFICATE_CACHE,OptimizationId.SHADOW_ADMISSION_CACHE,
            OptimizationId.QUAD_COMMAND_COMPACTION,OptimizationId.INDEX_SCALAR_CACHE);
        assertTrue(ComplementarySamplerPass.allowedFlags(allowed::contains,allowed::contains));
        for(var peer:OptimizationId.values())if(!allowed.contains(peer)) {
            assertFalse(ComplementarySamplerPass.allowedFlags(id->allowed.contains(id)||id==peer,allowed::contains),peer.name());
            assertFalse(ComplementarySamplerPass.allowedFlags(allowed::contains,id->allowed.contains(id)||id==peer),peer.name());
        }
    }
    @Test void solidCutoutAndShadowPassesCannotBorrowEachOthersProof() throws Exception {
        Object solid=new Object(),cutout=new Object(),translucent=new Object();
        assertTrue(ComplementarySamplerPass.matchesPass(SodiumPrograms.Pass.TERRAIN,solid,solid,cutout));
        assertTrue(ComplementarySamplerPass.matchesPass(SodiumPrograms.Pass.TERRAIN_CUTOUT,cutout,solid,cutout));
        assertFalse(ComplementarySamplerPass.matchesPass(SodiumPrograms.Pass.TERRAIN,cutout,solid,cutout));
        assertFalse(ComplementarySamplerPass.matchesPass(SodiumPrograms.Pass.TERRAIN_CUTOUT,solid,solid,cutout));
        assertFalse(ComplementarySamplerPass.matchesPass(SodiumPrograms.Pass.SHADOW,solid,solid,cutout));
        assertFalse(ComplementarySamplerPass.matchesPass(SodiumPrograms.Pass.TERRAIN,translucent,solid,cutout));
        // Production overload supplies the pinned renderer's actual SOLID/CUTOUT objects.
        var node=new org.objectweb.asm.tree.ClassNode();
        try(var input=ComplementarySamplerPass.class.getResourceAsStream("ComplementarySamplerPass.class")) {
            assertNotNull(input);new org.objectweb.asm.ClassReader(input).accept(node,0);
        }
        var method=node.methods.stream().filter(m->m.name.equals("matchesPass")&&m.desc.contains("TerrainRenderPass;")).findFirst().orElseThrow();
        var fields=new HashSet<String>();
        for(var instruction:method.instructions)if(instruction instanceof org.objectweb.asm.tree.FieldInsnNode field)
            if(field.owner.equals("net/caffeinemc/mods/sodium/client/render/chunk/terrain/DefaultTerrainRenderPasses"))fields.add(field.name);
        assertEquals(Set.of("SOLID","CUTOUT"),fields);
    }
    private static Key key(Object[] refs,int texture,int resolution) {
        return new Key(refs[0],refs[1],refs[2],refs[3],(com.pp.shaderlod.CoreUniformFrame.Core)refs[4],refs[5],refs[6],refs[7],refs[8],refs[9],refs[10],refs[11],
            17,texture,23,24,resolution,1920,1200,7);
    }
    @Test void sameIdsDoNotHideObjectReplacementRelinkContextOrTargetChanges() {
        Object[] refs=new Object[12];Arrays.setAll(refs,i->new Object());refs[4]=core(false);var original=key(refs,22,2048);
        for(int i=0;i<refs.length;i++) {
            var changed=refs.clone();changed[i]=i==4?core(true):new Object();assertNotEquals(original,key(changed,22,2048));
            var cache=new NativePassSamplerCache<Key,String>();
            try(var scope=cache.open(true)) {
                cache.arm();cache.remember(original,"actual observations");
                assertNull(cache.reuse(key(changed,22,2048)));
                cache.remember(original,"restored identities");assertNull(cache.reuse(original));
            }
        }
        assertNotEquals(original,key(refs,29,2048));assertNotEquals(original,key(refs,22,4096));
    }
    private static com.pp.shaderlod.CoreUniformFrame.Core core(boolean changed) {
        return new com.pp.shaderlod.CoreUniformFrame.Core(new com.pp.shaderlod.LodSettings(true,32,96,8,false),
            com.pp.shaderlod.LodPreset.PERFORMANCE,OptimizationSnapshot.noneRequested().with(OptimizationId.SHADOW_RECEIVER_FOOTPRINT,changed));
    }
    @Test void freshEqualCoreSnapshotsReuseButChangedValuesPoisonTheInvocation() {
        Object[] refs=new Object[12];Arrays.setAll(refs,i->new Object());refs[4]=core(false);
        var first=key(refs,22,2048);var next=refs.clone();next[4]=core(false);
        assertNotSame(refs[4],next[4]);assertEquals(first,key(next,22,2048));
        var cache=new NativePassSamplerCache<Key,String>();
        try(var scope=cache.open(true)){
            cache.arm();cache.remember(first,"samplers");assertEquals("samplers",cache.reuse(key(next,22,2048)));
            next[4]=core(true);assertNull(cache.reuse(key(next,22,2048)));assertNull(cache.reuse(first));
        }
    }
    @Test void failedExactObjectClearRetainsItsGrantForRetry() throws Exception {
        var attempts=new AtomicInteger();var permission=new AtomicInteger(1);
        java.util.function.BiPredicate<Key,Integer> revoker=(key,location)->{
            if(attempts.getAndIncrement()==0)throw new IllegalStateException("injected direct clear failure");
            permission.set(0);return true;
        };
        var ctor=Lane.class.getDeclaredConstructor(Family.class,Purpose.class,java.util.function.BiPredicate.class);
        ctor.setAccessible(true);var lane=(Lane<?>)ctor.newInstance(Family.REIMAGINED,Purpose.RECEIVER,revoker);
        var cache=cache(lane);Object[] refs=new Object[12];Arrays.setAll(refs,i->new Object());refs[4]=core(false);
        try(var scope=cache.open(true)){
            cache.arm();lane.granted(key(refs,22,2048),4);
            assertThrows(IllegalStateException.class,lane::revokeGranted);assertEquals(1,permission.get());
            // Scope invalidation retries the same retained actual-object grant before a writer.
            lane.invalidate();assertEquals(0,permission.get());assertEquals(2,attempts.get());
        }
        assertFalse(cache.active());assertEquals(0,lane.revokeGranted());
    }
    @SuppressWarnings("unchecked")
    private static NativePassSamplerCache<Key,Observation<Object>> cache(Lane<?> lane) throws Exception {
        var field=Lane.class.getDeclaredField("cache");field.setAccessible(true);
        return (NativePassSamplerCache<Key,Observation<Object>>)field.get(lane);
    }
    @Test void inactiveSiblingCannotRevokeSharedReceiverPermissionButAWriterDoes() throws Exception {
        var active=cache(ComplementarySamplerPass.REIMAGINED_RECEIVER);
        var permission=new AtomicInteger(1);
        try(var scope=active.open(true)) {
            active.arm();active.granted(()->permission.set(0));
            ComplementarySamplerPass.UNBOUND_RECEIVER.invalidate();assertEquals(1,permission.get());
            ComplementarySamplerPass.UNBOUND_TEMPORAL.invalidate();assertEquals(1,permission.get());
            ComplementarySamplerPass.invalidate();assertEquals(0,permission.get(),"shared hook runs before the writer");
        }
    }
    @Test void throwingFirstRevocationStillRevokesEveryOtherLaneBeforeWriter() throws Exception {
        var receiver=cache(ComplementarySamplerPass.REIMAGINED_RECEIVER);
        var temporal=cache(ComplementarySamplerPass.REIMAGINED_TEMPORAL);
        var permission=new AtomicInteger(1);var writer=new AtomicInteger();
        try(var a=receiver.open(true);var b=temporal.open(true)) {
            receiver.arm();temporal.arm();receiver.granted(()->{throw new IllegalStateException("injected revoke failure");});
            temporal.granted(()->permission.set(0));
            assertThrows(IllegalStateException.class,()->{ComplementarySamplerPass.invalidate();writer.incrementAndGet();});
            assertEquals(0,permission.get());assertEquals(0,writer.get());
        }
        assertFalse(receiver.active());assertFalse(temporal.active());
    }
    @Test void wrapperCloseReleasesAllScopesWhenARevokeThrows() throws Exception {
        var receiver=cache(ComplementarySamplerPass.REIMAGINED_RECEIVER);
        var temporal=cache(ComplementarySamplerPass.REIMAGINED_TEMPORAL);
        var a=receiver.open(true);var b=temporal.open(true);receiver.arm();temporal.arm();
        var permission=new AtomicInteger(1);receiver.granted(()->permission.set(0));
        temporal.granted(()->{throw new IllegalStateException("injected exit failure");});
        var ctor=Scope.class.getDeclaredConstructor(List.class,net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass.class);
        ctor.setAccessible(true);var scope=ctor.newInstance(List.of(a,b),null);
        assertThrows(IllegalStateException.class,scope::close);
        assertEquals(0,permission.get());assertFalse(receiver.active());assertFalse(temporal.active());
        scope.close(); // Idempotent even after throwing close.
    }
    @Test void realMutableIrisOptionsRefuseCachedObservationAndRestorationCannotRevivePass() throws Exception {
        var path=net.irisshaders.iris.shaderpack.include.AbsolutePackPath.fromAbsolutePath("/settings.glsl");
        var ctor=net.irisshaders.iris.shaderpack.include.IncludeGraph.class.getDeclaredConstructor(com.google.common.collect.ImmutableMap.class,com.google.common.collect.ImmutableMap.class);
        ctor.setAccessible(true);
        var raw=ctor.newInstance(com.google.common.collect.ImmutableMap.of(path,
            new net.irisshaders.iris.shaderpack.include.FileNode(path,com.google.common.collect.ImmutableList.of("#define SHADOW_QUALITY 2 // [2 3 4 5]","int quality = SHADOW_QUALITY;"))),com.google.common.collect.ImmutableMap.of());
        var options=new net.irisshaders.iris.shaderpack.option.ShaderPackOptions(raw,Map.of());
        var input=IrisCompiledSource.OptionsInput.capture(options,raw);
        var values=(net.irisshaders.iris.shaderpack.option.values.MutableOptionValues)options.getOptionValues();
        var cache=new NativePassSamplerCache<String,String>();
        try(var scope=cache.open(true)) {
            cache.arm();cache.remember("link","samplers");assertTrue(input.current(options));
            values.addAll(Map.of("SHADOW_QUALITY","3"));assertFalse(input.current(options));cache.invalidate();
            values.addAll(Map.of("SHADOW_QUALITY","2"));assertTrue(input.current(options));
            cache.remember("link","must not revive");assertNull(cache.reuse("link"));
        }
    }
    private static final class Program { int id=17;int handle(){if(id<0)throw new IllegalStateException("deleted");return id;} }
    @Test void samplerObservationCannotRepairMissingStaleDeletedOrReusedNativeReceipt() {
        var receipts=new TemporalProgramReceipts<Program,String>(Program::handle);var program=new Program();
        receipts.nativeProgram(17,program,"old",Map.of("permission",2));
        assertFalse(receipts.allows(17,"new"::equals,()->{fail("legacy fallback");return true;}));
        program.id=-1;assertFalse(receipts.allows(17,x->true,()->true));
        receipts.nativeProgram(17,new Program(),null,Map.of());assertFalse(receipts.allows(17,x->true,()->true));
        assertNull(receipts.location(17,"permission"));
        receipts.nativeProgram(17,new Program(),"new",Map.of("permission",7));assertTrue(receipts.allows(17,"new"::equals,()->false));
        receipts.clear();assertFalse(receipts.allows(17,x->true,()->true));
    }
}
