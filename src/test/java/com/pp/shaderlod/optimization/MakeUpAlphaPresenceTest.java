package com.pp.shaderlod.optimization;

import com.pp.shaderlod.*;
import com.pp.shaderlod.optimization.CompileOptionControl.*;
import com.pp.shaderlod.optimization.makeupao.MakeUpAoContract;
import com.pp.shaderlod.optimization.makeupearlyalpha.*;
import com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowCompiledPrograms;
import java.util.*;
import net.irisshaders.iris.gl.blending.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MakeUpAlphaPresenceTest {
    private static final String KEY=CompileOptionControl.MAKEUP_EARLY_ALPHA;
    private static String desired(OptimizationSnapshot flags,boolean on,LodPreset preset,String dimension,boolean dh,boolean voxy){
        var core=new CoreUniformFrame.Core(new LodSettings(on,32,96,8,false),preset);
        return CompileOptionControl.plan(Family.MAKEUP,core,flags,Set.of(KEY),Map.of(),dimension,dh,voxy,Map.of()).get(KEY);
    }
    @Test void presenceUsesOnlyTheSupportedRequestAndNeverCurrentOwners(){
        var alpha=OptimizationSnapshot.noneRequested().with(OptimizationId.EARLY_ALPHA,true);
        assertEquals("1",desired(alpha,true,LodPreset.PERFORMANCE,"minecraft:overworld",false,false));
        for(var id:OptimizationId.values()){
            var flags=OptimizationSnapshot.noneRequested().with(id,true);
            assertEquals(id==OptimizationId.EARLY_ALPHA?"1":"0",desired(flags,true,LodPreset.PERFORMANCE,"minecraft:overworld",false,false));
            assertEquals(id==OptimizationId.EARLY_ALPHA,CompileOptionControl.controlledOptions(id).contains(KEY));
        }
        assertEquals("0",desired(alpha,false,LodPreset.PERFORMANCE,"minecraft:overworld",false,false));
        assertEquals("0",desired(alpha,true,LodPreset.QUALITY,"minecraft:overworld",false,false));
        for(String dim:List.of("minecraft:the_nether","minecraft:the_end","custom:moon","unavailable"))
            assertEquals("0",desired(alpha,true,LodPreset.PERFORMANCE,dim,false,false));
        assertEquals("0",desired(alpha,true,LodPreset.PERFORMANCE,"minecraft:overworld",true,false));
        assertEquals("0",desired(alpha,true,LodPreset.PERFORMANCE,"minecraft:overworld",false,true));
    }
    @Test void actualIrisPreprocessorRemovesInactiveSamplingChoice() throws Exception {
        try(var input=getClass().getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/shared/makeup_early_alpha.glsl")) {
            assertNotNull(input);String helper=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            String function=helper.substring(helper.indexOf("bool shaderlodMakeupEarlyAlphaActive()"),helper.indexOf("// Prepared"));
            for(String mode:List.of("0","1")){
                String source=net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor.glslPreprocessSource(
                    "#define "+KEY+" "+mode+"\n"+function,com.google.common.collect.ImmutableList.of());
                assertEquals(mode.equals("0"),MakeUpOpaqueAlphaSpecialization.transformed(source));
            }
        }
    }
    @Test void retainedRequestReactivatesAfterUnsupportedDimensionRoundTrip() throws Exception {
        var path=net.irisshaders.iris.shaderpack.include.AbsolutePackPath.fromAbsolutePath("/test.glsl");
        var node=new net.irisshaders.iris.shaderpack.include.FileNode(path,com.google.common.collect.ImmutableList.of(
            "#define "+KEY+" 0 // [0 1]","#if "+KEY+" == 1","#endif"));
        var constructor=net.irisshaders.iris.shaderpack.include.IncludeGraph.class.getDeclaredConstructor(
            com.google.common.collect.ImmutableMap.class,com.google.common.collect.ImmutableMap.class);constructor.setAccessible(true);
        var graph=constructor.newInstance(com.google.common.collect.ImmutableMap.of(path,node),com.google.common.collect.ImmutableMap.of());
        var loaded0=new net.irisshaders.iris.shaderpack.option.ShaderPackOptions(graph,Map.of());
        var loaded1=new net.irisshaders.iris.shaderpack.option.ShaderPackOptions(graph,Map.of(KEY,"1"));
        var core=new CoreUniformFrame.Core(new LodSettings(true,32,96,8,false),LodPreset.PERFORMANCE);
        var requested=OptimizationSnapshot.noneRequested().with(OptimizationId.EARLY_ALPHA,true);
        assertTrue(CompileOptionControl.loadedGraphNeedsDimensionRollback(loaded1,"minecraft:the_nether"));
        assertFalse(CompileOptionControl.loadedGraphNeedsDimensionRollback(loaded0,"minecraft:overworld"));
        assertTrue(CompileOptionControl.makeUpAlphaWorldEntry(loaded0,"minecraft:overworld",core,requested));
        assertTrue(CompileOptionControl.dimensionCompileChange(KEY,"0",desired(requested,true,LodPreset.PERFORMANCE,"minecraft:overworld",false,false)));
        assertFalse(CompileOptionControl.makeUpAlphaWorldEntry(loaded1,"minecraft:overworld",core,requested));
        assertFalse(CompileOptionControl.makeUpAlphaWorldEntry(loaded0,"minecraft:the_nether",core,requested));
        assertFalse(CompileOptionControl.makeUpAlphaWorldEntry(loaded0,"minecraft:overworld",core,OptimizationSnapshot.noneRequested()));
        assertFalse(CompileOptionControl.makeUpAlphaWorldEntry(loaded0,"minecraft:overworld",new CoreUniformFrame.Core(core.settings(),LodPreset.QUALITY),requested));
        assertFalse(CompileOptionControl.dimensionCompileChange(CompileOptionControl.MAKEUP_DEPTH,"0","1"),"Entry repair is narrowly scoped");
    }
    @Test void binaryPeerDoesNotRelaxNativeOptions(){
        for(String mode:List.of("0","1")){
            var options=new TreeMap<>(MakeUpAoContract.generated("0"));options.put(KEY,mode);
            assertEquals(114,options.size());assertTrue(MakeUpAoContract.options(options));
            options.put("AA_TYPE","0");assertFalse(MakeUpAoContract.options(options));
        }
        for(String mode:List.of("2","true","")){
            var options=new TreeMap<>(MakeUpAoContract.generated("0"));options.put(KEY,mode);assertFalse(MakeUpAoContract.options(options));
        }
        var absent=new TreeMap<>(MakeUpAoContract.generated("0"));absent.remove(KEY);assertFalse(MakeUpAoContract.options(absent));
    }
    @Test void shadowCutoutMustMatchItsCapturedPresenceAndExactLateAlpha(){
        var alpha=new AlphaTest(AlphaTestFunction.GREATER,.1f);
        String nativeShape="uniform int shaderlodMakeupColoredShadowBudgetPermission;uniform int shaderlodOptShadowSampleBudget;"
            +"uniform int shaderlodMakeupEarlyAlphaPermission;uniform float shaderlodMakeupEarlyAlphaReference;uniform int shaderlodOptEarlyAlpha;"
            +"bool shaderlodMakeupEarlyAlphaActive(){return false;}bool shaderlodMakeupNativeAlphaReject(float finalAlpha){return false;}"
            +"vec4 fallback(vec3 p){return shadow2DLod(shadowtex1,p,0.0)+texture2DLod(gaux4,p.xy,0.0);}"
            +"uniform float iris_currentAlphaTest;void main(){"+alpha.toExpression("iris_FragData0.a","iris_currentAlphaTest","")+"}";
        assertTrue(MakeUpShadowCompiledPrograms.acceptsTransformed(nativeShape,alpha,"0"));
        assertFalse(MakeUpShadowCompiledPrograms.acceptsTransformed(nativeShape,alpha,"1"));
        assertFalse(MakeUpAlphaInterface.transformed(nativeShape,alpha),"Disabled alpha never earns early-alpha ownership");
        assertFalse(MakeUpShadowCompiledPrograms.acceptsTransformed(nativeShape.replace("iris_FragData0.a > iris_currentAlphaTest","iris_FragData0.a < iris_currentAlphaTest"),alpha,"0"));
        assertFalse(MakeUpShadowCompiledPrograms.acceptsTransformed(nativeShape.replace("return false;}","return true;}"),alpha,"0"));
        assertFalse(MakeUpShadowCompiledPrograms.acceptsTransformed(nativeShape,alpha,null));
        assertFalse(MakeUpShadowCompiledPrograms.acceptsTransformed(nativeShape,alpha,"2"));
    }
}
