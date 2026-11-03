package com.pp.shaderlod.optimization;

import com.google.gson.JsonParser;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.packs.PackSelectionService;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual current producers and packaged profiles; no captured runtime or GL claims. */
class ComplementaryProducerSourceTest {
    @TempDir Path directory;
    private static Map<String,String> profile(String path) throws Exception {
        try(var input=ComplementaryProducerSourceTest.class.getResourceAsStream("/assets/shaderlod/"+path)) {
            assertNotNull(input);
            var result=new TreeMap<String,String>();
            JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject().entrySet().forEach(e->result.put(e.getKey(),e.getValue().getAsString()));
            return result;
        }
    }
    private static boolean merged(String family,Path selected,Map<String,String> options) {
        return family.equals("reimagined")
            ? com.pp.shaderlod.optimization.reimaginedmerged.ReimaginedMergedLinkageGate.source(selected,options)
            : com.pp.shaderlod.optimization.unboundmerged.UnboundMergedLinkageGate.source(selected,options);
    }
    @Test void authenticCurrentSourceMutationQualityOffAndOptionalProfiles() throws Exception {
        var saved=OptimizationConfig.state();
        var scoped=PackSelectionService.class.getDeclaredField("recipeScopedSelections");scoped.setAccessible(true);
        boolean wasScoped=scoped.getBoolean(null);
        try {
            PackSelectionService.enableRecipeScopedSelections();
            OptimizationConfig.replace(OptimizationSnapshot.noneRequested().with(OptimizationId.SHADOW_RECEIVER_FOOTPRINT,true));
            for(String family:List.of("reimagined","unbound")) {
                var nativeOptions=profile(family+"quad/native398.json");
                var generated=new TreeMap<String,String>();
                try(var input=getClass().getResourceAsStream("/assets/shaderlod/rendererquad/generated-main-options.json")) {
                    assertNotNull(input);
                    JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject()
                        .getAsJsonObject("complementary-"+family).entrySet().forEach(e->generated.put(e.getKey(),e.getValue().getAsString()));
                }
                assertEquals(398,nativeOptions.size());assertEquals(411,generated.size());
                assertEquals(nativeOptions, family.equals("reimagined")
                    ? com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.nativeOptions()
                    : com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.nativeOptions());
                int[][] variants=family.equals("reimagined")?new int[][]{{1,-1},{2,-1},{3,-1}}:new int[][]{{1,-1},{4,1},{4,-1}};
                for(int[] variant:variants) {
                    Path packs=directory.resolve(family+"-"+variant[0]+"-"+variant[1]);Files.createDirectory(packs);
                    String name="Original.zip";
                    Files.copy(Path.of("dependencies/upstream/other-pack-research/Complementary"+(family.equals("reimagined")?"Reimagined":"Unbound")+"_r5.9.3.zip"),packs.resolve(name));
                    var expected=new TreeMap<>(nativeOptions);
                    if(family.equals("reimagined"))expected.put("RP_MODE",Integer.toString(variant[0]));
                    else {expected.put("SHADER_STYLE",Integer.toString(variant[0]));expected.put("CLOUD_STYLE_DEFINE",Integer.toString(variant[1]));}
                    var loaded=new TreeMap<>(generated);loaded.putAll(expected);
                    var service=new PackSelectionService();
                    var on=service.apply(packs,name,true,LodPreset.PERFORMANCE,expected,false);
                    assertTrue(on.accepted(),on.detail());assertTrue(on.generatedArchive());
                    Path selected=packs.resolve(on.packFile());
                    assertEquals("complementary-"+family,PackSelectionService.verifyGeneratedRendererGraph(selected,loaded));
                    // The renderer graph preserves optional native profiles, but
                    // merged CPU admission certifies only the packaged native398 map.
                    assertEquals(nativeOptions.equals(expected), merged(family,selected,loaded),
                        family+" variant="+Arrays.toString(variant));
                    byte[] original=Files.readAllBytes(selected),bad=original.clone();var stamp=Files.getLastModifiedTime(selected);
                    bad[bad.length/2]^=1;Files.write(selected,bad);Files.setLastModifiedTime(selected,stamp);
                    assertNull(PackSelectionService.verifyGeneratedRendererGraph(selected,loaded));
                    Files.write(selected,original);Files.setLastModifiedTime(selected,stamp);
                    var wrong=new TreeMap<>(loaded);wrong.put("TAA","false");
                    assertNull(PackSelectionService.verifyGeneratedRendererGraph(selected,wrong));
                    var quality=service.apply(packs,on.packFile(),true,LodPreset.QUALITY,loaded,false);
                    assertTrue(quality.accepted(),quality.detail());assertEquals(name,quality.packFile());
                    assertNull(PackSelectionService.verifyGeneratedRendererGraph(selected,loaded));
                    on=service.apply(packs,name,true,LodPreset.PERFORMANCE,expected,false);
                    assertTrue(on.accepted(),on.detail());
                    var off=service.apply(packs,on.packFile(),false,LodPreset.PERFORMANCE,loaded,false);
                    assertTrue(off.accepted(),off.detail());assertEquals(name,off.packFile());
                }
            }
        } finally { scoped.setBoolean(null,wasScoped);OptimizationConfig.restoreState(saved); }
    }
}
