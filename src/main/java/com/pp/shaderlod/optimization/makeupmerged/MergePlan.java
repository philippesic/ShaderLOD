package com.pp.shaderlod.optimization.makeupmerged;

import java.util.ArrayList;
import java.util.List;

/** Pure current-pass plan. Owns commands, never native region mesh state. */
public final class MergePlan {
    public static final int PAGE_SHIFT = 12, PAGE_VERTICES = 1 << PAGE_SHIFT;
    public static final int MAX_VERTICES = 1 << 24, MAX_REGIONS = 4096, MAX_COMMANDS = 1 << 20;
    public static final long MAX_BYTES = 256L << 20;
    public record Span(int sourceStart, int sourceEnd, int destinationStart, int destinationEnd,
                       int[] counts, int[] bases, float x, float y, float z) {}
    public final List<Span> spans = new ArrayList<>();
    public final int stride;
    public int vertices, commands, maxElements;
    public long copiedBytes;
    public MergePlan(int stride) {
        if (stride <= 0 || stride > 256) throw new IllegalArgumentException("unknown stride");
        this.stride = stride;
    }
    public void add(long bufferBytes, int[] counts, int[] bases, long[] pointers, float x, float y, float z) {
        if (spans.size() >= MAX_REGIONS || counts.length == 0 || counts.length != bases.length || counts.length != pointers.length
                || bufferBytes <= 0 || bufferBytes % stride != 0 || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
            throw new IllegalArgumentException("unknown extent/region/command contract");
        long start = Long.MAX_VALUE, end = 0;
        int max = maxElements;
        for (int i = 0; i < counts.length; i++) {
            // Exact native sequential quad IBO contract; never round/drop incomplete primitives.
            if (counts[i] <= 0 || counts[i] % 6 != 0 || bases[i] < 0 || pointers[i] != 0)
                throw new IllegalArgumentException("non-native opaque index contract");
            long last = bases[i] + (counts[i] / 6L) * 4;
            if (last > bufferBytes / stride) throw new IllegalArgumentException("source beyond buffer extent");
            start = Math.min(start, bases[i]); end = Math.max(end, last); max = Math.max(max, counts[i]);
        }
        long length = end - start;
        long padded = (length + PAGE_VERTICES - 1) & -((long) PAGE_VERTICES);
        long newVertices = vertices + padded, newCommands = (long) commands + counts.length;
        if (newVertices > MAX_VERTICES || newVertices * stride > MAX_BYTES || newCommands > MAX_COMMANDS)
            throw new IllegalArgumentException("bounded capacity exceeded");
        int[] rebased = bases.clone();
        for (int i = 0; i < rebased.length; i++) {
            long value = vertices + ((long) bases[i] - start);
            if (value < vertices || value + (counts[i] / 6L) * 4 > vertices + length || value > Integer.MAX_VALUE)
                throw new IllegalArgumentException("rebase overflow");
            rebased[i] = (int) value;
        }
        spans.add(new Span(Math.toIntExact(start), Math.toIntExact(end), vertices, (int) newVertices,
                counts.clone(), rebased, x, y, z));
        vertices = (int) newVertices; commands = (int) newCommands; maxElements = max;
        copiedBytes += length * stride;
    }
    public int pages() { return vertices >>> PAGE_SHIFT; }
    public int[] pageWords() {
        int[] words = new int[pages() * 4];
        for (Span span : spans) for (int page = span.destinationStart >>> PAGE_SHIFT; page < span.destinationEnd >>> PAGE_SHIFT; page++) {
            words[page * 4] = Float.floatToRawIntBits(span.x);
            words[page * 4 + 1] = Float.floatToRawIntBits(span.y);
            words[page * 4 + 2] = Float.floatToRawIntBits(span.z);
        }
        return words;
    }
}
