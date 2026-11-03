package com.pp.shaderlod.dev;

/** Exact-object comparison for one native-entry program and its completed outer method scope. */
final class NativeSubmitIdentityGuard {
    private NativeSubmitIdentityGuard() {}
    record Snapshot(Thread thread, long context, Object capabilities, Object pipeline, Object pack,
                    long epoch, Object receipt, Object program, int handle, Object pass,
                    Object source, Object certificate) {}
    static boolean same(Snapshot a, Snapshot b) {
        return a != null && b != null && a.thread == b.thread && a.context == b.context
                && a.capabilities == b.capabilities && a.pipeline == b.pipeline && a.pack == b.pack
                && a.epoch == b.epoch && a.receipt == b.receipt && a.program == b.program
                && a.handle == b.handle && a.pass == b.pass && a.source == b.source
                && a.certificate == b.certificate;
    }
}
