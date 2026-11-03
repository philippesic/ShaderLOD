package com.pp.shaderlod.optimization;

import com.google.gson.JsonParser;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.packs.PackSelectionService;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real public producer; source binding contract only, not linked-uniform or driver proof. */
class BslSeparateSamplerSourceJoinTest {
    @TempDir Path directory;
    private static String member(ZipFile zip,String name)throws Exception {
        try(var in=zip.getInputStream(zip.getEntry(name))){return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
    }
    @Test void generatedNativeComparisonAliasesAreIndependentOfFieldBitAndRawHelpersStayRaw()throws Exception {
        var prior=OptimizationConfig.state();
        Path original=Path.of("dependencies/upstream/other-pack-research/BSL_v10.1.8.zip");byte[] originalBytes=Files.readAllBytes(original);
        try {
            OptimizationConfig.restoreInMemory(OptimizationSnapshot.noneRequested().with(OptimizationId.BINDING_CERTIFICATE_CACHE,true));
            Files.copy(original,directory.resolve("Original.zip"));
            var nativeOptions=new TreeMap<String,String>();
            try(var in=getClass().getResourceAsStream("/assets/shaderlod/rendererquad/generated-main-options.json")) {
                assertNotNull(in);var family=JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("bsl-shaders");
                family.entrySet().forEach(e->{if(!e.getKey().startsWith("SHADERLOD_"))nativeOptions.put(e.getKey(),e.getValue().getAsString());});
            }
            var result=new PackSelectionService().apply(directory,"Original.zip",true,LodPreset.PERFORMANCE,nativeOptions,false);
            assertTrue(result.accepted(),result.detail());assertTrue(result.generatedArchive());
            try(var zip=new ZipFile(directory.resolve(result.packFile()).toFile())) {
                String aliases="#undef shadowtex0\n#undef shadowtex1\n#define shadowtex0 shadowtex0HW\n#define shadowtex1 shadowtex1HW\nuniform sampler2DShadow shadowtex0;";
                for(String path:List.of("shaders/lib/lighting/shadows.glsl","shaders/program/composite.glsl"))assertTrue(member(zip,path).contains(aliases),path);
                var declarationOwners=new TreeSet<String>();
                for(var entry:Collections.list(zip.entries()))if(entry.getName().startsWith("shaders/")&&entry.getName().endsWith(".glsl")) {
                    String source=member(zip,entry.getName()).replaceAll("(?m)//[^\\r\\n]*","");
                    if(source.contains("uniform sampler2DShadow shadowtex0;")||source.contains("uniform sampler2DShadow shadowtex1;"))declarationOwners.add(entry.getName());
                }
                assertEquals(Set.of("shaders/lib/lighting/shadows.glsl","shaders/program/composite.glsl"),declarationOwners,"Every native comparison declaration owner gets the alias before its downstream callers");
                String properties=member(zip,"shaders/shaders.properties");
                assertTrue(properties.contains("iris.features.required=HIGHER_SHADOWCOLOR SEPARATE_HARDWARE_SAMPLERS\n#if SHADERLOD_BSL_SHADOW_FIELD == 1\nprogram.world0/shadowcomp1.enabled=true"));
                String raw=member(zip,"shaders/lib/shaderlod/bsl_shadow_raw_query_r1.glsl");
                assertTrue(raw.indexOf("#undef shadowtex0")<raw.indexOf("uniform sampler2D shadowtex0;"));
                assertTrue(raw.indexOf("texelFetch(shadowtex0,")<raw.lastIndexOf("#define shadowtex0 shadowtex0HW"));
                String producer=member(zip,"shaders/lib/shaderlod/bsl_shadow_field_producer_r1.glsl");
                assertTrue(producer.contains("uniform sampler2D shadowtex0;"));assertFalse(producer.contains("#define shadowtex0"));
                assertFalse(producer.contains("/lib/lighting/shadows.glsl"));
                assertTrue(member(zip,"shaders/program/composite.glsl").indexOf(aliases)<member(zip,"shaders/program/composite.glsl").indexOf("/lib/atmospherics/lightShafts.glsl"));
            }
            assertArrayEquals(originalBytes,Files.readAllBytes(directory.resolve("Original.zip")));assertArrayEquals(originalBytes,Files.readAllBytes(original));
        }finally{OptimizationConfig.restoreState(prior);}
    }
}
