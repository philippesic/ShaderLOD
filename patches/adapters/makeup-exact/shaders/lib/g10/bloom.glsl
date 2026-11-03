/* MakeUp - bloom.glsl
Bloom functions.

Javier Garduño - GNU Lesser General Public License v3.0
*/


// ShaderLOD G10 modification: conditional bilinear-moment pairing, 2026-10-26.
// Original stencil and accumulation remain the fallback. No tone-map/AA reorder.
#if (3 >= 1 && 3 <= 4) && defined COMPOSITE1_SHADER && !defined UNKNOWN_DIM && defined IS_IRIS && defined IRIS_VERSION && IRIS_VERSION == 10812 && defined MC_VERSION && MC_VERSION == 12101 && !defined NETHER && !defined THE_END && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH
int shaderlodG10Pairs = 0;

bool shaderlodG10Cell(vec2 coords, vec2 radius, vec2 size) {
    // One center fraction per axis replaces two endpoint floors. A larger
    // 0.02-texel margin covers FP32 evaluation differences conservatively for
    // the <=4096 selected target. This changes admission, not pairing math.
    vec2 center = coords * size - 0.5;
    vec2 reach = radius * size;
    vec2 phase = fract(center);
    return all(lessThanEqual(size, vec2(4096.0))) &&
           all(greaterThan(center - reach, vec2(0.02))) &&
           all(lessThan(center + reach, size - vec2(1.02))) &&
           all(greaterThanEqual(phase, reach + vec2(0.02))) &&
           all(lessThan(phase, vec2(0.98) - reach));
}
#endif

vec3 mipmap_bloom(sampler2D image, vec2 coords, float dither) {
    vec3 blurSample = vec3(0.0);
    vec2 blurRadiusVec = vec2(0.1 * aspectRatioInverse, 0.1);

    int samplesQuantity = int(BLOOM_SAMPLES);

    vec2 blurRadiusFactor = blurRadiusVec * (1.0 / BLOOM_SAMPLES);
    float n;
    vec2 offset;
    vec2 offset2;
    float ditherShifted;


    #if (3 >= 1 && 3 <= 4) && defined COMPOSITE1_SHADER && !defined UNKNOWN_DIM && defined IS_IRIS && defined IRIS_VERSION && IRIS_VERSION == 10812 && defined MC_VERSION && MC_VERSION == 12101 && !defined NETHER && !defined THE_END && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && 3 <= 2
        // Iris full-size colortex0; mip sizes floor each level, also at odd sizes.
        float g10Level = floor(softLod);
        vec2 g10Size0 = max(floor(vec2(viewWidth, viewHeight) / exp2(g10Level)), vec2(1.0));
        vec2 g10Size1 = max(floor(vec2(viewWidth, viewHeight) / exp2(ceil(softLod))), vec2(1.0));
    #endif

    for(int i = 0; i < samplesQuantity; i++) {
        ditherShifted = i + dither;
        n = fract(ditherShifted * 1.6180339887) * 6.283185307179586;
        offset = vec2(cos(n), sin(n)) * ditherShifted * blurRadiusFactor;
        offset2 = vec2(-offset.y * 1.25, offset.x * 1.25);

        #if (3 >= 1 && 3 <= 4) && defined COMPOSITE1_SHADER && !defined UNKNOWN_DIM && defined IS_IRIS && defined IRIS_VERSION && IRIS_VERSION == 10812 && defined MC_VERSION && MC_VERSION == 12101 && !defined NETHER && !defined THE_END && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && 3 >= 3
            // Native i+dither is exactly zero. Every native offset is
            // signed zero; fullscreen coords are finite and strictly positive.
            if (i == 0 && dither == 0.0 && softLod >= 0.0 && softLod <= 16.0) {
                // Reuse an actually identical native sample; retain all four
                // native additions in their original order, no filter moments.
                vec3 g10Sample = texture2DLod(image, coords, softLod).rgb;
                blurSample += g10Sample;
                blurSample += g10Sample;
                blurSample += g10Sample;
                blurSample += g10Sample;
                #if 3 == 4
                    shaderlodG10Pairs++;
                #endif
            } else {
        blurSample += texture2DLod(image, coords + offset, softLod).rgb;
        blurSample += texture2DLod(image, coords - offset, softLod).rgb;
        blurSample += texture2DLod(image, coords + offset2, softLod).rgb;
        blurSample += texture2DLod(image, coords - offset2, softLod).rgb;
            }
        #elif (3 >= 1 && 3 <= 4) && defined COMPOSITE1_SHADER && !defined UNKNOWN_DIM && defined IS_IRIS && defined IRIS_VERSION && IRIS_VERSION == 10812 && defined MC_VERSION && MC_VERSION == 12101 && !defined NETHER && !defined THE_END && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH
            vec2 g10Radius = max(abs(offset), abs(offset2));
            // The first ring contains the useful small footprints. Keep later
            // rings native and avoid paying cell-guard ALU for them.
            if (i == 0 && shaderlodG10Cell(coords, g10Radius, g10Size0) &&
                shaderlodG10Cell(coords, g10Radius, g10Size1)) {
                // B(x,y)=a+bx+cy+dxy within each certified bilinear cell.
                // Native mean offset=0; mean(dx*dy)=-(9/32)*offset.x*offset.y.
                // +/- (3/4*x,-3/8*y) has the same mean and mixed moment.
                // Both new points lie inside the certified native bounding box.
                vec2 g10Offset = offset * vec2(0.75, -0.375);
                blurSample += texture2DLod(image, coords + g10Offset, softLod).rgb * 2.0;
                blurSample += texture2DLod(image, coords - g10Offset, softLod).rgb * 2.0;
                #if 3 == 2
                    shaderlodG10Pairs++;
                #endif
            } else {
        blurSample += texture2DLod(image, coords + offset, softLod).rgb;
        blurSample += texture2DLod(image, coords - offset, softLod).rgb;
        blurSample += texture2DLod(image, coords + offset2, softLod).rgb;
        blurSample += texture2DLod(image, coords - offset2, softLod).rgb;
            }
        #else
        blurSample += texture2DLod(image, coords + offset, softLod).rgb;
        blurSample += texture2DLod(image, coords - offset, softLod).rgb;
        blurSample += texture2DLod(image, coords + offset2, softLod).rgb;
        blurSample += texture2DLod(image, coords - offset2, softLod).rgb;
        #endif
    }

    blurSample /= (BLOOM_SAMPLES * 4.0);

    return blurSample;
}
