"""Scoped camera controls; no client, GPU, Java build or GLSL compilation."""
import json
import math
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'scripts'))
import prepare_shared_spatial_quality as quality


class SpatialQualityTests(unittest.TestCase):
    def test_actual_pinned_forest_inputs_and_every_route(self):
        scene = quality.runtime.inspect_scene(qualified=quality.runtime.qualify())
        self.assertEqual(len(scene['upstream_effective_options']), 366)
        for route in quality.ROUTES:
            request = quality.camera_request(scene['control'], route, 'fixture-'+route)
            self.assertEqual(request['waypoints'][0], quality.POSE | dict(seconds=0))
            self.assertEqual(request['waypoints'][-1], quality.POSE | dict(seconds=20))
            self.assertEqual(request['fps']*request['waypoints'][-1]['seconds']+1, 201)
            self.assertEqual(request['maximumInFlightImages'], 2)
            self.assertLess(len(json.dumps(request).encode()), 65536)
            total = 0
            for a,b in zip(request['waypoints'],request['waypoints'][1:]):
                dt = b['seconds']-a['seconds']
                distance = math.sqrt(sum((a[k]-b[k])**2 for k in ('x','y','z')))
                total += distance
                self.assertGreaterEqual(dt,.25)
                self.assertLessEqual(1.5*distance/dt,32)
                self.assertLessEqual(1.5*abs(a['yaw']-b['yaw'])/dt,90)
            self.assertLessEqual(total,512)

    def test_rejects_pose_weather_route_and_label_mutations(self):
        control = quality.POSE | dict(time=7000,weather='clear')
        for update in ({'x':256.6},{'x':True},{'time':12500},{'weather':'rain'}):
            with self.subTest(update=update):
                with self.assertRaisesRegex(ValueError,'control drift'):
                    quality.camera_request(control|update,'still','fixture')
        for route,label in [('invented','fixture'),('still','../escape'),('still','x'*65)]:
            with self.assertRaises(ValueError):quality.camera_request(control,route,label)

    def test_camera_command_has_no_workload_or_timing_activation(self):
        args = quality.command(ROOT/'run-workloads/fixture/forest/original',ROOT/'run-workloads/fixture/camera-request.json')
        self.assertIn('-PcameraQualityOnly=true',args)
        self.assertIn('-PcloudSamplerProbe=false',args)
        self.assertIn('-PincludeDh=false',args)
        for key in ('gpuTiming','cpuTiming','cpuLayerTiming','gpuPassTiming','benchmark','benchmarkOnly','workTrace'):
            self.assertIn('-P'+key+'=false',args)
        for key in ('measureWorkload','measureOnly','measurementSeconds','initialWarmup'):
            self.assertFalse(any(a.startswith('-P'+key+'=') for a in args))
        for task in ('compileJava','compileDevClientJava','processResources','processDevClientResources'):
            self.assertIn(task,args)

    def test_bad_route_rejected_before_copying(self):
        with mock.patch.object(quality.runtime,'prepare') as copy:
            with self.assertRaises(ValueError):quality.prepare(ROOT/'run-workloads/fixture','wrong')
            copy.assert_not_called()


if __name__ == '__main__':unittest.main()
