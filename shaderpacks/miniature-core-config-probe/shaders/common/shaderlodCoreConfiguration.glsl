/* Miniature 2.19 private core configuration; modified 2026-10-05.
 * Derived from Miniature by Mateus A. Kreuch, Copyright (c) 2022, MIT.
 * Full restored LICENSE.Miniature/source/contract accompany the private ZIP.
 * One original outer march and one refinement chain; explicit LOD0 active
 * sampling requires the pinned Iris translation and fresh driver validation.
 */
#ifndef SHADERLOD_MINIATURE_CORE_CONFIGURATION_INCLUDED
#define SHADERLOD_MINIATURE_CORE_CONFIGURATION_INCLUDED

uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset;
uniform float shaderlodNear;
uniform float shaderlodFar;
uniform float shaderlodTransition;
uniform int shaderlodDebug;

bool shaderlodMiniatureConfigurationValid() {
    if (shaderlodBridgeVersion != 2) return false;
    if ((shaderlodEnabled != 0 && shaderlodEnabled != 1) ||
        (shaderlodDebug != 0 && shaderlodDebug != 1)) return false;
    if (shaderlodPreset < 0 || shaderlodPreset > 2) return false;
    // Ordered bounded conjunction rejects NaN and infinities without relying
    // on isnan/isinf syntax in the source GLSL120 wrappers. No silent clamps.
    return shaderlodNear >= 1.0 && shaderlodNear <= 4095.0 &&
           shaderlodFar >= 2.0 && shaderlodFar <= 4096.0 &&
           shaderlodFar > shaderlodNear &&
           shaderlodTransition >= 0.0 && shaderlodTransition <= 512.0 &&
           shaderlodTransition <= min(2.0 * shaderlodNear, shaderlodFar - shaderlodNear);
}

vec2 shaderlodMiniaturePresetBudgets() {
    // Actual LodPreset ordinals: BALANCED0, PERFORMANCE1, AGGRESSIVE2.
    if (shaderlodPreset == 0) return vec2(4.0, 3.0);
    if (shaderlodPreset == 1) return vec2(3.0, 2.0);
    return vec2(2.0, 2.0); // Only reachable after exact ordinal validation.
}

vec3 shaderlodMiniaturePolicyPair(float a, float b, float weight) {
    // x/y are endpoint refinement counts, z is the second endpoint's weight.
    // Pure/equal budgets use exactly one endpoint color and return directly.
    if (a == b || weight <= 0.0) return vec3(a, a, 0.0);
    if (weight >= 1.0) return vec3(b, b, 0.0);
    return vec3(a, b, weight);
}

vec3 shaderlodMiniatureConfigurationPolicy(vec3 viewPos) {
#if SHADERLOD_MINIATURE_CORE_CONFIG_MODE == 1
    return vec3(4.0, 4.0, 0.0); // Full-four explicit-sampling control.
#else
    vec2 budgets = shaderlodMiniaturePresetBudgets();
    float radialBlocks = length(viewPos);
    float width = shaderlodTransition;
    if (width == 0.0) {
        if (radialBlocks < shaderlodNear) return vec3(4.0, 4.0, 0.0);
        if (radialBlocks < shaderlodFar) return vec3(budgets.x, budgets.x, 0.0);
        return vec3(budgets.y, budgets.y, 0.0);
    }
    float halfWidth = 0.5 * width;
    if (radialBlocks <= shaderlodNear - halfWidth) return vec3(4.0, 4.0, 0.0);
    if (radialBlocks < shaderlodNear + halfWidth) {
        return shaderlodMiniaturePolicyPair(4.0, budgets.x,
            smoothstep(shaderlodNear - halfWidth, shaderlodNear + halfWidth, radialBlocks));
    }
    if (radialBlocks <= shaderlodFar - halfWidth) return vec3(budgets.x, budgets.x, 0.0);
    if (radialBlocks < shaderlodFar + halfWidth) {
        return shaderlodMiniaturePolicyPair(budgets.x, budgets.y,
            smoothstep(shaderlodFar - halfWidth, shaderlodFar + halfWidth, radialBlocks));
    }
    return vec3(budgets.y, budgets.y, 0.0);
#endif
}

vec4 shaderlodMiniatureEndpoint(float depth, vec2 uv, float sceneDepth, float fresnel) {
    // Exact native final depth guard/vignette/fresnel at this endpoint.
    return sceneDepth + 0.0001 <= depth
        ? vec4(0.0)
        : vec4(texture2DLod(colortex0, uv, 0.0).rgb, getReflectionVignette(uv) * fresnel);
}

vec4 shaderlodMiniatureBlendEndpoints(vec4 a, vec4 b, float weight) {
    if (weight <= 0.0) return a;
    if (weight >= 1.0) return b;
    float opacity = mix(a.a, b.a, weight);
    if (opacity == 0.0) return vec4(0.0);
    vec3 contribution = mix(a.rgb * a.a, b.rgb * b.a, weight);
    return vec4(contribution / opacity, opacity);
}

