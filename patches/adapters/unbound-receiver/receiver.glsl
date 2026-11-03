// Native full-rate unbiased receiver chart. Support also includes native XY center/bias displacement.
uniform int shaderlodComplementaryReceiverPermission;
uniform int shaderlodOptShadowReceiverFootprint;
vec2 shaderlodComplementaryReceiverUv=vec2(0.0),shaderlodComplementaryReceiverDx=vec2(0.0),shaderlodComplementaryReceiverDy=vec2(0.0);
bool shaderlodComplementaryReceiverOrdinary=false;
#include "/lib/shaderlod/receiver_footprint_budget_r1.glsl"
