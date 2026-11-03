package com.pp.shaderlod.dev;

import com.pp.shaderlod.dev.mixin.DevEvidenceMixinPlugin;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkConsumerMixinGateTest {
    private static final String BENCHMARK="shaderlod.dev.benchmarkTunerProbe";
    private static final String[] KEYS={BENCHMARK,"shaderlod.dev.makeupMrtProbe","shaderlod.dev.spatialLayoutProbe",
        "shaderlod.dev.settledConsumerUniforms","shaderlod.dev.pixelCoverage","shaderlod.dev.pixelHistoryCapture",
        "shaderlod.dev.blissChainWitness","shaderlod.dev.sildurReceiverWitness","shaderlod.dev.makeupCloudWitness",
        "shaderlod.dev.bslMipWitness","shaderlod.dev.bslMipParity","shaderlod.dev.compositeCopyWitness",
        "shaderlod.dev.complementaryCopyGraph","shaderlod.dev.rasterActivationProbe","shaderlod.dev.cloudSamplerProbe"};
    private static final String[] HOOKS={"SpatialPassAccessor","SpatialCompositeMixin","SpatialQuadMixin"};
    private static boolean allowed(String name){return new DevEvidenceMixinPlugin().shouldApplyMixin("unused","com.pp.shaderlod.dev.mixin."+name);}
    private static void cleanProperties(Runnable check){
        var old=new HashMap<String,String>();for(var key:KEYS){old.put(key,System.getProperty(key));System.clearProperty(key);}
        try{check.run();}finally{for(var key:KEYS)if(old.get(key)==null)System.clearProperty(key);else System.setProperty(key,old.get(key));}
    }
    @Test void onlyNonblankBenchmarkPropertyAdmitsTheThreeConsumerHooks(){
        cleanProperties(()->{
            for(String value:new String[]{null,""," ","\t"}){
                if(value==null)System.clearProperty(BENCHMARK);else System.setProperty(BENCHMARK,value);
                for(var hook:HOOKS)assertFalse(allowed(hook),hook+"/"+value);
                assertFalse(allowed("SpatialFrameMixin"));
            }
            System.setProperty(BENCHMARK,"/fresh/control/probe.json");
            for(var hook:HOOKS)assertTrue(allowed(hook),hook);
            assertFalse(allowed("SpatialFrameMixin"));assertFalse(allowed("PixelHistoryFrameMixin"));
            assertFalse(allowed("RasterSamplerStateMixin"));
        });
    }
    @Test void legacyHistoryAndSettledConsumerOptInsKeepTheirExistingFramePolicy(){
        cleanProperties(()->{
            System.setProperty("shaderlod.dev.pixelHistoryCapture","true");
            for(var hook:HOOKS)assertTrue(allowed(hook));
            assertTrue(allowed("SpatialFrameMixin"));assertTrue(allowed("PixelHistoryFrameMixin"));
            System.clearProperty("shaderlod.dev.pixelHistoryCapture");
            System.setProperty("shaderlod.dev.settledConsumerUniforms","/legacy/request.json");
            for(var hook:HOOKS)assertTrue(allowed(hook));
            assertFalse(allowed("SpatialFrameMixin"));assertFalse(allowed("PixelHistoryFrameMixin"));
        });
    }
}