vec3 shaderlodMiniatureDebugPalette(float count) {
    // Requested budget palette: full4 green,3 yellow,2 red. Not hit coverage.
    if (count >= 4.0) return vec3(0.0, 1.0, 0.0);
    if (count >= 3.0) return vec3(1.0, 1.0, 0.0);
    return vec3(1.0, 0.0, 0.0);
}

vec4 shaderlodMiniatureConfigurationActive(float depth, vec3 normal, vec3 viewPos) {
    vec3 V = normalize(viewPos);
    vec3 R = normalize(reflect(V, normal));

    if (R.z >= -0.05) return vec4(0.0);

    float fresnel = 1.0 - dot(normal, -V);
    float grazingEpsilon = rescale(1.0 - abs(dot(R, normal)), 0.95, 1.0);
    float invR = 1.0 / abs(R.z);
    float invFar = 1.0 / (2.0*far);
    float lengthR = 1.0;
    vec3 oldPos = viewPos;

    for (int i = 0; i < SSR_MAX_STEPS; i++) {
        vec3 curPos = viewPos + R * lengthR;
        vec2 curUV = view2screen(curPos).st;

        if (curUV.s < 0.0 || curUV.s > 1.0 || curUV.t < 0.0 || curUV.t > 1.0)
            break;

        float sceneDepth = texture2DLod(depthtex0, curUV, 0.0).x;
        float sceneZ = screen2view(curUV, sceneDepth).z;
        float distanceEpsilon = clamp(abs(sceneZ) * invFar, 0.0, 1.0);
        float epsilon = 1.0 + 0.1*max(distanceEpsilon, grazingEpsilon);
        float diffZ = curPos.z - sceneZ * epsilon;

        if (diffZ < 0.0) {
            vec3 a = oldPos;
            vec3 b = curPos;

            if (diffZ > -SSR_BINARY_Z_CUTOFF) {
                // Policy uses the originating viewPos, only on eligible hits.
                vec3 policy = shaderlodMiniatureConfigurationPolicy(viewPos);
                int firstCount = int(policy.x);
                int secondCount = int(policy.y);
                int steps = 4;
#if SHADERLOD_MINIATURE_CORE_CONFIG_MODE == 3
                steps = firstCount > secondCount ? firstCount : secondCount;
#endif
                vec2 firstUV = curUV;
                vec2 secondUV = curUV;
                float firstDepth = sceneDepth;
                float secondDepth = sceneDepth;
                for (int j = 0; j < 4; j++) {
                    if (j >= steps) break;
                    curPos = (a + b) * 0.5;
                    curUV = view2screen(curPos).st;
                    sceneDepth = texture2DLod(depthtex0, curUV, 0.0).x;
                    sceneZ = screen2view(curUV, sceneDepth).z;

                    if (-curPos.z < -sceneZ) { a = curPos; }
                    else                     { b = curPos; }

                    if (j + 1 == firstCount) { firstUV = curUV; firstDepth = sceneDepth; }
                    if (j + 1 == secondCount) { secondUV = curUV; secondDepth = sceneDepth; }
                }
                vec4 first = shaderlodMiniatureEndpoint(depth, firstUV, firstDepth, fresnel);
                if (firstCount == secondCount || policy.z <= 0.0) return first;
                vec4 second = shaderlodMiniatureEndpoint(depth, secondUV, secondDepth, fresnel);
                return shaderlodMiniatureBlendEndpoints(first, second, policy.z);
            }

            // Native non-refinement hit path is unaffected; one color result.
            return shaderlodMiniatureEndpoint(depth, curUV, sceneDepth, fresnel);
        }

        oldPos = curPos;
        lengthR += max(SSR_STEP_SIZE * abs(diffZ) * invR, 1.0);
    }

    return vec4(0.0);
}

vec4 shaderlodMiniatureCoreConfiguration(float depth, vec3 normal, vec3 viewPos) {
    // This entire fallback decision is dynamically uniform. Invalid flags,
    // ordinals and finite/range/width fields are rejected, never clamped.
    if (!shaderlodMiniatureConfigurationValid() || shaderlodEnabled != 1 || SSR_STEP_SIZE != 1.6) {
        return getReflectionColor(depth, normal, viewPos);
    }
#if SHADERLOD_MINIATURE_CORE_DEBUG_ALLOW == 1
    if (shaderlodDebug == 1) {
        vec3 policy = shaderlodMiniatureConfigurationPolicy(viewPos);
        vec3 palette = mix(shaderlodMiniatureDebugPalette(policy.x),
                           shaderlodMiniatureDebugPalette(policy.y), policy.z);
        // Requested policy diagnostic only. This intentionally bypasses SSR;
        // it is never normal-quality, physical-count or timing evidence.
        return vec4(palette, 1.0);
    }
#endif
    return shaderlodMiniatureConfigurationActive(depth, normal, viewPos);
}

#endif
