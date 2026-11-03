package com.pp.shaderlod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Bounded, no-skip journal of writes made during an explicitly scoped shadow traversal.
 * Local completeness means the recorder did not lose observed writes; it is not a
 * producer, input, lifetime, transformed-hook or traversal-reuse certificate.
 */
public final class ShadowTraversalTouches {
    public static final String PROPERTY = "shaderlod.dev.shadowTraversalCertificate";
    public static final int STAMP_WRITE = 1;
    public static final int DIRECTION_WRITE = 2;
    public static final int DEFAULT_MAX_SECTIONS = 65_536;
    public static final int DEFAULT_MAX_WRITES = 1_048_576;

    public enum Writer {
        STAMP_SET(1), DIRECTION_SET(2), DIRECTION_UNION(4);
        private final int mask;
        Writer(int mask) { this.mask = mask; }
        public int mask() { return mask; }
    }

    public enum Invalidity {
        DISABLED, UNKNOWN_OWNER, INVALID_BOUND, NESTED_CAPTURE, WRONG_THREAD,
        WRONG_SCOPE, ALREADY_FINISHED, UNKNOWN_SECTION, UNKNOWN_WRITER,
        SECTION_LIMIT, WRITE_LIMIT, DISPOSED_SECTION, FOREIGN_FRAME, ABORTED
    }

    public enum CoverageGap { TRANSFORMED_HOOK_EXECUTION, EXTERNAL_WRITERS, POST_CAPTURE_LIFETIME }

    private static final Set<CoverageGap> COVERAGE_GAPS = Collections.unmodifiableSet(
            EnumSet.allOf(CoverageGap.class));
    private static final ThreadLocal<Capture> ACTIVE = new ThreadLocal<>();

    private ShadowTraversalTouches() { }

    /** Snapshot fields contain copied primitives; the section is an opaque identity, not copied native state. */
    public static final class Entry {
        private final Object section;
        private final int writeMask, writerMask, stampWrites, directionWrites;
        private final long firstWrite, lastWrite;
        private final OptionalInt stamp, incomingDirections;

        private Entry(MutableEntry entry) {
            section = entry.section;
            writeMask = entry.writeMask;
            writerMask = entry.writerMask;
            stampWrites = entry.stampWrites;
            directionWrites = entry.directionWrites;
            firstWrite = entry.firstWrite;
            lastWrite = entry.lastWrite;
            stamp = (writeMask & STAMP_WRITE) == 0 ? OptionalInt.empty() : OptionalInt.of(entry.stamp);
            incomingDirections = (writeMask & DIRECTION_WRITE) == 0
                    ? OptionalInt.empty() : OptionalInt.of(entry.incomingDirections);
        }

        public Object section() { return section; }
        public int writeMask() { return writeMask; }
        public int writerMask() { return writerMask; }
        public int stampWrites() { return stampWrites; }
        public int directionWrites() { return directionWrites; }
        /** Zero-based global write ordinals, with entries in first-touch order. */
        public long firstWrite() { return firstWrite; }
        public long lastWrite() { return lastWrite; }
        public OptionalInt stamp() { return stamp; }
        /** Empty for nearby-only stamp writes, even if the native section already has direction bits. */
        public OptionalInt incomingDirections() { return incomingDirections; }
    }

    public static final class Snapshot {
        private final Object traversalOwner;
        private final Thread thread;
        private final int frame;
        private final long observedWrites;
        private final List<Entry> entries;
        private final Set<Invalidity> invalidities;

        private Snapshot(Capture capture) {
            traversalOwner = capture.traversalOwner;
            thread = capture.thread;
            frame = capture.frame;
            observedWrites = capture.observedWrites;
            var copy = new ArrayList<Entry>(capture.order.size());
            for (var entry : capture.order) copy.add(new Entry(entry));
            entries = List.copyOf(copy);
            invalidities = Collections.unmodifiableSet(EnumSet.copyOf(capture.invalidities));
        }

