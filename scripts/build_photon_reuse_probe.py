#!/usr/bin/env python3
"""Untimed same-fragment G08 hit/optical-depth/cloud-output diagnostic.

Derived from the actually tested public archive. No native quality option,
allocation, sampler or phase is changed. RT9 is diagnostic output only.
"""
import copy
import hashlib
import json
from pathlib import Path
import zipfile

BASE='c4e508a6df6ebfd819ef9c5638c68c8e902ff9e10a48e36a9f7b586afd1f5159'
ORIGINAL='120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead'
HELPER='shaders/include/shaderlod/volume_light_rate.glsl'
PROGRAM='shaders/program/d1_clouds.fsh'
MARKER='SHADERLOD_G08_CLOUD_DIAGNOSTIC.json'


def sha(raw):return hashlib.sha256(raw).hexdigest()


def once(source,before,after):
    if source.count(before)!=1:raise ValueError('G08 diagnostic anchor changed: '+before[:80])
    return source.replace(before,after)


def build(base,output):
    base=Path(base);output=Path(output)
    if sha(base.read_bytes())!=BASE or output.exists():raise ValueError('Exact tested public archive and fresh output required')
    with zipfile.ZipFile(base) as native:
        helper=native.read(HELPER).decode();program=native.read(PROGRAM).decode()
        settings=native.read('shaders/settings.glsl').decode()
        if '\n#define SHADERLOD_VOLUME_LIGHT_RATE_REUSE\n' not in settings:raise ValueError('Actual reuse kernel not compiled')
        helper=once(helper,'#define SHADERLOD_VOLUME_LIGHT_RATE_HELPER',
                    '#define SHADERLOD_VOLUME_LIGHT_RATE_HELPER\nconst int shaderlodG08DiagnosticSchema=1023;\nbool shaderlodG08Native=false;\nuint shaderlodG08Hits=0u;\nbool shaderlodG08DepthDifference=false;')
        helper=once(helper,'if (!shaderlod_optimization_enabled(shaderlodOptVolumeLightReuse)) {','if (shaderlodG08Native) {')
        helper=once(helper,'        if (reuse_lighting) {','''        if (reuse_lighting) {
            shaderlodG08Hits += 1u;
            vec2 shaderlodG08Hash=hash2(fract(ray_pos));
            vec2 shaderlodG08Fresh=vec2(
                clouds_cumulus_optical_depth(ray_pos,light_dir,shaderlodG08Hash.x,lighting_steps,cached_lighting_extent),
                clouds_cumulus_optical_depth(ray_pos,sky_dir,shaderlodG08Hash.y,ambient_steps,cached_ambient_extent));
            shaderlodG08DepthDifference = shaderlodG08DepthDifference || any(notEqual(floatBitsToUint(shaderlodG08Fresh),floatBitsToUint(cached_depths)));''')
        program=once(program,'    clouds = vec4(0.0, 0.0, 0.0, 1.0);\n\n#if defined WORLD_OVERWORLD',
                     '    clouds = vec4(0.0, 0.0, 0.0, 1.0);\n    vec4 shaderlodG08Reference=clouds;\n    vec2 shaderlodG08ReferenceData=vec2(1e6,0.0);\n\n#if defined WORLD_OVERWORLD')
        program=once(program,'    clouds.xyz = result.scattering.xyz;','''    shaderlodG08Native=true;
    CloudsResult shaderlodG08Result=draw_clouds(ray_origin,ray_dir,clear_sky,distance_to_terrain,dither);
    shaderlodG08Native=false;
    shaderlodG08Reference=vec4(shaderlodG08Result.scattering.xyz,shaderlodG08Result.transmittance);
    shaderlodG08ReferenceData=vec2(shaderlodG08Result.apparent_distance*rcp(CLOUDS_SCALE),shaderlodG08Result.scattering.w);
    clouds.xyz = result.scattering.xyz;''')
        program=once(program,'    clouds *= crepuscular_rays.w;','    clouds *= crepuscular_rays.w;\n    shaderlodG08Reference *= crepuscular_rays.w;')
        program=once(program,'    clouds.rgb += crepuscular_rays.xyz;','    clouds.rgb += crepuscular_rays.xyz;\n    shaderlodG08Reference.rgb += crepuscular_rays.xyz;')
        program=once(program,'    clouds.xyz += draw_aurora(ray_dir, dither) * clouds.w;','''    vec3 shaderlodG08Aurora=draw_aurora(ray_dir,dither);
    clouds.xyz += shaderlodG08Aurora*clouds.w;
    shaderlodG08Reference.xyz += shaderlodG08Aurora*shaderlodG08Reference.w;
    bool shaderlodG08OutputDifference=any(notEqual(floatBitsToUint(clouds),floatBitsToUint(shaderlodG08Reference))) ||
        any(notEqual(floatBitsToUint(clouds_data),floatBitsToUint(shaderlodG08ReferenceData)));
    bool shaderlodG08CandidateNonfinite=any(isnan(clouds)) || any(isinf(clouds)) || any(isnan(clouds_data)) || any(isinf(clouds_data));
    bool shaderlodG08ReferenceNonfinite=any(isnan(shaderlodG08Reference)) || any(isinf(shaderlodG08Reference)) || any(isnan(shaderlodG08ReferenceData)) || any(isinf(shaderlodG08ReferenceData));
    int shaderlodG08OutputCode=(shaderlodG08OutputDifference?1:0)+(shaderlodG08CandidateNonfinite?2:0)+(shaderlodG08ReferenceNonfinite?4:0);
    clouds=vec4(float(shaderlodG08Hits),shaderlodG08DepthDifference?1.0:0.0,float(shaderlodG08OutputCode),float(shaderlodG08DiagnosticSchema));''')
        notice={'diagnostic_only':True,'schema':1023,'original_sha256':ORIGINAL,'base_sha256':BASE,
                'producer_sha256':sha(Path(__file__).read_bytes()),'modified_members':[HELPER,PROGRAM],'removed_metadata':['shaderlod-selection.json'],
                'base_selection_marker_sha256':sha(native.read('shaderlod-selection.json')),
                'scope':'Same-fragment actual reuse hits, same-input fresh versus reused optical-depth bits, and native versus reused cloud-program float outputs before RT storage. RT9 diagnostic only; no performance, displayed-image equivalence or hardware-fetch claim. Core Off/empty requests required; reuse selection explicitly private.'}
        output.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(output,'x') as candidate:
            for info in native.infolist():
                if info.filename=='shaderlod-selection.json':continue
                payload={HELPER:helper.encode(),PROGRAM:program.encode()}.get(info.filename,native.read(info.filename))
                candidate.writestr(copy.copy(info),payload)
            candidate.writestr(MARKER,json.dumps(notice,indent=2)+'\n')
    return notice|{'sha256':sha(output.read_bytes())}
