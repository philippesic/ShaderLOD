package com.pp.shaderlod.integration.makeupquad;
import org.lwjgl.system.MemoryUtil;
/** Pure in-place command transform. No GL calls, geometry edits, retained batches or allocation. */
public final class AdjacentQuadCoalescer {
    private AdjacentQuadCoalescer() {}
    // Proven request ceiling covers both first growth and every later native doubling.
    public static final int MAX_MERGED_COUNT = NativeIndexGrowth.SAFE_REQUEST_INDICES;
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
        return compactedSize(compactWithMaximum(pointers,counts,bases,size,capacity,pointerStride));
    }
    /** Allocation-free result: low word is size; high word is checked final maximum, or -1 on refusal. */
    public static long compactWithMaximum(long pointers, long counts, long bases, int size, int capacity, int pointerStride) {
        if (size < 0 || size > capacity || pointerStride != Long.BYTES ||
            pointers <= 0 || counts <= 0 || bases <= 0 ||
            (pointers & 7) != 0 || (counts & 3) != 0 || (bases & 3) != 0 ||
            pointers > Long.MAX_VALUE - 8L * size || counts > Long.MAX_VALUE - 4L * size ||
            bases > Long.MAX_VALUE - 4L * size) return result(size,-1);
        // In-place writes must never alter another array's still-unread commands.
        if (overlap(pointers,8L*size,counts,4L*size) || overlap(pointers,8L*size,bases,4L*size)
            || overlap(counts,4L*size,bases,4L*size)) return result(size,-1);
        int plannedBase=0, plannedCount=0, plannedMax=0, firstMerge=size;
        for (int i=0; i<size; i++) {
            long p=MemoryUtil.memGetAddress(pointers+(long)i*pointerStride);
            int count=MemoryUtil.memGetInt(counts+4L*i), base=MemoryUtil.memGetInt(bases+4L*i);
            if (p != 0 || count <= 0 || count % 6 != 0 || base < 0 ||
                (long)base+4L*(count/6)-1 > Integer.MAX_VALUE) return result(size,-1);
            if (plannedCount>0 && validatedMerge(plannedBase,plannedCount,base,count)) {
                if (firstMerge == size) firstMerge=i;
                plannedCount += count; // validatedMerge proved checked signed sum
            } else { plannedBase=base; plannedCount=count; }
            plannedMax=Math.max(plannedMax,plannedCount);
        }
        // Whole batch remains byte-for-byte native if its post-merge maximum exceeds the all-capacities proof.
        if (!NativeIndexGrowth.requestSafe(plannedMax)) return result(size,-1);
        // No writes occur before the first merge; validation already checked this prefix.
        int written=firstMerge;
        if(firstMerge==size)return result(size,plannedMax);
        int first=MemoryUtil.memGetInt(bases+4L*(written-1));
        int previous=MemoryUtil.memGetInt(counts+4L*(written-1));
        for (int read=firstMerge; read<size; read++) {
            int count=MemoryUtil.memGetInt(counts+4L*read), base=MemoryUtil.memGetInt(bases+4L*read);
            // Current inputs were validated before any writes. Disjoint arrays and
            // written <= read keep the unread suffix intact; the pending run is local.
            if (validatedMerge(first,previous,base,count)) {
                previous += count;
                continue;
            }
            MemoryUtil.memPutInt(counts+4L*(written-1),previous);
            if (written != read) {
                MemoryUtil.memPutAddress(pointers+(long)written*pointerStride,0);
                MemoryUtil.memPutInt(bases+4L*written,base);
            }
            first=base;previous=count;
            written++;
        }
        MemoryUtil.memPutInt(counts+4L*(written-1),previous);
        return result(written,plannedMax);
    }
    private static boolean overlap(long a,long aBytes,long b,long bBytes){return a<b+bBytes&&b<a+aBytes;}
    /** Only positive quad counts and bounded bases from this method's validation pass. */
    private static boolean validatedMerge(int base,int count,int nextBase,int nextCount){
        return (long)base+4L*(count/6)==nextBase&&(long)count+nextCount<=MAX_MERGED_COUNT;
    }
    public static int compactedSize(long result) { return (int)result; }
    public static int checkedMaximum(long result) { return (int)(result >>> 32); }
    private static long result(int size,int maximum) { return ((long)maximum << 32) | (size & 0xffffffffL); }
}
