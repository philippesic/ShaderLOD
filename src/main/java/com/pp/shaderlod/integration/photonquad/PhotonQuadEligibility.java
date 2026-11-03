package com.pp.shaderlod.integration.photonquad;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Explicit original Photon input/source certificate; runtime API/config gates remain required.
 * JDK only; this never links optional Minecraft/Iris/Sodium classes at bootstrap.
 * Hash-pinned proof records actual original SOLID/CUTOUT sources; it is not a live driver readback.
 */
public final class PhotonQuadEligibility {
    public static final String ARCHIVE_SHA = "120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead";
    public static final String OPTIONS_SHA = "9b1d4db811f54cc986c97e8c8ce917f6731fbaf1f006927ccdef351bb3c5797f";
    public static final String SOURCE_SHA = "24dce64cea2682c5d2b4d8f513531c25eadeb961bd3dc0293bd061f04c5ec5ea";
    private static final Map<String,String> NATIVE_OPTIONS = loadOptions();
    private static final boolean SOURCE_PROOF_PRESENT = sourceProofPresent();
    public record Result(boolean eligible, String reason) {}
    private PhotonQuadEligibility() {}
    public static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
    private static Map<String,String> loadOptions() {
        try (var in = PhotonQuadEligibility.class.getResourceAsStream("/assets/shaderlod/photonquad/native366.json")) {
            if (in == null) return Map.of();
            byte[] data = in.readAllBytes();
            if (!OPTIONS_SHA.equals(sha(data))) return Map.of();
            // Explicit parser for this hash-pinned, flat, unescaped string map only.
            // Different bytes never reach parsing; this is not a general JSON reader.
            var entry = Pattern.compile("  \"([A-Za-z0-9_]+)\": \"([A-Za-z0-9_.+\\-]+)\",?");
            String[] lines = new String(data, StandardCharsets.UTF_8).split("\\n");
            if (!lines[0].equals("{") || !lines[lines.length-1].equals("}")) return Map.of();
            var values = new TreeMap<String,String>();
            for (int i=1; i<lines.length-1; i++) {
                var match = entry.matcher(lines[i]);
                if (!match.matches() || values.put(match.group(1),match.group(2)) != null) return Map.of();
            }
            return values.size() == 366 ? Map.copyOf(values) : Map.of();
        } catch (Exception | LinkageError unavailable) { return Map.of(); }
    }
    public static Map<String,String> nativeOptions() { return NATIVE_OPTIONS; }
    public static boolean optionsMatch(Map<String,String> effective) {
        return NATIVE_OPTIONS.size() == 366 && NATIVE_OPTIONS.equals(effective);
    }
    /** Input identity is necessary, not renderer eligibility. Never use this as a runtime gate. */
    public static Result validateInputs(Path archive, Map<String,String> effective) {
        if (!optionsMatch(effective)) return new Result(false, "Photon requires certified native366 options");
        try {
            if (archive == null || !Files.isRegularFile(archive) || !ARCHIVE_SHA.equals(com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(archive)))
                return new Result(false, "Photon archive is not certified original v1.3b");
            return new Result(true, "Original Photon v1.3b/native366 inputs match; runtime API and config gates still required");
        } catch (Exception | LinkageError unavailable) { return new Result(false,"Photon input certificate unavailable"); }
    }
    private static boolean sourceProofPresent() {
        try (var in = PhotonQuadEligibility.class.getResourceAsStream("/assets/shaderlod/photonquad/source-proof.json")) {
            return in != null && SOURCE_SHA.equals(sha(in.readAllBytes()));
        } catch (Exception | LinkageError unavailable) { return false; }
    }
    public static Result validate(Path archive, Map<String,String> effective) {
        Result inputs = validateInputs(archive,effective);
        if (!inputs.eligible()) return inputs;
        if (!SOURCE_PROOF_PRESENT) return new Result(false,"Photon consuming-source certificate unavailable");
        return new Result(true,"Certified original Photon v1.3b/native366 SOLID/CUTOUT sources; runtime API and config gates still required");
    }
}
