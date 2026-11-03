package com.pp.shaderlod.optimization;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ComplementaryReceiverAdmissionTest {
    @TempDir Path directory;

    @Test void rejectedCombinationsNeverReadSourceAndEligibleDrawsAlwaysRevalidate() {
        for (String quality : Arrays.asList(null, "1", "2", "3"))
            for (boolean reuseEnabled : List.of(false, true))
                for (boolean reuseRequested : List.of(false, true))
                    for (String reuseLoaded : Arrays.asList(null, "0", "1"))
                        for (boolean rateEnabled : List.of(false, true))
                            for (boolean rateRequested : List.of(false, true))
                                for (String rateLoaded : Arrays.asList(null, "0", "1")) {
            boolean active = ComplementaryReceiverAdmission.spatialActive(reuseEnabled, reuseRequested, reuseLoaded)
                || ComplementaryReceiverAdmission.spatialActive(rateEnabled, rateRequested, rateLoaded);
            boolean wanted = "2".equals(quality)
                && !(reuseEnabled && reuseRequested && "1".equals(reuseLoaded))
                && !(rateEnabled && rateRequested && "1".equals(rateLoaded));
            var sourceCalls = new AtomicInteger();
            for (int frame = 0; frame < 3; frame++) {
                assertEquals(wanted, ComplementaryReceiverAdmission.validate(quality, active, () -> {
                    sourceCalls.incrementAndGet(); return true;
                }));
            }
            assertEquals(wanted ? 3 : 0, sourceCalls.get(), "No source memo or work on refused combinations");
            assertFalse(ComplementaryReceiverAdmission.validate(quality, active, () -> false),
                "An eligible setting can never override a failed source proof");
        }
    }

    @Test void rejectedToEligibleTransitionCannotReuseSourceBeforeSameStatEdit() throws Exception {
        Path file = directory.resolve("source.zip");
        Files.write(file, new byte[]{1,2,3,4});
        var time = Files.getLastModifiedTime(file);
        String expected = SourceArchiveDigest.sha256(file);
        var reads = new AtomicInteger();
        java.util.function.BooleanSupplier proof = () -> {
            reads.incrementAndGet();
            try { return expected.equals(SourceArchiveDigest.sha256(file)); }
            catch (Exception failure) { return false; }
        };
        assertTrue(ComplementaryReceiverAdmission.validate("2", false, proof));
        assertFalse(ComplementaryReceiverAdmission.validate("3", false, proof));
        assertFalse(ComplementaryReceiverAdmission.validate("2", true, proof));
        assertEquals(1, reads.get());
        Files.write(file, new byte[]{1,2,9,4});
        Files.setLastModifiedTime(file, time);
        assertEquals(4, Files.size(file));
        assertEquals(time, Files.getLastModifiedTime(file));
        assertFalse(ComplementaryReceiverAdmission.validate("2", false, proof));
        assertEquals(2, reads.get(), "Re-entry must read fresh bytes");
        Files.write(file, new byte[]{1,2,3,4});
        assertTrue(ComplementaryReceiverAdmission.validate("2", false, proof));
        assertThrows(IllegalStateException.class, () -> ComplementaryReceiverAdmission.validate("2", false,
            () -> { throw new IllegalStateException("source unavailable"); }));
    }

}
