#!/usr/bin/env python3
"""Finite local benchmark caller: shared clocks/analysis, canonical supervision, exact restoration."""
from __future__ import annotations
import argparse,json,hashlib,os,subprocess,sys,time,math,shutil
import benchmark_workflow as workflow
import benchmark_gl_context as gl_context
import benchmark_performance_stack as performance_stack
from contextlib import ExitStack
import experiment_lifecycle as life
import run_storage as storage
from pathlib import Path
from datetime import datetime,timezone,timedelta
from types import SimpleNamespace
import dev_client as dev
import benchmark_iteration as iteration
import benchmark_native_mips as native_mips
import benchmark_c01_cost as c01_cost
import benchmark_fixed16 as fixed16
import benchmark_g08_probe as g08_probe
import benchmark_bloom_vrs as bloom_vrs
import benchmark_makeup_cloud_bound as cloud_bound
import benchmark_photon_native_mips as photon_native_mips
import benchmark_mip_family as mip_family
import benchmark_mip_contrast as mip_contrast
import benchmark_c01_scene as c01_scene
import benchmark_c01_witness as c01_witness
import benchmark_mip_family_assessment as mip_assessment
import benchmark_nether_scene as nether_scene
import benchmark_copy_inventory as copy_inventory
import benchmark_native_control as native_control
import benchmark_bliss_exact_ao as bliss_ao
import benchmark_bliss_qualification as bliss_qualification
import benchmark_bliss_dead_copy as bliss_copy
import benchmark_bliss_zero_emission as bliss_zero
import benchmark_bliss_zero_lean as bliss_zero_lean
import benchmark_photon_dead_copies as photon_copy
import benchmark_bliss_unused_rt12 as bliss_unused
import benchmark_cpu_cost as cpu_cost
import benchmark_solas_lean_alpha as solas_alpha
import benchmark_sildur_lean_alpha as sildur_alpha
import benchmark_sildur_underwater_ao as sildur_ao
import benchmark_photon_pbr_resources as photon_pbr
ROOT=Path(__file__).resolve().parents[1]

def host_observation():
 telemetry={}
 for path in [*Path('/sys/class/thermal').glob('thermal_zone*/temp'),*Path('/sys/class/thermal').glob('thermal_zone*/type'),*Path('/sys/devices/system/cpu/cpufreq').glob('policy*/scaling_cur_freq')]:
  try:telemetry[str(path)]=path.read_text().strip()
  except OSError:pass
 if os.name == 'nt':
  from benchmark_windows_telemetry import gpu_observation
  telemetry['nvidia']=gpu_observation()
 return telemetry

# Shared private prototype lifecycle: candidate-specific validators stay in their modules.
# Module, report key and settled-observation requirement; membership also selects shared staging/observation hooks.
PRIVATE_CANDIDATES = {
 'native-control': (native_control, 'native_control_observations', False),
 'n04-bliss-sky': (bliss_ao, 'bliss_ao_observations', True),
 'n03-bliss-fog-copy': (bliss_copy, 'bliss_copy_observations', False),
 'n04-bliss-zero': (bliss_zero, 'bliss_zero_observations', False),
 'n03-photon-cloud-copy': (photon_copy, 'photon_copy_observations', False),
 'n02-bliss-unused-rt12': (bliss_unused, 'bliss_unused_observations', False),
 'n04-solas-lean-alpha': (solas_alpha, 'solas_alpha_observations', True),
 'n04-sildur-underwater-ao': (sildur_ao, 'sildur_ao_observations', True),
 'n02-photon-pbr-resources': (photon_pbr, 'photon_pbr_observations', False),
 'n04-sildur-lean-alpha': (sildur_alpha, 'sildur_alpha_observations', True),
}

def private_consumer(a):
 if a.candidate=='n04-bliss-zero' and getattr(a,'bliss_zero_lean',False):return bliss_zero_lean
 return PRIVATE_CANDIDATES[a.candidate][0] if a.candidate in PRIVATE_CANDIDATES else None


def verify_requested_dh(manifest, installed, mode):
 expected='ABSENT' if not installed else 'ENABLED' if mode=='DEFAULT' else 'INSTALLED_DISABLED'
 cases=manifest.get('windows',[])
 if not cases or any((w.get('configuration') or {}).get('dhState')!=expected for w in cases):
  raise ValueError('Exported scenario DH state differs from requested '+expected)
 return expected

# These IDs mirror BenchmarkScenario.fast/diagnostic; observed cases never define the requirement.
FAST_CASES = ('day-terrain', 'day-foliage', 'day-water', 'day-underwater', 'day-materials',
              'sunset-clear', 'night-clear', 'day-rain', 'cold-rain', 'native-route')
DIAGNOSTIC_CASES = ('diagnostic-terrain', 'diagnostic-materials', 'diagnostic-sunset',
                    'diagnostic-night', 'diagnostic-rain')


def scenario_evaluation(manifest, required_cases=FAST_CASES):
 required = set(required_cases)
 if not required or len(required) != len(required_cases):raise ValueError('Unique required cases must be declared')
 cases = manifest.get('windows', [])
 ids = [(w.get('scenario') or {}).get('caseId') for w in cases]
 duplicates = sorted({case for case in ids if ids.count(case)>1}, key=str)
 missing = sorted(required-set(ids));unexpected = sorted(set(ids)-required, key=str)
 unfinished = [case for case,w in zip(ids,cases) if w.get('hostObservations',{}).get('case_verdict')!='COMPLETE']
 invalid = [case for case,w in zip(ids,cases) if w.get('validity')!='VALID' or w.get('statistics',{}).get('count',0)<=0]
 complete = not (missing or unexpected or duplicates or unfinished)
 return {'execution':'COMPLETE' if cases else 'UNTESTED', 'completion':'COMPLETE' if complete else 'INCOMPLETE',
         'validity':'INVALID' if invalid or duplicates or unexpected else 'UNTESTED' if missing else 'VALID',
         'improvement':'NOT_EVALUATED', 'required_cases':list(required_cases), 'observed_cases':ids,
         'missing_cases':missing, 'unexpected_cases':unexpected, 'duplicate_cases':duplicates,
         'unfinished_cases':unfinished, 'invalid_cases':invalid,
         'limits':'Selected-configuration baseline only; no optimization comparison or live-render verification by this evaluation.'}


def scenario_timing_complete(manifest, required_cases=FAST_CASES):
 result = scenario_evaluation(manifest, required_cases)
 return result['completion']=='COMPLETE' and result['validity']=='VALID'


def comparison_evaluation(decisions, required_comparisons):
 required=set(required_comparisons)
 if not required or len(required)!=len(required_comparisons):raise ValueError('Unique required comparisons must be declared')
 ids=[d.get('comparisonId') for d in decisions];missing=sorted(required-set(ids))
 unmeasured=[d.get('comparisonId') for d in decisions if d.get('validity')!='VALID']
 inconclusive=[d.get('comparisonId') for d in decisions if d.get('verdict')=='INCONCLUSIVE']
 duplicate=len(set(ids))!=len(ids);unexpected=bool(set(ids)-required)
 invalid=duplicate or unexpected or any(d.get('validity')=='INVALID' or d.get('verdict') in ('INVALID','FAILED') for d in decisions)
 validity='INVALID' if invalid else 'UNTESTED' if missing or any(d.get('validity')!='VALID' for d in decisions) else 'VALID'
 optimizations=[d for d in decisions if d.get('controlKind')=='OPTIMIZATION']
 improvement='NOT_EVALUATED'
 if validity!='VALID':improvement='UNRESOLVED'
 elif optimizations:
  verdicts={d.get('verdict') for d in optimizations}
  improvement='INCONCLUSIVE' if 'INCONCLUSIVE' in verdicts else 'OBSERVED_BENEFIT' if verdicts=={'POSITIVE'} else 'VALID_NEGATIVE' if verdicts=={'NEGATIVE'} else 'MIXED' if verdicts=={'POSITIVE','NEGATIVE'} else 'UNRESOLVED'
 return {'execution':'FAILED' if any(d.get('execution')=='FAILED' for d in decisions) else 'COMPLETE' if decisions else 'UNTESTED',
         'completion':'INCOMPLETE' if missing or unmeasured or duplicate or unexpected else 'COMPLETE', 'validity':validity,
         'improvement':improvement, 'required_comparisons':list(required_comparisons),'missing_comparisons':missing,
         'unmeasured_comparisons':unmeasured,'inconclusive_comparisons':inconclusive,
         'decisions':decisions, 'limits':'Shared analyzer timing evidence only; controls cannot establish optimization benefit; no live rendering, quality or cross-session proof.'}


def instrument_controls_passed(decisions):
 checks={d.get('controlKind'):d for d in decisions}
 null=checks.get('KNOWN_NULL',{});effect=checks.get('KNOWN_EFFECT',{})
 return (len(decisions)==2 and all(d.get('validity')=='VALID' for d in decisions)
         and null.get('verdict')=='INCONCLUSIVE' and effect.get('verdict')=='POSITIVE'
         and null.get('reasons')==['known-null calibration only; cannot be an optimization win'])


def evidence_snapshot(request_path, request):
 paths={request_path.resolve()}
 for bracket in request.get('brackets',[]):
  for arm in ('before','candidate','return'):
   directory=Path(bracket[arm]).resolve()
   paths.update(directory/name for name in ('result.json','guard.json','before.json'))
   manifest=json.loads((directory/'result.json').read_text())['manifest']
   windows=manifest.get('windows',[])
   if len(windows)!=1 or windows[0].get('validity')!='VALID':raise ValueError('Invalid or ambiguous exported window: '+str(directory))
   if json.loads((directory/'guard.json').read_text()).get('valid') is not True:raise ValueError('Invalid capture guard: '+str(directory))
 return [{'path':str(path),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()} for path in sorted(paths)]


def analyze_request(request_path, output_directory, classpath=None):
 request_path=Path(request_path).resolve();comparison=request_path.stem
 try:
  request_bytes=request_path.read_bytes();request=json.loads(request_bytes);comparison=request.get('comparison_id','controls')
  evidence=evidence_snapshot(request_path,request)
  if next(row['sha256'] for row in evidence if row['path']==str(request_path))!=hashlib.sha256(request_bytes).hexdigest():raise ValueError('Request changed before evaluation')
  output_directory.mkdir(parents=True,exist_ok=False)
  replay=output_directory/'request.json';decision=output_directory/'decision.json'
  request['output']=str(decision);dev.write_json(replay,request)
  command=['java','-cp',classpath,'com.pp.shaderlod.dev.BenchmarkAnalyze',str(replay)] if classpath else [str(ROOT/'gradlew'),'benchmarkAnalyze','-PbenchmarkAnalysisRequest='+str(replay),'--console=plain']
  process=subprocess.run(command,cwd=ROOT,capture_output=True,text=True,timeout=120)
  (output_directory/'analyzer.log').write_text(process.stdout+process.stderr)
  if process.returncode:raise ValueError('Shared analyzer failed; see '+str(output_directory/'analyzer.log'))
  if evidence_snapshot(request_path,json.loads(request_path.read_text()))!=evidence:raise ValueError('Analysis inputs changed during evaluation')
  result=json.loads(decision.read_text())
  if result.get('comparisonId')!=comparison:raise ValueError('Analyzer comparison identity differs')
  result['execution']='COMPLETE';result['input_evidence']=evidence
  result['replay_evidence']={'path':str(replay),'sha256':hashlib.sha256(replay.read_bytes()).hexdigest()}
  result['decision_evidence']={'path':str(decision),'sha256':hashlib.sha256(decision.read_bytes()).hexdigest()}
  return result
 except (OSError,ValueError,KeyError,subprocess.TimeoutExpired) as error:
  return {'comparisonId':comparison,'execution':'FAILED','validity':'INVALID','verdict':'INVALID','reasons':[str(error)]}


def evaluate_main(arguments):
 parser=argparse.ArgumentParser(description='Headless replay of existing benchmark evidence; never launches a game.')
 parser.add_argument('--result',type=Path);parser.add_argument('--diagnostic',action='store_true')
 parser.add_argument('--required-case',action='append');parser.add_argument('--request',type=Path,action='append',default=[])
 parser.add_argument('--required-comparison',action='append');parser.add_argument('--analyzer-classpath')
 parser.add_argument('--output',required=True,type=Path)
 args=parser.parse_args(arguments)
 if bool(args.result)==bool(args.request):parser.error('Choose one result or one or more analysis requests')
 if args.output.exists():parser.error('A fresh evaluation output is required')
 if args.result:
  data=args.result.read_bytes();result=scenario_evaluation(json.loads(data)['manifest'],args.required_case or (DIAGNOSTIC_CASES if args.diagnostic else FAST_CASES))
  result['input_evidence']=[{'path':str(args.result.resolve()),'sha256':hashlib.sha256(data).hexdigest()}]
 else:
  if not args.required_comparison:parser.error('Declare --required-comparison for every required target')
  decisions=[analyze_request(path,args.output.parent/(args.output.stem+'-analysis-'+str(i)),args.analyzer_classpath) for i,path in enumerate(args.request)]
  result=comparison_evaluation(decisions,args.required_comparison)
 args.output.parent.mkdir(parents=True,exist_ok=True);dev.write_json(args.output,result)
 print(json.dumps({key:result[key] for key in ('execution','completion','validity','improvement')}))
 return 0 if result['completion']=='COMPLETE' and result['validity']=='VALID' else 2


def selected_features(a):
 return getattr(a,'features',None) or ([a.feature] if getattr(a,'feature',None) else [])


def bsl_source_alpha(a,identity):
 return bool(identity and identity.get('family')=='bsl-shaders'
             and getattr(a,'candidate','public')=='public' and selected_features(a)==['early_alpha']
             and getattr(a,'performance_stack','minimal')=='optimized' and not getattr(a,'dh',False))


def cpu_features(a):
 features=set(selected_features(a))
 return bool(features) and features <= {'quad_command_compaction','shadow_admission_cache','index_scalar_cache'}



def affinity_window(window,cpu):
 record=window.get('render_cpu_affinity',{})
 placement=window.get('cpu_placement',{})
 life.require(type(record.get('requested_cpu')) is int and record['requested_cpu']==cpu and record.get('applied') is True and record.get('restored') is True
              and isinstance(record.get('original_cpus'),list) and cpu in record['original_cpus']
              and all(type(value) is int and 0<=value<1024 for value in record['original_cpus'])
              and not record.get('error') and record.get('inherited_restricted_tasks')==[],
              'Render CPU diagnostic did not apply/restore cleanly')
 life.require(type(window.get('completed_render_frames')) is int and window['completed_render_frames']>0
              and placement.get('requested') is True and not placement.get('error') and placement.get('pending') is False
              and placement.get('unknown_heads')==placement.get('unknown_tails')==placement.get('changed_boundary_cpu')==0
              and placement.get('starts')==placement.get('ends')==window.get('completed_render_frames')
              and placement.get('head_cpus')==placement.get('tail_cpus')=={str(cpu):window.get('completed_render_frames')},
              'Render CPU boundary placement differs from declared diagnostic CPU')
 return record


def natural_terrain_inputs(a):
 eligible=a.candidate=='public' and cpu_features(a) or a.candidate=='native-control' and not selected_features(a)
 life.require(getattr(a,'standard_test',False) and eligible
              and not a.dh and a.saved_pose==0,'Natural terrain requires a native CPU standard test or unchanged native control, no DH and the fixed spawn pose')
 a.world='ShaderLOD-Smoke'


def translucent_scene(a):
 return getattr(a,'scene',None)=='translucent-fixture' or getattr(a,'scene',None)!='saved' and 'translucent_fastpath' in selected_features(a)


def candidate_compile_contract(identity, features):
 if len(features)==1:return identity.get('compile_controls',{}).get(features[0])
 controls=identity.get('compile_controls',{});expected={}
 for feature in features:
  contract=controls.get(feature)
  life.require(contract is not None,'Combination has no declared compile contract: '+feature)
  values=contract.get('options') or {contract['option']:contract['enabled']}
  for key,value in values.items():
   life.require(key not in expected or expected[key]==value,'Conflicting combination compile contracts')
   expected[key]=value
 return {'options':expected}


