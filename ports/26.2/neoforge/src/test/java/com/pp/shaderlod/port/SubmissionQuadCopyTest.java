package com.pp.shaderlod.port;
import com.pp.shaderlod.integration.makeupquad.SubmissionQuadCopy;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.PointerBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SubmissionQuadCopyTest {
    private static IntBuffer ints(int... values) { return IntBuffer.wrap(values); }
    private static PointerBuffer pointers(int size) { return PointerBuffer.allocateDirect(size); }
    private static List<Integer> triangles(IntBuffer counts,IntBuffer bases,int size) {
        var out=new ArrayList<Integer>();int[] pattern={0,1,2,2,3,0};
        for(int i=0;i<size;i++) for(int q=0;q<counts.get(i)/6;q++) for(int v:pattern) out.add(bases.get(i)+4*q+v);
        return out;
    }
    @Test void copiedCommandsPreserveExactTriangleOrderAndProvokingVerticesWithoutCachedWrites() {
        var p=pointers(3);var c=ints(6,6,24);var b=ints(0,4,8);
        var op=pointers(3);var oc=ints(0,0,0);var ob=ints(0,0,0);
        var original=triangles(c,b,3);
        int n=SubmissionQuadCopy.compact(p,c,b,3,24,op,oc,ob);
        assertEquals(2,n);assertEquals(12,oc.get(0));assertEquals(24,oc.get(1));
        assertEquals(original,triangles(oc,ob,n));
        assertArrayEquals(new int[]{6,6,24},c.array());assertArrayEquals(new int[]{0,4,8},b.array());
        assertEquals(0,p.get(0));assertEquals(0,p.get(1));assertEquals(0,p.get(2));
        assertTrue(oc.get(0)<=24 && oc.get(1)<=24);
    }
    @Test void customIndexPointerRefusesWholeBatchWithoutDestinationWrites() {
        var p=pointers(3);p.put(2,4);var oc=ints(99,99,99);var ob=ints(88,88,88);
        assertEquals(-1,SubmissionQuadCopy.compact(p,ints(6,6,24),ints(0,4,8),3,24,pointers(3),oc,ob));
        assertArrayEquals(new int[]{99,99,99},oc.array());assertArrayEquals(new int[]{88,88,88},ob.array());
    }
    @Test void invalidTailRefusesBeforeEarlierEligiblePairWrites() {
        for (int count:new int[]{0,-6,7,30}) {
            var oc=ints(99,99,99);
            assertEquals(-1,SubmissionQuadCopy.compact(pointers(3),ints(6,6,count),ints(0,4,8),3,24,pointers(3),oc,ints(0,0,0)));
            assertArrayEquals(new int[]{99,99,99},oc.array());
        }
    }
    @Test void overflowNoncontiguousAndNativeCapacityCannotMerge() {
        assertFalse(SubmissionQuadCopy.canMerge(0,6,4,6,6));
        assertFalse(SubmissionQuadCopy.canMerge(0,6,8,6,24));
        assertFalse(SubmissionQuadCopy.canMerge(Integer.MAX_VALUE-3,6,Integer.MAX_VALUE,6,24));
        assertFalse(SubmissionQuadCopy.canMerge(0,6,4,Integer.MAX_VALUE-1,Integer.MAX_VALUE));
    }
    @Test void commandLimitFallsBackAndNeverWritesCachedSources() {
        assertEquals(-1,SubmissionQuadCopy.compact(pointers(2),ints(6,6),ints(0,4),1025,24,pointers(2),ints(0,0),ints(0,0)));
    }
}
