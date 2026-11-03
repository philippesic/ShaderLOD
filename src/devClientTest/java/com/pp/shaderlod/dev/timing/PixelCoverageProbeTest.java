package com.pp.shaderlod.dev.timing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.integration.SourceFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;

/** Native-adapter gates and actual frozen archive/map; no mock GL and no native context. */
public final class PixelCoverageProbeTest {
    private static int checks;
    private static final String SCENE = "89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46";
    public static void main(String[] arguments) throws Exception {
        var properties = properties();
        PixelCoverageProbe.requireProperties(properties); checks++;
        for (String name : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming", "benchmark", "benchmarkOnly",
                "workTrace", "spatialLayoutProbe", "cloudSamplerProbe")) {
            var altered = copy(properties); altered.setProperty("shaderlod.dev." + name, "true");
            reject(() -> PixelCoverageProbe.requireProperties(altered));
        }
        for (String value : List.of("29.999", "NaN", "Infinity")) {
            var altered = copy(properties); altered.setProperty("shaderlod.dev.initialWarmup", value);
            reject(() -> PixelCoverageProbe.requireProperties(altered));
        }
        for (String missing : List.of("shaderlod.dev.gpuTiming", PixelCoverageProbe.SCENE_PROPERTY, "shaderlod.dev.compare")) {
            var altered = copy(properties); altered.remove(missing);
            reject(() -> PixelCoverageProbe.requireProperties(altered));
        }
        PixelCoverageProbe.requireProperties(new Properties()); checks++; // Default off needs no dependencies.

        // Real pinned Iris value type: getCurrentDimension constructs this two-argument object each time.
        var actualOverworld = new NamespacedId("minecraft", "overworld");
        if (actualOverworld == DimensionId.OVERWORLD) throw new AssertionError("Fixture must have distinct object identity"); checks++;
        if (!PixelCoverageProbe.isOverworld(actualOverworld)) throw new AssertionError("Actual Iris Overworld value rejected"); checks++;
        if (!PixelCoverageProbe.isOverworld(DimensionId.OVERWORLD)) throw new AssertionError("Overworld constant rejected"); checks++;
        if (!PixelCoverageProbe.isOverworld(new NamespacedId("minecraft:overworld"))) throw new AssertionError("Qualified Overworld value rejected"); checks++;
        for (var invalid : List.of(new NamespacedId("minecraft", "the_nether"), new NamespacedId("minecraft", "the_end"),
                new NamespacedId("custom", "overworld"), new NamespacedId("minecraft", "unknown"))) {
            if (PixelCoverageProbe.isOverworld(invalid)) throw new AssertionError("Other dimension accepted: " + invalid); checks++;
        }
        if (PixelCoverageProbe.isOverworld(null)) throw new AssertionError("Unavailable dimension accepted"); checks++;

        Path archive = Path.of(arguments[0]);
        var source = PackSourceReader.read(archive);
        var inventory = JsonParser.parseString(Files.readString(Path.of(arguments[1]))).getAsJsonObject();
        var options = new TreeMap<String, String>();
        inventory.getAsJsonObject("live_fixed_taau").getAsJsonObject("options").entrySet()
                .forEach(entry -> options.put(entry.getKey(), entry.getValue().getAsString()));
        PixelCoverageProbe.requireSource(source, options); checks++;
        var wrongMember = new TreeMap<>(source.memberSha256());
        wrongMember.put("shaders/world0/deferred4.fsh", "0".repeat(64));
        reject(() -> PixelCoverageProbe.requireSource(new SourceFingerprint(source.sourceSha256(), wrongMember), options));
        var missingOption = new TreeMap<>(options); missingOption.remove("TAAU");
        reject(() -> PixelCoverageProbe.requireSource(source, missingOption));
        var alteredOption = new TreeMap<>(options); alteredOption.put("TAAU", "false");
        reject(() -> PixelCoverageProbe.requireSource(source, alteredOption));

        var scene = new JsonObject();
        scene.addProperty("dimension", "minecraft:overworld");
        for (String key : List.of("day_time", "x", "y", "z", "yaw", "pitch", "rain", "thunder",
                "framebuffer_width", "framebuffer_height", "live_pixel_fov")) scene.addProperty(key, 1);
        scene.addProperty("live_pixel_camera_type", "FIRST_PERSON");
        var receipt = PixelCoverageProbe.sceneReceipt(scene, SCENE);
        for (String key : scene.keySet()) {
            var missing = scene.deepCopy(); missing.remove(key);
            reject(() -> PixelCoverageProbe.sceneReceipt(missing, SCENE));
        }
        reject(() -> PixelCoverageProbe.sceneReceipt(scene, ""));
        var moved = scene.deepCopy(); moved.addProperty("x", 2);
        if (receipt.equals(PixelCoverageProbe.sceneReceipt(moved, SCENE))) throw new AssertionError("Moved camera ignored"); checks++;

