package com.pp.shaderlod.packs;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.optimization.mips.BslLeanGraph;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MakeUpAsahiAlphaFallbackTest {
    private static BslLeanGraph.Route route(boolean exact,boolean overworld){
        return new BslLeanGraph.Route(OptimizationSnapshot.noneRequested().with(OptimizationId.EARLY_ALPHA,true),
            true,"makeup-ultra-fast-shaders",exact,overworld);
    }
    @Test void unsupportedCompilerDimensionAndEditedQualityRetainOriginal(){
        var nativeOptions=MakeUpQuadEligibility.nativeOptions();
        assertFalse(PackSelectionService.asahiAlphaOriginalFallback("Linux","aarch64","makeup-ultra-fast-shaders",
            true,LodPreset.PERFORMANCE,nativeOptions,route(true,true),false));
        for(var r:new BslLeanGraph.Route[]{route(false,true),route(true,false)})
            assertTrue(PackSelectionService.asahiAlphaOriginalFallback("Linux","aarch64","makeup-ultra-fast-shaders",
                true,LodPreset.PERFORMANCE,nativeOptions,r,false));
        var edited=new HashMap<>(nativeOptions);edited.put("AA_TYPE","0");
        assertTrue(PackSelectionService.asahiAlphaOriginalFallback("Linux","aarch64","makeup-ultra-fast-shaders",
            true,LodPreset.PERFORMANCE,edited,route(true,true),false));
    }
    @Test void macAndOtherRequestsKeepExistingPolicy(){
        assertFalse(PackSelectionService.asahiAlphaOriginalFallback("Mac OS X","aarch64","makeup-ultra-fast-shaders",
            true,LodPreset.PERFORMANCE,MakeUpQuadEligibility.nativeOptions(),route(false,true),false));
        var peer=route(false,true).requests().with(OptimizationId.AO_ATTENUATION,true);
        assertFalse(PackSelectionService.asahiAlphaOriginalFallback("Linux","aarch64","makeup-ultra-fast-shaders",
            true,LodPreset.PERFORMANCE,MakeUpQuadEligibility.nativeOptions(),
            new BslLeanGraph.Route(peer,true,"makeup-ultra-fast-shaders",false,true),false));
    }
}
