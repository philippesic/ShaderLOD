package com.pp.shaderlod.fabric;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.RendererBackend;
import com.pp.shaderlod.integration.RendererBackendObservationFixture;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ApplyBackendRefusalTest {
 @TempDir Path directory;
 @Test void actualPublicQualityRefusalBeforeAnyMutation() throws Exception {
  var constructor = RendererBackend.Refusal.class.getDeclaredConstructor(String.class);
  constructor.setAccessible(true);
  RendererBackend.Refusal refusal = constructor.newInstance("Unsupported actual backend. Previous selection retained.");
  check(() -> { throw refusal; });
 }
 @Test void unavailableContextMustNotReloadUnownedSelection() throws Exception {
  check(() -> RendererBackendObservationFixture.requireApplySupported(() -> { throw new IllegalStateException("context unavailable"); }));
 }
 @Test void unavailableLinkedRendererMustNotReloadUnownedSelection() throws Exception {
  check(() -> RendererBackendObservationFixture.requireApplySupported(() -> { throw new NoClassDefFoundError("renderer context unavailable"); }));
 }
 @Test void observedUnsupportedBackendMustNotReloadUnownedSelection() throws Exception {
  check(() -> RendererBackendObservationFixture.requireApplySupported(() -> "Vulkan"));
 }
 private void check(FabricLodSettings.Preflight preflight) throws Exception {
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
  var oldPreflight=FabricLodSettings.applyPreflight; var oldFactory=FabricLodSettings.selectionFactory; var oldReload=FabricLodSettings.applyReload; var oldFallback=FabricLodSettings.applyFallback;
  FabricLodConfig.load(directory.resolve("shaderlod-client.json")); var oldConfig=FabricLodConfig.snapshot();
  try {
   FabricLodSettings.applyPreflight=()->{preflights.incrementAndGet();preflight.run();};
   FabricLodSettings.selectionFactory=()->{creates.incrementAndGet();return new PackSelectionTransaction(backend);};
   FabricLodSettings.applyReload=()->reloads.incrementAndGet();
   FabricLodSettings.applyFallback=()->false;
   assertFalse(FabricLodSettings.apply(true,LodPreset.QUALITY));
   assertEquals(0,reloads.get(),"Preflight must not reload an unowned selection");
   assertTrue(FabricLodSettings.failureMessage().contains("Previous selection retained"),FabricLodSettings.failureMessage());
   assertEquals(1,preflights.get()); assertEquals(0,creates.get()); assertEquals(0,selects.get()); assertEquals(0,saves.get()); assertEquals(0,reloads.get());
   assertEquals(beforeQueue,queue);
   for(var entry:files.entrySet())assertArrayEquals(entry.getValue(),Files.readAllBytes(entry.getKey()));
   try(var names=Files.list(directory)){assertEquals(files.size(),names.count());}
   assertEquals(oldConfig,FabricLodConfig.snapshot());
  } finally {FabricLodSettings.applyPreflight=oldPreflight;FabricLodSettings.selectionFactory=oldFactory;FabricLodSettings.applyReload=oldReload;FabricLodSettings.applyFallback=oldFallback;}
 }
 @Test void observedOpenGlPublicApplyReachesQualityTransaction() throws Exception {
  Files.copy(Path.of("dependencies/upstream/photon_v1.3b.zip"),directory.resolve("Photon.zip"));
  Files.writeString(directory.resolve("Photon.zip.txt"),"TAA=true\nTAAU=false\nINFO=2\n");
  FabricLodConfig.load(directory.resolve("shaderlod-client.json"));
  var creates=new AtomicInteger(); var reloads=new AtomicInteger();
  var backend=new PackSelectionTransaction.Backend() {
   public Path directory(){return directory;}
   public String selected(){return "Photon.zip";}
   public Map<String,String> queue(){return new HashMap<>();}
   public Map<String,String> effectiveOptions(){return Map.of();}
   public void select(String name){assertEquals("Photon.zip",name);}
   public void save(){}
  };
  var oldPreflight=FabricLodSettings.applyPreflight; var oldFactory=FabricLodSettings.selectionFactory;
  var oldReload=FabricLodSettings.applyReload; var oldFallback=FabricLodSettings.applyFallback;
  try {
   FabricLodSettings.applyPreflight=()->RendererBackendObservationFixture.requireApplySupported(()->"OpenGL");
   FabricLodSettings.selectionFactory=()->{creates.incrementAndGet();return new PackSelectionTransaction(backend);};
   FabricLodSettings.applyReload=reloads::incrementAndGet; FabricLodSettings.applyFallback=()->false;
   assertTrue(FabricLodSettings.apply(true,LodPreset.QUALITY));
   assertEquals(1,creates.get()); assertEquals(1,reloads.get());
   assertTrue(FabricLodConfig.snapshot().enabled()); assertEquals(LodPreset.QUALITY,FabricLodConfig.snapshot().preset());
  } finally {
   FabricLodSettings.applyPreflight=oldPreflight;FabricLodSettings.selectionFactory=oldFactory;
   FabricLodSettings.applyReload=oldReload;FabricLodSettings.applyFallback=oldFallback;
  }
 }
}
