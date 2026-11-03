package com.pp.shaderlod.fabric.dev;
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
            long[] before=PortRendererFrames.snapshot();PortRendererFrames.beginMeasuredWindow(260);
            PortRendererFrames.completed("MINIMIZED",260,260,"NONE",true,false);
            PortRendererFrames.completed("MINIMIZED",260,30,"SHORT_AFK",true,false);
            PortRendererFrames.completed("MINIMIZED",260,260,"NONE",true,false);
            long[] after=PortRendererFrames.snapshot();
            check(after[0]-before[0]==3 && after[7]-before[7]==1,"actual counter branch records all completions and one rejected frame");
            check(!PortRendererFrames.windowAccepted(after[0]-before[0],before[7],after[7]),"counter rejection latched across good final completion");
            check(PortRendererFrames.firstRejectedRate()!=null && PortRendererFrames.firstRejectedRate().effectiveLimit()==30,"first rejected facts retained");
            PortRendererFrames.stopMeasuredWindow();
            PortRendererFrames.beginMeasuredWindow(260);before=PortRendererFrames.snapshot();
            PortRendererFrames.completed("MINIMIZED",260,260,"NONE",true,false);
            PortRendererFrames.completed("MINIMIZED",120,120,"NONE",true,false);
            PortRendererFrames.completed("MINIMIZED",260,260,"NONE",true,false);
            after=PortRendererFrames.snapshot();
            check(after[0]-before[0]==3 && after[7]-before[7]==1,"equal configured/effective transient drift rejected without cherry picking");
            check(PortRendererFrames.minConfigured()==120 && PortRendererFrames.maxConfigured()==260
                && PortRendererFrames.minEffective()==120 && PortRendererFrames.maxEffective()==260,"real callback bounds retain cap excursion");
            check(PortRendererFrames.rateObservedFrames()==3,"all observed completions retained");
            PortRendererFrames.unavailableCompleted("fixture");after=PortRendererFrames.snapshot();
            check(after[0]-before[0]==4 && after[7]-before[7]==2
                && PortRendererFrames.rateObservedFrames()==3 && PortRendererFrames.rateUnavailableFrames()==1,"unavailable observation retains frame and rejects whole window");

            PortRendererFrames.stopMeasuredWindow();
            PortRendererFrames.beginMeasuredWindow(260);before=PortRendererFrames.snapshot();
            PortRendererFrames.unavailableCompleted("first observation missing");
            PortRendererFrames.completed("MINIMIZED",260,260,"NONE",true,false);after=PortRendererFrames.snapshot();
            check(after[0]-before[0]==2 && after[7]-before[7]==1
                && PortRendererFrames.rateObservedFrames()==1 && PortRendererFrames.rateUnavailableFrames()==1,
                "unavailable first frame remains in whole denominator");
            check(PortRendererFrames.expectedLimit()==260 && PortRendererFrames.firstRejectedRate().configuredLimit()==-1,
                "expected cap and unavailable failure retained across good completion");
            PortRendererFrames.stopMeasuredWindow();
            PortRendererFrames.beginMeasuredWindow(260);before=PortRendererFrames.snapshot();
            PortRendererFrames.completed("MINIMIZED",260,260,"NONE",true,false);after=PortRendererFrames.snapshot();
            check(PortRendererFrames.rateUnavailableFrames()==0 && PortRendererFrames.rateObservedFrames()==1
                && PortRendererFrames.firstRejectedRate()==null && PortRendererFrames.windowAccepted(1,before[7],after[7]),
                "fresh window resets statistics without erasing historical rejected count");
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
