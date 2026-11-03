package com.pp.shaderlod.tuning;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TuningWindowTest {
    @Test void explicitResetExcludesOneUnavailableInitialIntervalButPreservesInteriorHoles() {
        var admission = new TuningWindow.InitialInterval();
        var missing = new com.pp.shaderlod.measurement.FrameMeasurement.Sample(1, 7, null, 1, null, null, null, "off");
        assertTrue(admission.exclude(missing));
        assertFalse(admission.exclude(missing));
        admission.reset();
        var valid = new com.pp.shaderlod.measurement.FrameMeasurement.Sample(2, 7, 10L, 1, null, null, null, "off");
        assertFalse(admission.exclude(valid));
        assertFalse(admission.exclude(missing));
    }

    @Test void r42FocusPauseDoesNotConsumePendingMeshReadinessAllowance(){
        // Illustrative 9-second prior wait, not an inferred R42 mesh-start timestamp.
        long second=1_000_000_000L,meshAt=80*second;
        var pause=new TuningWindow.InputPause();
        pause.pause(89*second); // R42 remained focus-paused at elapsed89s and143s.
        pause.pause(143*second); // Repeated observations must retain the original pause.
        long resumedAt=144*second;
        long shiftedMeshAt=meshAt+pause.resume(resumedAt);
        assertEquals(9*second,resumedAt-shiftedMeshAt);
        assertTrue(resumedAt-shiftedMeshAt<=12*second);
        assertTrue(148*second-shiftedMeshAt>12*second); // Active timeout remains strict.
        assertEquals(0,pause.resume(145*second));
    }
    @Test void excludingInputPauseFromReadinessDoesNotExtendWholeRunWallBudget(){
        long second=1_000_000_000L,runAt=0,meshAt=80*second;
        var pause=new TuningWindow.InputPause();pause.pause(89*second);
        long resumedAt=344*second;
        meshAt+=pause.resume(resumedAt);
        assertEquals(9*second,resumedAt-meshAt);
        assertTrue(resumedAt-runAt>300*second); // Run must still stop instead of sampling.
    }
    @Test void overlappingFocusAndChatPausesAreExcludedOnceAndPreservePriorPoseWait(){
        long second=1_000_000_000L,poseAt=10*second;
        var pause=new TuningWindow.InputPause();
        pause.pause(16*second);pause.pause(20*second); // Chat/focus overlap.
        poseAt+=pause.resume(30*second);
        assertEquals(6*second,30*second-poseAt);
        assertTrue(33*second-poseAt>8*second);
        pause.pause(34*second);assertEquals(2*second,pause.resume(36*second));
    }
    @Test void actualR42WarmZeroFocusEvidenceExceedsMeshCutoffWhileInputIsPaused()throws Exception {
        var base=java.nio.file.Path.of("run-workloads/performance-readiness-20261016/cloud-r42/unbound");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(base.resolve("focus-operator-events.json")),"Retained actual R42 evidence required");
        var early=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(base.resolve("receipts/status_ddc265f1e997.json"))).getAsJsonObject();
        var later=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(base.resolve("receipts/status_dbd15c57a303.json"))).getAsJsonObject();
        for(var receipt:java.util.List.of(early,later)){
            assertFalse(receipt.get("focused").getAsBoolean());assertTrue(receipt.get("tuner_active").getAsBoolean());
            assertTrue(receipt.get("tuner_status").getAsString().contains("repeatability-reload pose=0 warm=0 samples=0"));
            assertTrue(receipt.get("tuner_status").getAsString().contains("paused until game regains focus"));
        }
        var events=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(base.resolve("focus-operator-events.json"))).getAsJsonArray();
        assertFalse(events.get(0).getAsJsonObject().get("focused").getAsBoolean());
        assertTrue(events.get(1).getAsJsonObject().get("focused").getAsBoolean());
        assertTrue(events.get(1).getAsJsonObject().get("status").getAsString().contains("Reload mesh readiness exceeded 12s wall cutoff; original state restored"));
        long observedPause=java.time.Duration.between(java.time.Instant.parse(early.get("at").getAsString()),java.time.Instant.parse(later.get("at").getAsString())).toNanos();
        assertTrue(observedPause>12_000_000_000L);
        var pause=new TuningWindow.InputPause();pause.pause(0);pause.pause(observedPause);
        assertEquals(observedPause,pause.resume(observedPause));
    }
    @Test void fastAndSlowRenderersMustMeetBothTimeAndFrameRequirements(){
        for(var p:new TuningWindow[]{TuningWindow.SCREEN,TuningWindow.CONFIRM,TuningWindow.FINAL}){
            assertTrue(p.warming(p.warmupNs()-1,p.warmupFrames()*10));
            assertTrue(p.warming(p.warmupNs()*2,p.warmupFrames()-1));
            assertFalse(p.warming(p.warmupNs(),p.warmupFrames()));
            assertFalse(p.complete(p.sampleNs()-1,p.sampleFrames()*10));
            assertFalse(p.complete(p.sampleNs()*2,p.sampleFrames()-1));
            assertTrue(p.complete(p.sampleNs(),p.sampleFrames()));
        }
    }
    @Test void freshFinalWindowsAreLongerThanSearchWithoutClaimingJvmIndependence(){
        assertTrue(TuningWindow.FINAL.sampleNs()>TuningWindow.CONFIRM.sampleNs());
        assertTrue(TuningWindow.CONFIRM.sampleNs()>TuningWindow.SCREEN.sampleNs());
        assertTrue(TuningWindow.FINAL.sampleFrames()>TuningWindow.CONFIRM.sampleFrames());
    }
}
