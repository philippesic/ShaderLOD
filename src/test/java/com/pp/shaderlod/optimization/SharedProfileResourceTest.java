package com.pp.shaderlod.optimization;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Run on both loaders: the real strict reader must consume its packaged complete profile. */
class SharedProfileResourceTest {
    @Test void packagedAstraMapReachesItsStrictReader() {
        var map=com.pp.shaderlod.optimization.astraao.AstraAoContract.generated("0");
        assertEquals(1027,map.size());assertTrue(com.pp.shaderlod.optimization.astraao.AstraAoContract.options(map));
    }
    @Test void packagedSildurMapReachesItsStrictPixelReaderAndRejectsMissingControl() {
        var map=com.pp.shaderlod.optimization.sildurpixel.SildurPixelContract.generated("0");
        assertEquals(131,map.size());assertTrue(com.pp.shaderlod.optimization.sildurpixel.SildurPixelContract.options(map));
        var bad=new TreeMap<>(map);bad.remove(com.pp.shaderlod.optimization.sildurpixel.SildurPixelContract.OPTION);
        assertFalse(com.pp.shaderlod.optimization.sildurpixel.SildurPixelContract.options(bad));
    }
    @Test void bothPackagedSolasMaterialMapsReachTheirStrictPixelReader() {
        for(var map:List.of(com.pp.shaderlod.optimization.solaspixel.SolasPixelContract.generated("0"),com.pp.shaderlod.optimization.solaspixel.SolasPixelContract.pbrGenerated("0"))) {
            assertEquals(438,map.size());assertTrue(com.pp.shaderlod.optimization.solaspixel.SolasPixelContract.options(map));
            var bad=new TreeMap<>(map);bad.remove(com.pp.shaderlod.optimization.solaspixel.SolasPixelContract.OPTION);
            assertFalse(com.pp.shaderlod.optimization.solaspixel.SolasPixelContract.options(bad));
        }
    }
}
