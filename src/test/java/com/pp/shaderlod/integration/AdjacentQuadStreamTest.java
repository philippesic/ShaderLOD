package com.pp.shaderlod.integration;

import com.pp.shaderlod.integration.makeupquad.AdjacentQuadCoalescer;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

class AdjacentQuadStreamTest {
    private static List<Integer> stream(int[] counts,int[] bases,int size){
        var indices=new ArrayList<Integer>();
        for(int i=0;i<size;i++)for(int q=0;q<counts[i]/6;q++){
            int b=bases[i]+4*q;
            for(int v:new int[]{0,1,2,2,3,0})indices.add(b+v);
        }
        return indices;
    }
    @Test void randomNativeStreamsPreserveEveryOrderedTriangleAndMaximum(){
        var random=new Random(0x4e3031);
        for(int trial=0;trial<1000;trial++){
            int size=1+random.nextInt(64);int[] counts=new int[size],bases=new int[size];
            int base=random.nextInt(100);
            long p=MemoryUtil.nmemCalloc(size,8),c=MemoryUtil.nmemAlloc(4L*size),b=MemoryUtil.nmemAlloc(4L*size);
            try{
                for(int i=0;i<size;i++){
                    counts[i]=6*(1+random.nextInt(7));bases[i]=base;
                    MemoryUtil.memPutInt(c+4L*i,counts[i]);MemoryUtil.memPutInt(b+4L*i,base);
                    base=random.nextInt(8)==0?random.nextInt(2048):base+4*(counts[i]/6)+(random.nextBoolean()?0:4*(1+random.nextInt(3)));
                }
                var expected=stream(counts,bases,size);
                long result=AdjacentQuadCoalescer.compactWithMaximum(p,c,b,size,size,8);
                int after=AdjacentQuadCoalescer.compactedSize(result),maximum=0;
                for(int i=0;i<after;i++){
                    assertEquals(0,MemoryUtil.memGetAddress(p+8L*i));
                    counts[i]=MemoryUtil.memGetInt(c+4L*i);bases[i]=MemoryUtil.memGetInt(b+4L*i);
                    maximum=Math.max(maximum,counts[i]);
                }
                assertEquals(expected,stream(counts,bases,after));
                assertEquals(maximum,AdjacentQuadCoalescer.checkedMaximum(result));
            }finally{MemoryUtil.nmemFree(p);MemoryUtil.nmemFree(c);MemoryUtil.nmemFree(b);}
        }
    }
    @Test void overlappingInputArraysRefuseBeforeAnyWrite(){
        long memory=MemoryUtil.nmemCalloc(1,128);
        try{
            for(int offset=0;offset<128;offset+=4)MemoryUtil.memPutInt(memory+offset,6);
            for(int offset=0;offset<32;offset++)MemoryUtil.memPutByte(memory+offset,(byte)0);
            // Count/base overlap is otherwise valid and contains an actual merge:
            // counts [18,6,18,30], bases [6,18,30,42]. Refuse before it can corrupt a run.
            int[] aliasValues={18,6,18,30,42};
            for(int i=0;i<aliasValues.length;i++)MemoryUtil.memPutInt(memory+64+4L*i,aliasValues[i]);
            for(long[] arrays:new long[][]{{memory,memory+4,memory+64},{memory,memory+64,memory+8},{memory,memory+64,memory+68}}){
                byte[] before=new byte[128];for(int i=0;i<128;i++)before[i]=MemoryUtil.memGetByte(memory+i);
                long result=AdjacentQuadCoalescer.compactWithMaximum(arrays[0],arrays[1],arrays[2],4,4,8);
                assertEquals(4,AdjacentQuadCoalescer.compactedSize(result));
                assertEquals(-1,AdjacentQuadCoalescer.checkedMaximum(result));
                for(int i=0;i<128;i++)assertEquals(before[i],MemoryUtil.memGetByte(memory+i));
            }
        }finally{MemoryUtil.nmemFree(memory);}
    }
}
