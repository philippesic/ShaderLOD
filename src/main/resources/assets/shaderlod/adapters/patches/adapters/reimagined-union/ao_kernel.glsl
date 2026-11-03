// Native Reimagined r5.9.3 paired-depth AO; original ancestry/notices retained.
// Only the call-site UV is explicit; native sample pattern, radius and power retained.
float GetLinearDepth(float depth) {
    return (2.0 * near) / (far + near - depth * farMinusNear);
}

float GetLinearDepth(float depth, float far, float near) {
    return (2.0 * near) / (far + near - depth * (far - near));
}

#include "/lib/shaderlod/adapter.glsl"

#if SSAO_QUALI > 0
    vec2 OffsetDist(float x, int s) {
        float n = fract(x * 1.414) * 3.1415;
        return pow2(vec2(cos(n), sin(n)) * x / s);
    }

    float shaderlodReimaginedAoKernel(vec2 queryCoord, sampler2D depthtex, float z0, float linearZ0, float dither, float farM, float nearM, float aoWorldRange) {
        if (z0 < 0.56) return 1.0;
        float ao = 0.0;

        #if SSAO_QUALI == 2
            int samples = 4;
            float scm = 0.4;
        #elif SSAO_QUALI == 3
            int samples = 12;
            float scm = 0.6;
        #endif

        #if SHADERLOD_REFINEMENT_ADAPTER == 1 && !defined DISTANT_HORIZONS && !defined VOXY
            samples = shaderlod_reimagined_budget(shaderlodOptAoBudget, samples, 2);
        #endif

        #define SSAO_I_FACTOR 0.004

        float sampleDepth = 0.0, angle = 0.0, dist = 0.0;
        float fovScale = gbufferProjection[1][1];
        float distScale = max((farM - nearM) * linearZ0 + near, 3.0);
        vec2 scale = vec2(scm / aspectRatio, scm) * fovScale / distScale;

        #if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined OVERWORLD && defined FRAGMENT_SHADER && defined DEFERRED1 && !defined DISTANT_HORIZONS && !defined VOXY && RP_MODE == 1 && !defined GENERATED_NORMALS && !defined COATED_TEXTURES
            bool shaderlodReimaginedAOReuse = shaderlodReimaginedAODepthSamplingQualified == 1 && shaderlod_optimization_enabled(shaderlodOptAoDepthReuse);
            // Raw-depth reconstruction identity is independent of sampler qualification.
            // Retain every native query when the LOD0/UV cache is unqualified.
            bool shaderlodReimaginedAOLinearEnabled = shaderlod_optimization_enabled(shaderlodOptAoDepthReuse);
            bool shaderlodReimaginedAOLinearValid = false;
            float shaderlodReimaginedAOLinearRaw = 0.0;
            float shaderlodReimaginedAOLinearValue = 0.0;
            bool shaderlodReimaginedAOValid = false;
            vec2 shaderlodReimaginedAOCoord = vec2(0.0);
            float shaderlodReimaginedAOValue = 0.0;
        #endif
        for (int i = 1; i <= samples; i++) {
            vec2 offset = OffsetDist(i + dither, samples) * scale;
            if (i % 2 == 0) offset.y = -offset.y;

            vec2 coord1 = queryCoord + offset;
            vec2 coord2 = queryCoord - offset;

            #if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined OVERWORLD && defined FRAGMENT_SHADER && defined DEFERRED1 && !defined DISTANT_HORIZONS && !defined VOXY && RP_MODE == 1 && !defined GENERATED_NORMALS && !defined COATED_TEXTURES
            {
                vec2 shaderlodCurrentAOCoord = coord1;
                bool shaderlodCurrentAOValid = all(greaterThanEqual(shaderlodCurrentAOCoord, vec2(0.0))) && all(lessThanEqual(shaderlodCurrentAOCoord, vec2(1.0)));
                float shaderlodCurrentAODepth;
                if (shaderlodReimaginedAOReuse && shaderlodCurrentAOValid && shaderlodReimaginedAOValid && all(equal(shaderlodCurrentAOCoord, shaderlodReimaginedAOCoord)))
                    shaderlodCurrentAODepth = shaderlodReimaginedAOValue;
                else if (shaderlodReimaginedAOReuse && shaderlodCurrentAOValid)
                    shaderlodCurrentAODepth = texture2DLod(depthtex, shaderlodCurrentAOCoord, 0.0).r;
                else
                    shaderlodCurrentAODepth = texture2D(depthtex, coord1).r;
                shaderlodReimaginedAOCoord = shaderlodCurrentAOCoord;
                shaderlodReimaginedAOValue = shaderlodCurrentAODepth;
                shaderlodReimaginedAOValid = shaderlodCurrentAOValid;
                bool shaderlodCurrentLinearReusable = shaderlodReimaginedAOLinearEnabled && abs(shaderlodCurrentAODepth) < 1e10;
                if (shaderlodCurrentLinearReusable && shaderlodReimaginedAOLinearValid &&
                    shaderlodCurrentAODepth == shaderlodReimaginedAOLinearRaw)
                    sampleDepth = shaderlodReimaginedAOLinearValue;
                else sampleDepth = GetLinearDepth(shaderlodCurrentAODepth, farM, nearM);
                shaderlodReimaginedAOLinearValid = shaderlodCurrentLinearReusable && abs(sampleDepth) < 1e20;
                shaderlodReimaginedAOLinearRaw = shaderlodCurrentAODepth;
                shaderlodReimaginedAOLinearValue = sampleDepth;
            }
            #else
            sampleDepth = GetLinearDepth(texture2D(depthtex, coord1).r, farM, nearM);
            #endif
            float aosample = aoWorldRange * (linearZ0 - sampleDepth) * 2.0;
            angle = clamp(0.5 - aosample, 0.0, 1.0);
            dist = clamp(0.5 * aosample - 1.0, 0.0, 1.0);

            #if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined OVERWORLD && defined FRAGMENT_SHADER && defined DEFERRED1 && !defined DISTANT_HORIZONS && !defined VOXY && RP_MODE == 1 && !defined GENERATED_NORMALS && !defined COATED_TEXTURES
            {
                vec2 shaderlodCurrentAOCoord = coord2;
                bool shaderlodCurrentAOValid = all(greaterThanEqual(shaderlodCurrentAOCoord, vec2(0.0))) && all(lessThanEqual(shaderlodCurrentAOCoord, vec2(1.0)));
                float shaderlodCurrentAODepth;
                if (shaderlodReimaginedAOReuse && shaderlodCurrentAOValid && shaderlodReimaginedAOValid && all(equal(shaderlodCurrentAOCoord, shaderlodReimaginedAOCoord)))
                    shaderlodCurrentAODepth = shaderlodReimaginedAOValue;
                else if (shaderlodReimaginedAOReuse && shaderlodCurrentAOValid)
                    shaderlodCurrentAODepth = texture2DLod(depthtex, shaderlodCurrentAOCoord, 0.0).r;
                else
                    shaderlodCurrentAODepth = texture2D(depthtex, coord2).r;
                shaderlodReimaginedAOCoord = shaderlodCurrentAOCoord;
                shaderlodReimaginedAOValue = shaderlodCurrentAODepth;
                shaderlodReimaginedAOValid = shaderlodCurrentAOValid;
                bool shaderlodCurrentLinearReusable = shaderlodReimaginedAOLinearEnabled && abs(shaderlodCurrentAODepth) < 1e10;
                if (shaderlodCurrentLinearReusable && shaderlodReimaginedAOLinearValid &&
                    shaderlodCurrentAODepth == shaderlodReimaginedAOLinearRaw)
                    sampleDepth = shaderlodReimaginedAOLinearValue;
                else sampleDepth = GetLinearDepth(shaderlodCurrentAODepth, farM, nearM);
                shaderlodReimaginedAOLinearValid = shaderlodCurrentLinearReusable && abs(sampleDepth) < 1e20;
                shaderlodReimaginedAOLinearRaw = shaderlodCurrentAODepth;
                shaderlodReimaginedAOLinearValue = sampleDepth;
            }
            #else
            sampleDepth = GetLinearDepth(texture2D(depthtex, coord2).r, farM, nearM);
            #endif
            aosample = aoWorldRange * (linearZ0 - sampleDepth) * 2.0;
            angle += clamp(0.5 - aosample, 0.0, 1.0);
            dist += clamp(0.5 * aosample - 1.0, 0.0, 1.0);

            ao += clamp(angle + dist, 0.0, 1.0);
        }
        ao /= samples;

        #define SSAO_IM SSAO_I * SSAO_I_FACTOR
        return pow(ao, SSAO_IM);
    }
#endif

