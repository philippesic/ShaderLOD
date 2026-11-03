package com.pp.shaderlod;

import com.pp.shaderlod.optimization.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaderOptimizationProfilesG03Test {
    @Test void readyWorldNeverNormalizesEditedNativeQuality(){
        var nativeOptions=com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.nativeOptions();
        assertTrue(ShaderOptimizationProfiles.postSampleAlphaOptions(nativeOptions));
        var edited=new java.util.HashMap<>(nativeOptions);
        edited.put("AA_TYPE","0");
        assertFalse(ShaderOptimizationProfiles.postSampleAlphaOptions(edited));
        assertFalse(ShaderOptimizationProfiles.postSampleAlphaOptions(java.util.Map.of()));
    }
    @Test void readyWorldPreparationRequiresTheAsahiSingletonPerformanceRequest(){
        var flags=OptimizationSnapshot.noneRequested().with(OptimizationId.EARLY_ALPHA,true);
        var core=new CoreUniformFrame.Core(new LodSettings(true,32,96,8,false),LodPreset.PERFORMANCE);
        assertTrue(ShaderOptimizationProfiles.postSampleAlphaProfile("Linux","aarch64","makeup-ultra-fast-shaders",core,flags));
        for(String os:new String[]{"Mac OS X","Windows 11"})
            assertFalse(ShaderOptimizationProfiles.postSampleAlphaProfile(os,"aarch64","makeup-ultra-fast-shaders",core,flags));
        assertFalse(ShaderOptimizationProfiles.postSampleAlphaProfile("Linux","amd64","makeup-ultra-fast-shaders",core,flags));
        assertFalse(ShaderOptimizationProfiles.postSampleAlphaProfile("Linux","aarch64","bsl-shaders",core,flags));
        assertFalse(ShaderOptimizationProfiles.postSampleAlphaProfile("Linux","aarch64","makeup-ultra-fast-shaders",null,flags));
        assertFalse(ShaderOptimizationProfiles.postSampleAlphaProfile("Linux","aarch64","makeup-ultra-fast-shaders",
            new CoreUniformFrame.Core(new LodSettings(false,32,96,8,false),LodPreset.PERFORMANCE),flags));
        for(LodPreset preset:LodPreset.values())if(preset!=LodPreset.PERFORMANCE)
            assertFalse(ShaderOptimizationProfiles.postSampleAlphaProfile("Linux","aarch64","makeup-ultra-fast-shaders",
                new CoreUniformFrame.Core(core.settings(),preset),flags));
        assertFalse(ShaderOptimizationProfiles.postSampleAlphaProfile("Linux","aarch64","makeup-ultra-fast-shaders",core,OptimizationSnapshot.noneRequested()));
        for(var id:OptimizationId.values())if(id!=OptimizationId.EARLY_ALPHA)
            assertFalse(ShaderOptimizationProfiles.postSampleAlphaProfile("Linux","aarch64","makeup-ultra-fast-shaders",core,flags.with(id,true)));
    }
}
