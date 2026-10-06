package com.pp.shaderlod.dev.timing;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Exercises the actual capability formatter/getter seam without a GL context. */
public final class CapabilityReadbackTest {
    private static final int PROGRAM = 29;
    private static final String VERSION = "shaderlodCapabilitiesVersion";
    private static final String AVAILABLE = "shaderlodVolumeIntegrationAvailable";
    private static final String SPATIAL = "shaderlodOpaqueSpatialAvailable";
    private static final String QUERIED = "queried_without_gl_error_consumption";

    private static final class Reader implements CloudSamplerStateProbe.UniformReader {
        private final Map<String, Integer> locations = new LinkedHashMap<>();
        private final Map<Integer, Integer> values = new LinkedHashMap<>();
        private int locationReads;
        private int integerReads;
        private boolean locationFailure;
        private String failedName;
        private Integer failedLocation;

        public int location(int program, String name) {
            check(program == PROGRAM, "Lookup must use the observed linked program");
            check(Set.of(VERSION, AVAILABLE, SPATIAL).contains(name), "Only exact capability names may be queried");
            locationReads++;
            if (locationFailure || name.equals(failedName)) throw new IllegalStateException("fixture location failure");
            return locations.getOrDefault(name, -1);
        }

        public int integerValue(int program, int location) {
            check(program == PROGRAM, "Getter must use the observed linked program");
            integerReads++;
            if (Integer.valueOf(location).equals(failedLocation)) throw new IllegalStateException("fixture integer failure");
            check(values.containsKey(location), "Getter must use the returned location");
            return values.get(location);
        }

        public float floatValue(int program, int location) {
            throw new AssertionError("Capabilities must never use a float getter");
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("shaderlod.dev.cloudSamplerProbe", "false");
        disabledCallbacksRemainNoOp();
        actualScalarIntsAndLocationZeroAreRead();
        initialUnavailableAndUnexpectedValuesAreNotRewritten();
        missingInventoryNeverQueries();
        wrongTypesAndArraysNeverQuery();
        negativeLocationsNeverReadValues();
        lookupAndGetterFailuresStayUnknownAndIndependent();
        spatialFailuresPreserveOtherCapabilities();
        legacyMapsRemainSeparate();
        System.out.println("CapabilityReadbackTest: 9 fixture groups passed; no GL context or runtime capability claim");
    }

    private static void disabledCallbacksRemainNoOp() throws Exception {
        CloudSamplerStateProbe.beforeQuadDraw();
        CloudSamplerStateProbe.beforeRasterDraw();
        check(Boolean.FALSE.equals(field("failed")), "Disabled hooks cannot query GL or fail");
        check(field("report") == null, "Disabled hooks cannot produce a report");
        check(((Set<?>) field("SEEN")).isEmpty(), "Disabled hooks cannot observe programs");
    }

    private static void actualScalarIntsAndLocationZeroAreRead() {
        var reader = reader(1, 1);
        var result = read(inventory(GL11.GL_INT, 1, GL11.GL_INT, 1), reader);
        check(result.keySet().equals(Set.of(VERSION, AVAILABLE, SPATIAL)), "Separate bounded three-field capability map");
        for (String name : result.keySet()) {
            var field = result.getAsJsonObject(name);
            check(QUERIED.equals(field.get("status").getAsString()), "Legal scalar is read");
            check(field.get("expected_type").getAsInt() == GL11.GL_INT, "Expected GL_INT");
            check(field.get("expected_type_name").getAsString().equals("int"), "Expected scalar int name");
            check(field.get("found_in_linked_inventory").getAsBoolean(), "Actual inventory presence");
            check(field.get("active_uniform_type").getAsInt() == GL11.GL_INT, "Actual linked type retained");
            check(field.get("active_uniform_size").getAsInt() == 1, "Actual scalar size retained");
            check(field.get("active_uniform_name").getAsString().equals(name), "Actual linked name retained");
            check(field.get("value").getAsInt() == 1, "Actual integer getter value");
        }
        check(result.getAsJsonObject(VERSION).get("uniform_location").getAsInt() == 0, "Location zero is legal");
        check(result.getAsJsonObject(VERSION).get("expected_protocol_version").getAsInt() == 1, "Expectation is separately schema 1");
        check(reader.locationReads == 3 && reader.integerReads == 3, "Exactly three lookups and typed getters");
    }

