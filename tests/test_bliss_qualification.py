import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_bliss_qualification as q
import run_benchmark as runner


class QualificationTest(unittest.TestCase):
    def test_private_off_admission_does_not_accept_enabled_or_requested_routes(self):
        q.require_off({'installed':{'master':False,'flags':{}}})
        for state in ({},{'installed':{'master':True,'flags':{}}},
                      {'installed':{'master':False,'flags':{'anything':'true'}}}):
            with self.assertRaises(ValueError):q.require_off(state)
    def packet(self, active=False):
        p=q.ao.producer
        return {'actual':{'sources':[{'name':'composite1','vertex_source':'void main(){}',
            'fragment_source':(p.REPLACEMENT if active else p.ANCHOR).decode()+'\nif(swappedDepth>=1.0) SSAO_SSS=vec2(1.0,0.0);',
            'parsed_mips':[],'parsed_flips':{},'parsed_draw_buffers':[0]}],
            'events':[{'kind':'completed_quad','frame':i,'name':'composite1'} for i in (1,2)]}}

    def fixture(self, directory):
        vector=[{'frameId':i+1,'epoch':1,'totalFrameNs':None if i==0 else 1_000_000_000,'renderWallNs':100_000_000} for i in range(4)]
        guard={'valid':True,'completed_render_frames':4,'excluded_initial_unavailable_interval':True,
               'statistics':{'count':3},'wall_ns':4_000_000_000,'started_ns':0,'stopped_ns':4_000_000_000,
               'completed_render_ends_ns':[500_000_000,1_500_000_000,2_500_000_000,3_500_000_000]}
        result={'manifest':{'windows':[{'id':'q0_A','validity':'VALID','samples':vector,'statistics':{'count':3},'observerCost':{'calibration_delay_ns':'0'},'work':[{'signal':'calibration_spin_iterations','executed':0}]}]}}
        before={'world_frames':0,'render_frames':0,'qualification_observers':{'test':False},'effective_options':json.loads(q.ao.producer.PROFILE.read_text()),'dimension':'minecraft:overworld','width':2560,'height':1600,'vsync':False,'fps_limit':260}
        after=before|{'world_frames':4,'render_frames':4}
        rows={'guard':guard,'result':result,'before':before,'after':after}
        for name,data in rows.items():(directory/(name+'.json')).write_text(json.dumps(data))
        return rows

    def test_balanced_roles_and_whole_source_proof(self):
        for order in q.ORDERS:
            seq=q.sequence(order)
            self.assertEqual(2,sum(active for _,active in seq))
            for mode in ('null','clock','ao'):
                packets={label:self.packet(active and mode=='ao') for label,active in seq}
                q.assess(packets,order=order,mode=mode)
                packets[seq[-1][0]]['actual']['sources'][0]['vertex_source']='bad'
                with self.assertRaisesRegex(ValueError,'another attached'):q.assess(packets,order=order,mode=mode)
        with self.assertRaises(ValueError):q.sequence('AABB')

    def test_raw_count_epoch_hooks_duration_and_observers(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            pristine=self.fixture(root)
            self.assertEqual(4,q.validate_capture(root,'q0_A',seconds=2)['sample_count'])
            changes=[('guard',lambda r:r.update(completed_render_frames=5)),
                     ('result',lambda r:r['manifest']['windows'][0]['samples'][2].update(frameId=2)),
                     ('result',lambda r:r['manifest']['windows'][0]['samples'][2].update(epoch=2)),
                     ('after',lambda r:r.update(world_frames=3)),
                     ('after',lambda r:r.update(render_frames=3)),
                     ('guard',lambda r:r.update(completed_render_ends_ns=[1,2,2,3])),
                     ('before',lambda r:r.update(qualification_observers={'armed':True})),
                     ('guard',lambda r:r.update(wall_ns=4_000_000_001)),
                     ('result',lambda r:[s.update(totalFrameNs=1_000_000_000_000) for s in r['manifest']['windows'][0]['samples'][1:]]),
                     ('guard',lambda r:r['completed_render_ends_ns'].__setitem__(3,3_510_000_000))]
            for name,mutate in changes:
                rows=copy.deepcopy(pristine);mutate(rows[name])
                for key,value in rows.items():(root/(key+'.json')).write_text(json.dumps(value))
                with self.assertRaises(ValueError):q.validate_capture(root,'q0_A',seconds=2)
            self.fixture(root)
            with self.assertRaisesRegex(ValueError,'truncated'):q.validate_capture(root,'q0_A',seconds=60)

    def test_injected_clock_fixture_requires_delay_and_matching_endpoints(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);rows=self.fixture(root)
            row=rows['result']['manifest']['windows'][0]
            row['observerCost']['calibration_delay_ns']='25000000'
            row['work'][0]['executed']=100
            (root/'result.json').write_text(json.dumps(rows['result']))
            q.validate_capture(root,'q0_A',seconds=2,delay_ns=25_000_000)
            row['samples'][-1]['renderWallNs']=20_000_000
            (root/'result.json').write_text(json.dumps(rows['result']))
            with self.assertRaises(ValueError):q.validate_capture(root,'q0_A',seconds=2,delay_ns=25_000_000)

    def test_null_alias_is_exact_original_and_owned_staging(self):
        pack=q.ao.producer.ROOT/'dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip'
        options=q.ao.producer.ROOT/'dev/experiments/new-optimization-goals-20261102/bliss-native463.txt'
        identity=q.identity(pack,options,'null')
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'shaderpacks').mkdir();target=root/'shaderpacks'/identity['generated']
            from types import SimpleNamespace
            op=SimpleNamespace(output=root,data={'profile':str(root),'configuration':{str(target):{'expected':None},str(target)+'.txt':{'expected':None}}},
                               begin_mutation=lambda _:None,acknowledge_mutation=lambda _:None,pin=lambda _:None)
            receipt=q.stage(op,pack,options,identity)
            self.assertEqual(pack.read_bytes(),target.read_bytes())
            self.assertEqual(options.read_bytes(),Path(str(target)+'.txt').read_bytes())
            self.assertEqual([],receipt['changed_members'])

    def test_cohort_requires_independent_balanced_blocks_and_calibration(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);runs=[]
            for pose in (0,7):
                for i in range(6):
                    run=root/f'{pose}-{i}';run.mkdir();runs.append(run)
                    evaluation={'validity':'VALID','mode':'null','order':q.ORDERS[i%2],
                                'reduction_percent':.1 if i%2 else -.1,
                                'same_arm_spread_percent':{'A':1,'B':1},'p95_A_ms':60,'p95_B_ms':60}
                    summary={'qualification':{'mode':'null','saved_pose':pose,'seconds':60,'warmup':20,'conditioning':180},
                             'evaluation':evaluation,'configuration_restored':True,'clients_remaining':0,
                             'public_states':{'q0':{'client_pid':1000+len(runs),'client_started_at':'now'}}}
                    (run/'summary.json').write_text(json.dumps(summary))
            calibration=q.cohort(runs,mode='null')
            self.assertEqual('CALIBRATED',calibration['decision'])
            with self.assertRaisesRegex(ValueError,'twice'):q.cohort(runs+[runs[0]],mode='null')
            for run in runs:
                data=json.loads((run/'summary.json').read_text());data['qualification']['mode']='ao'
                data['evaluation'].update(mode='ao',reduction_percent=7)
                (run/'summary.json').write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError,'calibrated'):q.cohort(runs,mode='ao')
            result=q.cohort(runs,mode='ao',calibration=calibration)
            self.assertTrue(all(r['decision']=='PERFORMANCE_QUALIFIED_SCOPED' for r in result['scenes'].values()))
            data=json.loads((runs[0]/'summary.json').read_text());data['evaluation']['p95_B_ms']=120
            (runs[0]/'summary.json').write_text(json.dumps(data))
            result=q.cohort(runs,mode='ao',calibration=calibration)
            self.assertEqual('NO_QUALIFIED_BENEFIT',result['scenes']['0']['decision'])
            for run in runs:
                data=json.loads((run/'summary.json').read_text())
                data['public_states']['q0']['heap_max_bytes']=1536*1024*1024
                (run/'summary.json').write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError,'AO runtime differs'):q.cohort(runs,mode='ao',calibration=calibration)
            data=json.loads((runs[0]/'summary.json').read_text());data['public_states']['q0']['heap_max_bytes']=2304*1024*1024
            (runs[0]/'summary.json').write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError,'mixes runtime'):q.cohort(runs,mode='ao',calibration=calibration)


    def test_effective_heap_is_measured_not_inferred_from_argument_strings(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);rows=self.fixture(root)
            with self.assertRaisesRegex(ValueError,'Effective JVM heap'):
                q.validate_capture(root,'q0_A',seconds=2,expected_heap_bytes=1536*1024*1024)
            for key in ('before','after'):
                rows[key]['heap_max_bytes']=1536*1024*1024
                rows[key]['heap_launch_arguments']=['-Xmx6G','-Xmx1536m']
                (root/(key+'.json')).write_text(json.dumps(rows[key]))
            q.validate_capture(root,'q0_A',seconds=2,expected_heap_bytes=1536*1024*1024)
            rows['after']['heap_max_bytes']=6*1024*1024*1024
            (root/'after.json').write_text(json.dumps(rows['after']))
            with self.assertRaisesRegex(ValueError,'Effective JVM heap'):
                q.validate_capture(root,'q0_A',seconds=2,expected_heap_bytes=1536*1024*1024)

    @unittest.skipUnless(sys.platform=='linux','Linux proc telemetry')
    def test_memory_backend_and_identity_counter_refusals(self):
        import os
        before=q.memory_observation(os.getpid());after=q.memory_observation(os.getpid())
        delta=q.memory_delta(before,after)
        self.assertGreater(before['memory']['MemTotal_kib'],0)
        self.assertEqual(os.sysconf('SC_PAGE_SIZE'),before['page_bytes'])
        self.assertGreater(delta['external_bracket_seconds'],0)
        bad=copy.deepcopy(after);bad['process']['start_ticks']+=1
        with self.assertRaisesRegex(ValueError,'process changed'):q.memory_delta(before,bad)
        bad=copy.deepcopy(after);bad['process']['major_faults']=before['process']['major_faults']-1
        with self.assertRaisesRegex(ValueError,'counters reset'):q.memory_delta(before,bad)
        bad=copy.deepcopy(after);bad['pressure']['memory']['some']['total']=before['pressure']['memory']['some']['total']-1
        with self.assertRaisesRegex(ValueError,'pressure counter reset'):q.memory_delta(before,bad)

    def test_cli_requires_explicit_qualification_roles(self):
        args=runner.arguments(['--pack','/tmp/p.zip','--output','/tmp/q','--bliss-qualification','null','--qualification-order','BAAB'])
        self.assertEqual('null',args.bliss_qualification)
        self.assertEqual('BAAB',args.qualification_order)


if __name__=='__main__':unittest.main()
