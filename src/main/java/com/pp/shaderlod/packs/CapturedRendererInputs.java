package com.pp.shaderlod.packs;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Immutable output of a successful generated-pack verification. No path or file is retained.
 * An Iris consumer must separately join its actual raw graph, options and compiled stages to
 * this snapshot before using it as evidence. This object alone grants no runtime permission.
 */
public final class CapturedRendererInputs {
    public record Provenance(String originalSha256, String generatedSha256,
            String recipeSha256, String stateSha256) {}
    private final String adapterId;
    private final Map<String, String> effectiveOptions;
    private final Map<String, byte[]> members;
    private final Provenance provenance;

    CapturedRendererInputs(String adapterId, Map<String, String> effectiveOptions,
            Map<String, byte[]> members) {
        this(adapterId, effectiveOptions, members, null);
    }
    CapturedRendererInputs(String adapterId, Map<String, String> effectiveOptions,
            Map<String, byte[]> members, Provenance provenance) {
        this.adapterId = java.util.Objects.requireNonNull(adapterId);
        this.effectiveOptions = Map.copyOf(effectiveOptions);
        this.provenance = provenance;
        Map<String, byte[]> copy = new TreeMap<>();
        members.forEach((name, bytes) -> copy.put(name, bytes.clone()));
        this.members = Map.copyOf(copy);
    }

    public String adapterId() { return adapterId; }
    public Provenance provenance() { return provenance; }
    public Map<String, String> effectiveOptions() { return effectiveOptions; }
    public Set<String> memberNames() { return members.keySet(); }

    /** Loading-only complete input inventory, including compile-affecting property files. */
    public Map<String,String> shaderFileHashes() {
        var hashes=new TreeMap<String,String>();
        try {
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            members.forEach((name,bytes)->{if(name.startsWith("shaders/"))
                hashes.put(name,java.util.HexFormat.of().formatHex(digest.digest(bytes)));});
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        return Map.copyOf(hashes);
    }

    /** Pinned Iris ShaderPack.readProperties uses ISO-8859-1, not GLSL's UTF-8 decoder. */
    public boolean matchesIrisProperties(String archiveMember, String observed) {
        byte[] bytes = members.get(archiveMember);
        return bytes == null ? observed == null
            : observed != null && new String(bytes, StandardCharsets.ISO_8859_1).equals(observed);
    }

    /**
     * Compare with the pinned Iris IncludeGraph's Files.readString + split("\\R") semantics.
     * No byte-hash equivalence is implied: Iris normalizes line endings and trailing empty lines.
     * Invalid UTF-8 refuses, matching Files.readString's strict decoder rather than replacement.
     */
    public boolean matchesIrisLines(String archiveMember, List<String> observedLines) {
        byte[] bytes = members.get(archiveMember);
        if (bytes == null || observedLines == null) return false;
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            return List.of(decoded.split("\\R")).equals(observedLines);
        } catch (CharacterCodingException invalid) { return false; }
    }
}
