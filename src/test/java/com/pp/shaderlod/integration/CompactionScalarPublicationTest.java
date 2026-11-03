package com.pp.shaderlod.integration;

import com.pp.shaderlod.integration.makeupquad.*;
import java.util.*;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Sodium command memory and native getIndexBufferSize scan; no GL context or mixin impersonation. */
class CompactionScalarPublicationTest {
    private static final long GENERATION=17;
    private static final class Batch implements AutoCloseable {
        final MultiDrawBatch nativeBatch=new MultiDrawBatch(8);
        final LegacyScalarState scalar=new LegacyScalarState();
        Batch(int[] counts,int[] bases) { this(counts,bases,false); }
        Batch(int[] counts,int[] bases,boolean compactionOnly) {
            scalar.begin(true,nativeBatch.capacity(),GENERATION,compactionOnly);
            nativeBatch.size=counts.length;
            for(int i=0;i<counts.length;i++) {
                MemoryUtil.memPutAddress(nativeBatch.pElementPointer+(long)i*Pointer.POINTER_SIZE,0);
                MemoryUtil.memPutInt(nativeBatch.pElementCount+4L*i,counts[i]);
                MemoryUtil.memPutInt(nativeBatch.pBaseVertex+4L*i,bases[i]);
                if(!compactionOnly)scalar.emitted(counts[i]);
            }
        }
        long compact() {
            var b=nativeBatch;
            return AdjacentQuadCoalescer.compactWithMaximum(b.pElementPointer,b.pElementCount,b.pBaseVertex,b.size,b.capacity(),Pointer.POINTER_SIZE);
        }
        int[] commands() {
            var b=nativeBatch;var data=new int[b.size*4];
            for(int i=0;i<b.size;i++) {
                long pointer=MemoryUtil.memGetAddress(b.pElementPointer+(long)i*Pointer.POINTER_SIZE);
                data[4*i]=(int)pointer;data[4*i+1]=(int)(pointer>>>32);
                data[4*i+2]=MemoryUtil.memGetInt(b.pElementCount+4L*i);data[4*i+3]=MemoryUtil.memGetInt(b.pBaseVertex+4L*i);
            }
            return data;
        }
        @Override public void close(){nativeBatch.delete();}
    }
    @Test void actualFinalNativeMaximumIsPublishedInEitherReturnHookOrder() {
        int ceiling=AdjacentQuadCoalescer.MAX_MERGED_COUNT;
        int[][] counts={{6,6,12},{6,12},{6},{},{6,6,12,6},{ceiling,6}};
        int[][] bases={{0,4,8},{0,100},{0},{},{0,4,8,100},{0,4*(ceiling/6)}};
        for(boolean finishFirst:List.of(false,true))for(int sample=0;sample<counts.length;sample++) {
            try(var fixture=new Batch(counts[sample],bases[sample])) {
                var b=fixture.nativeBatch;var scalar=fixture.scalar;int before=b.size;
                int nativeMaximumBefore=b.getIndexBufferSize();
                if(finishFirst)scalar.finish(before);
                long result=fixture.compact();b.size=AdjacentQuadCoalescer.compactedSize(result);
                int maximum=AdjacentQuadCoalescer.checkedMaximum(result);
                assertEquals(b.getIndexBufferSize(),maximum,"Checked result agrees with the actual native post-buffer scan");
                assertTrue(NativeIndexGrowth.requestSafe(maximum));
                assertTrue(scalar.compacted(before,b.size,maximum,GENERATION));
                assertEquals(finishFirst,scalar.reusable(b.size,GENERATION),"Publication alone cannot finish an unfinished fill");
                if(!finishFirst)scalar.finish(b.size);
                assertTrue(scalar.reusable(b.size,GENERATION));assertEquals(b.getIndexBufferSize(),scalar.maximum());
                if(sample==0) {
                    assertEquals(1,b.size);assertEquals(24,scalar.maximum());assertTrue(scalar.maximum()>nativeMaximumBefore);
                }
                if(sample==4) {assertEquals(2,b.size);assertEquals(24,scalar.maximum());}
                if(sample==5) {assertEquals(2,b.size);assertEquals(ceiling,scalar.maximum());}
            }
        }
    }
    @Test void lateRefusalPreservesEveryCommandAfterMergeablePrefix() {
        for(String cause:List.of("foreign","nonquad","baseoverflow")) {
            try(var fixture=new Batch(new int[]{6,6,cause.equals("nonquad")?5:6},
                    new int[]{0,4,cause.equals("baseoverflow")?Integer.MAX_VALUE:8})) {
                var b=fixture.nativeBatch;
                if(cause.equals("foreign"))MemoryUtil.memPutAddress(b.pElementPointer+2L*Pointer.POINTER_SIZE,8);
                int[] original=fixture.commands();long result=fixture.compact();
                assertEquals(3,AdjacentQuadCoalescer.compactedSize(result));
                assertEquals(-1,AdjacentQuadCoalescer.checkedMaximum(result));
                assertArrayEquals(original,fixture.commands(),cause);
            }
        }
    }
    @Test void refusalsLeaveCommandBytesAndFinishedNativeScalarUntouched() {
        for(String cause:List.of("foreign","nonquad","baseoverflow","unsafe-growth")) {
            int first=cause.equals("nonquad")?5:cause.equals("unsafe-growth")?NativeIndexGrowth.SAFE_REQUEST_INDICES+6:6;
            int base=cause.equals("baseoverflow")?Integer.MAX_VALUE:0;
            try(var fixture=new Batch(new int[]{first,6},new int[]{base,4})) {
                var b=fixture.nativeBatch;var scalar=fixture.scalar;
                if(cause.equals("foreign"))MemoryUtil.memPutAddress(b.pElementPointer,8);
                scalar.finish(b.size);int[] original=fixture.commands();int nativeMaximum=b.getIndexBufferSize();
                long result=fixture.compact();
                assertEquals(b.size,AdjacentQuadCoalescer.compactedSize(result));
                assertEquals(-1,AdjacentQuadCoalescer.checkedMaximum(result),cause);
                assertArrayEquals(original,fixture.commands(),cause);
                // Production skips publication when the coalescer returns the refusal sentinel.
                assertTrue(scalar.reusable(b.size,GENERATION));assertEquals(nativeMaximum,scalar.maximum());
            }
        }
    }
    @Test void staleGenerationAndMismatchedFillCannotPublishEvenWithCheckedCompaction() {
        for(String cause:List.of("stale","missing-emission","wrong-before","invalid-maximum","disabled")) {
            try(var fixture=new Batch(new int[]{6,6},new int[]{0,4})) {
                var b=fixture.nativeBatch;var scalar=fixture.scalar;int before=b.size;
                long result=fixture.compact();b.size=AdjacentQuadCoalescer.compactedSize(result);
                if(cause.equals("missing-emission")) {scalar.begin(true,b.capacity(),GENERATION);scalar.emitted(6);}
                if(cause.equals("disabled"))scalar.begin(false,b.capacity(),GENERATION);
                scalar.finish(before);
                assertFalse(scalar.compacted(cause.equals("wrong-before")?before+1:before,b.size,
                    cause.equals("invalid-maximum")?7:AdjacentQuadCoalescer.checkedMaximum(result),
                    cause.equals("stale")?GENERATION+1:GENERATION),cause);
                scalar.finish(b.size);assertFalse(scalar.reusable(b.size,GENERATION),cause);
            }
        }
    }
    @Test void malformedPublicationDoesNotRevalidateAfterLaterFinish() {
        for(int[] tuple:new int[][]{{2,3,6},{2,0,0},{2,1,0},{0,0,6},{2,1,-1},{2,1,NativeIndexGrowth.SAFE_REQUEST_INDICES+6}}) {
            var scalar=new LegacyScalarState();scalar.begin(true,8,GENERATION);
            for(int i=0;i<tuple[0];i++)scalar.emitted(6);
            scalar.finish(tuple[0]);
            assertFalse(scalar.compacted(tuple[0],tuple[1],tuple[2],GENERATION));
            scalar.finish(tuple[1]);assertFalse(scalar.reusable(tuple[1],GENERATION));
        }
    }
    @Test void originalCompactApiReturnsSameCommandsAndSizeAsCheckedApi() {
        try(var first=new Batch(new int[]{6,6,12},new int[]{0,4,8});var second=new Batch(new int[]{6,6,12},new int[]{0,4,8})) {
            long result=first.compact();first.nativeBatch.size=AdjacentQuadCoalescer.compactedSize(result);
            var b=second.nativeBatch;b.size=AdjacentQuadCoalescer.compact(b.pElementPointer,b.pElementCount,b.pBaseVertex,b.size,b.capacity(),Pointer.POINTER_SIZE);
            assertEquals(first.nativeBatch.size,b.size);assertArrayEquals(first.commands(),second.commands());
        }
    }
    @Test void invalidMetadataRefusesBeforeChangingAnyNativeCommands() {
        try(var fixture=new Batch(new int[]{6,6},new int[]{0,4})) {
            var b=fixture.nativeBatch;int[] original=fixture.commands();
            long[] results={
                AdjacentQuadCoalescer.compactWithMaximum(b.pElementPointer,b.pElementCount,b.pBaseVertex,b.size,b.size-1,Pointer.POINTER_SIZE),
                AdjacentQuadCoalescer.compactWithMaximum(b.pElementPointer,b.pElementCount,b.pBaseVertex,b.size,b.capacity(),4),
                AdjacentQuadCoalescer.compactWithMaximum(b.pElementPointer,b.pElementCount+1,b.pBaseVertex,b.size,b.capacity(),Pointer.POINTER_SIZE),
                AdjacentQuadCoalescer.compactWithMaximum(Long.MAX_VALUE-7,b.pElementCount,b.pBaseVertex,b.size,b.capacity(),Pointer.POINTER_SIZE)};
            for(long result:results) {
                assertEquals(b.size,AdjacentQuadCoalescer.compactedSize(result));
                assertEquals(-1,AdjacentQuadCoalescer.checkedMaximum(result));
                assertArrayEquals(original,fixture.commands());
            }
        }
    }
    @Test void finalNativeScanAtCapacityProofLimitRetainsSafeGrowthArithmetic() {
        try(var fixture=new Batch(new int[]{NativeIndexGrowth.SAFE_REQUEST_INDICES},new int[]{0})) {
            var b=fixture.nativeBatch;fixture.scalar.finish(b.size);
            long result=fixture.compact();int maximum=AdjacentQuadCoalescer.checkedMaximum(result);
            assertEquals(b.getIndexBufferSize(),maximum);
            assertTrue(fixture.scalar.compacted(b.size,AdjacentQuadCoalescer.compactedSize(result),maximum,GENERATION));
            assertTrue(fixture.scalar.reusable(b.size,GENERATION));
            int requiredQuads=fixture.scalar.maximum()/6;
            for(int current:new int[]{0,1,requiredQuads-1,requiredQuads,NativeIndexGrowth.SAFE_BYTE_QUADS}) {
                if(current>=requiredQuads)continue;
                long next=Math.max(2L*current,(long)requiredQuads+16384);
                assertTrue(next<=NativeIndexGrowth.SAFE_BYTE_QUADS);
                assertTrue(24L*next<=Integer.MAX_VALUE,"Native growth byte arithmetic remains within its proven range");
            }
        }
    }

