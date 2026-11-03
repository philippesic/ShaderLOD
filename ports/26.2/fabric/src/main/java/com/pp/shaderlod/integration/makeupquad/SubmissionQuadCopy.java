package com.pp.shaderlod.integration.makeupquad;
import java.nio.IntBuffer;
import org.lwjgl.PointerBuffer;
/** Private submission transform; source buffers and cached batch metadata are never modified. */
public final class SubmissionQuadCopy {
    public static final int MAX_COMMANDS = 1024;
    private SubmissionQuadCopy() {}
    public static boolean canMerge(int base, int count, int nextBase, int nextCount, int nativeMax) {
        return base >= 0 && nextBase >= 0 && count > 0 && nextCount > 0 && count % 6 == 0 && nextCount % 6 == 0 &&
            (long)base + 4L * (count / 6) == nextBase && (long)count + nextCount <= nativeMax &&
            (long)nextBase + 4L * (nextCount / 6) - 1 <= Integer.MAX_VALUE;
    }
    /** -1 refuses the entire batch before writing destinations; inputs use absolute indexes. */
    public static int compact(PointerBuffer pointers, IntBuffer counts, IntBuffer bases, int size, int nativeMax,
                              PointerBuffer outputPointers, IntBuffer outputCounts, IntBuffer outputBases) {
        if (size < 2 || size > MAX_COMMANDS || nativeMax <= 0 || pointers.limit() < size || counts.limit() < size ||
            bases.limit() < size || outputPointers.limit() < size || outputCounts.limit() < size || outputBases.limit() < size) return -1;
        for (int i=0; i<size; i++) {
            int c=counts.get(i), b=bases.get(i);
            if (pointers.get(i) != 0 || c <= 0 || c % 6 != 0 || c > nativeMax || b < 0 ||
                (long)b + 4L*(c/6)-1 > Integer.MAX_VALUE) return -1;
        }
        int written=0;
        for (int i=0; i<size; i++) {
            int c=counts.get(i), b=bases.get(i);
            if (written>0 && canMerge(outputBases.get(written-1), outputCounts.get(written-1), b, c, nativeMax))
                outputCounts.put(written-1, outputCounts.get(written-1)+c);
            else {
                outputPointers.put(written,0); outputCounts.put(written,c); outputBases.put(written,b); written++;
            }
        }
        return written;
    }
}
