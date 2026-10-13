"""Exercise the isolated BSL candidate through the real local Java selection boundary."""

from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
ARTIFACT = ROOT / 'dev/logs/deep-shader-investigation-20261012/bsl/candidate-r2'
BASE = ROOT / 'dev/upstream/other-pack-research/BSL_v10.1.8.zip'
RECIPE = 'patches/adapters/bsl-shaders.json'
CERT = 'ports/26.2/neoforge/src/main/java/com/pp/shaderlod/integration/bindingcompat/BslSourceCertificate.java'
CATALOG = 'src/main/java/com/pp/shaderlod/packs/SupportedPackCatalog.java'
JDK = ROOT / 'dev/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home/bin'
CLASSPATH = ROOT / 'dev/logs/concept-transfer-r1/bsl-neoforge-main/checks-classpath.json'

JAVA = r'''
import com.google.gson.*;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.packs.PackSelectionService;
import com.pp.shaderlod.integration.bindingcompat.BslSourceCertificate;
import com.pp.shaderlod.integration.bslquad.BslQuadEligibility;
import java.nio.file.*;
import java.security.MessageDigest;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.zip.*;

public class DeepBslConsumer {
 static void yes(boolean b,String message){if(!b)throw new AssertionError(message);}
 static JsonObject json(byte[] data){return JsonParser.parseString(new String(data)).getAsJsonObject();}
 static byte[] member(Path zip,String name)throws Exception {
   try(var archive=new ZipFile(zip.toFile());var stream=archive.getInputStream(archive.getEntry(name))) {
     return stream.readAllBytes();
   }
 }
 static void refused(PackSelectionService.Result r,String message){
   yes(r.status()==PackSelectionService.Status.REFUSED,message+": "+r);
 }
 static byte[] changedMarker(Path path)throws Exception {
   var bytes=new ByteArrayOutputStream();
   try(var archive=new ZipFile(path.toFile());var output=new ZipOutputStream(bytes)) {
     var entries=archive.entries();
     while(entries.hasMoreElements()) {
       var entry=entries.nextElement();
       var next=new ZipEntry(entry.getName());output.putNextEntry(next);
       byte[] body;
       try(var stream=archive.getInputStream(entry)){body=stream.readAllBytes();}
       if(entry.getName().equals("shaderlod-selection.json")) {
         var marker=json(body);marker.addProperty("adapterId","wrong");body=marker.toString().getBytes();
       }
       output.write(body);output.closeEntry();
     }
   }
   return bytes.toByteArray();
 }
 public static void main(String[] args)throws Exception {
   Path dir=Path.of(args[0]);String original="Original.zip";
   Path nativePath=dir.resolve(original), nativeOptions=dir.resolve(original+".txt");
   byte[] nativeBytes=Files.readAllBytes(nativePath), sidecar=Files.readAllBytes(nativeOptions);
   var nativeMap=BslQuadEligibility.nativeOptions();
   yes(BslSourceCertificate.validate(dir,original,original,nativeMap,false).eligible(),"native rejected");
   var service=new PackSelectionService();
   var balanced=service.apply(dir,original,true,LodPreset.BALANCED,Map.of(),true);
   yes(balanced.accepted()&&balanced.generatedArchive(),"Balanced did not create adapter: "+balanced);
   String generated=balanced.packFile();Path generatedPath=dir.resolve(generated);
   JsonObject marker=json(member(generatedPath,"shaderlod-selection.json"));
   Path statePath=dir.resolve(marker.get("stateFile").getAsString());
   JsonObject state=json(Files.readAllBytes(statePath));
   yes(marker.get("recipeSha256").getAsString().equals(args[1]),"marker recipe mismatch");
   yes(state.get("recipeSha256").getAsString().equals(args[1]),"state recipe mismatch");
   String actualHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(generatedPath)));
   yes(state.get("generatedSha256").getAsString().equals(actualHash),"generated ZIP hash mismatch");
   var generatedMap=BslSourceCertificate.generatedOptions();
   var certificate=BslSourceCertificate.validate(dir,generated,original,generatedMap,false);
   boolean admitted=certificate.eligible();
   yes(admitted==Boolean.parseBoolean(args[2]),"old/new certificate admission mismatch");
   if(!admitted){
     yes(certificate.reason().contains("Stale/tampered packaged recipe"),"old certificate failed for a different reason: "+certificate.reason());
     System.out.println("PASS old certificate refuses new recipe; native admitted");return;
   }
   yes(generatedMap.size()==388&&nativeMap.size()==384,"option count mismatch");
   var unchanged=service.apply(dir,generated,true,LodPreset.BALANCED,Map.of(),true);
   yes(unchanged.status()==PackSelectionService.Status.UNCHANGED,"restart mutated");
   var badOptions=new TreeMap<>(generatedMap);badOptions.put("TAA","false");
   yes(!BslSourceCertificate.validate(dir,generated,original,badOptions,false).eligible(),"map tamper admitted");
   Path recipe=Path.of(args[3]);byte[] goodRecipe=Files.readAllBytes(recipe);
   Files.write(recipe,new byte[]{32},StandardOpenOption.APPEND);
   yes(!BslSourceCertificate.validate(dir,generated,original,generatedMap,false).eligible(),"recipe tamper admitted");
   refused(service.apply(dir,generated,true,LodPreset.BALANCED,Map.of(),true),"recipe tamper service");
   Files.write(recipe,goodRecipe);
   byte[] goodState=Files.readAllBytes(statePath);state.addProperty("recipeSha256","bad");
   Files.writeString(statePath,state.toString());
   yes(!BslSourceCertificate.validate(dir,generated,original,generatedMap,false).eligible(),"state tamper admitted");
   refused(service.apply(dir,generated,true,LodPreset.BALANCED,Map.of(),true),"state tamper service");
   Files.write(statePath,goodState);
   byte[] goodArchive=Files.readAllBytes(generatedPath);
   Files.write(generatedPath,new byte[]{1},StandardOpenOption.APPEND);
   yes(!BslSourceCertificate.validate(dir,generated,original,generatedMap,false).eligible(),"archive tamper admitted");
   refused(service.apply(dir,generated,true,LodPreset.BALANCED,Map.of(),true),"archive tamper service");
   Files.write(generatedPath,goodArchive);
   Files.write(generatedPath,changedMarker(generatedPath));
   yes(!BslSourceCertificate.validate(dir,generated,original,generatedMap,false).eligible(),"marker tamper admitted");
   refused(service.apply(dir,generated,true,LodPreset.BALANCED,Map.of(),true),"marker tamper service");
   Files.write(generatedPath,goodArchive);
   Files.write(nativePath,new byte[]{1},StandardOpenOption.APPEND);
   yes(!BslSourceCertificate.validate(dir,generated,original,generatedMap,false).eligible(),"native tamper admitted");
   refused(service.apply(dir,generated,true,LodPreset.BALANCED,Map.of(),true),"native tamper service");
   Files.write(nativePath,nativeBytes);
   yes(BslSourceCertificate.validate(dir,generated,original,generatedMap,false).eligible(),"restored generated rejected");
   var quality=service.apply(dir,generated,true,LodPreset.QUALITY,Map.of(),true);
   yes(quality.accepted()&&quality.packFile().equals(original),"Quality did not return native");
   yes(BslSourceCertificate.validate(dir,generated,original,generatedMap,true).eligible(),"native-return certificate rejected");
   yes(!BslSourceCertificate.validate(dir,generated,original,generatedMap,false).eligible(),"inactive adapter admitted");
   var off=service.apply(dir,original,false,LodPreset.BALANCED,Map.of(),false);
   yes(off.accepted()&&off.packFile().equals(original),"Off did not retain native");
   yes(Arrays.equals(nativeBytes,Files.readAllBytes(nativePath)),"native archive changed");
   yes(Arrays.equals(sidecar,Files.readAllBytes(nativeOptions)),"native sidecar changed");
   System.out.println("PASS new certificate, Balanced/Quality/Off, stale map/state/archive refusal and native restoration");
 }
}
'''

