package com.pp.shaderlod.dev;
/** Standalone JDK-only unit checks. Synthetic predicate facts are never evidence of a live admitted frame. */
public final class PortRendererRateChecks {
    private static void check(boolean value,String name) { if(!value)throw new AssertionError(name); }
    public static void main(String[] args) {
        check(PortRendererFrames.rateAdmitted("MINIMIZED",260,260,"NONE",true,false),"valid pure predicate");
        check(!PortRendererFrames.rateAdmitted("AFK",260,260,"NONE",true,false),"AFK policy even before inactivity threshold");
        check(!PortRendererFrames.rateAdmitted("MINIMIZED",260,30,"SHORT_AFK",true,false),"actual short-AFK cap/reason");
        check(!PortRendererFrames.rateAdmitted("MINIMIZED",260,260,"SHORT_AFK",true,false),"reason alone rejects");
        check(!PortRendererFrames.rateAdmitted("MINIMIZED",260,30,"NONE",true,false),"effective cap alone rejects");
        check(!PortRendererFrames.rateAdmitted("MINIMIZED",260,260,"NONE",false,false),"native focus missing");
        check(!PortRendererFrames.rateAdmitted("MINIMIZED",260,260,"NONE",true,true),"iconified");
        check(!PortRendererFrames.rateAdmitted(null,260,260,null,true,false),"unknown facts");
        check(!PortRendererFrames.rateAdmitted("MINIMIZED",0,0,"NONE",true,false),"invalid configured cap");
        check(!PortRendererFrames.windowAccepted(0,0,0),"no world completions");
        check(!PortRendererFrames.windowAccepted(3,0,1),"transient rejection cannot be hidden by good final frame");
        check(PortRendererFrames.windowAccepted(3,1,1),"prior-window failure does not fabricate a new rejected frame");
        String previous=System.getProperty(PortRendererFrames.PROPERTY);
        try {
            System.setProperty(PortRendererFrames.PROPERTY,"true");
            long[] before=PortRendererFrames.snapshot();PortRendererFrames.beginMeasuredWindow();
            PortRendererFrames.completed("MINIMIZED",260,260,"NONE",true,false);
            PortRendererFrames.completed("MINIMIZED",260,30,"SHORT_AFK",true,false);
            PortRendererFrames.completed("MINIMIZED",260,260,"NONE",true,false);
            long[] after=PortRendererFrames.snapshot();
            check(after[0]-before[0]==3 && after[7]-before[7]==1,"actual counter branch records all completions and one rejected frame");
            check(!PortRendererFrames.windowAccepted(after[0]-before[0],before[7],after[7]),"counter rejection latched across good final completion");
            check(PortRendererFrames.firstRejectedRate()!=null && PortRendererFrames.firstRejectedRate().effectiveLimit()==30,"first rejected facts retained");
            PortRendererFrames.stopMeasuredWindow();
            long rejected=PortRendererFrames.snapshot()[7];
            PortRendererFrames.completed("AFK",260,30,"SHORT_AFK",true,false);
            check(PortRendererFrames.snapshot()[7]==rejected,"ordinary/unmeasured completion does not poison measured-window counter");
        } finally {
            PortRendererFrames.stopMeasuredWindow();
            if(previous==null)System.clearProperty(PortRendererFrames.PROPERTY);else System.setProperty(PortRendererFrames.PROPERTY,previous);
        }
        System.out.println("PASS: pure admission and counter rejection negatives; no live Minecraft/GL evidence");
    }
}
