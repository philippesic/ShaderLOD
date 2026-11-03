package com.pp.shaderlod.tuning;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TuningBandsTest {
    @Test void everyRawUserFieldIsPinnedWithoutClampingOrRestoration() {
        var captured=new TuningBands(32,96,8,false);assertDoesNotThrow(()->captured.requireSame(new TuningBands(32,96,8,false)));
        for(var edit:new TuningBands[]{new TuningBands(33,96,8,false),new TuningBands(32,97,8,false),new TuningBands(32,96,9,false),new TuningBands(32,96,8,true)}) {
            assertThrows(IllegalStateException.class,()->captured.requireSame(edit));
            assertNotEquals(captured,edit); // The immutable edit is not silently restored to the old value.
        }
        assertThrows(IllegalArgumentException.class,()->new TuningBands(32,31,8,false));
        assertThrows(IllegalArgumentException.class,()->new TuningBands(32,96,Double.NaN,false));
    }
}
