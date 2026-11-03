package com.pp.shaderlod.integration.sildurquad;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
/** Pure original Sildur certificate; no game linkage or admission beyond source/options. */
public final class SildurQuadEligibility {
    public static final String ARCHIVE_SHA = "824504bc937f9570277c0a809f5059ae4a5fe70c413cf4459b6ccff4dfed7ebe";
    public static final String OPTIONS_SHA = "795a8b210019380e8d120924aa4ef24555afc8bf9ea5ff9bffe8dafb9d631bd3";
    public static final String SOURCE_PROOF_SHA = "896abface1ed04cf72f79f8460e55fa5821702a69382d9a5691ef47583cf6d51";
    public static final String API_PROOF_SHA = "806daab5da2ec861b16ff3271c481dbb579bf789743c78afee6103fe0ab2ea07";
    private static final String RESOURCE = "/assets/shaderlod/sildurquad/";
    private static final Map<String,String> NATIVE_OPTIONS = loadOptions();
    public record Result(boolean eligible, String reason) {}
    private SildurQuadEligibility() {}
    public static Map<String,String> nativeOptions() { return NATIVE_OPTIONS; }
    public static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static byte[] pinnedResource(String name, String expected) throws Exception {
        try (var in = SildurQuadEligibility.class.getResourceAsStream(RESOURCE + name)) {
            if (in == null) return null;
            byte[] bytes = in.readAllBytes();
            return expected.equals(sha(bytes)) ? bytes : null;
        }
    }
    private static Map<String,String> loadOptions() {
        try {
            if (pinnedResource("source-proof.json", SOURCE_PROOF_SHA) == null ||
                pinnedResource("api-proof.json", API_PROOF_SHA) == null) return Map.of();
            byte[] bytes = pinnedResource("native115.json", OPTIONS_SHA);
            if (bytes == null) return Map.of();
            // Decode only these exact hash-pinned flat string-map bytes.
            String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\n");
            if (lines.length != 117 || !lines[0].equals("{") || !lines[116].equals("}")) return Map.of();
            var values = new HashMap<String,String>();
            for (int i = 1; i < 116; i++) {
                String line = lines[i];
                if (line.endsWith(",")) line = line.substring(0, line.length()-1);
                int separator = line.indexOf("\": \"");
                if (!line.startsWith("  \"") || !line.endsWith("\"") || separator < 3) return Map.of();
                String key = line.substring(3, separator);
                String value = line.substring(separator+4, line.length()-1);
                if (values.put(key, value) != null) return Map.of();
            }
            return values.size() == 115 ? Map.copyOf(values) : Map.of();
        } catch (Exception | LinkageError unavailable) { return Map.of(); }
    }
    public static boolean optionsMatch(Map<String,String> effectiveOptions) {
        return NATIVE_OPTIONS.size() == 115 && NATIVE_OPTIONS.equals(effectiveOptions);
    }
    /** Union source path preserves the original-only finite-trial validator unchanged. */
    public static Result validateUnion(Path archive, Map<String,String> effectiveOptions) {
        var original = validate(archive,effectiveOptions);
        if (original.eligible()) return original;
        var union = SildurSourceCertificate.validate(archive,effectiveOptions);
        return new Result(union.eligible(),union.reason());
    }
    public static Result validate(Path archive, Map<String,String> effectiveOptions) {
        if (!optionsMatch(effectiveOptions)) return new Result(false, "Sildur backend requires certified native115/source/API proof");
        try {
            if (archive == null || !Files.isRegularFile(archive) || !ARCHIVE_SHA.equals(com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(archive)))
                return new Result(false, "Sildur archive is not the certified original v2.02 Extreme source");
            return new Result(true, "Certified original Sildur v2.02 Extreme/native115; common runtime API/config gates still required");
        } catch (Exception | LinkageError unavailable) { return new Result(false, "Sildur certificate unavailable"); }
    }
}
