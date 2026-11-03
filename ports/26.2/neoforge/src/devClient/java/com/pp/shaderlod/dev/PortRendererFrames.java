package com.pp.shaderlod.dev;
/** Dev-only counters for exact world completion, helper branch and actual GL function entry. */
public final class PortRendererFrames {
    public static final String PROPERTY="shaderlod.dev.portRendererSpeedProbe";
    private static long completed,helperCalls,inputCommands,outputCommands,reducedCalls,glCalls,copiedGlCalls;
    private static boolean pendingCopy,activeCopy,measuring;
    private static long rejectedMeasuredFrames;
    private static RateFacts firstRejectedRate;
    private static int expectedLimit,minConfigured,maxConfigured,minEffective,maxEffective;
    private static long rateObservedFrames,rateUnavailableFrames;
    private PortRendererFrames() {}
    public static boolean requested() { return Boolean.getBoolean(PROPERTY); }
    public record RateFacts(String inactivityPolicy,int configuredLimit,int effectiveLimit,String throttleReason,boolean focused,boolean iconified) {}
    /** Pure contract, independently testable without Minecraft, GL or a client process. */
    public static boolean rateAdmitted(String policy,int configured,int effective,String reason,boolean focused,boolean iconified) {
        return "MINIMIZED".equals(policy) && configured>0 && effective==configured && "NONE".equals(reason) && focused && !iconified;
    }
    /** A single rejected measured frame poisons the entire window even if the final frame is good. */
    public static boolean windowAccepted(long frames,long rejectedBefore,long rejectedAfter) {
        return frames>0 && rejectedBefore>=0 && rejectedAfter==rejectedBefore;
    }
    public static void beginMeasuredWindow(int configured) {
        if(configured<=0)throw new IllegalArgumentException("Missing expected configured cap");
        expectedLimit=configured;measuring=true;firstRejectedRate=null;rateObservedFrames=rateUnavailableFrames=0;
        minConfigured=minEffective=Integer.MAX_VALUE;maxConfigured=maxEffective=0;
    }
    public static int expectedLimit() { return expectedLimit; }
    public static long rateUnavailableFrames() { return rateUnavailableFrames; }
    public static int minConfigured() { return minConfigured; }
    public static int maxConfigured() { return maxConfigured; }
    public static int minEffective() { return minEffective; }
    public static int maxEffective() { return maxEffective; }
    public static long rateObservedFrames() { return rateObservedFrames; }
    /** Keep an unavailable observation in the completed-render denominator and poison the window. */
    public static void unavailableCompleted(String reason) {
        if(!requested())return;
        completed++;
        if(measuring) {
            rateUnavailableFrames++;
            rejectedMeasuredFrames++;
            if(firstRejectedRate==null)firstRejectedRate=new RateFacts("UNAVAILABLE",-1,-1,"UNAVAILABLE: "+reason,false,false);
        }
    }
    public static void stopMeasuredWindow() { measuring=false; }
    public static RateFacts firstRejectedRate() { return firstRejectedRate; }
    /** Invoked only by the actual completed-world hook with actual tracker/window facts. */
    public static void completed(String policy,int configured,int effective,String reason,boolean focused,boolean iconified) {
        if(!requested())return;
        completed++;
        if(measuring) {
            rateObservedFrames++;
            minConfigured=Math.min(minConfigured,configured);maxConfigured=Math.max(maxConfigured,configured);
            minEffective=Math.min(minEffective,effective);maxEffective=Math.max(maxEffective,effective);
        }
        if(measuring && (configured!=expectedLimit || !rateAdmitted(policy,configured,effective,reason,focused,iconified))) {
            rejectedMeasuredFrames++;
            if(firstRejectedRate==null)firstRejectedRate=new RateFacts(policy,configured,effective,reason,focused,iconified);
        }
    }
    public static void helper(int input,int output) {
        if(!requested()) return;
        helperCalls++; inputCommands+=input;
        outputCommands+=output>=0 ? output : input;
        pendingCopy=output>=0 && output<input;
        if(pendingCopy) reducedCalls++;
    }
    public static void beginGlSubmission() { if(requested()) { activeCopy=pendingCopy;pendingCopy=false; } }
    public static void endGlSubmission() { activeCopy=false; }
    public static void glFunctionEntry() { if(requested()) { glCalls++;if(activeCopy)copiedGlCalls++; } }
    public static long[] snapshot() { return new long[]{completed,helperCalls,inputCommands,outputCommands,reducedCalls,glCalls,copiedGlCalls,rejectedMeasuredFrames}; }
}