def argument_parser():
 p=argparse.ArgumentParser(description=__doc__+' Default: static preflight; --run explicitly launches the foreground protocol.',allow_abbrev=False)
 p.add_argument('--protocol',default=life.PROTOCOL,choices=[life.PROTOCOL])
 p.add_argument('--run',action='store_true',help='Launch the finite foreground benchmark; requires actual lane authority')
 p.add_argument('--slot',help='Existing managed world-pool slot name; see discover')
 p.add_argument('--profile',type=Path,default=ROOT/'run-workloads/world-pool/neoforge-1211-coast/client')
 p.add_argument('--pack',required=True,type=Path);p.add_argument('--pack-options',type=Path)
 p.add_argument('--bootstrap-options',type=Path,help='Exact base native366 used only before new N02 PBR Nether activation; qualifying arms use --pack-options in full')
 p.add_argument('--output',required=True,type=Path);p.add_argument('--arm',choices=['absent','master-off','empty','cpu2'],default='empty')
 p.add_argument('--suite',choices=['smoke','public-smoke','controls','scenarios','cancel'],default='controls')
 p.add_argument('--width',type=int,default=1920);p.add_argument('--height',type=int,default=1200)
 p.add_argument('--world', default=dev.WORLD, help='Existing saved world; public-smoke uses the pinned authored save')
 p.add_argument('--public-hot',action='store_true',help='Two matched native/control/candidate/return rounds through normal Apply')
 p.add_argument('--native-null',action='store_true',help='Four native controls; untimed public candidate/return observation afterward')
 p.add_argument('--bliss-qualification',choices=('null','ao','clock'),help='Maintained balanced frozen-Bliss qualification; null/clock use byte-identical ZIP, clock adds declared 25ms render-thread delay to B')
 p.add_argument('--qualification-order',choices=bliss_qualification.ORDERS,help='Explicit balanced qualification block order; A original, B null alias or frozen AO')
 p.add_argument('--qualification-client-heap',choices=('1536m','2304m'),help='Prospective Linux/Windows memory-budget qualification variant; changes only the owned client heap and records effective JVM maximum')
 p.add_argument('--bliss-zero-lean',action='store_true',help='Private cheaper zero-emission guard; requires same-run exhaustive native albedo-domain proof')
 p.add_argument('--bliss-albedo-domain',action='store_true',help='Untimed actual-backend exhaustive UNORM16 decode/cubic diagnostic for private Bliss copy or zero candidates')
 p.add_argument('--cpu-placement',action='store_true',help='Read-only Linux renderer-boundary CPU census for native CPU standard comparisons')
 p.add_argument('--audit-graph',action='store_true',help='Untimed selected/linked graph and scalar control inventory after each standard comparison arm')
 p.add_argument('--quality-motion',action='store_true',help='Functional check only: bounded lossless native moving-camera sequences after each arm; never a scored timing run')
 p.add_argument('--performance-stack',choices=performance_stack.CHOICES,default='minimal',help='Finite pinned optimizer tuple for G03 and its matched original baselines; other protocols retain minimal stack')
 p.add_argument('--gl-context',choices=gl_context.CHOICES,default='native',help='Request a real benchmark-only GLFW context; record and validate the actual returned version, keeping context cohorts separate')
 p.add_argument('--expected-renderer',help='Require this observed OpenGL renderer name substring before any scored capture; does not select a GPU or alter driver settings')
 p.add_argument('--comparison-order',choices=('ABBA','BAAB'),help='Four-window balanced public/native-control comparison; A native Off, B candidate. Separate from frozen Bliss qualification.')
 p.add_argument('--render-cpu',type=int,default=-1,help='Diagnostic: constrain only the settled render-thread window to one allowed Linux CPU; requires --cpu-placement')
 p.add_argument('--conditioning',type=float,default=0,help='Finite initial rendered conditioning seconds, at most600')
 p.add_argument('--saved-pose',type=int,default=0,choices=range(12))
 p.add_argument('--untimed-diagnostic',choices=('g01-graph','g09-storage','g09-prefix','g09-functional','c01-cost','c01-primitives','g08-reuse','g02-family','selected-work','retained-copies','cpu-admission-cost'),help='Bounded additional ownership diagnostic after each standard timed arm')
 p.add_argument('--scene',choices=('translucent-fixture','saved','natural-cave','natural-terrain','nether'),help='Explicit maintained scene; natural-terrain uses the prepared native spawn for CPU subsets only')
 p.add_argument('--feature', help='Single normal public optimization ID for public-smoke')
 p.add_argument('--features',type=lambda text:text.split(','),help='Experimental G01/G03/G04/G06 subset: comma-separated public IDs; mutually exclusive with --feature')
 p.add_argument('--g10-control',action='store_true',help='Observer-matched public combination with the private G10 kernel disabled')
 p.add_argument('--with-g10',action='store_true',help='Private exact G10 addition to an explicit public combination; no approximate pairing')
 p.add_argument('--c01-find-opportunity',action='store_true',help='Bounded untimed loaded-cave site/direction/group opportunity search')
 p.add_argument('--c01-group',type=int,choices=(8,16,32),default=32)
 p.add_argument('--c01-site',type=int,default=0,help='Bounded loaded natural-cave census index0..15')
 p.add_argument('--c01-direction',type=int,default=0,help='Natural cave cardinal direction0..3')
 p.add_argument('--g08-bound-layout',choices=('loop','split','alu'),default='loop',help='Finite private native100 cloud bound loop layout')
 p.add_argument('--g02-public-contrast',action='store_true',help='Finite same-process native/private-deletion/public-singleton/return contrast')
 p.add_argument('--c01-mode',choices=('deferred_cpu','deferred','deferred_wait'),default='deferred_cpu',help='Finite private current-frame occlusion decision mode')
 p.add_argument('--candidate',choices=('public','c02-bloom-vrs','c01-bounds','g10-exact-bloom','g10-native-control','g02-native-mips','g02-photon-native','g05-fixed16','g08-native-bound',*PRIVATE_CANDIDATES),default='public',help='Standard test: normal public request or finite private original-derived prototype')
 p.add_argument('--dh',action='store_true');p.add_argument('--dh-mode',choices=['DISABLED','DEFAULT'],default='DISABLED')
 p.add_argument('--seconds',type=float,default=6);p.add_argument('--warmup',type=float,default=20)
 p.add_argument('--budget',type=int,default=900);p.add_argument('--already-built',action='store_true',help='Compatibility flag; this protocol always requires a validated existing build')
 p.add_argument('--minimum-benefit-ms',type=float);p.add_argument('--maximum-drift-ms',type=float);p.add_argument('--maximum-p95-regression-ms',type=float)
 p.add_argument('--reload-controls',action='store_true');p.add_argument('--diagnostic',action='store_true')
 return p

def arguments(argv=None):
 p=argument_parser()
 a=p.parse_args(argv)
 if a.slot:
  if Path(a.slot).name!=a.slot or a.slot in ('.','..'):p.error('slot must be a single managed slot name')
  if argv and any(x=='--profile' or x.startswith('--profile=') for x in argv):p.error('choose slot or profile, not both')
  a.profile=ROOT/'run-workloads/world-pool'/a.slot/'client'
  if not (a.profile.parent/'workspace.json').is_file():p.error('unknown managed slot; use discover')
 for key in ('profile','pack','output','pack_options','bootstrap_options'):
  if getattr(a,key) is not None:setattr(a,key,storage.safe_path(getattr(a,key)))
 return a


