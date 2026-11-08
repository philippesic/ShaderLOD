package com.pp.shaderlod.dev.timing.costmap;

import java.math.BigInteger;
import static com.pp.shaderlod.dev.timing.costmap.CostMapRecords.*;

/** Pure decoding seam for a later backend. Values are unsigned raw ticks; no GL access.
 * One modular wrap is accepted only with an independently established maximum interval below
 * the counter modulus. Resets/disjoints require a clock generation change; never guess from ticks.
 */
public final class CounterDelta {
    private CounterDelta() {}
    public record Result(Long elapsedNs, Outcome outcome) {
        public Result { duration(outcome, elapsedNs); }
    }
    public static Result decode(long begin, long end, Clock issueClock, Clock resultClock,
                                long maxIntervalNs) {
        require(maxIntervalNs >= 0, "maximum interval");
        if (!issueClock.equals(resultClock)) return invalid("clock reset/domain change/disjoint");
        BigInteger modulus = BigInteger.ONE.shiftLeft(issueClock.counterBits());
        BigInteger a = unsigned(begin), b = unsigned(end);
        if (a.compareTo(modulus) >= 0 || b.compareTo(modulus) >= 0) return invalid("counter outside declared width");
        BigInteger bound = BigInteger.valueOf(maxIntervalNs);
        if (bound.compareTo(modulus) >= 0) return invalid("wrap count ambiguous");
        BigInteger delta = b.subtract(a).mod(modulus);
        if (delta.compareTo(bound) > 0 || delta.bitLength() > 63) return invalid("counter interval exceeds bound");
        return new Result(delta.longValueExact(), new Outcome(Status.MEASURED, "bounded modular nanosecond counter"));
    }
    private static BigInteger unsigned(long value) { return new BigInteger(Long.toUnsignedString(value)); }
    private static Result invalid(String reason) { return new Result(null, new Outcome(Status.INVALID, reason)); }
}
