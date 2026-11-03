package com.pp.shaderlod.optimization.sourcecache;

import com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedCompilationInputs;
import com.pp.shaderlod.packs.CapturedRendererInputs;
import com.pp.shaderlod.packs.PackSelectionService;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import net.irisshaders.iris.shaderpack.include.IncludeProcessor;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;

/** Load-time input evidence. Only the successful compile/link join may publish a draw owner. */
public class IrisCompiledSource {
    protected IrisCompiledSource() {}
    private static String last = "not observed";
    public static String status() { return last; }
    public interface OptionsView { OptionsInput shaderlod$makeupOptionsInput(); }
    public interface PackView { PackInput shaderlod$makeupPackInput(); }
    public interface SetView { SetInput shaderlod$makeupSetInput(); }

    public record OptionsInput(ShaderPackOptions owner, ReimaginedCompilationInputs captured,
            IncludeGraph rewritten, RawOptionOverrides overrides, Map<String,String> effective) {
        public OptionsInput { effective=Map.copyOf(effective); }
        public OptionsInput(ShaderPackOptions owner, ReimaginedCompilationInputs captured, IncludeGraph rewritten, RawOptionOverrides overrides) {
            this(owner,captured,rewritten,overrides,effective(captured));
        }
        private static Map<String,String> effective(ReimaginedCompilationInputs captured) {
            var values=new TreeMap<String,String>();captured.booleanOptions().forEach((k,v)->values.put(k,v.toString()));values.putAll(captured.stringOptions());return Map.copyOf(values);
        }
        public static OptionsInput capture(ShaderPackOptions owner, IncludeGraph raw) {
            var captured=ReimaginedCompilationInputs.capture(raw,owner.getOptionSet(),owner.getOptionValues());
            return new OptionsInput(owner,captured,owner.getIncludes(),RawOptionOverrides.capture(owner,captured));
        }
        public Map<String,String> values() {
            return effective;
        }
        /** No file reads or temporary full option maps on the draw path. */
        public boolean current(ShaderPackOptions actual) {
            if (actual != owner || actual.getIncludes() != rewritten) return false;
            if(overrides!=null)return overrides.current(actual);
            var declarations = actual.getOptionSet();
            if (!declarations.getBooleanOptions().keySet().equals(captured.booleanOptions().keySet())
                    || !declarations.getStringOptions().keySet().equals(captured.stringOptions().keySet())) return false;
            var values = actual.getOptionValues();
            for (var entry : captured.booleanOptions().entrySet())
                if (entry.getValue() != values.getBooleanValueOrDefault(entry.getKey())) return false;
            for (var entry : captured.stringOptions().entrySet())
                if (!entry.getValue().equals(values.getStringValueOrDefault(entry.getKey()))) return false;
            return true;
        }
    }
    public record SetInput(AbsolutePackPath directory, Function<AbsolutePackPath,String> provider,
            ShaderProperties properties, ShaderPack pack) {}
    public record PackInput(OptionsInput options, Function<AbsolutePackPath,String> provider,
            ShaderProperties properties, List<StringPair> glslDefines,
            List<StringPair> propertyDefines, Map<String,String> propertyText, IrisInputSnapshot observation) {
        public PackInput(OptionsInput options, Function<AbsolutePackPath,String> provider, ShaderProperties properties, List<StringPair> glslDefines, List<StringPair> propertyDefines, Map<String,String> propertyText) {
            this(options,provider,properties,glslDefines,propertyDefines,propertyText,null);
        }
        public PackInput {
            glslDefines = List.copyOf(glslDefines);
            propertyDefines = List.copyOf(propertyDefines);
            propertyText = Collections.unmodifiableMap(new HashMap<>(propertyText));
        }
    }
    private static final class Build {
        final Object pack;
        final Path directory;
        final IrisInputSnapshot observation;
        boolean discoveryMatches=true;
        int discoveries;
        final Map<String,String> properties = new HashMap<>();
        String shaderPropertiesText;
        List<StringPair> propertyDefines;
        ShaderPackOptions options;
        ShaderProperties parsed;
        Build(Object pack, Path directory) {
            this.pack = pack; this.directory=directory;
            IrisInputSnapshot observed;
            try { observed=IrisInputSnapshot.capture(directory); } catch(Exception|LinkageError unavailable) { observed=null; }
            this.observation=observed;
        }
    }
    private static final ThreadLocal<Build> BUILD = new ThreadLocal<>();
    public static void begin(Object pack, Path directory) { BUILD.set(new Build(pack,directory)); }
    public static void discovered(AbsolutePackPath directory,List<String> candidates,List<AbsolutePackPath> actual,boolean present) {
        var build=BUILD.get();if(build==null)return;
        build.discoveries++;
        build.discoveryMatches &= build.observation!=null&&build.observation.discovered(directory,candidates,actual,present);
    }
    public static void existence(Path path,boolean exists) {
        var build=BUILD.get();if(build==null)return;
        build.discoveryMatches &= build.observation!=null&&build.observation.exists(build.directory.relativize(path).toString(),exists);
    }
    public static void propertyRead(String name, String text) {
        var build = BUILD.get();
        if (build != null) {
            build.properties.put(name, text);
            build.discoveryMatches &= build.observation!=null&&build.observation.property(name,text);
        }
    }
    public static void properties(Object pack, ShaderPackOptions options, List<StringPair> defines,
            String text, ShaderProperties parsed) {
        var build = BUILD.get();
        if (build == null || build.pack != pack) return;
        build.shaderPropertiesText = text;
        build.propertyDefines = List.copyOf(defines);
        build.options = options;
        build.parsed = parsed;
    }
    public static PackInput finish(Object pack, ShaderPackOptions options,
            Function<AbsolutePackPath,String> provider, ShaderProperties properties, List<StringPair> defines) {
        var build = BUILD.get();
        BUILD.remove();
        if (build == null || build.pack != pack || build.options != options || build.parsed != properties
                || build.propertyDefines == null || provider == null
                || build.observation == null || !build.discoveryMatches || build.discoveries==0
                || !build.properties.keySet().containsAll(Set.of("shaders.properties","dimension.properties","item.properties","entity.properties","block.properties"))
                || !build.observation.unchanged(build.directory)
                || !Objects.equals(build.properties.get("shaders.properties"), build.shaderPropertiesText)
                || !(options instanceof OptionsView view) || view.shaderlod$makeupOptionsInput() == null) return null;
        return new PackInput(view.shaderlod$makeupOptionsInput(), provider, properties, defines,
            build.propertyDefines, build.properties,build.observation);
    }

