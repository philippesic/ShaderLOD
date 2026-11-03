package com.pp.shaderlod.dev.makeupquad;
/** One finite observer of completed main compact calls; no command access or transform. */
public final class MainQuadAdmission {
    public static final String PROPERTY="shaderlod.dev.makeupQuadMainAdmissionR1";
    private static final boolean ENABLED=Boolean.getBoolean(PROPERTY);
    private static final int LIMIT=512;
    private static Thread owner;
    private static boolean pending;
    private static int pendingInput,calls;
    private static long input,output;
    private MainQuadAdmission() {}
    public static void head(int size) {
        if (!ENABLED || calls>=LIMIT) return;
        if (owner==null) owner=Thread.currentThread();
        if (owner!=Thread.currentThread()) return;
        // Pinned target is nonrecursive and completes on one render thread.
        pendingInput=size;pending=true;
    }
    public static void returned(int size) {
        if (!ENABLED || calls>=LIMIT || !pending || owner!=Thread.currentThread()) return;
        pending=false;input+=pendingInput;output+=size;calls++;
        if (calls==LIMIT) System.out.println("[ShaderLOD makeup quad main admission R1] calls="+calls+
            " inputCommands="+input+" outputCommands="+output);
    }
}
