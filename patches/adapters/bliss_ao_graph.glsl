uniform int shaderlodOptAoRenderScale;
uniform int shaderlodBlissAoTargetReady;
uniform vec2 shaderlodBlissAoTargetSize;
bool shaderlodBlissAoActive() {
    return shaderlod_optimization_enabled(shaderlodOptAoRenderScale) &&
        shaderlodBlissAoTargetReady == 1 &&
        shaderlodBlissAoTargetSize == vec2(viewWidth,viewHeight) &&
        shaderlodNear > 0.0 && shaderlodNear <= 4096.0;
}