    private static void initialUnavailableAndUnexpectedValuesAreNotRewritten() {
        for (int available : new int[] {0, 1, -17}) {
            var result = read(inventory(GL11.GL_INT, 1, GL11.GL_INT, 1), reader(73, available, available));
            check(result.getAsJsonObject(VERSION).get("value").getAsInt() == 73, "Unexpected version remains actual value");
            check(result.getAsJsonObject(VERSION).get("expected_protocol_version").getAsInt() == 1, "Expectation cannot overwrite version");
            check(result.getAsJsonObject(AVAILABLE).get("value").getAsInt() == available, "Initial zero and unusual availability remain actual values");
            check(result.getAsJsonObject(SPATIAL).get("value").getAsInt() == available, "Spatial zero/one/unexpected integers remain actual values");
        }
    }

    private static void missingInventoryNeverQueries() {
        var reader = reader(1, 1);
        var result = CloudSamplerStateProbe.capabilityUniforms(PROGRAM, Map.of(), "bounded scan omitted field", reader);
        for (String name : result.keySet()) {
            var field = result.getAsJsonObject(name);
            unknown(field);
            check(!field.get("found_in_linked_inventory").getAsBoolean(), "Missing inventory explicitly retained");
            check(field.get("reason").getAsString().equals("bounded scan omitted field"), "Caller missing/truncation reason retained");
        }
        check(reader.locationReads == 0 && reader.integerReads == 0, "Missing fields cannot use readers or inferred substitutes");
    }

    private static void wrongTypesAndArraysNeverQuery() {
        for (var bad : new int[][] {{GL11.GL_FLOAT, 1}, {GL20.GL_BOOL, 1}, {GL11.GL_INT, 2}, {GL11.GL_INT, 0}}) {
            var reader = reader(1, 1);
            var result = read(inventory(bad[0], bad[1], bad[0], bad[1]), reader);
            unknown(result.getAsJsonObject(VERSION));
            unknown(result.getAsJsonObject(AVAILABLE));
            unknown(result.getAsJsonObject(SPATIAL));
            check(reader.locationReads == 0 && reader.integerReads == 0, "Wrong type or non-scalar blocks all queries");
        }
    }

    private static void negativeLocationsNeverReadValues() {
        var reader = reader(1, 1);
        reader.locations.put(VERSION, -1);
        reader.locations.put(AVAILABLE, -7);
        reader.locations.put(SPATIAL, -9);
        var result = read(inventory(GL11.GL_INT, 1, GL11.GL_INT, 1), reader);
        unknown(result.getAsJsonObject(VERSION));
        unknown(result.getAsJsonObject(AVAILABLE));
        unknown(result.getAsJsonObject(SPATIAL));
        check(result.getAsJsonObject(AVAILABLE).get("uniform_location").getAsInt() == -7, "Actual bad location retained");
        check(reader.locationReads == 3 && reader.integerReads == 0, "Negative locations cannot reach value getters");
    }

    private static void lookupAndGetterFailuresStayUnknownAndIndependent() {
        var reader = reader(1, 1);
        reader.locationFailure = true;
        var failed = read(inventory(GL11.GL_INT, 1, GL11.GL_INT, 1), reader);
        unknown(failed.getAsJsonObject(VERSION));
        unknown(failed.getAsJsonObject(AVAILABLE));
        unknown(failed.getAsJsonObject(SPATIAL));
        check(failed.getAsJsonObject(VERSION).get("reason").getAsString().contains("fixture location failure"), "Lookup failure reason retained");
        check(reader.locationReads == 3 && reader.integerReads == 0, "Each field reports lookup failure independently");
        reader = reader(1, 0);
        reader.failedLocation = 0;
        failed = read(inventory(GL11.GL_INT, 1, GL11.GL_INT, 1), reader);
        unknown(failed.getAsJsonObject(VERSION));
        check(failed.getAsJsonObject(VERSION).get("reason").getAsString().contains("fixture integer failure"), "Getter failure reason retained");
        check(QUERIED.equals(failed.getAsJsonObject(AVAILABLE).get("status").getAsString())
                && failed.getAsJsonObject(AVAILABLE).get("value").getAsInt() == 0, "One failure cannot hide another actual unavailable value");
        check(reader.integerReads == 3, "Other typed getters still execute");
    }

