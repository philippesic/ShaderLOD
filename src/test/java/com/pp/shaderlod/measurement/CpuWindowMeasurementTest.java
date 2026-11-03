package com.pp.shaderlod.measurement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CpuWindowMeasurementTest {
    @Test void coarseCumulativeEndpointsMeasureWholeWindowRatherThanSummedRenderSamples() {
        var clock=new CpuWindowMeasurement(new CpuWindowMeasurement.Snapshot(7,1_000,31_250_000,62_500_000));
        for(int i=0;i<1000;i++){clock.frameStarted(7);clock.frameCompleted(7);}
        var r=clock.finish(new CpuWindowMeasurement.Snapshot(7,2_000_001_000L,531_250_000,1_062_500_000));
        assertEquals(500_000_000L,r.threadCpuNs());assertEquals(0.5,r.threadCpuMsPerFrame());
        assertEquals(1.0,r.processCpuMsPerFrame());assertEquals(1000,r.completedFrames());
    }
    @Test void unavailableAndRegressedCountersRemainUnavailable() {
        var clock=new CpuWindowMeasurement(new CpuWindowMeasurement.Snapshot(7,1,20,-1));{clock.frameStarted(7);clock.frameCompleted(7);}
        var r=clock.finish(new CpuWindowMeasurement.Snapshot(7,100,10,-1));
        assertNull(r.threadCpuNs());assertEquals("counter regressed",r.threadStatus());assertNull(r.processCpuNs());
    }
    @Test void missingEitherEndpointAndNonpositiveWindowAreUnavailable() {
        for(long[] endpoints:new long[][]{{-1,40},{20,-1}}){
            var cpu=new CpuWindowMeasurement(new CpuWindowMeasurement.Snapshot(7,10,endpoints[0],10));cpu.frameStarted(7);cpu.frameCompleted(7);
            var result=cpu.finish(new CpuWindowMeasurement.Snapshot(7,100,endpoints[1],20));
            assertEquals("unsupported",result.threadStatus());assertNull(result.threadCpuMsPerFrame());assertEquals("available",result.processStatus());
        }
        var cpu=new CpuWindowMeasurement(new CpuWindowMeasurement.Snapshot(7,10,10,10));cpu.frameStarted(7);cpu.frameCompleted(7);
        assertEquals("nonpositive window",cpu.finish(new CpuWindowMeasurement.Snapshot(7,10,20,20)).threadStatus());
    }
    @Test void threadChangesZeroAdvanceAndEmptyWindowsCannotProduceCpuSavings() {
        var clock=new CpuWindowMeasurement(new CpuWindowMeasurement.Snapshot(7,1,20,30));{clock.frameStarted(7);clock.frameCompleted(7);}
        var r=clock.finish(new CpuWindowMeasurement.Snapshot(8,100,40,30));
        assertNull(r.threadCpuMsPerFrame());assertEquals("thread changed",r.threadStatus());
        assertNull(r.processCpuMsPerFrame());assertEquals("thread changed",r.processStatus());
        var sameThread=new CpuWindowMeasurement(new CpuWindowMeasurement.Snapshot(7,1,20,30));sameThread.frameStarted(7);sameThread.frameCompleted(7);
        assertEquals("counter did not advance",sameThread.finish(new CpuWindowMeasurement.Snapshot(7,100,20,30)).processStatus());
        assertNull(new CpuWindowMeasurement(new CpuWindowMeasurement.Snapshot(7,1,0,0))
            .finish(new CpuWindowMeasurement.Snapshot(7,100,50,50)).threadCpuMsPerFrame());
    }
}
