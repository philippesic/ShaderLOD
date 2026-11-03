package com.pp.shaderlod.optimization;

import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlpha;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MakeUpPostSampleStackTest {
    private static HashMap<String,String> minimal(){
        var map=new HashMap<>(MakeUpQuadRuntime.REQUIRED_STACK);map.put("shaderlod","test");return map;
    }
    @Test void acceptsOnlyCompleteMinimalOrExactLockedOptimizedTuple(){
        var map=minimal();assertTrue(MakeUpPostSampleAlpha.stackMatches(map));
        map.put("modernfix","5.27.24+mc1.21.1");assertFalse(MakeUpPostSampleAlpha.stackMatches(map));
        map.put("ferritecore","7.0.3");assertFalse(MakeUpPostSampleAlpha.stackMatches(map));
        map.put("immediatelyfast","1.6.14+1.21.1");assertTrue(MakeUpPostSampleAlpha.stackMatches(map));
        map.put("shaderlod_dev","0.1.0");assertTrue(MakeUpPostSampleAlpha.stackMatches(map));
        map.put("immediatelyfast","1.6.15+1.21.1");assertFalse(MakeUpPostSampleAlpha.stackMatches(map));
    }
    @Test void rejectsPeersMissingNativeModulesAndUnknownMods(){
        for(String peer:new String[]{"distanthorizons","voxy","entityculling","unknown"}){
            var map=minimal();map.put(peer,"1");assertFalse(MakeUpPostSampleAlpha.stackMatches(map));
        }
        var map=minimal();map.remove("sodium");assertFalse(MakeUpPostSampleAlpha.stackMatches(map));
        map=minimal();map.put("iris","changed");assertFalse(MakeUpPostSampleAlpha.stackMatches(map));
        assertFalse(MakeUpPostSampleAlpha.stackMatches(null));
    }
    @Test void refusesUnqualifiedMoreCullingTupleIncludingItsEmbeddedModule(){
        var map=minimal();
        map.put("moreculling","1.0.8");map.put("cloth_config","15.0.140");
        assertFalse(MakeUpPostSampleAlpha.stackMatches(map));
        map.put("modernfix","5.27.24+mc1.21.1");map.put("ferritecore","7.0.3");
        map.put("immediatelyfast","1.6.14+1.21.1");
        assertFalse(MakeUpPostSampleAlpha.stackMatches(map));
        map.put("conditional_mixin","0.6.3");
        assertFalse(MakeUpPostSampleAlpha.stackMatches(map));
        for(String peer:new String[]{"moreculling","cloth_config","conditional_mixin"}){
            var alone=minimal();alone.put(peer,map.get(peer));
            assertFalse(MakeUpPostSampleAlpha.stackMatches(alone),peer);
        }
    }
}
