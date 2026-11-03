package com.pp.shaderlod.integration;
import com.pp.shaderlod.LodPreset;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class PhotonQuadPrepareFailureModeTest {
 @TempDir Path dir;
 private final byte[] prior="# user options\nTAA=true\n".getBytes();
 private final byte[] rejected="TAA=false\n".getBytes();
 private Path options;
 private Set<PosixFilePermission> intended=PosixFilePermissions.fromString("rw-r-----");
 private PackSelectionTransaction transaction(int mutation) throws Exception {
  Files.copy(Path.of("dependencies/upstream/photon_v1.3b.zip"),dir.resolve("Photon.zip"));
  options=dir.resolve("Photon.zip.txt");Files.write(options,prior);Files.setPosixFilePermissions(options,intended);
  var backend=new PackSelectionTransaction.Backend(){
   public Path directory(){return dir;}public String selected(){return "Photon.zip";}
   public Map<String,String> queue(){return new HashMap<>();}public Map<String,String> effectiveOptions(){return Map.of();}
   public void select(String n){}public void save(){}
  };
  return new PackSelectionTransaction(backend,(d,s,e,p,v)->{
   try {
    if(mutation==0)Files.write(options,rejected);
    else if(mutation==1){
     Path temporary=Files.createTempFile(dir,"owned-prepare-", ".tmp");Files.write(temporary,rejected);
     Files.move(temporary,options,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    } else Files.setPosixFilePermissions(options,PosixFilePermissions.fromString("rw-------"));
   }catch(IOException x){throw new UncheckedIOException(x);}
   throw new UncheckedIOException(new IOException("Injected service failure before native fields are assigned"));
  });
 }
 private void failPrepare(PackSelectionTransaction tx) {
  assertThrows(UncheckedIOException.class,()->tx.prepare(true,LodPreset.PERFORMANCE));
  assertFalse(tx.requiresOwnedNativeRecovery(),"Reproduces the failure before late native witness assignment");
 }
 @Test void prepareFailureRestoresModeAndBytesFromReviewerCounterexample() throws Exception {
  var tx=transaction(0);failPrepare(tx);tx.restoreForRecovery();tx.finishRecovery();
  assertArrayEquals(prior,Files.readAllBytes(options));assertEquals(intended,Files.getPosixFilePermissions(options));
 }
 @Test void failedOwnedAtomicReplacementRestoresOriginalModeAndBytes() throws Exception {
  var tx=transaction(1);failPrepare(tx);tx.restoreForRecovery();tx.finishRecovery();
  assertArrayEquals(prior,Files.readAllBytes(options));assertEquals(intended,Files.getPosixFilePermissions(options));
 }
 @Test void concurrentModeEditRefusesByteAndPermissionOverwrite() throws Exception {
  var tx=transaction(0);failPrepare(tx);
  var independent=PosixFilePermissions.fromString("r--r-----");Files.setPosixFilePermissions(options,independent);
  assertThrows(IOException.class,tx::restoreForRecovery);tx.finishRecovery();
  assertArrayEquals(rejected,Files.readAllBytes(options));assertEquals(independent,Files.getPosixFilePermissions(options));
 }
 @Test void failedOwnedModeOnlyChangeRestoresPermissionPreimage() throws Exception {
  var tx=transaction(2);failPrepare(tx);tx.restoreForRecovery();tx.finishRecovery();
  assertArrayEquals(prior,Files.readAllBytes(options));assertEquals(intended,Files.getPosixFilePermissions(options));
 }
}
