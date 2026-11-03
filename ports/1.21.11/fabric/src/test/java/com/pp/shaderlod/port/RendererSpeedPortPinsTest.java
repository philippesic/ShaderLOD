package com.pp.shaderlod.port;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadApiPins;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Authentic release bytes, then independent missing/corrupt seam-resource failures. */
class RendererSpeedPortPinsTest {
    private static void addJar(byte[] bytes, Map<String,byte[]> resources) throws IOException {
        try (var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry=zip.getNextEntry())!=null) {
                byte[] data=zip.readAllBytes();
                if (entry.getName().endsWith("-mod.jar")) addJar(data,resources);
                else if (entry.getName().endsWith(".class")) resources.put(entry.getName(),data);
            }
        }
    }
    private static ClassLoader loader(Map<String,byte[]> resources) {
        return new ClassLoader(null) {
            public InputStream getResourceAsStream(String name) {
                byte[] bytes=resources.get(name);
                return bytes==null ? null : new ByteArrayInputStream(bytes);
            }
        };
    }
    @Test void exactReleaseAndIndependentSeamFailures() throws Exception {
        var resources=new HashMap<String,byte[]>();
        for (String file : List.of("iris-fabric-1.10.8+mc1.21.11.jar", "sodium-fabric-0.8.14+mc1.21.11.jar"))
            addJar(Files.readAllBytes(Path.of("ports/1.21.11/runtime",file)),resources);
        assertTrue(MakeUpQuadApiPins.resourcesMatch(loader(resources)));
        String key="net/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch.class";
        byte[] original=resources.remove(key);
        assertFalse(MakeUpQuadApiPins.resourcesMatch(loader(resources)));
        byte[] changed=original.clone(); changed[changed.length-1]^=1;
        resources.put(key,changed);
        assertFalse(MakeUpQuadApiPins.resourcesMatch(loader(resources)));
        resources.put(key,original);
        assertTrue(MakeUpQuadApiPins.resourcesMatch(loader(resources)));
    }
    @Test void offQualityUnloadedAndMismatchStayNative() {
        assertFalse(MakeUpQuadRuntime.configAllows(false,true,LodPreset.PERFORMANCE));
        assertFalse(MakeUpQuadRuntime.configAllows(true,false,LodPreset.PERFORMANCE));
        assertFalse(MakeUpQuadRuntime.configAllows(true,true,LodPreset.QUALITY));
        assertFalse(MakeUpQuadRuntime.configAllows(true,true,null));
        assertFalse(MakeUpQuadRuntime.stackMatches(Map.of()));
        var changed=new TreeMap<>(MakeUpQuadRuntime.REQUIRED_STACK);
        changed.put("shaderlod","0.1.0"); changed.put("sodium","unreviewed");
        assertFalse(MakeUpQuadRuntime.stackMatches(changed));
        assertFalse(MakeUpQuadRuntime.resourcesMatch(List.of("vanilla","file/unreviewed.zip")));
    }
}
