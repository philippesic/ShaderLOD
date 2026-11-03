// Only pinned ordinary Overworld terrain; source wrapper excludes DH/PBR/Voxy.
#include "/lib/shaderlod/bsl_shadow_field_control_r1.glsl"
uniform sampler2D shadowcolor2;

bool shaderlodBslShadowFieldAllLit(vec3 shadowPos, float offset, float subsurface) {
    if (!shaderlodBslShadowFieldEnabled() || subsurface != 0.0 ||
        shadowMapResolution != 2048 || offset != 1.0 / 2048.0 ||
        frameCounter < 0 || frameCounter >= 16777215 ||
        textureSize(shadowtex0HW, 0) != ivec2(2048) ||
        textureSize(shadowcolor2, 0) != ivec2(2048) ||
        !(shadowPos.z >= 0.0 && shadowPos.z <= 1.0)) return false;

    // One full physical texel expands UV arithmetic/filter-center error bounds.
    // Root must qualify <=1 texel error and <=1/65536 depth compare error.
    vec2 radius = vec2(offset + 1.0 / 2048.0);
    vec2 lower = floor((shadowPos.xy - radius) * 2048.0 - 0.5);
    vec2 upper = floor((shadowPos.xy + radius) * 2048.0 - 0.5) + 1.0;
    if (!all(greaterThanEqual(lower, vec2(0.0))) ||
        !all(lessThan(upper, vec2(2048.0)))) return false;
    ivec2 first = ivec2(lower) / 16;
    ivec2 last = ivec2(upper) / 16;
    if (any(greaterThan(last - first, ivec2(1)))) return false;

    // Current completed shadow pass is a source ordering contract plus root
    // resource certificate, not cache validity inferred from equal matrices.
    float stamp = texelFetch(shadowcolor2, ivec2(127), 0).r;
    if (stamp != -float(frameCounter + 1)) return false;
    float minimum = texelFetch(shadowcolor2, first, 0).r;
    minimum = min(minimum, texelFetch(shadowcolor2, ivec2(last.x, first.y), 0).r);
    minimum = min(minimum, texelFetch(shadowcolor2, ivec2(first.x, last.y), 0).r);
    minimum = min(minimum, texelFetch(shadowcolor2, last, 0).r);
    // Strict >; negative metadata/zero clear is not a proof. No all-dark case.
    return minimum > shadowPos.z + 1.0 / 65536.0;
}
