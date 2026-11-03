package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Real filesystem and utility checks; main entry point needs no Minecraft or GPU. */
public final class LoadedArtifactEvidenceTest {
    private static int checks;
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) throw new IllegalArgumentException("Pass an isolated ignored output directory");
        Path output = Path.of(arguments[0]);
        Files.createDirectories(output);
        Path root = Files.createTempDirectory(output, "roots-");
        Path artifact = root.resolve("artifact+with space.jar");
        Files.writeString(artifact, "abc", StandardCharsets.UTF_8);
        String abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        equal(LoadedArtifactEvidence.fileSha256(artifact), abc, "actual file SHA256");
        var observed = LoadedArtifactEvidence.observe(artifact, mods("a"));
        equal(observed.get("identity").getAsString(), abc, "direct per-root identity");
        equal(observed.get("uri").getAsString(), artifact.toUri().toString(), "raw Java URI preserved");
        equal(observed.get("path").getAsString(), artifact.toAbsolutePath().toString(), "raw absolute path");
        equal(observed.get("filesystem_provider").getAsString(), "file", "actual provider");
        pass(LoadedArtifactEvidence.snapshot(mods("a"), List.of(observed)), 1);

        var duplicatePhysical = LoadedArtifactEvidence.observe(artifact, mods("b"));
        var combined = LoadedArtifactEvidence.snapshot(mods("a", "b"), List.of(observed, duplicatePhysical));
        pass(combined, 1);
        equal(combined.getAsJsonArray("loaded_artifact_inventory").get(0).getAsJsonObject().getAsJsonObject("mods"), mods("a", "b"), "coalesced complete disjoint mods");
        equal(observed.getAsJsonObject("mods"), mods("a"), "input observations detached");
        observed.getAsJsonObject("mods").addProperty("mutated", "1");
        equal(combined.getAsJsonArray("loaded_artifact_inventory").get(0).getAsJsonObject().getAsJsonObject("mods"), mods("a", "b"), "snapshot detached from later input mutation");
        observed = LoadedArtifactEvidence.observe(artifact, mods("a"));

        var firstVirtual = virtual("a", "union:/outer.jar%230!/", abc);
        var secondVirtual = virtual("b", "union:/outer.jar%231!/", abc);
        pass(LoadedArtifactEvidence.snapshot(mods("a", "b"), List.of(firstVirtual, secondVirtual)), 2);
        equal(firstVirtual.get("path").getAsString(), "", "legitimate empty virtual path retained");
        var hashConflict = observed.deepCopy(); hashConflict.add("mods", mods("b")); hashConflict.addProperty("identity", "0".repeat(64));
        fail(LoadedArtifactEvidence.snapshot(mods("a", "b"), List.of(observed, hashConflict)), 2, "Conflicting");
        var pathConflict = observed.deepCopy(); pathConflict.add("mods", mods("b")); pathConflict.addProperty("path", "different raw path");
        fail(LoadedArtifactEvidence.snapshot(mods("a", "b"), List.of(observed, pathConflict)), 2, "Conflicting");
        var duplicateMod = LoadedArtifactEvidence.snapshot(mods("a"), List.of(observed, observed.deepCopy()));
        fail(duplicateMod, 2, "Duplicate artifact mod");
        equal(duplicateMod.get("complete_mod_coverage").getAsBoolean(), false, "duplicate mods prevent complete exact coverage");
        var wrongVersion = mods("a"); wrongVersion.addProperty("a", "2");
        fail(LoadedArtifactEvidence.snapshot(wrongVersion, List.of(observed)), 1, "exactly cover");
        fail(LoadedArtifactEvidence.snapshot(mods("a", "b"), List.of(observed)), 1, "exactly cover");
        fail(LoadedArtifactEvidence.snapshot(mods("b"), List.of(observed)), 1, "exactly cover");
        var unknown = observed.deepCopy(); unknown.add("identity", JsonNull.INSTANCE);
        fail(LoadedArtifactEvidence.snapshot(mods("a"), List.of(unknown)), 1, "Unknown, incomplete");
        var unreadable = observed.deepCopy(); unreadable.addProperty("identity_error", "fixture: denied");
        fail(LoadedArtifactEvidence.snapshot(mods("a"), List.of(unreadable)), 1, "Unknown, incomplete");
        var uriMissing = observed.deepCopy(); uriMissing.add("uri", JsonNull.INSTANCE); uriMissing.addProperty("locator_error", "fixture URI unavailable");
        var unknownSnapshot = LoadedArtifactEvidence.snapshot(mods("a"), List.of(uriMissing));
        fail(unknownSnapshot, 1, "Unknown or failed artifact locator");
        equal(unknownSnapshot.getAsJsonArray("loaded_artifact_inventory").get(0), uriMissing, "unknown locator retained intact");
        equal(unknownSnapshot.getAsJsonArray("loaded_artifact_inventory").get(0).getAsJsonObject().get("uri"), JsonNull.INSTANCE, "unknown URI null retained in memory");
        var missingUri = uriMissing.deepCopy(); missingUri.remove("uri");
        fail(LoadedArtifactEvidence.snapshot(mods("a"), List.of(missingUri)), 1, "Unknown or failed");
        var nonexistent = LoadedArtifactEvidence.observe(root.resolve("missing"), mods("a"));
        fail(LoadedArtifactEvidence.snapshot(mods("a"), List.of(nonexistent)), 1, "Unknown, incomplete");
        var nullRoot = LoadedArtifactEvidence.observe(null, mods("a"));
        fail(LoadedArtifactEvidence.snapshot(mods("a"), List.of(nullRoot)), 1, "Unknown or failed");
        var nullObservation = new ArrayList<JsonObject>(); nullObservation.add(null);
        fail(LoadedArtifactEvidence.snapshot(mods("a"), nullObservation), 1, "Null artifact observation");
        fail(LoadedArtifactEvidence.snapshot(mods("a"), null), 0, "observations missing");
        fail(LoadedArtifactEvidence.snapshot(null, List.of(observed)), 1, "registry missing");
        var malformed = observed.deepCopy(); malformed.add("mods", JsonNull.INSTANCE);
        fail(LoadedArtifactEvidence.snapshot(mods("a", "b"), List.of(malformed, duplicatePhysical)), 2, "Missing, empty or malformed");
        var empty = LoadedArtifactEvidence.observe(artifact, new JsonObject());
        fail(LoadedArtifactEvidence.snapshot(mods("a"), List.of(empty)), 1, "Missing, empty or malformed");
        var badHash = observed.deepCopy(); badHash.addProperty("identity", "not a hash");
        fail(LoadedArtifactEvidence.snapshot(mods("a"), List.of(badHash)), 1, "Unknown, incomplete");
        var unknownSameRoot = unknown.deepCopy(); unknownSameRoot.add("mods", mods("b"));
        fail(LoadedArtifactEvidence.snapshot(mods("a", "b"), List.of(observed, unknownSameRoot)), 2, "Conflicting");
        var differentProvider = observed.deepCopy(); differentProvider.add("mods", mods("b")); differentProvider.addProperty("filesystem_provider", "union");
        pass(LoadedArtifactEvidence.snapshot(mods("a", "b"), List.of(observed, differentProvider)), 2);

        Path tree = Files.createDirectory(root.resolve("tree"));
        Path nested = Files.createDirectory(tree.resolve("A"));
        Files.writeString(tree.resolve("z.txt"), "z");
        Files.writeString(nested.resolve("first.txt"), "first");
        Files.createSymbolicLink(tree.resolve("file-link"), artifact);
        Files.createSymbolicLink(tree.resolve("dir-link"), root);
        var directory = LoadedArtifactEvidence.directoryIdentity(tree);
        var expectedDirectory = new JsonObject();
        expectedDirectory.addProperty("kind", "development directory; regular files only, symlinks excluded");
        expectedDirectory.addProperty("complete", true);
        expectedDirectory.addProperty("files", 2);
        expectedDirectory.addProperty("bytes", 6);
        String entries = "A/first.txt\0" + independentHash("first") + "\n" + "z.txt\0" + independentHash("z") + "\n";
        expectedDirectory.addProperty("tree_sha256", independentHash(entries));
        equal(directory, expectedDirectory, "exact directory schema, sorted relative paths and symlink exclusion");
        var actualTree = LoadedArtifactEvidence.observe(tree, mods("a"));
        equal(actualTree.get("identity"), expectedDirectory, "observe uses exact directory identity");
        pass(LoadedArtifactEvidence.snapshot(mods("a"), List.of(actualTree)), 1);
        var fractional = actualTree.deepCopy(); fractional.getAsJsonObject("identity").addProperty("files", .5);
        fail(LoadedArtifactEvidence.snapshot(mods("a"), List.of(fractional)), 1, "Unknown, incomplete");
        Path emptyTree = Files.createDirectory(root.resolve("empty-tree"));
        equal(LoadedArtifactEvidence.directoryIdentity(emptyTree).get("tree_sha256").getAsString(), independentHash(""), "empty directory hash");
        Path oversizedTree = Files.createDirectory(root.resolve("oversized-tree"));
        try (var file = new java.io.RandomAccessFile(oversizedTree.resolve("big").toFile(), "rw")) { file.setLength(32L * 1024 * 1024 + 1); }
        var overBytes = LoadedArtifactEvidence.directoryIdentity(oversizedTree);
        incomplete(overBytes);
        fail(LoadedArtifactEvidence.snapshot(mods("a"), List.of(LoadedArtifactEvidence.observe(oversizedTree, mods("a")))), 1, "Unknown, incomplete");
        Path manyTree = Files.createDirectory(root.resolve("many-tree"));
        for (int index = 0; index < 5001; index++) Files.createFile(manyTree.resolve(String.format("%04d", index)));
        incomplete(LoadedArtifactEvidence.directoryIdentity(manyTree));
        var report = new JsonObject(); report.addProperty("checks", checks);
        report.addProperty("classification", "Actual utility filesystem/JSON checks; no Minecraft, GPU, ModList execution or loaded class proof");
        report.add("coalesced_snapshot", combined); report.add("unknown_snapshot", unknownSnapshot); report.add("directory_identity", directory);
        Files.writeString(output.resolve("results.json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create().toJson(report));
        System.out.println("Loaded artifact evidence: " + checks + " checks passed; no runtime/GPU claims");
    }

    private static void incomplete(JsonObject identity) {
        equal(identity.get("complete").getAsBoolean(), false, "bounded directory incomplete");
        if (identity.has("tree_sha256") || identity.has("files") || identity.has("bytes")) throw new AssertionError("Partial hash advertised");
        equal(identity.get("limit").getAsString(), "5000 files / 32 MiB; no partial tree hash advertised", "exact bounded schema");
        checks++;
    }

    private static JsonObject virtual(String mod, String uri, String hash) {
        var result = new JsonObject(); result.add("mods", mods(mod)); result.addProperty("path", "");
        result.addProperty("filesystem_provider", "union"); result.addProperty("uri", uri); result.addProperty("identity", hash);
        return result;
    }
    private static JsonObject mods(String... ids) {
        var result = new JsonObject(); for (String id : ids) result.addProperty(id, "1"); return result;
    }
    private static String independentHash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    private static void pass(JsonObject snapshot, int count) {
        LoadedArtifactEvidence.requireValid(snapshot);
        equal(snapshot.get("complete_mod_coverage").getAsBoolean(), true, "complete mod coverage");
        equal(snapshot.get("complete_identities").getAsBoolean(), true, "complete identities");
        equal(snapshot.getAsJsonArray("loaded_artifact_inventory").size(), count, "inventory roots");
    }
    private static void fail(JsonObject snapshot, int count, String reason) {
        equal(snapshot.getAsJsonArray("loaded_artifact_inventory").size(), count, "failed roots retained");
        if (!snapshot.getAsJsonArray("errors").toString().contains(reason)) throw new AssertionError("Missing failure: " + reason + " in " + snapshot);
        try { LoadedArtifactEvidence.requireValid(snapshot); }
        catch (IllegalStateException expected) { checks++; return; }
        throw new AssertionError("Invalid evidence accepted: " + snapshot);
    }
    private static void equal(Object actual, Object expected, String label) {
        if (!actual.equals(expected)) throw new AssertionError(label + ": " + actual + " != " + expected);
        checks++;
    }
}
