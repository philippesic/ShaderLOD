// ShaderLOD private MakeUp 9.5g merged-submission input R1.
// Native MakeUp by Javier Garcia: https://github.com/javiergcim/MakeUpUltraFast
// Native Iris/Sodium compressed vertex math is preserved. Off defaults native.
// Units: region offset is the original camera-relative binary32 vector in blocks.
// No distance transition, quality reduction, temporal reuse, or fragment change.
uniform bool shaderlod_makeup_merged_input_enabled;
uniform int shaderlod_makeup_region_page_count;
uniform usamplerBuffer shaderlod_makeup_region_offsets;
vec3 shaderlod_makeup_current_region_offset;
void shaderlodMakeupRegionOffsetInit() {
    shaderlod_makeup_current_region_offset = u_RegionOffset;
    // CPU admission proves every referenced vertex and table page before any draw.
    // This guard protects native/control input; it cannot repair invalid merged input.
    if (shaderlod_makeup_merged_input_enabled && gl_VertexID >= 0) {
        int page = gl_VertexID >> 12;
        if (page < shaderlod_makeup_region_page_count) {
            uvec4 bits = texelFetch(shaderlod_makeup_region_offsets, page);
            shaderlod_makeup_current_region_offset = uintBitsToFloat(bits.xyz);
        }
    }
}