        public Object traversalOwner() { return traversalOwner; }
        public int frame() { return frame; }
        public long observedWrites() { return observedWrites; }
        public List<Entry> entries() { return entries; }
        public Set<Invalidity> invalidities() { return invalidities; }
        public boolean complete() { return invalidities.isEmpty(); }
        public Set<CoverageGap> coverageGaps() { return COVERAGE_GAPS; }
    }

    /** Opaque owner-thread token. Always finish or abort it in the traversal's finally block. */
    public static final class Capture {
        private final Object traversalOwner;
        private final Thread thread = Thread.currentThread();
        private final int frame, maxSections, maxWrites;
        private final IdentityHashMap<Object, MutableEntry> sections = new IdentityHashMap<>();
        private final ArrayList<MutableEntry> order = new ArrayList<>();
        private final EnumSet<Invalidity> invalidities = EnumSet.noneOf(Invalidity.class);
        private long observedWrites;
        private boolean finished;

        private Capture(Object traversalOwner, int frame, int maxSections, int maxWrites) {
            this.traversalOwner = traversalOwner;
            this.frame = frame;
            this.maxSections = maxSections;
            this.maxWrites = maxWrites;
        }
    }

    private static final class MutableEntry {
        private final Object section;
        private final long firstWrite;
        private long lastWrite;
        private int writeMask, writerMask, stampWrites, directionWrites, stamp, incomingDirections;
        private MutableEntry(Object section, long ordinal) { this.section = section; firstWrite = ordinal; }
    }

    public static Capture begin(Object traversalOwner, int frame) {
        return begin(traversalOwner, frame, DEFAULT_MAX_SECTIONS, DEFAULT_MAX_WRITES);
    }

    /** Only the actual shadow traversal boundary may open this scope; main traversals must not call it. */
    public static Capture begin(Object traversalOwner, int frame, int maxSections, int maxWrites) {
        var capture = new Capture(traversalOwner, frame, maxSections, maxWrites);
        var previous = ACTIVE.get();
        if (previous != null) {
            synchronized (previous) { previous.invalidities.add(Invalidity.NESTED_CAPTURE); }
            capture.invalidities.add(Invalidity.NESTED_CAPTURE);
        }
        if (!Boolean.getBoolean(PROPERTY)) capture.invalidities.add(Invalidity.DISABLED);
        if (traversalOwner == null) capture.invalidities.add(Invalidity.UNKNOWN_OWNER);
        if (maxSections < 1 || maxSections > DEFAULT_MAX_SECTIONS
                || maxWrites < 1 || maxWrites > DEFAULT_MAX_WRITES) {
            capture.invalidities.add(Invalidity.INVALID_BOUND);
        }
        // A rejected nested capture does not displace or close the original thread's scope.
        if (capture.invalidities.isEmpty()) ACTIVE.set(capture);
        return capture;
    }

    public static boolean isCapturing() { return ACTIVE.get() != null; }

    /** Called after the native stamp setter, with its actual stored value. */
    public static void recordStamp(Object section, int storedFrame, boolean disposed) {
        record(section, storedFrame, disposed, Writer.STAMP_SET);
    }

    /** Called after the native setter/union, with the actual stored value, never just the union argument. */
    public static void recordDirections(Object section, int storedDirections, boolean disposed, Writer writer) {
        if (writer == Writer.STAMP_SET) {
            markIncomplete(Invalidity.UNKNOWN_WRITER);
            return;
        }
        record(section, storedDirections, disposed, writer);
    }

    public static void markIncomplete(Invalidity reason) {
        var capture = ACTIVE.get();
        if (capture != null) synchronized (capture) {
            capture.invalidities.add(reason == null ? Invalidity.UNKNOWN_WRITER : reason);
        }
    }

    /** An active deletion is a lifetime failure even if that section was not reached by the culler. */
    public static void recordDisposal(Object section) {
        markIncomplete(section == null ? Invalidity.UNKNOWN_SECTION : Invalidity.DISPOSED_SECTION);
    }

