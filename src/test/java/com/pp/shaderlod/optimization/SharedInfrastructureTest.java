package com.pp.shaderlod.optimization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;
import com.pp.shaderlod.packs.PackSelectionService;
import com.pp.shaderlod.LodPreset;
final class SharedInfrastructureTest {
    @Test void persistenceRecoveryClocksQueryOwnershipAndBracket() throws Exception {SharedInfrastructureChecksMain.main(new String[0]);}
    @TempDir Path directory;
    @Test void solasAndAstraActualOriginalSelectionRetainsNativeOnlyWithAvailabilityAndCpuRequests() throws Exception {
        var previous=OptimizationConfig.state();
        var pair=com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested()
            .with(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION,true)
            .with(com.pp.shaderlod.optimization.OptimizationId.SHADOW_ADMISSION_CACHE,true);
        com.pp.shaderlod.optimization.OptimizationConfig.load(directory.resolve("requests.properties"));
        try {
            for(String family:java.util.List.of("solas-shader","astralex")){
                var arm=Files.createDirectory(directory.resolve(family));
                var source=Path.of(family.equals("solas-shader")
                    ?"dependencies/upstream/pack-expansion/Solas Shader V3.7b.zip"
                    :"dependencies/upstream/pack-expansion/§r§lAstra§4§lLex§r§l_By_LexBoosT_§4§lV93.0§r§l.zip");
                var nativeOptions=family.equals("solas-shader")
                    ?com.pp.shaderlod.integration.solasquad.SolasQuadEligibility.nativeOptions()
                    :com.pp.shaderlod.integration.astraquad.AstraQuadEligibility.nativeOptions();
                assertEquals(family.equals("solas-shader")?428:1015,nativeOptions.size());
                Path original=arm.resolve(source.getFileName());Files.copy(source,original);
                assertTrue(family.equals("solas-shader")
                    ?com.pp.shaderlod.integration.solasquad.SolasQuadEligibility.validate(original,nativeOptions).eligible()
                    :com.pp.shaderlod.integration.astraquad.AstraQuadEligibility.validate(original,nativeOptions).eligible());
                var invalid=new HashMap<>(nativeOptions);invalid.put("TAA","unknown");
                assertFalse(family.equals("solas-shader")
                    ?com.pp.shaderlod.integration.solasquad.SolasQuadEligibility.validate(original,invalid).eligible()
                    :com.pp.shaderlod.integration.astraquad.AstraQuadEligibility.validate(original,invalid).eligible());
                byte[] originalBytes=Files.readAllBytes(original);var service=new PackSelectionService();
                com.pp.shaderlod.optimization.OptimizationConfig.replace(pair);
                var on=service.apply(arm,original.getFileName().toString(),true,LodPreset.PERFORMANCE,nativeOptions,true);
                assertTrue(on.accepted(),on.detail());assertEquals(original.getFileName().toString(),on.packFile());
                assertFalse(on.generatedArchive());try(var files=Files.list(arm)){assertEquals(1,files.count());}
                for(boolean enabled:new boolean[]{true,false}){
                    var nativeReturn=service.apply(arm,on.packFile(),enabled,LodPreset.QUALITY,nativeOptions,true);
                    assertTrue(nativeReturn.accepted(),nativeReturn.detail());assertEquals(on.packFile(),nativeReturn.packFile());
                }
                var unavailable=service.apply(arm,on.packFile(),true,LodPreset.PERFORMANCE,nativeOptions,false);
                assertTrue(unavailable.accepted(),unavailable.detail());assertNotEquals(on.packFile(),unavailable.packFile());
                var recovered=service.apply(arm,unavailable.packFile(),false,LodPreset.QUALITY,Map.of(),false);
                assertTrue(recovered.accepted(),recovered.detail());assertEquals(on.packFile(),recovered.packFile());
                com.pp.shaderlod.optimization.OptimizationConfig.replace(pair.with(com.pp.shaderlod.optimization.OptimizationId.AO_BUDGET,true));
                var glsl=service.apply(arm,on.packFile(),true,LodPreset.PERFORMANCE,nativeOptions,true);
                assertTrue(glsl.accepted(),glsl.detail());assertNotEquals(on.packFile(),glsl.packFile());
                var quality=service.apply(arm,glsl.packFile(),true,LodPreset.QUALITY,Map.of(),true);
                assertTrue(quality.accepted(),quality.detail());assertEquals(on.packFile(),quality.packFile());
                assertArrayEquals(originalBytes,Files.readAllBytes(original));
            }
        } finally {OptimizationConfig.restoreState(previous);}
    }
}
