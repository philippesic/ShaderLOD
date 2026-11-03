// Offline pinned-Iris regression. No game, rendering pipeline or GL context.
import java.nio.file.*;
import java.util.*;
import com.google.common.collect.*;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.parsing.BooleanParser;
import net.irisshaders.iris.shaderpack.preprocessor.PropertiesPreprocessor;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.irisshaders.iris.shaderpack.parsing.CommentDirectiveParser;
import net.irisshaders.iris.shaderpack.parsing.CommentDirective;
import net.irisshaders.iris.shaderpack.parsing.ConstDirectiveParser;
import net.irisshaders.iris.gl.texture.TextureScaleOverride;
import net.irisshaders.iris.targets.BufferFlipper;

class MakeupG06Properties {
    static ShaderPackOptions options(Path root, Map<String,String> values) throws Exception {
        var nodes = ImmutableMap.<AbsolutePackPath,FileNode>builder();
        try (var paths = Files.walk(root)) {
            for (Path p : paths.filter(Files::isRegularFile).toList()) {
                String name = p.toString();
                if (!name.endsWith(".glsl") && !name.endsWith(".fsh") && !name.endsWith(".vsh")) continue;
                var absolute = AbsolutePackPath.fromAbsolutePath("/" + root.relativize(p));
                nodes.put(absolute, new FileNode(absolute, ImmutableList.copyOf(Files.readAllLines(p))));
            }
        }
        var constructor = IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class, ImmutableMap.class);
        constructor.setAccessible(true);
        var graph = (IncludeGraph)constructor.newInstance(nodes.build(), ImmutableMap.of());
        return new ShaderPackOptions(graph, values);
    }

    static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path archive = Path.of(args[0]);
        var nativeOptions = new Properties();
        try (var in = Files.newBufferedReader(Path.of(args[1]))) { nativeOptions.load(in); }
        String oldProperties = Files.readString(Path.of(args[2]));
        try (var zip = FileSystems.newFileSystem(archive)) {
            var root = zip.getPath("/shaders");
            String fixed = Files.readString(root.resolve("shaders.properties"));
            int checks = 0;
            for (int mode = 0; mode <= 3; mode++) for (boolean dh : List.of(false, true)) {
                var values = new TreeMap<String,String>();
                nativeOptions.forEach((k,v) -> values.put(k.toString(),v.toString()));
                values.put("G06_AO_SPATIAL", Integer.toString(mode));
                var options = options(root, values);
                // These really are options from all dimensions; they are not a
                // statement that the current world0 shader runs in the Nether.
                for (String n : List.of("NETHER", "THE_END", "UNKNOWN_DIM")) {
                    require(options.getOptionSet().getBooleanOptions().containsKey(n), "missing native flag " + n);
                    require(options.getOptionValues().getBooleanValueOrDefault(n), "native flag false " + n);
                }
                var defines = new ArrayList<StringPair>(List.of(new StringPair("MC_VERSION","12101"),
                    new StringPair("IS_IRIS","1"), new StringPair("IRIS_VERSION","10812"),
                    new StringPair("MC_OS_MAC","1")));
                if (dh) defines.add(new StringPair("DISTANT_HORIZONS","1"));
                // The exact overload called by ShaderProperties, including its
                // real discovered-option injection. Avoid unrelated constructor
                // dependencies on Minecraft UI classes in this no-game test.
                var before = new Properties();
                before.load(new java.io.StringReader(PropertiesPreprocessor.preprocessSource(oldProperties, options, defines)));
                var after = new Properties();
                after.load(new java.io.StringReader(PropertiesPreprocessor.preprocessSource(fixed, options, defines)));
                String key = "program.world0/deferred.enabled";
                require(!BooleanParser.parse(before.getProperty(key), options.getOptionValues()), "explicit dimension-guard regression mutation did not reproduce refusal");
                boolean enabled = mode > 0 && !dh;
                require(BooleanParser.parse(after.getProperty(key), options.getOptionValues()) == enabled, "current candidate producer mismatch");
                var scaleText = after.getProperty("size.buffer.colortex8");
                require((scaleText != null) == enabled, "RT8 allocation mismatch");
                if (enabled) {
                    var parts = scaleText.split(" ");
                    var scale = new TextureScaleOverride(parts[0], parts[1]);
                    require(scale.getX(1920) == 960 && scale.getY(1200) == 600, "RT8 extent mismatch");
                    var includes = new IncludeProcessor(options.getIncludes());
                    String producer = JcppProcessor.glslPreprocessSource(String.join("\n", includes.getIncludedFile(
                        AbsolutePackPath.fromAbsolutePath("/world0/deferred.fsh"))), defines);
                    var output = CommentDirectiveParser.findDirective(producer, CommentDirective.Type.RENDERTARGETS);
                    require(output.isPresent() && output.get().getDirective().trim().equals("8"), "producer output join");
                    var constants = new HashMap<String,String>();
                    for (var d : ConstDirectiveParser.findDirectives(producer)) constants.put(d.getKey(), d.getValue());
                    require("RG32F".equals(constants.get("colortex8Format")), "producer target format");
                    require("false".equals(constants.get("colortex8Clear")), "producer clear policy");
                    String consumer = JcppProcessor.glslPreprocessSource(String.join("\n", includes.getIncludedFile(
                        AbsolutePackPath.fromAbsolutePath("/world0/deferred1.fsh"))), defines);
                    require(consumer.contains("uniform sampler2D colortex8"), "RT8 consumer sampler missing");
                    require(consumer.contains("g06Ao(depth, dither)"), "authentic caller join missing");
                    var flip = new BufferFlipper();
                    require(!flip.snapshot().contains(8), "unexpected initial flip");
                    flip.flip(8); // CompositeRenderer's default after RT8 output.
                    require(flip.snapshot().contains(8), "current output not visible to next pass");
                    flip.flip(1); flip.flip(4); // Native consumer outputs, no RT8 write.
                    require(flip.snapshot().contains(8), "consumer disturbs RT8 side");
                }
                System.out.println("mode=" + mode + " DH=" + dh + " mutation_producer=false current_producer=" + enabled
                    + " RT8=" + (enabled ? "960x600" : "native layout"));
                checks++;
            }
            System.out.println("PASS actual ShaderPackOptions -> PropertiesPreprocessor(options) -> BooleanParser: " + checks + " full-native-option cases; producer RT8/RG32F/current-side/caller/sampler source joins");
        }
    }
}
