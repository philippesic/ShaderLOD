package com.pp.shaderlod.optimization.astraspatial;

import java.lang.reflect.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.pp.shaderlod.optimization.DepthTextureStorage.*;

/** Raw6402 is observed on the PC; precision/type cases below are queried-value fixtures, not driver claims. */
class AstraDepthStorageTest {
    @Test void unsizedDepthMapsOnlyProvenPrecisionAndRepresentation() {
        int[] bits={16,24,32};int[] formats={0x81A5,0x81A6,0x81A7};
        for(int i=0;i<bits.length;i++) {
            var storage=com.pp.shaderlod.optimization.DepthTextureStorage.resolve(6402,bits[i],UNSIGNED_NORMALIZED);
            assertNotNull(storage);assertEquals(formats[i],storage.privateFormat());
            assertEquals(6402,storage.rawFormat());assertEquals(bits[i],storage.depthBits());assertEquals(UNSIGNED_NORMALIZED,storage.depthType());
        }
        assertEquals(0x8CAC,com.pp.shaderlod.optimization.DepthTextureStorage.resolve(6402,32,FLOAT).privateFormat());
        for(int precision=-1;precision<=64;precision++) {
            assertEquals(precision==16||precision==24||precision==32,com.pp.shaderlod.optimization.DepthTextureStorage.resolve(6402,precision,UNSIGNED_NORMALIZED)!=null);
            assertEquals(precision==32,com.pp.shaderlod.optimization.DepthTextureStorage.resolve(6402,precision,FLOAT)!=null);
            for(int type:new int[]{0,-1,0x8F9C,0x1404,0x1405,0x1403,0x1402,0x140A})
                assertNull(com.pp.shaderlod.optimization.DepthTextureStorage.resolve(6402,precision,type));
        }
    }
    @Test void sizedDepthAndStencilRetainExactPreviousAllocationFormat() {
        for(int format:new int[]{0x81A5,0x81A6,0x81A7,0x8CAC,0x88F0,0x8CAD}) {
            var storage=com.pp.shaderlod.optimization.DepthTextureStorage.resolve(format,0,0);
            assertNotNull(storage);assertEquals(format,storage.privateFormat());assertEquals(format,storage.rawFormat());
        }
        for(int format:new int[]{0,-1,0x1908,0x8814,0x84F9})assertNull(com.pp.shaderlod.optimization.DepthTextureStorage.resolve(format,24,UNSIGNED_NORMALIZED));
    }
    @Test void bothActualFieldOwnersRetainRawIdentityAndSizedAllocation()throws Exception {
        var observed=com.pp.shaderlod.optimization.DepthTextureStorage.resolve(6402,24,UNSIGNED_NORMALIZED);
        var sized=com.pp.shaderlod.optimization.DepthTextureStorage.resolve(0x81A6,0,0);
        assertEquals(observed.privateFormat(),sized.privateFormat());assertNotEquals(observed,sized,"Same private allocation never hides a raw representation mutation");
        assertNotEquals(observed,com.pp.shaderlod.optimization.DepthTextureStorage.resolve(6402,32,UNSIGNED_NORMALIZED));
        assertNotEquals(com.pp.shaderlod.optimization.DepthTextureStorage.resolve(6402,32,UNSIGNED_NORMALIZED),com.pp.shaderlod.optimization.DepthTextureStorage.resolve(6402,32,FLOAT));
        for(String owner:new String[]{"AstraSpatialRuntime","AstraWorldPixelRuntime"}) {
            Class<?> fields=Class.forName("com.pp.shaderlod.optimization.astraspatial."+owner+"$Fields");
            Constructor<?> ctor=owner.equals("AstraSpatialRuntime")?fields.getDeclaredConstructor(int.class,int.class,com.pp.shaderlod.optimization.DepthTextureStorage.Storage.class):fields.getDeclaredConstructor(int.class,int.class,com.pp.shaderlod.optimization.DepthTextureStorage.Storage.class,int[].class);
            ctor.setAccessible(true);Object instance=owner.equals("AstraSpatialRuntime")?ctor.newInstance(3840,2160,observed):ctor.newInstance(3840,2160,observed,new int[]{0x8814,0x8814,0x8814,0x8814});
            Field raw=fields.getDeclaredField("nativeStorage"),format=fields.getDeclaredField("format");raw.setAccessible(true);format.setAccessible(true);
            assertEquals(observed,raw.get(instance));assertEquals(0x81A6,format.getInt(instance));
        }
    }
}