    public record Stages(String vertex, String geometry, String control, String evaluation, String fragment) {
        public static Stages of(ProgramSource source) {
            return new Stages(source.getVertexSource().orElse(null), source.getGeometrySource().orElse(null),
                source.getTessControlSource().orElse(null), source.getTessEvalSource().orElse(null),
                source.getFragmentSource().orElse(null));
        }
        public boolean arguments(String vertex, String geometry, String control, String evaluation, String fragment) {
            return equals(new Stages(vertex,geometry,control,evaluation,fragment));
        }
    }
    public record Certificate(ProgramSource source, ProgramSet set, PackInput input,
            String selected, Stages stages, long epoch) {
        public boolean current(ShaderPack pack, String selected, long epoch) {
            return this.epoch == epoch && this.selected.equals(selected) && set.getPack() == pack
                && source.getParent() == set && pack instanceof PackView view
                && view.shaderlod$makeupPackInput() == input && input.options().current(pack.getShaderPackOptions());
        }
    }

    /** The publication gate also proves complete discovery/absence and all input properties. */
    public static boolean matchesRaw(CapturedRendererInputs snapshot, PackInput input, String family) {
        return input != null && input.observation() != null && input.observation().matches(snapshot)
            && matchesRaw(snapshot,input.options(),family);
    }
    /** Per-node comparison only; never sufficient on its own to publish a loaded graph. */
    public static boolean matchesRaw(CapturedRendererInputs snapshot, OptionsInput input) {
        return matchesRaw(snapshot,input,"makeup-ultra-fast-shaders");
    }
    public static boolean matchesRaw(CapturedRendererInputs snapshot, OptionsInput input, String family) {
        if (snapshot == null || input == null || !family.equals(snapshot.adapterId())
                || !input.captured().rawGraph().getFailures().isEmpty()
                || !input.rewritten().getFailures().isEmpty()
                || !snapshot.effectiveOptions().equals(input.values())) return false;
        for (var node : input.captured().rawGraph().getNodes().entrySet())
            if (!snapshot.matchesIrisLines("shaders" + node.getKey().getPathString(), node.getValue().getLines())) return false;
        return true;
    }

