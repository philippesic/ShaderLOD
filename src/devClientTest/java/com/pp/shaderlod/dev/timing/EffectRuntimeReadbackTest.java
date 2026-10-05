package com.pp.shaderlod.dev.timing;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Actual readback formatter/getter branches with a scalar fake reader; no GL context. */
public final class EffectRuntimeReadbackTest {
    private static final int PROGRAM = 17;
    private static final String QUERIED = "queried_without_gl_error_consumption";
    private static final String[] EFFECTS = {"shaderlodEffectsVersion",
            "shaderlodVolumetricsMode", "shaderlodVolumetricsNear", "shaderlodVolumetricsFar", "shaderlodVolumetricsTransition",
            "shaderlodVolumetricsMediumWorkRatio", "shaderlodVolumetricsFarWorkRatio",
            "shaderlodOpaqueLightingMode", "shaderlodOpaqueLightingNear", "shaderlodOpaqueLightingFar", "shaderlodOpaqueLightingTransition",
            "shaderlodOpaqueLightingMediumWorkRatio", "shaderlodOpaqueLightingFarWorkRatio"};
    private static final Set<String> INTEGERS = Set.of("shaderlodEffectsVersion", "shaderlodVolumetricsMode", "shaderlodOpaqueLightingMode");

    private static final class Reader implements CloudSamplerStateProbe.UniformReader {
        private final Map<String, Integer> locations = new LinkedHashMap<>();
        private int locationReads;
        private int integerReads;
        private int floatReads;
        private boolean nonfinite;
        private boolean fail;
        public int location(int program, String name) {
            check(program == PROGRAM, "Explicit linked program identity");
            locationReads++;
            return locations.getOrDefault(name, -1);
        }
        public int integerValue(int program, int location) {
            integerReads++;
            if (fail) throw new IllegalStateException("diagnostic getter failed");
            return 2;
        }
        public float floatValue(int program, int location) {
            floatReads++;
            if (fail) throw new IllegalStateException("diagnostic getter failed");
            return nonfinite ? Float.NaN : .25f;
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("shaderlod.dev.cloudSamplerProbe", "false");
        disabledBothOriginsNeverQueryContext();
        linkedEffectsHaveExpectedTypesAndActualValues();
        wrongTypesArraysAndMissingFieldsAreUnknownWithoutGetters();
        unqueryableNonfiniteAndGetterFailuresStayUnknown();
        oldCoreSevenFieldShapeIsPreserved();
        shadowSamplerTargetAndComparisonNamesAreExplicit();
        allocationIsDriverDimensionDataWithUnknownFallbacks();
        System.out.println("EffectRuntimeReadbackTest: 7 fixture groups passed; no GL context or driver claim");
    }

    private static void disabledBothOriginsNeverQueryContext() throws Exception {
        CloudSamplerStateProbe.beforeQuadDraw();
        CloudSamplerStateProbe.beforeRasterDraw();
        check(Boolean.FALSE.equals(field("failed")), "Disabled callbacks must not attempt GL and fail");
        check(field("report") == null, "Disabled callbacks cannot create a report");
        check(((Set<?>) field("SEEN")).isEmpty(), "Disabled callbacks cannot scan programs");
    }

    private static void linkedEffectsHaveExpectedTypesAndActualValues() {
        var reader = new Reader();
        var inventory = new LinkedHashMap<String, CloudSamplerStateProbe.Uniform>();
        for (int i = 0; i < EFFECTS.length; i++) {
            String name = EFFECTS[i];
            inventory.put(name, new CloudSamplerStateProbe.Uniform(name, 1, INTEGERS.contains(name) ? GL11.GL_INT : GL11.GL_FLOAT));
            reader.locations.put(name, i); // Location zero is legal and must be read.
        }
        JsonObject values = CloudSamplerStateProbe.effectUniforms(PROGRAM, inventory, "missing", reader);
        check(values.size() == 13, "Version plus six fields for each of two effects");
        for (String name : EFFECTS) {
            var value = values.getAsJsonObject(name);
            check(QUERIED.equals(value.get("status").getAsString()), name);
            check(value.get("active_uniform_size").getAsInt() == 1, "Scalar shape");
            check(value.get("value").getAsDouble() == (INTEGERS.contains(name) ? 2 : .25), "Read actual typed values");
        }
        check(values.getAsJsonObject("shaderlodEffectsVersion").get("uniform_location").getAsInt() == 0, "Zero location is queryable");
        check(values.getAsJsonObject("shaderlodEffectsVersion").get("expected_protocol_version").getAsInt() == 2, "Protocol expectation is separate from value");
        check(reader.locationReads == 13 && reader.integerReads == 3 && reader.floatReads == 10, "No supplier/source reads; expected typed getters only");
    }

