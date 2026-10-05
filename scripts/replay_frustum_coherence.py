#!/usr/bin/env python3
"""Prepare explicit pinned-helper variants and replay one captured BASE frustum's raw queries."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / 'src/main/java/com/pp/shaderlod/ShadowFrustumCoherence.java'
DIAGNOSTICS = ROOT / 'src/main/java/com/pp/shaderlod/ShadowFrustumCoherenceDiagnostics.java'
VARIANTS = [(grid, grid + 32, slots) for grid in (64, 32, 16) for slots in (64, 256, 512)]
ANCHORS = (
    '    private static final int SLOTS = 64;',
    '        float lowX = x * 64.0f, lowY = y * 64.0f, lowZ = z * 64.0f;',
    '        float highX = lowX + 96.0f, highY = lowY + 96.0f, highZ = lowZ + 96.0f;',
    '        float scaled = value * (1.0f / 64.0f);',
)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def raw_bits(value, width):
    if type(value) is not int or not -(1 << (width - 1)) <= value < (1 << width):
        raise ValueError(f'Expected lossless signed/unsigned raw int{width}, got {value!r}')
    return value & ((1 << width) - 1)


def required_bool(data, name):
    value = data.get(name)
    if type(value) is not bool:
        raise ValueError(f'{name} must be an explicit boolean')
    return value


def required_integer(data, name, minimum=0):
    value = data.get(name)
    if type(value) is not int or value < minimum:
        raise ValueError(f'{name} must be an integer >= {minimum}')
    return value


def float_kind(bits, width):
    value = struct.unpack('>f' if width == 32 else '>d', bits.to_bytes(width // 8, 'big'))[0]
    return 'nan' if math.isnan(value) else 'positive_infinity' if value == math.inf else 'negative_infinity' if value == -math.inf else 'finite'


def validate_trace(report, source_sha):
    trace = report.get('shadow_coherence_oracle', {}).get('replayTrace') if 'shadow_coherence_oracle' in report else report
    if not isinstance(trace, dict):
        raise ValueError('Missing replayTrace object')
    if trace.get('kind') != 'BASE':
        raise ValueError('Only BASE captures supported; reversed alternate-box context requires a separate schema')
    if trace.get('frustumClass') != 'net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum':
        raise ValueError('Only the exact pinned AdvancedShadowCullingFrustum class is supported')
    if not required_bool(trace, 'contextAvailable'):
        raise ValueError('Captured helper context is unavailable')
    context = trace.get('context')
    if not isinstance(context, dict) or not required_bool(context, 'available'):
        raise ValueError('Unknown/unavailable replay context')
    if not required_bool(context, 'ownerAssociationVerified'):
        raise ValueError('Helper/frustum owner association was not verified')
    if context.get('helperSourceSha256') != source_sha:
        raise ValueError('Captured helper source SHA256 does not match the actual input source')
    if not re.fullmatch(r'[0-9a-f]{64}', source_sha):
        raise ValueError('Invalid helper source SHA256')
    frame = required_integer(trace, 'renderFrame')
    generation = required_integer(trace, 'prepareOrPlaneGeneration')
    identity = required_integer(trace, 'frustumIdentity', -(1 << 31))
    if context.get('frustumIdentity') != identity:
        raise ValueError('Context/trace frustum identities differ')
    count = required_integer(trace, 'planeCount')
    if count > 13 or context.get('planeCount') != count:
        raise ValueError('Unsupported/inconsistent pinned plane count')
    planes = context.get('rawPlaneBits')
    if not isinstance(planes, list) or len(planes) != count:
        raise ValueError('rawPlaneBits must contain exactly planeCount active rows')
    normalized_planes = []
    for row in planes:
        if not isinstance(row, list) or len(row) != 4:
            raise ValueError('Every active plane requires four raw coefficients')
        normalized_planes.append([raw_bits(value, 32) for value in row])
    planes = normalized_planes
    fma = required_bool(context, 'fma')
    limit = raw_bits(context.get('rawDistanceLimitBits'), 64)
    kind = float_kind(limit, 64)
    if context.get('distanceLimitKind') != kind:
        raise ValueError('Distance-limit kind disagrees with raw bits')
    hexadecimal = context.get('distanceLimitBitsHex')
    if hexadecimal is not None and (not isinstance(hexadecimal, str) or int(hexadecimal.removeprefix('0x'), 16) != limit):
        raise ValueError('Distance-limit hexadecimal/raw fields disagree')
    queries = trace.get('queries')
    if not isinstance(queries, list) or not queries or len(queries) > 8192:
        raise ValueError('Trace needs 1..8192 ordered queries')
    if required_integer(trace, 'recordedQueries') != len(queries):
        raise ValueError('Recorded query count differs from rows')
    total = required_integer(trace, 'totalObservedQueries')
    if total < len(queries):
        raise ValueError('Observed query count cannot be smaller than recorded prefix')
    ended = required_bool(trace, 'sequenceEnded')
    boundary = required_bool(trace, 'sequenceBoundaryObserved')
    cache_state = required_bool(trace, 'proofCacheStateCaptured')
    if cache_state:
        raise ValueError('Captured proof-cache state is unsupported; this tool explicitly starts cold')
    truncated = required_bool(trace, 'truncated')
    complete = required_bool(trace, 'complete')
    if complete != (ended and boundary and not truncated and total == len(queries)):
        raise ValueError('Inconsistent complete/prefix provenance')
    if not truncated and total > len(queries):
        raise ValueError('Dropped rows must be marked truncated')
    unavailable = trace.get('originalUnavailable')
    if type(unavailable) is not bool:
        unavailable = required_integer(trace, 'originalUnavailable')
    if unavailable:
        raise ValueError('Captured original classifier had unavailable queries')
    if required_bool(trace, 'revisitedAfterClose'):
        raise ValueError('Closed capture scope was revisited; cannot assume a single uninterrupted sequence')
    normalized = []
    for index, query in enumerate(queries):
        if not isinstance(query, dict) or query.get('index') != index:
            raise ValueError('Query indices must be ordered and contiguous from zero')
        bounds = query.get('rawBoundsBits')
        if not isinstance(bounds, list) or len(bounds) != 6:
            raise ValueError('Every query requires six raw endpoint bits')
        normalized.append(([raw_bits(value, 32) for value in bounds],
                           required_bool(query, 'original'), required_bool(query, 'actual')))
    return {'trace': trace, 'planes': planes, 'count': count, 'fma': fma, 'limit': limit, 'queries': normalized,
            'provenance': {'frame': frame, 'generation': generation, 'frustum_identity': identity,
                           'scope': 'complete first same-frame/frustum sequence' if complete else 'partial recorded prefix only',
                           'complete': complete, 'truncated': truncated, 'sequence_ended': ended,
                           'sequence_boundary_observed': boundary, 'proof_cache_state_captured': cache_state,
                           'replay_cache_initialization': 'cold empty helper; runtime pre-capture state unavailable',
                           'recorded': len(queries), 'total_observed': total, 'end_reason': trace.get('endReason'),
                           'captured_runtime_mismatches': sum(actual != original for _, original, actual in normalized),
                           'distance_kind': kind,
                           'nonfinite_plane_coefficients': sum(float_kind(value, 32) != 'finite' for row in planes for value in row),
                           'nonfinite_query_endpoints': sum(float_kind(value, 32) != 'finite' for row, _, _ in normalized for value in row)}}


def variant_source(source, grid, extent, slots):
    if (grid, extent, slots) not in VARIANTS:
        raise ValueError('Variant is outside the explicit bounded matrix')
    replacements = (
        f'    private static final int SLOTS = {slots};',
        f'        float lowX = x * {grid}.0f, lowY = y * {grid}.0f, lowZ = z * {grid}.0f;',
        f'        float highX = lowX + {extent}.0f, highY = lowY + {extent}.0f, highZ = lowZ + {extent}.0f;',
        f'        float scaled = value * (1.0f / {grid}.0f);',
    )
    changed = []
    for before, after in zip(ANCHORS, replacements):
        if source.count(before) != 1:
            raise ValueError(f'Pinned explicit anchor changed or duplicated: {before}')
        source = source.replace(before, after, 1)
        if before != after:
            changed.append({'before': before, 'after': after})
    return source, changed


def write_binary(data, path):
    with path.open('wb') as output:
        output.write(b'FRCO1')
        output.write(struct.pack('>I?Q', data['count'], data['fma'], data['limit']))
        for row in data['planes']:
            output.write(struct.pack('>IIII', *row))
        output.write(struct.pack('>I', len(data['queries'])))
        for bounds, original, actual in data['queries']:
            output.write(struct.pack('>IIIIII??', *bounds, original, actual))


JAVA_RUNNER = r'''package com.pp.shaderlod;
import java.io.*;
import java.nio.file.*;
public final class CoherenceReplay {
  static float[][] planes, boxes;
  static boolean fma;
  static double limit;
  static boolean[] expected, captured;
  static volatile long sink;
  static float safe(float a,float b,float c) { return a*b+c; }
  static boolean original(float[] b) {
    if(b[3]<-limit || b[0]>limit || b[4]<-limit || b[1]>limit || b[5]<-limit || b[2]>limit) return false;
    return originalCorners(b)>0;
  }
  static int originalCorners(float[] b) {
    boolean inside=true;
    for(float[] p:planes) {
      float x=p[0]<0?b[0]:b[3], y=p[1]<0?b[1]:b[4], z=p[2]<0?b[2]:b[5];
      if(fma) {
        if(Math.fma(p[0],x,Math.fma(p[1],y,p[2]*z))>=-p[3])
          inside &= Math.fma(p[0],p[0]<0?b[3]:b[0],Math.fma(p[1],p[1]<0?b[4]:b[1],Math.fma(p[2],p[2]<0?b[5]:b[2],p[3])))>=0;
        else return 0;
      } else {
        if(safe(p[0],x,safe(p[1],y,p[2]*z))>=-p[3])
          inside &= safe(p[0],p[0]<0?b[3]:b[0],safe(p[1],p[1]<0?b[4]:b[1],safe(p[2],p[2]<0?b[5]:b[2],p[3])))>=0;
        else return 0;
      }
    }
    return inside?1:2;
  }
  static boolean candidate(ShadowFrustumCoherence helper,float[] b) {
    return helper.canAccept(b[0],b[1],b[2],b[3],b[4],b[5]) || original(b);
  }
  static void read(String name) throws Exception {
    try(var in=new DataInputStream(Files.newInputStream(Path.of(name)))) {
      if(!new String(in.readNBytes(5),java.nio.charset.StandardCharsets.US_ASCII).equals("FRCO1")) throw new IOException("Wrong raw trace format");
      int count=in.readInt(); fma=in.readBoolean(); limit=Double.longBitsToDouble(in.readLong());
      planes=new float[count][4]; for(float[] p:planes) for(int i=0;i<4;i++) p[i]=Float.intBitsToFloat(in.readInt());
      int countQueries=in.readInt(); boxes=new float[countQueries][6]; expected=new boolean[countQueries]; captured=new boolean[countQueries];
      for(int q=0;q<countQueries;q++) { for(int i=0;i<6;i++) boxes[q][i]=Float.intBitsToFloat(in.readInt()); expected[q]=in.readBoolean(); captured[q]=in.readBoolean(); }
      if(in.read()!=-1) throw new IOException("Trailing raw trace data");
    }
  }
static long loop(boolean adaptive) {
    // Include a new frame-local helper and its bounded arrays, as the engine commonly creates fresh frusta.
    ShadowFrustumCoherence helper=adaptive?new ShadowFrustumCoherence(planes,planes.length,fma,limit):null;
    long result=0;
    for(float[] b:boxes) {
      boolean visible=adaptive?candidate(helper,b):original(b);
      result=result*31+(visible?1:0);
    }
    return result;
  }
  public static void main(String[] args) throws Exception {
    read(args[0]);
    var helper=new ShadowFrustumCoherence(planes,planes.length,fma,limit);
    String mode=args[1];
    boolean counters=mode.equals("counts");
    if(ShadowFrustumCoherenceDiagnostics.ENABLED!=counters) throw new IllegalStateException("Wrong final diagnostic gate");
    if(counters) ShadowFrustumCoherenceDiagnostics.beginMeasurement();
    int mismatch=0, oracleMismatch=0, accepted=0, rejected=0, distanceRejected=0;
    for(int i=0;i<boxes.length;i++) {
      boolean baseline=original(boxes[i]); if(baseline!=expected[i]) oracleMismatch++;
      if(!expected[i]) rejected++;
      float[] b=boxes[i];
      if(b[3]<-limit || b[0]>limit || b[4]<-limit || b[1]>limit || b[5]<-limit || b[2]>limit) distanceRejected++;
      boolean fast=helper.canAccept(boxes[i][0],boxes[i][1],boxes[i][2],boxes[i][3],boxes[i][4],boxes[i][5]);
      boolean actual=fast || baseline; if(actual!=expected[i]) mismatch++; if(fast) accepted++;
    }
    if(counters) {
      var s=ShadowFrustumCoherenceDiagnostics.snapshot();
      System.out.println("{\"queries\":"+boxes.length+",\"mismatches\":"+mismatch+",\"reference_replay_mismatches\":"+oracleMismatch+",\"selected_accepts\":"+accepted
        +",\"cached_accepts\":"+s.cachedProofAccepts()+",\"cached_fallbacks\":"+s.cachedProofFallbacks()+",\"new_accepts\":"+s.newProofAccepts()+",\"new_fallbacks\":"+s.newProofFallbacks()
        +",\"invalid_aabbs\":"+s.invalidAabbFallbacks()+",\"outside_coarse_boxes\":"+s.coarseContainmentFallbacks()
        +",\"reference_rejects\":"+rejected+",\"reference_distance_rejects\":"+distanceRejected+",\"reference_plane_rejects\":"+(rejected-distanceRejected)+"}");
    } else {
      if(mismatch!=0 || oracleMismatch!=0) throw new IllegalStateException("Timing prohibited after correctness mismatch");
      int warm=Integer.parseInt(args[2]), repetitions=Integer.parseInt(args[3]);
      for(int i=0;i<warm;i++) { sink=loop(false); sink=loop(true); }
      long[] base=new long[repetitions], adaptive=new long[repetitions];
      for(int i=0;i<repetitions;i++) {
        if((i&1)==0) { long t=System.nanoTime();sink=loop(false);base[i]=System.nanoTime()-t;t=System.nanoTime();sink=loop(true);adaptive[i]=System.nanoTime()-t; }
        else { long t=System.nanoTime();sink=loop(true);adaptive[i]=System.nanoTime()-t;t=System.nanoTime();sink=loop(false);base[i]=System.nanoTime()-t; }
      }
      System.out.println("{\"baseline_trace_ns\":"+java.util.Arrays.toString(base)+",\"candidate_trace_ns\":"+java.util.Arrays.toString(adaptive)+",\"counter_gate\":false,\"helper_allocation_included\":true,\"checksum\":"+sink+"}");
    }
    if(mismatch!=0 || oracleMismatch!=0) System.exit(2);
  }
}'''


def prepare(trace_path, destination, source_path=HELPER, diagnostics_path=DIAGNOSTICS):
    source_bytes = source_path.read_bytes()
    diagnostics = diagnostics_path.read_bytes()
    data = validate_trace(json.loads(trace_path.read_text()), digest(source_bytes))
    if destination.exists():
        raise ValueError(f'Refusing to replace existing replay output: {destination}')
    variant_inputs = []
    for grid, extent, slots in VARIANTS:
        text, changes = variant_source(source_bytes.decode(), grid, extent, slots)
        variant_inputs.append((grid, extent, slots, text, changes))
    destination.mkdir(parents=True)
    write_binary(data, destination / 'trace.bin')
    manifest = {'schema': 1, 'trace_path': str(trace_path.resolve()), 'trace_sha256': digest(trace_path.read_bytes()),
                'helper_source_sha256': digest(source_bytes), 'diagnostics_source_sha256': digest(diagnostics),
                'runner_source_sha256': digest(JAVA_RUNNER.encode()), 'provenance': data['provenance'],
                'execution': 'prepared only; no Java compiled or executed', 'variants': []}
    for grid, extent, slots, text, changes in variant_inputs:
        name = f'grid{grid}-extent{extent}-slots{slots}'
        src = destination / name / 'src/com/pp/shaderlod'
        src.mkdir(parents=True)
        (src / 'ShadowFrustumCoherence.java').write_text(text)
        (src / 'ShadowFrustumCoherenceDiagnostics.java').write_bytes(diagnostics)
        (src / 'CoherenceReplay.java').write_text(JAVA_RUNNER)
        manifest['variants'].append({'name': name, 'grid': grid, 'extent': extent, 'slots': slots,
                                     'source_sha256': digest(text.encode()), 'anchor_changes': changes})
    (destination / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    return manifest


def java_tools(java_home=None):
    if java_home:
        directory = Path(java_home) / 'bin'
    elif sys.platform == 'darwin':
        directory = Path(subprocess.check_output(['/usr/libexec/java_home', '-v', '21'], text=True).strip()) / 'bin'
    else:
        directory = Path(shutil.which('java') or '').parent
    java, javac = directory / 'java', directory / 'javac'
    if not java.is_file() or not javac.is_file():
        raise ValueError('A Java 21 JDK with java and javac is required')
    version = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True).stderr
    if not re.search(r'version "21(?:\.|\")', version):
        raise ValueError('Replay must use Java 21, matching the pinned runtime')
    return java, javac, version.strip()


def execute(destination, manifest, java_home=None, timing=False, warm=300, repeats=30):
    java, javac, version = java_tools(java_home)
    manifest['java_version'] = version
    failures = 0
    for variant in manifest['variants']:
        root = destination / variant['name']
        classes = root / 'classes'
        classes.mkdir()
        sources = sorted((root / 'src').rglob('*.java'))
        compilation = subprocess.run([str(javac), '--release', '21', '-d', str(classes), *map(str, sources)], capture_output=True, text=True)
        (root / 'compile.stdout').write_text(compilation.stdout)
        (root / 'compile.stderr').write_text(compilation.stderr)
        if compilation.returncode:
            raise RuntimeError(f"Compiler failed for {variant['name']}: {compilation.stderr}")
        command = [str(java), '-Dshaderlod.dev.shadowCoherenceOracle=true', '-cp', str(classes),
                   'com.pp.shaderlod.CoherenceReplay', str(destination / 'trace.bin'), 'counts']
        result = subprocess.run(command, capture_output=True, text=True, timeout=30)
        (root / 'counts.stdout').write_text(result.stdout)
        (root / 'counts.stderr').write_text(result.stderr)
        if result.returncode not in (0, 2):
            raise RuntimeError(f"Replay failed for {variant['name']}: {result.stderr}")
        counts = json.loads(result.stdout)
        variant['counts'] = counts
        manifest['execution'] = 'Java replay in progress'
        (destination / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        failures += counts['mismatches'] + counts['reference_replay_mismatches']
        if timing and not counts['mismatches'] and not counts['reference_replay_mismatches']:
            timing_command = [str(java), '-Dshaderlod.dev.shadowCoherenceOracle=false', '-cp', str(classes),
                              'com.pp.shaderlod.CoherenceReplay', str(destination / 'trace.bin'), 'timing', str(warm), str(repeats)]
            measured = subprocess.run(timing_command, capture_output=True, text=True, timeout=30, check=True)
            (root / 'timing.stdout').write_text(measured.stdout)
            (root / 'timing.stderr').write_text(measured.stderr)
            variant['diagnostic_cpu_timing'] = json.loads(measured.stdout)
    manifest['execution'] = 'Java 21 raw-query replay completed; no Minecraft/frame performance claim'
    manifest['correctness_failures'] = failures
    manifest['correctness_failures'] += manifest['provenance']['captured_runtime_mismatches']
    (destination / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    return manifest['correctness_failures']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('trace', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--source', type=Path, default=HELPER)
    parser.add_argument('--diagnostics-source', type=Path, default=DIAGNOSTICS)
    parser.add_argument('--execute', action='store_true', help='Compile/replay only after the coordinator releases a CPU window.')
    parser.add_argument('--timing', action='store_true', help='Separate no-counter JVM diagnostic CPU model; requires --execute.')
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--warmup-iterations', type=int, default=300)
    parser.add_argument('--timing-repeats', type=int, default=30)
    args = parser.parse_args()
    output = args.output.resolve()
    if not any(output.is_relative_to(ROOT / directory) for directory in ('build', 'dev/logs')):
        raise ValueError('Generated replay output must live under ignored build/ or dev/logs/')
    if args.timing and not args.execute:
        raise ValueError('--timing requires explicit --execute')
    if not 1 <= args.warmup_iterations <= 1000 or not 1 <= args.timing_repeats <= 100:
        raise ValueError('Timing warmup/repetitions exceed bounded diagnostic limits')
    manifest = prepare(args.trace, output, args.source, args.diagnostics_source)
    failed = execute(output, manifest, args.java_home, args.timing, args.warmup_iterations, args.timing_repeats) if args.execute else 0
    print(json.dumps({'manifest': str(output / 'manifest.json'), 'execution': manifest['execution'],
                      'correctness_failures': failed, 'scope': manifest['provenance']['scope']}, indent=2))
    return 2 if failed else 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as failure:
        print(f'Frustum replay failed: {failure}', file=sys.stderr)
        sys.exit(1)