    /** Uses pinned Iris option rewriting, include expansion and preprocessing; no GLSL rewriting. */
    public static Stages replay(OptionsInput input, List<StringPair> defines, AbsolutePackPath directory) {
        return replay(input,defines,directory,"gbuffers_terrain");
    }
    public static Stages replay(OptionsInput input, List<StringPair> defines, AbsolutePackPath directory, String programName) {
        var replayed = new ShaderPackOptions(input.captured().rawGraph(), input.values());
        var expected = replayed.getIncludes();
        if (!expected.getFailures().isEmpty() || !expected.getNodes().keySet().equals(input.rewritten().getNodes().keySet())) return null;
        for (var entry : expected.getNodes().entrySet())
            if (!entry.getValue().getLines().equals(input.rewritten().getNodes().get(entry.getKey()).getLines())) return null;
        var processor = new IncludeProcessor(expected);
        String base = directory.getPathString();
        if (!base.endsWith("/")) base += "/";
        return new Stages(stage(processor,defines,base+programName+".vsh"),
            stage(processor,defines,base+programName+".gsh"), stage(processor,defines,base+programName+".tcs"),
            stage(processor,defines,base+programName+".tes"), stage(processor,defines,base+programName+".fsh"));
    }
    private static String stage(IncludeProcessor processor, List<StringPair> defines, String path) {
        var lines = processor.getIncludedFile(AbsolutePackPath.fromAbsolutePath(path));
        if (lines == null) return null;
        var text = new StringBuilder();
        for (String line : lines) text.append(line).append('\n');
        return JcppProcessor.glslPreprocessSource(text.toString(), defines);
    }
    private static Stages providerStages(Function<AbsolutePackPath,String> provider, AbsolutePackPath directory, String programName) {
        String base = directory.getPathString();
        if (!base.endsWith("/")) base += "/";
        return new Stages(provider.apply(AbsolutePackPath.fromAbsolutePath(base+programName+".vsh")),
            provider.apply(AbsolutePackPath.fromAbsolutePath(base+programName+".gsh")),
            provider.apply(AbsolutePackPath.fromAbsolutePath(base+programName+".tcs")),
            provider.apply(AbsolutePackPath.fromAbsolutePath(base+programName+".tes")),
            provider.apply(AbsolutePackPath.fromAbsolutePath(base+programName+".fsh")));
    }
    public static Certificate certify(ProgramSource source, ProgramSet set, Path archive, String selected, long epoch) {
        return certify(source,set,archive,selected,epoch,"makeup-ultra-fast-shaders","gbuffers_terrain");
    }
    /** Family/program pairs are admitted explicitly; this helper does no GLSL transformation. */
    public static Certificate certify(ProgramSource source, ProgramSet set, Path archive, String selected, long epoch,
            String family, String programName) {
        if (!(Set.of("makeup-ultra-fast-shaders","complementary-reimagined","complementary-unbound").contains(family)&&Set.of("deferred","deferred1").contains(programName)) && !("sildurs-vibrant-shaders".equals(family)&&Set.of("deferred","deferred1","deferred2").contains(programName)) && !("photon".equals(family)&&"gbuffers_terrain_solid".equals(programName)) && !programName.equals(Map.of("makeup-ultra-fast-shaders","gbuffers_terrain","bsl-shaders","gbuffers_terrain","astralex","gbuffers_terrain","bliss-shader","gbuffers_terrain","photon","gbuffers_terrain","complementary-reimagined","gbuffers_terrain","sildurs-vibrant-shaders","gbuffers_textured","solas-shader","gbuffers_terrain","complementary-unbound","gbuffers_terrain").get(family))) return null;
        last="source/set observers";
        if (source == null || set == null || source.getParent() != set || !programName.equals(source.getName())
                || !(set instanceof SetView setView) || !(set.getPack() instanceof PackView packView)) return null;
        var input = packView.shaderlod$makeupPackInput();
        var actualSet = setView.shaderlod$makeupSetInput();
        last="pack/set/provider/properties/options identity";
        if (input == null || actualSet == null || actualSet.pack() != set.getPack()
                || actualSet.provider() != input.provider() || actualSet.properties() != input.properties()
                || !Set.of("/", "/world0").contains(actualSet.directory().getPathString())
                || !input.options().current(set.getPack().getShaderPackOptions())) return null;
        var snapshot = PackSelectionService.captureGeneratedRendererInputs(archive,input.options().values());
        last="verified snapshot/raw graph";
        if (!matchesRaw(snapshot,input,family)) return null;
        last="observed properties bytes";
        if (!snapshot.matchesIrisProperties("shaders/shaders.properties",input.propertyText().get("shaders.properties"))
                || !snapshot.matchesIrisProperties("shaders/dimension.properties",input.propertyText().get("dimension.properties"))) return null;
        last="replayed stages/provider/ProgramSource";
        var expected = replay(input.options(), input.glslDefines(), actualSet.directory(),programName);
        var observed = Stages.of(source);
        if (expected == null || expected.vertex() == null || expected.fragment() == null
                || expected.geometry() != null || expected.control() != null || expected.evaluation() != null
                || !expected.equals(observed) || !expected.equals(providerStages(input.provider(),actualSet.directory(),programName))) return null;
        last="verified compile inputs";
        return new Certificate(source,set,input,selected,expected,epoch);
    }
}
