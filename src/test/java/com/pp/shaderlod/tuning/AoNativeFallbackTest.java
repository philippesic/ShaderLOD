package com.pp.shaderlod.tuning;

import com.pp.shaderlod.optimization.AoNativeFallback;
import com.pp.shaderlod.optimization.astraao.AstraAoContract;
import com.pp.shaderlod.optimization.blissao.BlissAoContract;
import com.pp.shaderlod.optimization.makeupao.MakeUpAoContract;
import com.pp.shaderlod.optimization.reimaginedao.ReimaginedAoContract;
import com.pp.shaderlod.optimization.sildurao.SildurAoContract;
import com.pp.shaderlod.optimization.solasao.SolasAoContract;
import com.pp.shaderlod.optimization.unboundao.UnboundAoContract;
import java.util.*;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real maintained option maps; unit ownership checks do not assert actual GL admission. */
class AoNativeFallbackTest {
    private record Family(String id,String option,Function<String,Map<String,String>> generated,Predicate<Map<String,String>> valid) {}
    private static List<Family> families(){return List.of(
        new Family(SolasAoContract.ID,SolasAoContract.OPTION,SolasAoContract::generated,SolasAoContract::options),
        new Family(AstraAoContract.ID,AstraAoContract.OPTION,AstraAoContract::generated,AstraAoContract::options),
        new Family(MakeUpAoContract.ID,MakeUpAoContract.OPTION,MakeUpAoContract::generated,MakeUpAoContract::options),
        new Family(ReimaginedAoContract.ID,ReimaginedAoContract.OPTION,ReimaginedAoContract::generated,ReimaginedAoContract::options),
        new Family(UnboundAoContract.ID,UnboundAoContract.OPTION,UnboundAoContract::generated,UnboundAoContract::options),
        new Family(SildurAoContract.ID,SildurAoContract.OPTION,SildurAoContract::generated,SildurAoContract::options),
        new Family(BlissAoContract.ID,BlissAoContract.OPTION,BlissAoContract::generated,BlissAoContract::options),
        new Family("bsl-shaders","SHADERLOD_BSL_AO_HALF_RES",AoNativeFallbackTest::bslGenerated,
            map->com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("bsl-shaders",map)));}
    private static Map<String,String> bslGenerated(String graph) {
        try(var input=AoNativeFallbackTest.class.getResourceAsStream("/assets/shaderlod/rendererquad/generated-main-options.json")) {
            assertNotNull(input);var result=new TreeMap<String,String>();
            com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("bsl-shaders").entrySet().forEach(e->result.put(e.getKey(),e.getValue().getAsString()));
            assertEquals("0",result.put("SHADERLOD_BSL_AO_HALF_RES",graph));return Map.copyOf(result);
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    private final Object oldPack=new Object(),newPack=new Object(),world=new Object(),oldPipeline=new Object(),newPipeline=new Object();
    private final String dimension="minecraft:overworld",selected="Actual-family-generated.zip";
    private AoNativeFallback.Receipt receipt(String family,Map<String,String> before,Map<String,String> after) {
        return new AoNativeFallback.Receipt(family,oldPack,newPack,oldPipeline,newPipeline,world,dimension,71,selected,selected,before,after,true,"actual runtime safety refusal");
    }
    private boolean matches(String family,AoNativeFallback.Receipt receipt,Map<String,String> before,Map<String,String> after) {
        return receipt.matches(family,oldPack,newPack,oldPipeline,newPipeline,world,dimension,71,selected,before,after);
    }
    @Test void everyExplicitFamilyUsesItsActualFullMapAndExactOption() {
        for(var family:families()) {
            var before=family.generated.apply("1");var after=family.generated.apply("0");
            assertTrue(before.size()>100,family.id);assertTrue(family.valid.test(before),family.id);assertTrue(family.valid.test(after),family.id);
            assertEquals(family.option,AoNativeFallback.option(family.id));
            assertTrue(matches(family.id,receipt(family.id,before,after),before,after),family.id);
            var slot=new AoNativeFallback();slot.publish(receipt(family.id,before,after));
            assertTrue(matches(family.id,slot.take(),before,after));assertNull(slot.take());
        }
    }
    @Test void everyEnabledRequestedFamilyMustActuallyLoadHalfOneBeforeAnySampling() {
        for(var family:families()) {
            var one=family.generated.apply("1");var zero=family.generated.apply("0");
            assertTrue(AoNativeFallback.loadedGraphMatches(family.id,true,true,one),family.id);
            assertFalse(AoNativeFallback.loadedGraphMatches(family.id,true,true,zero),family.id);
            var missing=new TreeMap<>(one);missing.remove(family.option);
            assertFalse(AoNativeFallback.loadedGraphMatches(family.id,true,true,missing),family.id);
            assertTrue(AoNativeFallback.loadedGraphMatches(family.id,false,true,zero),"Global Off retains native graph");
            assertTrue(AoNativeFallback.loadedGraphMatches(family.id,true,false,zero),"Unrequested AO retains native graph");
        }
    }
    @Test void otherFamilyOrOtherNativeAndPrivateOptionChangesNeverMatch() {
        for(var family:families()) {
            var before=family.generated.apply("1");var after=family.generated.apply("0");
            var receipt=receipt(family.id,before,after);
            String other=family.id.equals(SolasAoContract.ID)?AstraAoContract.ID:SolasAoContract.ID;
            assertFalse(matches(other,receipt,before,after),family.id+" cross-family");
            String nativeKey=before.keySet().stream().filter(k->!k.startsWith("SHADERLOD_")).sorted().findFirst().orElseThrow();
            String privateKey=before.keySet().stream().filter(k->k.startsWith("SHADERLOD_")&&!k.equals(family.option)).sorted().findFirst().orElseThrow();
            for(String key:List.of(nativeKey,privateKey)) {
                var changed=new TreeMap<>(after);changed.put(key,"changed");
                assertFalse(matches(family.id,receipt(family.id,before,changed),before,changed),family.id+"/"+key);
            }
            assertFalse(matches(family.id,receipt(family.id,after,before),after,before),family.id+" reversed graph");
        }
    }
    @Test void unsupportedFamilyAndUnownedOptionCannotAuthorizeFallback() {
        assertNull(AoNativeFallback.option("photon"));assertNull(AoNativeFallback.option("unrecognized"));
        var before=SolasAoContract.generated("1");var after=SolasAoContract.generated("0");
        assertFalse(matches("photon",receipt("photon",before,after),before,after));
        var missing=new TreeMap<>(after);missing.remove(SolasAoContract.OPTION);
        assertFalse(matches(SolasAoContract.ID,receipt(SolasAoContract.ID,before,missing),before,missing));
    }
    @Test void wrongOrUnchangedPipelineCannotAuthorizeKnownPackReplacement() {
        var before=SolasAoContract.generated("1");var after=SolasAoContract.generated("0");
        var receipt=receipt(SolasAoContract.ID,before,after);
        assertFalse(receipt.matches(SolasAoContract.ID,oldPack,newPack,new Object(),newPipeline,world,dimension,71,selected,before,after));
        assertFalse(receipt.matches(SolasAoContract.ID,oldPack,newPack,oldPipeline,new Object(),world,dimension,71,selected,before,after));
        var same=new AoNativeFallback.Receipt(SolasAoContract.ID,oldPack,newPack,oldPipeline,oldPipeline,world,dimension,71,
            selected,selected,before,after,true,"refused");
        assertFalse(same.matches(SolasAoContract.ID,oldPack,newPack,oldPipeline,oldPipeline,world,dimension,71,selected,before,after));
    }
}
