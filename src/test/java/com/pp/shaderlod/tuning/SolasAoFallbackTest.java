package com.pp.shaderlod.tuning;

import com.pp.shaderlod.optimization.solasao.SolasAoContract;
import com.pp.shaderlod.optimization.AoNativeFallback;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure ownership/option-transition checks, not a rendered AO admission or performance claim. */
class SolasAoFallbackTest {
    private final Object oldPack=new Object(),newPack=new Object(),world=new Object(),oldPipeline=new Object(),newPipeline=new Object();
    private final String dimension="minecraft:overworld",selected="ShaderLOD-solas-fixture.zip";
    // The maintained real Solas native map, plus its actual declared generated controls.
    private final Map<String,String> before=SolasAoContract.generated("1"),after=SolasAoContract.generated("0");
    private AoNativeFallback.Receipt receipt(Object old,Object next,Object scene,Object dim,long generation,
            String oldName,String nextName,Map<String,String> prior,Map<String,String> current,boolean queue,String reason) {
        return new AoNativeFallback.Receipt(SolasAoContract.ID,old,next,oldPipeline,newPipeline,scene,dim,generation,oldName,nextName,prior,current,queue,reason);
    }
    private AoNativeFallback.Receipt valid() {
        return receipt(oldPack,newPack,world,dimension,17,selected,selected,before,after,true,"AO producer framebuffer/depth/projection unavailable");
    }
    private boolean matches(AoNativeFallback.Receipt receipt) {
        return receipt.matches(SolasAoContract.ID,oldPack,newPack,oldPipeline,newPipeline,world,dimension,17,selected,before,after);
    }
    @Test void exactNativeSafetyReloadMatchesActualFullOptionContractOnce() {
        assertTrue(before.size()>428);assertTrue(SolasAoContract.options(before));assertTrue(SolasAoContract.options(after));
        assertTrue(matches(valid()));
        var slot=new AoNativeFallback();slot.publish(valid());assertTrue(matches(slot.take()));assertNull(slot.take(),"Destructive take must prevent replay");
        slot.publish(valid());slot.clear();assertNull(slot.take(),"Unrelated reload must invalidate pending evidence");
    }
    @Test void identityGenerationWorldDimensionQueueAndReasonMismatchRefuse() {
        var refusals=List.of(
            receipt(new Object(),newPack,world,dimension,17,selected,selected,before,after,true,"refused"),
            receipt(oldPack,new Object(),world,dimension,17,selected,selected,before,after,true,"refused"),
            receipt(oldPack,oldPack,world,dimension,17,selected,selected,before,after,true,"refused"),
            receipt(oldPack,newPack,new Object(),dimension,17,selected,selected,before,after,true,"refused"),
            receipt(oldPack,newPack,world,"minecraft:the_nether",17,selected,selected,before,after,true,"refused"),
            receipt(oldPack,newPack,world,dimension,18,selected,selected,before,after,true,"refused"),
            receipt(oldPack,newPack,world,dimension,17,"Other.zip",selected,before,after,true,"refused"),
            receipt(oldPack,newPack,world,dimension,17,selected,"Other.zip",before,after,true,"refused"),
            receipt(oldPack,newPack,world,dimension,17,selected,selected,before,after,false,"refused"),
            receipt(oldPack,newPack,world,dimension,17,selected,selected,before,after,true,""));
        for(var refused:refusals)assertFalse(matches(refused),refused.toString());
    }
    @Test void anyOtherNativeOrGeneratedOptionChangeAndMissingKeysRefuse() {
        for(String key:List.of("TAA","SHADERLOD_REFINEMENT_ADAPTER","SHADERLOD_SOLAS_DEPTH_PRIMING")) {
            assertTrue(after.containsKey(key),key);
            var changed=new TreeMap<>(after);changed.put(key,"changed");
            var receipt=receipt(oldPack,newPack,world,dimension,17,selected,selected,before,changed,true,"refused");
            assertFalse(receipt.matches(SolasAoContract.ID,oldPack,newPack,oldPipeline,newPipeline,world,dimension,17,selected,before,changed),key);
        }
        var missing=new TreeMap<>(after);missing.remove("TAA");
        assertFalse(receipt(oldPack,newPack,world,dimension,17,selected,selected,before,missing,true,"refused")
            .matches(SolasAoContract.ID,oldPack,newPack,oldPipeline,newPipeline,world,dimension,17,selected,before,missing));
        assertFalse(valid().matches(SolasAoContract.ID,oldPack,newPack,oldPipeline,newPipeline,world,dimension,17,selected,before,before),"Current options must equal the recorded post-reload map");
        assertFalse(valid().matches(SolasAoContract.ID,oldPack,newPack,oldPipeline,newPipeline,world,dimension,17,selected,after,after),"Expected old options must equal recorded old map");
        assertFalse(matches(receipt(oldPack,newPack,world,dimension,17,selected,selected,after,before,true,"refused")),"Only1→0 allowed");
    }
}
