"""Actual maintained recipe/depth admission joins; no driver or speed evidence."""
import json
from pathlib import Path
import re
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from build_supported_pack_adapters import apply, read_archive, shader_source_digest


class ReimaginedDepthRecipeBindingTests(unittest.TestCase):
    def test_current_assembled_source_matches_exact_depth_admission_pin(self):
        manifest=json.loads((ROOT/'patches/adapters/complementary-reimagined.json').read_text())
        stock=read_archive(ROOT/'dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip')
        generated=apply(stock,manifest)
        reader=(ROOT/'src/main/java/com/pp/shaderlod/optimization/reimaginedprime/ReimaginedDepthPriming.java').read_text()
        pin=re.search(r'public static final String SOURCE="([a-f0-9]{64})"',reader).group(1)
        self.assertEqual(shader_source_digest(generated),pin)
        self.assertIn('!SOURCE.equals(com.pp.shaderlod.packs.SupportedPackCatalog.shaderSourceDigest(',reader)
        self.assertIn('PackSelectionService.verifyGeneratedRendererGraph(path,actual)',reader)
        self.assertIn('if(!profileMatch(actual))return false;',reader)
        # Mutating even the unrelated AO source remains outside the complete depth certificate.
        changed=dict(generated);changed['shaders/program/deferred1.glsl']+=b'\n// independent edit\n'
        self.assertNotEqual(shader_source_digest(changed),pin)



if __name__ == '__main__':unittest.main()