def preflight(a):
 """Batch independent cheap boundaries; no profile/world/build mutation or launch."""
 import tempfile
 import check_experiment_plan as checker
 errors=[];checked=[];plan=None;check_seconds={}
 def check(name,call):
  started=time.monotonic()
  try:call();checked.append(name)
  except (OSError,ValueError,KeyError,TypeError,dev.zipfile.BadZipFile) as error:errors.append({'check':name,'error':str(error),'next_action':workflow.remedy(name)})
  finally:check_seconds[name]=time.monotonic()-started
 def inputs():
  life.require(not a.output.exists(),'A fresh report root is required; use recover for an interrupted operation')
  life.require(not a.output.is_relative_to(a.profile.parent) and not a.profile.is_relative_to(a.output),'Reports must be outside the runtime slot')
  life.require(3<=a.seconds<=60 and 5<=a.warmup<=90 and 120<=a.budget<=1800,'Bounded sample/warmup/budget required')
  life.require(640<=a.width<=7680 and 480<=a.height<=4320,'Bounded explicit native viewport required')
  life.require(a.dh or a.dh_mode=='DISABLED','DH DEFAULT requires installed DH')
  life.require(not a.diagnostic or a.suite=='scenarios','Diagnostic requires scenarios')
  life.require(a.suite not in ('scenarios','cancel') or a.arm!='absent','Menu benchmark requires main mod')
  life.require(not os.environ.get('JAVA_TOOL_OPTIONS'),'Ambient JAVA_TOOL_OPTIONS must be explicit')
  life.require(not any(os.environ.get(key) for key in ('_JAVA_OPTIONS','JDK_JAVA_OPTIONS')), 'Ambient Java injection is not allowed')
  for value in (a.minimum_benefit_ms,a.maximum_drift_ms,a.maximum_p95_regression_ms):
   if value is not None:life.require(math.isfinite(value) and value>=0,'Practical criteria must be finite and nonnegative')
 check('bounded_inputs',inputs)
 def public_inputs():
  performance_stack.validate_request(a)
  gl_context.validate_request(getattr(a,'gl_context','native'),frozen_qualification=bool(getattr(a,'bliss_qualification',None)))
  expected=getattr(a,'expected_renderer',None)
  if expected is not None:life.require(bool(expected.strip()) and len(expected)<=128,'Expected renderer must be a nonempty name of at most128 characters')
  if a.quality_motion:
   import benchmark_quality_motion
   benchmark_quality_motion.validate_request(a)
  if getattr(a,'comparison_order',None):
   life.require(getattr(a,'standard_test',False) and not getattr(a,'native_startup_check',False)
                and a.candidate in ('public','native-control','c02-bloom-vrs') and not a.bliss_qualification
                and not a.g02_public_contrast and not a.with_g10 and not a.g10_control and not a.dh,
                'Balanced comparisons require standard public, native-control or bloom VRS, no DH/private additions/frozen qualification')
  if getattr(a,'audit_graph',False):life.require(getattr(a,'standard_test',False) and not getattr(a,'native_startup_check',False) and not getattr(a,'bliss_qualification',None),'Audit graph requires a standard candidate comparison outside frozen qualification')
  if getattr(a,'qualification_client_heap',None):
   life.require(getattr(a,'bliss_qualification',None) and sys.platform in ('linux','win32'),
                'Memory-budget variant requires explicit Linux/Windows Bliss qualification')
  if getattr(a,'bliss_qualification',None) or getattr(a,'qualification_order',None):
   life.require(getattr(a,'standard_test',False) and a.candidate=='n04-bliss-sky' and a.bliss_qualification and a.qualification_order
                and a.scene=='saved' and a.saved_pose in (0,7) and not a.dh and not selected_features(a)
                and not a.untimed_diagnostic and not a.bliss_albedo_domain and not a.bliss_zero_lean,
                'Qualification requires explicit frozen Bliss mode/order, saved mixed0 or sky7 and native options without other diagnostics')
  if getattr(a,'bliss_zero_lean',False):life.require(a.candidate=='n04-bliss-zero' and a.bliss_albedo_domain,'Lean Bliss zero guard requires its same-run native GPU domain diagnostic')
  life.require((a.candidate=='n02-photon-pbr-resources')==bool(getattr(a,'bootstrap_options',None)),'Bootstrap options are required only for the new N02 PBR candidate')
  if getattr(a,'cpu_placement',False):life.require(getattr(a,'standard_test',False) and a.candidate=='public' and cpu_features(a) and sys.platform=='linux' and not a.dh,'CPU placement requires a Linux native CPU standard comparison without DH')
  if getattr(a,'render_cpu',-1)!=-1:
   life.require(0<=a.render_cpu<1024 and getattr(a,'cpu_placement',False) and sys.platform=='linux' and a.render_cpu in os.sched_getaffinity(0),
                'Render CPU requires an allowed Linux CPU and explicit native CPU placement observation')
  if a.candidate in ('n03-photon-cloud-copy','n02-photon-pbr-resources'):life.require(a.scene=='nether','Private Photon cloud-copy requires the guarded Nether scene')
  if a.scene=='natural-terrain':natural_terrain_inputs(a)
  if a.scene=='nether':
   life.require(getattr(a,'standard_test',False) and ((a.candidate=='public' and a.feature=='dead_mip_chain' or a.candidate=='g02-photon-native' and not selected_features(a)) and a.untimed_diagnostic=='g02-family' or a.candidate in ('n03-photon-cloud-copy','n02-photon-pbr-resources') and not selected_features(a) and not a.untimed_diagnostic) and not a.dh and life.digest(a.pack)=='120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead','Nether scene requires pinned Photon singleton mip or private copy observation')
  life.require(not a.c01_find_opportunity or a.scene=='natural-cave' and a.candidate=='c01-bounds','Cave opportunity search requires private natural C01 scene')
  life.require(a.c01_group==32 or a.candidate=='c01-bounds','C01 group requires private C01')
  if a.scene=='natural-cave':
   life.require(getattr(a,'standard_test',False) and a.candidate=='c01-bounds' and not selected_features(a) and not a.dh and 0<=a.c01_site<16 and 0<=a.c01_direction<4,'Natural cave requires finite private C01 and bounded site/direction')
   a.world='ShaderLOD-Smoke'
  else:life.require(a.c01_site==0 and a.c01_direction==0,'Cave site/direction require natural-cave scene')
  if a.candidate in PRIVATE_CANDIDATES:
   life.require(not a.untimed_diagnostic and not selected_features(a) and not a.dh,'Private Bliss sky candidate owns its source diagnostic and empty requests')
  if getattr(a,'bliss_albedo_domain',False):life.require(a.candidate in ('n03-bliss-fog-copy','n04-bliss-zero') and not a.dh,'Albedo domain diagnostic requires exact private Bliss native463 candidate')
  if a.untimed_diagnostic=='cpu-admission-cost':
   life.require(getattr(a,'standard_test',False) and a.candidate=='public' and not a.dh
                and set(selected_features(a))=={'quad_command_compaction','shadow_admission_cache','index_scalar_cache'},
                'Admission-cost diagnostic requires the exact native CPU triple without DH')
  elif a.untimed_diagnostic=='retained-copies':
   life.require(getattr(a,'standard_test',False) and a.candidate=='public' and not a.dh,
                'Retained-copy inventory requires standard public test without DH')
  elif a.untimed_diagnostic=='selected-work':
   life.require(getattr(a,'standard_test',False) and a.candidate=='public' and not a.dh,'Selected-work inventory requires a standard public comparison without DH')
  elif a.untimed_diagnostic=='g02-family':
   life.require(getattr(a,'standard_test',False) and (a.candidate=='public' and a.feature=='dead_mip_chain' or a.candidate in ('g02-native-mips','g02-photon-native') and not selected_features(a)) and not getattr(a,'features',None) and not a.dh,'Mip family observer requires the singleton public or exact private mip candidate without DH')
  elif a.untimed_diagnostic=='g08-reuse':
   life.require(getattr(a,'standard_test',False) and a.candidate=='public' and a.feature=='volume_light_reuse' and not getattr(a,'features',None) and not a.dh,'Cloud reuse observation requires private singleton Photon test without DH')
   a.g08_probe_identity=g08_probe.identity(a.pack,a.pack_options)
  elif a.untimed_diagnostic in ('c01-cost','c01-primitives'):
   life.require(getattr(a,'standard_test',False) and a.candidate=='c01-bounds' and not selected_features(a) and not a.dh,'C01 costs require a private singleton standard test without DH')
  elif a.untimed_diagnostic:
   life.require(getattr(a,'standard_test',False) and a.candidate=='public' and not getattr(a,'features',None) and not a.dh,'Ownership diagnostics require a singleton standard test without DH')
   life.require(a.feature=={'g01-graph':'dead_frame_copy','g09-storage':'pom_empty_height_prefix','g09-prefix':'pom_empty_height_prefix','g09-functional':'pom_empty_height_prefix'}[a.untimed_diagnostic],'Diagnostic feature differs')
  life.require(a.g08_bound_layout=='loop' or a.candidate=='g08-native-bound','Cloud bound layout requires its private candidate')
  life.require(not a.g02_public_contrast or a.candidate=='g02-native-mips' and not a.untimed_diagnostic,'G02 public contrast requires the finite BSL native-mips candidate without another diagnostic')
  life.require(a.c01_mode=='deferred_cpu' or a.candidate=='c01-bounds','C01 mode requires private C01 candidate')
  life.require(a.untimed_diagnostic!='c01-cost' or a.c01_mode=='deferred_cpu','Cost diagnostic currently records deferred CPU mode')
  life.require(not getattr(a,'scene',None) or getattr(a,'standard_test',False),'Explicit scene requires standard test')
  life.require(not (a.with_g10 and a.g10_control),'G10 exact addition and observer-only control are mutually exclusive')
  life.require(not (a.with_g10 or a.g10_control) or getattr(a,'features',None) and a.candidate in ('public','c01-bounds') and getattr(a,'standard_test',False) and not a.dh,'G10 addition requires explicit standard MakeUp combination without DH')
  life.require(not (a.feature and getattr(a,'features',None)),'Use --feature or --features, not both')
  if getattr(a,'features',None):
   life.require(getattr(a,'standard_test',False) and a.candidate in ('public','c01-bounds') and not a.dh,'Combinations require standard public test without DH')
   life.require(2<=len(a.features)<=4 and len(set(a.features))==len(a.features) and (cpu_features(a) or set(a.features)<={'dead_frame_copy','early_alpha','translucent_fastpath','ao_attenuation'}),'Only explicit shader subsets or native CPU subsets are currently supported')
  if cpu_features(a) and 'quad_command_compaction' in selected_features(a):
   life.require('shadow_admission_cache' in selected_features(a),'CPU compaction requires admission cache')
  life.require(a.candidate=='public' or (getattr(a,'standard_test',False) and a.suite=='public-smoke'),'Private candidate requires standard test')
  if a.suite=='public-smoke':
   life.require(a.arm!='absent' and a.world==('ShaderLOD-Smoke' if a.scene in ('natural-cave','natural-terrain') else 'ShaderLOD-Benchmark-overworld-3-flat'), 'Main mod and pinned prepared save required')
   if a.candidate=='public' or selected_features(a):
    life.require(selected_features(a) and all(('\"'+feature+'\"') in (ROOT/'src/main/java/com/pp/shaderlod/optimization/OptimizationId.java').read_text() for feature in selected_features(a)), 'Unknown public feature')
   else:life.require(getattr(a,'standard_test',False) and not selected_features(a) and not a.dh,'Private candidates use standard test without public feature/DH')
  else:life.require(not selected_features(a),'Public features require public-smoke')
  life.require(0<=a.conditioning<=600 and (not a.native_null or (a.suite=='public-smoke' and not a.public_hot and a.arm=='master-off')), 'Native-null requires native public-smoke; bounded conditioning')
  life.require(not a.public_hot or a.suite=='public-smoke','--public-hot requires public-smoke')
 check('public_inputs',public_inputs)
 def standard_inputs():
  if not getattr(a,'standard_test',False):return
  if getattr(a,'native_startup_check',False):
   life.require(a.candidate=='public' and not selected_features(a) and not a.untimed_diagnostic and not a.dh,'Native startup check is original-only, main absent, without feature/diagnostic/DH')
   return
  life.require(a.suite=='public-smoke' and not a.public_hot and not a.native_null and not a.reload_controls and not a.diagnostic,
               'Standard test has one native/candidate/return sequence; diagnostic/cohort alternatives are separate operations')
  if getattr(a,'bliss_qualification',None):
   a.private_identity=bliss_qualification.identity(a.pack,a.pack_options,a.bliss_qualification)
   return
  if a.candidate=='n02-photon-pbr-resources':
   a.private_identity=photon_pbr.identity(a.pack,a.pack_options,a.bootstrap_options)
   return
  if a.candidate in PRIVATE_CANDIDATES:
   a.private_identity=private_consumer(a).identity(a.pack,a.pack_options)
   return
  if a.candidate=='c02-bloom-vrs':
   life.require(not selected_features(a) and not a.dh and a.scene in (None,'saved') and not a.untimed_diagnostic,'C02 requires standalone saved-pose minimal-stack test')
   a.private_identity=bloom_vrs.identity(a.pack,a.pack_options)
   return
  if a.candidate=='g08-native-bound':
   a.private_identity=cloud_bound.identity(a.pack,a.pack_options,layout=a.g08_bound_layout)
   life.require(a.saved_pose==7 and not a.untimed_diagnostic,'Native cloud bound requires saved sky pose7 and its own diagnostic')
   return
  if a.candidate=='g05-fixed16':
   a.private_identity=fixed16.identity(a.pack,a.pack_options)
   life.require(a.saved_pose==2,'Fixed16 requires authored water pose2')
   return
  if a.candidate=='g02-photon-native':
   life.require(a.scene=='nether' and a.untimed_diagnostic=='g02-family','Private Photon requires native Nether and complete mip/pass observer')
   a.private_identity=photon_native_mips.identity(a.pack,a.pack_options)
   return
  if a.candidate=='g02-native-mips':
   a.private_identity=native_mips.identity(a.pack,a.pack_options)
   if a.g02_public_contrast:a.g02_public_identity=mip_contrast.public_identity(a.pack,ROOT,a.private_identity)
   return
  if a.candidate=='c01-bounds':
   a.private_identity=iteration.c01_identity(a.pack,a.pack_options,ROOT,mode=a.c01_mode,group=a.c01_group)
   if not selected_features(a):return
   a.private_identity['scope']='Finite private C01 plus explicit public combination; original native100 and attached SOLID pins retained.'
  if a.candidate in ('g10-exact-bloom','g10-native-control'):
   a.private_identity=iteration.g10_identity(a.pack,a.pack_options,ROOT,native_control=a.candidate=='g10-native-control')
   return
  identity=iteration.public_identity(a.pack,ROOT)
  if getattr(a,'features',None) and not cpu_features(a):
   life.require(identity['family']=='makeup-ultra-fast-shaders','Experimental combination requires pinned MakeUp')
   candidate_compile_contract(identity,selected_features(a))
  if translucent_scene(a):
   life.require(identity['family']=='makeup-ultra-fast-shaders' and a.saved_pose==9,
                'G04 standard fixture requires pinned MakeUp and --saved-pose 9')
  a.public_identity=identity
 check('standard_public_identity',standard_inputs)
 def slot():
  marker=a.profile.parent/'workspace.json';data=json.loads(marker.read_text())
  life.require(a.profile.parent.parent==ROOT/'run-workloads/world-pool' and a.profile.name=='client','Use an existing bounded world-pool slot')
  life.require(data.get('schema')==1 and data.get('status')=='ready' and data.get('slot')==a.profile.parent.name,'Pool receipt is not ready')
  life.require(not list(a.profile.parent.glob('.client-reset-*')),'Interrupted slot reset requires its owner')
 check('existing_pool_slot',slot)
 hook_actions=('prepare','status','open_coast','video','pose','configure','window_start','window_stop','restore','cancel','benchmark_cancel')
 if getattr(a,'standard_test',False):hook_actions+=('open_saved_authored','pose_saved_authored','settings_apply','capture_saved_authored','close_saved_authored')
 if getattr(a,'native_baseline',False):hook_actions+=('graph_dump',)
 if getattr(a,'audit_graph',False):hook_actions+=('graph_dump',)
 if getattr(a,'quality_motion',False):hook_actions+=('quality_start','quality_status','quality_stop')
 if cpu_features(a):hook_actions+=('cpu_graph_identity',)
 if a.untimed_diagnostic=='cpu-admission-cost':hook_actions+=('cpu_admission_cost',)
 if a.untimed_diagnostic=='retained-copies':hook_actions+=('frame_copy_observe',)
 if getattr(a,'standard_test',False) and 'dead_frame_copy' in selected_features(a):hook_actions+=('frame_copy_observe',)
 if getattr(a,'standard_test',False) and (a.feature=='dead_mip_chain' or a.candidate=='g02-native-mips'):hook_actions+=('bsl_mip_observe',)
 if getattr(a,'standard_test',False) and translucent_scene(a):hook_actions+=('prepare_feature_scene',)
 if getattr(a,'standard_test',False) and a.feature in ('reflection_next_event_refinement','reflection_projected_refinement'):hook_actions+=('reflection_status',)
 if getattr(a,'standard_test',False) and a.feature=='ao_depth_reuse':hook_actions+=('ao_depth_status',)
 if getattr(a,'standard_test',False) and 'early_alpha' in selected_features(a):hook_actions+=('early_alpha_status','bsl_public_state')
 if a.with_g10 or a.g10_control:hook_actions+=('g10_bloom_status',)
 if a.candidate=='g08-native-bound':hook_actions+=('g08_bound_status','g08_bound_queue','g08_bound_read','diagnostic_pack','diagnostic_empty_requests')
 if a.candidate=='c02-bloom-vrs':hook_actions+=('vrs_request','vrs_status')
 if a.candidate=='c01-bounds':hook_actions+=('c01_bounds',)
 if a.untimed_diagnostic=='g09-functional':hook_actions+=('g09_prefix_work','g09_prefix_admission')
 if a.scene=='nether':hook_actions+=('mip_nether_pose','mip_nether_settle')
 if a.scene=='natural-cave':hook_actions+=('c01_natural_open','c01_natural_spawn','c01_natural_scan','c01_natural_site')
 if a.scene=='natural-terrain':hook_actions+=('native_terrain_open','native_terrain_spawn')
 if a.g02_public_contrast:hook_actions+=('mip_family_observe','graph_dump')
 if a.untimed_diagnostic in ('g02-family','selected-work'):hook_actions+=('mip_family_observe','graph_dump')
 if a.untimed_diagnostic=='g08-reuse':hook_actions+=('g08_cloud_status','g08_cloud_read','g08_cloud_queue','diagnostic_pack','diagnostic_empty_requests')
 if a.untimed_diagnostic=='c01-cost':hook_actions+=('c01_cost',)
 if a.untimed_diagnostic in ('g01-graph','g09-storage','g09-prefix','g09-functional'):hook_actions+=(('g01_graph',) if a.untimed_diagnostic=='g01-graph' else ('g09_prefix_admission',))
 if a.candidate=='g02-native-mips':hook_actions+=('diagnostic_pack',)
 if a.candidate in ('g02-photon-native','n04-bliss-sky','n03-bliss-fog-copy','n04-bliss-zero','n03-photon-cloud-copy','n02-bliss-unused-rt12','n04-solas-lean-alpha','n04-sildur-underwater-ao','n02-photon-pbr-resources','n04-sildur-lean-alpha'):hook_actions+=('diagnostic_pack','diagnostic_empty_requests')
 if a.candidate in PRIVATE_CANDIDATES:hook_actions+=('mip_family_observe','graph_dump','diagnostic_pack','diagnostic_empty_requests')
 if a.candidate in PRIVATE_CANDIDATES and not getattr(a,'bliss_qualification',None):hook_actions+=('shaderlod.dev.privateObservedReload',)
 if a.candidate=='n04-solas-lean-alpha':hook_actions+=('solas_lean_alpha',)
 if a.candidate=='n04-sildur-lean-alpha':hook_actions+=('sildur_lean_alpha',)
 if a.candidate=='g05-fixed16':hook_actions+=('diagnostic_pack','depth_hierarchy_cost','depth_hierarchy_status','depth_hierarchy_verify','causal_fragment','motion_start','motion_stop')
 if a.candidate in ('g10-exact-bloom','g10-native-control'):hook_actions+=('diagnostic_pack','g10_bloom_status','g10_bloom_work')
 def source_hooks():
  hooks=ROOT/'src/devClient/java/com/pp/shaderlod/dev/BenchmarkControlProbe.java'
  raw=hooks.read_bytes()
  for action in hook_actions:life.require(action.encode() in raw,'Required source benchmark hook missing: '+action)
  iris=dev.properties((a.profile/'config/iris.properties').read_text())
  life.require(iris.get('enableDebugOptions','false')=='false','Disable generated source export for this benchmark protocol')
 check('source_hooks_export_policy',source_hooks)
 def compiled_hooks():
  raw=(ROOT/'build/classes/java/devClient/com/pp/shaderlod/dev/BenchmarkControlProbe.class').read_bytes()
  for action in hook_actions:life.require(action.encode() in raw,'Required compiled benchmark hook missing: '+action)
 check('compiled_control_hooks',compiled_hooks)
 def runtime_artifacts():
  lock=json.loads((ROOT/'dependencies/runtime.lock.json').read_text())
  selected=['iris','sodium']+(['distanthorizons'] if a.dh else [])
  for name in selected:
   artifact=lock['artifacts'][name]
   path=(a.profile/'mods'/artifact['filename']) if name=='distanthorizons' else ROOT/'dependencies/runtime'/artifact['filename']
   path=storage.safe_path(path);actual=hashlib.sha512()
   with path.open('rb') as stream:
    for block in iter(lambda:stream.read(life.MIB),b''):actual.update(block)
   life.require(actual.hexdigest()==artifact['hashes']['sha512'],'Pinned runtime artifact differs: '+name)
  performance_stack.validate_cache(ROOT,getattr(a,'performance_stack','minimal'))
 check('runtime_artifact_pins',runtime_artifacts)
 check('existing_build_binding',dev.validate_built)
 def create_plan():
  nonlocal plan
  # dev.plan needs a real control file; its immutable path is relocated once below.
  with tempfile.TemporaryDirectory(prefix='shaderlod-plan-') as td:
   control=Path(td)/'control.json';control.write_text('{"request_id":"initial_prepare","action":"prepare"}')
   params=SimpleNamespace(world=a.world,mode='off' if a.arm=='absent' else 'on',profile=a.profile,pack=a.pack,
    pack_options=a.bootstrap_options if a.candidate=='n02-photon-pbr-resources' else a.pack_options,dh=a.dh,already_built=True,ready_timeout=min(180 if getattr(a,'standard_test',False) else 300,a.budget-60),
    performance_stack=getattr(a,'performance_stack','minimal'),
    benchmark_tuner_probe=None,benchmark_control_probe=control,
    bsl_mip_witness_runtime=getattr(a,'standard_test',False) and (a.feature=='dead_mip_chain' or a.candidate=='g02-native-mips'),
    c01_bounds_runtime=a.candidate=='c01-bounds',
    shortlist_combination_runtime=bool(getattr(a,'features',None)),
    g10_benchmark_runtime=a.candidate in ('g10-exact-bloom','g10-native-control') or a.with_g10 or a.g10_control,
    shortlist_g10_runtime=a.with_g10,
    stop_at=(datetime.now(timezone.utc)+timedelta(seconds=a.budget)).isoformat(),preset=None,
    apply_lod_settings=False,lod_state=None,consumer_uniforms=None,diagnostic_gpu_pass_timing=False,foreground=True)
   plan=dev.plan(params)
   plan['requested_gl_context']=getattr(a,'gl_context','native')
   plan['expected_renderer']=getattr(a,'expected_renderer',None)
   if plan['requested_gl_context']!='native':plan['command']+=['-PbenchmarkGlContext='+plan['requested_gl_context']]
  final_control=str(a.output/'control.json')
  plan['benchmark_control_probe']=final_control
  plan['command']=[('-PbenchmarkControlProbe='+final_control) if x.startswith('-PbenchmarkControlProbe=') else x for x in plan['command']]
  # A finite experiment must not leave a newly created shared daemon behind.
  plan['command']+=['--no-daemon']
  if getattr(a,'qualification_client_heap',None):plan['command']+=['-PclientHeap='+a.qualification_client_heap]
  if not getattr(a,'bliss_qualification',None):plan['command']+=['-PrendererWorkCounters=true']
  if a.candidate=='g08-native-bound':plan['command']+=['-Pg08BoundRuntime=true']
  if a.candidate=='g02-native-mips':plan['command']+=['-PbslNativeMipWitnessRuntime=true']
  if a.candidate=='g05-fixed16':plan['command']+=['-PdepthHierarchyR50=true','-PdepthHierarchyParallelR70=true','-PdepthHierarchyLowerAsahiR2=true','-PdepthHierarchyCostAsahiR3=true','-PdepthHierarchyFixed16AsahiR1=true','-PfragmentWorkProbe=true']
  if a.scene=='natural-cave':plan['command']+=['-Pc01PreparedSmoke=true']
  if a.scene=='natural-terrain':plan['command']+=['-PnativeTerrainScene=true']
  if a.untimed_diagnostic=='cpu-admission-cost':plan['command']+=['-PcpuAdmissionCost=true']
  if getattr(a,'cpu_placement',False):plan['command']+=['-PcpuPlacement=true']
  if getattr(a,'render_cpu',-1)>=0:plan['command']+=['-PrenderCpu='+str(a.render_cpu)]
  if a.g02_public_contrast:plan['command']+=['-PmipFamilyRuntime=true']
  if a.untimed_diagnostic in ('g02-family','selected-work') or a.candidate in PRIVATE_CANDIDATES:plan['command']+=['-PmipFamilyRuntime=true']
  if a.quality_motion:plan['command']+=['-PqualityMotionRuntime=true']
  if a.candidate in PRIVATE_CANDIDATES and not getattr(a,'bliss_qualification',None):plan['command']+=['-PprivateObservedReload=true']
  if a.candidate=='n04-bliss-sky':plan['command']+=['-PblissAoCoverage=true']
  if a.candidate=='n04-solas-lean-alpha':plan['command']+=['-PsolasLeanAlpha=true']
  if a.candidate=='n04-sildur-lean-alpha':plan['command']+=['-PsildurLeanAlpha=true']
  if a.candidate=='n04-sildur-underwater-ao':plan['command']+=['-PsildurAoObservation=true']
  if getattr(a,'bliss_albedo_domain',False):plan['command']+=['-PblissAlbedoDomain=true']
  if a.untimed_diagnostic=='g08-reuse':plan['command']+=['-Pg08CloudRuntime=true']
  if a.untimed_diagnostic=='c01-cost':plan['command']+=['-Pc01Cost=true']
  if a.untimed_diagnostic=='g01-graph':plan['command']+=['-Pg01ExposureProbe=true']
  if a.untimed_diagnostic=='g09-functional':plan['command']+=['-Pg09PrefixAdmissionOnly=1','-Pg09PrefixFunctional=1','-PgpuAttribution=false','-PgpuPassTiming=false','-PcpuLayerTiming=false']
  if a.untimed_diagnostic=='g09-prefix':plan['command']+=['-Pg09PrefixAdmissionOnly=1','-PgpuAttribution=false','-PgpuPassTiming=false','-PcpuLayerTiming=false']
  if a.untimed_diagnostic=='g09-storage':plan['command']+=['-Pg09StoragePropertiesOnly=1','-Pg09PrefixAdmissionOnly=1','-PgpuAttribution=false','-PgpuPassTiming=false','-PcpuLayerTiming=false']
  if a.suite=='public-smoke' or getattr(a,'native_startup_check',False):plan['command']+=['-PsavedAuthoredScene=true']
  runtime=json.loads((ROOT/'dependencies/runtime.lock.json').read_text())
  life.require(runtime['minecraft']=='1.21.1' and runtime['neoforge']=='21.1.253','Unsupported protocol runtime version')
  plan['protocol']=life.PROTOCOL
  plan['native_viewport']=[a.width,a.height]
  plan['requested_options']=dev.properties(Path(plan['pack_options']).read_text()) if plan.get('pack_options') else {}
  plan['runtime_versions']={'minecraft':runtime['minecraft'],'neoforge':runtime['neoforge'],
   **{name:life.neoforge_mod_version(ROOT/'dependencies/runtime'/runtime['artifacts'][name]['filename'],name) for name in ('iris','sodium')}}
  if a.dh:plan['runtime_versions']['distanthorizons']=life.neoforge_mod_version(a.profile/'mods'/runtime['artifacts']['distanthorizons']['filename'],'distanthorizons')
  result=checker.check_plan(plan)
  errors.extend(result['errors']);checked.extend(result['checked'])
 check('generated_plan',create_plan)
 # Scope metadata only; the expensive global logical limit is rechecked by canonical supervisor admission.
 def capacity():
  probe=a.output.parent
  while not probe.exists():probe=probe.parent
  life.require(shutil.disk_usage(probe).free>=storage.MIN_FREE+life.LIMITS['report_bytes'],'Report destination lacks 32 GiB plus report allowance')
  life.require(shutil.disk_usage(a.profile).free>=storage.MIN_FREE+life.LIMITS['profile_growth_bytes'],'Profile lacks 32 GiB plus runtime growth allowance')
 check('destination_capacity',capacity)
 if getattr(a,'standard_test',False):
  check('restore_filesystem',lambda:check_restore_filesystem(a.output,a.profile))
  check('runtime_available',lambda:life.require(not dev.clients(dev.process_table()),'Minecraft client is active; preserve its operation and retry after it is released'))
  if not any(e['check']=='runtime_available' for e in errors):
   check('managed_profile_inputs',lambda:iteration.check_profile_inputs(a.profile))
 def storage_guard():
  with tempfile.TemporaryDirectory(prefix='shaderlod-storage-preflight-',dir=probe_parent(a.output)) as directory:
   life.StorageGuard(Path(directory),a.profile).check()
 check('actual_storage_guard',storage_guard)
 if getattr(a,'standard_test',False):
  # Conservative ordinary startup/reload allowance, plus a separate teardown reserve.
  arms=4 if getattr(a,'bliss_qualification',None) else 3
  required=180+60+arms*(a.seconds+a.warmup+15)+90
  required+=a.conditioning
  if a.feature=='dead_mip_chain' or a.candidate in ('g02-photon-native','n04-bliss-sky','n03-bliss-fog-copy','n04-bliss-zero','n03-photon-cloud-copy','n02-bliss-unused-rt12','n04-solas-lean-alpha','n04-sildur-underwater-ao','n02-photon-pbr-resources','n04-sildur-lean-alpha'):required+=arms*32  # untimed observed reload/frames
  if 'dead_frame_copy' in selected_features(a):required+=3*4
  if a.budget<required:errors.append({'check':'complete_sequence_budget','error':f'Full standard sequence requires at least {math.ceil(required)} seconds including 90 seconds recovery'})
 return {'protocol':life.PROTOCOL,'errors':errors,'checked':checked,'check_seconds':check_seconds,'plan':plan,
         'unverified':['Live ownership/lane and world locks','Actual JVM loaded runtime and control acknowledgments','Capture completion and native render correctness','APFS/Windows/WSL runtime and physical reclamation']}


