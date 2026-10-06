package com.pp.shaderlod.integration;

import java.util.Map;

/** Observed container/source digest and exact members; neither establishes shader consumption. */
public record SourceFingerprint(String sourceSha256, Map<String, String> memberSha256) {
    public SourceFingerprint {
        SourceBinding.requireDigest(sourceSha256);
        memberSha256 = Map.copyOf(memberSha256);
        memberSha256.forEach((name, hash) -> { SourceBinding.requireMember(name); SourceBinding.requireDigest(hash); });
    }
}
