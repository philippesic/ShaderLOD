package com.pp.shaderlod.dev;

/** Untimed observation transaction. Never leave rollback sources in its receipts. */
final class ObservedPackReload {
    /** Capability and dimension gate only; the caller also checks ownership, Off and exact bytes. */
    static boolean permitsSelection(boolean blissCoverage,boolean privateReload,boolean mipRuntime,String dimension) {
        return blissCoverage&&"minecraft:overworld".equals(dimension)
            ||privateReload&&mipRuntime&&("minecraft:overworld".equals(dimension)||"minecraft:the_nether".equals(dimension));
    }
    @FunctionalInterface interface Step { void run() throws Exception; }
    static void run(Step arm, Step mutation, Step disarm, Step rollback) throws Exception {
        arm.run();
        try { mutation.run(); }
        catch (Exception failure) {
            try { disarm.run(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            try { rollback.run(); } catch (Exception recovery) { failure.addSuppressed(recovery); }
            throw failure;
        }
    }
}
