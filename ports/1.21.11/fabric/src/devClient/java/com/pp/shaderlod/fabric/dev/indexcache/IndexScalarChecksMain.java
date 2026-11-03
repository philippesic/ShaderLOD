package com.pp.shaderlod.fabric.dev.indexcache;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import org.lwjgl.system.MemoryUtil;
/** Real native batch scalar reference, no GL context, no claimed mixin/runtime admission. */
public final class IndexScalarChecksMain {
    private static void check(boolean pass) {if(!pass)throw new AssertionError("Index scalar cache mismatch");}
    public static void main(String[] args) {
        var batch=new MultiDrawBatch(3);var state=new IndexScalarState();
        try {
            state.invalidate();batch.size=3;batch.isFilled=true;
            MemoryUtil.memPutInt(batch.pElementCount,6);MemoryUtil.memPutInt(batch.pElementCount+4,120);MemoryUtil.memPutInt(batch.pElementCount+8,12);
            state.remember(batch.isFilled,batch.size,batch.getIndexBufferSize());check(state.maximum()==120);check(state.reusable(true,3));
            // Same size, changed maximum: partial writer invalidation must precede first write.
            state.invalidate();MemoryUtil.memPutInt(batch.pElementCount+4,18);check(!state.reusable(true,3));
            state.remember(true,3,batch.getIndexBufferSize());check(state.maximum()==18);
            // Native clear keeps memory; scalar for empty batch is exactly zero.
            state.invalidate();batch.clear();check(batch.getIndexBufferSize()==0);check(!state.reusable(batch.isFilled,batch.size));
            state.remember(false,0,0);check(!state.reusable(true,0));
            // Failed partial rebuild never leaves a previously memoized maximum reusable.
            state.invalidate();batch.isFilled=true;batch.size=1;MemoryUtil.memPutInt(batch.pElementCount,600);check(!state.reusable(true,1));
            state.remember(true,1,batch.getIndexBufferSize());check(state.maximum()==600);check(!state.reusable(true,2));
            // Match native signed Math.max seeded at zero, not an unsigned interpretation.
            state.invalidate();batch.size=2;MemoryUtil.memPutInt(batch.pElementCount,-1);MemoryUtil.memPutInt(batch.pElementCount+4,-2);
            state.remember(true,2,batch.getIndexBufferSize());check(state.maximum()==0);
            System.out.println("Real Sodium batch scalar/reference/invalidation cases passed; mixin and GL/runtime unverified");
        } finally {state.invalidate();batch.delete();}
    }
}