def probe_parent(path):
 while not path.exists():path=path.parent
 return path


def check_restore_filesystem(output,profile):
 life.require(probe_parent(output).stat().st_dev==profile.stat().st_dev,
              'Standard test report and profile must be on the same filesystem for atomic world restoration')


def supervise_operation(output):
 """Existing supervisor, with exact prepare checkpoints inside its owned lane."""
 op=life.Operation(output);original=dev.prepare
 until=time.monotonic()+10
 while not (op.output/'launch-authorized.json').exists():
  life.require(time.monotonic()<until,'Controller did not hand over supervisor ownership')
  time.sleep(.05)
 authorization=json.loads((op.output/'launch-authorized.json').read_text())
 life.require(authorization.get('operation')==op.data['id'],'Supervisor operation identity differs')
 op.reload()
 def prepare(plan,backup):
  op.begin_mutation('supervisor-prepare')
  try:return original(plan,backup)
  finally:
   op.acknowledge_mutation('supervisor-prepare')
   op.record_preparation_backups(backup)
   op.begin_mutation('initial-runtime-prepare')
 dev.prepare=prepare
 try:dev.supervise(op.output/'session')
 finally:dev.prepare=original


def release_runtime(op, proc=None, *, preserve_conflicts=False):
 """Use existing exact-client/launcher APIs; preserve custody on any refusal."""
 session=op.output/'session';marker=session/'owner.json'
 owner=dev.read_published_json(marker) if marker.exists() else {}
 # Expected shutdown writes are admitted only when stop is deliberately issued
 # from an acknowledged checkpoint. Recovery of a dead controller never adopts
 # previously unrecorded exit writes.
 shutdown=False
 if proc is not None and proc.poll() is None and not op.data['mutation_pending']:
  try:op.begin_mutation('runtime-shutdown');shutdown=True
  except ValueError as error:
   # Preserve uncertain configuration, but always release exact owned processes.
   op.data['shutdown_configuration_conflict']=str(error);op.save()
 if owner.get('clients'):
  dev.stop(session)
 if owner.get('launcher'):
  op.data['release']=dev.retire_source_launcher(session)
 if proc is not None:proc.wait(timeout=20)
 elif op.data['processes'].get('supervisor'):
  # The exact supervisor may still be reaping an already stopped client.
  proof=op.data['processes']['supervisor'];until=time.monotonic()+20
  while time.monotonic()<until:
   row=dev.process_table().get(int(proof['pid']))
   if row is None or dev.identity(row)!=proof:break
   time.sleep(.1)
 table=dev.process_table()
 life.require(not dev.clients(table),'Minecraft client remains; configuration retained')
 for proof in [*owner.get('clients',{}).values(),owner.get('launcher'),op.data['processes'].get('supervisor')]:
  life.process_absent(proof,table,dev.identity)
 with dev.runtime_lock():
  life.require(not dev.clients(dev.process_table()),'Client started before restoration')
  # Save locks must be free, even if the last JVM census was empty.
  with ExitStack() as locks:
   from benchmark_file_locks import world_lock
   for path in (Path(op.data['profile'])/'saves').glob('*/session.lock'):
    locks.enter_context(world_lock(storage.safe_path(path)))
   if shutdown:op.acknowledge_mutation('runtime-shutdown')
   op.data['process_release_verified']=True;op.save()
   if preserve_conflicts:op.preserve_conflicting_configuration()
   op.restore()
 op.data['release_verified']=True;op.save()


def recover_main(argv):
 p=argparse.ArgumentParser(description='Restore/release one interrupted operation; never replay captures or comparisons.')
 p.add_argument('--output',required=True,type=Path);p.add_argument('--dispose-backups',action='store_true')
 p.add_argument('--preserve-conflicts',action='store_true',help='After exact runtime release, retain conflicting owned configuration files before restoring the original backup; never replay commands')
 p.add_argument('--preserve-optional-configs',action='store_true',help='Explicitly retain known newly generated optional-mod configs before restoring an older operation; existing/unknown edits remain protected')
 p.add_argument('--preserve-generated-backups',action='store_true',help='Retain known newly generated ShaderLOD FML backups omitted by an older operation; existing and unknown files remain protected')
 a=p.parse_args(argv);op=life.Operation(a.output)
 with life.operation_lock(op.output):
  if op.data['state'] not in life.TERMINAL:
   # Controller must be absent; a recycled PID is never signaled.
   life.process_absent(op.data['processes'].get('controller'),dev.process_table(),dev.identity)
   release_runtime(op,preserve_conflicts=a.preserve_conflicts)
   if a.preserve_optional_configs:performance_stack.preserve_created_configs(op,ROOT)
   if a.preserve_generated_backups:iteration.preserve_created_config_backups(op)
   if op.data.get('iteration_profile'):iteration.finish_profile(op)
   op.data['state']='failed-restored';op.save()
  if a.dispose_backups:op.dispose_backups()
 print(json.dumps({'state':op.data['state'],'configuration_restored':op.data.get('configuration_restored'),'replayed_commands':0}))
 return 0


class Control:
 def __init__(self,op,guard,end):
  self.op,self.guard,self.end=op,guard,end;self.recovery=False
 def wait(self,seconds,*,full=True):
  if not self.recovery and time.monotonic()+seconds>self.end:raise TimeoutError('Run budget exhausted; recovery reserve retained')
  self.guard.check(full=full)
  until=time.monotonic()+seconds
  while time.monotonic()<until:
   time.sleep(min(1,max(0,until-time.monotonic())))
   self.guard.check(full=False)
 def send(self,action,**kwargs):
  op=self.op;row=op.command(action,kwargs)
  # Read-only status/window commands must not silently adopt unrelated config edits.
  mutates=action in {'vrs_request','prepare','open_coast','video','pose','configure','reload','restore','cancel','benchmark_cancel','close_coast','benchmark_start','settings_apply','open_saved_authored','pose_saved_authored','close_saved_authored','prepare_feature_scene','diagnostic_pack','diagnostic_empty_requests','c01_natural_open','c01_natural_spawn','c01_natural_site','mip_nether_pose','mip_nether_settle'}
  mutates=mutates or (action in ('bsl_mip_observe','mip_family_observe') and kwargs.get('reload') is True)
  if mutates:op.begin_mutation(row['id'])
  destination=op.output/'receipts'/(row['id']+'.json')
  life.require(not destination.exists(),'Control receipt collision; refuse replay')
  life.atomic_json(op.output/'control.json',{'request_id':row['id'],'action':action,**kwargs})
  command_seconds=180 if op.data.get('mutation_pending') else 90
  until=min(self.end,time.monotonic()+command_seconds) if not self.recovery else time.monotonic()+10
  while time.monotonic()<until:
   marker=op.output/'session/owner.json'
   if marker.exists() and dev.read_published_json(marker).get('status') in {'startup-failed','startup-timeout','exited','stopped','timed-out'}:raise RuntimeError('Client terminal; control unavailable')
   if destination.exists():
    try:receipt=json.loads(destination.read_text())
    except json.JSONDecodeError:
     # The existing Java receipt writer may be observed between truncate and write.
     self.wait(.05,full=False);continue
    if receipt.get('status')!='PENDING':
     life.require(receipt.get('request_id')==row['id'],'Control receipt request identity differs')
     # A rejected operation is still an acknowledged boundary; preserve its
     # exact partial/rolled-back bytes, then report the rejection to the caller.
     if mutates:
      # World entry is asynchronous: its callback receipt precedes rendered
      # readiness and delayed Iris/settings writes. Keep ownership until then.
      if (action in {'vrs_request','open_saved_authored','c01_natural_open','mip_nether_pose','settings_apply','diagnostic_pack'} or action in ('bsl_mip_observe','mip_family_observe') and kwargs.get('reload') is True) and receipt.get('success') is True:
       self.acknowledged_ready=self.ready()
      # Driver reload/family migration can persist configuration after the control callback.
      # Keep the mutation boundary open until its owned bytes have settled.
      until_settled=time.monotonic()+10;previous=None;stable_since=time.monotonic()
      while True:
       current={path:life.digest(path) for path in op.data['configuration']}
       if current!=previous:previous=current;stable_since=time.monotonic()
       if time.monotonic()-stable_since>=1:break
       life.require(time.monotonic()<until_settled,'Configuration mutation did not settle');time.sleep(.2)
      op.acknowledge_mutation(row['id'])
     op.complete_command(row,receipt)
     return receipt
   # Recovery must not be prevented by low space or exhausted science time.
   if self.recovery:time.sleep(.2)
   else:self.wait(.2,full=False)
  raise TimeoutError(row['id'])
 def ready(self):
  cut=min(self.end,time.monotonic()+180);stable=None;since=time.monotonic();first_frame=None
  while time.monotonic()<cut:
   state=self.send('status')['state'];pin=tuple(state.get(k) for k in ('pipeline_identity','pack_identity','width','height'))
   if pin!=stable:stable=pin;since=time.monotonic();first_frame=state.get('render_frames')
   # world_frames is capture-scoped. Global completed-render progress works
   # both inside and outside capture windows; a static status cannot pass.
   progressed=isinstance(first_frame,int) and state.get('render_frames',first_frame)>first_frame
   if state.get('world_loaded') and state.get('meshes_ready') and not state.get('fallback') and progressed and time.monotonic()-since>=2:return state
   self.wait(.5)
  raise TimeoutError('World/pipeline readiness')


def settle_initial_runtime(control,op):
 """Keep the existing startup mutation open through following client ticks.

 The prepare receipt can precede the last asynchronous family-config rename.
 Read-only callbacks and the normal one-second byte-stability boundary complete
 that same owned startup; ordinary later mutation/CAS checks remain unchanged.
 """
 life.require(op.data.get('mutation_pending')=='initial-runtime-prepare','Owned initial preparation required')
 until=time.monotonic()+10;previous=None;stable_since=time.monotonic();callbacks=0
 while True:
  control.send('status');callbacks+=1
  current={path:life.digest(path) for path in op.data['configuration']}
  if current!=previous:previous=current;stable_since=time.monotonic()
  if callbacks>=2 and time.monotonic()-stable_since>=1:
   return {'status_callbacks':callbacks,'stable_seconds':time.monotonic()-stable_since,
           'scope':'Same owned initial startup, after subsequent client callbacks and stable tracked configuration bytes'}
  life.require(time.monotonic()<until,'Initial configuration did not settle')
  control.wait(.2,full=False)


