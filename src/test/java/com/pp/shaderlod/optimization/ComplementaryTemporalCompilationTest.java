package com.pp.shaderlod.optimization;
import java.util.*;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;
import com.pp.shaderlod.optimization.unboundearlyalpha.*;
import com.pp.shaderlod.optimization.reimaginedearlyalpha.*;
import net.irisshaders.iris.gl.blending.*;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ComplementaryTemporalCompilationTest {
    private boolean profile(String family,Map<String,String> options){return family.equals("Unbound")?UnboundTemporalCompiledPrograms.profile(options):ReimaginedTemporalCompiledPrograms.profile(options);}
    private boolean transformed(String family,String text,AlphaTest alpha){return family.equals("Unbound")?UnboundTemporalCompiledPrograms.acceptsTransformed(text,alpha):ReimaginedTemporalCompiledPrograms.acceptsTransformed(text,alpha);}
    @Test void temporalAdmissionRetainsExactProfilesAndDoesNotNormalizeCallerQuality() throws Exception {
        try(var in=getClass().getResourceAsStream("/assets/shaderlod/rendererquad/generated-main-options.json")){
            var all=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            for(String family:List.of("Unbound","Reimagined")){
                var actual=new TreeMap<String,String>();all.getAsJsonObject("complementary-"+family.toLowerCase()).entrySet().forEach(e->actual.put(e.getKey(),e.getValue().getAsString()));
                for(String quality:List.of("2","3","4","5")){
                    actual.put("SHADOW_QUALITY",quality);var before=Map.copyOf(actual);
                    assertTrue(profile(family,actual),family+quality);assertEquals(before,actual);
                    assertEquals(quality.equals("2"),com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("complementary-"+family.toLowerCase(),actual),"alpha stays quality 2");
                }
                actual.put("RP_MODE","0");assertFalse(profile(family,actual));actual.put("RP_MODE","1");
                for(String bad:List.of("0","1","6")){actual.put("SHADOW_QUALITY",bad);assertFalse(profile(family,actual));}
                actual.put("SHADOW_QUALITY","3");actual.put("UNKNOWN","1");assertFalse(profile(family,actual));
            }
        }
    }
    @Test void solidAlwaysAndCutoutGreaterOnly(){
        var greater=new AlphaTest(AlphaTestFunction.GREATER,.1f);
        assertTrue(UnboundTemporalCompiledPrograms.acceptsPass(SodiumPrograms.Pass.TERRAIN,AlphaTest.ALWAYS));
        assertTrue(ReimaginedTemporalCompiledPrograms.acceptsPass(SodiumPrograms.Pass.TERRAIN_CUTOUT,greater));
        assertFalse(UnboundTemporalCompiledPrograms.acceptsPass(SodiumPrograms.Pass.TERRAIN,greater));
        assertFalse(ReimaginedTemporalCompiledPrograms.acceptsPass(SodiumPrograms.Pass.TERRAIN_CUTOUT,AlphaTest.ALWAYS));
        for(float ref:new float[]{Float.NaN,-1,2})assertFalse(UnboundTemporalCompiledPrograms.acceptsPass(SodiumPrograms.Pass.TERRAIN_CUTOUT,new AlphaTest(AlphaTestFunction.GREATER,ref)));
    }
    @Test void missingReceiverCapabilityDoesNotBlockTemporalButLateDiscardAndTemporalCapabilityAreRequired(){
        var greater=new AlphaTest(AlphaTestFunction.GREATER,.1f);
        for(String family:List.of("Unbound","Reimagined")){
            String protocol="uniform int shaderlod"+family+"TemporalShadowSamplingQualified; uniform int shaderlodOptTemporalShadowFilter;\n";
            String nativeCutout=protocol+"uniform float iris_currentAlphaTest; void main(){"+greater.toExpression("iris_FragData0.a","iris_currentAlphaTest","")+"}";
            assertFalse(nativeCutout.contains("ReceiverPermission"));
            assertTrue(transformed(family,protocol+"void main(){}",AlphaTest.ALWAYS));
            assertTrue(transformed(family,nativeCutout,greater));
            assertFalse(transformed(family,protocol+"void main(){}",greater));
            assertFalse(transformed(family,nativeCutout.replace("TemporalShadowSamplingQualified","missing"),greater));
            String stub="bool shaderlod"+family+"NativeAlphaReject(float finalAlpha){return false;}\n";
            assertTrue(transformed(family,stub+nativeCutout,greater));
            String known="uniform int shaderlod"+family+"EarlyAlphaPermission; uniform float shaderlod"+family+"EarlyAlphaReference; uniform int shaderlodOptEarlyAlpha; // textureLod\n"+
                "bool shaderlod"+family+"NativeAlphaReject(float finalAlpha){if(!(finalAlpha>shaderlod"+family+"EarlyAlphaReference)){return true;}return false;}\n";
            assertTrue(transformed(family,known+nativeCutout,greater));
            assertFalse(transformed(family,stub.replace("return false","return true")+nativeCutout,greater));
        }
    }
}
