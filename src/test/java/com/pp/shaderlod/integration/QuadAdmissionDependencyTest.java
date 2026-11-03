package com.pp.shaderlod.integration;

import static org.junit.jupiter.api.Assertions.*;
import com.pp.shaderlod.integration.makeupquad.*;
import org.junit.jupiter.api.Test;

class QuadAdmissionDependencyTest {
    @Test void uncachedQuadRequestsAreRefused() {
        assertFalse(MakeUpQuadRuntime.compactionRequestsAllow(false,false));
        assertFalse(MakeUpQuadRuntime.compactionRequestsAllow(false,true));
        assertFalse(MakeUpQuadRuntime.compactionRequestsAllow(true,false));
        assertTrue(MakeUpQuadRuntime.compactionRequestsAllow(true,true));
    }

    @Test void refusedQuadStillAllowsStandaloneScalarRecording() {
        for(boolean quadRequested:new boolean[]{false,true}) {
            var state=new LegacyScalarState();
            state.begin(true,3,17,MakeUpQuadRuntime.compactionRequestsAllow(quadRequested,false));
            assertTrue(state.recording());
            state.emitted(6);state.emitted(18);state.emitted(12);state.finish(3);
            assertTrue(state.reusable(3,17));
            assertEquals(18,state.maximum());
        }
    }

    @Test void pairedRequestsStillRequireFreshCompactionPublication() {
        var state=new LegacyScalarState();
        state.begin(true,3,17,MakeUpQuadRuntime.compactionRequestsAllow(true,true));
        assertFalse(state.recording());
        state.finish(3);
        assertFalse(state.reusable(3,17));
        state.begin(true,3,17,MakeUpQuadRuntime.compactionRequestsAllow(true,true));
        assertTrue(state.compacted(3,2,18,17));
        state.finish(2);
        assertTrue(state.reusable(2,17));
        assertEquals(18,state.maximum());
    }
}
