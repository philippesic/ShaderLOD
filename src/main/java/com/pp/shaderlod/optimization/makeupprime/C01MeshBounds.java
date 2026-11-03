package com.pp.shaderlod.optimization.makeupprime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Bounds of the actual encoded vertices, computed once by the mesh producer. */
public final class C01MeshBounds {
    public static final java.util.concurrent.atomic.AtomicLong scans=new java.util.concurrent.atomic.AtomicLong(),admitted=new java.util.concurrent.atomic.AtomicLong(),wavingExcluded=new java.util.concurrent.atomic.AtomicLong();
    public interface Carrier { C01MeshBounds shaderlod$bounds(); void shaderlod$bounds(C01MeshBounds b); }
    public interface Storage { C01MeshBounds shaderlod$bounds(int section); }
    public final float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
    public final float[] max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
    public final int vertices,section;
    public final int[] counts;
    private C01MeshBounds(int vertices,int section,int[] counts){this.vertices=vertices;this.section=section;this.counts=counts.clone();}
    public static float decode(int hi,int lo,int axis){
        int shift=axis*10;
        return (((hi>>>shift)&1023)<<10 | ((lo>>>shift)&1023))*0x1p-15f-8.0f;
    }
    public static boolean waving(int id){return id==10031||id==10175||id==10176||id==10059||id==10018;}
    public static C01MeshBounds scan(ByteBuffer original,int stride,int materialOffset,int[] counts){
        long[] cost=C01Cost.begin();try{scans.incrementAndGet();
        if(original==null||stride<24||materialOffset<20||materialOffset+4>stride||counts==null||counts.length!=7)return null;
        var b=original.duplicate().order(ByteOrder.nativeOrder());int bytes=b.remaining(),start=b.position();
        long n=0;for(int c:counts){if(c<0||(c&3)!=0)return null;n+=c;}
        if(n<=0||n*stride!=bytes)return null;
        int section=(b.getInt(start+16)>>>24)&255;
        var result=new C01MeshBounds((int)n,section,counts);
        for(int offset=start;offset<start+bytes;offset+=stride){
            if(((b.getInt(offset+16)>>>24)&255)!=section)return null;
            if(waving((b.getInt(offset+materialOffset)>>>1)-1)){wavingExcluded.incrementAndGet();return null;}
            int hi=b.getInt(offset),lo=b.getInt(offset+4);
            for(int a=0;a<3;a++){float v=decode(hi,lo,a);result.min[a]=Math.min(result.min[a],v);result.max[a]=Math.max(result.max[a],v);}
        }
        admitted.incrementAndGet();return result;}finally{C01Cost.end("cache_scan",cost);}
    }
}
