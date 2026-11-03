package com.pp.shaderlod.experiment.makeupquad;
/** One bounded intrinsic aggregate, never a command census or profiler. Disabled by default. */
public final class QuadCoalescerAdmission {
    private static final int LIMIT=512;
    private static int calls;
    private static long input, output;
    private QuadCoalescerAdmission() {}
    public static void record(int before,int after) {
        if(!Boolean.getBoolean("shaderlod.dev.makeupQuadCoalescerAdmission") || calls>=LIMIT) return;
        input+=before; output+=after; calls++;
        if(calls==LIMIT) System.out.println("[ShaderLOD makeup quad coalescer R1] calls="+calls+" inputCommands="+input+" outputCommands="+output);
    }
}
