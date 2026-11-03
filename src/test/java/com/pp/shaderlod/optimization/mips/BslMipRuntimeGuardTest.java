package com.pp.shaderlod.optimization.mips;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonParser;
import java.nio.file.*;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BslMipRuntimeGuardTest {
    private static String directive()throws Exception { return directive("composite7"); }
    private static String directive(String program)throws Exception {
        var recipe=JsonParser.parseString(Files.readString(Path.of("patches/adapters/bsl-shaders.json"))).getAsJsonObject();
        for(var item:recipe.getAsJsonArray("patches")) {
            var p=item.getAsJsonObject();if(!p.get("path").getAsString().equals("shaders/program/"+program+".glsl"))continue;
            String source=p.get("after").getAsString();return source.substring(source.indexOf("#ifdef LIGHT_SHAFT"),source.indexOf("//Common Functions//"));
        }
        throw new AssertionError("Missing actual c7 patch");
    }
    private static ImmutableList<StringPair> input(String request) {
        return ImmutableList.of(new StringPair("SHADERLOD_BSL_DEAD_MIP_CHAIN",request),
            new StringPair("IS_IRIS","1"),new StringPair("MC_VERSION","12101"),new StringPair("IRIS_VERSION","10812"),
            new StringPair("OVERWORLD","1"),new StringPair("LIGHT_SHAFT","1"),new StringPair("TAA","1"));
    }
    @Test void coldPersistedOneWithoutJavaHookKeepsNativeMipRequest()throws Exception {
        assertTrue(JcppProcessor.glslPreprocessSource(directive(),input("1")).contains("colortex1MipmapEnabled = true"));
        var admitted=BslMipRuntimeGuard.defines(input("1"),true);
        assertTrue(JcppProcessor.glslPreprocessSource(directive(),admitted).contains("colortex1MipmapEnabled = false"));
        assertTrue(JcppProcessor.glslPreprocessSource(directive(),BslMipRuntimeGuard.defines(input("0"),true)).contains("colortex1MipmapEnabled = true"));
        assertTrue(JcppProcessor.glslPreprocessSource(directive(),BslMipRuntimeGuard.defines(input("1"),false)).contains("colortex1MipmapEnabled = true"));
    }
    @Test void capabilityIsReservedAndInputRemainsImmutable()throws Exception {
        var input=ImmutableList.<StringPair>builder().addAll(input("1")).add(new StringPair(BslMipRuntimeGuard.DEFINE,"1")).build();
        var denied=BslMipRuntimeGuard.defines(input,false);assertEquals(input.size()-1,denied.size());
        assertTrue(JcppProcessor.glslPreprocessSource(directive(),denied).contains("colortex1MipmapEnabled = true"));
        assertEquals(1,input.stream().filter(v->v.key().equals(BslMipRuntimeGuard.DEFINE)).count());
        assertEquals(1,BslMipRuntimeGuard.defines(input,true).stream().filter(v->v.key().equals(BslMipRuntimeGuard.DEFINE)).count());
    }
    @Test void c6RequiresIndependentExperimentalCapabilityAndNativeGraph()throws Exception {
        var input=ImmutableList.<StringPair>builder().addAll(input("1")).add(new StringPair("FXAA","1")).build();
        String c6=directive("composite6");
        assertTrue(JcppProcessor.glslPreprocessSource(c6,BslMipRuntimeGuard.defines(input,true,false)).contains("colortex1MipmapEnabled = true"));
        assertTrue(JcppProcessor.glslPreprocessSource(c6,BslMipRuntimeGuard.defines(input,true,true)).contains("colortex1MipmapEnabled = false"));
        assertTrue(JcppProcessor.glslPreprocessSource(c6,BslMipRuntimeGuard.defines(input,false,true)).contains("colortex1MipmapEnabled = true"));
        var zero=ImmutableList.<StringPair>builder().addAll(input("0")).add(new StringPair("FXAA","1")).build();
        assertTrue(JcppProcessor.glslPreprocessSource(c6,BslMipRuntimeGuard.defines(zero,true,true)).contains("colortex1MipmapEnabled = true"));
        var wrongTuple=input.stream().map(v->v.key().equals("IRIS_VERSION")?new StringPair(v.key(),"10808"):v).collect(ImmutableList.toImmutableList());
        assertTrue(JcppProcessor.glslPreprocessSource(c6,BslMipRuntimeGuard.defines(wrongTuple,true,true)).contains("colortex1MipmapEnabled = true"));
        var spoof=ImmutableList.<StringPair>builder().addAll(input).add(new StringPair(BslMipRuntimeGuard.C6_DEFINE,"1")).build();
        var denied=BslMipRuntimeGuard.defines(spoof,true,false);
        assertTrue(denied.stream().noneMatch(v->v.key().equals(BslMipRuntimeGuard.C6_DEFINE)));
        assertTrue(JcppProcessor.glslPreprocessSource(c6,denied).contains("colortex1MipmapEnabled = true"));
        assertTrue(JcppProcessor.glslPreprocessSource(directive(),denied).contains("colortex1MipmapEnabled = false"));
    }

    @Test void leanProofReuseNeverCachesMutableActualOptions() {
        var pinned=DeadMipAdmission.leanProfile();
        assertFalse(pinned.isEmpty());
        assertSame(pinned,DeadMipAdmission.leanProfile());
        assertThrows(UnsupportedOperationException.class,()->pinned.put("TAA","false"));
        var actual=new java.util.HashMap<>(pinned);
        assertTrue(DeadMipAdmission.expectedLeanOptions(actual));
        actual.put("SHADERLOD_BSL_DEAD_MIP_CHAIN","1");
        assertTrue(DeadMipAdmission.expectedLeanOptions(actual));
        actual.put("ADVANCED_MATERIALS","true");
        assertTrue(DeadMipAdmission.expectedLeanOptions(actual));
        actual.put("PARALLAX_QUALITY","64");
        assertFalse(DeadMipAdmission.expectedLeanOptions(actual));
        actual.put("PARALLAX_QUALITY",pinned.get("PARALLAX_QUALITY"));
        assertTrue(DeadMipAdmission.expectedLeanOptions(actual));
        assertThrows(UnsupportedOperationException.class,()->DeadMipAdmission.leanProfiles().add(java.util.Map.of()));
        actual.put("ADVANCED_MATERIALS",pinned.get("ADVANCED_MATERIALS"));
        assertTrue(DeadMipAdmission.expectedLeanOptions(actual));
        actual.remove("TAA"); actual.put("UNKNOWN_OPTION","true");
        assertFalse(DeadMipAdmission.expectedLeanOptions(actual));
        assertFalse(DeadMipAdmission.expectedLeanOptions(null));
    }

}
