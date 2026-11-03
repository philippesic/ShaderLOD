// Native Unbound r5.9.3 paired-depth AO; original ancestry/notices retained.
// Only the call-site UV is explicit; native sample pattern, radius and power retained.
float GetLinearDepth(float depth) {
    return (2.0 * near) / (far + near - depth * farMinusNear);
}

float GetLinearDepth(float depth, float far, float near) {
    return (2.0 * near) / (far + near - depth * (far - near));
}

#if SSAO_QUALI > 0
    vec2 OffsetDist(float x, int s) {
        float n = fract(x * 1.414) * 3.1415;
        return pow2(vec2(cos(n), sin(n)) * x / s);
    }

    float shaderlodUnboundAoKernel(vec2 queryCoord, sampler2D depthtex, float z0, float linearZ0, float dither, float farM, float nearM, float aoWorldRange) {
        if (z0 < 0.56) return 1.0;
        float ao = 0.0;

        #if SSAO_QUALI == 2
            int samples = 4;
            float scm = 0.4;
        #elif SSAO_QUALI == 3
            int samples = 12;
            float scm = 0.6;
        #endif

        #define SSAO_I_FACTOR 0.004

        #if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined DEFERRED1 && defined OVERWORLD && !defined DISTANT_HORIZONS && !defined VOXY && RP_MODE == 1 && !defined GENERATED_NORMALS && !defined COATED_TEXTURES
            if (shaderlod_optimization_enabled(shaderlodOptAoBudget) && shaderlodNear >= 0.0 && shaderlodNear <= 65536.0 &&
                linearZ0 * (farM - nearM) + nearM > max(3.0, shaderlodNear))
                samples = shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled, shaderlodPreset, samples, 4);
        #endif
        // Native paired-depth loop, offsets/samples support and ao/=samples normalization retained.
        float sampleDepth = 0.0, angle = 0.0, dist = 0.0;
        float fovScale = gbufferProjection[1][1];
        float distScale = max((farM - nearM) * linearZ0 + near, 3.0);
        vec2 scale = vec2(scm / aspectRatio, scm) * fovScale / distScale;

