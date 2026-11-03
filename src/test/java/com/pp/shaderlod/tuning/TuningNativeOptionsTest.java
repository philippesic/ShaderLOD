package com.pp.shaderlod.tuning;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonParser;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.packs.PackSelectionService;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class TuningNativeOptionsTest {
    private boolean previousScopedSelections;
    @org.junit.jupiter.api.BeforeEach void rememberSelectionMode() throws Exception {
        var field=PackSelectionService.class.getDeclaredField("recipeScopedSelections");field.setAccessible(true);previousScopedSelections=field.getBoolean(null);
    }
    @org.junit.jupiter.api.AfterEach void restoreSelectionMode() throws Exception {
        var field=PackSelectionService.class.getDeclaredField("recipeScopedSelections");field.setAccessible(true);field.setBoolean(null,previousScopedSelections);
    }

    @TempDir Path directory;
    record Family(String id,String archive,String reference,String optional,String value) {}
    static Stream<Family> families() {
        return Stream.of(
            new Family("photon","photon_v1.3b.zip","photonquad/native366.json","POM","true"),
            new Family("solas-shader","Solas Shader V3.7b.zip","solasquad/native428.json","PARALLAX","true"),
            new Family("astralex","§r§lAstra§4§lLex§r§l_By_LexBoosT_§4§lV93.0§r§l.zip","astraquad/native1015.json","PARALLAX","true"),
            new Family("complementary-reimagined","ComplementaryReimagined_r5.9.3.zip","reimaginedquad/native398.json","RP_MODE","2"),
            new Family("complementary-unbound","ComplementaryUnbound_r5.9.3.zip","unboundquad/native398.json","RP_MODE","2"),
            new Family("bliss-shader","Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip","blissquad/native463.json","POM","true"),
            new Family("makeup-ultra-fast-shaders","MakeUp-UltraFast-9.5g.zip","makeupquad/native100.json","AA_TYPE","1"),
            new Family("sildurs-vibrant-shaders","Sildur's Vibrant Shaders v2.02 Extreme.zip","sildurquad/native115.json","nMap","2"),
            new Family("bsl-shaders","BSL_v10.1.8.zip","bslquad/native384.json","PARALLAX","true"));
    }
    private Map<String,String> nativeMap(Family f) throws Exception {
        var result=new TreeMap<String,String>();
        try(var in=getClass().getResourceAsStream("/assets/shaderlod/"+f.reference())) {
            assertNotNull(in);JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject().entrySet().forEach(e->result.put(e.getKey(),e.getValue().getAsString()));
        }
        return result;
    }
    private Path original(Family f) throws IOException {
        Path source = Path.of("dependencies/upstream",f.archive());
        if (!Files.exists(source)) source = Path.of("dependencies/upstream/other-pack-research",f.archive());
        if (!Files.exists(source)) source = Path.of("dependencies/upstream/pack-expansion",f.archive());
        return Files.copy(source,directory.resolve("Original.zip"));
    }
    /** Independent live-map input from actual public Iris parser, including all generated declarations. */
    private static ShaderPackOptions parser(Path archive,Map<String,String> overrides) throws Exception {
        try(var zip=FileSystems.newFileSystem(archive)) {
            var root=zip.getPath("/shaders");var starts=ImmutableList.<AbsolutePackPath>builder();
            for(String dim:List.of("/","/world0/","/world-1/","/world1/"))
                ShaderPackSourceNames.findPresentSources(starts,root,AbsolutePackPath.fromAbsolutePath(dim),ShaderPackSourceNames.POTENTIAL_STARTS);
            var pending=new ArrayDeque<AbsolutePackPath>(starts.build());var nodes=new LinkedHashMap<AbsolutePackPath,FileNode>();
            while(!pending.isEmpty()) {var name=pending.removeFirst();if(nodes.containsKey(name))continue;
                var node=new FileNode(name,ImmutableList.copyOf(Files.readAllLines(name.resolved(root))));nodes.put(name,node);pending.addAll(node.getIncludes().values());}
            var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);constructor.setAccessible(true);
            var graph=constructor.newInstance(ImmutableMap.copyOf(nodes),ImmutableMap.of());
            return new ShaderPackOptions(graph,overrides);
        }
    }
    private static boolean bliss(Path archive) throws IOException {
        try(var zip=new java.util.zip.ZipFile(archive.toFile())){return zip.getEntry("shaders/dimensions/composite.fsh")!=null && zip.getEntry("shaders/lib/settings.glsl")!=null;}
    }
    /** Bliss merging diagnostics require initialized Iris. Reuse captured complete option keys,
     * and derive each legal value domain from actual Iris per-file annotations, without logging/platform fakes. */
    private static Map<String,String> blissValues(Path archive) throws Exception {
        boolean generated;try(var zip=new java.util.zip.ZipFile(archive.toFile())){generated=zip.getEntry("shaderlod-selection.json")!=null;}
        String resource=generated?"rendererquad/generated-main-options.json":"blissquad/native463.json";
        try(var in=TuningNativeOptionsTest.class.getResourceAsStream("/assets/shaderlod/"+resource)){
            var object=JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();if(generated)object=object.getAsJsonObject("bliss-shader");
            var result=new TreeMap<String,String>();object.entrySet().forEach(e->result.put(e.getKey(),e.getValue().getAsString()));return result;
        }
    }
    private static Map<String,Set<String>> blissDomains(Path archive) throws Exception {
        var expected=blissValues(archive);var result=new TreeMap<String,Set<String>>();
        try(var zip=new java.util.zip.ZipFile(archive.toFile())){
            for(var entry:Collections.list(zip.entries())){String name=entry.getName();if(!name.startsWith("shaders/")||!name.matches(".*\\.(glsl|fsh|vsh|gsh|csh|tcs|tes|settings)$"))continue;
                var text=new String(zip.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);var annotated=new net.irisshaders.iris.shaderpack.option.OptionAnnotatedSource(text);
                annotated.getBooleanOptions().values().forEach(o->{if(expected.containsKey(o.getName()))result.computeIfAbsent(o.getName(),k->new HashSet<>()).addAll(Set.of("true","false"));});
                annotated.getStringOptions().values().forEach(o->{if(expected.containsKey(o.getName()))result.computeIfAbsent(o.getName(),k->new HashSet<>()).addAll(o.getAllowedValues());});
            }
        }
        assertEquals(expected.keySet(),result.keySet(),"Every captured Bliss key has an authentic declaration");return result;
    }
    private static Map<String,Set<String>> domains(Path archive) throws IOException {
        try {if(bliss(archive))return blissDomains(archive);var options=parser(archive,Map.of()).getOptionSet();var result=new TreeMap<String,Set<String>>();
            options.getBooleanOptions().forEach((k,v)->result.put(k,Set.of("true","false")));
            options.getStringOptions().forEach((k,v)->result.put(k,Set.copyOf(v.getOption().getAllowedValues())));return result;
        } catch(Exception failure){throw new IOException(failure);}
    }
    private static Map<String,String> project(Path dir,String selected,String family,Map<String,String> actual) throws IOException {
        return TuningNativeOptions.project(dir,selected,family,actual,TuningNativeOptionsTest::domains);
    }
    private static Map<String,String> parsed(Path archive,Map<String,String> overrides) throws Exception {
            if(bliss(archive)){var result=new TreeMap<>(blissValues(archive));var allowed=blissDomains(archive);for(var e:overrides.entrySet())if(result.containsKey(e.getKey())){assertTrue(allowed.get(e.getKey()).contains(e.getValue()));result.put(e.getKey(),e.getValue());}return result;}
            var options=parser(archive,overrides);var result=new TreeMap<String,String>();
            options.getOptionSet().getBooleanOptions().keySet().forEach(k->result.put(k,Boolean.toString(options.getOptionValues().getBooleanValueOrDefault(k))));
            options.getOptionSet().getStringOptions().keySet().forEach(k->result.put(k,options.getOptionValues().getStringValueOrDefault(k)));
            return result;
    }
    @ParameterizedTest @MethodSource("families")
    void actualOriginalAndGeneratedProjectToSameCompleteNativeChoices(Family family) throws Exception {
        {
            original(family);var nativeValues=nativeMap(family);
            var parsedNative=parsed(directory.resolve("Original.zip"),Map.of());
            assertEquals(nativeValues.keySet(),parsedNative.keySet(),family.id()+" complete native domain keys");
            assertEquals(nativeValues,parsed(directory.resolve("Original.zip"),nativeValues),family.id()+" full legal native profile");
            nativeValues.put(family.optional(),family.value());
            assertEquals(nativeValues,project(directory,"Original.zip",family.id(),nativeValues));
            PackSelectionService.enableRecipeScopedSelections();
            var selected=new PackSelectionService().apply(directory,"Original.zip",true,LodPreset.PERFORMANCE,nativeValues,false);
            assertTrue(selected.accepted(),selected.detail());assertTrue(selected.generatedArchive());
            var override=new TreeMap<>(nativeValues);
            try(var in=Files.newInputStream(directory.resolve(selected.packFile()+".txt"))){var p=new Properties();p.load(in);p.forEach((k,v)->override.put(k.toString(),v.toString()));}
            var actual=parsed(directory.resolve(selected.packFile()),override);
            assertTrue(actual.size()>nativeValues.size(),"Authentic adapter adds real declarations");
            assertEquals(nativeValues,project(directory,selected.packFile(),family.id(),actual));
            var unknown=new TreeMap<>(actual);unknown.put("SHADERLOD_UNKNOWN_NATIVE_OPTION","1");
            assertThrows(IOException.class,()->project(directory,selected.packFile(),family.id(),unknown));
            var missing=new TreeMap<>(actual);missing.remove(family.optional());
            assertThrows(IOException.class,()->project(directory,selected.packFile(),family.id(),missing));
            var invalid=new TreeMap<>(actual);invalid.put(family.optional(),"not-a-native-value");
            assertThrows(IOException.class,()->project(directory,selected.packFile(),family.id(),invalid));
            assertThrows(IOException.class,()->project(directory,selected.packFile(),"wrong-family",actual));
            Files.write(directory.resolve(selected.packFile()),new byte[]{7},StandardOpenOption.APPEND);
            assertThrows(IOException.class,()->project(directory,selected.packFile(),family.id(),actual));
        }
    }
    @Test void changedOriginalSourceCannotEstablishNativeDomains() throws Exception {
        var family=families().findFirst().orElseThrow();var path=original(family);
        try(var zip=FileSystems.newFileSystem(path);var paths=Files.walk(zip.getPath("/shaders"))){
            var member=paths.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".glsl")).findFirst().orElseThrow();
            Files.writeString(member,"\n// changed executable source\n",StandardOpenOption.APPEND);
        }
        assertThrows(IOException.class,()->project(directory,"Original.zip",family.id(),nativeMap(family)));
    }
}
