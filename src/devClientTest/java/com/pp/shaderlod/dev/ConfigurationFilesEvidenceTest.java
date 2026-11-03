package com.pp.shaderlod.dev;

import java.nio.file.Files;
import java.nio.file.Path;

/** Executes the real boundary collector without Minecraft or production mod classes. */
public final class ConfigurationFilesEvidenceTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Files.createDirectories(root.resolve("config/nested"));
        Files.createDirectories(root.resolve("shaderpacks"));
        Files.writeString(root.resolve("options.txt"), "fov:70\n");
        Files.writeString(root.resolve("config/nested/example.toml"), "value = 1\n");
        Files.write(root.resolve("config/unknown.bin"), new byte[]{(byte) 0xff, 0});
        Files.writeString(root.resolve("shaderpacks/pack.zip.txt"), "TAAU=true\n");
        Files.write(root.resolve("shaderpacks/pack.zip"), new byte[]{1});
        var first = ConfigurationFilesEvidence.capture(root);
        check(first.get("complete").getAsBoolean());
        check(first.getAsJsonObject("files").size() == 4);
        check(first.getAsJsonObject("files").getAsJsonObject("options.txt").get("utf8").getAsString().equals("fov:70\n"));
        var binary = first.getAsJsonObject("files").getAsJsonObject("config/unknown.bin");
        check(!binary.has("utf8") && binary.has("sha256") && binary.get("bytes").getAsInt() == 2);
        Files.writeString(root.resolve("options.txt"), "fov:71\n");
        var second = ConfigurationFilesEvidence.capture(root);
        check(second.get("complete").getAsBoolean());
        check(!first.getAsJsonObject("files").get("options.txt").equals(second.getAsJsonObject("files").get("options.txt")));
        Files.createSymbolicLink(root.resolve("config/link"), root.resolve("options.txt").toAbsolutePath());
        var linked = ConfigurationFilesEvidence.capture(root);
        check(!linked.get("complete").getAsBoolean() && !linked.getAsJsonArray("errors").isEmpty());
        Files.delete(root.resolve("config/link"));
        Files.write(root.resolve("config/oversize.bin"), new byte[1024 * 1024 + 1]);
        check(!ConfigurationFilesEvidence.capture(root).get("complete").getAsBoolean());
        Files.delete(root.resolve("config/oversize.bin"));
        for (int i = 0; i < 129; i++) Files.writeString(root.resolve("config/file" + i), "x");
        check(!ConfigurationFilesEvidence.capture(root).get("complete").getAsBoolean());
        for (int i = 0; i < 129; i++) Files.delete(root.resolve("config/file" + i));
        Files.delete(root.resolve("options.txt"));
        check(!ConfigurationFilesEvidence.capture(root).get("complete").getAsBoolean());
        System.out.println("ConfigurationFilesEvidence: " + checks + " real filesystem checks passed");
    }
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
}