    @Test void pendingPublicationAgreesWithActualNativeMaximumAcrossMergeAndGrowthBoundaries() {
        int ceiling=AdjacentQuadCoalescer.MAX_MERGED_COUNT;
        int[][] counts={{6,6,12},{6,12},{6},{},{6,6,12,6},{ceiling,6}};
        int[][] bases={{0,4,8},{0,100},{0},{},{0,4,8,100},{0,4*(ceiling/6)}};
        for(int i=0;i<counts.length;i++)try(var fixture=new Batch(counts[i],bases[i],true)) {
            var b=fixture.nativeBatch;var scalar=fixture.scalar;int before=b.size;
            assertFalse(scalar.recording());long result=fixture.compact();b.size=AdjacentQuadCoalescer.compactedSize(result);
            assertTrue(scalar.compacted(before,b.size,AdjacentQuadCoalescer.checkedMaximum(result),GENERATION));
            assertFalse(scalar.reusable(b.size,GENERATION));scalar.finish(b.size);
            assertTrue(scalar.reusable(b.size,GENERATION));assertEquals(b.getIndexBufferSize(),scalar.maximum());
        }
    }
    @Test void pendingMalformedProofCannotBeRevalidated() {
        for(int[] tuple:new int[][]{{9,1,6},{2,3,6},{2,0,0},{2,1,0},{0,0,6},{2,1,-1},{2,1,7},{2,1,NativeIndexGrowth.SAFE_REQUEST_INDICES+6}}) {
            var state=new LegacyScalarState();state.begin(true,8,GENERATION,true);
            assertFalse(state.compacted(tuple[0],tuple[1],tuple[2],GENERATION));
            state.finish(tuple[1]);assertFalse(state.reusable(tuple[1],GENERATION));
        }
    }
    @Test void pendingNativeRefusalDoesNotPublishPremergeOrObservedMaximum() {
        for(String cause:List.of("foreign","nonquad","baseoverflow","unsafe-growth")) {
            int first=cause.equals("nonquad")?5:cause.equals("unsafe-growth")?NativeIndexGrowth.SAFE_REQUEST_INDICES+6:6;
            try(var fixture=new Batch(new int[]{first,6},new int[]{cause.equals("baseoverflow")?Integer.MAX_VALUE:0,4},true)) {
                var b=fixture.nativeBatch;if(cause.equals("foreign"))MemoryUtil.memPutAddress(b.pElementPointer,8);
                int[] original=fixture.commands();long result=fixture.compact();
                assertEquals(-1,AdjacentQuadCoalescer.checkedMaximum(result));assertArrayEquals(original,fixture.commands());
                fixture.scalar.finish(b.size);assertFalse(fixture.scalar.reusable(b.size,GENERATION));
            }
        }
    }

}
