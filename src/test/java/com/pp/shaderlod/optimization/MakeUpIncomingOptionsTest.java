package com.pp.shaderlod.optimization;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pp.shaderlod.CoreUniformFrame;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.LodSettings;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility;
import com.pp.shaderlod.optimization.CompileOptionControl.NativeOption;
import com.pp.shaderlod.optimization.framecopy.MakeUpDeadCopyAdmission;
import java.util.*;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Incoming sidecar regression; no client, GL or global Iris current-pack access. */
class MakeUpIncomingOptionsTest {
    private static final String KEY=CompileOptionControl.MAKEUP_DEAD_FRAME_COPY;
    private static NativeOption binary(String value){return new NativeOption("STRING",Set.of("0","1"),"0",value);}
    private static Map<String,NativeOption> native100() {
        assertEquals(100,MakeUpQuadEligibility.nativeOptions().size());
        var options=new TreeMap<String,NativeOption>();
        MakeUpQuadEligibility.nativeOptions().forEach((key,value)->options.put(key,
            new NativeOption(Set.of("true","false").contains(value)?"BOOLEAN":"STRING",Set.of(value),value,value)));
        options.put(KEY,binary("1"));options.put("SHADERLOD_REFINEMENT_ADAPTER",binary("1"));
        return options;
    }
    private static Map<String,String> admittedPlan(Map<String,NativeOption> loaded) {
        return CompileOptionControl.plan(CompileOptionControl.Family.MAKEUP,
            new CoreUniformFrame.Core(new LodSettings(true,32,96,8,false),LodPreset.PERFORMANCE),
            OptimizationSnapshot.noneRequested().with(OptimizationId.DEAD_FRAME_COPY,true),Set.of(KEY),loaded,
            "minecraft:overworld",false,false,Map.of(OptimizationId.DEAD_FRAME_COPY,true));
    }
    @Test void oldLoadedNative100CannotAuthorizeIncomingBloomOffSidecar() {
        var loaded=native100();var desired=admittedPlan(loaded);assertEquals("1",desired.get(KEY));
        var incoming=new TreeMap<>(loaded);
        incoming.put("BLOOM",new NativeOption("BOOLEAN",Set.of("false","true"),"true","false"));
        var before=Map.copyOf(incoming);
        assertEquals("0",CompileOptionControl.filterIncomingMakeUpDeadCopy(desired,incoming).get(KEY));
        assertEquals(before,incoming);assertEquals("false",incoming.get("BLOOM").value());
        assertEquals("1",loaded.get(KEY).value());assertEquals("1",desired.get(KEY));
    }
    @Test void incomingAaChangesAndEveryPeerGraphAreVetoedWithoutRepairingUserValues() {
        var desired=admittedPlan(native100());
        var incoming=native100();incoming.put("AA_TYPE",new NativeOption("STRING",Set.of("0","2"),"2","0"));
        assertEquals("0",CompileOptionControl.filterIncomingMakeUpDeadCopy(desired,incoming).get(KEY));
        assertEquals("0",incoming.get("AA_TYPE").value());
        for(var peer:Set.of(CompileOptionControl.MAKEUP_AO_HALF,CompileOptionControl.MAKEUP_DEPTH,
                CompileOptionControl.MAKEUP_EARLY_ALPHA,CompileOptionControl.MAKEUP_SPATIAL_REUSE,
                CompileOptionControl.MAKEUP_SPATIAL_RATE,CompileOptionControl.MAKEUP_WORLD_PIXEL)) {
            incoming=native100();incoming.put(peer,binary("1"));var before=Map.copyOf(incoming);
            assertEquals("0",CompileOptionControl.filterIncomingMakeUpDeadCopy(desired,incoming).get(KEY),peer);
            assertEquals(before,incoming);
        }
    }
    @Test void validIncomingAndUnrelatedPlansAreUnchanged() {
        var desired=new TreeMap<>(admittedPlan(native100()));desired.put(CompileOptionControl.MAKEUP_DEPTH,"1");
        assertSame(desired,CompileOptionControl.filterIncomingMakeUpDeadCopy(desired,native100()));
        var incoming=native100();incoming.remove("BLOOM");
        var filtered=CompileOptionControl.filterIncomingMakeUpDeadCopy(desired,incoming);
        var expected=new TreeMap<>(desired);expected.put(KEY,"0");assertEquals(expected,filtered);
        var off=Map.of(KEY,"0",CompileOptionControl.MAKEUP_DEPTH,"1");
        assertSame(off,CompileOptionControl.filterIncomingMakeUpDeadCopy(off,incoming));
        assertSame(off,CompileOptionControl.filterIncomingMakeUpDeadCopy(off,null));
    }
    @Test void missingAndInvalidTypedControlsFailClosed() {
        var desired=Map.of(KEY,"1");var incoming=native100();incoming.remove(KEY);
        assertEquals("0",CompileOptionControl.filterIncomingMakeUpDeadCopy(desired,incoming).get(KEY));
        for(var invalid:List.of(new NativeOption("BOOLEAN",Set.of("false","true"),"false","true"),
                new NativeOption("STRING",Set.of("0","1"),"1","1"),
                new NativeOption("STRING",Set.of("0","1","2"),"0","1"),binary("2"))) {
            incoming=native100();incoming.put(KEY,invalid);
            assertEquals("0",CompileOptionControl.filterIncomingMakeUpDeadCopy(desired,incoming).get(KEY));
        }
        assertEquals("0",CompileOptionControl.filterIncomingMakeUpDeadCopy(desired,null).get(KEY));
    }
    private static IncludeGraph raw() throws Exception {
        var lines=ImmutableList.<String>builder();
        MakeUpQuadEligibility.nativeOptions().forEach((key,value)->{
            if(Set.of("true","false").contains(value)) {
                lines.add((value.equals("true")?"":"//")+"#define "+key);
                lines.add("#ifdef "+key,"#endif");
            } else {
                lines.add("#define "+key+" "+value+" // ["+value+(key.equals("AA_TYPE")?" 0":"")+"]");
                lines.add("#if "+key+" == "+value,"#endif");
            }
        });
        for(String key:List.of(KEY,"SHADERLOD_REFINEMENT_ADAPTER",CompileOptionControl.MAKEUP_DEPTH)) {
            lines.add("#define "+key+" 0 // [0 1]");
            lines.add("#if "+key+" == 1","#endif");
        }
        var path=AbsolutePackPath.fromAbsolutePath("/incoming.glsl");
        var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);constructor.setAccessible(true);
        return constructor.newInstance(ImmutableMap.of(path,new FileNode(path,lines.build())),ImmutableMap.of());
    }
    @Test void actualIncomingTypedReaderAndReparsePreserveSidecarAndRewriteGlsl() throws Exception {
        var graph=raw();var enabled=Map.of(KEY,"1","SHADERLOD_REFINEMENT_ADAPTER","1");
        var loaded=new ShaderPackOptions(graph,enabled);
        var typed=CompileOptionControl.readOptions(loaded,Map.of(),false);
        assertTrue(MakeUpDeadCopyAdmission.options(typed));var desired=admittedPlan(typed);
        assertSame(loaded,CompileOptionControl.parsedShaderPackOptions(graph,enabled,loaded,desired));
        for(var edit:List.of(Map.of("BLOOM","false"),Map.of("AA_TYPE","0"),Map.of(CompileOptionControl.MAKEUP_DEPTH,"1"))) {
            var changed=new TreeMap<>(enabled);changed.putAll(edit);
            var before=Map.copyOf(changed);var incoming=new ShaderPackOptions(graph,changed);
            var fixed=CompileOptionControl.parsedShaderPackOptions(graph,changed,incoming,desired);
            var expected=new TreeMap<>(CompileOptionControl.readOptions(incoming,Map.of(),false));expected.put(KEY,binary("0"));
            assertEquals(expected,CompileOptionControl.readOptions(fixed,Map.of(),false));assertEquals(before,changed);
            assertEquals("1",loaded.getOptionValues().getStringValueOrDefault(KEY));
            var path=AbsolutePackPath.fromAbsolutePath("/incoming.glsl");
            assertTrue(fixed.getIncludes().getNodes().get(path).getLines().stream().anyMatch(line->line.startsWith("#define "+KEY+" 0")));
        }
        assertEquals("0",CompileOptionControl.parsedShaderPackOptions(graph,enabled,loaded,Map.of(KEY,"0"))
            .getOptionValues().getStringValueOrDefault(KEY));
    }
}
