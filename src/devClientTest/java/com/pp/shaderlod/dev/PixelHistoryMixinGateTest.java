package com.pp.shaderlod.dev;

import com.pp.shaderlod.dev.mixin.DevEvidenceMixinPlugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PixelHistoryMixinGateTest {
    @Test void defaultOffExcludesDrawAndCaptureHooks() {
        String[] keys={"shaderlod.dev.pixelHistoryCapture","shaderlod.dev.spatialLayoutProbe","shaderlod.dev.pixelCoverage"};
        String[] old=new String[keys.length];
        try {
            for(int i=0;i<keys.length;i++){old[i]=System.getProperty(keys[i]);System.clearProperty(keys[i]);}
            var plugin=new DevEvidenceMixinPlugin();
            for(String name:new String[]{"SpatialPassAccessor","SpatialCompositeMixin","SpatialQuadMixin","PixelHistoryFrameMixin"})
                assertFalse(plugin.shouldApplyMixin("unused","com.pp.shaderlod.dev.mixin."+name));
            System.setProperty(keys[0],"true");
            for(String name:new String[]{"SpatialPassAccessor","SpatialCompositeMixin","SpatialQuadMixin","PixelHistoryFrameMixin"})
                assertTrue(plugin.shouldApplyMixin("unused","com.pp.shaderlod.dev.mixin."+name));
            assertFalse(plugin.shouldApplyMixin("unused","com.pp.shaderlod.dev.mixin.RasterSamplerStateMixin"));
        } finally {for(int i=0;i<keys.length;i++)if(old[i]==null)System.clearProperty(keys[i]);else System.setProperty(keys[i],old[i]);}
    }
}
