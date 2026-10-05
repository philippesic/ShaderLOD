/* MakeUp-ShaderLOD-Experimental: modified 2026-10-05.
 * Ray search below derives from MakeUp Ultra Fast 9.5g by Javier Garduño.
 * GNU LGPL v3; upstream URLs and source pin: SHADERLOD_EXPERIMENT.md.
 * Enabled water-only copy: original 10-step search/refinement/coverage math;
 * explicit LOD 0 depth fetch avoids implicit derivatives across distance skip.
 * Original fastRaymarch (including glass) remains untouched in water.glsl.
 */
#ifndef SHADERLOD_MAKEUP_WATER_SSR_HELPER
#define SHADERLOD_MAKEUP_WATER_SSR_HELPER

// fragpos is originating camera/view-relative position in Minecraft blocks.
// No world origin, ray endpoint or screen-depth distance participates.
float shaderlodWaterSSRWeight(vec3 fragpos) {
    float startBlocks = max(SHADERLOD_MAKEUP_SSR_START, 0.0);
    float endBlocks = max(SHADERLOD_MAKEUP_SSR_END, startBlocks + 1.0);
    float distanceSquared = dot(fragpos, fragpos);
    if (distanceSquared >= endBlocks * endBlocks) return 0.0;
    if (distanceSquared <= startBlocks * startBlocks) return 1.0;
    return 1.0 - smoothstep(startBlocks, endBlocks, sqrt(distanceSquared));
}

vec3 shaderlodWaterRaymarch(vec3 direction, vec3 hitPoint, inout float infinite, float dither) {
    vec3 pathIncrement;
    vec3 currentMarchPoint = hitPoint;
    vec3 oldMarchPoint;
    float screenDepth;
    float depthDifference = 1.0;
    vec3 screenMarchPos = camera_to_screen(hitPoint);
    float prevScreenDepth = screenMarchPos.z;
    float hitPointDepth = screenMarchPos.z;
    bool searchFlag = false;
    bool hiddenFlag = false;
    bool firstHidden = true;
    bool outOfEyeFlag = false;
    bool toFar = false;
    vec3 lastScreenMarchPos;

    int no_hidden_steps = 0;
    bool hiddens = false;

    // Ray marching
    for (int i = 0; i < RAYMARCH_STEPS; i++) {
        if (searchFlag) {
            pathIncrement *= 0.5;
            currentMarchPoint += pathIncrement * sign(depthDifference);
        } else {
            oldMarchPoint = currentMarchPoint;
            currentMarchPoint = hitPoint + ((direction * exp2(i + dither)) - direction);
            pathIncrement = currentMarchPoint - oldMarchPoint;
        }

        lastScreenMarchPos = screenMarchPos;
        screenMarchPos = camera_to_screen(currentMarchPoint);

        if ( // Is outside screen space
            screenMarchPos.x < 0.0 ||
            screenMarchPos.x > 1.0 ||
            screenMarchPos.y < 0.0 ||
            screenMarchPos.y > 1.0 ||
            screenMarchPos.z < 0.0
        ) {
            outOfEyeFlag = true;
        }

        if (screenMarchPos.z > 0.9999) {
            toFar = true;
        }

        screenDepth = texture2DLod(depthtex1, screenMarchPos.xy, 0.0).x;
        depthDifference = screenDepth - screenMarchPos.z;

        if (depthDifference < 0.0 && abs(screenDepth - prevScreenDepth) > abs(screenMarchPos.z - lastScreenMarchPos.z)) {
            hiddenFlag = true;
            hiddens = true;
            if (firstHidden) {
                firstHidden = false;
            }
        } else if (depthDifference > 0.0) {
            hiddenFlag = false;
            if (!hiddens) {
                no_hidden_steps++;
            }
        }

        if (searchFlag == false && depthDifference < 0.0 && hiddenFlag == false) {
            searchFlag = true;
        }

        prevScreenDepth = screenDepth;
    }

    infinite = float(screenDepth > 0.9999);

    if (outOfEyeFlag) {
        infinite = 1.0;
        return screenMarchPos;
    } else if (toFar) {
        if (screenDepth > 0.9999) {
            infinite = 1.0;
            return screenMarchPos;
        } else if (no_hidden_steps < 3 || screenDepth > hitPointDepth) {
            return screenMarchPos;
        } else {
            infinite = 1.0;
            return vec3(1.0);
        }
    } else {
        return screenMarchPos;
    }
}

#endif
