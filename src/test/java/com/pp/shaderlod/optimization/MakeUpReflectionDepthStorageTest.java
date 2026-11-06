package com.pp.shaderlod.optimization;

import com.pp.shaderlod.optimization.makeupshadow.MakeUpReflectionRuntime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MakeUpReflectionDepthStorageTest {
    private static int[] observed(){return new int[]{12,12,2560,1600,2560,1600,0x1902,24,0x8C17,0,0x2600,0x2600,0};}
    private static boolean accepts(int[] s,float anisotropy){return MakeUpReflectionRuntime.sampler(s[0],s[1],s[2],s[3],s[4],s[5],s[6],s[7],s[8],s[9],s[10],s[11],s[12],anisotropy);}
    @Test void observedBaseD24MatchesSizedD24WithoutChangingTheDescription(){
        var s=observed();assertTrue(accepts(s,1));assertEquals(0x1902,s[6]);
        s[6]=0x81A6;assertTrue(accepts(s,1));
    }
    @Test void ownershipDimensionsAndSamplerRestrictionsStillRefuse(){
        for(var bad:new int[][]{{0,0},{1,13},{2,1280},{3,800},{4,0},{5,0},{9,0x884E},{10,0x2703},{11,0x2601},{12,1}}){
            var s=observed();s[bad[0]]=bad[1];assertFalse(accepts(s,1),"field "+bad[0]);
        }
        for(float a:new float[]{0,2,Float.NaN,Float.POSITIVE_INFINITY})assertFalse(accepts(observed(),a));
    }
    @Test void baseEnumNeverInfersPrecisionOrType(){
        for(int bits:new int[]{0,16,32}){var s=observed();s[7]=bits;assertFalse(accepts(s,1));}
        for(int type:new int[]{0,0x1406,0x1404,0x1405}){var s=observed();s[8]=type;assertFalse(accepts(s,1));}
        var s=observed();s[6]=0x8814;assertFalse(accepts(s,1));
    }
}
