package com.pp.shaderlod.optimization.vrs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Desktop NV route only. The operating system and GPU name grant no permission. */
public record VrsCapabilities(boolean openGl45, boolean shadingRateImage,
        boolean framebufferMixedSamples, Map<String, Boolean> functions) {
    public static final List<String> REQUIRED_FUNCTIONS = List.of(
            "glBindShadingRateImageNV", "glShadingRateImagePaletteNV",
            "glGetShadingRateImagePaletteNV", "glShadingRateImageBarrierNV",
            "glCreateTextures", "glTextureStorage2D", "glClearTexImage",
            "glGetTextureLevelParameteriv");

    public VrsCapabilities { functions = Map.copyOf(functions); }
    public List<String> missing() {
        var missing = new ArrayList<String>();
        if (!openGl45) missing.add("OpenGL 4.5");
        if (!shadingRateImage) missing.add("GL_NV_shading_rate_image");
        if (!framebufferMixedSamples) missing.add("GL_NV_framebuffer_mixed_samples");
        for (String name : REQUIRED_FUNCTIONS)
            if (!functions.getOrDefault(name, false)) missing.add(name);
        return List.copyOf(missing);
    }
    public boolean supported() {
        if (!openGl45 || !shadingRateImage || !framebufferMixedSamples) return false;
        for (String name : REQUIRED_FUNCTIONS) if (!functions.getOrDefault(name,false)) return false;
        return true;
    }
}
