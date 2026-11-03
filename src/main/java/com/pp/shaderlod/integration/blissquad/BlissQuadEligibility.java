package com.pp.shaderlod.integration.blissquad;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
/** Pure original Bliss certificate; no game linkage or admission beyond source/options. */
public final class BlissQuadEligibility {
    public static final String ARCHIVE_SHA = "f41db92acc585fe9ffe0cc124d6ecaea27cef9ded91129ebc2a8e60226947b2c";
    public static final String OPTIONS_SHA = "c6c53b96a18a0f6d6a8a6a3019f7041315aa5e626052f90201665cea21cd7883";
    public static final String SOURCE_PROOF_SHA = "be8d75438ade58edd1518b30c7000edc2c0df80ce2696cc2884819dd9564c73b";
    public static final String API_PROOF_SHA = "806daab5da2ec861b16ff3271c481dbb579bf789743c78afee6103fe0ab2ea07";
    private static final String RESOURCE = "/assets/shaderlod/blissquad/";
    private static final Map<String,String> NATIVE_OPTIONS = loadOptions();
    public record Result(boolean eligible, String reason) {}
    private BlissQuadEligibility() {}
    public static Map<String,String> nativeOptions() { return NATIVE_OPTIONS; }
    public static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static byte[] pinnedResource(String name, String expected) throws Exception {
        try (var in = BlissQuadEligibility.class.getResourceAsStream(RESOURCE + name)) {
            if (in == null) return null;
            byte[] bytes = in.readAllBytes();
            return expected.equals(sha(bytes)) ? bytes : null;
        }
    }
    private static Map<String,String> loadOptions() {
        try {
            if (pinnedResource("source-proof.json", SOURCE_PROOF_SHA) == null ||
                pinnedResource("api-proof.json", API_PROOF_SHA) == null) return Map.of();
            byte[] bytes = pinnedResource("native463.json", OPTIONS_SHA);
            if (bytes == null) return Map.of();
            // Decode only these exact hash-pinned flat string-map bytes.
            String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\n");
            if (lines.length != 465 || !lines[0].equals("{") || !lines[464].equals("}")) return Map.of();
            var values = new HashMap<String,String>();
            for (int i = 1; i < 464; i++) {
                String line = lines[i];
                if (line.endsWith(",")) line = line.substring(0, line.length()-1);
                int separator = line.indexOf("\": \"");
                if (!line.startsWith("  \"") || !line.endsWith("\"") || separator < 3) return Map.of();
                String key = line.substring(3, separator);
                String value = line.substring(separator+4, line.length()-1);
                if (values.put(key, value) != null) return Map.of();
            }
            return values.size() == 463 ? Map.copyOf(values) : Map.of();
        } catch (Exception | LinkageError unavailable) { return Map.of(); }
    }
    public static boolean optionsMatch(Map<String,String> effectiveOptions) {
        return NATIVE_OPTIONS.size() == 463 && NATIVE_OPTIONS.equals(effectiveOptions);
    }
    public static Result validate(Path archive, Map<String,String> effectiveOptions) {
        if (!optionsMatch(effectiveOptions)) return new Result(false, "Bliss backend requires certified native463/source/API proof");
        try {
            if (archive == null || !Files.isRegularFile(archive) || !ARCHIVE_SHA.equals(com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(archive)))
                return new Result(false, "Bliss archive is not the certified original 2.1.2 source");
            return new Result(true, "Certified original Bliss 2.1.2/native463; common runtime API/config gates still required");
        } catch (Exception | LinkageError unavailable) { return new Result(false, "Bliss certificate unavailable"); }
    }
}
