// Native dbao is retained verbatim. Only distant, depth-continuous AO is reused.
#include "/lib/g06/scope.glsl"
#ifdef G06_AO_ACTIVE
uniform sampler2D colortex8;
// Only for the untimed mode 3; the compiler removes this state in mode 2.
float g06Decision = 0.0;

float g06Ao(float z, float dither) {
    #if G06_AO_SPATIAL == 1
        return dbao(dither); // Authentic producer-only cost control.
    #else
    vec2 fullSize = floor(vec2(1.0 / pixelSizeX, 1.0 / pixelSizeY) + 0.5);
    float blocks = ld(z) * far; // Native AO depth measure, not radial distance.
    if (!(z >= 0.56 && z < 1.0 && blocks > 64.0 && blocks < 1.0e6) ||
        any(notEqual(mod(fullSize, 2.0), vec2(0.0)))) return dbao(dither);
    g06Decision = 1.0;
    vec2 halfSize = fullSize * 0.5;
    // Producer anchors are full-resolution pixels (2*x + 0.5, 2*y + 0.5).
    vec2 position = (gl_FragCoord.xy - 0.5) * 0.5;
    vec2 base = floor(position);
    vec2 f = fract(position);
    vec2 lo = clamp(base, vec2(0.0), halfSize - 1.0);
    vec2 hi = clamp(base + 1.0, vec2(0.0), halfSize - 1.0);
    vec2 a = texture2DLod(colortex8, (lo + 0.5) / halfSize, 0.0).rg;
    if (a.x < 0.0 || a.x > 1.0 || abs(a.y - blocks) > max(0.125, blocks * 0.002))
        return dbao(dither); // Later taps cannot reverse rejection.
    vec2 b = texture2DLod(colortex8, (vec2(hi.x, lo.y) + 0.5) / halfSize, 0.0).rg;
    if (b.x < 0.0 || b.x > 1.0 || abs(b.y - blocks) > max(0.125, blocks * 0.002))
        return dbao(dither); // Later taps cannot reverse rejection.
    vec2 c = texture2DLod(colortex8, (vec2(lo.x, hi.y) + 0.5) / halfSize, 0.0).rg;
    if (c.x < 0.0 || c.x > 1.0 || abs(c.y - blocks) > max(0.125, blocks * 0.002))
        return dbao(dither); // Later taps cannot reverse rejection.
    vec2 d = texture2DLod(colortex8, (hi + 0.5) / halfSize, 0.0).rg;
    if (d.x < 0.0 || d.x > 1.0 || abs(d.y - blocks) > max(0.125, blocks * 0.002))
        return dbao(dither); // Later taps cannot reverse rejection.
    g06Decision = 2.0;
    return mix(mix(a.x, b.x, f.x), mix(c.x, d.x, f.x), f.y);
    #endif
}
#endif
