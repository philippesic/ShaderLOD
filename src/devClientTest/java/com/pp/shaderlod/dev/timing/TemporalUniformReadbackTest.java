package com.pp.shaderlod.dev.timing;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Tests the real typed readback seam; no GL context or frame-lifetime evidence. */
public final class TemporalUniformReadbackTest {
    private static final int PROGRAM = 41;
    private static final String VERSION = "shaderlodTemporalVersion";
    private static final String RESET = "shaderlodHistoryReset";
    private static final String QUERIED = "queried_without_gl_error_consumption";
    private static int checks;

    private static final class Reader implements CloudSamplerStateProbe.UniformReader {
        final Map<String, Integer> locations = new LinkedHashMap<>();
        final Map<Integer, Integer> values = new LinkedHashMap<>();
        int locationReads;
        int integerReads;
        String failedName;
        Integer failedLocation;

        public int location(int program, String name) {
            check(program == PROGRAM, "Observed program identity passed to location getter");
            check(Set.of(VERSION, RESET).contains(name), "Exact temporal field names only");
            locationReads++;
            if (name.equals(failedName)) throw new IllegalStateException("fixture location failure");
            return locations.getOrDefault(name, -1);
        }

        public int integerValue(int program, int location) {
            check(program == PROGRAM, "Observed program identity passed to integer getter");
            integerReads++;
            if (Integer.valueOf(location).equals(failedLocation)) throw new IllegalStateException("fixture integer failure");
            check(values.containsKey(location), "Returned location used for value getter");
            return values.get(location);
        }

        public float floatValue(int program, int location) {
            throw new AssertionError("Temporal fields must never use float getter");
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("shaderlod.dev.cloudSamplerProbe", "false");
        disabledCallbacks();
        actualInts();
        unexpectedValuesRemainRaw();
        missingAndTruncated();
        invalidShape();
        invalidLocations();
        independentFailures();
        separateSchemas();
        System.out.println("TemporalUniformReadbackTest: " + checks + " checks in 8 fixture groups passed; no GL context");
    }

    private static void disabledCallbacks() throws Exception {
        CloudSamplerStateProbe.beforeQuadDraw();
        CloudSamplerStateProbe.beforeRasterDraw();
        check(Boolean.FALSE.equals(field("failed")), "Disabled hooks cannot fail or query GL");
        check(field("report") == null, "Disabled hooks cannot create reports");
        check(((Set<?>) field("SEEN")).isEmpty(), "Disabled hooks cannot observe programs");
    }

    private static void actualInts() {
        for (int reset : new int[] {0, 1}) {
            var reader = reader(1, reset);
            var result = read(inventory(), reader);
            check(result.keySet().equals(Set.of(VERSION, RESET)), "Exactly two separate temporal fields");
            for (String name : result.keySet()) {
                var value = result.getAsJsonObject(name);
                check(QUERIED.equals(value.get("status").getAsString()), "Typed getter status");
                check(value.get("expected_type").getAsInt() == GL11.GL_INT, "Expected GL_INT");
                check(value.get("expected_type_name").getAsString().equals("int"), "Expected scalar int label");
                check(value.get("found_in_linked_inventory").getAsBoolean(), "Linked inventory presence retained");
                check(value.get("active_uniform_name").getAsString().equals(name), "Linked name retained");
                check(value.get("active_uniform_type").getAsInt() == GL11.GL_INT, "Actual linked type retained");
                check(value.get("active_uniform_size").getAsInt() == 1, "Actual scalar size retained");
            }
            check(result.getAsJsonObject(VERSION).get("value").getAsInt() == 1, "Actual version read");
            check(result.getAsJsonObject(VERSION).get("expected_protocol_version").getAsInt() == 1, "Separate schema expectation");
            check(result.getAsJsonObject(VERSION).get("uniform_location").getAsInt() == 0, "Location zero valid");
            check(result.getAsJsonObject(RESET).get("value").getAsInt() == reset, "Both actual reset values read");
            check(reader.locationReads == 2 && reader.integerReads == 2, "Two exact locations and typed values");
        }
    }

    private static void unexpectedValuesRemainRaw() {
        for (int version : new int[] {0, -17, 73}) {
            var result = read(inventory(), reader(version, -19));
            check(result.getAsJsonObject(VERSION).get("value").getAsInt() == version, "Unexpected actual version never normalized");
            check(result.getAsJsonObject(VERSION).get("expected_protocol_version").getAsInt() == 1, "Expectation cannot overwrite actual version");
            check(result.getAsJsonObject(RESET).get("value").getAsInt() == -19, "Invalid reset retained for downstream rejection");
        }
    }

    private static void missingAndTruncated() {
        for (String reason : new String[] {"inactive, optimized out, or undeclared", "not found in bounded/truncated active-uniform scan", "current program not linked"}) {
            var reader = reader(1, 1);
            var result = CloudSamplerStateProbe.temporalUniforms(PROGRAM, Map.of(), reason, reader);
            for (String name : result.keySet()) {
                var value = result.getAsJsonObject(name);
                unknown(value);
                check(!value.get("found_in_linked_inventory").getAsBoolean(), "Missing inventory reported");
                check(value.get("reason").getAsString().equals(reason), "Exact inventory failure retained");
            }
            check(reader.locationReads == 0 && reader.integerReads == 0, "Unknown cannot query or infer values");
        }
    }

