package com.pp.shaderlod.optimization;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonParser;
import com.pp.shaderlod.optimization.mips.DeadMipAdmission;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Iris incoming parser/reparse, without reload HEAD or old loaded-pack permission. */
class BslC6IncomingOptionsTest {
    private static final String KEY=CompileOptionControl.BSL_DEAD_MIP;
    private static IncludeGraph raw()throws Exception {
        var profile=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/shaderlod/adapters/qualification/dead-mip-admission.json")))
            .getAsJsonObject().getAsJsonObject("families").getAsJsonObject("bsl-shaders").getAsJsonObject("options");
        var expected=new TreeMap<String,String>();profile.entrySet().forEach(e->expected.put(e.getKey(),e.getValue().getAsString()));
        expected.putIfAbsent(CompileOptionControl.BSL_DEPTH,"0");
        var lines=ImmutableList.<String>builder();
        expected.forEach((key,value)->{
            if(Set.of("true","false").contains(value)) {
                lines.add((value.equals("true")?"":"//")+"#define "+key,"#ifdef "+key,"#endif");
            } else {
                String values=key.equals(KEY)?"0 1":key.equals("FXAA_SUBPIXEL")?"0.50 0.75":value;
                lines.add("#define "+key+" "+value+" // ["+values+"]","#if "+key+" == "+value,"#endif");
            }
        });
        var path=AbsolutePackPath.fromAbsolutePath("/incoming.glsl");
        var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);constructor.setAccessible(true);
        return constructor.newInstance(ImmutableMap.of(path,new FileNode(path,lines.build())),ImmutableMap.of());
    }
    @Test void exactMapPersistedOneStaysOnWithoutReloadPlan()throws Exception {
        var graph=raw();var overrides=Map.of(KEY,"1");var incoming=new ShaderPackOptions(graph,overrides);
        var values=new TreeMap<String,String>();CompileOptionControl.readOptions(incoming,Map.of(),false).forEach((k,v)->values.put(k,v.value()));
        assertTrue(DeadMipAdmission.expectedOptions("bsl-shaders",values));
        assertSame(incoming,CompileOptionControl.filterIncomingBslC6(graph,overrides,incoming,true));
    }
    @Test void coldChangedNativeMapForcesOnlyOwnedControlOffBeforeGlslConsumption()throws Exception {
        var graph=raw();var overrides=Map.of(KEY,"1","FXAA_SUBPIXEL","0.75");var incoming=new ShaderPackOptions(graph,overrides);
        var fixed=CompileOptionControl.filterIncomingBslC6(graph,overrides,incoming,true);
        assertNotSame(incoming,fixed);assertEquals("0",fixed.getOptionValues().getStringValueOrDefault(KEY));
        assertEquals("0.75",fixed.getOptionValues().getStringValueOrDefault("FXAA_SUBPIXEL"));
        assertEquals(Map.of(KEY,"1","FXAA_SUBPIXEL","0.75"),overrides);
        var before=new TreeMap<>(CompileOptionControl.readOptions(incoming,Map.of(),false));
        var after=new TreeMap<>(CompileOptionControl.readOptions(fixed,Map.of(),false));before.remove(KEY);after.remove(KEY);assertEquals(before,after);
        assertTrue(fixed.getIncludes().getNodes().get(AbsolutePackPath.fromAbsolutePath("/incoming.glsl"))
            .getLines().stream().anyMatch(line->line.startsWith("#define "+KEY+" 0")));
    }
    @Test void absentOrPartialC6ProfilesFailClosed() {
        assertFalse(DeadMipAdmission.expectedC6Options(null));
        assertFalse(DeadMipAdmission.expectedC6Options(Map.of()));
    }
    @Test void c7OnlyModeAndNativeZeroAreUnchanged()throws Exception {
        var graph=raw();var overrides=Map.of(KEY,"1","FXAA_SUBPIXEL","0.75");var incoming=new ShaderPackOptions(graph,overrides);
        assertSame(incoming,CompileOptionControl.filterIncomingBslC6(graph,overrides,incoming,false));
        var zero=Map.of(KEY,"0","FXAA_SUBPIXEL","0.75");incoming=new ShaderPackOptions(graph,zero);
        assertSame(incoming,CompileOptionControl.filterIncomingBslC6(graph,zero,incoming,true));
    }
    @Test void peerCompileGraphIsNotNormalizedIntoC6Qualification()throws Exception {
        var graph=raw();var overrides=Map.of(KEY,"1",CompileOptionControl.BSL_AO_HALF,"1");
        var incoming=new ShaderPackOptions(graph,overrides);var fixed=CompileOptionControl.filterIncomingBslC6(graph,overrides,incoming,true);
        assertEquals("0",fixed.getOptionValues().getStringValueOrDefault(KEY));
        assertEquals("1",fixed.getOptionValues().getStringValueOrDefault(CompileOptionControl.BSL_AO_HALF));
    }

}
