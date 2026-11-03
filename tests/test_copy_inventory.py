import sys
import unittest
from pathlib import Path
from unittest.mock import Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_copy_inventory as inventory

class CopyInventoryTest(unittest.TestCase):
    def record(self):
        return dict(finalPassesCompleted=5,finalPassesEntered=5,apiPinned=True,
                    metadataStatus='OK',graphChanges=1,saturated=False,
                    targets=[{'target':{'target':7},'executedCopies':5}],
                    executedSwapCopies=5,plannedSwapCopies=5)

    def test_counts_are_inventory_not_deletion_proof(self):
        self.assertEqual(1,inventory.validate(self.record())['executed_copies_per_frame'])

    def test_refuses_incomplete_metadata_and_graph_changes(self):
        for change in [dict(finalPassesCompleted=0),dict(graphChanges=2),dict(metadataStatus='ERROR'),
                       dict(targets=[]),dict(executedFallbackCopies=1),dict(streamMismatches=1),dict(apiPinned=False)]:
            with self.subTest(change=change),self.assertRaises(ValueError):
                inventory.validate(self.record()|change)

    def test_failed_observation_disarms(self):
        control=Mock();control.send.side_effect=[{},RuntimeError('lost status'),{}]
        with self.assertRaises(RuntimeError):inventory.capture(control)
        self.assertEqual({'enabled':False,'reset':False},control.send.call_args.kwargs)
