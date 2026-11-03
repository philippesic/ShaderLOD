package com.pp.shaderlod.tuning;

import com.pp.shaderlod.optimization.sildurao.SildurAoContract;
import com.pp.shaderlod.optimization.sildurspatial.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Sildur option maps and queue consumer; no driver or timing claim. */
class SildurSpatialFallbackTest {
    private final Object oldPack=new Object(),newPack=new Object(),oldPipeline=new Object(),newPipeline=new Object(),world=new Object();
    private final String dimension="minecraft:overworld",selected="ShaderLOD-Sildur-generated.zip";
    private static Map<String,String> options(int mode) {
        var options=new TreeMap<>(SildurAoContract.generated("0"));
        options.put(SildurSpatialContract.REUSE,mode==1?"1":"0");
        options.put(SildurSpatialContract.RATE,mode==2?"1":"0");
        assertTrue(SildurSpatialContract.options(options));return Map.copyOf(options);
    }
    private SildurSpatialFallback.Receipt receipt(Map<String,String> before,Map<String,String> after) {
        return new SildurSpatialFallback.Receipt(oldPack,newPack,oldPipeline,newPipeline,world,dimension,23,
            selected,selected,before,after,true,"producer-before: exact actual proof refused");
    }
    private boolean matches(SildurSpatialFallback.Receipt receipt,Map<String,String> before,Map<String,String> after) {
        return receipt.matches(SildurSpatialContract.ID,oldPack,newPack,oldPipeline,newPipeline,world,dimension,23,selected,before,after);
    }
    @Test void exactReuseOrRateNativeRecoveryIsOneUseAndKeepsReason(){
        for(int mode:List.of(1,2)) {
            var before=options(mode);var after=options(0);assertTrue(before.size()>115);
            var slot=new SildurSpatialFallback();var receipt=receipt(before,after);slot.publish(receipt);
            assertTrue(matches(slot.take(),before,after));assertNull(slot.take());
            assertEquals("producer-before: exact actual proof refused",receipt.reason());
            slot.publish(receipt);slot.clear();assertNull(slot.take());
        }
    }
    @Test void anyOtherOptionChangeOrNonOneHotTransitionRefuses(){
        for(int mode:List.of(1,2)) {
            var before=options(mode);var after=options(0);
            String nativeKey=before.keySet().stream().filter(k->!k.startsWith("SHADERLOD")).sorted().findFirst().orElseThrow();
            for(String key:List.of(nativeKey,SildurAoContract.OPTION)) {
                var changed=new TreeMap<>(after);changed.put(key,"changed");
                assertFalse(matches(receipt(before,changed),before,changed),key);
            }
            var missing=new TreeMap<>(after);missing.remove(SildurSpatialContract.RATE);
            assertFalse(matches(receipt(before,missing),before,missing));
            assertFalse(matches(receipt(before,options(mode)),before,options(mode)),"No recovery occurred");
            assertFalse(matches(receipt(after,before),after,before),"Reverse transition");
        }
        var both=new TreeMap<>(options(1));both.put(SildurSpatialContract.RATE,"1");
        assertFalse(matches(receipt(both,options(0)),both,options(0)),"Both requested is not a certified one-hot transition");
        assertFalse(matches(receipt(options(0),options(0)),options(0),options(0)));
    }
    @Test void foreignOrReusedIdentitiesAndUnownedQueueRefuse(){
        var before=options(1);var after=options(0);var receipt=receipt(before,after);
        assertFalse(receipt.matches("bliss-shader",oldPack,newPack,oldPipeline,newPipeline,world,dimension,23,selected,before,after));
        assertFalse(receipt.matches(SildurSpatialContract.ID,new Object(),newPack,oldPipeline,newPipeline,world,dimension,23,selected,before,after));
        assertFalse(receipt.matches(SildurSpatialContract.ID,oldPack,new Object(),oldPipeline,newPipeline,world,dimension,23,selected,before,after));
        assertFalse(receipt.matches(SildurSpatialContract.ID,oldPack,newPack,new Object(),newPipeline,world,dimension,23,selected,before,after));
        assertFalse(receipt.matches(SildurSpatialContract.ID,oldPack,newPack,oldPipeline,new Object(),world,dimension,23,selected,before,after));
        assertFalse(receipt.matches(SildurSpatialContract.ID,oldPack,newPack,oldPipeline,newPipeline,new Object(),dimension,23,selected,before,after));
        assertFalse(receipt.matches(SildurSpatialContract.ID,oldPack,newPack,oldPipeline,newPipeline,world,"minecraft:the_nether",23,selected,before,after));
        assertFalse(receipt.matches(SildurSpatialContract.ID,oldPack,newPack,oldPipeline,newPipeline,world,dimension,24,selected,before,after));
        assertFalse(receipt.matches(SildurSpatialContract.ID,oldPack,newPack,oldPipeline,newPipeline,world,dimension,23,"Other.zip",before,after));
        var queued=new SildurSpatialFallback.Receipt(oldPack,newPack,oldPipeline,newPipeline,world,dimension,23,selected,selected,before,after,false,"refused");
        assertFalse(matches(queued,before,after));
        var reused=new SildurSpatialFallback.Receipt(oldPack,newPack,oldPipeline,oldPipeline,world,dimension,23,selected,selected,before,after,true,"refused");
        assertFalse(reused.matches(SildurSpatialContract.ID,oldPack,newPack,oldPipeline,oldPipeline,world,dimension,23,selected,before,after));
        var renamed=new SildurSpatialFallback.Receipt(oldPack,newPack,oldPipeline,newPipeline,world,dimension,23,selected,"Other.zip",before,after,true,"refused");
        assertFalse(matches(renamed,before,after));
        assertFalse(matches(receipt,before,before),"Current values must equal the receipt's post-reload map");
    }
    @Test void everyActiveRequestedModeRequiresItsActualLoadedOneHotMap(){
        for(int mode:List.of(1,2)) {
            boolean reuse=mode==1,rate=mode==2;
            assertTrue(SildurSpatialFallback.loadedGraphMatches(SildurSpatialContract.ID,true,reuse,rate,options(mode)));
            assertFalse(SildurSpatialFallback.loadedGraphMatches(SildurSpatialContract.ID,true,reuse,rate,options(0)));
            assertFalse(SildurSpatialFallback.loadedGraphMatches(SildurSpatialContract.ID,true,reuse,rate,options(mode==1?2:1)));
            assertFalse(SildurSpatialFallback.loadedGraphMatches(SildurSpatialContract.ID,true,reuse,rate,Map.of()));
            assertTrue(SildurSpatialFallback.loadedGraphMatches(SildurSpatialContract.ID,false,reuse,rate,Map.of()));
        }
        assertFalse(SildurSpatialFallback.loadedGraphMatches(SildurSpatialContract.ID,true,true,true,options(1)));
        assertTrue(SildurSpatialFallback.loadedGraphMatches(SildurSpatialContract.ID,true,false,false,Map.of()));
        assertTrue(SildurSpatialFallback.loadedGraphMatches("photon",true,true,false,Map.of()));
    }
    @Test void actualQueueRecoveryZerosOnlySpatialControlsAndPreservesOtherPendingChoicesEvenOnThrow(){
        var queue=new HashMap<>(Map.of(SildurSpatialContract.REUSE,"1",SildurSpatialContract.RATE,"0","unrelated","old"));
        assertThrows(java.io.IOException.class,()->SildurSpatialRecovery.nativeReload(queue,()->{
            assertEquals(Map.of(SildurSpatialContract.REUSE,"0",SildurSpatialContract.RATE,"0"),queue);
            queue.put("new-unrelated","retained");throw new java.io.IOException("actual reload failure");
        }));
        assertEquals(Map.of("unrelated","old","new-unrelated","retained"),queue);
    }
}
