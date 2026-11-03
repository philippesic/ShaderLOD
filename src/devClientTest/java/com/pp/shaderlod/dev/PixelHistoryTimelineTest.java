package com.pp.shaderlod.dev;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PixelHistoryTimelineTest {
    private static PixelHistoryTimeline.Values values(int global, int reset) { return new PixelHistoryTimeline.Values(2, global, 1, reset); }
    @Test void bothDirectionsCaptureExactOffsetsAndSwitchOnlyAfterCompleteFrame() {
        var timeline = new PixelHistoryTimeline(); var names = new ArrayList<String>();
        var first = timeline.frame(100, values(0,0), values(0,0));
        assertEquals("off-pre-switch", first.capture()); assertEquals(Boolean.TRUE, first.switchTo()); names.add(first.capture());
        for (int phase=1, counter=101; phase<=2; phase++) for (int offset=0; offset<=60; offset++,counter++) {
            var v=values(phase==1?1:0,offset==0?1:0); var a=timeline.frame(counter,v,v);
            assertEquals(phase,a.phase()); assertEquals(offset,a.offset());
            assertEquals(phase==2 && offset==60,a.done());
            if(a.capture()!=null) names.add(a.capture());
            assertEquals(phase==1 && offset==60?Boolean.FALSE:null,a.switchTo());
        }
        assertEquals(List.of("off-pre-switch","on-frame-plus-0","on-frame-plus-1","on-frame-plus-4","on-frame-plus-16","on-frame-plus-60","off-frame-plus-0","off-frame-plus-1","off-frame-plus-4","off-frame-plus-16","off-frame-plus-60"),names);
        assertThrows(IllegalStateException.class,()->timeline.frame(223,values(0,0),values(0,0)));
    }
    @Test void actualConsumersMustAgreeEveryFrame() { assertThrows(IllegalStateException.class,()->new PixelHistoryTimeline().frame(12,values(0,0),values(0,1))); }
    @Test void unknownAndWrongProtocolsFailRatherThanBecomeStable() {
        for(var v:List.of(new PixelHistoryTimeline.Values(0,0,0,0),new PixelHistoryTimeline.Values(2,0,0,1),new PixelHistoryTimeline.Values(3,0,1,0)))
            assertThrows(IllegalStateException.class,()->new PixelHistoryTimeline().frame(12,v,v));
    }
    @Test void firstSwitchRequiresResetAndNextRequiresNoReset() {
        for(int wrongReset:List.of(0,2,-1)) {
            var t=new PixelHistoryTimeline();t.frame(12,values(0,0),values(0,0));
            assertThrows(IllegalStateException.class,()->t.frame(13,values(1,wrongReset),values(1,wrongReset)));
        }
        var t=new PixelHistoryTimeline();t.frame(12,values(0,0),values(0,0));t.frame(13,values(1,1),values(1,1));
        assertThrows(IllegalStateException.class,()->t.frame(14,values(1,1),values(1,1)));
    }
    @Test void missedDuplicateAndWrappedCounterFail() {
        for(int bad:List.of(12,14,0,-1)) { var t=new PixelHistoryTimeline();t.frame(12,values(0,0),values(0,0)); assertThrows(IllegalStateException.class,()->t.frame(bad,values(1,1),values(1,1))); }
    }
    @Test void wrongGlobalOnFirstSwitchFailsWithoutSettlingExclusion() {
        var t=new PixelHistoryTimeline(); t.frame(12,values(0,0),values(0,0));
        assertThrows(IllegalStateException.class,()->t.frame(13,values(0,0),values(0,0)));
    }
    @Test void initialBaselineRejectsResetAndOnState() {
        for(var v:List.of(values(0,1),values(1,0),values(1,1))) assertThrows(IllegalStateException.class,()->new PixelHistoryTimeline().frame(12,v,v));
    }
}
