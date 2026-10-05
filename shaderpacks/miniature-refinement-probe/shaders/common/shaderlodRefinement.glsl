/* Miniature 2.19 private refinement probe; modified 2026-10-05.
 * Derived from Miniature by Mateus A. Kreuch, Copyright (c) 2022.
 * MIT notice restored as LICENSE.Miniature; source/contract in probe archive.
 * Original function remains untouched. Each eligible pixel runs one SSR path.
 * Explicit LOD 0 syntax relies on inspected pinned Iris translation; driver
 * compilation/image parity are unverified. Literal kernels retain outer march.
 */
#ifndef SHADERLOD_MINIATURE_REFINEMENT_INCLUDED
#define SHADERLOD_MINIATURE_REFINEMENT_INCLUDED

uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform float shaderlodNear;
uniform float shaderlodFar;

vec4 shaderlodMiniatureExplicit4(float depth, vec3 normal, vec3 viewPos) {
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
      vec2 curUV  = view2screen(curPos).st;

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
            for (int j = 0; j < 4; j++) {
               curPos = (a + b) * 0.5;
               curUV = view2screen(curPos).st;
               sceneDepth = texture2DLod(depthtex0, curUV, 0.0).x;
               sceneZ = screen2view(curUV, sceneDepth).z;

               if (-curPos.z < -sceneZ) { a = curPos; }
               else                     { b = curPos; }
            }
         }

         return sceneDepth + 0.0001 <= depth
               ? vec4(0.0)
               : vec4(texture2DLod(colortex0, curUV, 0.0).rgb,
                      getReflectionVignette(curUV) * fresnel);
      }

      oldPos = curPos;
      lengthR += max(SSR_STEP_SIZE * abs(diffZ) * invR, 1.0);
   }

   return vec4(0.0);
}

vec4 shaderlodMiniatureExplicit3(float depth, vec3 normal, vec3 viewPos) {
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
      vec2 curUV  = view2screen(curPos).st;

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
            for (int j = 0; j < 3; j++) {
               curPos = (a + b) * 0.5;
               curUV = view2screen(curPos).st;
               sceneDepth = texture2DLod(depthtex0, curUV, 0.0).x;
               sceneZ = screen2view(curUV, sceneDepth).z;

               if (-curPos.z < -sceneZ) { a = curPos; }
               else                     { b = curPos; }
            }
         }

         return sceneDepth + 0.0001 <= depth
               ? vec4(0.0)
               : vec4(texture2DLod(colortex0, curUV, 0.0).rgb,
                      getReflectionVignette(curUV) * fresnel);
      }

      oldPos = curPos;
      lengthR += max(SSR_STEP_SIZE * abs(diffZ) * invR, 1.0);
   }

   return vec4(0.0);
}

vec4 shaderlodMiniatureExplicit2(float depth, vec3 normal, vec3 viewPos) {
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
      vec2 curUV  = view2screen(curPos).st;

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
            for (int j = 0; j < 2; j++) {
               curPos = (a + b) * 0.5;
               curUV = view2screen(curPos).st;
               sceneDepth = texture2DLod(depthtex0, curUV, 0.0).x;
               sceneZ = screen2view(curUV, sceneDepth).z;

               if (-curPos.z < -sceneZ) { a = curPos; }
               else                     { b = curPos; }
            }
         }

         return sceneDepth + 0.0001 <= depth
               ? vec4(0.0)
               : vec4(texture2DLod(colortex0, curUV, 0.0).rgb,
                      getReflectionVignette(curUV) * fresnel);
      }

      oldPos = curPos;
      lengthR += max(SSR_STEP_SIZE * abs(diffZ) * invR, 1.0);
   }

   return vec4(0.0);
}

vec4 shaderlodMiniatureRefinement(float depth, vec3 normal, vec3 viewPos) {
    // Uniform eligibility branch preserves original function/fetch source for
    // absent/old/future bridge, global disable or unqualified native stride.
    if (shaderlodBridgeVersion != 2 || shaderlodEnabled == 0 || SSR_STEP_SIZE != 1.6) {
        return getReflectionColor(depth, normal, viewPos);
    }
#if SHADERLOD_MINIATURE_REFINE_MODE == 1
    // Separate full-four explicit-sampling control; no distance classifier.
    return shaderlodMiniatureExplicit4(depth, normal, viewPos);
#else
    float nearBlocks = max(shaderlodNear, 0.0);
    float farBlocks = max(shaderlodFar, nearBlocks + 1.0);
    float radialBlocks = length(viewPos);
    // Stable radial zones, no random mask. These integer transitions may ring.
    if (radialBlocks <= nearBlocks) return shaderlodMiniatureExplicit4(depth, normal, viewPos);
    if (radialBlocks >= farBlocks) return shaderlodMiniatureExplicit2(depth, normal, viewPos);
    return shaderlodMiniatureExplicit3(depth, normal, viewPos);
#endif
}

#endif
