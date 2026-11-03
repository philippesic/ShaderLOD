import subprocess
import tempfile
import unittest
from pathlib import Path
from pinned_tools import java_tool

class DiagnosticArchiveIdentityTest(unittest.TestCase):
    def test_exact_original_exception_and_both_control_callers(self):
        root=Path(__file__).resolve().parents[1]
        source=root/'src/devClient/java/com/pp/shaderlod/dev/DiagnosticArchiveIdentity.java'
        fixture='''package com.pp.shaderlod.dev;
public class Fixture {
 public static void main(String[] args) {
  String name="Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip";
  String sha="f41db92acc585fe9ffe0cc124d6ecaea27cef9ded91129ebc2a8e60226947b2c";
  if(!DiagnosticArchiveIdentity.allows(name,sha))throw new AssertionError("native Bliss refused");
  if(!DiagnosticArchiveIdentity.allows("Private-native.zip",sha))throw new AssertionError("existing name refused");
  for(String invalid:new String[]{"../"+name,"/"+name,"sub/"+name,"evil(1).zip",name+".bak"})
   if(DiagnosticArchiveIdentity.allows(invalid,sha))throw new AssertionError(invalid);
  if(DiagnosticArchiveIdentity.allows(name,"a".repeat(64)))throw new AssertionError("wrong original pin");
  if(DiagnosticArchiveIdentity.allows("native.zip","bad"))throw new AssertionError("bad pin");
  String solas="Solas Shader V3.7b.zip",solasSha="b7dfe4c7353d677a92cc4c86b87bc0633f1014305d927ca6a482c303427bf825";
  if(!DiagnosticArchiveIdentity.allows(solas,solasSha))throw new AssertionError("native Solas refused");
  if(DiagnosticArchiveIdentity.allows(solas,sha)||DiagnosticArchiveIdentity.allows("../"+solas,solasSha)
     ||DiagnosticArchiveIdentity.allows("Other Solas.zip",solasSha))throw new AssertionError("Solas identity widened");
  String sildur="Sildur's Vibrant Shaders v2.02 Extreme.zip",sildurSha="824504bc937f9570277c0a809f5059ae4a5fe70c413cf4459b6ccff4dfed7ebe";
  if(!DiagnosticArchiveIdentity.allows(sildur,sildurSha))throw new AssertionError("native Sildur refused");
  if(DiagnosticArchiveIdentity.allows(sildur,sha)||DiagnosticArchiveIdentity.allows("../"+sildur,sildurSha))throw new AssertionError("Sildur identity widened");
 }
}'''
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp);f=p/'Fixture.java';f.write_text(fixture)
            subprocess.run([java_tool('javac'),'-d',tmp,str(source),str(f)],check=True,capture_output=True)
            subprocess.run([java_tool('java'),'-cp',tmp,'com.pp.shaderlod.dev.Fixture'],check=True,capture_output=True)
        controls=(root/'src/devClient/java/com/pp/shaderlod/dev/BenchmarkControlProbe.java').read_text()
        self.assertIn('DiagnosticArchiveIdentity.allows(name,expected)',controls)
        self.assertIn('DiagnosticArchiveIdentity.allows(name,pin)',controls)
