package com.pp.shaderlod.integration;

import com.pp.shaderlod.lod.adapter.AdapterIdentity;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Exact source contract registered independently of an adapter's work declarations. */
public record SourceBinding(AdapterIdentity adapter, String sourceSha256,
                            Map<String, String> memberSha256, Map<String, Set<String>> requiredOptions) {
    public SourceBinding {
        Objects.requireNonNull(adapter, "adapter");
        requireDigest(sourceSha256);
        memberSha256 = Map.copyOf(memberSha256);
        if (memberSha256.isEmpty()) throw new IllegalArgumentException("Pin consuming source members");
        memberSha256.forEach((name, hash) -> { requireMember(name); requireDigest(hash); });
        var options = new java.util.LinkedHashMap<String, Set<String>>();
        requiredOptions.forEach((key, values) -> {
            if (key == null || !key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Require an actual Iris option key");
            }
            var allowed = Set.copyOf(values);
            if (allowed.isEmpty()) throw new IllegalArgumentException("Declare accepted effective option values");
            options.put(key, allowed);
        });
        requiredOptions = Map.copyOf(options);
    }

    public boolean matches(SourceFingerprint source) {
        return sourceSha256.equals(source.sourceSha256()) && memberSha256.entrySet().stream()
                .allMatch(member -> member.getValue().equals(source.memberSha256().get(member.getKey())));
    }

    public boolean acceptsOptions(Map<String, String> effectiveOptions) {
        return requiredOptions.entrySet().stream()
                .allMatch(option -> effectiveOptions.get(option.getKey()) != null
                        && option.getValue().contains(effectiveOptions.get(option.getKey())));
    }

    static void requireDigest(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Require exact SHA-256");
    }

    static void requireMember(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("\\")
                || java.util.Arrays.stream(name.split("/", -1)).anyMatch(part -> part.isEmpty()
                    || part.equals(".") || part.equals(".."))) {
            throw new IllegalArgumentException("Require an exact relative source member");
        }
    }
}