#if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined OVERWORLD && defined FRAGMENT_SHADER && !defined DISTANT_HORIZONS && !defined VOXY && RP_MODE == 1 && !defined GENERATED_NORMALS && !defined COATED_TEXTURES && defined DEFERRED1
        // Exact arithmetic cache needs no sampler qualification: keep each native read.
        // Restrict the new route to the requested NeoForge 1.21.1 Iris tuple.
        bool shaderlodAOLinearReuse = false;
        #if MC_VERSION == 12101 && IRIS_VERSION == 10812
        shaderlodAOLinearReuse = shaderlod_optimization_enabled(shaderlodOptAoDepthReuse);
        #endif
        bool shaderlodAOLinearValid = false;
        float shaderlodAOLinearRaw = 0.0;
        float shaderlodAOLinearValue = 0.0;
        bool shaderlodAOReuse = shaderlodUnboundAODepthSamplingQualified == 1 && shaderlod_optimization_enabled(shaderlodOptAoDepthReuse);
        bool shaderlodAOValid = false;
        vec2 shaderlodAOCoord = vec2(0.0);
        float shaderlodAOValue = 0.0;
        for (int i = 1; i <= samples; i++) {
#else
        for (int i = 1; i <= samples; i++) {
#endif
            vec2 offset = OffsetDist(i + dither, samples) * scale;
            if (i % 2 == 0) offset.y = -offset.y;

            vec2 coord1 = queryCoord + offset;
            vec2 coord2 = queryCoord - offset;

#if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined OVERWORLD && defined FRAGMENT_SHADER && !defined DISTANT_HORIZONS && !defined VOXY && RP_MODE == 1 && !defined GENERATED_NORMALS && !defined COATED_TEXTURES && defined DEFERRED1
            {
            vec2 shaderlodCurrentAOCoord = coord1;
            bool shaderlodCurrentAOValid = all(greaterThanEqual(shaderlodCurrentAOCoord, vec2(0.0))) && all(lessThanEqual(shaderlodCurrentAOCoord, vec2(1.0)));
            float shaderlodCurrentAODepth;
            if (shaderlodAOReuse && shaderlodCurrentAOValid && shaderlodAOValid && all(equal(shaderlodCurrentAOCoord, shaderlodAOCoord)))
                shaderlodCurrentAODepth = shaderlodAOValue;
            else if (shaderlodAOReuse && shaderlodCurrentAOValid)
                shaderlodCurrentAODepth = texture2DLod(depthtex, shaderlodCurrentAOCoord, 0.0).r;
            else
                shaderlodCurrentAODepth = texture2D(depthtex, coord1).r;
            shaderlodAOCoord = shaderlodCurrentAOCoord;
            shaderlodAOValue = shaderlodCurrentAODepth;
            shaderlodAOValid = shaderlodCurrentAOValid;
            // farM and nearM are invariant within this AO invocation. Raw equality
            // reuses precisely the same pure native expression, never a nearby texel.
            bool shaderlodAOLinearCurrentValid = shaderlodCurrentAODepth >= 0.0 && shaderlodCurrentAODepth <= 1.0;
            if (shaderlodAOLinearReuse && shaderlodAOLinearCurrentValid && shaderlodAOLinearValid && shaderlodCurrentAODepth == shaderlodAOLinearRaw)
                sampleDepth = shaderlodAOLinearValue;
            else
                sampleDepth = GetLinearDepth(shaderlodCurrentAODepth, farM, nearM);
            shaderlodAOLinearValid = shaderlodAOLinearCurrentValid;
            shaderlodAOLinearRaw = shaderlodCurrentAODepth;
            shaderlodAOLinearValue = sampleDepth;
            }
#else
            sampleDepth = GetLinearDepth(texture2D(depthtex, coord1).r, farM, nearM);
#endif
            float aosample = aoWorldRange * (linearZ0 - sampleDepth) * 2.0;
            angle = clamp(0.5 - aosample, 0.0, 1.0);
            dist = clamp(0.5 * aosample - 1.0, 0.0, 1.0);

#if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined OVERWORLD && defined FRAGMENT_SHADER && !defined DISTANT_HORIZONS && !defined VOXY && RP_MODE == 1 && !defined GENERATED_NORMALS && !defined COATED_TEXTURES && defined DEFERRED1
            {
            vec2 shaderlodCurrentAOCoord = coord2;
            bool shaderlodCurrentAOValid = all(greaterThanEqual(shaderlodCurrentAOCoord, vec2(0.0))) && all(lessThanEqual(shaderlodCurrentAOCoord, vec2(1.0)));
            float shaderlodCurrentAODepth;
            if (shaderlodAOReuse && shaderlodCurrentAOValid && shaderlodAOValid && all(equal(shaderlodCurrentAOCoord, shaderlodAOCoord)))
                shaderlodCurrentAODepth = shaderlodAOValue;
            else if (shaderlodAOReuse && shaderlodCurrentAOValid)
                shaderlodCurrentAODepth = texture2DLod(depthtex, shaderlodCurrentAOCoord, 0.0).r;
            else
                shaderlodCurrentAODepth = texture2D(depthtex, coord2).r;
            shaderlodAOCoord = shaderlodCurrentAOCoord;
            shaderlodAOValue = shaderlodCurrentAODepth;
            shaderlodAOValid = shaderlodCurrentAOValid;
            // farM and nearM are invariant within this AO invocation. Raw equality
            // reuses precisely the same pure native expression, never a nearby texel.
            bool shaderlodAOLinearCurrentValid = shaderlodCurrentAODepth >= 0.0 && shaderlodCurrentAODepth <= 1.0;
            if (shaderlodAOLinearReuse && shaderlodAOLinearCurrentValid && shaderlodAOLinearValid && shaderlodCurrentAODepth == shaderlodAOLinearRaw)
                sampleDepth = shaderlodAOLinearValue;
            else
                sampleDepth = GetLinearDepth(shaderlodCurrentAODepth, farM, nearM);
            shaderlodAOLinearValid = shaderlodAOLinearCurrentValid;
            shaderlodAOLinearRaw = shaderlodCurrentAODepth;
            shaderlodAOLinearValue = sampleDepth;
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

