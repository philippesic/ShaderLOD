package com.pp.shaderlod.optimization.makeupspatial;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure actual snapshot/policy helpers; no GL location or driver admission is fabricated. */
class MakeUpSpatialRateInputUniformTest {
    @Test void absenceUsesActualLocationPresenceAndCannotAliasIntegerMinusOneOrFloatNegativeZero() {
        var absent=MakeUpSpatialRuntime.stageUniformValue(-1,0);
        assertEquals(List.of(0,0),absent);
        assertNotEquals(absent,MakeUpSpatialRuntime.stageUniformValue(0,-1));
        assertNotEquals(absent,MakeUpSpatialRuntime.stageUniformValue(7,Float.floatToRawIntBits(-0.0f)));
        assertEquals(List.of(1,Integer.MIN_VALUE),MakeUpSpatialRuntime.stageUniformValue(7,Float.floatToRawIntBits(-0.0f)));
    }
    @Test void rateInputUnusedFieldsRetainTheirStageAndWorldRestrictions() {
        for(String name:List.of("ditherShift","shaderlodOptShadowSampleBudget","shaderlodOptTemporalShadowFilter","shaderlodOptShadowReceiverFootprint")) {
            var expected=MakeUpSpatialRuntime.stageUniformValue(1,0);var absent=MakeUpSpatialRuntime.stageUniformValue(-1,0);
            assertTrue(MakeUpSpatialRuntime.rateInputUniformMatches(name,true,false,false,expected,absent),name);
            assertFalse(MakeUpSpatialRuntime.rateInputUniformMatches(name,false,false,false,expected,absent),"Reuse producer "+name);
            assertTrue(MakeUpSpatialRuntime.rateInputUniformMatches(name,true,true,false,expected,absent),"World input closure also omits "+name);
            assertFalse(MakeUpSpatialRuntime.rateInputUniformMatches(name,true,false,true,expected,absent),"Rate lighting "+name);
            for(int wrong:new int[]{-1,Integer.MIN_VALUE,1,Float.floatToRawIntBits(-0.0f)})
                assertFalse(MakeUpSpatialRuntime.rateInputUniformMatches(name,true,false,false,expected,MakeUpSpatialRuntime.stageUniformValue(1,wrong)),"Present wrong value "+name+"/"+wrong);
            assertTrue(MakeUpSpatialRuntime.rateInputUniformMatches(name,true,false,false,expected,expected));
        }
    }
    @Test void onlyFourSourceUnusedFieldsMayBeAbsentInWorldInputWithPresentValuesStillStrict() {
        var expected=MakeUpSpatialRuntime.stageUniformValue(1,0);var absent=MakeUpSpatialRuntime.stageUniformValue(-1,0);
        for(String name:List.of("ditherShift","shaderlodOptShadowSampleBudget","shaderlodOptTemporalShadowFilter","shaderlodOptShadowReceiverFootprint")) {
            assertTrue(MakeUpSpatialRuntime.rateInputUniformMatches(name,true,true,false,expected,absent),name);
            assertFalse(MakeUpSpatialRuntime.rateInputUniformMatches(name,true,true,true,expected,absent),"World lighting still consumes shadow policy "+name);
            assertFalse(MakeUpSpatialRuntime.rateInputUniformMatches(name,false,true,false,expected,absent),"No reduced input role "+name);
            for(int wrong:new int[]{-1,Integer.MIN_VALUE,1})assertFalse(MakeUpSpatialRuntime.rateInputUniformMatches(name,true,true,false,expected,
                MakeUpSpatialRuntime.stageUniformValue(1,wrong)),"Present wrong world flag "+name+"/"+wrong);
        }
        for(String required:List.of("shaderlodEffectsVersion","shaderlodOpaqueLightingMode","shaderlodOpaqueLightingNear",
                "shaderlodOpaqueLightingFar","shaderlodOpaqueLightingTransition","shaderlodOptPixelScale","frameCounter"))
            assertFalse(MakeUpSpatialRuntime.rateInputUniformMatches(required,true,true,false,expected,absent),required);
    }
    @Test void oneCapturedSnapshotReportsEveryIndependentMismatchWithoutChangingAcceptance() {
        var expected=new java.util.TreeMap<String,List<Integer>>();
        expected.put("shaderlodEffectsVersion",List.of(2));expected.put("shaderlodOpaqueLightingMode",List.of(1,0));
        expected.put("ditherShift",List.of(1,1054867456));expected.put("shaderlodOptShadowSampleBudget",List.of(1,0));
        var observed=new java.util.TreeMap<>(expected);observed.put("ditherShift",List.of(0,0));observed.put("shaderlodOptShadowSampleBudget",List.of(0,0));
        assertNull(MakeUpSpatialRuntime.uniformFailures(expected,observed,true,true,false));
        observed.put("shaderlodEffectsVersion",List.of(1));observed.put("shaderlodOpaqueLightingMode",List.of(1,-1));
        String failure=MakeUpSpatialRuntime.uniformFailures(expected,observed,true,true,false);assertNotNull(failure);
        assertTrue(failure.contains("shaderlodEffectsVersion expected=[2] actual=[1]"));
        assertTrue(failure.contains("shaderlodOpaqueLightingMode expected=[1, 0] actual=[1, -1]"));
        assertFalse(failure.contains("ditherShift"));assertFalse(failure.contains("shaderlodOptShadowSampleBudget"));
        observed.put("ditherShift",List.of(1,Integer.MIN_VALUE));
        assertTrue(MakeUpSpatialRuntime.uniformFailures(expected,observed,true,true,false).contains("ditherShift expected=[1, 1054867456] actual=[1, -2147483648]"));
    }
    @Test void selectedFlagEffectsAndLightingPolicyRemainStrict() {
        for(String required:List.of("shaderlodEffectsVersion","shaderlodOpaqueLightingMode","shaderlodOpaqueLightingNear",
                "shaderlodOpaqueLightingFar","shaderlodOpaqueLightingMediumWorkRatio","shaderlodOpaqueLightingFarWorkRatio",
                "shaderlodOptSpatialLightingRate","shaderlodBridgeVersion","shaderlodEnabled","shaderlodOptimizationFlagsVersion","shaderlodPreset","frameCounter"))
            assertFalse(MakeUpSpatialRuntime.rateInputUniformMatches(required,true,false,false,List.of(1,2),List.of(0,0)),required);
    }
    @Test void onlyReducedLightingMayOmitTheAlreadyConsumedPolicyModeAndPresentValuesRemainExact() {
        for(int mode:new int[]{0,1,2}) {
            var expected=MakeUpSpatialRuntime.stageUniformValue(3,mode);var absent=MakeUpSpatialRuntime.stageUniformValue(-1,0);
            assertTrue(MakeUpSpatialRuntime.rateLightingUniformMatches("shaderlodOpaqueLightingMode",true,false,true,expected,absent));
            assertFalse(MakeUpSpatialRuntime.rateLightingUniformMatches("shaderlodOpaqueLightingMode",true,false,false,expected,absent),"Input eligibility still consumes policy");
            assertTrue(MakeUpSpatialRuntime.rateLightingUniformMatches("shaderlodOpaqueLightingMode",true,true,true,expected,absent),"World-RGB lighting also consumes already eligible input images");
            assertFalse(MakeUpSpatialRuntime.rateLightingUniformMatches("shaderlodOpaqueLightingMode",true,true,false,expected,absent),"World input eligibility still consumes policy");
            assertFalse(MakeUpSpatialRuntime.rateLightingUniformMatches("shaderlodOpaqueLightingMode",false,false,true,expected,absent));
            assertTrue(MakeUpSpatialRuntime.rateLightingUniformMatches("shaderlodOpaqueLightingMode",true,false,true,expected,expected));
            for(boolean world:new boolean[]{false,true}) {
                for(int wrong:new int[]{-1,Integer.MIN_VALUE,3})assertFalse(MakeUpSpatialRuntime.rateLightingUniformMatches(
                    "shaderlodOpaqueLightingMode",true,world,true,expected,MakeUpSpatialRuntime.stageUniformValue(3,wrong)),"Present wrong integer cannot alias absence");
                assertFalse(MakeUpSpatialRuntime.rateLightingUniformMatches("shaderlodOpaqueLightingMode",true,world,true,expected,
                    MakeUpSpatialRuntime.stageUniformValue(3,(mode+1)%3)),"Present different valid mode still refused");
            }
        }
        for(String required:List.of("shaderlodEffectsVersion","shaderlodOptSpatialLightingRate","shaderlodBridgeVersion","shaderlodEnabled",
                "shaderlodOptimizationFlagsVersion","shaderlodPreset","frameCounter","ditherShift","shaderlodOptShadowSampleBudget"))
            assertFalse(MakeUpSpatialRuntime.rateLightingUniformMatches(required,true,false,true,List.of(1,2),List.of(0,0)),required);
    }
    private static String functionBody(String source,String name) {
        int start=source.indexOf(name+"(");assertTrue(start>=0,name);start=source.indexOf('{',start);int depth=1,end=start+1;
        while(depth>0){char c=source.charAt(end++);if(c=='{')depth++;else if(c=='}')depth--;}
        return source.substring(start+1,end-1);
    }
    @Test void actualPinnedRateEvaluatorConsumesCapturedInputsAndRetainsRequiredProtocol()throws Exception {
        try(var recipe=getClass().getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/makeup-ultra-fast-shaders.json")) {
            assertNotNull(recipe);var manifest=com.google.gson.JsonParser.parseString(new String(recipe.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var pin=manifest.getAsJsonObject("additions").getAsJsonObject("shaders/lib/shaderlod/makeup_spatial_inputs.glsl");
            try(var input=getClass().getResourceAsStream("/assets/shaderlod/adapters/"+pin.get("path").getAsString())) {
                assertNotNull(input);byte[] bytes=input.readAllBytes();assertEquals(pin.get("sha256").getAsString(),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
                String source=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
                String rate=functionBody(source,"shaderlodMakeupSpatialRateEvaluate");
                assertFalse(rate.contains("shaderlodOpaqueLightingMode"));assertFalse(rate.contains("shaderlodMakeupSpatialEligible"));assertFalse(rate.contains("shaderlodMakeupSpatialDistance"));
                assertTrue(rate.contains("shaderlodEffectsVersion!=2"));assertTrue(rate.contains("shaderlodOptSpatialLightingRate"));assertTrue(rate.contains("frameCounter"));
                assertTrue(rate.contains("texelFetch2D(colortex8"));assertTrue(rate.contains("get_colored_shadow"));
                assertTrue(functionBody(source,"shaderlodMakeupSpatialExportInputs").contains("shaderlodMakeupSpatialEligible"));
                String export=functionBody(source,"shaderlodMakeupSpatialExportInputs");
                assertTrue(export.contains("#if SHADERLOD_MAKEUP_SPATIAL_RATE == 1 || SHADERLOD_MAKEUP_WORLD_PIXEL == 1\n#if SHADERLOD_MAKEUP_WORLD_PIXEL == 1"),"World and rate inputs select the same non-lighting branch");
                int nativeLighting=export.indexOf("#else\n    ivec2 pixel=");assertTrue(nativeLighting>0,"Native shadow-lighting branch remains separate");
                String captured=export.substring(0,nativeLighting),lighting=export.substring(nativeLighting);
                assertFalse(captured.contains("get_colored_shadow("));assertFalse(captured.contains("get_shadow("));
                assertTrue(captured.contains("shaderlodMakeupFog("),"World retains native fog capture");
                assertFalse(java.util.regex.Pattern.compile("\\bdither\\b").matcher(captured).find(),"Neither input branch consumes the unused dither parameter");
                assertTrue(lighting.contains("get_colored_shadow("));assertTrue(lighting.contains("get_shadow("));
                assertTrue(functionBody(source,"shaderlodMakeupSpatialDistance").contains("shaderlodOpaqueLightingMode"));
            }
            String nativeSource=null;
            for(var patch:manifest.getAsJsonArray("patches")) {
                var object=patch.getAsJsonObject();if(object.has("after")&&object.get("after").getAsString().contains("#include \"/lib/shaderlod/makeup_world_pixel.glsl\""))nativeSource=object.get("after").getAsString();
            }
            assertNotNull(nativeSource);String prefix=nativeSource.substring(nativeSource.indexOf("void main()"),nativeSource.indexOf("float block_luma"));
            assertTrue(prefix.contains("#if defined DISTANT_HORIZONS && !defined GBUFFER_BEACONBEAM"));
            prefix=prefix.replaceAll("(?s)#if defined DISTANT_HORIZONS && !defined GBUFFER_BEACONBEAM.*?#endif","").replaceAll("float dither = [^;]*;","");
            assertFalse(java.util.regex.Pattern.compile("\\bdither\\b").matcher(prefix).find(),"Admitted non-DH terrain prefix has no remaining dither use");
            assertFalse(java.util.regex.Pattern.compile("\\bfrogAdjust\\s*=").matcher(prefix).find(),"Fog factor remains a captured vertex input");
            for(String key:List.of("shaders/lib/shaderlod/early_alpha.glsl","shaders/lib/shaderlod/makeup_spatial_bridge.glsl")) {
                var helperPin=manifest.getAsJsonObject("additions").getAsJsonObject(key);
                try(var input=getClass().getResourceAsStream("/assets/shaderlod/adapters/"+helperPin.get("path").getAsString())) {
                    assertNotNull(input);byte[] bytes=input.readAllBytes();assertEquals(helperPin.get("sha256").getAsString(),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
                    String source=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
                    assertFalse(functionBody(source,key.contains("early_alpha")?"shaderlodMakeupFog":"shaderlodMakeupWorldFog").contains("dither"),"Actual native fog helper has no dither dependency");
                }
            }
            var worldPin=manifest.getAsJsonObject("additions").getAsJsonObject("shaders/lib/shaderlod/makeup_world_pixel.glsl");
            try(var input=getClass().getResourceAsStream("/assets/shaderlod/adapters/"+worldPin.get("path").getAsString())) {
                assertNotNull(input);byte[] bytes=input.readAllBytes();assertEquals(worldPin.get("sha256").getAsString(),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
                String source=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
                for(String name:List.of("shaderlodMakeupWorldSize","shaderlodMakeupWorldLo")) {
                    String body=functionBody(source,name);assertFalse(body.contains("shaderlodOpaqueLightingMode"));assertFalse(body.contains("shaderlodMakeupSpatialEligible"));assertFalse(body.contains("shaderlodMakeupSpatialDistance"));
                }
                assertTrue(functionBody(source,"shaderlodMakeupWorldTry").contains("shaderlodMakeupSpatialEligible"),"Forward world consumer retains the policy check; only its removed lighting closure may omit Mode");
            }
        }
    }
}
