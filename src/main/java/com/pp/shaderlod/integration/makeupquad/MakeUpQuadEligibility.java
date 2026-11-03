package com.pp.shaderlod.integration.makeupquad;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
/** Pure original-pack/native-options certificate shared with the normal settings transaction. */
public final class MakeUpQuadEligibility {
    public static final String ARCHIVE_SHA = "0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8";
    public static final String OPTIONS_SHA = "0c11f31b3d5e51d9d1bff56a84ac13a3f1265472f7313997a493c74435a418bb";
    private static final Map<String,String> NATIVE_OPTIONS = loadOptions();
    public record Result(boolean eligible, String reason) {}
    private MakeUpQuadEligibility() {}
    public static Map<String,String> nativeOptions() { return NATIVE_OPTIONS; }
    public static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static Map<String,String> loadOptions() {
        try (var in = MakeUpQuadEligibility.class.getResourceAsStream("/assets/shaderlod/makeupquad/native100.json")) {
            if (in == null) return Map.of();
            byte[] bytes = in.readAllBytes();
            if (!OPTIONS_SHA.equals(sha(bytes))) return Map.of();
            Map<String,String> values = new Gson().fromJson(new String(bytes, StandardCharsets.UTF_8),
                new TypeToken<Map<String,String>>() {}.getType());
            return values != null && values.size() == 100 ? Map.copyOf(values) : Map.of();
        } catch (Exception | LinkageError unavailable) { return Map.of(); }
    }
    public static boolean optionsMatch(Map<String,String> effectiveOptions) {
        return NATIVE_OPTIONS.size() == 100 && NATIVE_OPTIONS.equals(effectiveOptions);
    }
    public static Result validate(Path archive, Map<String,String> effectiveOptions) {
        if (!optionsMatch(effectiveOptions)) return new Result(false, "MakeUp backend requires certified native100 options");
        try {
            if (archive == null || !Files.isRegularFile(archive) || !ARCHIVE_SHA.equals(com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(archive)))
                return new Result(false, "MakeUp archive is not the certified original 9.5g source");
            return new Result(true, "Certified original MakeUp 9.5g/native100; runtime API and config gates still required");
        } catch (Exception | LinkageError unavailable) {
            return new Result(false, "MakeUp source certificate unavailable");
        }
    }
}
