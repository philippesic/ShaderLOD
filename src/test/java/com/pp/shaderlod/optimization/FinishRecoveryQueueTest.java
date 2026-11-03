package com.pp.shaderlod.optimization;

import com.pp.shaderlod.CoreUniformFrame;
import com.pp.shaderlod.LodSettings;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Executes actual Apply + Session + selection recovery. Only prepared file metadata is fixture-seeded.
 * No shader archives, GL, or personal config are used. The native queue is the actual mutable backend map. */
class FinishRecoveryQueueTest {
    @TempDir Path directory;
    OptimizationConfig.State saved;
    boolean oldLive;
    @BeforeEach void before() throws Exception {
        saved=OptimizationConfig.state();
        var field=OptimizationEditTransaction.class.getDeclaredField("liveUniformUpdates");field.setAccessible(true);
        oldLive=field.getBoolean(null);field.setBoolean(null,true);
        OptimizationConfig.load(directory.resolve("flags.properties"));
        OptimizationConfig.replace(OptimizationSnapshot.noneRequested());
    }
    @AfterEach void after() throws Exception {
        OptimizationConfig.restoreState(saved);
        var field=OptimizationEditTransaction.class.getDeclaredField("liveUniformUpdates");field.setAccessible(true);field.setBoolean(null,oldLive);
        assertFalse(MakeUpQuadLifecycle.suspended());
    }
    static Object field(Object owner,String name) throws Exception {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    static void field(Object owner,String name,Object value) throws Exception {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);
    }
    final class Backend implements PackSelectionTransaction.Backend {
        final Map<String,String> queue=new LinkedHashMap<>();
        public Path directory(){return directory;}
        public String selected(){return null;}
        public Map<String,String> queue(){return queue;}
        public Map<String,String> effectiveOptions(){return Map.of();}
        public void select(String name){}
        public void save(){}
    }
    @SuppressWarnings("unchecked")
    PackSelectionTransaction nativeSelection(Backend backend,Path sidecar) throws Exception {
        var selection=new PackSelectionTransaction(backend);
        byte[] data=Files.readAllBytes(sidecar);var mode=Files.getPosixFilePermissions(sidecar);
        ((Map<Path,byte[]>)field(selection,"before")).put(sidecar,data);
        ((Map<Path,byte[]>)field(selection,"after")).put(sidecar,data);
        ((Map<Path,Object>)field(selection,"beforeModes")).put(sidecar,mode);
        ((Map<Path,Object>)field(selection,"afterModes")).put(sidecar,mode);
        field(selection,"queuedNativePath",sidecar);field(selection,"nativeBeforeMode",mode);field(selection,"nativePreparedMode",mode);
        return selection;
    }
    CoreUniformFrame.Core core(){return new CoreUniformFrame.Core(new LodSettings(true,32,96,8,false),LodPreset.PERFORMANCE);}

    @Test void failedFinalValidationRetainsNewNativeIntentWithoutOverwritingTamperedSidecar() throws Exception {
        var backend=new Backend();Path sidecar=directory.resolve("native.txt");
        Files.writeString(sidecar,"TAA=true\n");
        var selection=nativeSelection(backend,sidecar);
        var next=OptimizationSnapshot.noneRequested().with(OptimizationId.QUAD_COMMAND_COMPACTION,true);
        var session=CompileOptionControl.session(selection,core(),next,false,backend.queue,Map.of(),true);
        var reloads=new AtomicInteger();var observed=new AtomicInteger();
        var result=OptimizationEditTransaction.apply(next,reloads::incrementAndGet,()->{
            assertEquals(0,observed.getAndIncrement());
            // Exact public callback boundary: CPU source was validated, then independent user intent/file edit arrives.
            backend.queue.put("TAA","false");backend.queue.put("SHADOW_QUALITY","3");
            try{Files.writeString(sidecar,"TAA=false\n# independent sidecar edit\n");}catch(Exception e){throw new RuntimeException(e);}
            return false;
        },flags->session);
        assertFalse(result.success());assertFalse(result.recoveryComplete());
        assertEquals(0,reloads.get(),"sidecar tamper must refuse a recovery reload");
        assertEquals(OptimizationSnapshot.noneRequested(),OptimizationConfig.snapshot());
        assertEquals("TAA=false\n# independent sidecar edit\n",Files.readString(sidecar));
        assertEquals(Map.of("TAA","false","SHADOW_QUALITY","3"),backend.queue,
            "finish failure must release the queue deferred by later restoration");
    }

    @Test void ordinaryFailedApplyKeepsDeferredNewerIntentAndCapturedValues() throws Exception {
        var backend=new Backend();backend.queue.put("OLD_NATIVE","initial");
        Path sidecar=directory.resolve("native.txt");Files.writeString(sidecar,"TAA=true\n");
        var selection=nativeSelection(backend,sidecar);backend.queue.clear();
        var next=OptimizationSnapshot.noneRequested().with(OptimizationId.DEPTH_PRIMING,true);
        var session=CompileOptionControl.session(selection,core(),next,false,backend.queue,Map.of(),false);
        var loads=new AtomicInteger();
        var result=OptimizationEditTransaction.apply(next,()->{
            if(loads.getAndIncrement()==0){backend.queue.put("OLD_NATIVE","newer");backend.queue.put("TAA","false");throw new java.io.IOException("driver fixture");}
            assertTrue(backend.queue.isEmpty(),"pending edits must not enter the recovery load");
        },()->false,flags->session);
        assertFalse(result.success());assertTrue(result.recoveryComplete());assertEquals(2,loads.get());
        assertEquals(Map.of("OLD_NATIVE","newer","TAA","false"),backend.queue);
        assertEquals("TAA=true\n",Files.readString(sidecar));
    }

