package com.pp.shaderlod.integration.bslquad;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
/** Pure original Bsl certificate; no game linkage or admission beyond source/options. */
public final class BslQuadEligibility {
    public static final String ARCHIVE_SHA = "36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5";
    public static final String OPTIONS_SHA = "0aaa1276ea6e24106e6ed062a3bd149d1b53c09693d04a4637dcb19e6c43c394";
    public static final String SOURCE_PROOF_SHA = "82d47e661df8ead0923ee1b05380561910c5465a67ca215b94e26fbd1cb86ee2";
    public static final String API_PROOF_SHA = "806daab5da2ec861b16ff3271c481dbb579bf789743c78afee6103fe0ab2ea07";
    private static final String RESOURCE = "/assets/shaderlod/bslquad/";
    private static final Map<String,String> NATIVE_OPTIONS = loadOptions();
    public record Result(boolean eligible, String reason) {}
    private BslQuadEligibility() {}
    public static Map<String,String> nativeOptions() { return NATIVE_OPTIONS; }
    public static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static byte[] pinnedResource(String name, String expected) throws Exception {
        try (var in = BslQuadEligibility.class.getResourceAsStream(RESOURCE + name)) {
            if (in == null) return null;
            byte[] bytes = in.readAllBytes();
            return expected.equals(sha(bytes)) ? bytes : null;
        }
    }
    private static Map<String,String> loadOptions() {
        try {
            if (pinnedResource("source-proof.json", SOURCE_PROOF_SHA) == null ||
                pinnedResource("api-proof.json", API_PROOF_SHA) == null) return Map.of();
            byte[] bytes = pinnedResource("native384.json", OPTIONS_SHA);
            if (bytes == null) return Map.of();
            // Decode only these exact hash-pinned flat string-map bytes.
            String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\n");
            if (lines.length != 386 || !lines[0].equals("{") || !lines[385].equals("}")) return Map.of();
            var values = new HashMap<String,String>();
            for (int i = 1; i < 385; i++) {
                String line = lines[i];
                if (line.endsWith(",")) line = line.substring(0, line.length()-1);
                int separator = line.indexOf("\": \"");
                if (!line.startsWith("  \"") || !line.endsWith("\"") || separator < 3) return Map.of();
                String key = line.substring(3, separator);
                String value = line.substring(separator+4, line.length()-1);
                if (values.put(key, value) != null) return Map.of();
            }
            return values.size() == 384 ? Map.copyOf(values) : Map.of();
        } catch (Exception | LinkageError unavailable) { return Map.of(); }
    }
    public static boolean optionsMatch(Map<String,String> effectiveOptions) {
        return NATIVE_OPTIONS.size() == 384 && NATIVE_OPTIONS.equals(effectiveOptions);
    }
    public static Result validate(Path archive, Map<String,String> effectiveOptions) {
        if (!optionsMatch(effectiveOptions)) return new Result(false, "Bsl backend requires certified native384/source/API proof");
        try {
            if (archive == null || !Files.isRegularFile(archive) || !ARCHIVE_SHA.equals(com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(archive)))
                return new Result(false, "Bsl archive is not the certified original 10.1.8 source");
            return new Result(true, "Certified original Bsl 10.1.8/native384; common runtime API/config gates still required");
        } catch (Exception | LinkageError unavailable) { return new Result(false, "Bsl certificate unavailable"); }
    }
}
