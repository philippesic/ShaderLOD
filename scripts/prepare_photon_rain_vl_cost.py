#!/usr/bin/env python3
"""Source-ready rain/VL ceiling recipe only. Never discover, copy, build or launch."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import sys
import zipfile

import prepare_photon_fxaa_cost as cost

ROOT = cost.ROOT
OUTPUT = ROOT / 'run-workloads/rain-vl-cost-r1'
BUILD = ROOT / 'build/rain-vl-cost-r1'
ORDER = ('vl-before', 'vl-off', 'vl-after')
MODES = ('true', 'false', 'true')
NATIVE_HASH = cost.canonical.SOURCE_OPTIONS_SHA256
JAR_SHA256 = 'e86b77eb478c5bbcae9f91fb14cf1712eb9b9fcb93d760a104eeb8245072b864'
NATIVE_INVENTORY = ROOT / 'build/fxaa-cost-r1/option-inventory.json'
COAST = dict(x=500.5,y=68.0,z=660.5,yaw=87.38,pitch=3.735,time=12500)
OFF = cost.OFF
EVIDENCE = dict(performanceEvidence=False,adapter_gain=False,quality_equivalent=False,
    diagnostic_only=True,runtime='unverified',shader_support_claim=False,
    evidence_class='original-native-rain-VL-maximum-removal-ceiling-calibration')


def require(condition, message):
    if not condition: raise ValueError(message)


def option_maps(native):
    require(type(native) is dict and len(native)==366 and cost.matrix.canonical_options_hash(native)==NATIVE_HASH,
        'Complete canonical original 366-map differs')
    fixed = dict(TAA='true',TAAU='false',TAAU_RENDER_SCALE='0.75',FXAA='true',CAS='true',CAS_INTENSITY='0.50',
        VL='true',VL_RENDER_SCALE='0.50',OVERWORLD_FOG_INTENSITY='1.00',AIR_FOG_CLOUDY_NOISE='true',
        AIR_FOG_COLORED_LIGHT_SHAFTS='false',AIR_FOG_MIE_DENSITY_RAIN='0.030')
    require(all(native.get(k)==v for k,v in fixed.items()),'Native temporal/fog/effect controls differ')
    maps = {arm: native | {'VL':mode} for arm,mode in zip(ORDER,MODES)}
    require([k for k in native if native[k]!=maps['vl-off'][k]]==['VL'],'Only VL may differ')
    return maps


def rain_control(canonical):
    require(type(canonical) is dict and all(canonical.get(k)==v for k,v in COAST.items()),'Canonical coastal pose/day differs')
    require(canonical.get('weather')=='clear','Canonical weather differs')
    return canonical | dict(weather='rain',enabled=True)


def command(profile):
    argv = cost.command(profile)
    require(argv.count('-PfixedWetness=0')==1,'Inherited wetness command changed')
    argv[argv.index('-PfixedWetness=0')] = '-PfixedWetness=1'
    return argv


def plan(output=OUTPUT, build=BUILD, root=ROOT):
    root = Path(root).resolve()
    require(not Path(output).is_symlink() and not Path(build).is_symlink(),'Symlink owned output unsupported')
    output,build = Path(output).resolve(),Path(build).resolve()
    require(output==root/'run-workloads/rain-vl-cost-r1' and build==root/'build/rain-vl-cost-r1','Only owned families allowed')
    require(not output.exists(),'Preserve previous output')
    return EVIDENCE | dict(schema=1,status='SOURCE_RECIPE_ONLY_PROFILES_NOT_ASSEMBLED',output=str(output),build=str(build),
        scene='coastalwater',arms=list(ORDER),vl_values=list(MODES),profile_count=3,weather='rain',fixed_wetness=1,
        initial_warmup_seconds=60,measurement_seconds=20,gpu_sample_interval=5,minimum_gpu_samples=60,
        framebuffer=[2976,1672],window=[1488,836],requested_chunks=24,
        coarse_cpu_scopes=['game-render','render-and-blit','run-tick','swap-and-events'],
        minimum_each_control_fps_gain=.05,maximum_each_control_drift=.10,
        client_gpu_owner='root',owned_live_processes=[],launch_authorized=False,
        assembly_hold='No actual Iris discovery/runtime inventory/profile copy before root releases quiet-window hold. Recipe is not launchable.',
        runtime_receipt_required='Explicit independently qualified current R10 receipt path and SHA-256',
        production_jar_required=dict(path=str(ROOT/'build/libs/shaderlod-0.1.0-dev.jar'),sha256=JAR_SHA256),
        denominator='Original Photon at native resolution in the same controlled rain scene; not original default-weather or mod gain',
        stop_procedure=cost.canonical.STOP)


def source_gate():
    archive = ROOT/'dependencies/upstream/photon_v1.3b.zip'
    require(cost.file_hash(archive)==cost.canonical.ORIGINAL_SHA256,'Pinned Photon archive differs')
    names = ['shaders/include/weather/fog.glsl','shaders/include/weather/core.glsl',
        'shaders/include/fog/overworld/raymarched.glsl','shaders/include/fog/overworld/constants.glsl',
        'shaders/settings.glsl','shaders/program/c0_vl.fsh']
    with zipfile.ZipFile(archive) as z:
        members = {n:hashlib.sha256(z.read(n)).hexdigest() for n in names}
        fog=z.read(names[0]).decode(); weather=z.read(names[1]).decode(); ray=z.read(names[2]).decode()
    require('rainStrength * biome_may_rain' in fog and 'AIR_FOG_MIE_DENSITY_RAIN' in fog,'Rain coefficient interpolation source changed')
    require('humidity += wetness;' in weather and 'return clamp01(humidity);' in weather,'Wetness/humidity source changed')
    require('density.y *= 4.0 * sqr(noise);' in ray and 'transmittance *= step_transmittance;' in ray,'Density/integration source changed')
    return dict(status='CONDITIONAL_COST_CALIBRATION_ONLY',archive_sha256=cost.canonical.ORIGINAL_SHA256,
        member_sha256=members,chosen_pose=COAST,
        mie_height_envelope_ratio_coast_vs_forest=2**((95-70)/7),
        coefficient_reason='Settled rainStrength=1 makes the source interpolate to .030 Mie extinction only where biome_may_rain=1. Wetness1 saturates humidity; no shader effect option changed.',
        unverified=['actual biome_may_rain','actual flat fog coefficients/palette','ray/noise distributions','current whole-VL cost','actual shader rainStrength uniform consumption'],
        existing_cost_clue='Historical coastal composite1.095ms is instrumented/unmatched; it only permits conditional maximum-removal calibration, not a current budget or predicted >5% gain.',
        implementation_authorized=False)


def source_recipe():
    operation=plan(); gate=source_gate()
    inventory=json.loads(NATIVE_INVENTORY.read_text())
    observed=inventory['coastalwater']['original']; native=observed['options']
    require(observed['option_count']==366 and observed['sha256']==NATIVE_HASH,'Retained actual Iris native inventory differs')
    maps=option_maps(native)
    control_path=cost.canonical.SOURCE.parent/'control.json'
    original=json.loads(control_path.read_text()); control=rain_control(original)
    recipes=[]
    for arm in ORDER:
        profile=OUTPUT/arm/'control'
        request=EVIDENCE | dict(run_id=OUTPUT.name+'-'+arm,case='coastal-rain-'+arm,task='runClientLodOn',
            stack='minimal',include_dh=False,initial_warmup_seconds=60,measurement_seconds=20,
            gpu_queries=True,cpu_timing=True,sample_interval=5,fixed_wetness=1,hide_workload_gui=True,
            expected_framebuffer=[2976,1672],expected_window=[1488,836])
        recipes.append(dict(arm=arm,profile=str(profile),control=control,request=request,
            expected_effective_options=maps[arm],expected_effective_options_sha256=cost.matrix.canonical_options_hash(maps[arm]),
            command=command(profile),status='recipe-only-profile-absent'))
    paths=[Path(__file__).resolve(),ROOT/'tests/test_photon_rain_vl_cost.py']
    for module in tuple(sys.modules.values()):
        name=getattr(module,'__file__',None)
        if name:
            path=Path(name).resolve()
            if path.suffix=='.py' and path.is_relative_to(ROOT/'scripts'): paths.append(path)
    return EVIDENCE | dict(operation=operation,source_gate=gate,recipes=recipes,
        recipe_source_files_sha256={str(p):cost.file_hash(p) for p in sorted(set(paths))},
        pinned_existing_native_inventory=dict(path=str(NATIVE_INVENTORY),sha256=cost.file_hash(NATIVE_INVENTORY)),
        canonical_control_source=dict(path=str(control_path),sha256=cost.file_hash(control_path)),
        canonical_source_world_sha256=cost.quality.WORLD_SHA256['coastalwater'],
        postanalysis=postanalysis_plan())


def qualify_rain(report, request):
    require(type(request.get('fixed_wetness')) in (int,float) and request['fixed_wetness']==1,'Rain wetness target must be one')
    cost.matrix.validate_temporal_inputs(report,request)
    for boundary in ('input_state_start','input_state_end'):
        state=report.get(boundary,{})
        require(type(state.get('rain')) in (int,float) and math.isfinite(state['rain']) and abs(state['rain']-1)<1e-6,'Settled rain missing')
        require(type(state.get('thunder')) in (int,float) and abs(state['thunder'])<1e-6,'Rain-only thunder differs')
    for boundary in ('shader_temporal_inputs_start','shader_temporal_inputs_end'):
        state=report.get(boundary,{})
        require(type(state.get('rain_strength_source')) in (int,float) and math.isfinite(state['rain_strength_source'])
            and abs(state['rain_strength_source']-1)<1e-6,'Observed rainStrength CPU source differs')
    return dict(rain_source=1,fixed_applied_wetness=1,shader_uniform_readback=False,fog_coefficients_readback=False)


def screen(frames, gpu):
    require(len(frames)==len(gpu)==3 and all(type(v) in (int,float) and math.isfinite(v) and v>0 for v in (*frames,*gpu)),
        'Three positive finite frame/GPU values required')
    drift=dict(frame=abs(frames[2]/frames[0]-1),gpu=abs(gpu[2]/gpu[0]-1))
    gains=[frames[i]/frames[1]-1 for i in (0,2)]; reductions=[1-gpu[1]/gpu[i] for i in (0,2)]
    verdict=('inconclusive-control-drift' if max(drift.values())>.10+1e-12 else
        'stop-no-useful-ceiling' if min(gains)<=.05+1e-12 or min(reductions)<=0 else 'useful-maximum-removal-calibration-only')
    return EVIDENCE | dict(verdict=verdict,control_drift=drift,fps_gain_vs_each_vl=gains,gpu_reduction_vs_each_vl=reductions,
        optical_tail_implementation_accepted=False)


def postanalysis_plan():
    return dict(assembly=[
        'After root release ONLY: qualify canonical archive/source/world/current explicit R10 receipt/main e86, direct mod roots and all compiled directory digests; freeze exact recipe/source dependencies.',
        'Discover complete original366 VLtrue/VLfalse maps through pinned Iris. Only VL differs. Do not substitute retained/source-map prediction for fresh discovery.',
        'Copy canonical profile/world into three fresh owned directories under world lock; no weather or save edits to canonical source. All copied initial world hashes equal pinned original.',
        'Apply weather rain only through new parent control.json consumed by ComparisonClient; preserve nativeTAA/FXAA/CAS/all other effect options, main globaltrue/all seven INHERIT/unity, noDH/noVoxy.',
        'Freeze complete raw settings/options/sidecars/controls/requests/world/artifacts and actual argv. Keep startup-normalized configuration allowances source-specific and full-map strict.'],
        per_arm=[
        'Root alone reserves client/GPU and proves exact launcher/Java birth/cwd/VM args plus terminal absence. No unrelated CPU/tools during quiet60warm20capture.',
        'Require complete actual Workload schema/request UUID/time/source531members/options366/disk configuration/current R10 direct artifacts/native focus/window/GUI/pacing/7effect semantics/input guards.',
        'At capture boundaries require observed rain=1,thunder=0, shader temporal rain_strength_source=1 and fixed wetness1 applied to every active wetness supplier/ranges; no GL rainStrength or fog-palette coefficient readback claim.',
        'Actual60warm20capture/full-game-render N5 >=60 usable positive epoch rows, FOUR coarse CPU scopes/wall coverage, all probes/pass/attribution/history/trace/culling flagsOFF. No reader gate weakening or fake rows.',
        'Matrices/fogcoefficients/actual eligible cells unavailable. Retain final PNG/hash as quality-changing maximum-removal result, never quality acceptance.'],
        cross_arm=[
        'Complete world/control/weather/time/camera/options/config/artifacts/hardware equality except VLtrue/false; exact typed hardware payloads and timestamp prefixes handled source-explicitly with raw original failures preserved.',
        'VLtrue-before/after frame and GPU drift <=10%; VLfalse >5% higher FPS and positive GPU reduction versus BOTH controls. Flat/adverse/inconclusive stops optical-tail implementation.',
        'Passing ceiling only earns separate bounded actual eligibility diagnostic; not mod gain, default-weather speed, distant-cue quality or optical-tail implementation acceptance.'])


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-recipe',action='store_true',help='Print pinned source recipe; no output creation or profile assembly')
    args=parser.parse_args()
    result=source_recipe() if args.source_recipe else plan()
    print(json.dumps(result,indent=2))


if __name__=='__main__': main()
