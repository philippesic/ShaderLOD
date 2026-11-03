"""Actual Java Properties compatibility for original shader filenames."""
import sys,unittest,tempfile,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path[:0]=[str(ROOT/'scripts'),str(ROOT/'tests')]
import dev_client as dev
from pinned_tools import java_tool
class IrisPropertyTests(unittest.TestCase):
 def test_ascii_and_minecraft_json_remain_unchanged(self):
  self.assertEqual(dev.java_property_value('BSL_v10.1.8.zip'),'BSL_v10.1.8.zip')
  text='resourcePacks:["file/\\u00a7-native.zip"]\n'
  self.assertEqual(dev.properties(text)['resourcePacks'],'["file/\\u00a7-native.zip"]')
 def test_native_readiness_accepts_iris_display_formatting_only(self):
  text='Using shaderpack: AstraLex.zip\nShaderLOD landscape ready: side=LOD, modPresent=true\nShaderLOD inspection native fullscreen: true, monitor=1, framebuffer=2560x1600, target=2560x1600\n'
  self.assertTrue(all(dev.readiness(text,'§r§lAstra§4§lLex§r§l.zip','on').values()))
  self.assertFalse(dev.readiness(text,'Different.zip','on')['shader'])
 def test_actual_java_load_preserves_unicode_spaces_and_backslashes(self):
  value=' §r§lAstra Lex 🚀 \\native.zip'
  text=dev.replace_properties('enableShaders=false\n',{'shaderPack':dev.java_property_value(value),'enableShaders':'true'})
  self.assertTrue(text.isascii());self.assertEqual(dev.java_properties(text)['shaderPack'],value)
  with tempfile.TemporaryDirectory() as directory:
   p=Path(directory);source=p/'ReadIris.java';props=p/'iris.properties';props.write_bytes(text.encode('ascii'))
   source.write_text('import java.util.*;import java.nio.file.*;class ReadIris{public static void main(String[]a)throws Exception{var p=new Properties();try(var in=Files.newInputStream(Path.of(a[0]))){p.load(in);}System.out.print(p.getProperty("shaderPack"));}}')
   result=subprocess.run([java_tool('java'),str(source),str(props)],capture_output=True,text=True,timeout=30)
   self.assertEqual(result.returncode,0,result.stderr);self.assertEqual(result.stdout,value)
if __name__=='__main__':unittest.main()
