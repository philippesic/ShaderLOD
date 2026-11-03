package com.pp.shaderlod.port.dev;
/** Standalone dev counter admission checks; parent runs after normal dev compilation. */
public final class PortSpeedCounterChecks {
    private static void check(boolean value) { if (!value) throw new AssertionError("Counter admission mismatch"); }
    public static void main(String[] args) {
        check(PortRendererFrames.stageCommandsAllow(true, 2, 10, 4, 2));
        check(PortRendererFrames.stageCommandsAllow(false, 0, 0, 0, 0));
        check(!PortRendererFrames.stageCommandsAllow(true, 0, 0, 0, 0));
        check(!PortRendererFrames.stageCommandsAllow(true, 2, 10, 10, 0));
        check(!PortRendererFrames.stageCommandsAllow(false, 1, 10, 4, 1));
        check(!PortRendererFrames.stageCommandsAllow(true, 1, 4, 10, 1));
        check(!PortRendererFrames.stageCommandsAllow(true, -1, 10, 4, 1));
        check(!PortRendererFrames.stageCommandsAllow(true, 1, 10, 4, 2));
        check(PortRendererFrames.limiterAllows(260, 260, "NONE", true, true, false));
        check(!PortRendererFrames.limiterAllows(260, 30, "SHORT_AFK", true, true, false));
        check(!PortRendererFrames.limiterAllows(260, 10, "LONG_AFK", true, true, false));
        check(!PortRendererFrames.limiterAllows(260, 260, "NONE", false, true, false));
        check(!PortRendererFrames.limiterAllows(260, 260, "NONE", true, false, false));
        check(!PortRendererFrames.limiterAllows(260, 10, "WINDOW_ICONIFIED", true, true, true));
        check(!PortRendererFrames.limiterAllows(260, 60, "OUT_OF_LEVEL_MENU", true, true, false));
        check(!PortRendererFrames.limiterAllows(260, 260, null, true, true, false));
        check(!PortRendererFrames.limiterAllows(260, 30, "NONE", true, true, false));
        check(!PortRendererFrames.limiterAllows(0, 0, "NONE", true, true, false));
        // Never selectively discard invalid frames: measured counters include them and latch failure.
        System.setProperty("shaderlod.dev.portRendererSpeedProbe", "true");
        PortRendererFrames.beginWindow();
        long before = PortRendererFrames.frames();
        PortRendererFrames.completed(260, 30, "SHORT_AFK", true, true, false);
        check(PortRendererFrames.frames() == before + 1);
        check(PortRendererFrames.invalidMeasuredFrames() == 1);
        PortRendererFrames.unavailableFrame("missing tracker");
        check(PortRendererFrames.frames() == before + 2);
        check(PortRendererFrames.invalidMeasuredFrames() == 2);
        PortRendererFrames.endWindow();
        PortRendererFrames.completed(260, 30, "SHORT_AFK", true, true, false);
        check(PortRendererFrames.invalidMeasuredFrames() == 2);
        System.out.println("23 dev counter/limiter admission checks passed; live frame/mixin/speed unverified");
    }
}
