package com.pp.shaderlod.integration.bindingcompat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Full-byte SHA-256 with bounded temporary storage; no metadata cache or skipped checks. */
public final class SourceArchiveDigest {
    private static final int BUFFER_BYTES = 64 * 1024;

    private SourceArchiveDigest() {}

    public static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            for (int count; (count = input.read(buffer)) != -1;) {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
