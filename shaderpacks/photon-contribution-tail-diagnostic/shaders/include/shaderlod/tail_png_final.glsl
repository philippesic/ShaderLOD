// Private query-free diagnostic for Photon v1.3b by SixthSurge.
// Existing targets 9/10 only; deliberate output destruction, never a speed/quality run.
uniform sampler2D colortex9;
uniform sampler2D colortex10;
uniform int frameCounter;
uniform int worldTime;
uniform float viewWidth;
// viewHeight is already declared by upstream final.

vec3 shaderlod_diag_nibbles(uvec3 values, uint shift) {
    return vec3((values >> shift) & uvec3(15u)) / 15.0;
}
uint shaderlod_diag_header(int i, ivec2 native_size, ivec2 cloud_size) {
    // Byte protocol: magic/schema/stage/source tag, dimensions, frame/time and tail option.
    uint bytes[28] = uint[28](199u, 1u, 1u, 199u, 161u, 77u, 50u,
        uint(native_size.x)&255u, uint(native_size.x)>>8u,
        uint(native_size.y)&255u, uint(native_size.y)>>8u,
        uint(cloud_size.x)&255u, uint(cloud_size.x)>>8u,
        uint(cloud_size.y)&255u, uint(cloud_size.y)>>8u,
        uint(frameCounter)&255u, (uint(frameCounter)>>8u)&255u,
        uint(worldTime)&255u, (uint(worldTime)>>8u)&255u,
#ifdef SHADERLOD_CONTRIBUTION_TAIL
        1u,
#else
        0u,
#endif
        uint(CLOUDS_TEMPORAL_UPSCALING), uint(CLOUDS_CUMULUS_PRIMARY_STEPS_H),
        uint(CLOUDS_CUMULUS_PRIMARY_STEPS_Z), 0u, 0u, 0u, 0u, 0u);
    uint sum = 0u;
    for (int j=0;j<23;++j) sum += bytes[j];
    bytes[23] = sum&255u;
    // Reserved bytes are fixed sentinels.
    bytes[24]=165u; bytes[25]=90u; bytes[26]=60u; bytes[27]=195u;
    return bytes[clamp(i,0,27)];
}
void shaderlod_diag_final() {
    ivec2 p=ivec2(gl_FragCoord.xy);
    ivec2 native_size=ivec2(viewWidth,viewHeight);
    ivec2 cloud_size=textureSize(colortex9,0);
    fragment_color=vec3(1.0,0.0,1.0);
    // Known 16-level calibration per channel: no CAS, EOTF or dithering assumptions.
    if (p.y==0) { fragment_color=vec3(float(p.x%16)/15.0); return; }
    if (p.y==1 && p.x<56) {
        uint value=shaderlod_diag_header(p.x/2,native_size,cloud_size);
        fragment_color=vec3(float((value >> uint(4*(p.x%2)))&15u)/15.0);return;
    }
    if (p.y<8) { fragment_color=vec3(5.0/15.0,10.0/15.0,3.0/15.0);return; }
    ivec2 q=ivec2(p.x/4,p.y-8);
    if (q.x>=cloud_size.x || q.y>=cloud_size.y) return;
    vec4 raw=texelFetch(colortex9,q,0);
    vec2 stamp=texelFetch(colortex10,q,0).xy;
    bool valid=all(greaterThanEqual(raw,vec4(0.0))) && all(lessThanEqual(raw,vec4(255.0))) &&
        all(equal(raw,floor(raw))) && !any(isnan(raw)) && !any(isinf(raw)) &&
        stamp.x==float((frameCounter+37*worldTime)%1024) &&
        stamp.y>=0.0 && stamp.y<=255.0 && stamp.y==floor(stamp.y) &&
        !isnan(stamp.y) && !isinf(stamp.y);
#if defined TAAU || defined VOXY || defined BLOCKY_CLOUDS || CLOUDS_TEMPORAL_UPSCALING != 4
    valid=false;
#endif
    uvec4 v=valid?uvec4(raw):uvec4(255u);
    if (p.x%4<2) {fragment_color=shaderlod_diag_nibbles(v.xyz,uint(4*(p.x%4)));return;}
    uint optical_digest=valid?uint(stamp.y):255u;
    if (p.x%4==3) {fragment_color=vec3(float(optical_digest&15u),float(optical_digest>>4u),float(15u-(optical_digest&15u)))/15.0;return;}
    uint check=(v.x+3u*v.y+5u*v.z+7u*v.w+optical_digest+(optical_digest>>4u)+uint(q.x)+3u*uint(q.y))&15u;
    fragment_color=vec3(float(v.w&15u),float(v.w>>4u),float(check))/15.0;
}