    private static void invalidShape() {
        for (String name : Set.of(VERSION, RESET)) {
            for (int[] shape : new int[][] {{GL11.GL_FLOAT, 1}, {GL20.GL_BOOL, 1}, {GL11.GL_INT, 0}, {GL11.GL_INT, 2}}) {
                var inventory = new LinkedHashMap<>(inventory());
                inventory.put(name, new CloudSamplerStateProbe.Uniform(name, shape[1], shape[0]));
                var reader = reader(1, 1);
                var result = read(inventory, reader);
                unknown(result.getAsJsonObject(name));
                check(result.getAsJsonObject(name).get("active_uniform_type").getAsInt() == shape[0], "Wrong type retained");
                check(result.getAsJsonObject(name).get("active_uniform_size").getAsInt() == shape[1], "Wrong size retained");
                check(reader.locationReads == 1 && reader.integerReads == 1, "Invalid shape prevents its own location/value calls");
                String other = name.equals(VERSION) ? RESET : VERSION;
                check(result.getAsJsonObject(other).get("value").getAsInt() == 1, "Other legal field survives");
            }
        }
    }

    private static void invalidLocations() {
        var reader = reader(1, 1);
        reader.locations.put(VERSION, -1);
        reader.locations.put(RESET, -7);
        var result = read(inventory(), reader);
        unknown(result.getAsJsonObject(VERSION));
        unknown(result.getAsJsonObject(RESET));
        check(result.getAsJsonObject(RESET).get("uniform_location").getAsInt() == -7, "Actual invalid location retained");
        check(reader.locationReads == 2 && reader.integerReads == 0, "Invalid locations never query values");
    }

    private static void independentFailures() {
        for (String name : Set.of(VERSION, RESET)) {
            for (boolean getterFailure : new boolean[] {false, true}) {
                var reader = reader(1, 0);
                if (getterFailure) reader.failedLocation = reader.locations.get(name);
                else reader.failedName = name;
                var result = read(inventory(), reader);
                unknown(result.getAsJsonObject(name));
                check(result.getAsJsonObject(name).get("reason").getAsString().contains(getterFailure ? "fixture integer failure" : "fixture location failure"), "Precise exception retained");
                String other = name.equals(VERSION) ? RESET : VERSION;
                check(QUERIED.equals(result.getAsJsonObject(other).get("status").getAsString()), "Independent field survives exception");
                check(result.getAsJsonObject(other).get("value").getAsInt() == (other.equals(VERSION) ? 1 : 0), "Other actual value retained");
                check(reader.locationReads == 2 && reader.integerReads == (getterFailure ? 2 : 1), "Only legal lookup reaches getter");
            }
        }
    }

    private static void separateSchemas() {
        var reader = reader(1, 1);
        var core = CloudSamplerStateProbe.coreUniforms(PROGRAM, inventory(), "inactive", reader);
        var effects = CloudSamplerStateProbe.effectUniforms(PROGRAM, inventory(), "inactive", reader);
        var capabilities = CloudSamplerStateProbe.capabilityUniforms(PROGRAM, inventory(), "inactive", reader);
        check(core.size() == 7 && effects.size() == 13 && capabilities.size() == 3, "Legacy map selections preserved");
        for (String name : Set.of(VERSION, RESET)) {
            check(!core.has(name) && !effects.has(name) && !capabilities.has(name), "Temporal names never contaminate other maps");
        }
        check(effects.getAsJsonObject("shaderlodEffectsVersion").get("expected_protocol_version").getAsInt() == 2, "Effects schema remains 2");
        check(capabilities.getAsJsonObject("shaderlodCapabilitiesVersion").get("expected_protocol_version").getAsInt() == 1, "Capability schema remains 1");
        check(reader.locationReads == 0 && reader.integerReads == 0, "Temporal inventory cannot imply legacy values");
    }

    private static Reader reader(int version, int reset) {
        var reader = new Reader();
        reader.locations.put(VERSION, 0);
        reader.locations.put(RESET, 4);
        reader.values.put(0, version);
        reader.values.put(4, reset);
        return reader;
    }

    private static Map<String, CloudSamplerStateProbe.Uniform> inventory() {
        return Map.of(VERSION, new CloudSamplerStateProbe.Uniform(VERSION, 1, GL11.GL_INT),
                RESET, new CloudSamplerStateProbe.Uniform(RESET, 1, GL11.GL_INT));
    }

    private static JsonObject read(Map<String, CloudSamplerStateProbe.Uniform> inventory, Reader reader) {
        return CloudSamplerStateProbe.temporalUniforms(PROGRAM, inventory, "inactive, optimized out, or undeclared", reader);
    }

    private static void unknown(JsonObject value) {
        check(value.get("status").getAsString().equals("unknown") && !value.has("value") && value.has("reason"), "Unknown has reason and no invented value");
    }

    private static Object field(String name) throws Exception {
        Field field = CloudSamplerStateProbe.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
