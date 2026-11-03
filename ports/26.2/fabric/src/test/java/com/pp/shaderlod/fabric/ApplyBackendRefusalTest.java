package com.pp.shaderlod.fabric;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.RendererBackend;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ApplyBackendRefusalTest {
 @TempDir Path directory;
 @Test void actualPublicApplyRefusesBeforeAnyMutationOnUnsupportedIdentities() throws Exception {
  for (String[] identity : new String[][]{{"Vulkan","com.mojang.blaze3d.vulkan.VulkanDevice"},{"OpenGL","unknown.Device"},{null,null}}) {
   check(() -> RendererBackend.requireApplySupportedIdentity(identity[0], identity[1]));
  }
 }
 @Test void unavailableActualRenderContextUsesTheSameEarlyTypedRefusal() throws Exception {
  check(RendererBackend::requireApplySupported);
 }
 private void check(RendererBackend.ApplyPreflight preflight) throws Exception {
  var queue = new HashMap<String,String>(Map.of("TAA","false","nativeQuality","High"));
  var beforeQueue = Map.copyOf(queue);
  var files = new LinkedHashMap<Path,byte[]>();
  for (String name : List.of("original.zip","original.zip.txt",".shaderlod-selection-fixture.json","shaderlod-client.json")) {
   Path file = directory.resolve(name); byte[] bytes = ("original bytes: " + name).getBytes(java.nio.charset.StandardCharsets.UTF_8);
   Files.write(file,bytes); files.put(file,bytes);
  }
  var creates=new AtomicInteger(); var selects=new AtomicInteger(); var saves=new AtomicInteger(); var reloads=new AtomicInteger(); var preflights=new AtomicInteger();
  var backend = new PackSelectionTransaction.Backend() {
   public Path directory(){return directory;}
   public String selected(){return "original.zip";}
   public Map<String,String> queue(){return queue;}
   public Map<String,String> effectiveOptions(){return Map.copyOf(queue);}
   public void select(String name){selects.incrementAndGet();}
   public void save(){saves.incrementAndGet();}
  };
  var oldPreflight=FabricLodSettings.applyPreflight; var oldFactory=FabricLodSettings.selectionFactory; var oldReload=FabricLodSettings.applyReload;
  FabricLodConfig.load(directory.resolve("shaderlod-client.json")); var oldConfig=FabricLodConfig.snapshot();
  try {
   FabricLodSettings.applyPreflight=()->{preflights.incrementAndGet();preflight.run();};
   FabricLodSettings.selectionFactory=()->{creates.incrementAndGet();return new PackSelectionTransaction(backend);};
   FabricLodSettings.applyReload=()->reloads.incrementAndGet();
   assertFalse(FabricLodSettings.apply(true,LodPreset.QUALITY));
   assertTrue(FabricLodSettings.failureMessage().contains("Previous selection retained"),FabricLodSettings.failureMessage());
   assertEquals(1,preflights.get()); assertEquals(0,creates.get()); assertEquals(0,selects.get()); assertEquals(0,saves.get()); assertEquals(0,reloads.get());
   assertEquals(beforeQueue,queue);
   for(var entry:files.entrySet())assertArrayEquals(entry.getValue(),Files.readAllBytes(entry.getKey()));
   try(var names=Files.list(directory)){assertEquals(files.size(),names.count());}
   assertEquals(oldConfig,FabricLodConfig.snapshot());
  } finally {FabricLodSettings.applyPreflight=oldPreflight;FabricLodSettings.selectionFactory=oldFactory;FabricLodSettings.applyReload=oldReload;}
 }
}
