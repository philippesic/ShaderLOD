package com.pp.shaderlod.domain;

import java.util.Objects;

public final class ResourcePatch {
  private final String member;
  private final String expectedSha256;
  private final byte[] replacement;

  public ResourcePatch(String member, String expectedSha256, byte[] replacement) {
    if (member == null
        || !member.startsWith("shaders/")
        || java.util.List.of(member.split("/")).stream()
            .anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))
        || member.contains("\\")) {
      throw new IllegalArgumentException("Patch must name a safe shader member");
    }
    PackIdentity.requireDigest(expectedSha256);
    this.member = member;
    this.expectedSha256 = expectedSha256;
    this.replacement = Objects.requireNonNull(replacement).clone();
  }

  public String member() {
    return member;
  }

  public String expectedSha256() {
    return expectedSha256;
  }

  public byte[] replacement() {
    return replacement.clone();
  }
}