    @Test void successfulLiveApplyStillFinishesOnce() throws Exception {
        var backend=new Backend();Path sidecar=directory.resolve("native.txt");Files.writeString(sidecar,"TAA=true\n");
        var selection=nativeSelection(backend,sidecar);
        var next=OptimizationSnapshot.noneRequested().with(OptimizationId.QUAD_COMMAND_COMPACTION,true);
        var session=CompileOptionControl.session(selection,core(),next,false,backend.queue,Map.of(),true);
        var result=OptimizationEditTransaction.apply(next,()->fail("live path must not reload"),()->false,flags->session);
        assertTrue(result.success());assertEquals(next,OptimizationConfig.snapshot());assertTrue(backend.queue.isEmpty());
        assertEquals("TAA=true\n",Files.readString(sidecar));
    }
    @Test void failedFinishIsNeverReplayedAndCleanupDoesNotMaskPrimaryError() throws Exception {
        for(boolean error:List.of(false,true)) {
            OptimizationConfig.replace(OptimizationSnapshot.noneRequested());
            Throwable primary=error?new AssertionError("primary finish"):new IllegalStateException("primary finish");
            var secondary=new AssertionError("release also failed");
            var finishes=new AtomicInteger();var restores=new AtomicInteger();var releases=new AtomicInteger();
            var session=new CompileOptionControl.Session(){
                public boolean prepare(){return false;}
                public void reload(OptimizationEditTransaction.Reload action)throws Exception{action.run();}
                public void restore(){restores.incrementAndGet();}
                public void recoveryReload(OptimizationEditTransaction.Reload action)throws Exception{action.run();}
                public void finish(){finishes.incrementAndGet();if(primary instanceof Error e)throw e;throw (RuntimeException)primary;}
                public void releaseRecovery(){assertTrue(MakeUpQuadLifecycle.suspended());releases.incrementAndGet();throw secondary;}
            };
            var next=OptimizationSnapshot.noneRequested().with(OptimizationId.AO_BUDGET,true);
            if(error)assertSame(primary,assertThrows(AssertionError.class,()->OptimizationEditTransaction.apply(next,()->{},()->false,flags->session)));
            else {
                var result=OptimizationEditTransaction.apply(next,()->{},()->false,flags->session);
                assertFalse(result.success());assertFalse(result.recoveryComplete());
                assertTrue(result.message().contains("consumer recovery release failed"));
            }
            assertEquals(1,finishes.get());assertEquals(1,restores.get());assertEquals(1,releases.get());
            assertArrayEquals(new Throwable[]{secondary},primary.getSuppressed());
            assertEquals(OptimizationSnapshot.noneRequested(),OptimizationConfig.snapshot());
            assertFalse(MakeUpQuadLifecycle.suspended());
        }
    }

    @Test void prepareFailureStillFinishesOnceAndReleasesRecoveryOwnership() throws Exception {
        var events=new ArrayList<String>();
        var session=new CompileOptionControl.Session(){
            public boolean prepare()throws Exception{events.add("prepare");throw new java.io.IOException("prepare failure");}
            public void reload(OptimizationEditTransaction.Reload action)throws Exception{fail("forward reload");}
            public void restore(){events.add("restore");}
            public void recoveryReload(OptimizationEditTransaction.Reload action){events.add("recovery");}
            public void finish(){events.add("finish");}
            public void releaseRecovery(){events.add("release");}
        };
        var result=OptimizationEditTransaction.apply(OptimizationSnapshot.noneRequested().with(OptimizationId.AO_BUDGET,true),()->{},()->false,flags->session);
        assertFalse(result.success());assertTrue(result.recoveryComplete());
        assertEquals(List.of("prepare","restore","recovery","finish","release"),events);
        assertEquals(OptimizationSnapshot.noneRequested(),OptimizationConfig.snapshot());
    }

    @Test void failureBeforeNativeWriterOwnershipDoesNotConsumeFutureAaDuringRecovery() throws Exception {
        var backend=new Backend();
        // This is also the state before prepare has reached queuedNativePath publication.
        var selection=new PackSelectionTransaction(backend);
        var next=OptimizationSnapshot.noneRequested().with(OptimizationId.DEPTH_PRIMING,true);
        var session=CompileOptionControl.session(selection,core(),next,false,backend.queue,Map.of(),false);
        var loads=new AtomicInteger();var nativeValues=new LinkedHashMap<>(Map.of("TAA","true"));
        var result=OptimizationEditTransaction.apply(next,()->{
            if(loads.getAndIncrement()==0){backend.queue.put("TAA","false");throw new java.io.IOException("failed before native writer ownership");}
            nativeValues.putAll(backend.queue);backend.queue.clear(); // The pinned loader consumes its queue.
        },()->false,flags->session);
        assertFalse(result.success());assertTrue(result.recoveryComplete());assertEquals(2,loads.get());
        assertEquals(Map.of("TAA","true"),nativeValues,"recovery must load the previous AA, not queued future user intent");
        assertEquals(Map.of("TAA","false"),backend.queue);
        assertEquals(OptimizationSnapshot.noneRequested(),OptimizationConfig.snapshot());
    }

    @Test void recoveryReleaseIsIdempotentAfterIntentWasReturned() throws Exception {
        var backend=new Backend();backend.queue.put("TAA","false");
        var selection=new PackSelectionTransaction(backend);
        selection.restoreForRecovery();assertTrue(backend.queue.isEmpty());
        selection.finishRecovery();assertEquals(Map.of("TAA","false"),backend.queue);
        backend.queue.clear(); // A subsequent consumer has legitimately taken that intent.
        selection.finishRecovery();assertTrue(backend.queue.isEmpty(),"released ownership must not resurrect already consumed intent");
    }

}
