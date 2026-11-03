package com.pp.shaderlod.dev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;

/** Actual r3 raw inputs as source fixtures only; never rewrites their failed contracts. */
public final class PixelControlReceiptTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var rawOff = read("global-off"); var rawOn = read("global-on");
        reject(() -> WorkloadInputs.withoutFeature(rawOff, "live_pixel"));
        var off = fixture(rawOff); var on = fixture(rawOn);
        equal(WorkloadInputs.withoutFeature(off, "live_pixel"), WorkloadInputs.withoutFeature(on, "live_pixel"));
        // Existing r3 ordered hashes must fail the new protocol, not be silently dropped.
        var legacy = off.deepCopy(); legacy.add("live_pixel_effect_controls_sha256", rawOff.get("live_pixel_effect_controls_sha256"));
        reject(() -> WorkloadInputs.withoutFeature(legacy, "live_pixel"));
        var reversed = reverse(off).getAsJsonObject();
        equal(WorkloadInputs.canonicalJsonHash(off), WorkloadInputs.canonicalJsonHash(reversed));
        equal(WorkloadInputs.canonicalJsonHash(WorkloadInputs.withoutFeature(off, "live_pixel")), WorkloadInputs.canonicalJsonHash(WorkloadInputs.withoutFeature(reversed, "live_pixel")));
        var array1 = JsonParser.parseString("{\"v\":[1,2]}"); var array2 = JsonParser.parseString("{\"v\":[2,1]}");
        different(WorkloadInputs.canonicalJsonHash(array1), WorkloadInputs.canonicalJsonHash(array2));
        var changed = off.deepCopy(); changed.getAsJsonObject("live_pixel_effect_controls").addProperty("unknown", true);
        reject(() -> WorkloadInputs.withoutFeature(changed, "live_pixel"));
        for (String file : List.of("config/iris.properties", "shaderpacks/live-pixel-budget-enabled.zip.txt")) {
            var config = rawOff.getAsJsonObject("configuration_files").deepCopy();
            var item = config.getAsJsonObject("files").getAsJsonObject(file);
            rewrite(item, item.get("utf8").getAsString().replaceAll("[0-2][0-9]:[0-5][0-9]:[0-5][0-9]", "23:59:58"));
            equal(WorkloadInputs.livePixelConfigurationSemantics(rawOff.getAsJsonObject("configuration_files")), WorkloadInputs.livePixelConfigurationSemantics(config));
            rewrite(item, item.get("utf8").getAsString() + "UNKNOWN_SETTING=true\n");
            different(WorkloadInputs.livePixelConfigurationSemantics(rawOff.getAsJsonObject("configuration_files")), WorkloadInputs.livePixelConfigurationSemantics(config));
        }
        var invalid = rawOff.getAsJsonObject("configuration_files").deepCopy();
        var backup = invalid.getAsJsonObject("files").getAsJsonObject("config/shaderlod-client-1.toml.bak");
        rewrite(backup, backup.get("utf8").getAsString().replace("near=32.0", "near=64.0"));
        reject(() -> WorkloadInputs.livePixelConfigurationSemantics(invalid));
        var unknown = rawOff.getAsJsonObject("configuration_files").deepCopy();
        var arbitrary = new JsonObject(); rewrite(arbitrary, "[lod]\nenabled=false\n");
        unknown.getAsJsonObject("files").add("config/other.toml.bak", arbitrary);
        var before = WorkloadInputs.livePixelConfigurationSemantics(unknown);
        rewrite(arbitrary, "[lod]\nenabled=true\n");
        different(before, WorkloadInputs.livePixelConfigurationSemantics(unknown));
        var corrupt = off.deepCopy(); corrupt.addProperty("config/iris.properties_sha256", "0".repeat(64));
        reject(() -> WorkloadInputs.withoutFeature(corrupt, "live_pixel"));
        var duplicate = rawOff.getAsJsonObject("configuration_files").deepCopy();
        var iris = duplicate.getAsJsonObject("files").getAsJsonObject("config/iris.properties");
        rewrite(iris, iris.get("utf8").getAsString() + "#Tue Oct 06 01:11:59 PDT 2026\n");
        reject(() -> WorkloadInputs.livePixelConfigurationSemantics(duplicate));
        var mainDrift = rawOff.getAsJsonObject("configuration_files").deepCopy();
        for (String path : List.of("config/shaderlod-client.toml", "config/shaderlod-client-1.toml.bak")) {
            var item = mainDrift.getAsJsonObject("files").getAsJsonObject(path);
            rewrite(item, item.get("utf8").getAsString().replace("near = 32.0", "near = 64.0").replace("near=32.0", "near=64.0"));
        }
        different(WorkloadInputs.livePixelConfigurationSemantics(rawOff.getAsJsonObject("configuration_files")), WorkloadInputs.livePixelConfigurationSemantics(mainDrift));
        var crlf = rawOff.getAsJsonObject("configuration_files").deepCopy();
        var crlfIris = crlf.getAsJsonObject("files").getAsJsonObject("config/iris.properties");
        rewrite(crlfIris, crlfIris.get("utf8").getAsString().replace("\n", "\r\n"));
        var normalizedCrlf = WorkloadInputs.livePixelConfigurationSemantics(crlf);
        if (!normalizedCrlf.getAsJsonObject("files").getAsJsonObject("config/iris.properties").get("utf8_generated_timestamp_omitted").getAsString().contains("#<iris-startup-timestamp>\r\n")) throw new AssertionError("CRLF changed");
        checks++;
        different(WorkloadInputs.livePixelConfigurationSemantics(rawOff.getAsJsonObject("configuration_files")), normalizedCrlf);
        var corruptRaw = rawOff.getAsJsonObject("configuration_files").deepCopy();
        corruptRaw.getAsJsonObject("files").getAsJsonObject("config/shaderlod-client.toml").addProperty("sha256", "0".repeat(64));
        reject(() -> WorkloadInputs.livePixelConfigurationSemantics(corruptRaw));
        if (args.length > 0) {
            Files.writeString(Path.of(args[0]), new com.google.gson.GsonBuilder().disableHtmlEscaping().create().toJson(off));
            Files.writeString(Path.of(args[0] + ".semantic.json"), new com.google.gson.GsonBuilder().disableHtmlEscaping().create().toJson(WorkloadInputs.withoutFeature(off, "live_pixel")));
        }
        System.out.println("PASS: " + checks + " pixel controls canonicalization/rejection checks; source fixtures only, fresh runtime required");
    }
    private static JsonObject read(String arm) throws Exception { return JsonParser.parseString(Files.readString(Path.of("run-workloads/pixel-coverage-runtime-r3/" + arm + "/control/pixel-coverage-evidence.json"))).getAsJsonObject().getAsJsonObject("initialInputs"); }
    private static JsonObject fixture(JsonObject raw) {
        var copy = raw.deepCopy();
        copy.addProperty("live_pixel_control_receipt_schema", WorkloadInputs.LIVE_PIXEL_CONTROL_RECEIPT_SCHEMA);
        for (String field : List.of("live_pixel_effect_controls", "live_pixel_source_qualification")) copy.addProperty(field + "_sha256", WorkloadInputs.canonicalJsonHash(copy.get(field)));
        copy.add("live_pixel_configuration_semantics", WorkloadInputs.livePixelConfigurationSemantics(copy.getAsJsonObject("configuration_files")));
        copy.remove("control_sha256"); // Probe requested control is checked and exported separately.
        return copy;
    }
    private static JsonElement reverse(JsonElement value) {
        if (value.isJsonObject()) {
            var keys = new java.util.ArrayList<>(value.getAsJsonObject().keySet()); java.util.Collections.reverse(keys);
            var result = new JsonObject(); keys.forEach(key -> result.add(key, reverse(value.getAsJsonObject().get(key)))); return result;
        }
        if (value.isJsonArray()) { var result = new com.google.gson.JsonArray(); value.getAsJsonArray().forEach(v -> result.add(reverse(v))); return result; }
        return value.deepCopy();
    }
    private static void rewrite(JsonObject file, String text) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8); file.addProperty("utf8", text); file.addProperty("bytes", bytes.length);
        file.addProperty("sha256", java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
    private static void equal(Object a, Object b) { if (!a.equals(b)) throw new AssertionError("Expected equal"); checks++; }
    private static void different(Object a, Object b) { if (a.equals(b)) throw new AssertionError("Change ignored"); checks++; }
    private static void reject(Runnable run) { try { run.run(); } catch (RuntimeException expected) { checks++; return; } throw new AssertionError("Invalid control accepted"); }
}
