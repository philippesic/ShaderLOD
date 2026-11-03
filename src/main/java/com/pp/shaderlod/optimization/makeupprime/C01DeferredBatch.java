package com.pp.shaderlod.optimization.makeupprime;

import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import org.lwjgl.system.MemoryUtil;

/** One render-thread-owned native batch; no mesh/tessellation lifetime is retained. */
final class C01DeferredBatch implements AutoCloseable {
    static final int CAPACITY=1792;
    final MultiDrawBatch batch=new MultiDrawBatch(CAPACITY);
    final int[] starts=new int[256],ends=new int[256];
    final boolean[] eligible=new boolean[256];
    int groups,firstSlot,region;
    C01Bounds.Replay replay;

    void capture(MultiDrawBatch source,int count,int slot,int ordinal,
            int[] from,int[] to,boolean[] valid,C01Bounds.Replay callback) {
        if(source.size<0||source.size>CAPACITY||count<0||count>256||slot<0||callback==null)
            throw new IllegalArgumentException("Finite deferred native batch");
        if(count>0) {
            int end=0;
            for(int i=0;i<count;i++) {
                if(from[i]!=end||to[i]<from[i]||to[i]>source.size)
                    throw new IllegalArgumentException("Native group partition mismatch");
                end=to[i];
            }
            if(end!=source.size)throw new IllegalArgumentException("Incomplete native group partition");
        }
        batch.size=source.size;
        MemoryUtil.memCopy(source.pElementPointer,batch.pElementPointer,8L*source.size);
        MemoryUtil.memCopy(source.pElementCount,batch.pElementCount,4L*source.size);
        MemoryUtil.memCopy(source.pBaseVertex,batch.pBaseVertex,4L*source.size);
        System.arraycopy(from,0,starts,0,count);System.arraycopy(to,0,ends,0,count);
        System.arraycopy(valid,0,eligible,0,count);
        groups=count;firstSlot=slot;region=ordinal;replay=callback;
    }

    void release() {replay=null;}
    @Override public void close() {release();batch.delete();}
}