    private static void spatialFailuresPreserveOtherCapabilities() {
        for (int failure = 0; failure < 7; failure++) {
            var reader = reader(1, 0, 1);
            var inventory = new LinkedHashMap<>(inventory(GL11.GL_INT, 1, GL11.GL_INT, 1));
            switch (failure) {
                case 0 -> inventory.remove(SPATIAL);
                case 1 -> inventory.put(SPATIAL, new CloudSamplerStateProbe.Uniform(SPATIAL, 1, GL11.GL_FLOAT));
                case 2 -> inventory.put(SPATIAL, new CloudSamplerStateProbe.Uniform(SPATIAL, 1, GL20.GL_BOOL));
                case 3 -> inventory.put(SPATIAL, new CloudSamplerStateProbe.Uniform(SPATIAL, 2, GL11.GL_INT));
                case 4 -> reader.locations.put(SPATIAL, -1);
                case 5 -> reader.failedName = SPATIAL;
                case 6 -> reader.failedLocation = 8;
                default -> throw new AssertionError("Unknown fixture");
            }
            var result = read(inventory, reader);
            unknown(result.getAsJsonObject(SPATIAL));
            check(result.getAsJsonObject(VERSION).get("value").getAsInt() == 1, "Spatial failure preserves actual schema value");
            check(result.getAsJsonObject(AVAILABLE).get("value").getAsInt() == 0, "Spatial failure preserves actual volume zero");
            check(QUERIED.equals(result.getAsJsonObject(VERSION).get("status").getAsString())
                    && QUERIED.equals(result.getAsJsonObject(AVAILABLE).get("status").getAsString()), "Independent capabilities remain queried");
            check(reader.integerReads == (failure == 6 ? 3 : 2), "Spatial getter runs only for legal shape/location");
            check(reader.locationReads == (failure < 4 ? 2 : 3), "Missing/invalid spatial shape cannot query a location");
        }
    }

    private static void legacyMapsRemainSeparate() {
        var reader = reader(1, 1);
        var capabilities = inventory(GL11.GL_INT, 1, GL11.GL_INT, 1);
        var core = CloudSamplerStateProbe.coreUniforms(PROGRAM, capabilities, "inactive", reader);
        var effects = CloudSamplerStateProbe.effectUniforms(PROGRAM, capabilities, "inactive", reader);
        check(core.size() == 7 && effects.size() == 13, "Legacy core7 and effect13 sizes preserved");
        for (String name : Set.of(VERSION, AVAILABLE, SPATIAL)) {
            check(!core.has(name) && !effects.has(name), "Capabilities do not alter legacy map selection");
        }
        check(effects.getAsJsonObject("shaderlodEffectsVersion").get("expected_protocol_version").getAsInt() == 2, "Legacy effect protocol remains 2");
        check(reader.locationReads == 0 && reader.integerReads == 0, "Capability inventory cannot imply legacy fields");
    }

    private static Reader reader(int version, int available) {
        return reader(version, available, 1);
    }

    private static Reader reader(int version, int available, int spatial) {
        var reader = new Reader();
        reader.locations.put(VERSION, 0);
        reader.locations.put(AVAILABLE, 4);
        reader.locations.put(SPATIAL, 8);
        reader.values.put(0, version);
        reader.values.put(4, available);
        reader.values.put(8, spatial);
        return reader;
    }

    private static Map<String, CloudSamplerStateProbe.Uniform> inventory(int versionType, int versionSize, int availableType, int availableSize) {
        return Map.of(VERSION, new CloudSamplerStateProbe.Uniform(VERSION, versionSize, versionType),
                AVAILABLE, new CloudSamplerStateProbe.Uniform(AVAILABLE, availableSize, availableType),
                SPATIAL, new CloudSamplerStateProbe.Uniform(SPATIAL, availableSize, availableType));
    }

    private static JsonObject read(Map<String, CloudSamplerStateProbe.Uniform> inventory, Reader reader) {
        return CloudSamplerStateProbe.capabilityUniforms(PROGRAM, inventory, "inactive, optimized out, or undeclared", reader);
    }

    private static void unknown(JsonObject field) {
        check(field.get("status").getAsString().equals("unknown") && !field.has("value") && field.has("reason"), "Unknown must have reason and no invented value");
    }

    private static Object field(String name) throws Exception {
        Field field = CloudSamplerStateProbe.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
