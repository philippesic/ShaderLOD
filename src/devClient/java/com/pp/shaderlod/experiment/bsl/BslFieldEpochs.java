package com.pp.shaderlod.experiment.bsl;

/** Execution identity, not a temporal cache. Every native shadow traversal must
 * produce a fresh complete field. Counter equality never certifies a traversal. */
public final class BslFieldEpochs {
    public record Context(Object pipeline, Object notifier, Object pack, Object world,
            Object camera, Object target, int frame, int width, int height) {
        public boolean valid() {
            return pipeline != null && notifier != null && pack != null && world != null
                && camera != null && target != null && frame >= 0 && frame < 16_777_215
                && width > 0 && height > 0;
        }
        public boolean same(Context b) {
            return b != null && pipeline == b.pipeline && notifier == b.notifier && pack == b.pack
                && world == b.world && camera == b.camera && target == b.target && frame == b.frame
                && width == b.width && height == b.height;
        }
    }
    public record Identity(long pipelineEpoch, long renderEpoch, long shadowEpoch, int frame) {}
    private Context current;
    private long pipelineEpoch, renderEpoch, shadowEpoch;
    private boolean open, shadowOpen, nativeBeforeField, producing, complete, restored, failed;
    private int producerDepth, producerField;
    public void begin(Context c) {
        boolean samePipeline = current != null && c != null && current.pipeline == c.pipeline;
        boolean duplicate = samePipeline && current.frame == c.frame;
        if (!samePipeline) pipelineEpoch++;
        renderEpoch++;
        current = c;
        open = c != null && c.valid();
        shadowOpen = nativeBeforeField = producing = complete = restored = false;
        failed = duplicate || !open;
        producerDepth = producerField = 0;
    }
    public void shadowBegin(Context c) {
        shadowEpoch++;
        if (!open || failed || !current.same(c) || shadowOpen || complete) { fail(); return; }
        shadowOpen = true;
        nativeBeforeField = restored = false;
    }
    /** Reached only after native geometry/depth copy/mip preparation, directly
     * before the actual shadow-composite chain. Earlier native compute work in
     * that chain must also finish before the field's own Program.use/quad. */
    public void nativeBeforeField(Context c) {
        if (!shadowOpen || !current.same(c)) { fail(); return; }
        nativeBeforeField = true;
    }
    public boolean producer(Context c, int depth, int field, boolean exactResources) {
        complete = false;
        if (!open || failed || !shadowOpen || !nativeBeforeField || producing
                || !current.same(c) || depth <= 0 || field <= 0 || depth == field || !exactResources) {
            fail(); return false;
        }
        producing = true;
        producerDepth = depth; producerField = field;
        return true;
    }
    public void fieldReturned(Context c, boolean completeUsefulRegion) {
        if (!producing || failed || !current.same(c) || !completeUsefulRegion) { fail(); return; }
        producing = false;
        complete = true;
    }
    public void shadowReturned(Context c) {
        if (!shadowOpen || producing || !complete || failed || !current.same(c)) { fail(); return; }
        shadowOpen = false;
        restored = true;
    }
    public boolean consumer(Context c, int depth, int field, boolean supportAndInputs) {
        return open && !failed && restored && complete && !shadowOpen && !producing
            && current.same(c) && producerDepth == depth && producerField == field && supportAndInputs;
    }
    public boolean producerUniform(Context c) {
        return producing && !failed && current != null && current.same(c);
    }
    public boolean fieldComplete() { return complete && !failed; }
    public int depthTexture() { return producerDepth; }
    public int fieldTexture() { return producerField; }
    public Identity identity() { return new Identity(pipelineEpoch, renderEpoch, shadowEpoch,
            current == null ? -1 : current.frame); }
    public void fail() { failed = true; producing = complete = restored = false; }
    public void end() { open = false; producing = complete = restored = false; }
    public void dispose(Object pipeline) {
        if (current != null && current.pipeline == pipeline) { end(); current = null; }
    }
}
