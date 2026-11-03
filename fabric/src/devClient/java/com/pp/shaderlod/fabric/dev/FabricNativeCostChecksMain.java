package com.pp.shaderlod.fabric.dev;

/** Parent-scheduled pure qualification negatives; no rendering or pacing claim. */
public final class FabricNativeCostChecksMain {
    private static void check(boolean pass) { if (!pass) throw new AssertionError("Native cost contract"); }
    private static void good(FabricNativeCostWindow w) { w.observe(260,260,260,true,true,false,true,true,false,true,true); }
    public static void main(String[] args) {
        check(FabricNativeCostWindow.flagsAllow("bsl",true,false,false,false,false,true));
        for (String family : new String[]{"", "unknown", "makeup"})
            check(!FabricNativeCostWindow.flagsAllow(family,true,false,false,false,false,true));
        for (int f=0;f<6;f++) check(!FabricNativeCostWindow.flagsAllow("bsl",f!=0,f==1,f==2,f==3,f==4,f!=5));
        var w = new FabricNativeCostWindow(); good(w); check(w.frames()==0);
        w.begin(); good(w); check(w.frames()==1 && w.invalidFrames()==0 && w.minEffective()==260 && w.maxEffective()==260);
        for (int f=0;f<11;f++) {
            var bad = new FabricNativeCostWindow(); bad.begin(); good(bad);
            bad.observe(f==0?60:260,f==1?30:260,f==2?-1:260,f!=3,f!=4,f==5,f!=6,f!=7,f==8,f!=9,f!=10);
            check(bad.frames()==2 && bad.invalidFrames()==1 && !bad.firstInvalid().isEmpty());
            bad.end(); good(bad); check(bad.frames()==2); bad.begin(); good(bad);
            check(bad.frames()==1 && bad.invalidFrames()==1); // Reset cannot erase a bad frame.
        }
        for (String phase : new String[]{"original", "on", "original-return"}) {
            check(FabricNativeCostWindow.singleFlagsAllow(phase,"bsl",true,false,false,false,false,true,false));
            check(FabricNativeCostWindow.phaseEnabled(phase) == phase.equals("on"));
        }
        for (String phase : new String[]{"", "off", "native-off-2", "unknown"})
            check(!FabricNativeCostWindow.singleFlagsAllow(phase,"bsl",true,false,false,false,false,true,false));
        check(!FabricNativeCostWindow.singleFlagsAllow("on","bsl",true,false,false,false,false,true,true));
        check(!FabricNativeCostWindow.singleFlagsAllow("on","unknown",true,false,false,false,false,true,false));
        check(!FabricNativeCostWindow.singleFlagsAllow("on","bsl",true,true,false,false,false,true,false));
        var on = new FabricNativeCostWindow(); on.begin(true);
        on.observe(260,260,260,true,true,false,true,true,false,false,false);
        check(on.frames()==1 && on.invalidFrames()==0);
        on.observe(260,260,260,true,true,false,true,true,false,true,false);
        on.observe(260,260,260,true,true,false,true,true,false,false,true);
        on.observe(260,260,30,true,true,false,true,true,false,false,false);
        check(on.frames()==4 && on.invalidFrames()==3);
        on.end(); on.begin(true); on.observe(260,260,260,true,true,false,true,true,false,false,false);
        check(on.frames()==1 && on.invalidFrames()==3);
        System.out.println("Native cost mode/rate/focus/viewport/native negatives passed; no driver claim");
    }
}
