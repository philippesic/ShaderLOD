package com.pp.shaderlod.optimization;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Queue ownership unit boundary only; authentic Sildur source/parser integration lives in CompileBootstrapChecksMain. */
class CompileOwnedActivationTest {
    private static final String KEY=CompileOptionControl.SILDUR_PIXEL_GRAPH;
    private static Map<String,CompileOptionControl.NativeOption> loaded() {
        return Map.of(KEY,new CompileOptionControl.NativeOption("STRING",Set.of("0","1"),"0","0"));
    }
    @Test void unownedReloadRetainsPixelQueueRefusalButOwnedActivationDoesNotReplan()throws Exception {
        var queue=new LinkedHashMap<String,String>();queue.put(KEY,"1");
        // Same queue-empty source refusal which currently overwrites an owned activation.
        CompileOptionControl.synchronizeBeforeReload(queue,()->Map.of(KEY,queue.isEmpty()?"1":"0"),CompileOwnedActivationTest::loaded);
        assertEquals("0",queue.get(KEY));queue.clear();
        CompileOptionControl.activateOwned(queue,Map.of(KEY,"1"),new LinkedHashMap<>(),()->{
            CompileOptionControl.synchronizeBeforeReload(queue,()->{fail("Owned desired1 must not be re-admitted against its own queue");return Map.of();},CompileOwnedActivationTest::loaded);
            assertEquals("1",queue.remove(KEY)); // Actual Iris loader consumes the owned entry.
        });
        CompileOptionControl.synchronizeBeforeReload(queue,()->Map.of(KEY,"0"),CompileOwnedActivationTest::loaded);
        assertTrue(queue.isEmpty(),"scope gone; unchanged normal0 does not queue a reload");
    }
    @Test void changedExtraAndDifferentQueueRefuse()throws Exception {
        for(int kind=0;kind<3;kind++) {
            final int mutation=kind;var queue=new LinkedHashMap<String,String>();
            try {
                assertThrows(IllegalStateException.class,()->CompileOptionControl.activateOwned(queue,Map.of(KEY,"1"),new LinkedHashMap<>(),()->{
                    Map<String,String> observed=queue;
                    if(mutation==0)queue.put(KEY,"0");
                    if(mutation==1)queue.put("FOREIGN","1");
                    if(mutation==2)observed=new LinkedHashMap<>(queue);
                    CompileOptionControl.synchronizeBeforeReload(observed,Map::of,CompileOwnedActivationTest::loaded);
                }));
            }finally{queue.clear();}
        }
    }
    @Test void foreignOwnerAndNestedActivationRefuse()throws Exception {
        var queue=new LinkedHashMap<String,String>();
        try {
            CompileOptionControl.activateOwned(queue,Map.of(KEY,"1"),new LinkedHashMap<>(),()->{
                assertThrows(IllegalStateException.class,()->CompileOptionControl.activateOwned(new LinkedHashMap<>(),Map.of(KEY,"1"),new LinkedHashMap<>(),()->fail("nested reload")));
                var failure=new AtomicReference<Throwable>();
                var thread=new Thread(()->{try{CompileOptionControl.synchronizeBeforeReload(queue,Map::of,CompileOwnedActivationTest::loaded);}catch(Throwable t){failure.set(t);}},"compile-scope-unit-wrong-owner");
                thread.start();thread.join(2000);assertFalse(thread.isAlive());assertInstanceOf(IllegalStateException.class,failure.get());
                CompileOptionControl.synchronizeBeforeReload(queue,Map::of,CompileOwnedActivationTest::loaded);
            });
        }finally{queue.clear();}
    }
    @Test void thrownReloadReleasesScopeAndOuterCleanupPreservesForeignIntent()throws Exception {
        var queue=new LinkedHashMap<String,String>();var changes=Map.of(KEY,"1");var staged=new LinkedHashMap<String,String>();
        var failure=assertThrows(IOException.class,()->CompileOptionControl.activateOwned(queue,changes,staged,()->{queue.put("FOREIGN","future");throw new IOException("driver");}));
        CompileOptionControl.cleanupQueue(queue,staged,Map.of(),failure);
        assertEquals(Map.of("FOREIGN","future"),queue);
        queue.clear();
        CompileOptionControl.activateOwned(queue,changes,staged,()->CompileOptionControl.synchronizeBeforeReload(queue,Map::of,CompileOwnedActivationTest::loaded));
        CompileOptionControl.cleanupQueue(queue,staged,Map.of(),null);assertTrue(queue.isEmpty());
    }
    @Test void foreignQueueCannotBeClaimed() {
        var queue=new LinkedHashMap<String,String>();queue.put("FOREIGN","future");
        assertThrows(IllegalStateException.class,()->CompileOptionControl.activateOwned(queue,Map.of(KEY,"1"),new LinkedHashMap<>(),()->fail("must not reload")));
        assertEquals(Map.of("FOREIGN","future"),queue);
    }
    @Test void generationChangeAfterHeadRefusesAndPreservesPrimaryReloadError()throws Exception {
        var state=OptimizationConfig.state();
        try {
            for(boolean throwDriver:List.of(false,true)) {
                var queue=new LinkedHashMap<String,String>();var staged=new LinkedHashMap<String,String>();
                var driver=new IOException("original driver failure");
                var thrown=assertThrows(Exception.class,()->CompileOptionControl.activateOwned(queue,Map.of(KEY,"1"),staged,()->{
                    CompileOptionControl.synchronizeBeforeReload(queue,Map::of,CompileOwnedActivationTest::loaded);
                    queue.clear(); // normal consumption after HEAD
                    OptimizationConfig.restoreInMemory(OptimizationConfig.snapshot()); // actual generation mutation
                    if(throwDriver)throw driver;
                }));
                if(throwDriver)assertSame(driver,thrown);else assertInstanceOf(IllegalStateException.class,thrown);
                CompileOptionControl.cleanupQueue(queue,staged,Map.of(),thrown);
                assertTrue(queue.isEmpty());
            }
        }finally{OptimizationConfig.restoreState(state);}
    }

}
