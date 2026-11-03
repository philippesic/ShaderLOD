package com.pp.shaderlod.optimization.depthprime;

import java.util.function.ToIntFunction;

/** Both Java program identities and their live handles belong to this one successful link pair. */
public final class DepthProgramLease<P> {
    private final Object pipeline, owner, pass;
    private final P nativeProgram, depthProgram;
    private final int nativeId, depthId;
    private final long epoch;
    private final ToIntFunction<P> handle;
    public DepthProgramLease(Object pipeline, Object owner, Object pass, P nativeProgram, P depthProgram,
            long epoch, ToIntFunction<P> handle) {
        this.pipeline=java.util.Objects.requireNonNull(pipeline); this.owner=java.util.Objects.requireNonNull(owner); this.pass=java.util.Objects.requireNonNull(pass);
        this.nativeProgram=nativeProgram; this.depthProgram=depthProgram;
        this.epoch=epoch; this.handle=handle;
        nativeId=handle.applyAsInt(nativeProgram); depthId=handle.applyAsInt(depthProgram);
    }
    public boolean current(Object pipeline, Object owner, Object pass, P nativeProgram, P depthProgram, long epoch) {
        if (this.pipeline!=pipeline || this.owner!=owner || this.pass!=pass || this.epoch!=epoch
                || this.nativeProgram!=nativeProgram || this.depthProgram!=depthProgram
                || nativeId<=0 || depthId<=0 || nativeId==depthId) return false;
        try { return handle.applyAsInt(nativeProgram)==nativeId && handle.applyAsInt(depthProgram)==depthId; }
        catch (RuntimeException deleted) { return false; }
    }
}
