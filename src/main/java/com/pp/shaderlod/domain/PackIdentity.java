package com.pp.shaderlod.domain;

import java.util.Map;

public record PackIdentity(String archiveSha256, Map<String, String> memberSha256) {
  public PackIdentity {
    requireDigest(archiveSha256);
    memberSha256 = Map.copyOf(memberSha256);
    memberSha256.forEach((name, digest) -> {
      if (name.isBlank()
          || name.startsWith("/")
          || name.contains("\\")
          || java.util.List.of(name.split("/")).stream()
              .anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))) {
        throw new IllegalArgumentException("Unsafe archive member: " + name);
      }
      requireDigest(digest);
    });
  }

  public static void requireDigest(String digest) {
    if (digest == null || !digest.matches("[a-f0-9]{64}")) {
      throw new IllegalArgumentException("SHA-256 required");
    }
  }
}
