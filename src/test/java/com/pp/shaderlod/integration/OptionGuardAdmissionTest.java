package com.pp.shaderlod.integration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.packs.PackSelectionService;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/** Real Iris declarations at the cheap rejection boundary, plus a normal producer/parser control. */
class OptionGuardAdmissionTest {
    @TempDir Path directory;
    record Guard(String name,String option,Predicate<OptionSet> declaration) {}
    static Stream<Guard> guards() {
        return Stream.of(
            new Guard("AstraAo","SHADERLOD_ASTRALEX_AO_HALF_RES",com.pp.shaderlod.optimization.astraao.AstraAoOptionGuard::declaration),
            new Guard("BlissAo","SHADERLOD_BLISS_AO_HALF_RES",com.pp.shaderlod.optimization.blissao.BlissAoOptionGuard::declaration),
            new Guard("BlissPixel","SHADERLOD_BLISS_PIXEL_GRAPH",com.pp.shaderlod.optimization.blisspixel.BlissPixelOptionGuard::declaration),
            new Guard("BslAo","SHADERLOD_BSL_AO_HALF_RES",com.pp.shaderlod.optimization.bsl.BslAoOptionGuard::declaration),
            new Guard("MakeUpAo","SHADERLOD_MAKEUP_AO_HALF_RES",com.pp.shaderlod.optimization.makeupao.MakeUpAoOptionGuard::declaration),
            new Guard("ReimaginedAo","SHADERLOD_REIMAGINED_AO_HALF_RES",com.pp.shaderlod.optimization.reimaginedao.ReimaginedAoOptionGuard::declaration),
            new Guard("SildurAo","SHADERLOD_SILDUR_AO_HALF_RES",com.pp.shaderlod.optimization.sildurao.SildurAoOptionGuard::declaration),
            new Guard("SildurPixel","SHADERLOD_SILDUR_PIXEL_GRAPH",com.pp.shaderlod.optimization.sildurpixel.SildurPixelOptionGuard::declaration),
            new Guard("SolasAo","SHADERLOD_SOLAS_AO_HALF_RES",com.pp.shaderlod.optimization.solasao.SolasAoOptionGuard::declaration),
            new Guard("SolasPixel","SHADERLOD_SOLAS_PIXEL_GRAPH",com.pp.shaderlod.optimization.solaspixel.SolasPixelOptionGuard::declaration),
            new Guard("UnboundAo","SHADERLOD_UNBOUND_AO_HALF_RES",com.pp.shaderlod.optimization.unboundao.UnboundAoOptionGuard::declaration));
    }
    private static final OptionLocation LOCATION=new OptionLocation(AbsolutePackPath.fromAbsolutePath("/test.glsl"),0);
    private static StringOption string(String name,String value,String domain) {
        return Objects.requireNonNull(StringOption.create(OptionType.DEFINE,name,"["+domain+"]",value));
    }
    /** Counts reads of an unrelated declaration through the real Iris merged-option API. */
    private static final class ObservedOption extends MergedStringOption {
        int reads;
        ObservedOption(){super(LOCATION,string("UNRELATED_NATIVE_OPTION","0","0 1"));}
        @Override public StringOption getOption(){reads++;return super.getOption();}
    }
    @ParameterizedTest @MethodSource("guards")
    void absentOrBooleanFamilySentinelNeverTraversesUnrelatedDeclarations(Guard guard) {
        for(boolean wrongType:List.of(false,true)) {
            var unrelated=new ObservedOption();var builder=OptionSet.builder();builder.addStringOption(unrelated);
            if(wrongType)builder.addBooleanOption(LOCATION,new BooleanOption(OptionType.DEFINE,guard.option(),"",false));
            var declarations=builder.build();unrelated.reads=0;
            assertFalse(guard.declaration().test(declarations),guard.name());
            assertEquals(0,unrelated.reads,"Unrelated native declarations must not be read on rejection: "+guard.name());
        }
    }
    private static ShaderPackOptions parse(Path archive)throws Exception {
        try(var zip=FileSystems.newFileSystem(archive)) {
            var root=zip.getPath("/shaders");var starts=ImmutableList.<AbsolutePackPath>builder();
            for(String dimension:List.of("/","/world0/","/world-1/","/world1/"))
                ShaderPackSourceNames.findPresentSources(starts,root,AbsolutePackPath.fromAbsolutePath(dimension),ShaderPackSourceNames.POTENTIAL_STARTS);
            var pending=new ArrayDeque<AbsolutePackPath>(starts.build());var nodes=new LinkedHashMap<AbsolutePackPath,FileNode>();
            while(!pending.isEmpty()) {
                var name=pending.removeFirst();if(nodes.containsKey(name))continue;
                var node=new FileNode(name,ImmutableList.copyOf(Files.readAllLines(name.resolved(root))));nodes.put(name,node);pending.addAll(node.getIncludes().values());
            }
            var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);constructor.setAccessible(true);
            return new ShaderPackOptions(constructor.newInstance(ImmutableMap.copyOf(nodes),ImmutableMap.of()),Map.of());
        }
    }
    private static OptionSet replace(OptionSet original,String key,StringOption replacement) {
        var builder=OptionSet.builder();
        original.getBooleanOptions().forEach((k,v)->{if(!k.equals(key))builder.addBooleanOption(v);});
        original.getStringOptions().forEach((k,v)->{if(!k.equals(key))builder.addStringOption(v);});
        if(replacement!=null)builder.addStringOption(LOCATION,replacement);
        return builder.build();
    }
    @Test void normalProducerPreservesGeneratedAdmissionAndFullDeclarationRefusal()throws Exception {
        Files.copy(Path.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip"),directory.resolve("Original.zip"));
        var nativeDeclarations=parse(directory.resolve("Original.zip")).getOptionSet();
        guards().forEach(guard->assertFalse(guard.declaration().test(nativeDeclarations),guard.name()+" native graph"));
        var selected=new PackSelectionService().apply(directory,"Original.zip",true,LodPreset.PERFORMANCE,Map.of());
        assertTrue(selected.accepted(),selected.detail());assertTrue(selected.generatedArchive());
        var generatedOptions=parse(directory.resolve(selected.packFile()));
        com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.capture(generatedOptions);
        var generated=generatedOptions.getOptionSet();
        assertEquals(114,generated.getBooleanOptions().size()+generated.getStringOptions().size());
        var alpha=generated.getStringOptions().get("SHADERLOD_MAKEUP_EARLY_ALPHA");
        assertNotNull(alpha);assertEquals("0",alpha.getOption().getDefaultValue());
        assertEquals(Set.of("0","1"),Set.copyOf(alpha.getOption().getAllowedValues()));
        var actualDefaults=new TreeMap<String,String>();
        generated.getBooleanOptions().forEach((k,v)->actualDefaults.put(k,Boolean.toString(v.getOption().getDefaultValue())));
        generated.getStringOptions().forEach((k,v)->actualDefaults.put(k,v.getOption().getDefaultValue()));
        actualDefaults.put("SHADERLOD_REFINEMENT_ADAPTER","1");
        try(var input=getClass().getResourceAsStream("/assets/shaderlod/rendererquad/generated-main-options.json")) {
            assertNotNull(input);var stored=new TreeMap<String,String>();
            com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("makeup-ultra-fast-shaders").entrySet()
                .forEach(e->stored.put(e.getKey(),e.getValue().getAsString()));
            assertEquals(stored,actualDefaults,"Maintained fixture must match authentic Iris discovery exactly");
        }
        var makeup=guards().filter(g->g.name().equals("MakeUpAo")).findFirst().orElseThrow();
        assertTrue(makeup.declaration().test(generated),"Actual normal producer and Iris parser must remain admitted");
        var live=(net.irisshaders.iris.shaderpack.option.values.MutableOptionValues)generatedOptions.getOptionValues();
        assertEquals("0",com.pp.shaderlod.optimization.makeupao.MakeUpAoOptionGuard.declaredHalf(generated,live).orElseThrow());
        live.getStringValues().put(makeup.option(),"1");
        assertEquals("1",com.pp.shaderlod.optimization.makeupao.MakeUpAoOptionGuard.declaredHalf(generated,live).orElseThrow());
        assertTrue(com.pp.shaderlod.optimization.makeupao.MakeUpAoOptionGuard.snapshot(generated,live,true).isEmpty(),"Mutating raw options cannot qualify inputs for an already compiled program");
        com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.capture(generatedOptions);
        assertEquals("1",com.pp.shaderlod.optimization.makeupao.MakeUpAoOptionGuard.snapshot(generated,live,true).get(makeup.option()));
        live.getStringValues().remove(makeup.option());
        assertEquals("0",com.pp.shaderlod.optimization.makeupao.MakeUpAoOptionGuard.declaredHalf(generated,live).orElseThrow());

        assertFalse(makeup.declaration().test(replace(generated,"SHADERLOD_MAKEUP_EARLY_ALPHA",null)));
        assertFalse(makeup.declaration().test(replace(generated,"SHADERLOD_MAKEUP_EARLY_ALPHA",string("SHADERLOD_MAKEUP_EARLY_ALPHA","1","0 1"))));
        guards().filter(g->!g.name().equals("MakeUpAo")).forEach(guard->assertFalse(guard.declaration().test(generated),guard.name()+" wrong family"));
        assertFalse(makeup.declaration().test(replace(generated,makeup.option(),string(makeup.option(),"1","0 1"))),"Changed sentinel default still refuses");
        assertFalse(makeup.declaration().test(replace(generated,makeup.option(),string(makeup.option(),"0","0 1 2"))),"Spoofed binary domain still refuses");
        assertFalse(makeup.declaration().test(replace(generated,"AO",null)),"A valid family sentinel cannot bypass complete declaration checks");
        var foreign=OptionSet.builder();foreign.addAll(generated);foreign.addStringOption(LOCATION,string("FOREIGN_DECLARATION","0","0 1"));
        assertFalse(makeup.declaration().test(foreign.build()),"An extra declaration still refuses");
    }
}
