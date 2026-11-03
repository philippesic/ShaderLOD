package com.pp.shaderlod.optimization;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real Apply/persistence/recovery with a bounded fake selection-session boundary. No GL/client. */
class GeneratedCloudControlTest {
    @TempDir Path directory;
    OptimizationConfig.State saved;
    boolean oldLive;
    final AtomicInteger holds=new AtomicInteger(),checks=new AtomicInteger(),reloads=new AtomicInteger();
    @BeforeEach void setup()throws Exception {
        saved=OptimizationConfig.state();
        var live=OptimizationEditTransaction.class.getDeclaredField("liveUniformUpdates");live.setAccessible(true);
        oldLive=live.getBoolean(null);live.setBoolean(null,true);
        OptimizationConfig.load(directory.resolve("flags.properties"));OptimizationConfig.replace(OptimizationSnapshot.noneRequested());
    }
    @AfterEach void cleanup()throws Exception {
        OptimizationConfig.restoreState(saved);
        var live=OptimizationEditTransaction.class.getDeclaredField("liveUniformUpdates");live.setAccessible(true);live.setBoolean(null,oldLive);
        assertFalse(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
    }
    static final class Selection implements CompileOptionControl.Session {
        boolean needsReload;int prepared,finished,restored,recovered,released;
        public boolean prepare(){prepared++;return needsReload;}
        public void reload(OptimizationEditTransaction.Reload reload)throws Exception {reload.run();}
        public void restore(){restored++;}
        public void recoveryReload(OptimizationEditTransaction.Reload reload)throws Exception {recovered++;reload.run();}
        public void finish(){finished++;}
        public void releaseRecovery(){released++;}
    }
    @Test void alternatingRealRequestsPreserveSessionAndNeverReload() {
        var next=OptimizationSnapshot.noneRequested();
        for(boolean enabled:new boolean[]{false,true,false,true,false}){
            next=next.with(OptimizationId.CLOUD_PRIMARY_BUDGET,enabled);var selection=new Selection();
            var session=GeneratedControl.sameProgramSession(selection,checks::incrementAndGet,holds::incrementAndGet);
            var result=OptimizationEditTransaction.apply(next,reloads::incrementAndGet,()->false,flags->session);
            assertTrue(result.success(),result.message());assertEquals(next,OptimizationConfig.snapshot());
            assertEquals(1,selection.prepared);assertEquals(1,selection.finished);assertEquals(0,selection.restored);
        }
        assertEquals(0,reloads.get());assertEquals(5,holds.get());assertEquals(10,checks.get());
    }
    @Test void requiredReloadFailsAndUsesOrdinaryRecovery() {
        var selection=new Selection();selection.needsReload=true;
        var session=GeneratedControl.sameProgramSession(selection,checks::incrementAndGet,holds::incrementAndGet);
        var result=OptimizationEditTransaction.apply(OptimizationSnapshot.noneRequested().with(OptimizationId.CLOUD_PRIMARY_BUDGET,true),
            reloads::incrementAndGet,()->false,flags->session);
        assertFalse(result.success());assertTrue(result.recoveryComplete());
        assertEquals(OptimizationSnapshot.noneRequested(),OptimizationConfig.snapshot());
        assertEquals(1,selection.restored);assertEquals(1,selection.recovered);assertEquals(1,selection.released);
        assertEquals(1,reloads.get());assertEquals(0,holds.get());assertEquals(0,checks.get());
    }
    @Test void identityFailureAtFinishRestoresRequestsWithoutPublishingHold() {
        var selection=new Selection();
        var session=GeneratedControl.sameProgramSession(selection,()->{if(checks.incrementAndGet()==2)throw new IllegalStateException("changed");},holds::incrementAndGet);
        var result=OptimizationEditTransaction.apply(OptimizationSnapshot.noneRequested().with(OptimizationId.CLOUD_PRIMARY_BUDGET,true),
            reloads::incrementAndGet,()->false,flags->session);
        assertFalse(result.success());assertEquals(OptimizationSnapshot.noneRequested(),OptimizationConfig.snapshot());
        assertEquals(0,holds.get());assertEquals(1,selection.restored);assertEquals(1,selection.recovered);
        assertEquals(0,selection.finished,"Failed finish must not be replayed");assertEquals(1,selection.released);
    }
    @Test void forbiddenForwardReloadNeverInvokesNativeCallback() {
        var session=GeneratedControl.sameProgramSession(new Selection(),()->{},holds::incrementAndGet);
        assertThrows(IllegalStateException.class,()->session.reload(reloads::incrementAndGet));assertEquals(0,reloads.get());
    }
    @Test void everyActivePeerRejectsCloudOnlyScope() {
        var off=OptimizationSnapshot.noneRequested();assertTrue(GeneratedControl.cloudOnly(off));
        var cloud=off.with(OptimizationId.CLOUD_PRIMARY_BUDGET,true);assertTrue(GeneratedControl.cloudOnly(cloud));
        for(var id:OptimizationId.values())if(id!=OptimizationId.CLOUD_PRIMARY_BUDGET){
            assertFalse(GeneratedControl.cloudOnly(off.with(id,true)),id.key());
            assertFalse(GeneratedControl.cloudOnly(cloud.with(id,true)),id.key());
        }
    }
}
