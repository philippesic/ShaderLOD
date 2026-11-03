package com.pp.shaderlod.measurement;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ManualWindowTest {
    static final class Gpu implements FrameMeasurement.GpuBackend {
        int issued,deleted;boolean ready;
        public String status(){return "fake elapsed query";}public int counterBits(){return 64;}
        public int create(){return ++issued;}public boolean busy(){return false;}
        public void begin(int q){}public void end(){}public boolean available(int q){return ready;}
        public long nanoseconds(int q){return 42;}public void delete(int q){deleted++;}
    }
    void timer(FrameMeasurement timer)throws Exception {
        var field=RuntimeMeasurement.class.getDeclaredField("timer");field.setAccessible(true);field.set(null,timer);
    }
    @Test void pausedWindowDrainsWithoutIssuingOrReadingSceneAndSurvivesInvalidation()throws Exception {
        var gpu=new Gpu();var clock=new AtomicLong();
        var frame=new FrameMeasurement(gpu,()->clock.addAndGet(100),()->0,()->0);
        RuntimeMeasurement.request(true);timer(frame);frame.enabled(true);
        try {
            frame.begin();frame.end();assertEquals(1,frame.pendingQueries());
            RuntimeMeasurement.pauseWindow();RuntimeMeasurement.begin(true);RuntimeMeasurement.end();
            assertEquals(1,gpu.issued);assertTrue(RuntimeMeasurement.requested());
            gpu.ready=true;RuntimeMeasurement.pollSamples();assertEquals(0,RuntimeMeasurement.pendingQueries());
            assertEquals(42L,RuntimeMeasurement.drain().getFirst().gpuNs());
            RuntimeMeasurement.invalidate();RuntimeMeasurement.begin(true);RuntimeMeasurement.end();
            assertEquals(1,gpu.issued,"Invalidation must not reopen paused capture or inspect a Minecraft scene");
            assertTrue(RuntimeMeasurement.requested());assertTrue(RuntimeMeasurement.drain().isEmpty());
            RuntimeMeasurement.resumeWindow();assertTrue(RuntimeMeasurement.drain().isEmpty());
        } finally {RuntimeMeasurement.close();timer(null);}
    }
}