        var controlOff = new JsonObject(); controlOff.addProperty("enabled", false); controlOff.addProperty("time", 12500);
        var controlOn = controlOff.deepCopy(); controlOn.addProperty("enabled", true);
        var off = PixelCoverageProbe.controlsReceipt(controls(false), controlOff, 0);
        var on = PixelCoverageProbe.controlsReceipt(controls(true), controlOn, 1);
        if (!off.equals(on)) throw new AssertionError("Authorized global change did not match"); checks++;
        reject(() -> PixelCoverageProbe.controlsReceipt(controls(false), controlOn, 0));
        var wrongBoolean = controlOff.deepCopy(); wrongBoolean.addProperty("enabled", "false");
        reject(() -> PixelCoverageProbe.controlsReceipt(controls(false), wrongBoolean, 0));
        var differentTime = controlOn.deepCopy(); differentTime.addProperty("time", 6000);
        if (off.equals(PixelCoverageProbe.controlsReceipt(controls(true), differentTime, 1))) throw new AssertionError("Other requested control omitted"); checks++;
        System.out.println("PASS: PixelCoverageProbe source/property/scene/control gates, " + checks + " checks; native readback UNVERIFIED");
    }
    private static JsonObject controls(boolean enabled) {
        var root = new JsonObject();
        root.addProperty("live_pixel_control_receipt_schema", com.pp.shaderlod.dev.WorkloadInputs.LIVE_PIXEL_CONTROL_RECEIPT_SCHEMA);
        var lod = new JsonObject(); lod.addProperty("enabled", enabled); lod.addProperty("near", 32);
        root.add("lod_settings", lod.deepCopy());
        var semantic = new JsonObject(); semantic.add("lod", lod.deepCopy()); root.add("loaded_semantic_config", semantic);
        root.addProperty("control_sha256", enabled ? "1".repeat(64) : "0".repeat(64));
        var captured = new JsonObject(); captured.addProperty("complete", true); captured.add("errors", new com.google.gson.JsonArray());
        var files = new JsonObject();
        for (String name : List.of("options.txt", "config/shaderlod-effects-client.toml")) files.add(name, record("same"));
        files.add("config/iris.properties", record("#Tue Oct 06 01:11:59 PDT 2026\nenableShaders=true\n"));
        files.add("shaderpacks/live-pixel-budget-enabled.zip.txt", record("#Tue Oct 06 01:11:59 PDT 2026\nTAAU=true\n"));
        files.add("config/shaderlod-client.toml", record("[lod]\nenabled=" + enabled + "\nnear=32\n"));
        captured.add("files", files); root.add("configuration_files", captured);
        root.add("live_pixel_configuration_semantics", com.pp.shaderlod.dev.WorkloadInputs.livePixelConfigurationSemantics(captured));
        root.add("config/iris.properties_sha256", files.getAsJsonObject("config/iris.properties").get("sha256"));
        for (String field : List.of("live_pixel_effect_controls", "live_pixel_source_qualification")) {
            var empty = new JsonObject(); root.add(field, empty);
            root.addProperty(field + "_sha256", com.pp.shaderlod.dev.WorkloadInputs.canonicalJsonHash(empty));
        }
        return root;
    }
    private static JsonObject record(String text) {
        var file = new JsonObject(); byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        file.addProperty("utf8", text); file.addProperty("bytes", bytes.length);
        try { file.addProperty("sha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        return file;
    }
    private static Properties properties() {
        var properties = new Properties(); properties.setProperty(PixelCoverageEvidence.PROPERTY, "true");
        for (String key : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming")) properties.setProperty("shaderlod.dev." + key, "false");
        properties.setProperty("shaderlod.dev.measureWorkload", "true"); properties.setProperty("shaderlod.dev.compare", "fixture");
        properties.setProperty("shaderlod.dev.initialWarmup", "30"); properties.setProperty(PixelCoverageProbe.SCENE_PROPERTY, SCENE);
        return properties;
    }
    private static Properties copy(Properties source) { var result = new Properties(); result.putAll(source); return result; }
    private static void reject(Runnable action) {
        try { action.run(); } catch (RuntimeException expected) { checks++; return; }
        throw new AssertionError("Unsafe gate accepted");
    }
}
