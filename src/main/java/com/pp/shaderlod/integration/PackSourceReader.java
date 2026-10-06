package com.pp.shaderlod.integration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.zip.ZipFile;

/** Read once at uniform registration. No archive I/O belongs in a per-frame supplier. */
public final class PackSourceReader {
    private PackSourceReader() {}

    public static SourceFingerprint read(Path source) throws IOException {
        var members = new TreeMap<String, String>();
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            try (var paths = Files.walk(source)) {
                for (var path : paths.toList()) {
                    if (Files.isSymbolicLink(path)) throw new IOException("Symlink source member");
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                    String name = source.relativize(path).toString().replace(java.io.File.separatorChar, '/');
                    SourceBinding.requireMember(name);
                    try (var input = Files.newInputStream(path)) { members.put(name, hash(input)); }
                }
            }
            // Directory source identity: sorted UTF-8 member, NUL, lowercase hex hash, LF.
            var digest = digest();
            members.forEach((name, hash) -> digest.update((name + "\0" + hash + "\n").getBytes(StandardCharsets.UTF_8)));
            return new SourceFingerprint(HexFormat.of().formatHex(digest.digest()), members);
        }
        if (Files.isSymbolicLink(source)) throw new IOException("Symlink source archive");
        final String archiveHash;
        try (var input = Files.newInputStream(source)) { archiveHash = hash(input); }
        try (var archive = new ZipFile(source.toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                SourceBinding.requireMember(entry.getName());
                try (var input = archive.getInputStream(entry)) {
                    if (members.putIfAbsent(entry.getName(), hash(input)) != null) {
                        throw new IOException("Duplicate source member: " + entry.getName());
                    }
                }
            }
        }
        return new SourceFingerprint(archiveHash, members);
    }

    private static String hash(InputStream input) throws IOException {
        var digest = digest();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
