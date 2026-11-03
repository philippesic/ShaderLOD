package com.pp.shaderlod.dev;

import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PixelHistoryPropertiesTest {
    @Test void timerDefaultsCannotSilentlyEnableQueriesAndCameraModesAreExclusive() {
        var keys=List.of("pixelHistoryCapture","gpuTiming","cpuTiming","benchmark","measureWorkload","cameraQuality","cloudSamplerProbe","shadowObserve","spatialLayoutProbe","pixelCoverage","workTrace","shadowCoherenceOracle","dhDrawEvidence","cpuLayerTiming","gpuPassTiming","gpuAttribution","workTraceOnly","pixelCoverageOnly","spatialLayoutOnly","benchmarkOnly","cameraQualityOnly");
        var old=new LinkedHashMap<String,String>();
        try {
            for(String key:keys){String k="shaderlod.dev."+key;old.put(k,System.getProperty(k));System.clearProperty(k);}
            assertDoesNotThrow(PixelHistorySession::requireProperties);
            System.setProperty("shaderlod.dev.pixelHistoryCapture","true");
            assertThrows(IllegalStateException.class,PixelHistorySession::requireProperties);
            System.setProperty("shaderlod.dev.gpuTiming","false");
            assertThrows(IllegalStateException.class,PixelHistorySession::requireProperties);
            System.setProperty("shaderlod.dev.cpuTiming","false");
            assertDoesNotThrow(PixelHistorySession::requireProperties);
            for(String flag:List.of("benchmark","measureWorkload","cloudSamplerProbe","shadowObserve","spatialLayoutProbe","pixelCoverage","workTrace","shadowCoherenceOracle","dhDrawEvidence","cpuLayerTiming","gpuPassTiming","gpuAttribution","workTraceOnly","pixelCoverageOnly","spatialLayoutOnly","benchmarkOnly","cameraQualityOnly")) {
                System.setProperty("shaderlod.dev."+flag,"true");
                assertThrows(IllegalStateException.class,PixelHistorySession::requireProperties);
                System.clearProperty("shaderlod.dev."+flag);
            }
            System.setProperty("shaderlod.dev.cameraQuality","request.json");
            assertThrows(IllegalStateException.class,PixelHistorySession::requireProperties);
        } finally {old.forEach((k,v)->{if(v==null)System.clearProperty(k);else System.setProperty(k,v);});}
    }
}
