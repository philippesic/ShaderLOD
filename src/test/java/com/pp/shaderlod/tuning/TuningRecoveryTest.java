package com.pp.shaderlod.tuning;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TuningRecoveryTest {
    private static final class OwnedScene {
        boolean retained=true,closeFails=true;
        int graphRestores,videoRestores,closeAttempts;
        void restore() {
            graphRestores++;videoRestores++;closeAttempts++;
            if(closeFails)throw new IllegalStateException("scene close not acknowledged");
            retained=false;
        }
    }
    @Test void failedSceneCloseParksWithoutReleasingOwnershipAndExplicitUndoRetriesFullRecovery() {
        var scene=new OwnedScene();var recovery=new TuningRecovery();
        assertEquals(TuningRecovery.Result.RETRY,recovery.attempt(scene::restore));
        assertEquals(TuningRecovery.Result.RETRY,recovery.attempt(scene::restore));
        assertEquals(TuningRecovery.Result.PARKED,recovery.attempt(scene::restore));
        assertTrue(scene.retained);assertTrue(recovery.parked());assertEquals(3,recovery.attempts());
        assertEquals("scene close not acknowledged",recovery.failure().getMessage());
        for(int tick=0;tick<20;tick++)assertEquals(TuningRecovery.Result.PARKED,recovery.attempt(scene::restore));
        assertEquals(3,scene.closeAttempts);assertEquals(3,scene.graphRestores);assertEquals(3,scene.videoRestores);
        scene.closeFails=false;recovery.explicitRetry();
        assertEquals(TuningRecovery.Result.COMPLETE,recovery.attempt(scene::restore));
        assertFalse(scene.retained);assertFalse(recovery.parked());assertNull(recovery.failure());
        assertEquals(4,scene.graphRestores);assertEquals(4,scene.videoRestores);assertEquals(4,scene.closeAttempts);
    }
}