def ready_after_apply(control):
 """Apply already waited for rendered readiness; verify its boundary remains current."""
 previous=getattr(control,'acknowledged_ready',None)
 state=control.send('status')['state']
 keys=('pipeline_identity','pack_identity','width','height','dimension')
 if (isinstance(previous,dict) and all(previous.get(k) is not None for k in keys) and all(state.get(k)==previous.get(k) for k in keys)
     and all(state.get(k) for k in ('world_loaded','meshes_ready')) and not state.get('fallback')
     and isinstance(state.get('render_frames'),int) and isinstance(previous.get('render_frames'),int)
     and state['render_frames']>previous['render_frames']):
  control.reused_apply_readiness=getattr(control,'reused_apply_readiness',0)+1
  return state
 return control.ready()


def run_operation(a,admission):
 out=a.output;plan=admission['plan'];target=a.profile/'shaderpacks'/a.pack.name
 paths=life.configuration_paths(a.profile,a.pack)
 paths+=performance_stack.configuration_paths(ROOT,a.profile,getattr(a,'performance_stack','minimal'))
 if a.untimed_diagnostic=='g08-reuse':paths += [a.profile/'shaderpacks'/(g08_probe.NAME+suffix) for suffix in ('','.txt')]
 identity=getattr(a,'public_identity',None)
 source_bsl=bsl_source_alpha(a,identity)
 g02_public=getattr(a,'g02_public_identity',None)
 if g02_public:
  paths += [a.profile/'shaderpacks'/g02_public[key] for key in ('generated','state','legacy_state')]
  paths += [a.profile/'shaderpacks'/(g02_public['generated']+'.txt')]
 private_identity=getattr(a,'private_identity',None)
 private_alpha_action='sildur_lean_alpha' if a.candidate=='n04-sildur-lean-alpha' else 'solas_lean_alpha'
 private_alpha_prefix='sildur' if a.candidate=='n04-sildur-lean-alpha' else 'solas'
 private_alpha_consumer=sildur_alpha if a.candidate=='n04-sildur-lean-alpha' else solas_alpha
 c02=a.candidate=='c02-bloom-vrs'
 c01=private_identity and private_identity.get('candidate')=='c01-bounds'
 g02=private_identity and private_identity.get('candidate') in ('g02-native-mips','g02-photon-native')
 photon_g02=bool(g02 and private_identity.get('candidate')=='g02-photon-native')
 bliss=private_identity and private_identity.get('candidate') in PRIVATE_CANDIDATES
 bliss_consumer=private_consumer(a)
 bliss_observations_key=PRIVATE_CANDIDATES[a.candidate][1] if a.candidate in PRIVATE_CANDIDATES else None
 g05=private_identity and private_identity.get('candidate')=='g05-fixed16'
 g08=private_identity and private_identity.get('candidate')=='g08-native-bound'
 if g08:paths += [a.profile/'shaderpacks'/(private_identity[key]+suffix) for key in ('generated','work') for suffix in ('','.txt')]
 if g05:paths += [a.profile/'shaderpacks'/(private_identity[key]+suffix) for key in ('generated','work') for suffix in ('','.txt')]
 if g02 or bliss:paths += [a.profile/'shaderpacks'/(private_identity['generated']+suffix) for suffix in ('','.txt')]
 if a.candidate=='n02-photon-pbr-resources':paths += [a.profile/'shaderpacks'/(private_identity['qualified_original']+suffix) for suffix in ('','.txt')]
 g10=private_identity and private_identity.get('candidate') in ('g10-exact-bloom','g10-native-control')
 if g10:
  paths += [a.profile/'shaderpacks'/(private_identity[key]+suffix) for key in ('generated','work') for suffix in ('','.txt')]
 if identity:
  directory=a.profile/'shaderpacks'
  paths+= [directory/identity[key] for key in ('generated','state','legacy_state')]
  paths+= [directory/(identity['generated']+'.txt')]
 quality_limits={'report_bytes':12*1024**3,'capture_bytes':12*1024**3} if a.quality_motion else None
 op=life.Operation.create(out,a.profile,plan,sorted(set(paths)),limits=quality_limits)
 with life.operation_lock(out):
  op.data['processes']['controller']=dev.identity(dev.process_table()[os.getpid()]);op.save()
  guard=life.StorageGuard(out,a.profile,limits=quality_limits);control=Control(op,guard,time.monotonic()+a.budget-(90 if getattr(a,'standard_test',False) else 45))
  clock=time.monotonic();costs={}
  summary={'purpose':'functional-check' if getattr(a,'functional_check',False) else 'benchmark','cost_seconds':costs,'workflow_setup_seconds':admission.get('workflow_setup_seconds',0),'workflow_build_seconds':admission.get('workflow_build_seconds',0),'started':life.now(),'protocol':life.PROTOCOL,'arm':a.arm,'suite':a.suite,'windows':[],'analyses':[]}
  if private_identity:summary['private_identity']=private_identity
  if bliss:summary['private_reload_protocol']='atomic-observed-reload-v3'
  summary['candidate_contract']={'public_features':selected_features(a),'c01_private':bool(c01),'g02_native_mips_private':bool(g02),'g05_fixed16_private':bool(g05),'g10_private_addition':a.with_g10,'g10_observer_control':a.g10_control,'scene':getattr(a,'scene',None) or 'automatic'}
  if getattr(a,'comparison_order',None):summary['comparison_order']=a.comparison_order
  if getattr(a,'bliss_qualification',None):
   summary['qualification']={'mode':a.bliss_qualification,'order':a.qualification_order,
                             'saved_pose':a.saved_pose,
                             'protocol_variant':bliss_qualification.PROTOCOL_VARIANT,
                             'untimed_reload':'atomic observer plus verified selection; one success reload with preceding GL drain',
                             'client_heap_requested':getattr(a,'qualification_client_heap',None),
                             'sequence':bliss_qualification.sequence(a.qualification_order),
                             'seconds':a.seconds,'warmup':a.warmup,'conditioning':a.conditioning,
                             'scope':'One process block; no independent-frame inference or per-block acceptance.'}
  proc=None;log=None
  def save():life.atomic_json(out/'summary.json',summary)
  def window(label,delay=0,recording=True):
   telemetry=host_observation()
   summary.setdefault('host_telemetry',{})[label]={'before':telemetry,'loadavg':list(os.getloadavg()) if hasattr(os,'getloadavg') else None}
   state=control.send('status')['state']
   life.require(not state.get('installed',{}).get('cpu_admission_cost',{}).get('observing',False),'CPU admission-cost observer armed during timing')
   life.require(not state.get('installed',{}).get('g10_bloom_observing',False),'G10 observer armed during timing')
   life.require(not state.get('installed',{}).get('post_sample_draw_witness',{}).get('observing',False),'G03 witness armed during timing')
   life.runtime_ready(state,op.data.get('runtime_plan',plan),dev.read_published_json(out/'session/owner.json'))
   life.require(life.digest(target)==op.data['inputs'][str(a.pack)],'Installed selected archive changed')
   if getattr(a,'bliss_qualification',None):
    selected_archive=a.profile/'shaderpacks'/state['pack']
    selected_pin=summary['private_artifacts']['candidate_sha256'] if state['pack']==private_identity['generated'] else private_identity['original_sha256']
    life.require(life.digest(selected_archive)==selected_pin,'Qualification selected archive changed before timing')
   op.data['runtime_binding']=state;op.save()
   control.send('focus');control.wait(1)
   if getattr(a,'qualification_client_heap',None):
    summary.setdefault('qualification_memory',{})[label]={'before':bliss_qualification.memory_observation(state['client_pid'])}
   control.send('window_start',label=label,comparison=a.arm,repeat=out.name,
    arm=('FEATURE' if label.startswith('candidate') or label=='public' else 'MASTER_ON_NO_REQUESTS' if label.startswith('control') or c02 else 'MASTER_OFF') if a.suite=='public-smoke' else {'absent':'ABSENT','master-off':'MASTER_OFF','empty':'MASTER_ON_NO_REQUESTS','cpu2':'FEATURE'}[a.arm],recording=recording,calibration_delay_ns=delay,qualification=bool(getattr(a,'bliss_qualification',None)))
   # No metadata tree walk during a timing window. Cheap free/log checks remain.
   control.wait(a.seconds,full=False)
   value=control.send('window_stop')['window']
   if getattr(a,'qualification_client_heap',None):
    memory=summary['qualification_memory'][label]
    memory['after']=bliss_qualification.memory_observation(state['client_pid'])
    memory['delta']=bliss_qualification.memory_delta(memory['before'],memory['after'])
   if getattr(a,'bliss_qualification',None):life.require(life.digest(selected_archive)==selected_pin,'Qualification selected archive changed during timing')
   life.capture_complete(op,label,value,recording=recording)
   if getattr(a,'standard_test',False):iteration.arm_metrics(out/'windows'/label,label)
   if getattr(a,'bliss_qualification',None):
    summary.setdefault('qualification_capture_reconciliation',{})[label]=bliss_qualification.validate_capture(out/'windows'/label,label,seconds=a.seconds,delay_ns=delay,expected_pack=state['pack'],expected_heap_bytes=int(a.qualification_client_heap[:-1])*1024*1024 if getattr(a,'qualification_client_heap',None) else None)
   guard.check();summary['windows'].append(value)
   summary['host_telemetry'][label]['after']=host_observation()
   save()
  def analyze(label,kind,labels):
   if any(x is None for x in (a.minimum_benefit_ms,a.maximum_drift_ms,a.maximum_p95_regression_ms)):
    summary['analyses'].append({'label':label,'verdict':'UNTESTED','reason':'No predeclared practical criterion; raw metrics only'});return
   request={'output':str(out/(label+'-decision.json')),'comparison_id':label,'control_kind':kind,
    'control_class':'unchanged-no-reload','calibration':'within-session instrument diagnostic; chronological32-frame blocks; no cross-session gain claim',
    'minimum_benefit_ms':a.minimum_benefit_ms,'maximum_drift_ms':a.maximum_drift_ms,'maximum_p95_regression_ms':a.maximum_p95_regression_ms,
    'criterion_reason':'Explicit CLI criterion frozen before control capture; not a universal optimization gate',
    'brackets':[{'repeat_id':out.name,'before':str(out/'windows'/labels[i]),'candidate':str(out/'windows'/labels[i+1]),'return':str(out/'windows'/labels[i+2])} for i in range(0,len(labels),3)]}
   path=out/(label+'-request.json');life.atomic_json(path,request);summary.setdefault('analysis_requests',[]).append(str(path))
  try:
   guard.check();life.atomic_json(out/'preflight.json',admission);life.atomic_json(out/'plan.json',plan)
   if getattr(a,'standard_test',False):
    detail_start=time.monotonic();iteration.prepare_profile(op)
    summary.setdefault('cost_details_seconds',{})['profile_prepare']=time.monotonic()-detail_start
   resource_contract=iteration.resource_contract(a.profile) if getattr(a,'standard_test',False) and not getattr(a,'native_startup_check',False) else None
   if g10:summary['private_artifacts']=iteration.stage_g10(op,a.pack,a.pack_options,private_identity)
   if g02:summary['private_artifacts']=(photon_native_mips if photon_g02 else native_mips).stage(op,a.pack,a.pack_options,private_identity)
   if bliss:summary['private_artifacts']=(bliss_qualification if getattr(a,'bliss_qualification',None) else bliss_consumer).stage(op,a.pack,a.pack_options,private_identity)
   if g08:summary['private_artifacts']=cloud_bound.stage(op,a.pack,a.pack_options,private_identity)
   if g05:summary['private_artifacts']=fixed16.stage(op,a.pack,a.pack_options,private_identity)
   if a.untimed_diagnostic=='g08-reuse':summary['g08_probe_artifact']=g08_probe.stage(op,a.pack_options,a.g08_probe_identity)
   # Existing serialized admission includes all workloads and reserves this
   # operation's declared growth. No APFS clone discount or policy relaxation.
   if getattr(a,'cpu_placement',False):
    topology={}
    for directory in sorted(Path('/sys/devices/system/cpu/cpufreq').glob('policy*')):
     topology[directory.name]={name:(directory/name).read_text().strip() for name in ('related_cpus','cpuinfo_max_freq') if (directory/name).is_file()}
    summary['cpu_placement_topology']={'cpufreq_policies':topology,'scope':'Read-only topology; CPU capacities/frequencies are not per-frame CPU residence or normalized work.'};save()
   with storage.storage_lock():storage.admit(a.profile,life.LIMITS['profile_growth_bytes'])
   # Freeze actual harness/input identities once. Current build binding was checked in preflight.
   inputs=[a.pack,ROOT/'dependencies/runtime.lock.json',Path(__file__),ROOT/'scripts/benchmark_iteration.py',ROOT/'scripts/experiment_lifecycle.py',ROOT/'scripts/run_storage.py',ROOT/'scripts/dev_client.py',ROOT/'scripts/check_experiment_plan.py']
   if identity:inputs.append(Path(identity['recipe_path']))
   if g02_public:inputs += [Path(g02_public['recipe_path']),Path(mip_contrast.__file__),Path(mip_family.__file__)]
   if g08:inputs += [Path(name) for name in private_identity['producers']]+[Path(cloud_bound.__file__),Path(cloud_bound.private_off.__file__)]
   if g10 or g02 or g05 or bliss:inputs += [Path(name) for name in private_identity['producers']]
   if c02:inputs += [Path(bloom_vrs.__file__),Path(bloom_vrs.bsl.__file__)]
   if g02:inputs += [Path(native_mips.__file__)]
   if photon_g02:inputs += [Path(photon_native_mips.__file__)]
   if g05:inputs += [Path(fixed16.__file__)]
   if a.scene=='natural-cave':inputs += [Path(c01_scene.__file__)]
   if a.scene=='nether':inputs += [Path(nether_scene.__file__)]
   if a.untimed_diagnostic in ('g02-family','selected-work'):inputs += [Path(mip_family.__file__)]
   if a.untimed_diagnostic=='retained-copies':inputs += [Path(copy_inventory.__file__)]
   if a.quality_motion:inputs += [ROOT/'scripts/benchmark_quality_motion.py']
   if a.untimed_diagnostic=='cpu-admission-cost':inputs += [Path(cpu_cost.__file__)]
   if getattr(a,'bliss_albedo_domain',False):inputs += [ROOT/'scripts/benchmark_bliss_albedo_domain.py']
   if a.untimed_diagnostic=='g08-reuse':inputs += [Path(g08_probe.__file__),Path(g08_probe.producer.__file__),Path(g08_probe.private_off.__file__),g08_probe.BASE]
   if a.untimed_diagnostic=='c01-primitives':inputs += [Path(c01_witness.__file__)]
   if a.untimed_diagnostic=='g02-family' or g02_public:inputs += [Path(mip_assessment.__file__)]
   if a.untimed_diagnostic=='c01-cost':inputs += [Path(c01_cost.__file__)]
   inputs += [Path(gl_context.__file__),Path(performance_stack.__file__)]
   if getattr(a,'performance_stack','minimal')!='minimal':
    inputs += [ROOT/'dependencies/performance-mods.lock.json']+[p for p,_ in performance_stack.artifacts(ROOT,a.performance_stack)]
   if plan.get('pack_options'):inputs.append(Path(plan['pack_options']))
   # Retain the actual validated source/classes/resources map as one compact
   # frozen evidence file instead of copying worlds or generated source trees.
   build_receipt=dev.LOGS/'built.json'
   if build_receipt.is_file():
    (out/'build-binding.json').write_bytes(build_receipt.read_bytes());op.pin([out/'build-binding.json'])
   op.data['inputs']={str(p):life.digest(p) for p in inputs};op.save()
   session=out/'session';session.mkdir()
   life.atomic_json(out/'control.json',{'request_id':'initial_prepare','action':'prepare'})
   dev.write_json(session/'owner.json',{'status':'pending','plan':plan,'clients':{},
    'stop_command':[sys.executable,str(ROOT/'scripts/dev_client.py'),'stop','--session',str(session)]})
   log=(out/'supervisor.log').open('x')
   dev.validate_built()
   life.require(all(life.digest(p)==sha for p,sha in op.data['inputs'].items()),'Experiment inputs changed before launch')
   proc=subprocess.Popen([sys.executable,str(Path(__file__).resolve()),'_supervise-operation','--output',str(out)],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
   table=dev.process_table();life.require(proc.pid in table,'Supervisor process identity unavailable; retain ownership')
   op.data['processes']['supervisor']=dev.identity(table[proc.pid]);op.save()
   life.atomic_json(out/'launch-authorized.json',{'operation':op.data['id']})
   initial=out/'receipts/initial_prepare.json'
   def initial_receipt():
    try:return json.loads(initial.read_text())
    except (FileNotFoundError,json.JSONDecodeError):return {}
   while not initial_receipt() or initial_receipt().get('status')=='PENDING':
    owner=dev.read_published_json(session/'owner.json')
    if owner.get('status') in {'startup-failed','startup-timeout','exited'}:raise RuntimeError('Client startup failed: '+owner.get('error',owner['status']))
    control.wait(.5,full=False)
   op.reload()  # Supervisor prepare checkpoint is authoritative; no concurrent manifest writer after startup.
   receipt=json.loads(initial.read_text());life.require(receipt.get('success') is True and receipt.get('request_id')=='initial_prepare','Initial preparation failed or mismatched')
   op.data['initial_runtime_settling']=settle_initial_runtime(control,op)
   op.acknowledge_mutation('initial-runtime-prepare')
   op.data['state']='running';op.save()
   costs['preparation_startup']=time.monotonic()-clock;phase=time.monotonic()
   if a.suite=='public-smoke' or getattr(a,'native_startup_check',False):
    native_options=None
    control.send('c01_natural_open' if a.scene=='natural-cave' else 'native_terrain_open' if a.scene=='natural-terrain' else 'open_saved_authored')
    until=time.monotonic()+90
    while not control.send('status')['state'].get('world_loaded'):
     life.require(time.monotonic()<until,'Saved world did not open');control.wait(.5)
    if a.scene=='natural-cave':control.send('c01_natural_spawn')
    elif a.scene=='natural-terrain':summary['natural_terrain']=control.send('native_terrain_spawn')
    else:control.send('pose_saved_authored',index=a.saved_pose)
   else:control.send('open_coast');control.ready();control.send('pose')
   control.ready();control.send('video',width=a.width,height=a.height,fps_limit=260,vsync=False);control.ready()
   if sys.platform=='darwin':
    focus=subprocess.run([sys.executable,str(ROOT/'scripts/dev_client_ui.py'),'--session',str(session),'focus'],capture_output=True,text=True,timeout=20)
    (out/'focus.log').write_text(focus.stdout+focus.stderr);life.require(focus.returncode==0,'Owned focus failed')
   else:control.send('focus')
   options={'distance':12}
   if a.arm!='absent':options.update(master=a.arm!='master-off',features=['quad_command_compaction','shadow_admission_cache'] if a.arm=='cpu2' else [])
   if a.dh:
    control.send('configure',distance=12,dh_mode=a.dh_mode);control.ready();control.wait(2)
    life.require(control.send('status')['state'].get('dh_mode')==a.dh_mode,'DH mode did not acknowledge')
   control.send('configure',**options);control.ready();control.send('focus');control.wait(a.warmup)
   state=control.send('status')['state'];life.runtime_ready(state,plan,dev.read_published_json(session/'owner.json'))
   summary['gl_context']=gl_context.observe(getattr(a,'gl_context','native'),state,expected_renderer=getattr(a,'expected_renderer',None))
   summary['performance_stack']=performance_stack.observe(ROOT,getattr(a,'performance_stack','minimal'),state);save()
   if resource_contract is not None:
    resource_baseline=iteration.resource_selection(state,resource_contract)
    summary['resource_contract']=resource_contract | {'actual_ids':resource_baseline}
   if getattr(a,'qualification_client_heap',None):bliss_qualification.require_heap(state,int(a.qualification_client_heap[:-1])*1024*1024)
   summary['ready']=state;costs['world_and_initial_warmup']=time.monotonic()-phase;save()
   if identity:
    native_options=iteration.original_option_snapshot(state,expected_pack=plan['pack_name'],family=identity['family'])
    summary['native_option_snapshot']={'pack':state['pack'],'family':identity['family'],'values':native_options,
                                     'source':'Observed original Off state before the comparison sequence'};save()
   if getattr(a,'standard_test',False) and translucent_scene(a):
    life.require(identity and identity['family']=='makeup-ultra-fast-shaders' and a.saved_pose==9,
                 'G04 standard fixture requires pinned MakeUp and saved pose9')
    summary['feature_scene']=control.send('prepare_feature_scene',feature='translucent_fastpath')['feature_scene'];save()
    until=time.monotonic()+30
    while not control.send('status')['state'].get('standard_feature_scene_ready'):
     life.require(time.monotonic()<until,'G04 fixture not visible to client; restore entire save');control.wait(.5)
    control.ready()
   if a.scene=='natural-cave':
    census=control.send('c01_natural_scan')['natural_sites'];life.require(len(census.get('sites',[]))>a.c01_site,'Prepared loaded chunks contain no requested native cave site')
    selected=control.send('c01_natural_site',index=a.c01_site,direction=a.c01_direction)['natural_site'];control.ready();control.wait(a.warmup)
    summary['natural_cave']={'census':census,'selected':selected,'direction':a.c01_direction,'native_seed':8675309,'world':'ShaderLOD-Smoke','scope':'Prepared native natural world; loaded-chunk census only, no constructed occluders; spectator camera and whole-save restoration'};save()
   if a.c01_find_opportunity:
    summary['c01_opportunity_search']=c01_scene.find(control,summary['natural_cave']['census'],private_identity);control.wait(a.warmup);save()
   if a.scene=='nether':
    summary['nether_scene']=nether_scene.enter(control);control.wait(a.warmup);save()
    if a.candidate=='n02-photon-pbr-resources':
     chosen=private_identity['qualified_original'];pin=private_identity['original_sha256']
     control.send('diagnostic_pack',pack=chosen,sha256=pin,dimension=private_identity['required_dimension'])
     state=photon_pbr.private_off.clear(control,chosen,pin)
     photon_pbr.activation(state,private_identity,expected_pack=chosen)
     summary['photon_pbr_bootstrap']={'unscored_bootstrap_options':plan['requested_options'],
      'qualified_options':private_identity['native_options'],'qualified_pack':chosen,
      'qualified_state':state,'scope':'Base native366 only for startup and Nether entry. Full native PBR/POM and AA activated before conditioning and every qualifying arm.'};save()

   if g08:
    summary['g08_native_bound_diagnostic']=cloud_bound.diagnose(control,private_identity,summary['private_artifacts'],original=plan['pack_name']);save()
   if a.untimed_diagnostic in ('g02-family','selected-work') or g02_public:
    summary['mip_family_activation']=mip_family.capture(control);save()
   if a.conditioning:
    phase=time.monotonic();control.wait(a.conditioning);costs['rendered_conditioning']=time.monotonic()-phase;save()
   if getattr(a,'native_startup_check',False):
    state=control.send('status')['state']
    life.require(state.get('main_mod') is False and state.get('world_loaded') is True and state.get('fallback') is False and state.get('saved_authored_ready') is True,'Native startup absent-main readiness not qualified')
    summary['native_startup_check']={'state':state,'scope':'Authentic original native startup with ShaderLOD absent in the backed-up authored save; no optimization or timing conclusion.'};save()
    if getattr(a,'native_baseline',False):
     for i in range(3):
      control.ready();control.wait(a.warmup);phase=time.monotonic();window('native_'+str(i));costs['native_'+str(i)+'_measurement']=time.monotonic()-phase
     summary['native_graph_export']=control.send('graph_dump')['graph_export'];save()
   elif a.suite=='public-smoke':
    sequence=[('native',False),('candidate',True),('return',False)]
    if getattr(a,'comparison_order',None):sequence=iteration.comparison_sequence(a.comparison_order)
    if getattr(a,'bliss_qualification',None):sequence=bliss_qualification.sequence(a.qualification_order)
    if g02_public:
     sequence=[('native',False),('candidate',True),('public',True),('return',False)]
     summary['g02_contrast_contract']={'order':[x[0] for x in sequence],'public_identity':g02_public,'scope':'Finite same-process native/private/public/return; descriptive additional public arm, not ordinary three-arm protocol'}
    if a.public_hot:sequence=[(label+'_'+str(i),label=='candidate') for i in range(2) for label in ('native','control','candidate','return')]
    if a.native_null:sequence=[('native_'+str(i),False) for i in range(4)]+[('candidate_observation',True),('return_observation',False)]
    for label,active in sequence:
     phase=time.monotonic()
     if a.candidate in ('n04-solas-lean-alpha','n04-sildur-lean-alpha'):control.send(private_alpha_action,arm=False)
     if c01:control.send('c01_bounds',mode='off')
     if g05:control.send('depth_hierarchy_cost',enabled=False)
     if g10 or g02 or g05 or g08:
      native_name=private_identity['qualified_original'] if a.candidate=='n02-photon-pbr-resources' else plan['pack_name']
      context={'dimension':private_identity['required_dimension']} if a.candidate=='n02-photon-pbr-resources' else {}
      control.send('diagnostic_pack',pack=native_name,sha256=private_identity['original_sha256'],**context)
     if not bliss or getattr(a,'bliss_qualification',None):control.send('configure',features=selected_features(a) if active and (not private_identity or identity is not None) else [])
     if not bliss:
      applied=control.send('settings_apply',enabled=(active or label.startswith('control')) and (not private_identity or identity is not None),preset='PERFORMANCE')
      life.require(applied.get('success') is True,'Public settings Apply failed')
     state=control.ready() if bliss else ready_after_apply(control)
     if c02:
      summary.setdefault('c02_capabilities',control.send('vrs_status')['vrs']);save()
      life.require(summary['c02_capabilities'].get('hardware_available') is True,'C02 hardware/context unavailable: '+str(summary['c02_capabilities'].get('missing')))
      control.send('configure',master=True,features=[])
      control.send('vrs_request',enabled=active)
      state=control.ready();bloom_vrs.activation(state,private_identity,plan['pack_name'])
     if bliss:bliss_qualification.require_off(state)
     if g08:
      chosen=private_identity['generated'] if active else plan['pack_name']
      pin=summary['private_artifacts']['artifacts']['lean']['sha256'] if active else private_identity['original_sha256']
      if active:control.send('diagnostic_pack',pack=chosen,sha256=pin)
      state=cloud_bound.private_off.clear(control,chosen,pin)
      cloud_bound.activation(state,private_identity,active=active,expected_pack=chosen)
      if active:summary['g08_native_bound_sampling']=cloud_bound.sampling(control,pin,private_identity);save()
     if g10:
      chosen=private_identity['generated'] if active else plan['pack_name']
      pin=summary['private_artifacts']['lean_sha256'] if active else private_identity['original_sha256']
      if active:control.send('diagnostic_pack',pack=chosen,sha256=pin)
      control.send('configure',master=False,features=[])
      state=control.ready()
      iteration.g10_activation(state,private_identity,active=active,expected_pack=chosen)
     if g05:
      chosen=private_identity['generated'] if active else plan['pack_name']
      pin=summary['private_artifacts']['lean_sha256'] if active else private_identity['original_sha256']
      if active:control.send('diagnostic_pack',pack=chosen,sha256=pin)
      control.send('configure',master=False,features=[]);state=control.ready()
      fixed16.activation(state,private_identity,active=active,expected_pack=chosen)
      control.send('depth_hierarchy_cost',enabled=active);control.wait(1)
     if bliss:
      if active and getattr(a,'bliss_zero_lean',False):bliss_zero_lean.require_native_domain(summary.get(bliss_observations_key,{}))
      chosen=private_identity['generated'] if active else private_identity['qualified_original'] if a.candidate=='n02-photon-pbr-resources' else plan['pack_name']
      pin=summary['private_artifacts']['candidate_sha256'] if active else private_identity['original_sha256']
      selection={'pack':chosen,'sha256':pin,'dimension':private_identity.get('required_dimension','minecraft:overworld')}
      packet=mip_family.capture(control,settled=PRIVATE_CANDIDATES[a.candidate][2],selection=selection)
      state=bliss_consumer.private_off.clear(control,chosen,pin)
      bliss_consumer.activation(state,private_identity,expected_pack=chosen)
      if PRIVATE_CANDIDATES[a.candidate][2]:
       image_path=Path(packet['actual']['settled_screenshot'])
       if not image_path.is_absolute():image_path=a.profile/image_path
       life.require(image_path.parent.resolve()==(a.profile/'screenshots').resolve() and image_path.name.startswith('probe-ao-settled-'),
                    'Settled AO screenshot outside owned profile')
       from PIL import Image
       until=time.monotonic()+10
       while True:
        try:
         with Image.open(image_path) as image:image.verify()
         break
        except (OSError,SyntaxError):
         life.require(time.monotonic()<until,'Settled AO image missing/incomplete');control.wait(.1)
       saved_image=out/(label+'-ao-settled.png');shutil.copy2(image_path,saved_image);op.pin([saved_image])
       life.require(life.digest(image_path)==life.digest(saved_image),'Settled AO image changed during copy')
       image_path.unlink();packet['settled_screenshot_evidence']=str(saved_image)
      summary.setdefault(bliss_observations_key,{})[label]=packet;save()
      if getattr(a,'bliss_qualification',None):bliss_qualification.source_work(packet,active=active,mode=a.bliss_qualification)
      else:bliss_consumer.source_work(packet,active=active)
      if getattr(a,'bliss_albedo_domain',False):
       import benchmark_bliss_albedo_domain as albedo_domain
       summary.setdefault('bliss_albedo_domain',{})[label]=albedo_domain.validate(packet);save()
      if a.candidate=='n04-bliss-sky':summary.setdefault('bliss_ao_depth_census',{})[label]=bliss_ao.depth_census(packet);save()
      if a.candidate in ('n04-solas-lean-alpha','n04-sildur-lean-alpha') and active:
       summary[private_alpha_prefix+'_alpha_source_approval']=private_alpha_consumer.arm(control,summary[bliss_observations_key],private_identity,pin);save()
      state=control.ready();bliss_consumer.activation(state,private_identity,expected_pack=chosen)
     if g02:
      if label=='public':
       state=mip_contrast.activate_public(control,g02_public,private_identity,original=plan['pack_name'])
       summary['g02_contrast_public_sha256']=mip_contrast.preserve_public(op,a.profile,g02_public);save()
      else:
       chosen=private_identity['generated'] if active else plan['pack_name']
       pin=summary['private_artifacts']['candidate_sha256'] if active else private_identity['original_sha256']
       if active:control.send('diagnostic_pack',pack=chosen,sha256=pin)
       if photon_g02:
        state=photon_native_mips.private_off.clear(control,chosen,pin)
        photon_native_mips.activation(state,private_identity,expected_pack=chosen)
       else:
        control.send('configure',master=False,features=[])
        state=control.ready();native_mips.activation(state,private_identity,expected_pack=chosen)
       if active:
        archive=a.profile/'shaderpacks'/chosen;saved=out/'selected-candidate.zip'
        shutil.copy2(archive,saved)
        life.require(life.digest(archive)==pin and life.digest(saved)==pin,'G02 actual candidate archive changed')
        op.pin([saved])

     if identity:
      # Family selection may repopulate requests. Explicitly acknowledge the
      # singleton/empty requests after selection, through the ordinary path.
      control.send('configure',features=selected_features(a) if active else [])
      state=control.ready()
      life.require(native_options is not None,'Observed original options must be captured before any public arm')
      options=iteration.activation(state,feature=selected_features(a),enabled=active,
       expected_pack=identity['generated'] if active and not (cpu_features(a) or source_bsl) else plan['pack_name'],family=identity['family'],native_options=native_options,
       compile_control=None if cpu_features(a) or source_bsl else candidate_compile_contract(identity,selected_features(a)),program_markers={} if cpu_features(a) or source_bsl else identity.get('program_markers',{}))
      if source_bsl:
       life.require(options==native_options,'Source-only BSL route changed native options')
       if active:life.require(state['installed'].get('family_early_alpha',{}).get('post_sample',{}).get('active') is True,'BSL native source lowering unavailable: '+str(state['installed'].get('family_early_alpha',{}).get('post_sample_status')))
      if cpu_features(a):
       life.require(options==native_options,'CPU route changed the exact native option map')
       cpu_identity=control.send('cpu_graph_identity')['cpu_graph_identity']
       prior=list(summary.get('cpu_graph_identity',{}).values())
       summary.setdefault('cpu_reload_deltas',{})[label]=cpu_identity['reloads']-prior[-1]['reloads'] if prior else 0
       summary.setdefault('cpu_graph_identity',{})[label]=cpu_identity;save()
     if c01:iteration.c01_activation(state,private_identity,expected_pack=identity['generated'] if identity and active else plan['pack_name'],features=selected_features(a) if identity and active else None)
     if not (g10 or g02 or g05 or g08 or bliss):life.require(state.get('installed',{}).get('family') and state.get('effective_options'), 'Public adapter/options evidence missing')
     if resource_contract is not None:iteration.resource_selection(state,resource_contract,baseline=resource_baseline)
     if summary.get('feature_scene'):
      life.require(state.get('standard_feature_scene_ready') is True,'Matched feature scene changed before arm')
     if active and (not private_identity or identity is not None):
      life.require((state['pack']==plan['pack_name']) if cpu_features(a) or source_bsl else (state['pack']!=plan['pack_name']), 'Candidate archive selection differs from planned route')
      life.require(state['installed'].get('master') is True and all(state['installed'].get('flags',{}).get(feature)=='true' for feature in selected_features(a)), 'Requested public candidate is not active')
      if identity and not cpu_features(a):
       archive=a.profile/'shaderpacks'/(plan['pack_name'] if source_bsl else identity['generated']);saved=out/'selected-candidate.zip'
       shutil.copy2(archive,saved)
       life.require(life.digest(archive)==life.digest(saved),'Actual candidate archive changed while preserving evidence')
       op.pin([saved]);summary['selected_features']=selected_features(a)
       summary['public_identity']=identity|{'actual_selected_sha256':life.digest(saved),'actual_selected_name':state['pack'],'archive_role':'unchanged original with source lowering' if source_bsl else 'generated adapter'}
       if not source_bsl:summary['public_identity']['actual_generated_sha256']=life.digest(saved)
     elif not label.startswith('control') and not (g10 or g02 or g05 or g08 or bliss):life.require(state['pack']==plan['pack_name'],'Native control did not return to original pack')
     op.data['runtime_plan']=dict(plan,pack_name=state['pack'])
     if a.candidate=='n02-photon-pbr-resources':op.data['runtime_plan']['requested_options']=private_identity['native_options']
     op.save()
     if c01 and active:
      control.send('c01_bounds',mode=private_identity['mode'],prefix=private_identity['prefix'],group=private_identity['group'],frames=private_identity['frames'],delay=private_identity['delay'])
     control.wait(a.warmup);costs[label+'_activation_warmup']=time.monotonic()-phase
     if c01:
      c01_before=control.send('c01_bounds')['c01_bounds']
      summary.setdefault('private_work',{})[label]={'before':c01_before};summary['private_identity']=private_identity;save()
      iteration.c01_work(c01_before,active=active,mode=private_identity['mode'])
     if g10:
      g10_before=iteration.g10_observation(control);summary.setdefault('private_work',{})[label]={'before':g10_before};summary['private_identity']=private_identity;save()
      iteration.g10_draw(g10_before,active=active and private_identity.get('compile_mode','3')=='3',expected_source='composite1')
     if a.with_g10 or a.g10_control:
      bloom_role='composite2' if active and 'dead_frame_copy' in selected_features(a) else 'composite1'
      bloom_before=iteration.g10_observation(control);iteration.g10_draw(bloom_before,active=active and a.with_g10,expected_source=bloom_role)
      summary.setdefault('combined_g10',{})[label]={'before':bloom_before,'expected_source':bloom_role};save()
     if getattr(a,'features',None):
      if 'ao_attenuation' in selected_features(a):life.require(state.get('installed',{}).get('g06_selected_source_skip') is active,'Combined G06 selected source did not match arm')
      if active and 'dead_frame_copy' in selected_features(a):
       receipt=iteration.untimed_work(control,feature='dead_frame_copy',family=identity['family'])
       iteration.combination_copy_work(receipt,active=True)
       summary['combination_activation']={'copy':receipt,'g06_selected_source_skip':'ao_attenuation' in selected_features(a),'scope':'Observed copy calls and requested source markers; no fragment-count or motion proof.'};save()
     if 'translucent_fastpath' in selected_features(a):
      life.require(state.get('installed',{}).get('g04_selected_source_fastpath') is active,'G04 selected source did not match arm')
     if getattr(a,'standard_test',False) and 'early_alpha' in selected_features(a) and active and identity['family'] not in iteration.CROSS_ALPHA_FAMILIES:
      summary['early_alpha_activation']=iteration.untimed_work(control,feature='early_alpha',family=identity['family']);save()
      early_alpha_before=control.send('status')['state'];iteration.early_alpha_work(early_alpha_before,activation=summary['early_alpha_activation'])
     capture=control.send('capture_saved_authored');image_path=Path(capture['capture_requested'])
     if not image_path.is_absolute():image_path=a.profile/image_path
     image_path=storage.safe_path(image_path)
     life.require(image_path.is_relative_to(a.profile/'screenshots'),'Rendered PNG outside owned profile')
     from PIL import Image
     until=time.monotonic()+10
     while True:
      try:
       with Image.open(image_path) as image:image.verify()
       break
      except (OSError,SyntaxError):
       life.require(time.monotonic()<until,'Rendered PNG missing/incomplete');control.wait(.1)
     shutil.copy2(image_path,out/(label+'.png'));op.pin([out/(label+'.png')])
     life.require(life.digest(image_path)==life.digest(out/(label+'.png')),'Rendered PNG changed during evidence copy')
     image_path.unlink()
     summary.setdefault('public_states',{})[label]=state
     if a.native_null:
      summary.setdefault('graph_exports',{})[label]=control.send('graph_dump')['graph_export']
     phase=time.monotonic()
     if g05:
      g05_before=control.send('depth_hierarchy_status')['depth_hierarchy'];fixed16.progress(g05_before,active=active)
      summary.setdefault('private_work',{})[label]={'before':g05_before};save();phase=time.monotonic()
     if a.untimed_diagnostic=='g09-functional':
      g09_before=control.send('g09_prefix_work')['g09_work'];summary.setdefault('g09_private_work',{})[label]={'before':g09_before};save();iteration.g09_functional_work(g09_before,active=active)
     if a.candidate in ('n04-solas-lean-alpha','n04-sildur-lean-alpha'):
      solas_alpha_before=control.send(private_alpha_action)[private_alpha_action];private_alpha_consumer.progress(solas_alpha_before,active=active)
     if cpu_features(a):cpu_before=control.send('cpu_graph_identity')
     cross_alpha=identity and identity['family'] in iteration.CROSS_ALPHA_FAMILIES and 'early_alpha' in selected_features(a)
     if cross_alpha:
      if identity['family']=='bsl-shaders':
       summary.setdefault('bsl_post_sample_observations',{})[label]=iteration.untimed_work(control,feature='early_alpha',family='bsl-shaders');save()
       control.wait(a.warmup)  # Same settling after disarming the observer in every BSL G03 arm.
      cross_alpha_before=control.send('status')['state']
      iteration.family_early_alpha_work(cross_alpha_before,family=identity['family'],active=active)
     if c02:
      c02_before=control.send('vrs_status')['vrs'];summary.setdefault('c02_work',{})[label]={'before':c02_before};save()
      bloom_vrs.progress(c02_before,active=active)
     if not label.endswith('_observation'):window(label,delay=25_000_000 if getattr(a,'bliss_qualification',None)=='clock' and active else 0)
     if identity and identity['family'] in iteration.AO_SCALE_FAMILIES and 'ao_render_scale' in selected_features(a):
      directory=out/'windows'/label
      receipt=iteration.ao_scale_work(json.loads((directory/'after.json').read_text()),family=identity['family'],active=active,
                                      before=json.loads((directory/'before.json').read_text()))
      summary.setdefault('ao_scale_work',{})[label]=receipt;save()
     if identity and identity['family'] in iteration.DEPTH_REUSE_FAMILIES and 'ao_depth_reuse' in selected_features(a):
      directory=out/'windows'/label
      receipt=iteration.depth_reuse_work(json.loads((directory/'after.json').read_text()),family=identity['family'],active=active,
                                         before=json.loads((directory/'before.json').read_text()))
      summary.setdefault('depth_reuse_work',{})[label]=receipt;save()
     if identity and identity['family'] in iteration.REFLECTION_REUSE_FAMILIES and 'reflection_next_event_refinement' in selected_features(a):
      directory=out/'windows'/label
      receipt=iteration.reflection_reuse_work(json.loads((directory/'after.json').read_text()),family=identity['family'],active=active,
                                              before=json.loads((directory/'before.json').read_text()))
      summary.setdefault('reflection_reuse_work',{})[label]=receipt;save()
     if getattr(a,'render_cpu',-1)>=0:
      affinity_window(summary['windows'][-1],a.render_cpu)
     costs[label+'_measurement']=time.monotonic()-phase;save()
     if c02:
      c02_after=control.send('vrs_status')['vrs'];summary['c02_work'][label]['after']=c02_after;save()
      bloom_vrs.progress(c02_after,active=active,before=c02_before)
      bloom_vrs.activation(control.send('status')['state'],private_identity,plan['pack_name'])
     if a.candidate in ('n04-solas-lean-alpha','n04-sildur-lean-alpha'):
      solas_alpha_after=control.send(private_alpha_action)[private_alpha_action];private_alpha_consumer.progress(solas_alpha_after,active=active,before=solas_alpha_before)
      summary.setdefault(private_alpha_prefix+'_alpha_work',{})[label]={'before':solas_alpha_before,'after':solas_alpha_after};save()
     if cross_alpha:
      cross_alpha_after=control.send('status')['state']
      summary.setdefault('family_early_alpha_work',{})[label]={'before':cross_alpha_before['installed']['family_early_alpha'],
       'after':iteration.family_early_alpha_work(cross_alpha_after,family=identity['family'],active=active,before=cross_alpha_before)};save()
     if cpu_features(a):
      cpu_state=control.send('status')['state']
      cpu_after=control.send('cpu_graph_identity')
      life.require(cpu_before['cpu_graph_identity']==cpu_after['cpu_graph_identity'],
                   'CPU timing changed native pipeline/program identity')
      summary.setdefault('cpu_work',{})[label]={'installed':cpu_state.get('installed',{}),'before':cpu_before,'after':cpu_after}
      save()
      iteration.cpu_work(cpu_state,features=selected_features(a),active=active,before=cpu_before,after=cpu_after)
     if g05:
      g05_after=control.send('depth_hierarchy_status')['depth_hierarchy'];summary['private_work'][label]['after']=g05_after;save()
      fixed16.progress(g05_after,active=active,before=g05_before)
     if a.untimed_diagnostic=='g09-functional':
      g09_after=control.send('g09_prefix_work')['g09_work'];summary['g09_private_work'][label]['after']=g09_after;save();iteration.g09_functional_work(g09_after,active=active,before=g09_before)
     if a.untimed_diagnostic=='cpu-admission-cost' and active:
      summary['cpu_admission_cost']=cpu_cost.capture(control);save()
     if a.untimed_diagnostic in ('g02-family','selected-work') or g02_public:
      summary.setdefault('mip_family_observations',{})[label]=mip_family.capture(control);save()
     if a.untimed_diagnostic=='retained-copies':
      summary.setdefault('retained_copy_inventory',{})[label]=copy_inventory.capture(control);save()
     if a.untimed_diagnostic in ('g01-graph','g09-storage','g09-prefix','g09-functional'):
      summary.setdefault('ownership_diagnostics',{})[label]=iteration.ownership_diagnostic(control,a.untimed_diagnostic,active=active);save()
     if getattr(a,'standard_test',False) and 'early_alpha' in selected_features(a) and active and identity['family'] not in iteration.CROSS_ALPHA_FAMILIES:
      early_alpha_after=control.send('status')['state'];iteration.early_alpha_work(early_alpha_after,before=early_alpha_before,activation=iteration.untimed_work(control,feature='early_alpha',family=identity['family']))
      summary['early_alpha_route']=iteration.early_alpha_work(early_alpha_after,activation=summary['early_alpha_activation']);summary['early_alpha_progress']={'before':early_alpha_before['installed']['early_alpha_draw_counters'],'after':early_alpha_after['installed']['early_alpha_draw_counters']};save()
     if c01:
      c01_after=control.send('c01_bounds')['c01_bounds'];summary['private_work'][label]['after']=c01_after;save()
      iteration.c01_work(c01_after,active=active,before=c01_before,mode=private_identity['mode'])
      if active and a.untimed_diagnostic=='c01-cost':
       summary['c01_cost_diagnostic']=c01_cost.capture(control,group=private_identity['group']);save()
     if g10:
      g10_after=iteration.g10_observation(control);summary['private_work'][label]['after']=g10_after;save()
      iteration.g10_draw(g10_after,active=active and private_identity.get('compile_mode','3')=='3',before=g10_before,expected_source='composite1')
     if a.with_g10 or a.g10_control:
      bloom_after=iteration.g10_observation(control);summary['combined_g10'][label]['after']=bloom_after;save()
      iteration.g10_draw(bloom_after,active=active and a.with_g10,before=bloom_before,expected_source=bloom_role)
     if g02 and not photon_g02:
      summary.setdefault('untimed_work_witnesses',{})[label]=iteration.untimed_work(control,feature='dead_mip_chain',family='bsl-shaders');save()
      native_mips.witness(summary['untimed_work_witnesses'][label],active=active)
     if getattr(a,'standard_test',False) and identity:
      observed={feature:iteration.untimed_work(control,feature=feature,family=identity['family']) for feature in selected_features(a)}
      observed={feature:value for feature,value in observed.items() if value is not None}
      if observed:
       witness=next(iter(observed.values())) if len(selected_features(a))==1 else {'features':observed}
       summary.setdefault('untimed_work_witnesses',{})[label]=witness;save()
      if getattr(a,'features',None) and 'dead_frame_copy' in selected_features(a):
       iteration.combination_copy_work(observed['dead_frame_copy'],active=active)
     if getattr(a,'audit_graph',False):
      phase=time.monotonic();summary.setdefault('audit_graph_exports',{})[label]=control.send('graph_dump')['graph_export'];costs[label+'_untimed_graph']=time.monotonic()-phase;save()
     if a.quality_motion:
      import benchmark_quality_motion as quality_motion
      phase=time.monotonic();summary.setdefault('quality_motion',{})[label]=quality_motion.capture(control,out,label)
      costs[label+'_untimed_quality']=time.monotonic()-phase
      op.pin([out/('quality-'+label)/name for name in ('manifest.json','request.json')]);save()
    if getattr(a,'comparison_order',None):
     if a.candidate=='native-control':
      control.send('diagnostic_pack',pack=plan['pack_name'],sha256=private_identity['original_sha256'])
      state=native_control.private_off.clear(control,plan['pack_name'],private_identity['original_sha256'])
     else:
      if c02:control.send('vrs_request',enabled=False)
      restored_apply=control.send('settings_apply',enabled=False,preset='PERFORMANCE')
      life.require(restored_apply.get('success') is True,'Balanced final native Apply failed')
      control.send('configure',master=False,features=[]);state=control.ready()
     life.require(state.get('pack')==plan['pack_name'] and state.get('installed',{}).get('master') is False
                  and not any(v in (True,'true') for v in state.get('installed',{}).get('flags',{}).values()),
                  'Balanced final native restoration differs')
     summary['comparison_native_return']=state;save()
    if c01 and a.scene=='natural-cave':
     summary['c01_native_visibility']=c01_scene.visibility(control,private_identity);save()
    if a.untimed_diagnostic=='c01-primitives':
     summary['c01_primitive_witness']=c01_witness.capture(control,private_identity);save()
    if a.untimed_diagnostic=='g02-family' or g02_public:
     summary['mip_family_assessment']=mip_assessment.assess(summary['mip_family_observations'],family=identity['family'] if identity else private_identity.get('family','bsl-shaders'),states=summary['public_states']);save()
    if photon_g02:summary['photon_native_pass_assessment']=photon_native_mips.assess(summary['mip_family_observations']);save()
    if g02_public:summary['g02_public_contrast']=mip_contrast.comparison(out);save()
    if a.untimed_diagnostic=='g08-reuse':
     summary['g08_reuse_diagnostic']=g08_probe.capture(control,a.g08_probe_identity,summary['g08_probe_artifact'],original=plan['pack_name']);save()
    if g05:
     summary['g05_diagnostics']=fixed16.diagnose(control,private_identity,original=plan['pack_name']);save()
    if g10 and private_identity.get('compile_mode','3')=='3':
     control.send('diagnostic_pack',pack=private_identity['work'],sha256=summary['private_artifacts']['work_sha256']);control.send('configure',master=False,features=[])
     work_state=control.ready();control.wait(2)
     iteration.g10_activation(work_state,private_identity,active=True,expected_pack=private_identity['work'],mode='4')
     control.send('g10_bloom_status',observe=True)
     try:
      control.wait(.1)
      summary['g10_work_images']=[];phases=set()
      for sample in range(48):
       work=control.send('g10_bloom_work',sha256=summary['private_artifacts']['work_sha256'])['g10_work']
       reduced=iteration.g10_image(work);phases.add(reduced['producer_phase'])
       summary['g10_work_images'].append(work|{'reduced':reduced});save()
       if len(phases)==16:break
       control.wait(.1)
      summary['g10_work_phase_coverage']={'observed':sorted(phases),'complete':len(phases)==16,'scope':'Finite48-sample cap; missing phases are unmeasured, never assumed native.'};save()
     finally:control.send('g10_bloom_status',observe=False)
     control.send('diagnostic_pack',pack=plan['pack_name'],sha256=private_identity['original_sha256']);control.ready()
     control.send('configure',master=False,features=[]);final_native=control.ready()
     iteration.g10_activation(final_native,private_identity,active=False,expected_pack=plan['pack_name'])
   elif a.suite in ('scenarios','cancel'):
    control.send('close_coast');control.send('benchmark_start',distance=12,diagnostic=a.diagnostic);began=time.monotonic();cancelled=False
    while True:
     result=control.send('benchmark_status')['benchmark'];summary['benchmark']=result;save()
     if a.suite=='cancel' and not cancelled and time.monotonic()-began>10:control.send('benchmark_cancel');cancelled=True
     if not result['active'] and not result['restoration_pending']:
      path=storage.safe_path(result.get('result_path',''))
      life.require(path.is_relative_to(a.profile) and path.is_file(),'Completed scenario result missing/outside profile')
      data=path.read_bytes();(out/'menu-result.json').write_bytes(data);manifest=json.loads(data)['manifest']
      if a.suite=='scenarios':
       summary['verified_requested_dh']=verify_requested_dh(manifest,a.dh,a.dh_mode)
       summary['scenario_evaluation']=scenario_evaluation(manifest,DIAGNOSTIC_CASES if a.diagnostic else FAST_CASES)
       summary['scenario_complete']=scenario_timing_complete(manifest,DIAGNOSTIC_CASES if a.diagnostic else FAST_CASES)
      op.pin([out/'menu-result.json']);break
     control.wait(1)
   else:
    nulls=[]
    for i in range(6 if a.suite=='controls' else 3):label='null_'+str(i);window(label);nulls.append(label)
    if a.suite=='controls':
     analyze('null','KNOWN_NULL',nulls);positive=[]
     for i in range(6):label='known_'+str(i);window(label,15_000_000 if i%3!=1 else 0);positive.append(label)
     analyze('known-effect','KNOWN_EFFECT',positive)
     for i,record in enumerate([False,True,False]):window('observer_'+str(i),recording=record)
     if a.reload_controls:
      for i in range(3):control.send('reload');control.ready();control.send('focus');control.wait(8);window('reload_'+str(i))
   if bliss:
    assessment=(bliss_qualification.assess(summary[bliss_observations_key],order=a.qualification_order,mode=a.bliss_qualification)
                if getattr(a,'bliss_qualification',None) else bliss_consumer.assess(summary[bliss_observations_key]))
    if getattr(a,'bliss_qualification',None):summary['qualification_graph_assessment']=bliss_qualification.assess_graph(summary[bliss_observations_key],order=a.qualification_order)
    summary['photon_pbr_source_assessment' if a.candidate=='n02-photon-pbr-resources' else 'photon_copy_source_assessment' if a.candidate=='n03-photon-cloud-copy' else 'bliss_source_assessment']=assessment
   summary['execution_completed']=True
  except (Exception,KeyboardInterrupt) as error:
   summary.update(execution_completed=False,error=str(error) or type(error).__name__)
  finally:
   control.recovery=True;phase=time.monotonic()
   if a.quality_motion and proc is not None:
    try:
     control.send('quality_stop');deadline=time.monotonic()+45
     while not control.send('quality_status')['quality']['finalized']:
      life.require(time.monotonic()<deadline,'Quality image leases not drained before restoration');time.sleep(.2)
    except Exception as error:summary.setdefault('control_recovery_errors',[]).append(str(error))
   if c02 and proc is not None:
    try:control.send('vrs_request',enabled=False)
    except Exception as error:summary.setdefault('control_recovery_errors',[]).append(str(error))
   if a.candidate in ('n04-solas-lean-alpha','n04-sildur-lean-alpha') and proc is not None:
    try:control.send(private_alpha_action,arm=False)
    except Exception as error:summary.setdefault('control_recovery_errors',[]).append(str(error))
   if a.untimed_diagnostic=='cpu-admission-cost' and proc is not None:
    try:control.send('cpu_admission_cost',stop=True)
    except Exception as error:summary.setdefault('control_recovery_errors',[]).append(str(error))
   if a.untimed_diagnostic=='c01-cost' and proc is not None:
    try:control.send('c01_cost',observe=False)
    except Exception as error:summary.setdefault('control_recovery_errors',[]).append(str(error))
   if g05 and proc is not None:
    try:control.send('depth_hierarchy_cost',enabled=False)
    except Exception as error:summary.setdefault('control_recovery_errors',[]).append(str(error))
   if c01 and proc is not None:
    try:control.send('c01_bounds',mode='off')
    except Exception as error:summary.setdefault('control_recovery_errors',[]).append(str(error))
   if proc is not None:
    if a.suite=='public-smoke' or getattr(a,'native_startup_check',False):
     try:control.send('close_saved_authored')
     except Exception as error:summary.setdefault('control_recovery_errors',[]).append(str(error))
    for action in (('cancel','benchmark_cancel') if a.arm!='absent' else ('cancel',)):
     try:control.send(action)
     except Exception as error:summary.setdefault('control_recovery_errors',[]).append(str(error))
    if a.suite in ('scenarios','cancel'):
     try:
      deadline=time.monotonic()+25
      while time.monotonic()<deadline:
       benchmark=control.send('benchmark_status')['benchmark']
       if not benchmark['active'] and not benchmark['restoration_pending']:break
       time.sleep(.2)
      else:raise TimeoutError('Benchmark restoration still pending')
     except Exception as error:summary['benchmark_recovery_error']=str(error)
    try:
     if a.suite!='public-smoke' and not getattr(a,'native_startup_check',False):control.send('restore')
    except Exception as error:summary.setdefault('control_recovery_errors',[]).append(str(error))
   # Interrupted commands are uncertain. Release/restoration refuses rather than guessing.
   try:
    release_runtime(op,proc)
    if getattr(a,'standard_test',False):
     detail_start=time.monotonic();iteration.finish_profile(op)
     summary.setdefault('cost_details_seconds',{})['profile_restore_and_reset']=time.monotonic()-detail_start
    summary['configuration_restored']=True
    op.data['state']='complete' if summary.get('execution_completed') else 'failed-restored';op.save()
    summary['backup_disposal']=op.dispose_backups()
    if getattr(a,'standard_test',False):summary['world_backup_disposal']=iteration.dispose_worlds(op)
   except Exception as error:summary['release_error']=str(error);op.data['state']='recovery-required';op.save()
   if log:log.close()
   summary['clients_remaining']=len(dev.clients(dev.process_table()));summary['finished']=life.now();summary['storage']=guard.last
   costs['restoration_release']=time.monotonic()-phase;costs['total']=time.monotonic()-clock;summary['command_total_seconds']=costs['total']+summary['workflow_setup_seconds']
   save()
  if op.data.get('release_verified'):
   for request in summary.get('analysis_requests',[]):
    guard.check();result=analyze_request(Path(request),out/(Path(request).stem+'-analysis'));summary['analyses'].append(result);save()
  if a.suite=='scenarios':summary['evaluation']=summary.get('scenario_evaluation',scenario_evaluation({},DIAGNOSTIC_CASES if a.diagnostic else FAST_CASES))
  elif a.suite=='controls':summary['evaluation']=comparison_evaluation(summary['analyses'],['null','known-effect']);summary['instrument_checks_passed']=instrument_controls_passed([d for d in summary['analyses'] if 'controlKind' in d])
  elif getattr(a,'native_baseline',False):summary['evaluation']=iteration.baseline_evaluation(out,execution_completed=summary.get('execution_completed'),restored=summary.get('configuration_restored'))
  elif getattr(a,'native_startup_check',False):summary['evaluation']={'execution':'COMPLETE' if summary.get('execution_completed') else 'FAILED','completion':'COMPLETE' if summary.get('execution_completed') and summary.get('configuration_restored') else 'INCOMPLETE','validity':'VALID' if summary.get('execution_completed') and summary.get('configuration_restored') else 'INVALID','improvement':'NOT_EVALUATED','limits':'Native startup diagnostic with ShaderLOD absent; no optimization comparison.'}
  elif getattr(a,'bliss_qualification',None):summary['evaluation']=bliss_qualification.evaluate(out,order=a.qualification_order,mode=a.bliss_qualification,execution_completed=summary.get('execution_completed'),restored=summary.get('configuration_restored'))
  elif getattr(a,'functional_check',False):summary['evaluation']=iteration.functional_evaluation(execution_completed=summary.get('execution_completed'),restored=summary.get('configuration_restored'))
  elif getattr(a,'comparison_order',None):summary['evaluation']=iteration.balanced_evaluation(out,order=a.comparison_order,execution_completed=summary.get('execution_completed'),restored=summary.get('configuration_restored'))
  elif getattr(a,'standard_test',False):summary['evaluation']=iteration.evaluate(out,execution_completed=summary.get('execution_completed'),restored=summary.get('configuration_restored'))
  else:summary['evaluation']={'completion':'NOT_EVALUATED','validity':'UNTESTED','improvement':'NOT_EVALUATED'}
  if c02:summary['evaluation']['quality_scope']=bloom_vrs.QUALITY
  if g10 and private_identity.get('compile_mode')=='0':
   summary['evaluation']['improvement']='KNOWN_NULL_CONTROL';summary['evaluation']['control_scope']='Native-equivalent private pack, not an optimization comparison. Descriptive arm metrics retained.'
  if a.candidate=='native-control':
   summary['evaluation']['improvement']='KNOWN_NULL_CONTROL';summary['evaluation']['control_scope']='Byte-identical original ZIP alias, exact native options and Master Off. Apparent differences measure this session and protocol noise, not optimization benefit.'
  if a.untimed_diagnostic in ('g09-storage','g09-prefix'):
   summary['evaluation']['improvement']='DIAGNOSTIC_NATIVE_HOLD'
   summary['evaluation']['control_scope']='G09 storage observation retires its owner with Active0; timings cannot establish prefix optimization performance.'
  summary['evaluation']['execution']='COMPLETE' if summary.get('execution_completed') else 'FAILED'
  summary['workflow_counters']={'reused_apply_readiness':getattr(control,'reused_apply_readiness',0)}
  summary['workflow']=workflow.outcome(summary)
  save();op.pin([out/'summary.json',out/'plan.json',out/'preflight.json'])
  if op.data['state'] in life.TERMINAL:op.data['terminal_storage']=storage.scoped_usage([out]);op.save()
  print(json.dumps({'state':op.data['state'],'execution_completed':summary.get('execution_completed'),'restored':summary.get('configuration_restored'),'error':summary.get('error'),'release_error':summary.get('release_error'),
                    'output':str(out),'workflow':summary['workflow'],'evaluation':summary['evaluation']}))
  if not summary.get('execution_completed') or not summary.get('configuration_restored') or summary['clients_remaining'] or summary.get('benchmark_recovery_error'):return 1
  if a.suite=='controls' and not summary.get('instrument_checks_passed'):return 2
  if a.suite=='scenarios' and not summary.get('scenario_complete'):return 2
  if getattr(a,'standard_test',False) and summary['evaluation']['validity']!='VALID':return 2
  return 0


def main(argv=None):
 argv=list(sys.argv[1:] if argv is None else argv)
 if argv and argv[0] in ('profile','profile-plan','profile-report'):
  import cost_map
  return cost_map.command(argv)
 if argv and argv[0] in ('discover','report'):return workflow.command(argv,sys.modules[__name__])
 planning=bool(argv and argv[0]=='plan')
 if planning:argv=['test',*argv[1:]]
 if argv and argv[0]=='evaluate':return evaluate_main(argv[1:])
 if argv and argv[0]=='recover':return recover_main(argv[1:])
 if argv and argv[0]=='_supervise-operation':
  p=argparse.ArgumentParser();p.add_argument('--output',required=True,type=Path)
  supervise_operation(p.parse_args(argv[1:]).output);return 0
 native_baseline=bool(argv and argv[0]=='baseline')
 native_startup=bool(argv and argv[0]=='native-startup') or native_baseline
 functional_check=bool(argv and argv[0]=='check')
 standard=bool(argv and argv[0]=='test') or native_startup or functional_check
 if standard:argv=argv[1:]
 try:a=arguments(argv)
 except (ValueError,OSError) as error:
  print(json.dumps({'state':'input-error','error':str(error),'launched':False}));return 1
 a.standard_test=standard;a.native_startup_check=native_startup;a.native_baseline=native_baseline;a.functional_check=functional_check
 if standard:
  supplied={flag.split('=',1)[0] for flag in argv if flag.startswith('--')}
  if supplied & {'--suite','--arm','--seconds','--world','--warmup','--budget','--conditioning','--public-hot','--native-null','--reload-controls','--diagnostic'}:
   print(json.dumps({'error':'test owns schedule, timing and recovery budget; change the maintained protocol rather than overriding it'}));return 1
  a.run=True;a.suite='public-smoke';a.arm='master-off';a.world='ShaderLOD-Benchmark-overworld-3-flat'
  a.seconds=20
  a.warmup=5;a.conditioning=120;a.budget=900
  if getattr(a,'bliss_qualification',None):a.seconds=60;a.warmup=20;a.conditioning=180;a.budget=1800
  if functional_check:
   if a.bliss_qualification:
    print(json.dumps({'error':'Frozen qualification cannot use functional check'}));return 1
   a.seconds=3;a.conditioning=0;a.budget=900
  if native_startup:a.suite='smoke';a.arm='absent';a.seconds=3;a.conditioning=0;a.budget=450
  if native_baseline:a.seconds=20;a.conditioning=120;a.budget=900
 setup_started=time.monotonic();build_seconds=0
 result=preflight(a)
 if planning:
  result['schedule']={'conditioning_seconds':a.conditioning,'seconds_per_window':a.seconds,'warmup_seconds':a.warmup,'budget_seconds':a.budget}
  result['launched']=False;result['build_needed']=any(e['check'] in {'existing_build_binding','compiled_control_hooks'} for e in result['errors'])
  print(json.dumps(result,indent=2));return 1 if result['errors'] else 0
 if standard and result['errors'] and all(error['check'] in {'existing_build_binding','compiled_control_hooks'} for error in result['errors']):
  try:
   print(json.dumps({'stage':'build','reason':'Source/output receipt or compiled control hooks are stale; rebuilding before launch'}),file=sys.stderr)
   with dev.runtime_lock():
    life.require(not dev.clients(dev.process_table()),'Runtime lane occupied; build withheld')
    build_started=time.monotonic();dev.compile_current(timeout=180);build_seconds=time.monotonic()-build_started
   result=preflight(a)
  except (ValueError,OSError,subprocess.TimeoutExpired) as error:
   print(json.dumps({'state':'preflight-failed','error':str(error),'launched':False}));return 1
 if result['errors'] or not a.run:
  print(json.dumps(result,indent=2));return 1 if result['errors'] else 0
 result['workflow_setup_seconds']=time.monotonic()-setup_started
 result['workflow_build_seconds']=build_seconds
 return run_operation(a,result)


if __name__=='__main__':
 import platform
 if (sys.argv[1:] and sys.argv[1] in ('test','check','plan','native-startup','baseline')
     and platform.system()=='Linux' and platform.machine() in ('aarch64','arm64')
     and os.environ.get('SHADERLOD_ARM64_RUNTIME_READY')!='1'):
  os.execv(sys.executable,[sys.executable,str(ROOT/'scripts/linux_arm64_runtime.py'),'benchmark',*sys.argv[1:]])
 sys.exit(main())
