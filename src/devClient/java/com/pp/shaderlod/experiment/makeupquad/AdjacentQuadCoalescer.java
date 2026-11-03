package com.pp.shaderlod.experiment.makeupquad;
import org.lwjgl.system.MemoryUtil;
/** Pure in-place command transform. No GL calls, geometry edits, retained batches or allocation. */
public final class AdjacentQuadCoalescer {
    private AdjacentQuadCoalescer() {}
    // Native grow allocates (requestedQuads + 16384) * 24 bytes on first growth.
    // Do not introduce a merged command beyond the positive int byte-allocation domain.
    public static final int MAX_MERGED_COUNT = (Integer.MAX_VALUE / 24 - 16384) * 6;
    public static boolean canMerge(long firstPointer, int firstBase, int firstCount,
                                   long nextPointer, int nextBase, int nextCount) {
        if (firstPointer != 0 || nextPointer != 0 || firstBase < 0 || nextBase < 0 ||
            firstCount <= 0 || nextCount <= 0 || firstCount % 6 != 0 || nextCount % 6 != 0) return false;
        long sum = (long) firstCount + nextCount;
        long end = (long) firstBase + 4L * (firstCount / 6);
        long last = (long) nextBase + 4L * (nextCount / 6) - 1;
        return end == nextBase && sum <= MAX_MERGED_COUNT && last <= Integer.MAX_VALUE;
    }
    /** Invalid metadata/stride/any custom-IBO or nonquad command leaves the whole batch untouched. */
    public static int compact(long pointers, long counts, long bases, int size, int capacity, int pointerStride) {
        if (size < 0 || size > capacity || pointerStride != Long.BYTES ||
            pointers <= 0 || counts <= 0 || bases <= 0 ||
            (pointers & 7) != 0 || (counts & 3) != 0 || (bases & 3) != 0 ||
            pointers > Long.MAX_VALUE - 8L * size || counts > Long.MAX_VALUE - 4L * size ||
            bases > Long.MAX_VALUE - 4L * size) return size;
        for (int i=0; i<size; i++) {
            long p=MemoryUtil.memGetAddress(pointers+(long)i*pointerStride);
            int count=MemoryUtil.memGetInt(counts+4L*i), base=MemoryUtil.memGetInt(bases+4L*i);
            if (p != 0 || count <= 0 || count % 6 != 0 || base < 0 ||
                (long)base+4L*(count/6)-1 > Integer.MAX_VALUE) return size;
        }
        int written=0;
        for (int read=0; read<size; read++) {
            int count=MemoryUtil.memGetInt(counts+4L*read), base=MemoryUtil.memGetInt(bases+4L*read);
            if (written>0) {
                int previous=MemoryUtil.memGetInt(counts+4L*(written-1));
                int first=MemoryUtil.memGetInt(bases+4L*(written-1));
                if (canMerge(0,first,previous,0,base,count)) {
                    MemoryUtil.memPutInt(counts+4L*(written-1),Math.addExact(previous,count));
                    continue;
                }
            }
            if (written != read) {
                MemoryUtil.memPutAddress(pointers+(long)written*pointerStride,0);
                MemoryUtil.memPutInt(counts+4L*written,count);
                MemoryUtil.memPutInt(bases+4L*written,base);
            }
            written++;
        }
        return written;
    }
}
