package com.pp.shaderlod.optimization.bslspatial;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BslComparisonSamplerSelectionTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
    @Test void hardwareComparisonAliasWinsOverCanonicalRawName() {
        for(String name:new String[]{"shadowtex0","shadowtex1"}) {
            assertEquals(name+"HW",BslSpatialRuntime.comparisonSampler(name,true));
            assertEquals(name,BslSpatialRuntime.comparisonSampler(name,false));
        }
        assertThrows(IllegalArgumentException.class,()->BslSpatialRuntime.comparisonSampler("shadowcolor0",true));
    }
    @Test void pinnedNativeAndNormalGeneratedLightingUseShadowComparisonTypes()throws Exception {
        Path original=directory.resolve("Original.zip");
        java.nio.file.Files.copy(Path.of("dependencies/upstream/other-pack-research/BSL_v10.1.8.zip"),original);
        var generated=new com.pp.shaderlod.packs.PackSelectionService().apply(directory,"Original.zip",true,
            com.pp.shaderlod.LodPreset.PERFORMANCE,com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions(),false);
        assertTrue(generated.accepted(),generated.detail());
        assertTrue(generated.generatedArchive(),"Test requires the current real producer");
        String[][] inputs={
            {"dependencies/upstream/other-pack-research/BSL_v10.1.8.zip","b7cecede848a68f6118cdb1ad1c14dfaae32c7fbcdb82f3a11d14bf9274c064a"},
            {directory.resolve(generated.packFile()).toString(),"9cfe4134c3ba01da34b20ead9a34c2f4507aa289d701301d48ee20a582d4cc4f"}
        };
        for(String[] input:inputs)try(var zip=new ZipFile(Path.of(input[0]).toFile());var in=zip.getInputStream(zip.getEntry("shaders/lib/lighting/shadows.glsl"))) {
            byte[] bytes=in.readAllBytes();assertEquals(input[1],HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            String source=new String(bytes,java.nio.charset.StandardCharsets.UTF_8).replaceAll("(?m)//[^\\r\\n]*","");
            assertTrue(source.contains("uniform sampler2DShadow shadowtex0;"));assertTrue(source.contains("uniform sampler2DShadow shadowtex1;"));
            assertFalse(source.contains("uniform sampler2D shadowtex0;"));assertFalse(source.contains("uniform sampler2D shadowtex1;"));
            assertTrue(source.contains("texture2DShadow(sampler2DShadow shadowtex, vec3 shadowPos)"));assertTrue(source.contains("shadow2D(shadowtex, shadowPos).x"));
        }
    }
}
