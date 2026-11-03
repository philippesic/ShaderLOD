package com.pp.shaderlod.integration.reimaginedquad;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
/** Pure original Reimagined certificate; no game linkage or admission beyond source/options. */
public final class ReimaginedQuadEligibility {
    public static final String ARCHIVE_SHA = "fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38";
    public static final String OPTIONS_SHA = "ccfca334aeaa074a74d1f8e9071b707d32161a6b771366fefcc0f87f3b1175f4";
    public static final String SOURCE_PROOF_SHA = "25e3408224e16748c86ae505b20355c50be514de2601658a74598cef1df84baf";
    public static final String API_PROOF_SHA = "806daab5da2ec861b16ff3271c481dbb579bf789743c78afee6103fe0ab2ea07";
    private static final String RESOURCE = "/assets/shaderlod/reimaginedquad/";
    private static final Map<String,String> NATIVE_OPTIONS = loadOptions();
    public record Result(boolean eligible, String reason) {}
    private ReimaginedQuadEligibility() {}
    public static Map<String,String> nativeOptions() { return NATIVE_OPTIONS; }
    public static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static byte[] pinnedResource(String name, String expected) throws Exception {
        try (var in = ReimaginedQuadEligibility.class.getResourceAsStream(RESOURCE + name)) {
            if (in == null) return null;
            byte[] bytes = in.readAllBytes();
            return expected.equals(sha(bytes)) ? bytes : null;
        }
    }
    private static Map<String,String> loadOptions() {
        try {
            if (pinnedResource("source-proof.json", SOURCE_PROOF_SHA) == null ||
                pinnedResource("api-proof.json", API_PROOF_SHA) == null) return Map.of();
            byte[] bytes = pinnedResource("native398.json", OPTIONS_SHA);
            if (bytes == null) return Map.of();
            // Decode only these exact hash-pinned flat string-map bytes.
            String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\n");
            if (lines.length != 400 || !lines[0].equals("{") || !lines[399].equals("}")) return Map.of();
            var values = new HashMap<String,String>();
            for (int i = 1; i < 399; i++) {
                String line = lines[i];
                if (line.endsWith(",")) line = line.substring(0, line.length()-1);
                int separator = line.indexOf("\": \"");
                if (!line.startsWith("  \"") || !line.endsWith("\"") || separator < 3) return Map.of();
                String key = line.substring(3, separator);
                String value = line.substring(separator+4, line.length()-1);
                if (values.put(key, value) != null) return Map.of();
            }
            return values.size() == 398 ? Map.copyOf(values) : Map.of();
        } catch (Exception | LinkageError unavailable) { return Map.of(); }
    }
    public static boolean optionsMatch(Map<String,String> effectiveOptions) {
        return NATIVE_OPTIONS.size() == 398 && NATIVE_OPTIONS.equals(effectiveOptions);
    }
    public static Result validate(Path archive, Map<String,String> effectiveOptions) {
        if (!optionsMatch(effectiveOptions)) return new Result(false, "Reimagined backend requires certified native398/source/API proof");
        try {
            if (archive == null || !Files.isRegularFile(archive) || !ARCHIVE_SHA.equals(com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(archive)))
                return new Result(false, "Reimagined archive is not the certified original r5.9.3 source");
            return new Result(true, "Certified original Reimagined r5.9.3/native398; common runtime API/config gates still required");
        } catch (Exception | LinkageError unavailable) { return new Result(false, "Reimagined certificate unavailable"); }
    }
}
