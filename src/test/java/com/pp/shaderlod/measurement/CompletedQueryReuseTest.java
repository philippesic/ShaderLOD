package com.pp.shaderlod.measurement;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class CompletedQueryReuseTest {
    static class Gpu implements FrameMeasurement.GpuBackend {
        final Set<Integer> live=new HashSet<>(),ready=new HashSet<>(),ended=new HashSet<>();
        final List<Integer> starts=new ArrayList<>(),deletes=new ArrayList<>();
        final Map<Integer,Long> results=new HashMap<>();
        boolean reuse,allReady,busy,failRead,failAvailable,failDelete,failEnd,failBegin;
        int created,active,reads,maxLive;long result=100;
        public boolean reuseCompletedQueries(){return reuse;}
        public String status(){return "fake elapsed";}public int counterBits(){return 64;}
        public int create(){int q=++created;assertTrue(live.add(q));maxLive=Math.max(maxLive,live.size());return q;}
        public boolean busy(){return busy;}
        public void begin(int q){assertTrue(live.contains(q));assertEquals(0,active);assertFalse(busy);
            if(failBegin)throw new IllegalStateException("begin failed");
            active=q;starts.add(q);ended.remove(q);ready.remove(q);results.put(q,++result);}
        public void end(){if(active==0)throw new IllegalStateException("no owned scope");ended.add(active);active=0;if(failEnd)throw new IllegalStateException("end failed");}
        public boolean available(int q){assertTrue(ended.contains(q));if(failAvailable)throw new IllegalStateException("available failed");return allReady||ready.contains(q);}
        public long nanoseconds(int q){assertTrue(ended.contains(q));assertTrue(allReady||ready.contains(q),"no unresolved read");reads++;
            if(failRead)throw new IllegalStateException("read failed");return results.get(q);}
        public void delete(int q){assertNotEquals(q,active,"never delete an active query");assertTrue(live.remove(q),"exactly one delete attempt");deletes.add(q);ended.remove(q);ready.remove(q);
            if(failDelete)throw new IllegalStateException("delete failed");}
    }
    static final class Fixture {
        final Gpu gpu=new Gpu();final AtomicLong clock=new AtomicLong(100);
        final FrameMeasurement timer;
        Fixture(boolean reuse){gpu.reuse=reuse;timer=new FrameMeasurement(gpu,clock::get,()->0,()->0);timer.enabled(true);}
        void frame(){timer.begin();clock.addAndGet(10);timer.end();clock.addAndGet(90);}
    }
    @Test void defaultRetainsPerFrameAllocationDeletionAndClocks(){
        var f=new Fixture(false);f.gpu.allReady=true;
        // Changing the fake flag after construction cannot change this timer's mode.
        f.gpu.reuse=true;for(int i=0;i<40;i++)f.frame();
        assertEquals(40,f.gpu.created);assertEquals(40,f.gpu.deletes.size());assertTrue(f.gpu.live.isEmpty());
        var samples=f.timer.drain();assertEquals(40,samples.size());assertNull(samples.getFirst().frameWallNs());
        for(int i=0;i<samples.size();i++){var s=samples.get(i);assertEquals(i+1,s.frame());assertEquals(101+i,s.gpuNs());assertEquals(10,s.renderWallNs());assertEquals(0,s.renderCpuNs());assertEquals(0,s.processCpuNs());if(i>0)assertEquals(100,s.frameWallNs());}
        f.timer.enabled(false);
    }
    @Test void completedReuseKeepsEveryFrameResultAndBoundedOwnership(){
        var f=new Fixture(true);f.gpu.allReady=true;f.gpu.reuse=false;
        for(int i=0;i<400;i++)f.frame();
        assertEquals(1,f.gpu.created);assertEquals(400,f.gpu.starts.size());assertEquals(400,f.gpu.reads);assertEquals(1,f.gpu.maxLive);assertTrue(f.gpu.deletes.isEmpty());
        var samples=f.timer.drain();assertEquals(400,samples.size());
        for(int i=0;i<samples.size();i++){var s=samples.get(i);assertEquals(i+1,s.frame());assertEquals(101+i,s.gpuNs());assertEquals(10,s.renderWallNs());assertEquals(0,s.renderCpuNs());if(i>0)assertEquals(100,s.frameWallNs());}
        f.timer.enabled(false);assertEquals(List.of(1),f.gpu.deletes);assertTrue(f.gpu.live.isEmpty());
        f.timer.enabled(true);f.frame();assertEquals(2,f.gpu.created);f.timer.enabled(false);
    }
    @Test void pendingNeverReusedAndFullRingStillSkipsWithoutReading(){
        var f=new Fixture(true);for(int i=0;i<10;i++)f.frame();
        assertEquals(8,f.gpu.created);assertEquals(8,f.timer.pendingQueries());assertEquals(0,f.gpu.reads);assertEquals(8,f.gpu.maxLive);
        var skipped=f.timer.drain();assertEquals(2,skipped.size());assertTrue(skipped.stream().allMatch(s->s.gpuNs()==null&&s.gpuStatus().contains("ring full")));
        f.gpu.allReady=true;f.timer.poll();assertEquals(8,f.timer.drain().size());
        for(int i=0;i<40;i++)f.frame();assertEquals(8,f.gpu.created);assertEquals(8,f.gpu.maxLive);
        f.timer.enabled(false);assertEquals(8,new HashSet<>(f.gpu.deletes).size());assertEquals(8,f.gpu.deletes.size());
    }
    @Test void invalidationReleasesPoolPendingAndActiveExactlyOnceWithoutReadingAbandonedResults(){
        var f=new Fixture(true);for(int i=0;i<3;i++)f.frame();
        f.gpu.ready.addAll(List.of(1,2));f.timer.poll();assertEquals(2,f.gpu.reads);
        f.timer.begin();assertEquals(1,f.gpu.active);assertEquals(1,f.timer.pendingQueries());
        f.gpu.ready.add(3);f.timer.invalidate();assertEquals(2,f.gpu.reads,"abandoned ready query must not be read/recycled");
        assertEquals(Set.of(1,2,3),new HashSet<>(f.gpu.deletes));assertEquals(3,f.gpu.deletes.size());assertTrue(f.gpu.live.isEmpty());assertEquals(0,f.gpu.active);assertTrue(f.timer.drain().isEmpty());
        f.timer.invalidate();f.timer.enabled(false);assertEquals(3,f.gpu.deletes.size());
    }
    @Test void disablingAlsoDrainsMixedOwnership(){
        var f=new Fixture(true);for(int i=0;i<3;i++)f.frame();f.gpu.ready.addAll(List.of(1,2));f.timer.poll();f.timer.begin();
        f.timer.enabled(false);assertTrue(f.gpu.live.isEmpty());assertEquals(3,f.gpu.deletes.size());assertEquals(0,f.timer.pendingQueries());
        int starts=f.gpu.starts.size();f.timer.begin();f.timer.end();assertEquals(starts,f.gpu.starts.size());
    }
    @Test void timeoutAndNonpositiveResultsAreDeletedAndNeverRecycled(){
        var f=new Fixture(true);f.frame();f.clock.addAndGet(6_000_000_000L);f.timer.poll();
        assertEquals(List.of(1),f.gpu.deletes);assertTrue(f.timer.drain().getFirst().gpuStatus().contains("timeout"));
        f.frame();f.gpu.results.put(2,0L);f.gpu.ready.add(2);f.timer.poll();assertNull(f.timer.drain().getFirst().gpuNs());
        f.frame();f.gpu.results.put(3,-1L);f.gpu.ready.add(3);f.timer.poll();assertNull(f.timer.drain().getFirst().gpuNs());
        f.frame();assertEquals(List.of(1,2,3,4),f.gpu.starts);f.timer.enabled(false);assertEquals(4,f.gpu.deletes.size());
    }
    @Test void readOrAvailabilityFailuresCannotBecomeReusableEvenIfCallerRetries(){
        for(boolean readFailure:new boolean[]{false,true}){
            var f=new Fixture(true);f.frame();f.gpu.allReady=true;f.gpu.failRead=readFailure;f.gpu.failAvailable=!readFailure;
            assertThrows(IllegalStateException.class,f.timer::poll);assertEquals(0,f.timer.pendingQueries());assertEquals(List.of(1),f.gpu.deletes);
            f.gpu.failRead=false;f.gpu.failAvailable=false;f.frame();assertEquals(List.of(1,2),f.gpu.starts);f.timer.enabled(false);assertEquals(2,f.gpu.deletes.size());
        }
    }
    @Test void deletionFailureIsNotRetriedAndCleanupContinuesForOtherOwners(){
        var f=new Fixture(true);for(int i=0;i<3;i++)f.frame();f.gpu.failDelete=true;
        assertThrows(IllegalStateException.class,f.timer::invalidate);assertEquals(3,f.gpu.deletes.size());assertTrue(f.gpu.live.isEmpty());
        f.timer.invalidate();assertEquals(3,f.gpu.deletes.size());f.timer.enabled(false);
    }
    @Test void invalidResultDeletionFailureCannotLeaveStalePendingOrFreeEntry(){
        var f=new Fixture(true);f.frame();f.gpu.results.put(1,0L);f.gpu.allReady=true;f.gpu.failDelete=true;
        assertThrows(IllegalStateException.class,f.timer::poll);assertEquals(0,f.timer.pendingQueries());
        f.gpu.failDelete=false;f.frame();assertEquals(List.of(1,2),f.gpu.starts);f.timer.enabled(false);assertEquals(List.of(1,2),f.gpu.deletes);
    }
    @Test void failedEndAndBeginAreNeverPooledAndCleanupRetainsOwnership(){
        var f=new Fixture(true);f.timer.begin();f.gpu.failEnd=true;assertThrows(IllegalStateException.class,f.timer::end);
        f.gpu.failEnd=false;
        // Fake's first end already released the active scope; cleanup still attempts deletion.
        assertThrows(IllegalStateException.class,f.timer::invalidate);
        assertTrue(f.gpu.live.isEmpty());assertEquals(List.of(1),f.gpu.deletes);
        f.gpu.failBegin=true;assertThrows(IllegalStateException.class,f.timer::begin);
        assertThrows(IllegalStateException.class,f.timer::invalidate);
        assertEquals(List.of(1,2),f.gpu.deletes);assertTrue(f.gpu.live.isEmpty());f.timer.enabled(false);
    }
    @Test void foreignElapsedOwnerSkipsEvenWhenCompletedPoolExists(){
        var f=new Fixture(true);f.gpu.allReady=true;f.frame();f.gpu.busy=true;f.frame();
        var s=f.timer.drain();assertEquals(2,s.size());assertNull(s.getLast().gpuNs());assertTrue(s.getLast().gpuStatus().contains("another elapsed"));assertEquals(1,f.gpu.starts.size());
        f.gpu.busy=false;f.frame();assertEquals(List.of(1,1),f.gpu.starts);f.timer.enabled(false);
    }
    @Test void asynchronousCompletionPreservesExistingPublicationOrder(){
        var f=new Fixture(true);f.frame();f.frame();
        f.gpu.ready.add(2);f.timer.poll();var second=f.timer.drain().getFirst();
        assertEquals(2,second.frame());assertEquals(102,second.gpuNs());
        f.gpu.ready.add(1);f.timer.poll();var first=f.timer.drain().getFirst();
        assertEquals(1,first.frame());assertEquals(101,first.gpuNs());
        f.timer.enabled(false);assertEquals(Set.of(1,2),new HashSet<>(f.gpu.deletes));
    }
    @Test void queryOffOverridesFrozenReusePropertyWithoutGlContext(){
        String off="shaderlod.dev.disableGpuQueries",reuse="shaderlod.dev.reuseCompletedGpuQueries";
        String priorOff=System.getProperty(off),priorReuse=System.getProperty(reuse);
        try {
            System.clearProperty(off);System.setProperty(reuse,"true");var on=new OpenGlElapsedBackend();
            System.clearProperty(reuse);assertTrue(on.reuseCompletedQueries());
            var ordinary=new OpenGlElapsedBackend();System.setProperty(reuse,"true");assertFalse(ordinary.reuseCompletedQueries());
            System.setProperty(off,"true");var disabled=new OpenGlElapsedBackend();System.clearProperty(off);
            assertTrue(disabled.diagnosticQueryOff());assertFalse(disabled.reuseCompletedQueries());
            assertEquals(0,disabled.counterBits());assertTrue(disabled.status().contains("disabled"));
        } finally {
            if(priorOff==null)System.clearProperty(off);else System.setProperty(off,priorOff);
            if(priorReuse==null)System.clearProperty(reuse);else System.setProperty(reuse,priorReuse);
        }
    }
    @Test void runtimeReloadAndCloseReleaseCompletedPool() throws Exception {
        var f=new Fixture(true);f.gpu.allReady=true;f.frame();
        var field=RuntimeMeasurement.class.getDeclaredField("timer");field.setAccessible(true);
        RuntimeMeasurement.request(true);field.set(null,f.timer);
        try {
            RuntimeMeasurement.onReload();assertEquals(List.of(1),f.gpu.deletes);assertTrue(f.gpu.live.isEmpty());
            f.frame();assertEquals(2,f.gpu.created);assertEquals(Set.of(2),f.gpu.live);
            RuntimeMeasurement.close();assertEquals(List.of(1,2),f.gpu.deletes);assertTrue(f.gpu.live.isEmpty());
            assertNull(field.get(null));assertFalse(RuntimeMeasurement.requested());
        } finally {RuntimeMeasurement.close();field.set(null,null);}
    }
}
