package com.pp.shaderlod.optimization.unboundao;

/** Current-frame identity only; caller supplies actual checked GL/program/profile facts.
 * Installed caller is the pinned pre-capture PipelineManager boundary and actual draw-return hooks. */
public final class UnboundAoEpoch {
    public record Context(Object pipeline,Object pack,Object targets,long sourceGeneration,long flagGeneration,
                          int width,int height,int producerProgram,int consumerProgram) {}
    public record Output(int texture,int framebuffer,int width,int height) {}
    private Context current;
    private Output started,completed;
    private long frame,completedFrame;
    private boolean revoked;
    public void begin(Context context) {current=context;started=completed=null;completedFrame=-1;frame++;}
    public boolean start(Context context,Output output,boolean actualResourceProof) {
        started=completed=null;completedFrame=-1;
        if(revoked||!same(context)||output==null||!actualResourceProof)return false;
        started=output;return true;
    }
    public boolean returned(Context context,Output output,boolean originalDrawReturned,boolean sameBindings) {
        if(revoked||!same(context)||started==null||!started.equals(output)||!originalDrawReturned||!sameBindings) {
            invalidate();return false;
        }
        completed=output;completedFrame=frame;started=null;return true;
    }
    public boolean consumer(Context context,int actualSampledTexture,boolean actualConsumerProof) {
        return !revoked&&same(context)&&completed!=null&&completedFrame==frame&&
                actualSampledTexture==completed.texture()&&actualConsumerProof;
    }
    public void revoke() {revoked=true;invalidate();}
    public void invalidate() {current=null;started=completed=null;completedFrame=-1;}
    /** Requalification is a new actual setup operation; beginning a frame never restores admission. */
    public void requalified() {revoked=false;invalidate();}
    public boolean revoked() {return revoked;}
    private boolean same(Context c) {
        return current!=null&&c!=null&&c.pipeline()==current.pipeline()&&c.pack()==current.pack()&&
                c.targets()==current.targets()&&c.sourceGeneration()==current.sourceGeneration()&&
                c.flagGeneration()==current.flagGeneration()&&c.width()==current.width()&&c.height()==current.height()&&
                c.producerProgram()==current.producerProgram()&&c.consumerProgram()==current.consumerProgram();
    }
}
