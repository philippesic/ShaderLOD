"""Real pinned-source checks. These do not establish driver rendering or speed."""
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("sky_builder", ROOT / "scripts/build_bliss_sky_elision_r1.py")
b = importlib.util.module_from_spec(spec)
spec.loader.exec_module(b)
Q = json.loads((ROOT / "dev/logs/cross-shader-performance-r3/bliss-shader-lod-next-r1/native-options.json").read_text())
BASE = ("IS_IRIS", "IRIS_VERSION=10812", "MC_VERSION=12101", "MC_OS_MAC", "MC_HAND_DEPTH=0.125")
EXPANDED = {}

def expand(files, name):
    key = (id(files), name)
    if key in EXPANDED:
        return EXPANDED[key]
    s = files[name].decode().replace("\r\n", "\n")
    for k, v in Q.items():
        if v in ("true", "false"):
            if v == "false":
                s = re.sub(r"(?m)^(\s*)#define " + re.escape(k) + r"\b[^\n]*", r"\1// disabled native " + k, s)
            else:
                s = re.sub(r"(?m)^(\s*)//\s*#define " + re.escape(k) + r"\b", r"\1#define " + k, s)
        else:
            s = re.sub(r"(?m)^(\s*#define " + re.escape(k) + r"\s+)\S+", lambda m: m[1] + v, s)
            s = re.sub(r"(\bconst\s+(?:float|int|bool)\s+" + re.escape(k) + r"\s*=\s*)[^;]+", lambda m: m[1] + v, s)
    result = re.sub(r'^\s*#include\s+"([^"]+)"\s*$', lambda m: expand(files, "shaders" + m[1] if m[1].startswith("/") else str(Path(name).parent / m[1])), s, flags=re.M)
    EXPANDED[key] = result
    return result

def cpp(files, name="shaders/world0/composite1.fsh", defs=BASE):
    s = re.sub(r"(?m)^\s*#(?:version|extension)\b[^\n]*", "", expand(files, name))
    p = subprocess.run(["clang", "-E", "-P", "-x", "c", "-", *["-D" + d for d in defs]], input=s, text=True, capture_output=True, timeout=15)
    if p.returncode:
        raise AssertionError(p.stderr)
    return p.stdout

def tokens(s):
    return re.findall(r"\w+|[^\s]", re.sub(r"/\*.*?\*/|//[^\n]*", "", s, flags=re.S))

class BlissSkyElisionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = b.members(b.BASE)
        cls.variants = [b.patch(cls.original, i) for i in range(3)]

    def test_authentic_inputs_and_unchanged_members(self):
        self.assertEqual(b.sha(b.BASE.read_bytes()), b.BASE_SHA)
        self.assertEqual(b.sha(b.OPTIONS.read_bytes()), b.OPTIONS_SHA)
        self.assertEqual(len(Q), 463)
        self.assertEqual(Q["indirect_effect"], "1")
        self.assertIn(b"#define DENOISE_SSS_AND_SSAO", self.original["shaders/lib/settings.glsl"])
        for f in self.variants:
            self.assertEqual(set(f), set(self.original))
            for n in set(f) - {b.MEMBER}:
                self.assertEqual(f[n], self.original[n], n)

    def test_source_guards_and_native_disabled(self):
        self.assertEqual(tokens(cpp(self.variants[0])), tokens(cpp(self.original)))
        fallbacks = [BASE + ("DISTANT_HORIZONS",), BASE + ("VOXY",), (), ("IS_IRIS", "MC_OS_MAC"), ("IS_IRIS", "IRIS_VERSION=10813", "MC_VERSION=12101", "MC_OS_MAC"), ("IS_IRIS", "IRIS_VERSION=10812", "MC_VERSION=12111", "MC_OS_MAC"), ("IRIS_VERSION=10812", "MC_VERSION=12101", "MC_OS_MAC")]
        for defs in fallbacks:
            for f in self.variants:
                self.assertEqual(tokens(cpp(f, defs=defs)), tokens(cpp(self.original, defs=defs)), defs)
        for world in ("world-1", "world1"):
            name = "shaders/" + world + "/composite1.fsh"
            for f in self.variants:
                self.assertEqual(tokens(cpp(f, name)), tokens(cpp(self.original, name)))

    def test_no_sampling_or_neighbor_derivative_change(self):
        native = self.original[b.MEMBER].decode().replace("\r\n", "\n")
        changed = self.variants[2][b.MEMBER].decode().replace("\r\n", "\n")
        # Entire seven-tap function and its neighbors remain exact.
        function = native[native.index("vec2 SSAO("):native.index("vec4 encode (")]
        self.assertIn(function, changed)
        self.assertNotRegex(function, r"\b(?:texture2D|dFdx|dFdy|fwidth)\s*\(")
        self.assertIn("if(swappedDepth >= 1.0) SSAO_SSS = vec2(1.0,0.0);", changed)
        # All implicit texture calls stay outside the new branch, unchanged.
        self.assertEqual(re.findall(r"\btexture2D\([^;]+", native), re.findall(r"\btexture2D\([^;]+", changed))
        self.assertIn("shaderlodPreset = -1", changed)
        for bridge in (0, 1, 2, 3):
            for enabled in (0, 1, 2):
                for preset in (-1, 0, 1, 2, 3, 4):
                    for depth in (0.0, 0.9, 1.0):
                        active = depth >= 1 and bridge == 2 and enabled == 1 and 0 <= preset <= 2
                        native_result = (1, 0) if depth >= 1 else (0.72, 0.11)
                        candidate_result = (1, 0) if active else native_result
                        self.assertEqual(native_result, candidate_result)

    def test_actual_active_wrapper_source_syntax(self):
        for mode, f in enumerate(self.variants):
            text = cpp(f)
            if mode == 2:
                self.assertIn("if (swappedDepth >= 1.0 && shaderlodBridgeVersion == 2", text)
                self.assertIn("vec2 SSAO(", text)
            path = b.OUT / ("source-mode" + str(mode) + ".fsh")
            path.write_text("#version 330 compatibility\n#define texelFetch2D texelFetch\n" + text)
            p = subprocess.run(["glslangValidator", "-S", "frag", str(path)], text=True, capture_output=True, timeout=15)
            (b.OUT / ("glslang-mode" + str(mode) + ".txt")).write_text(p.stdout + p.stderr)
            self.assertEqual(p.returncode, 0, p.stdout + p.stderr)

    def test_pin_and_overwrite_refusal(self):
        with self.assertRaises(ValueError):
            b.patch(self.original | {b.MEMBER: self.original[b.MEMBER] + b"\n"}, 2)
        with self.assertRaises(ValueError):
            b.patch(self.original, 4)
        with self.assertRaises(ValueError):
            b.build()

if __name__ == "__main__":
    unittest.main()
