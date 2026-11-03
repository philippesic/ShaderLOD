package com.pp.shaderlod.optimization.bsl;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Resource admission stays independent of OS; the shader source keeps exact API/version gates. */
final class BslReceiverPcAdmissionTest {
    private static BslReceiverContract.Sampler sampler(int width,int format,int bits,int type,
            int compare,int function,int min,int mag,int base,int wrap,float anisotropy) {
        return new BslReceiverContract.Sampler(7,width,2048,format,bits,type,compare,function,min,mag,base,wrap,wrap,anisotropy);
    }
    @Test void acceptsSupportedActualDepthStorageWithoutAnOperatingSystemAssumption() {
        for (int[] storage:new int[][]{{0x81A5,16,0x8C17},{0x81A6,24,0x8C17},{0x81A7,32,0x8C17},{0x8CAC,32,0x1406}})
            assertTrue(BslReceiverContract.depth(sampler(2048,storage[0],storage[1],storage[2],0x884E,0x0203,0x2601,0x2601,0,0x812F,1)));
    }
    @Test void refusesUnknownOrChangedSamplerContracts() {
        assertFalse(BslReceiverContract.depth(null));
        int[][] mutations={{1024,0x81A6,24,0x8C17,0x884E,0x0203,0x2601,0x2601,0,0x812F},
            {2048,0x81A6,16,0x8C17,0x884E,0x0203,0x2601,0x2601,0,0x812F},
            {2048,0x81A6,24,0x8C17,0,0x0203,0x2601,0x2601,0,0x812F},
            {2048,0x81A6,24,0x8C17,0x884E,0x0201,0x2601,0x2601,0,0x812F},
            {2048,0x81A6,24,0x8C17,0x884E,0x0203,0x2703,0x2601,0,0x812F},
            {2048,0x81A6,24,0x8C17,0x884E,0x0203,0x2601,0x2600,0,0x812F},
            {2048,0x81A6,24,0x8C17,0x884E,0x0203,0x2601,0x2601,1,0x812F},
            {2048,0x81A6,24,0x8C17,0x884E,0x0203,0x2601,0x2601,0,0x2901}};
        for(int[] v:mutations)assertFalse(BslReceiverContract.depth(sampler(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],v[9],1)));
        assertFalse(BslReceiverContract.depth(sampler(2048,0x81A6,24,0x8C17,0x884E,0x0203,0x2601,0x2601,0,0x812F,2)));
        assertFalse(BslReceiverContract.depth(sampler(2048,0x81A6,24,0x8C17,0x884E,0x0203,0x2601,0x2601,0,0x812F,Float.NaN)));
    }
    @Test void requiresActualNativeViewport() {
        assertTrue(BslReceiverContract.viewport(0,0,1920,1200,1920,1200));
        assertFalse(BslReceiverContract.viewport(0,0,960,600,1920,1200));
        assertFalse(BslReceiverContract.viewport(1,0,1920,1200,1920,1200));
        assertFalse(BslReceiverContract.viewport(0,0,1920,1200,Float.NaN,1200));
    }
}
