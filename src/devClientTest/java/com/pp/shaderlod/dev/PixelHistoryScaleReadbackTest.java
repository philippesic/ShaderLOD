package com.pp.shaderlod.dev;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PixelHistoryScaleReadbackTest {
    private static final int INT = 0x1404;
    private static final String VERSION="shaderlodScaleHistoryVersion";
    private static PixelHistorySession.UniformObservation read(String name,int value) {
        return new PixelHistorySession.UniformObservation(name,INT,1,4,value,"read",null);
    }
    private static Map<String,PixelHistorySession.UniformObservation> values(int version,int previous,int transition) {
        var map=new LinkedHashMap<String,PixelHistorySession.UniformObservation>();
        map.put(VERSION,read(VERSION,version));
        map.put("shaderlodPreviousEnabled",read("shaderlodPreviousEnabled",previous));
        map.put("shaderlodScaleTransition",read("shaderlodScaleTransition",transition));return map;
    }
    @Test void actualInventoryShapeAndLocationControlWhetherIntegerCanBeRead() {
        int[] integerReads={0};
        var reader=new PixelHistorySession.UniformReader(){public int location(String n){return 4;} public int integer(int location){integerReads[0]++;return 1;}};
        var valid=PixelHistorySession.observeUniform(VERSION,Map.of(VERSION,new PixelHistorySession.UniformShape(INT,1)),reader);
        assertEquals("read",valid.status());assertEquals(1,valid.integerValue());assertEquals(4,valid.location());
        for(var entry:Map.of("missing",Map.<String,PixelHistorySession.UniformShape>of(),"float",Map.of(VERSION,new PixelHistorySession.UniformShape(0x1406,1)),"array",Map.of(VERSION+"[0]",new PixelHistorySession.UniformShape(INT,1)),"size2",Map.of(VERSION,new PixelHistorySession.UniformShape(INT,2))).entrySet()) {
            var unknown=PixelHistorySession.observeUniform(VERSION,entry.getValue(),reader);
            assertNotEquals("read",unknown.status());assertNull(unknown.integerValue());assertEquals(4,unknown.location());
        }
        assertEquals(1,integerReads[0]);
    }
    @Test void unavailableLocationAndReadFailureAreRawUnknownsNotZero() {
        var inventory=Map.of(VERSION,new PixelHistorySession.UniformShape(INT,1));
        var noLocation=PixelHistorySession.observeUniform(VERSION,inventory,new PixelHistorySession.UniformReader(){public int location(String n){return -1;}public int integer(int l){fail("must not read");return 0;}});
        assertEquals("location_unknown",noLocation.status());assertNull(noLocation.integerValue());assertEquals(-1,noLocation.location());
        var failed=PixelHistorySession.observeUniform(VERSION,inventory,new PixelHistorySession.UniformReader(){public int location(String n){return 4;}public int integer(int l){throw new IllegalStateException("native read unavailable");}});
        assertEquals("read_failed",failed.status());assertNull(failed.integerValue());assertTrue(failed.error().contains("native read unavailable"));
    }
    @Test void onlyActualConsumingOptionCombinationsRequireExtension() {
        for(boolean cloud:new boolean[]{false,true}) for(boolean taa:new boolean[]{false,true}) {
            assertEquals(cloud,PixelHistorySession.extensionRequired("deferred2",cloud,taa));
            assertEquals(cloud&&taa,PixelHistorySession.extensionRequired("composite4",cloud,taa));
        }
        assertNull(PixelHistorySession.validateScaleHistory(Map.of(),false,0,0,-1));
        assertThrows(IllegalStateException.class,()->PixelHistorySession.validateScaleHistory(Map.of(),true,0,0,-1));
    }
    @Test void bothActualTimelineSwitchDirectionsAndAllStableFramesRequireCorrectPreviousScale() {
        var timeline=new PixelHistoryTimeline();int frame=100;
        var base=new PixelHistoryTimeline.Values(2,0,1,0);
        var action=timeline.frame(frame++,base,base);
        assertEquals(new PixelHistorySession.ScaleValues(1,0,0),PixelHistorySession.validateScaleHistory(values(1,0,0),true,0,action.phase(),action.offset()));
        for(int phase=1;phase<=2;phase++) for(int offset=0;offset<=60;offset++) {
            int global=phase==1?1:0; var current=new PixelHistoryTimeline.Values(2,global,1,offset==0?1:0);
            action=timeline.frame(frame++,current,current);
            int previous=offset==0?1-global:global,transition=offset==0?1:0;
            assertEquals(new PixelHistorySession.ScaleValues(1,previous,transition),PixelHistorySession.validateScaleHistory(values(1,previous,transition),true,global,action.phase(),action.offset()));
        }
    }
    @Test void invalidTypedReadProtocolAndEarlyOrStuckTransitionsFail() {
        for(var bad:new int[][]{{0,0,1},{2,0,1},{1,-1,1},{1,2,1},{1,0,-1},{1,0,2},{1,1,1},{1,0,0}})
            assertThrows(IllegalStateException.class,()->PixelHistorySession.validateScaleHistory(values(bad[0],bad[1],bad[2]),true,1,1,0));
        assertThrows(IllegalStateException.class,()->PixelHistorySession.validateScaleHistory(values(1,0,1),true,1,1,1));
        var map=values(1,0,1);map.put(VERSION,new PixelHistorySession.UniformObservation(VERSION,0x1406,1,4,1,"read",null));
        assertThrows(IllegalStateException.class,()->PixelHistorySession.validateScaleHistory(map,true,1,1,0));
    }
}
