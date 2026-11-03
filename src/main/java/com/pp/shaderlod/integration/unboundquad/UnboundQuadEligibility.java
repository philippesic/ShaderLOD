package com.pp.shaderlod.integration.unboundquad;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
/** Pure original-pack/native-options certificate shared with the normal settings transaction. */
public final class UnboundQuadEligibility {
    public static final String ARCHIVE_SHA = "98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a";
    public static final String OPTIONS_SHA = "b64d94998c99bf867e80d350a4a0a8108c58507e0401eb053a70e3aca81a5036";
    private static final Map<String,String> NATIVE_OPTIONS = loadOptions();
    public record Result(boolean eligible, String reason) {}
    private UnboundQuadEligibility() {}
    public static Map<String,String> nativeOptions() { return NATIVE_OPTIONS; }
    public static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static Map<String,String> loadOptions() {
        try (var in = UnboundQuadEligibility.class.getResourceAsStream("/assets/shaderlod/unboundquad/native398.json")) {
            if (in == null) return Map.of();
            byte[] bytes = in.readAllBytes();
            if (!OPTIONS_SHA.equals(sha(bytes))) return Map.of();
            Map<String,String> values = new Gson().fromJson(new String(bytes, StandardCharsets.UTF_8),
                new TypeToken<Map<String,String>>() {}.getType());
            return values != null && values.size() == 398 ? Map.copyOf(values) : Map.of();
        } catch (Exception | LinkageError unavailable) { return Map.of(); }
    }
    public static boolean optionsMatch(Map<String,String> effectiveOptions) {
        return NATIVE_OPTIONS.size() == 398 && NATIVE_OPTIONS.equals(effectiveOptions);
    }
    public static Result validate(Path archive, Map<String,String> effectiveOptions) {
        if (!optionsMatch(effectiveOptions)) return new Result(false, "Unbound backend requires certified native398 options");
        try {
            if (archive == null || !Files.isRegularFile(archive) || !ARCHIVE_SHA.equals(com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(archive)))
                return new Result(false, "Unbound archive is not the certified original r5.9.3 source");
            return new Result(true, "Certified original Unbound r5.9.3/native398; runtime API and config gates still required");
        } catch (Exception | LinkageError unavailable) {
            return new Result(false, "Unbound source certificate unavailable");
        }
    }
}