TRANSACTION_JAVA = r'''
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class DeepBslRecovery {
 static void yes(boolean b,String message){if(!b)throw new AssertionError(message);}
 static Map<String,byte[]> files(Path directory)throws Exception {
   var result=new TreeMap<String,byte[]>();
   try(var paths=Files.list(directory)) {
     for(var path:paths.toList())if(Files.isRegularFile(path))
       result.put(path.getFileName().toString(),Files.readAllBytes(path));
   }
   return result;
 }
 static void same(Map<String,byte[]> expected,Map<String,byte[]> actual){
   yes(expected.keySet().equals(actual.keySet()),"file set not restored: "+actual.keySet());
   for(var name:expected.keySet())yes(Arrays.equals(expected.get(name),actual.get(name)),"changed file "+name);
 }
 static class Backend implements PackSelectionTransaction.Backend {
   final Path directory;String selected="Original.zip";
   final Map<String,String> queue=new HashMap<>(Map.of("INFO","3"));
   Backend(Path directory){this.directory=directory;}
   public Path directory(){return directory;}
   public String selected(){return selected;}
   public Map<String,String> queue(){return queue;}
   public Map<String,String> effectiveOptions(){return Map.of();}
   public boolean nativeRendererAvailable(){return true;}
   public void select(String name){selected=name;}
   public void save(){}
 }
 static void run(Path directory,boolean failReload)throws Exception {
   Backend backend=new Backend(directory);
   Map<String,byte[]> baseline=files(directory);
   Map<String,String> initialQueue=Map.copyOf(backend.queue);
   var transaction=new PackSelectionTransaction(backend);
   yes(transaction.prepare(true,LodPreset.BALANCED),"Balanced did not prepare a reload");
   yes(!backend.selected.equals("Original.zip"),"generated pack not selected");
   yes(Files.isRegularFile(directory.resolve(backend.selected)),"generated ZIP absent");
   Files.writeString(directory.resolve("Unrelated.txt"),"independent file\n");
   if(failReload){
     try{transaction.reload(()->{throw new IOException("injected reload rejection");});
       throw new AssertionError("reload unexpectedly succeeded");
     }catch(IOException failure){yes(failure.getMessage().equals("injected reload rejection"),"wrong reload failure");}
   }
   transaction.restore();
   yes(backend.selected.equals("Original.zip"),"original selection not restored");
   yes(backend.queue.equals(initialQueue),"queued options not restored");
   yes(Files.readString(directory.resolve("Unrelated.txt")).equals("independent file\n"),"unrelated file changed");
   Files.delete(directory.resolve("Unrelated.txt"));
   same(baseline,files(directory));
 }
 public static void main(String[] args)throws Exception {
   run(Path.of(args[0]),false);
   run(Path.of(args[1]),true);
   System.out.println("PASS r2 Balanced transaction Cancel and injected reload failure restore exact owned files, selection, queue; unrelated file retained");
 }
}
'''


