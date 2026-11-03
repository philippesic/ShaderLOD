package com.pp.shaderlod.measurement;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CpuWindowCaptureTest {
    static final FrameMeasurement.GpuBackend OFF=new FrameMeasurement.GpuBackend(){
        public String status(){return "off";} public int counterBits(){return 0;}
        public int create(){throw new AssertionError();}public boolean busy(){throw new AssertionError();}
        public void begin(int q){throw new AssertionError();}public void end(){throw new AssertionError();}
        public boolean available(int q){throw new AssertionError();}public long nanoseconds(int q){throw new AssertionError();}public void delete(int q){throw new AssertionError();}
    };
    @Test void completedFramesIncludeFirstBoundaryButExcludeIncompleteAndDrainWork() {
        long[] t={100,10,20};
        var clock=new FrameMeasurement(OFF,()->t[0],()->t[1],()->t[2]);
        assertNull(clock.pauseCpuWindow());clock.begin();clock.end();assertTrue(clock.drain().isEmpty());
        clock.enabled(true);clock.end(); // unmatched return does not count
        clock.begin();t[0]=200;t[1]=20;t[2]=40;clock.end();
        t[0]=500;t[1]=50;t[2]=100;clock.begin();t[0]=600;t[1]=60;t[2]=120;clock.end();
        t[0]=700;t[1]=70;t[2]=140;clock.begin(); // incomplete frame at stop
        t[0]=800;t[1]=80;t[2]=160;var result=clock.pauseCpuWindow();
        assertEquals(2,result.completedFrames());assertEquals(700,result.elapsedWallNs());assertEquals(70,result.threadCpuNs());
        t[0]=9000;t[1]=900;t[2]=1800;clock.end();clock.poll();clock.drain();
        assertSame(result,clock.pauseCpuWindow());clock.enabled(false);assertSame(result,clock.pauseCpuWindow());
    }
    @Test void frameThreadMismatchAndNoCompletedFramesCannotYieldCpuAverage() {
        var window=new CpuWindowMeasurement(new CpuWindowMeasurement.Snapshot(7,1,10,20));
        window.frameCompleted(7);window.frameStarted(7);window.frameCompleted(8);
        var result=window.finish(new CpuWindowMeasurement.Snapshot(7,100,30,60));
        assertEquals(0,result.completedFrames());assertEquals("frame thread changed",result.threadStatus());assertNull(result.processCpuMsPerFrame());
        window.frameStarted(7);window.frameCompleted(7);assertSame(result,window.finish(new CpuWindowMeasurement.Snapshot(7,200,40,80)));
    }
    @Test void rawZerosRemainWhileZeroHeavyDiagnosticDoesNotClaimResolution() {
        var samples=List.of(new FrameMeasurement.Sample(1,1,null,100,0L,-1L,null,"off"),
                new FrameMeasurement.Sample(2,1,100L,100,0L,null,null,"off"),
                new FrameMeasurement.Sample(3,1,100L,100,15_625_000L,null,null,"off"));
        var report=CpuMeasurementReport.observations(null,samples);
        assertTrue(report.get("render_cpu.status").contains("coarse/zero-heavy"));assertEquals("2",report.get("render_cpu.zeroSamples"));
        assertEquals("15625000",report.get("render_cpu.minimumPositiveDeltaNs"));assertEquals("unavailable",report.get("render_scope_process_cpu.status"));
        assertTrue(report.get("cpu_window.threadStatus").contains("recording off"));assertEquals(0L,samples.getFirst().renderCpuNs());
        assertTrue(CpuMeasurementReport.observations(null,samples.subList(0,2)).get("render_cpu.status").contains("did not advance"));
    }
    @Test void restartingWindowCannotReuseFrozenCpuEndpoints() {
        long[] t={100};var clock=new FrameMeasurement(OFF,()->t[0],()->t[0],()->t[0]);
        clock.enabled(true);clock.begin();t[0]=200;clock.end();t[0]=300;
        var first=clock.pauseCpuWindow();clock.resetCpuWindow();clock.invalidate();
        t[0]=1000;clock.begin();t[0]=1200;clock.end();t[0]=1300;
        var second=clock.pauseCpuWindow();assertEquals(100,first.start().wallNs());assertEquals(1000,second.start().wallNs());assertEquals(1,second.completedFrames());
    }
    @Test void disabledRecordingNeverReadsCpuCounters() {
        var clock=new FrameMeasurement(OFF,()->{throw new AssertionError();},()->{throw new AssertionError();},()->{throw new AssertionError();});
        clock.begin();clock.end();clock.poll();assertNull(clock.pauseCpuWindow());assertTrue(clock.drain().isEmpty());
    }
    @Test void repeatedLiveSnapshotsContinueWithNewCompletedFrames() {
        long[] t={100};var clock=new FrameMeasurement(OFF,()->t[0],()->t[0],()->t[0]);
        clock.enabled(true);clock.begin();t[0]=200;clock.end();t[0]=300;
        var first=clock.snapshotCpuWindow();assertEquals(1,first.completedFrames());
        t[0]=400;clock.begin();t[0]=500;clock.end();t[0]=600;
        var second=clock.snapshotCpuWindow();assertEquals(2,second.completedFrames());assertEquals(500,second.threadCpuNs());
        assertEquals(1,first.completedFrames());assertNotSame(first,second);
        var stopped=clock.pauseCpuWindow();t[0]=10000;
        assertSame(stopped,clock.snapshotCpuWindow());assertEquals(500,stopped.threadCpuNs());
    }
    @Test void explicitEpochInvalidationDropsCpuButOrdinaryStopRetainsIt() {
        long[] t={100};var clock=new FrameMeasurement(OFF,()->t[0],()->t[0],()->t[0]);
        clock.enabled(true);clock.begin();t[0]=200;clock.end();t[0]=300;
        var first=clock.pauseCpuWindow();clock.enabled(false);assertSame(first,clock.snapshotCpuWindow());
        clock.invalidate();assertNull(clock.snapshotCpuWindow());
        clock.enabled(true);t[0]=1000;clock.begin();t[0]=1200;clock.end();t[0]=1300;
        var second=clock.pauseCpuWindow();assertEquals(1000,second.start().wallNs());
        assertEquals(1,second.completedFrames());assertEquals(300,second.threadCpuNs());
    }
}