    private static void wrongTypesArraysAndMissingFieldsAreUnknownWithoutGetters() {
        var reader = new Reader();
        Map<String, CloudSamplerStateProbe.Uniform> inventory = Map.of(
                "shaderlodEffectsVersion", new CloudSamplerStateProbe.Uniform("shaderlodEffectsVersion", 1, GL11.GL_FLOAT),
                "shaderlodOpaqueLightingMode", new CloudSamplerStateProbe.Uniform("shaderlodOpaqueLightingMode", 2, GL11.GL_INT));
        var values = CloudSamplerStateProbe.effectUniforms(PROGRAM, inventory, "truncated inventory", reader);
        for (String name : EFFECTS) check("unknown".equals(values.getAsJsonObject(name).get("status").getAsString()), name);
        check(reader.locationReads == 0 && reader.integerReads == 0 && reader.floatReads == 0, "Wrong scalar type/array/missing means no value getter");
        check(values.getAsJsonObject("shaderlodVolumetricsFar").get("reason").getAsString().equals("truncated inventory"), "Missing reason retained");
    }

    private static void unqueryableNonfiniteAndGetterFailuresStayUnknown() {
        String name = "shaderlodVolumetricsFar";
        Map<String, CloudSamplerStateProbe.Uniform> inventory = Map.of(name, new CloudSamplerStateProbe.Uniform(name, 1, GL11.GL_FLOAT));
        var reader = new Reader();
        var missing = CloudSamplerStateProbe.effectUniforms(PROGRAM, inventory, "inactive", reader).getAsJsonObject(name);
        check("unknown".equals(missing.get("status").getAsString()) && reader.floatReads == 0, "Negative location not queried");
        reader.locations.put(name, 1);
        reader.nonfinite = true;
        var invalid = CloudSamplerStateProbe.effectUniforms(PROGRAM, inventory, "inactive", reader).getAsJsonObject(name);
        check("unknown".equals(invalid.get("status").getAsString()) && !invalid.has("value"), "NaN is unknown, not JSON value");
        reader.fail = true;
        var failed = CloudSamplerStateProbe.effectUniforms(PROGRAM, inventory, "inactive", reader).getAsJsonObject(name);
        check("unknown".equals(failed.get("status").getAsString()) && failed.get("reason").getAsString().contains("diagnostic getter failed"), "Getter exception preserved as unknown");
    }

    private static void oldCoreSevenFieldShapeIsPreserved() {
        var reader = new Reader();
        var core = CloudSamplerStateProbe.coreUniforms(PROGRAM, Map.of(), "inactive", reader);
        check(core.keySet().equals(Set.of("shaderlodBridgeVersion", "shaderlodEnabled", "shaderlodNear", "shaderlodFar",
                "shaderlodPreset", "shaderlodTransition", "shaderlodDebug")), "Existing seven core names remain separate");
        check(core.getAsJsonObject("shaderlodBridgeVersion").get("expected_type").getAsInt() == GL11.GL_INT, "Core integer type preserved");
        check(core.getAsJsonObject("shaderlodNear").get("expected_type").getAsInt() == GL11.GL_FLOAT, "Core float type preserved");
        check(reader.locationReads == 0, "Inactive core fields cannot query a location");
    }

    private static void shadowSamplerTargetAndComparisonNamesAreExplicit() {
        check(CloudSamplerStateProbe.samplerTarget(GL20.GL_SAMPLER_2D_SHADOW) == GL11.GL_TEXTURE_2D, "2D shadow shares 2D binding target");
        check(CloudSamplerStateProbe.samplerTypeName(GL20.GL_SAMPLER_2D_SHADOW).equals("sampler2DShadow"), "Shadow sampler remains distinguishable");
        check(CloudSamplerStateProbe.samplerTarget(GL20.GL_SAMPLER_CUBE) == 0, "Unsupported cube remains inventory only");
        check(CloudSamplerStateProbe.compareModeName(GL30.GL_COMPARE_REF_TO_TEXTURE).equals("GL_COMPARE_REF_TO_TEXTURE"), "Compare reference mode");
        check(CloudSamplerStateProbe.compareModeName(GL11.GL_NONE).equals("GL_NONE"), "No comparison mode");
        check(CloudSamplerStateProbe.compareFunctionName(GL11.GL_LEQUAL).equals("GL_LEQUAL"), "Comparison function");
        check(CloudSamplerStateProbe.compareFunctionName(-1).equals("unknown"), "Unsupported comparison value unknown");
    }

    private static void allocationIsDriverDimensionDataWithUnknownFallbacks() {
        var three = CloudSamplerStateProbe.allocation(GL12.GL_TEXTURE_3D, 64, 64, 64, GL30.GL_R8);
        check(QUERIED.equals(three.get("status").getAsString()) && three.get("depth").getAsInt() == 64, "Actual three-dimensional allocation data");
        var two = CloudSamplerStateProbe.allocation(GL11.GL_TEXTURE_2D, 128, 32, 1, GL11.GL_RGBA16);
        check(two.get("width").getAsInt() == 128 && two.get("height").getAsInt() == 32 && !two.has("depth"), "2D dimensions do not invent queried depth");
        check("unknown".equals(CloudSamplerStateProbe.allocation(GL11.GL_TEXTURE_2D, 0, 32, 1, GL11.GL_RGBA16).get("status").getAsString()), "No allocation remains unknown");
        check("unknown".equals(CloudSamplerStateProbe.allocation(GL12.GL_TEXTURE_3D, 64, 64, 64, 0).get("status").getAsString()), "Unknown format remains unknown");
        check("unknown".equals(CloudSamplerStateProbe.allocation(GL20.GL_SAMPLER_CUBE, 64, 64, 1, GL11.GL_RGBA16).get("status").getAsString()), "Unsupported target remains unknown");
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