def sha(data):
    return hashlib.sha256(data).hexdigest()


class DeepCandidateConsumers(unittest.TestCase):
    def test_old_and_new_exact_java_consumers(self):
        receipt = json.loads((ARTIFACT / 'receipt.json').read_text())
        self.assertEqual(sha(BASE.read_bytes()), receipt['original_archive_sha256'])
        dependencies = [Path(p) for p in json.loads(CLASSPATH.read_text())]
        self.assertTrue((JDK / 'javac').is_file())
        self.assertTrue(all(p.exists() for p in dependencies), 'recorded Java dependencies missing')
        for rel, pair in receipt['overlay'].items():
            self.assertEqual(sha((ROOT / rel).read_bytes()), pair['before'], rel)
            self.assertEqual(sha((ARTIFACT / 'overlay' / rel).read_bytes()), pair['after'], rel)
        recipe_hash = receipt['overlay'][RECIPE]['after']
        self.assertIn('RECIPE_SHA="' + recipe_hash + '"', (ARTIFACT / 'overlay' / CERT).read_text())
        with tempfile.TemporaryDirectory() as tmp_string:
            tmp = Path(tmp_string)
            resources = tmp / 'resources'
            shutil.copytree(ROOT / 'src/main/resources/assets/shaderlod/adapters',
                            resources / 'assets/shaderlod/adapters')
            shutil.copytree(ROOT / 'src/main/resources/assets/shaderlod/bslquad',
                            resources / 'assets/shaderlod/bslquad')
            for rel in ('src/main/resources/assets/shaderlod/adapters/' + RECIPE,
                        'src/main/resources/assets/shaderlod/adapters/patches/adapters/bsl-shaders.glsl'):
                dst = resources / rel.removeprefix('src/main/resources/')
                dst.write_bytes((ARTIFACT / 'overlay' / rel).read_bytes())
            packs = tmp / 'packs'; packs.mkdir()
            shutil.copyfile(BASE, packs / 'Original.zip')
            (packs / 'Original.zip.txt').write_text('# Native BSL quality\r\nTAA=true\r\nFXAA=true\r\nCLOUDS=2\r\n')
            source = tmp / 'DeepBslConsumer.java'; source.write_text(JAVA)
            classes = tmp / 'classes'; classes.mkdir()
            cp = os.pathsep.join(map(str, dependencies))
            report = []
            sources = [ARTIFACT / 'overlay' / CATALOG,
                       ROOT / 'src/main/java/com/pp/shaderlod/packs/PackSelectionService.java',
                       ROOT / 'src/main/java/com/pp/shaderlod/LodPreset.java']
            for candidate_cert, expect in ((ROOT / CERT, False), (ARTIFACT / 'overlay' / CERT, True)):
                build = subprocess.run([str(JDK / 'javac'), '-proc:none', '-cp', cp,
                    '-d', str(classes), *map(str, sources), str(candidate_cert), str(source)],
                    text=True, capture_output=True, timeout=45)
                self.assertEqual(build.returncode, 0, build.stdout + build.stderr)
                run_cp = os.pathsep.join((str(classes), str(resources), cp))
                packdir = packs if expect else tmp / 'old-packs'
                if not expect:
                    packdir.mkdir()
                    shutil.copyfile(BASE, packdir / 'Original.zip')
                    shutil.copyfile(packs / 'Original.zip.txt', packdir / 'Original.zip.txt')
                run = subprocess.run([str(JDK / 'java'), '-cp', run_cp, 'DeepBslConsumer',
                    str(packdir), recipe_hash, str(expect).lower(),
                    str(resources / 'assets/shaderlod/adapters' / RECIPE)],
                    text=True, capture_output=True, timeout=45)
                self.assertEqual(run.returncode, 0, run.stdout + run.stderr)
                generated = next(packdir.glob('ShaderLOD-bsl-shaders-*.zip'), None)
                self.assertIsNotNone(generated)
                generated_hash = sha(generated.read_bytes())
                with zipfile.ZipFile(generated) as archive:
                    names = archive.namelist()
                    with zipfile.ZipFile(ARTIFACT / 'candidate.zip') as prepared:
                        generated_source = set(names) - {'shaderlod-selection.json', 'SHADERLOD_PRIVATE_SELECTION.md'}
                        prepared_source = set(prepared.namelist()) - {'shaderlod-adapter.json', 'SHADERLOD_PRIVATE_ADAPTER.md'}
                        self.assertEqual(generated_source, prepared_source)
                        for name in names:
                            if name in generated_source:
                                self.assertEqual(sha(archive.read(name)), sha(prepared.read(name)), name)
                report.append({'certificate': 'new' if expect else 'old', 'generated_sha256': generated_hash,
                               'output': run.stdout.strip(), 'archive_count': len(names)})
            recovery_source = tmp / 'DeepBslRecovery.java'
            recovery_source.write_text(TRANSACTION_JAVA)
            recovery_build = subprocess.run([str(JDK / 'javac'), '-proc:none',
                '-cp', os.pathsep.join((str(classes), cp)), '-d', str(classes),
                str(ROOT / 'src/main/java/com/pp/shaderlod/integration/PackSelectionTransaction.java'),
                str(recovery_source)], text=True, capture_output=True, timeout=45)
            self.assertEqual(recovery_build.returncode, 0,
                             recovery_build.stdout + recovery_build.stderr)
            recovery_dirs = []
            for label in ('cancel', 'reload-failure'):
                path = tmp / label; path.mkdir()
                shutil.copyfile(BASE, path / 'Original.zip')
                (path / 'Original.zip.txt').write_text('# Native BSL quality\r\nTAA=true\r\nFXAA=true\r\nCLOUDS=2\r\nINFO=2\r\n')
                recovery_dirs.append(path)
            recovery = subprocess.run([str(JDK / 'java'), '-cp',
                os.pathsep.join((str(classes), str(resources), cp)),
                'DeepBslRecovery', *map(str, recovery_dirs)],
                text=True, capture_output=True, timeout=45)
            self.assertEqual(recovery.returncode, 0, recovery.stdout + recovery.stderr)
            evidence_base = ROOT / 'dev/logs/deep-shader-investigation-20261012/consumer-checks'
            evidence_base.mkdir(parents=True, exist_ok=True)
            receiver = Path(tempfile.mkdtemp(prefix='receiver-r2-', dir=evidence_base))
            (receiver / 'bsl-transaction-recovery.json').write_text(json.dumps({
                'candidate_recipe_sha256': recipe_hash,
                'transaction_source_sha256': sha((ROOT / 'src/main/java/com/pp/shaderlod/integration/PackSelectionTransaction.java').read_bytes()),
                'output': recovery.stdout.strip(),
                'cases': ['Balanced prepare then Cancel restore', 'Balanced prepare then injected reload failure and restore'],
                'backend': 'simulated Iris selection and queue; real transaction, service, recipe, source, filesystem',
                'minecraft_runtime': False, 'skips': 0, 'stubs': 0,
            }, indent=2) + '\n')
            (receiver / 'bsl-java-consumers.json').write_text(json.dumps({
                'candidate_recipe_sha256': recipe_hash,
                'source_zip_sha256': receipt['original_archive_sha256'],
                'checks': report, 'minecraft_runtime': False, 'skips': 0, 'stubs': 0,
            }, indent=2) + '\n')
            print(f'R2 consumer receiver: {receiver}')


if __name__ == '__main__':
    unittest.main()
