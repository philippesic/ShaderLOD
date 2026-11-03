package com.pp.shaderlod;
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
 private void check(ShaderLodSettings.Preflight preflight) throws Exception {
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
  var oldPreflight=ShaderLodSettings.applyPreflight; var oldFactory=ShaderLodSettings.selectionFactory; var oldReload=ShaderLodSettings.applyReload; var oldFallback=ShaderLodSettings.applyFallback;
  // Unloaded NeoForge config must not even be read by a refused Apply.
  try {
   ShaderLodSettings.applyPreflight=()->{preflights.incrementAndGet();preflight.run();};
   ShaderLodSettings.selectionFactory=()->{creates.incrementAndGet();return new PackSelectionTransaction(backend);};
   ShaderLodSettings.applyReload=()->reloads.incrementAndGet();
   ShaderLodSettings.applyFallback=()->false;
   assertFalse(ShaderLodSettings.apply(true,LodPreset.QUALITY));
   assertEquals(0,reloads.get(),"Preflight must not reload an unowned selection");
   assertTrue(ShaderLodSettings.failureMessage().contains("Previous selection retained"),ShaderLodSettings.failureMessage());
   assertEquals(1,preflights.get()); assertEquals(0,creates.get()); assertEquals(0,selects.get()); assertEquals(0,saves.get()); assertEquals(0,reloads.get());
   assertEquals(beforeQueue,queue);
   for(var entry:files.entrySet())assertArrayEquals(entry.getValue(),Files.readAllBytes(entry.getKey()));
   try(var names=Files.list(directory)){assertEquals(files.size(),names.count());}
   
  } finally {ShaderLodSettings.applyPreflight=oldPreflight;ShaderLodSettings.selectionFactory=oldFactory;ShaderLodSettings.applyReload=oldReload;ShaderLodSettings.applyFallback=oldFallback;}
 }
}
