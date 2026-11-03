#!/usr/bin/env python3
"""Build private pinned Sildur receiver experiments; default mode is native."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "patches/sildur-experiments/receiver-budget-r1.json"
SHARED = ROOT / "shaderpacks/common/shaders/include/shaderlod/receiver_footprint_budget_r1.glsl"
IRIS = ROOT / "dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar"
MEMBER = "shaders/deferred.fsh"
PROPERTIES = "shaders/shaders.properties"
INCLUDE = "shaders/lib/shaderlod/receiver_footprint_budget_r1.glsl"
MODES = ("native", "candidate", "native-equivalent")
GUARD = ("SHADERLOD_SILDUR_RECEIVER_BUDGET == 1 && defined IS_IRIS"
         " && defined IRIS_VERSION && IRIS_VERSION == 10812"
         " && defined MC_VERSION && MC_VERSION == 12101 && defined MC_OS_MAC"
         " && defined Shadows && defined TAA && defined PCSS && nMap == 0"
         " && shadow_samples == 12 && !defined raytracedShadows"
         " && !defined DISTANT_HORIZONS && !defined VOXY")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def members_digest(files: dict[str, bytes]) -> str:
    digest = hashlib.sha256()
    for name, data in sorted(files.items()):
        encoded = name.encode()
        digest.update(len(encoded).to_bytes(8, "big"))
        digest.update(encoded)
        digest.update(hashlib.sha256(data).digest())
    return digest.hexdigest()


def read_archive(path: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate archive member")
        return {name: archive.read(name) for name in names}


def replace_once(source: str, before: str, after: str) -> str:
    if source.count(before) != 1:
        raise ValueError("Pinned source boundary missing or ambiguous: " + before[:65])
    return source.replace(before, after, 1)


def patch_receiver(source: bytes, mode: str) -> bytes:
    if mode not in MODES:
        raise ValueError("Unknown receiver mode")
    text = source.decode()
    # The original nMap option lives only in this existing settings section.
    # Expose its SAME declaration to deferred, without copying/renaming/defaulting it.
    text = replace_once(text, '#include "shaders.settings"',
        '#define gbuffers_textured // ShaderLOD: expose native nMap option at this consumer.\n'
        '#include "shaders.settings"\n#undef gbuffers_textured\n'
        f'#define SHADERLOD_SILDUR_RECEIVER_BUDGET {int(mode != "native")}\n'
        f'#if {GUARD}\n#define SHADERLOD_SILDUR_RECEIVER_ACTIVE\n'
        '#include "/lib/shaderlod/receiver_footprint_budget_r1.glsl"\n'
        'uniform float shaderlodReceiverReductionPermission;\n'
        'float shaderlodSildurReceiverWeight = 0.0;\n#endif')
    # Original loop is retained byte-for-byte inside its native branch.
    begin = '\t\tvec2 shadows = vec2(0.0);'
    end = '\t\tshading = shadows * rShadowSamples;'
    if text.count(begin) != 1 or text.count(end) != 1:
        raise ValueError("Native filter loop boundary changed")
    native = text[text.index(begin):text.index(end) + len(end)]
    # Native12 has three angular rays, four radii per ray (indices j+0,3,6,9).
    # Transfer each i=6..8 weight equally to i-3 and i+3, its alpha endpoints.
    # Mass and radial second moments survive. First moments change by the sqrt
    # Jensen gap; this is approximation, not a visibility-error bound.
    reduced = native.replace('for(int i = 0; i < shadow_samples; i++){',
        'for(int i = 0; i < shadow_samples; i++){\n'
        '\t\t\tbool slOmitted = i >= 6 && i < 9;\n'
        '\t\t\tif (slOmitted && shaderlodSildurReceiverWeight >= 1.0) continue;\n'
        '\t\t\tfloat slTapWeight = slOmitted ? 1.0 - shaderlodSildurReceiverWeight\n'
        '\t\t\t    : (i >= 3 ? 1.0 + 0.5 * shaderlodSildurReceiverWeight : 1.0);')
    reduced = reduced.replace('))).x;', '))).x * slTapWeight;')
    if reduced.count('.x * slTapWeight;') != 2:
        raise ValueError('Both native channels require identical weights')
    # Native >=0.5 px; nine positions <=0.25 px; transition retains twelve.
    replacement = ('#ifdef SHADERLOD_SILDUR_RECEIVER_ACTIVE\n'
        '\t\tif (shaderlodSildurReceiverWeight > 0.0) {\n' + reduced + '\n'
        '\t\t} else {\n' + native + '\n\t\t}\n#else\n' + native + '\n#endif')
    text = replace_once(text, native, replacement)
    anchor = '\n\tfloat ao = 1.0;'
    prelude = '''
#ifdef SHADERLOD_SILDUR_RECEIVER_ACTIVE
    // Main is still uniform here: all derivative evaluations precede land/sky,
    // diffuse, hand and shadow-map branches. This is a LOCAL affine estimate.
    vec3 slPos = mat3(gbufferModelViewInverse) * fragpos0 + gbufferModelViewInverse[3].xyz;
    slPos = mat3(shadowModelView) * slPos + shadowModelView[3].xyz;
    slPos = diagonal3(shadowProjection) * slPos + shadowProjection[3].xyz;
    float slDistortion = utilDistortion(slPos.xy);
    vec2 slUv = slPos.xy * slDistortion * 0.5 + 0.5;
    vec2 slDx = dFdx(slUv);
    vec2 slDy = dFdy(slUv);
    vec3 slViewDx = dFdx(fragpos0);
    vec3 slViewDy = dFdy(fragpos0);
    vec3 slNormalDx = dFdx(normal);
    vec3 slNormalDy = dFdy(normal);
    float slMatDx = dFdx(mats);
    float slMatDy = dFdy(mats);
    float slPdepth = texture2D(colortex2, texcoord.xy).b;
    float slPdepthDx = dFdx(slPdepth);
    float slPdepthDy = dFdy(slPdepth);
    float slHandDepth = texture2D(depthtex2, texcoord.xy).x;
    float slHandDx = dFdx(slHandDepth - depth0);
    float slHandDy = dFdy(slHandDepth - depth0);
    float slRadius = slPdepth * slDistortion * Nearshadowplane * k / shadowMapResolution;
    // A full texel per axis conservatively includes the hardware PCF support.
    vec2 slSupport = vec2(abs(slRadius) + 1.0 / float(shadowMapResolution));
    float slRange = length(fragpos0);
    bool slEligible = depth0 > 0.0 && depth0 < 1.0 && isEyeInWater == 0 && !isNether
        && noise >= 0.0 && noise < 1.0 && framemod8 >= 0 && framemod8 < 8
        && mats == 0.0 && slMatDx == 0.0 && slMatDy == 0.0
        && dot(normal, normal) > 0.99 && dot(normal, normal) < 1.01
        && length(slNormalDx) < 0.02 && length(slNormalDy) < 0.02
        && length(slViewDx) < slRange * 0.05 && length(slViewDy) < slRange * 0.05
        && slHandDepth == depth0 && slHandDx == 0.0 && slHandDy == 0.0
        && slPdepth >= 1.412 && slPdepth <= 30.01
        && abs(slPdepthDx) < 0.05 * slPdepth && abs(slPdepthDy) < 0.05 * slPdepth
        && slDistortion > 0.0 && slDistortion < 100.0 && slRadius > 0.0
        && abs(slPos.z) < 5.9
        && all(greaterThan(slUv - slSupport, vec2(0.0)))
        && all(lessThan(slUv + slSupport, vec2(1.0)));
    float slWeight = shaderlod_receiver_reduction_weight_r1(slEligible, slDx, slDy, slSupport, 1.0);
    // Permission defaults to zero if unbound. Unexpected values fail native.
    // Candidate/full-work counterfactual differ only in this runtime value.
    shaderlodSildurReceiverWeight = slWeight * (shaderlodReceiverReductionPermission == 1.0
        ? clamp(shaderlodReceiverReductionPermission, 0.0, 1.0) : 0.0);
#endif
'''
    text = replace_once(text, anchor, '\n' + prelude + anchor)
    return text.encode()


def apply(files: dict[str, bytes], manifest: dict, mode: str = "native") -> dict[str, bytes]:
    if mode not in MODES or manifest.get("id") != "sildur-receiver-budget-r1":
        raise ValueError("Unsupported receiver manifest or mode")
    if members_digest(files) != manifest['original_members_digest']:
        raise ValueError("Original archive member set/content changed")
    for name, digest in manifest['source_members'].items():
        if sha256(files[name]) != digest:
            raise ValueError("Pinned source changed: " + name)
    shared = SHARED.read_bytes()
    if sha256(shared) != manifest['shared_policy_sha256']:
        raise ValueError("Shared policy source changed")
    if INCLUDE in files:
        raise ValueError("Include collides with original member")
    result = dict(files)
    result[MEMBER] = patch_receiver(files[MEMBER], mode)
    if sha256(result[MEMBER]) != manifest['patched_receiver_sha256'][mode]:
        raise ValueError('Receiver patch differs from reviewed source pin')
    # Numeric values are custom-uniform expressions, never BooleanParser keys.
    value = '1.0' if mode == 'candidate' else '0.0'
    result[PROPERTIES] += ('\n# Private ShaderLOD receiver R1 runtime counterfactual\n'
        'uniform.float.shaderlodReceiverReductionPermission=' + value + '\n').encode()
    result[INCLUDE] = shared
    return result


def validate_options(data: bytes) -> None:
    # Original user options are copied byte-for-byte. No experiment override can
    # silently change the baked switch or take ownership of a private uniform.
    for line in data.decode('utf-8').splitlines():
        line = line.strip()
        if line and not line.startswith('#') and ('shaderlod' in line.lower()):
            raise ValueError("Private experiment overrides are not native options")


def canonical(value) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(',', ':')).encode()


def validate_observation(path: Path | None, manifest: dict) -> tuple[dict, dict]:
    """Certify one untouched-original observation; this is not candidate work proof."""
    certificate = manifest.get('activation_options')
    if certificate is None or path is None:
        raise ValueError('Candidate activation requires a pinned full original option observation; source gate is pending')
    raw = Path(path).read_bytes()
    observation = json.loads(raw)
    if not isinstance(observation, dict) or not isinstance(observation.get('loadedIrisOptions'), dict):
        raise ValueError('Expected an original capture receipt with a complete effective option map')
    values = observation['loadedIrisOptions']
    if any(not isinstance(k, str) or not isinstance(v, str) for k, v in values.items()):
        raise ValueError('Expected complete observed string-valued option map')
    for key, value in {'count': len(values), 'names_sha256': sha256(canonical(sorted(values))),
                       'values_sha256': sha256(canonical(values))}.items():
        if certificate.get(key) != value:
            raise ValueError('Uncertified or incomplete original option observation: ' + key)
    for key, expected in certificate['required_values'].items():
        if values.get(key) != expected:
            raise ValueError('Ineligible native option: ' + key)
    # Hash identity is also required below, but these checks make actual reasons
    # explicit and prevent a future refreshed pin from silently relaxing scope.
    for key, expected in certificate['required_capture_fields'].items():
        actual = observation.get(key)
        if type(actual) is not type(expected) or actual != expected:
            raise ValueError('Ineligible original capture field: ' + key)
    if sha256(raw) != certificate['receipt_sha256']:
        raise ValueError('Uncertified original capture receipt bytes')
    for source in certificate['runtime_evidence_inputs']:
        if sha256((ROOT / source['path']).read_bytes()) != source['sha256']:
            raise ValueError('Original runtime provenance changed: ' + source['path'])
    return values, {
        'path': str(Path(path).resolve()), 'sha256': sha256(raw),
        'count': len(values), 'names_sha256': certificate['names_sha256'],
        'values_sha256': certificate['values_sha256'],
        'scope': 'Prior original observation only; reobserve selected candidate, native options and runtime before acceptance',
    }


def build(base: Path, output: Path, options: Path, mode: str = 'native',
          manifest_path: Path = MANIFEST, iris: Path = IRIS,
          effective_options: Path | None = None) -> dict:
    base, output, options = Path(base), Path(output), Path(options)
    sidecar = Path(str(output) + '.txt')
    if output.resolve() in (base.resolve(), options.resolve()) or sidecar.resolve() in (base.resolve(), options.resolve()):
        raise ValueError('Cannot overwrite original inputs')
    if output.exists() or sidecar.exists():
        raise ValueError('Output already exists')
    manifest = json.loads(Path(manifest_path).read_text())
    if sha256(base.read_bytes()) != manifest['original_archive_sha256']:
        raise ValueError('Original archive SHA changed')
    iris_hash = sha256(Path(iris).read_bytes())
    if iris_hash != manifest['supported_runtime_source_boundary']['iris_jar_sha256']:
        raise ValueError('Uncertified Iris JAR')
    observed = observation_receipt = None
    if mode == 'candidate' or effective_options is not None:
        observed, observation_receipt = validate_observation(effective_options, manifest)
    native_options = options.read_bytes()
    validate_options(native_options)
    if observed is not None and sha256(native_options) != manifest['activation_options']['native_sidecar_sha256']:
        raise ValueError('Native sidecar differs from the original option observation')
    original = read_archive(base)
    result = apply(original, manifest, mode)
    modified = sorted(n for n in original if result[n] != original[n])
    if modified != sorted((MEMBER, PROPERTIES)):
        raise ValueError('Unexpected upstream changes')
    receipt = dict(schema=1, id=manifest['id'], mode=mode, default_mode='native',
        original_archive_sha256=manifest['original_archive_sha256'],
        original_members_digest=members_digest(original), patched_members_digest=members_digest(result),
        modified_upstream_members=modified, added_members=[INCLUDE],
        source_sha256=sha256(result[MEMBER]), shared_policy_sha256=sha256(result[INCLUDE]),
        supplied_iris_jar_sha256=iris_hash, supplied_effective_options=observed,
        original_observation_receipt=observation_receipt,
        native_options_sha256=sha256(native_options), guard=GUARD,
        candidate_native_samples=[9,12], transition_iterations=12,
        validation={k:False for k in ('driver_compilation','loaded_options','actual_work',
            'in_world_rendering','quality','performance','dh','pbr','other_loaders_versions')})
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'x', compression=zipfile.ZIP_DEFLATED) as z:
        for name, data in sorted(result.items()):
            entry = zipfile.ZipInfo(name, (1980,1,1,0,0,0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o644 << 16
            z.writestr(entry, data)
    with sidecar.open('xb') as f:
        f.write(native_options)
    return {**receipt, 'output':str(output.resolve()), 'output_sha256':sha256(output.read_bytes()),
            'manifest_sha256':sha256(Path(manifest_path).read_bytes())}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--options', required=True, type=Path)
    parser.add_argument('--mode', choices=MODES, default='native')
    parser.add_argument('--iris', type=Path, default=IRIS)
    parser.add_argument('--effective-options', type=Path,
                        help='Exact original capture receipt containing loadedIrisOptions; required for candidate')
    args = parser.parse_args()
    print(json.dumps(build(args.base, args.output, args.options, args.mode,
                           iris=args.iris, effective_options=args.effective_options), indent=2))
