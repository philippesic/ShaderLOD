package com.pp.shaderlod.optimization.vrs;

import com.google.gson.JsonParser;
import com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

/** Native, unmodified BSL10.1.8 bloom prefilter. No generated pack or shader rewriting. */
public final class BloomVrsSourceProof {
    public static final String ARCHIVE_SHA = "36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5";
    public static final String PROGRAM = "composite4";
    private static final Map<String,String> OPTIONS = options();
    private BloomVrsSourceProof() {}
    private static Map<String,String> options() {
        try (var in = BloomVrsSourceProof.class.getResourceAsStream("/assets/shaderlod/bslquad/native384.json")) {
            if (in == null) return Map.of();
            byte[] bytes = in.readAllBytes();
            if (!BloomVrsPins.sha(bytes).equals("0aaa1276ea6e24106e6ed062a3bd149d1b53c09693d04a4637dcb19e6c43c394")) return Map.of();
            var out = new TreeMap<String,String>();
            JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject()
                    .entrySet().forEach(e -> out.put(e.getKey(),e.getValue().getAsString()));
            return out.size() == 384 ? Map.copyOf(out) : Map.of();
        } catch (Exception unavailable) { return Map.of(); }
    }
    public static boolean optionsMatch(Map<String,String> actual) {
        return OPTIONS.size() == 384 && OPTIONS.equals(actual);
    }
    public static boolean nativeArchive(Path path,Map<String,String> actual) {
        try {
            return optionsMatch(actual) && !Files.isSymbolicLink(path) && Files.isRegularFile(path)
                    && ARCHIVE_SHA.equals(BloomVrsPins.sha(Files.readAllBytes(path)));
        } catch (Exception unavailable) { return false; }
    }
    public static IrisCompiledSource.Certificate certify(ProgramSource source,long epoch) throws Exception {
        if (source == null || !PROGRAM.equals(source.getName())) return null;
        var set = source.getParent();
        if (!(set instanceof IrisCompiledSource.SetView sv)
                || !(set.getPack() instanceof IrisCompiledSource.PackView pv)) return null;
        var input = pv.shaderlod$makeupPackInput(); var si = sv.shaderlod$makeupSetInput();
        if (input == null || si == null || si.pack() != set.getPack()
                || si.provider() != input.provider() || si.properties() != input.properties()
                || !"/world0".equals(si.directory().getPathString())
                || !input.options().current(set.getPack().getShaderPackOptions())
                || !optionsMatch(input.options().values())) return null;
        String selected = Iris.getIrisConfig().getShaderPackName().orElse(null);
        if (selected == null || !Path.of(selected).getFileName().toString().equals(selected)) return null;
        Path path = Iris.getShaderpacksDirectory().resolve(selected);
        if (!nativeArchive(path,input.options().values()) || input.observation() == null
                || !input.observation().matchesArchive(path)
                || !input.options().captured().rawGraph().getFailures().isEmpty()
                || !input.options().rewritten().getFailures().isEmpty()) return null;
        try (var archive = new ZipFile(path.toFile())) {
            for (var node : input.options().captured().rawGraph().getNodes().entrySet()) {
                var entry = archive.getEntry("shaders" + node.getKey().getPathString());
                if (entry == null) return null;
                String raw;
                try (var in = archive.getInputStream(entry)) {
                    raw = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(in.readAllBytes())).toString();
                }
                if (!List.of(raw.split("\\R")).equals(node.getValue().getLines())) return null;
            }
        }
        var replay = IrisCompiledSource.replay(input.options(),input.glslDefines(),si.directory(),PROGRAM);
        if (replay == null || replay.vertex() == null || replay.fragment() == null
                || replay.geometry() != null || replay.control() != null || replay.evaluation() != null
                || !replay.equals(IrisCompiledSource.Stages.of(source))) return null;
        return new IrisCompiledSource.Certificate(source,set,input,selected,replay,epoch);
    }
}
