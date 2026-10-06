package com.pp.shaderlod.dev.timing;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.ARBSamplerObjects;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Opt-in render-thread diagnostic. Getters can stall; this is not a timing run. */
public final class CloudSamplerStateProbe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean("shaderlod.dev.cloudSamplerProbe");
    private static final int PROGRAM_LIMIT = 64;
    private static final int UNIFORM_LIMIT = 4096;
    private static final int UNIFORM_NAME_LIMIT = 1024;
    private static final String[] WATCHED = {"colortex8", "noisetex", "colortex6", "colortex7"};
    private static final String[] CORE_UNIFORMS = {"shaderlodBridgeVersion", "shaderlodEnabled", "shaderlodNear", "shaderlodFar",
            "shaderlodPreset", "shaderlodTransition", "shaderlodDebug"};
    private static final Set<String> CORE_INTEGER_UNIFORMS = Set.of("shaderlodBridgeVersion", "shaderlodEnabled", "shaderlodPreset", "shaderlodDebug");
    private static final String[] EFFECT_UNIFORMS = {"shaderlodEffectsVersion",
            "shaderlodVolumetricsMode", "shaderlodVolumetricsNear", "shaderlodVolumetricsFar", "shaderlodVolumetricsTransition",
            "shaderlodVolumetricsMediumWorkRatio", "shaderlodVolumetricsFarWorkRatio",
            "shaderlodOpaqueLightingMode", "shaderlodOpaqueLightingNear", "shaderlodOpaqueLightingFar", "shaderlodOpaqueLightingTransition",
            "shaderlodOpaqueLightingMediumWorkRatio", "shaderlodOpaqueLightingFarWorkRatio"};
    private static final Set<String> EFFECT_INTEGER_UNIFORMS = Set.of("shaderlodEffectsVersion", "shaderlodVolumetricsMode", "shaderlodOpaqueLightingMode");
    private static final String[] CAPABILITY_UNIFORMS = {"shaderlodCapabilitiesVersion", "shaderlodVolumeIntegrationAvailable",
            "shaderlodOpaqueSpatialAvailable"};
    private static final Set<String> CAPABILITY_INTEGER_UNIFORMS = Set.of(CAPABILITY_UNIFORMS);
    private static final UniformReader GL_UNIFORM_READER = new UniformReader() {
        public int location(int program, String name) { return GL20.glGetUniformLocation(program, name); }
        public int integerValue(int program, int location) { return GL20.glGetUniformi(program, location); }
        public float floatValue(int program, int location) { return GL20.glGetUniformf(program, location); }
    };
    private static final Set<Integer> SEEN = new HashSet<>();
    private static final JsonArray PROGRAMS = new JsonArray();
    private static JsonObject report;
    private static boolean failed;

    private CloudSamplerStateProbe() {}

    /** Called at pinned Iris FullScreenQuadRenderer.renderQuad()V HEAD. */
    public static void beforeQuadDraw() {
        beforeDraw("iris-fullscreen-quad");
    }

    /** Optional pinned Sodium native draw-site hook; the hook owner proves binding order. */
    public static void beforeRasterDraw() {
        beforeDraw("sodium-raster-submission");
    }

    private static void beforeDraw(String origin) {
        if (!ENABLED || failed || SEEN.size() >= PROGRAM_LIMIT) return;
        try {
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            if (program == 0 || !SEEN.add(program)) return;
            GLCapabilities capabilities = GL.getCapabilities();
            if (report == null) report = header(capabilities);
            JsonObject entry = new JsonObject();
            entry.addProperty("timestamp_utc", Instant.now().toString());
            entry.addProperty("current_program", program);
            entry.addProperty("draw_origin", origin);
            try {
                capture(program, entry, capabilities);
            } catch (RuntimeException exception) {
                entry.add("capture_failure", unknown(exception.toString()));
                LOGGER.warn("Cloud sampler-state capture failed for program {}", program, exception);
            }
            PROGRAMS.add(entry);
            report.addProperty("captured_programs", SEEN.size());
            report.addProperty("program_limit_reached", SEEN.size() >= PROGRAM_LIMIT);
            writeReport();
        } catch (RuntimeException | IOException exception) {
            failed = true;
            LOGGER.warn("Cloud sampler-state probe stopped after diagnostic failure", exception);
        }
    }

    private static JsonObject header(GLCapabilities capabilities) {
        JsonObject result = new JsonObject();
        result.addProperty("schema", 1);
        result.addProperty("probe", "shaderlod.dev.cloudSamplerProbe");
        result.addProperty("started_utc", Instant.now().toString());
        result.addProperty("hook", "net.irisshaders.iris.pathways.FullScreenQuadRenderer.renderQuad()V HEAD");
        result.addProperty("pinned_iris", "1.8.12+1.21.1-neoforge");
        result.addProperty("gl_version", GL11.glGetString(GL11.GL_VERSION));
        result.addProperty("gl_renderer", GL11.glGetString(GL11.GL_RENDERER));
        result.addProperty("gl_vendor", GL11.glGetString(GL11.GL_VENDOR));
        result.addProperty("glsl_version", GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION));
        result.addProperty("sampler_objects_supported", samplerObjects(capabilities));
        result.addProperty("anisotropy_supported", anisotropy(capabilities));
        result.addProperty("max_combined_texture_units", GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS));
        result.addProperty("program_limit", PROGRAM_LIMIT);
        result.addProperty("uniform_scan_limit", UNIFORM_LIMIT);
        result.addProperty("uniform_name_length_limit", UNIFORM_NAME_LIMIT);
        result.addProperty("sampler_selection", "Watched aliases plus active scalar sampler2D/sampler3D/sampler2DShadow uniforms in the bounded linked-uniform scan; other sampler types/arrays outside watched aliases are inventory only");
        result.addProperty("core_uniform_schema", 2);
        result.addProperty("core_uniform_selection", "Seven existing core fields: version/enable/preset/debug scalar int and near/far/transition scalar float linked-program readbacks; no source/supplier reevaluation; first observed draw is not a settled live-reload guarantee");
        result.addProperty("effect_uniform_schema", 2);
        result.addProperty("effect_uniform_selection", "Effects version plus six Volumetrics and six OpaqueLighting fields; version/mode scalar int, bands/retained ratios scalar float; actual linked readbacks, not mod suppliers or inferred consumption");
        result.addProperty("capability_uniform_schema", 1);
        result.addProperty("capability_uniform_selection", "Capabilities version, VolumeIntegration availability and OpaqueSpatial availability scalar int linked-program readbacks; actual sampled values, no source detection or supplier invocation; first observed draw may precede capability availability");
        result.addProperty("texture_allocation_scope", "Bound nonzero 2D/3D texture level 0 dimensions/internal format only; no other mip images, completeness, texture contents or physical fetch counts");
        result.addProperty("raster_hook", "Optional pinned Sodium native draw-site callback; availability/injection verified separately by hook owner");
        result.addProperty("scope", "First draw per numeric GL program ID in this JVM across enabled fullscreen/raster hooks; per-entry draw_origin is not a program-name or shader-stage proof");
        result.addProperty("query_status", "Driver values queried without consuming glGetError; inspect runtime GL logs for errors");
        result.addProperty("interpretation", "Missing/inactive/unsupported state is unknown; sampler state alone does not prove divergent implicit derivatives safe");
        result.add("programs", PROGRAMS);
        return result;
    }

    private static void capture(int program, JsonObject entry, GLCapabilities capabilities) {
        entry.add("core_uniforms", coreUniforms(program, Map.of(), "not inspected; see capture_failure if present"));
        entry.add("effect_uniforms", effectUniforms(program, Map.of(), "not inspected; see capture_failure if present", GL_UNIFORM_READER));
        entry.add("capability_uniforms", capabilityUniforms(program, Map.of(), "not inspected; see capture_failure if present", GL_UNIFORM_READER));
        int linked = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS);
        entry.addProperty("linked", linked != GL11.GL_FALSE);
        if (linked == GL11.GL_FALSE) {
            entry.add("samplers", unknown("current program not linked"));
            entry.add("core_uniforms", coreUniforms(program, Map.of(), "current program not linked"));
            entry.add("effect_uniforms", effectUniforms(program, Map.of(), "current program not linked", GL_UNIFORM_READER));
            entry.add("capability_uniforms", capabilityUniforms(program, Map.of(), "current program not linked", GL_UNIFORM_READER));
            return;
        }
        int count = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
        int maxLength = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORM_MAX_LENGTH);
        entry.addProperty("active_uniform_count", count);
        entry.addProperty("uniform_scan_truncated", count > UNIFORM_LIMIT);
        entry.addProperty("uniform_name_length_truncated", maxLength > UNIFORM_NAME_LIMIT);
        Map<String, Uniform> uniforms = new LinkedHashMap<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var size = stack.mallocInt(1);
            var type = stack.mallocInt(1);
            for (int index = 0; index < Math.min(count, UNIFORM_LIMIT); index++) {
                String name = GL20.glGetActiveUniform(program, index, Math.max(1, Math.min(maxLength, UNIFORM_NAME_LIMIT)), size, type);
                uniforms.put(name, new Uniform(name, size.get(0), type.get(0)));
            }
        }
        entry.addProperty("cloud_worley_sampler_candidate", uniforms.values().stream().anyMatch(u -> u.type() == GL20.GL_SAMPLER_3D));
        JsonObject linkedUniforms = new JsonObject();
        for (var uniform : uniforms.values()) {
            JsonObject identity = new JsonObject();
            identity.addProperty("type", uniform.type());
            identity.addProperty("size", uniform.size());
            linkedUniforms.add(uniform.name(), identity);
        }
        entry.add("linked_uniforms", linkedUniforms);
        entry.add("core_uniforms", coreUniforms(program, uniforms, count > UNIFORM_LIMIT
                ? "not found in truncated active-uniform scan"
                : "inactive, optimized out, or undeclared; linked introspection cannot distinguish"));
        entry.add("effect_uniforms", effectUniforms(program, uniforms, count > UNIFORM_LIMIT || maxLength > UNIFORM_NAME_LIMIT
                ? "not found in bounded/truncated active-uniform scan"
                : "inactive, optimized out, or undeclared; linked introspection cannot distinguish", GL_UNIFORM_READER));
        entry.add("capability_uniforms", capabilityUniforms(program, uniforms, count > UNIFORM_LIMIT || maxLength > UNIFORM_NAME_LIMIT
                ? "not found in bounded/truncated active-uniform scan"
                : "inactive, optimized out, or undeclared; linked introspection cannot distinguish", GL_UNIFORM_READER));
        JsonObject samplers = new JsonObject();
        entry.add("samplers", samplers);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        entry.addProperty("active_texture_before", previousActiveTexture);
        try {
            for (String name : WATCHED) {
                Uniform uniform = uniforms.get(name);
                if (uniform == null) {
                    samplers.add(name, unknown(count > UNIFORM_LIMIT ? "not found in truncated active-uniform scan" : "missing or inactive linked uniform"));
                    continue;
                }
                try {
                    samplers.add(name, sampler(program, uniform, capabilities));
                } catch (RuntimeException exception) {
                    samplers.add(name, unknown(exception.toString()));
                }
            }
            // Capture linked scalar 2D/3D/shadow names as well as watched aliases.
            // This includes final-pass depth/color samplers and Iris-remapped
            // custom textures without assuming source aliases survive.
            for (var uniform : uniforms.values()) {
                if (samplerTarget(uniform.type()) == 0
                        || uniform.size() != 1 || samplers.has(uniform.name())) continue;
                try { samplers.add(uniform.name(), sampler(program, uniform, capabilities)); }
                catch (RuntimeException exception) { samplers.add(uniform.name(), unknown(exception.toString())); }
            }
        } finally {
            // Raw GL calls avoid altering Minecraft's cached texture state; restore even on query failure.
            GL13.glActiveTexture(previousActiveTexture);
        }
        entry.addProperty("active_texture_after", GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
    }

    private static JsonObject coreUniforms(int program, Map<String, Uniform> uniforms, String missingReason) {
        return coreUniforms(program, uniforms, missingReason, GL_UNIFORM_READER);
    }

    static JsonObject coreUniforms(int program, Map<String, Uniform> uniforms, String missingReason, UniformReader reader) {
        return uniformReadbacks(program, uniforms, missingReason, CORE_UNIFORMS, CORE_INTEGER_UNIFORMS, reader);
    }

    static JsonObject effectUniforms(int program, Map<String, Uniform> uniforms, String missingReason, UniformReader reader) {
        JsonObject result = uniformReadbacks(program, uniforms, missingReason, EFFECT_UNIFORMS, EFFECT_INTEGER_UNIFORMS, reader);
        result.getAsJsonObject("shaderlodEffectsVersion").addProperty("expected_protocol_version", 2);
        return result;
    }

    static JsonObject capabilityUniforms(int program, Map<String, Uniform> uniforms, String missingReason, UniformReader reader) {
        JsonObject result = uniformReadbacks(program, uniforms, missingReason, CAPABILITY_UNIFORMS, CAPABILITY_INTEGER_UNIFORMS, reader);
        result.getAsJsonObject("shaderlodCapabilitiesVersion").addProperty("expected_protocol_version", 1);
        return result;
    }

    private static JsonObject uniformReadbacks(int program, Map<String, Uniform> uniforms, String missingReason,
                                              String[] names, Set<String> integers, UniformReader reader) {
        JsonObject result = new JsonObject();
        for (String name : names) {
            int expectedType = integers.contains(name) ? GL11.GL_INT : GL11.GL_FLOAT;
            Uniform uniform = uniforms.get(name);
            JsonObject value = new JsonObject();
            result.add(name, value);
            value.addProperty("expected_type", expectedType);
            value.addProperty("expected_type_name", expectedType == GL11.GL_INT ? "int" : "float");
            value.addProperty("found_in_linked_inventory", uniform != null);
            if (uniform == null) {
                value.addProperty("status", "unknown");
                value.addProperty("reason", missingReason);
                continue;
            }
            value.addProperty("active_uniform_name", uniform.name());
            value.addProperty("active_uniform_type", uniform.type());
            value.addProperty("active_uniform_size", uniform.size());
            if (uniform.type() != expectedType || uniform.size() != 1) {
                value.addProperty("status", "unknown");
                value.addProperty("reason", "active linked uniform is not the expected scalar type; location/value not queried");
                continue;
            }
            try {
                int location = reader.location(program, name);
                value.addProperty("uniform_location", location);
                if (location < 0) {
                    value.addProperty("status", "unknown");
                    value.addProperty("reason", "active linked uniform has no queryable location; value not queried");
                } else if (expectedType == GL11.GL_INT) {
                    value.addProperty("value", reader.integerValue(program, location));
                    value.addProperty("status", "queried_without_gl_error_consumption");
                } else {
                    float actual = reader.floatValue(program, location);
                    if (Float.isFinite(actual)) {
                        value.addProperty("value", actual);
                        value.addProperty("status", "queried_without_gl_error_consumption");
                    } else {
                        value.addProperty("status", "unknown");
                        value.addProperty("reason", "non-finite float returned by uniform getter");
                    }
                }
            } catch (RuntimeException exception) {
                value.addProperty("status", "unknown");
                value.addProperty("reason", exception.toString());
            }
        }
        return result;
    }

    private static JsonObject sampler(int program, Uniform uniform, GLCapabilities capabilities) {
        JsonObject result = new JsonObject();
        result.addProperty("active_uniform_name", uniform.name());
        result.addProperty("active_uniform_size", uniform.size());
        result.addProperty("active_uniform_type", uniform.type());
        int target = samplerTarget(uniform.type());
        result.addProperty("active_uniform_type_name", samplerTypeName(uniform.type()));
        if (target == 0 || uniform.size() != 1) {
            result.add("binding", unknown("only scalar sampler2D/sampler3D/sampler2DShadow uniforms supported by this pinned probe"));
            return result;
        }
        int location = GL20.glGetUniformLocation(program, uniform.name());
        result.addProperty("uniform_location", location);
        if (location < 0) {
            result.add("binding", unknown("linked uniform has no queryable location"));
            return result;
        }
        int unit = GL20.glGetUniformi(program, location);
        result.addProperty("texture_unit", unit);
        if (unit < 0 || unit >= GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)) {
            result.add("binding", unknown("sampler unit outside context limits"));
            return result;
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        int texture = GL11.glGetInteger(target == GL11.GL_TEXTURE_2D ? GL11.GL_TEXTURE_BINDING_2D : GL12.GL_TEXTURE_BINDING_3D);
        result.addProperty("texture_target", target);
        result.addProperty("texture_target_name", target == GL11.GL_TEXTURE_2D ? "GL_TEXTURE_2D" : "GL_TEXTURE_3D");
        result.addProperty("bound_texture", texture);
        JsonObject textureParameters;
        if (texture == 0) {
            textureParameters = unknown("no nonzero texture bound; default-object parameters not treated as sampled state");
            result.add("texture_level_zero", unknown("no nonzero texture bound; allocation not queried"));
        } else {
            textureParameters = parameters(target, 0, capabilities);
            textureParameters.addProperty("base_level", GL11.glGetTexParameteri(target, GL12.GL_TEXTURE_BASE_LEVEL));
            textureParameters.addProperty("max_level", GL11.glGetTexParameteri(target, GL12.GL_TEXTURE_MAX_LEVEL));
            textureParameters.addProperty("level_scope", "Level 0 allocation queried separately; other mip images/completeness not queried");
            result.add("texture_level_zero", textureLevelZero(target, capabilities));
        }
        result.add("texture_parameters", textureParameters);
        if (!samplerObjects(capabilities)) {
            result.add("bound_sampler", unknown("sampler objects unsupported by context"));
            result.add("effective_sampling_parameters", textureParameters.deepCopy());
            result.addProperty("effective_parameter_source", "texture; sampler objects unsupported");
        } else {
            int boundSampler = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, unit);
            result.addProperty("bound_sampler", boundSampler);
            if (boundSampler == 0) {
                result.add("effective_sampling_parameters", textureParameters.deepCopy());
                result.addProperty("effective_parameter_source", "texture; sampler object zero");
            } else {
                JsonObject samplerParameters = parameters(target, boundSampler, capabilities);
                result.add("sampler_parameters", samplerParameters);
                result.add("effective_sampling_parameters", samplerParameters.deepCopy());
                result.addProperty("effective_parameter_source", "sampler object overrides filtering/LOD/anisotropy/comparison; texture retains base/max levels/allocation");
            }
        }
        return result;
    }

    private static JsonObject parameters(int target, int sampler, GLCapabilities capabilities) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "queried_without_gl_error_consumption");
        int min = integerParameter(target, sampler, GL11.GL_TEXTURE_MIN_FILTER, capabilities);
        int mag = integerParameter(target, sampler, GL11.GL_TEXTURE_MAG_FILTER, capabilities);
        result.addProperty("min_filter", min);
        result.addProperty("min_filter_name", filterName(min));
        result.addProperty("mag_filter", mag);
        result.addProperty("mag_filter_name", filterName(mag));
        result.addProperty("min_lod", floatParameter(target, sampler, GL12.GL_TEXTURE_MIN_LOD, capabilities));
        result.addProperty("max_lod", floatParameter(target, sampler, GL12.GL_TEXTURE_MAX_LOD, capabilities));
        result.addProperty("lod_bias", floatParameter(target, sampler, GL14.GL_TEXTURE_LOD_BIAS, capabilities));
        if (capabilities.OpenGL14 || capabilities.GL_ARB_shadow) {
            int mode = integerParameter(target, sampler, GL14.GL_TEXTURE_COMPARE_MODE, capabilities);
            int function = integerParameter(target, sampler, GL14.GL_TEXTURE_COMPARE_FUNC, capabilities);
            result.addProperty("compare_mode", mode);
            result.addProperty("compare_mode_name", compareModeName(mode));
            result.addProperty("compare_func", function);
            result.addProperty("compare_func_name", compareFunctionName(function));
        } else {
            result.add("compare_mode", unknown("texture comparison parameters unsupported by context"));
            result.add("compare_func", unknown("texture comparison parameters unsupported by context"));
        }
        if (anisotropy(capabilities)) {
            result.addProperty("max_anisotropy", floatParameter(target, sampler, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, capabilities));
        } else {
            result.add("max_anisotropy", unknown("anisotropic filtering unsupported by context"));
        }
        return result;
    }

    private static int integerParameter(int target, int sampler, int parameter, GLCapabilities capabilities) {
        if (sampler == 0) return GL11.glGetTexParameteri(target, parameter);
        return capabilities.OpenGL33 ? GL33.glGetSamplerParameteri(sampler, parameter) : ARBSamplerObjects.glGetSamplerParameteri(sampler, parameter);
    }

    private static float floatParameter(int target, int sampler, int parameter, GLCapabilities capabilities) {
        if (sampler == 0) return GL11.glGetTexParameterf(target, parameter);
        return capabilities.OpenGL33 ? GL33.glGetSamplerParameterf(sampler, parameter) : ARBSamplerObjects.glGetSamplerParameterf(sampler, parameter);
    }

    private static boolean samplerObjects(GLCapabilities capabilities) {
        return capabilities.OpenGL33 || capabilities.GL_ARB_sampler_objects;
    }

    private static boolean anisotropy(GLCapabilities capabilities) {
        return capabilities.OpenGL46 || capabilities.GL_EXT_texture_filter_anisotropic || capabilities.GL_ARB_texture_filter_anisotropic;
    }

    private static JsonObject textureLevelZero(int target, GLCapabilities capabilities) {
        if (!capabilities.OpenGL11 || (target == GL12.GL_TEXTURE_3D && !capabilities.OpenGL12)) {
            return unknown("texture level allocation getters unsupported by context");
        }
        try {
            int width = GL11.glGetTexLevelParameteri(target, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(target, 0, GL11.GL_TEXTURE_HEIGHT);
            int format = GL11.glGetTexLevelParameteri(target, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            int depth = target == GL12.GL_TEXTURE_3D ? GL11.glGetTexLevelParameteri(target, 0, GL12.GL_TEXTURE_DEPTH) : 1;
            return allocation(target, width, height, depth, format);
        } catch (RuntimeException exception) {
            return unknown(exception.toString());
        }
    }

    static JsonObject allocation(int target, int width, int height, int depth, int format) {
        if (target != GL11.GL_TEXTURE_2D && target != GL12.GL_TEXTURE_3D) return unknown("unsupported texture allocation target");
        JsonObject result = new JsonObject();
        result.addProperty("level", 0);
        result.addProperty("width", width);
        result.addProperty("height", height);
        if (target == GL12.GL_TEXTURE_3D) result.addProperty("depth", depth);
        result.addProperty("dimensions", target == GL12.GL_TEXTURE_3D ? 3 : 2);
        result.addProperty("internal_format", format);
        boolean known = width > 0 && height > 0 && depth > 0 && format > 0;
        result.addProperty("status", known ? "queried_without_gl_error_consumption" : "unknown");
        if (!known) result.addProperty("reason", "level 0 image absent or getter result not meaningful; no GL error consumed");
        result.addProperty("scope", "Level 0 allocation only; no image contents, other mip levels or completeness claim");
        return result;
    }

    static int samplerTarget(int type) {
        return switch (type) {
            case GL20.GL_SAMPLER_2D, GL20.GL_SAMPLER_2D_SHADOW -> GL11.GL_TEXTURE_2D;
            case GL20.GL_SAMPLER_3D -> GL12.GL_TEXTURE_3D;
            default -> 0;
        };
    }

    static String samplerTypeName(int type) {
        return switch (type) {
            case GL20.GL_SAMPLER_2D -> "sampler2D";
            case GL20.GL_SAMPLER_2D_SHADOW -> "sampler2DShadow";
            case GL20.GL_SAMPLER_3D -> "sampler3D";
            default -> "unknown/unsupported";
        };
    }

    static String compareModeName(int mode) {
        return switch (mode) {
            case GL11.GL_NONE -> "GL_NONE";
            case GL30.GL_COMPARE_REF_TO_TEXTURE -> "GL_COMPARE_REF_TO_TEXTURE";
            default -> "unknown";
        };
    }

    static String compareFunctionName(int function) {
        return switch (function) {
            case GL11.GL_LESS -> "GL_LESS";
            case GL11.GL_LEQUAL -> "GL_LEQUAL";
            case GL11.GL_EQUAL -> "GL_EQUAL";
            case GL11.GL_GREATER -> "GL_GREATER";
            case GL11.GL_GEQUAL -> "GL_GEQUAL";
            case GL11.GL_NOTEQUAL -> "GL_NOTEQUAL";
            case GL11.GL_ALWAYS -> "GL_ALWAYS";
            case GL11.GL_NEVER -> "GL_NEVER";
            default -> "unknown";
        };
    }

    private static String filterName(int value) {
        return switch (value) {
            case GL11.GL_NEAREST -> "GL_NEAREST";
            case GL11.GL_LINEAR -> "GL_LINEAR";
            case GL11.GL_NEAREST_MIPMAP_NEAREST -> "GL_NEAREST_MIPMAP_NEAREST";
            case GL11.GL_LINEAR_MIPMAP_NEAREST -> "GL_LINEAR_MIPMAP_NEAREST";
            case GL11.GL_NEAREST_MIPMAP_LINEAR -> "GL_NEAREST_MIPMAP_LINEAR";
            case GL11.GL_LINEAR_MIPMAP_LINEAR -> "GL_LINEAR_MIPMAP_LINEAR";
            default -> "unknown";
        };
    }

    private static JsonObject unknown(String reason) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "unknown");
        result.addProperty("reason", reason);
        return result;
    }

    private static void writeReport() throws IOException {
        Path output = Minecraft.getInstance().gameDirectory.toPath().resolve("logs/cloud-sampler-state.json");
        Files.createDirectories(output.getParent());
        Path temporary = output.resolveSibling("cloud-sampler-state.json.tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        try {
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
        LOGGER.info("Cloud sampler-state probe captured {} / {} programs: {}", SEEN.size(), PROGRAM_LIMIT, output);
    }

    record Uniform(String name, int size, int type) {}

    /** Narrow getter seam for scalar fixtures; no mod supplier or GL state mutation. */
    interface UniformReader {
        int location(int program, String name);
        int integerValue(int program, int location);
        float floatValue(int program, int location);
    }
}
