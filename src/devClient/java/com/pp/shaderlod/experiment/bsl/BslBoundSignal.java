package com.pp.shaderlod.experiment.bsl;
/** A notifier callback is a write into its native ProgramUniforms owner's
 * location, not a program-independent invalidation event. Complete live source
 * proof identity supplies owner/generation; a GL name alone is insufficient. */
public final class BslBoundSignal {
    private Runnable listener;
    private Object owner;
    private int program;
    public void attach(Runnable next,Object liveOwner,int boundProgram) {
        listener=next;owner=next==null?null:liveOwner;program=next==null?0:boundProgram;
    }
    public void fire(Object liveOwner,int boundProgram) {
        if(listener!=null&&owner!=null&&liveOwner==owner&&program>0&&boundProgram==program)listener.run();
    }
}