    private static void record(Object section, int storedValue, boolean disposed, Writer writer) {
        var capture = ACTIVE.get();
        if (capture == null) return;
        synchronized (capture) {
            if (capture.finished) { capture.invalidities.add(Invalidity.ALREADY_FINISHED); return; }
            if (capture.thread != Thread.currentThread()) {
                capture.invalidities.add(Invalidity.WRONG_THREAD);
                return;
            }
            if (section == null) { capture.invalidities.add(Invalidity.UNKNOWN_SECTION); return; }
            if (writer == null) { capture.invalidities.add(Invalidity.UNKNOWN_WRITER); return; }
            if (disposed) capture.invalidities.add(Invalidity.DISPOSED_SECTION);
            if (writer == Writer.STAMP_SET && storedValue != capture.frame) {
                capture.invalidities.add(Invalidity.FOREIGN_FRAME);
            }
            if (capture.observedWrites >= capture.maxWrites) {
                capture.invalidities.add(Invalidity.WRITE_LIMIT);
                return;
            }
            long ordinal = capture.observedWrites++;
            var entry = capture.sections.get(section);
            if (entry == null) {
                if (capture.order.size() >= capture.maxSections) {
                    capture.invalidities.add(Invalidity.SECTION_LIMIT);
                    return;
                }
                entry = new MutableEntry(section, ordinal);
                capture.sections.put(section, entry);
                capture.order.add(entry);
            }
            entry.lastWrite = ordinal;
            entry.writerMask |= writer.mask;
            if (writer == Writer.STAMP_SET) {
                entry.writeMask |= STAMP_WRITE;
                entry.stampWrites++;
                entry.stamp = storedValue;
            } else {
                entry.writeMask |= DIRECTION_WRITE;
                entry.directionWrites++;
                entry.incomingDirections = storedValue;
            }
        }
    }

    /** A wrong-thread finish marks the token incomplete but never clears another thread's context. */
    public static Snapshot finish(Capture capture) {
        if (capture == null) throw new IllegalArgumentException("Capture token is required");
        synchronized (capture) {
            if (capture.thread != Thread.currentThread()) {
                capture.invalidities.add(Invalidity.WRONG_THREAD);
                return new Snapshot(capture);
            }
            if (capture.finished) capture.invalidities.add(Invalidity.ALREADY_FINISHED);
            if (ACTIVE.get() == capture) ACTIVE.remove();
            else if (capture.invalidities.isEmpty()) capture.invalidities.add(Invalidity.WRONG_SCOPE);
            capture.finished = true;
            var snapshot = new Snapshot(capture);
            capture.sections.clear();
            capture.order.clear();
            return snapshot;
        }
    }

    public static void abort(Capture capture) {
        if (capture == null) return;
        synchronized (capture) { capture.invalidities.add(Invalidity.ABORTED); }
        finish(capture);
    }

    /** Pure test/diagnostic adapter. There is deliberately no native RenderSection implementation. */
    public interface ReplayTarget {
        boolean isLive(Object section);
        void setVisibilityStamp(Object section, int frame);
        void setIncomingDirections(Object section, int directions);
    }

    public enum ReplayResult { APPLIED, INCOMPLETE, WRONG_THREAD, ACTIVE_CAPTURE, INVALID_TARGET }

    /**
     * Validates all identities before writing, retags only recorded stamps and writes only
     * recorded directions. This replays final state, not intermediate setter chronology.
     * Adapter exceptions propagate: a failed adapter is never reported as an applied replay.
     */
    public static ReplayResult replay(Snapshot snapshot, int currentFrame, ReplayTarget target) {
        if (snapshot == null || !snapshot.complete()) return ReplayResult.INCOMPLETE;
        if (snapshot.thread != Thread.currentThread()) return ReplayResult.WRONG_THREAD;
        if (isCapturing()) return ReplayResult.ACTIVE_CAPTURE;
        if (target == null) return ReplayResult.INVALID_TARGET;
        for (var entry : snapshot.entries) if (!target.isLive(entry.section)) return ReplayResult.INVALID_TARGET;
        for (var entry : snapshot.entries) {
            if (entry.stamp.isPresent()) target.setVisibilityStamp(entry.section, currentFrame);
            if (entry.incomingDirections.isPresent()) {
                target.setIncomingDirections(entry.section, entry.incomingDirections.getAsInt());
            }
        }
        return ReplayResult.APPLIED;
    }
}
