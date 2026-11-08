"""CPU-only stage-1 contract and adjacent pure timing acceptance tests.

Run: python3 -m unittest discover -s tests -p test_cost_map_contract.py -v
No Gradle client task, Minecraft classpath, native library or display is used.
"""
from pathlib import Path
import subprocess
import tempfile
import unittest

from pinned_tools import java_tool

ROOT = Path(__file__).resolve().parents[1]
TIMING = ROOT / 'src/devClient/java/com/pp/shaderlod/dev/timing'
TESTS = ROOT / 'src/devClientTest/java/com/pp/shaderlod/dev/timing'


class CostMapContractTest(unittest.TestCase):
    def test_contract_and_adjacent_pure_timing(self):
        sources = sorted((TIMING / 'costmap').glob('*.java'))
        sources += sorted((TESTS / 'costmap').glob('*.java'))
        for name in ('GpuSamplingPolicy', 'TimingStatistics'):
            sources += [TIMING / (name + '.java'), TESTS / (name + 'Test.java')]
        with tempfile.TemporaryDirectory(prefix='shaderlod-costmap-') as directory:
            subprocess.run([java_tool('javac'), '--release', '21', '-d', directory,
                            *map(str, sources)], check=True, timeout=60)
            for main in ('costmap.CostMapContractTest', 'GpuSamplingPolicyTest', 'TimingStatisticsTest'):
                with self.subTest(main=main):
                    subprocess.run([java_tool('java'), '-Djava.awt.headless=true', '-cp', directory,
                                    'com.pp.shaderlod.dev.timing.' + main], check=True, timeout=30)
