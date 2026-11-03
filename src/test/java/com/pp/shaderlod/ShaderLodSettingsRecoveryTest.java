package com.pp.shaderlod;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle;
import com.pp.shaderlod.optimization.CompileOptionControl;
import com.pp.shaderlod.optimization.OptimizationConfig;
import com.pp.shaderlod.optimization.OptimizationId;
import com.pp.shaderlod.optimization.OptimizationSnapshot;
import com.pp.shaderlod.packs.PackSelectionService;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual settings failure/recovery caller, transaction file witnesses and incoming constructor boundary.
 * Uses temporary config and the pinned upstream archive, with a headless Iris option parser/loader callback.
 * No client, GL or global Iris current-pack mutation. */
class ShaderLodSettingsRecoveryTest {
    private static final String KEY=CompileOptionControl.MAKEUP_DEAD_FRAME_COPY;
    @TempDir Path temporary;
    private Object savedRequests;
    private java.lang.reflect.Method restoreRequests;
    /** Use FML's actual sealed LoadedConfig, real event bus and temporary TOML persistence. */
    private static final class Loaded {
        private final CommentedConfig config = CommentedConfig.inMemory();
        private final IConfigSpec.ILoadedConfig delegate;
        public CommentedConfig config() { return config; }

        Loaded(Path path) throws ReflectiveOperationException {
            var bus = BusBuilder.builder().markerType(IModBusEvent.class).build();
            bus.addListener((ModConfigEvent.Reloading event) -> {
                assertFalse(Thread.holdsLock(ShaderLodConfig.class), "Save must not invert FML's reload lock order");
            });
            var info = (IModInfo) Proxy.newProxyInstance(IModInfo.class.getClassLoader(), new Class<?>[]{IModInfo.class},
                    (proxy, method, args) -> method.getName().equals("getModId") ? "shaderlod" : null);
            var container = new ModContainer(info) {
                @Override public IEventBus getEventBus() { return bus; }
            };
            var constructor = ModConfig.class.getDeclaredConstructor(ModConfig.Type.class, IConfigSpec.class,
                    ModContainer.class, String.class, ReentrantLock.class);
            constructor.setAccessible(true);
            var modConfig = constructor.newInstance(ModConfig.Type.CLIENT, ShaderLodConfig.SPEC, container,
                    path.getFileName().toString(), new ReentrantLock());
            var loadedConstructor = Class.forName("net.neoforged.fml.config.LoadedConfig")
                    .getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
            loadedConstructor.setAccessible(true);
            delegate = (IConfigSpec.ILoadedConfig) loadedConstructor.newInstance(config, path, modConfig);
        }
    }

    @BeforeEach void load() throws Exception {
        var capture=OptimizationConfig.class.getDeclaredMethod("state");capture.setAccessible(true);
        savedRequests=capture.invoke(null);
        restoreRequests=OptimizationConfig.class.getDeclaredMethod("restoreState",capture.getReturnType());
        restoreRequests.setAccessible(true);
        OptimizationConfig.load(temporary.resolve("requests.properties"));
        OptimizationConfig.replace(OptimizationSnapshot.noneRequested().with(OptimizationId.DEAD_FRAME_COPY,true));
        FMLPaths.loadAbsolutePaths(temporary);FMLConfig.load();
        var loaded=new Loaded(temporary.resolve("shaderlod-client.toml"));
        ShaderLodConfig.SPEC.correct(loaded.config());ShaderLodConfig.SPEC.acceptConfig(loaded.delegate);
        ShaderLodConfig.ENABLED.set(true);ShaderLodConfig.PRESET.set(LodPreset.PERFORMANCE);
    }
    @AfterEach void unload() throws Exception {
        try {ShaderLodConfig.SPEC.acceptConfig(null);}
        finally {if(restoreRequests!=null)restoreRequests.invoke(null,savedRequests);}
        assertFalse(MakeUpQuadLifecycle.suspended());
    }
    private final class Backend implements PackSelectionTransaction.Backend {
        String selected;
        final Map<String,String> queue=new LinkedHashMap<>();
        public Path directory(){return temporary;}
        public String selected(){return selected;}
        public Map<String,String> queue(){return queue;}
        public Map<String,String> effectiveOptions(){return Map.of();}
        public void select(String name){selected=name;}
        public void save(){}
    }
    private Backend generated() throws Exception {
        Files.copy(Path.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip"),temporary.resolve("MakeUp.zip"));
        var nativeValues=new Properties();nativeValues.putAll(MakeUpQuadEligibility.nativeOptions());
        try(var output=Files.newOutputStream(temporary.resolve("MakeUp.zip.txt"))){nativeValues.store(output,null);}
        var result=new PackSelectionService().apply(temporary,"MakeUp.zip",true,LodPreset.PERFORMANCE,Map.of(),false);
        assertTrue(result.accepted(),result.detail());assertTrue(result.generatedArchive());
        var backend=new Backend();backend.selected=result.packFile();
        // A previously successful G01 Apply persisted these valid native100 + private controls.
        var saved=new TreeMap<>(MakeUpQuadEligibility.nativeOptions());
        saved.put("SHADERLOD_REFINEMENT_ADAPTER","1");saved.put(KEY,"1");
        var bytes=new StringBuilder("# saved valid G01 graph\r\n");
        saved.forEach((key,value)->bytes.append(key).append('=').append(value).append("\r\n"));
        Files.writeString(temporary.resolve(backend.selected+".txt"),bytes);
        return backend;
    }
    private static IncludeGraph raw(boolean generated) throws Exception {
        var lines=ImmutableList.<String>builder();
        MakeUpQuadEligibility.nativeOptions().forEach((key,value)->{
            if(Set.of("true","false").contains(value)) {
                lines.add((value.equals("true")?"":"//")+"#define "+key);
                lines.add("#ifdef "+key,"#endif");
            } else {
                lines.add("#define "+key+" "+value+" // ["+value+(key.equals("AA_TYPE")?" 0":"")+"]");
                lines.add("#if "+key+" == "+value,"#endif");
            }
        });
        if(generated)for(String key:List.of(KEY,"SHADERLOD_REFINEMENT_ADAPTER")) {
            lines.add("#define "+key+" 0 // [0 1]","#if "+key+" == 1","#endif");
        }
        var path=AbsolutePackPath.fromAbsolutePath("/recovery.glsl");
        var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);
        constructor.setAccessible(true);
        return constructor.newInstance(ImmutableMap.of(path,new FileNode(path,lines.build())),ImmutableMap.of());
    }
    private Map<String,String> sidecar(Backend backend) throws IOException {
        var values=new Properties();
        try(var input=Files.newInputStream(temporary.resolve(backend.selected+".txt"))){values.load(input);}
        var result=new TreeMap<String,String>();values.forEach((key,value)->result.put((String)key,(String)value));return result;
    }
    private static ShaderPackOptions incoming(IncludeGraph raw,Map<String,String> overrides) {
        return CompileOptionControl.parsedShaderPackOptions(raw,overrides,new ShaderPackOptions(raw,overrides));
    }
    private void irisWrite(Backend backend,ShaderPackOptions options) throws IOException {
        var values=new Properties();var typed=options.getOptionValues();
        options.getOptionSet().getBooleanOptions().keySet().forEach(key->values.setProperty(key,Boolean.toString(typed.getBooleanValueOrDefault(key))));
        options.getOptionSet().getStringOptions().keySet().forEach(key->values.setProperty(key,typed.getStringValueOrDefault(key)));
        Path path=temporary.resolve(backend.selected+".txt");
        try(var output=Files.newOutputStream(path)){values.store(output,null);}
        PackSelectionTransaction.irisOptionsWritten(path,values); // Same pinned writer RETURN seam as settings tests.
    }
    @Test void failedOffRestoresPreviouslyLoadedAndSavedG01One() throws Exception {failedSettings(false,false);}
    @Test void failedQualityRestoresPreviouslyLoadedAndSavedG01One() throws Exception {failedSettings(true,false);}
    @Test void recoveryExceptionClearsScopeAndReturnsDeferredIntent() throws Exception {failedSettings(true,true);}
    private void failedSettings(boolean enabled,boolean failRecovery) throws Exception {
        var backend=generated();String generated=backend.selected;
        Path savedPath=temporary.resolve(generated+".txt");
        var savedMode=java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----");
        Files.setPosixFilePermissions(savedPath,savedMode);
        byte[] saved=Files.readAllBytes(savedPath);
        var graph=raw(true);var originalGraph=raw(false);var savedOptions=sidecar(backend);
        var loaded=new AtomicReference<>(new ShaderPackOptions(graph,savedOptions));
        var currentPackName=new AtomicReference<>(generated);
        // The fixture represents an actually valid prior graph, not an arbitrary saved control.
        var read=CompileOptionControl.class.getDeclaredMethod("readOptions",ShaderPackOptions.class,Map.class,boolean.class);
        read.setAccessible(true);
        @SuppressWarnings("unchecked") var typed=(Map<String,CompileOptionControl.NativeOption>)read.invoke(null,loaded.get(),Map.of(),false);
        assertTrue(com.pp.shaderlod.optimization.framecopy.MakeUpDeadCopyAdmission.options(typed));
        assertEquals("1",loaded.get().getOptionValues().getStringValueOrDefault(KEY));
        backend.queue.put("AA_TYPE","0");var loads=new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(enabled,LodPreset.QUALITY,()->new PackSelectionTransaction(backend),()->{
            if(loads.incrementAndGet()==1) {
                assertEquals("MakeUp.zip",backend.selected);
                loaded.set(incoming(originalGraph,sidecar(backend)));currentPackName.set(backend.selected);
                irisWrite(backend,loaded.get());
                backend.queue.put("FOREIGN_NATIVE","newer");
                throw new IOException("Forward failed after Iris replaced currentPack with original");
            }
            assertEquals("MakeUp.zip",currentPackName.get(),"the failed forward pack is still current during construction");
            assertEquals(generated,backend.selected);assertTrue(backend.queue.isEmpty());
            assertTrue(ShaderLodConfig.ENABLED.get());assertEquals(LodPreset.PERFORMANCE,ShaderLodConfig.PRESET.get());
            assertArrayEquals(saved,Files.readAllBytes(savedPath));
            // Real reload HEAD must not derive zero from the failed-forward original graph.
            CompileOptionControl.beforeReload();
            loaded.set(incoming(graph,sidecar(backend)));currentPackName.set(backend.selected);
            assertEquals("1",loaded.get().getOptionValues().getStringValueOrDefault(KEY));
            irisWrite(backend,loaded.get());
            if(failRecovery)throw new IOException("Recovery failed after construction/witnessed write");
        },()->false,(selection,reload)->selection.reload(reload::run)));
        assertEquals(2,loads.get());assertEquals(generated,backend.selected);
        assertTrue(ShaderLodConfig.ENABLED.get());assertEquals(LodPreset.PERFORMANCE,ShaderLodConfig.PRESET.get());
        assertTrue(OptimizationConfig.requested(OptimizationId.DEAD_FRAME_COPY));
        assertEquals("1",loaded.get().getOptionValues().getStringValueOrDefault(KEY));
        assertArrayEquals(saved,Files.readAllBytes(savedPath));assertEquals(savedMode,Files.getPosixFilePermissions(savedPath));
        assertEquals(Map.of("AA_TYPE","0","FOREIGN_NATIVE","newer"),backend.queue);
        assertEquals("0",incoming(graph,savedOptions).getOptionValues().getStringValueOrDefault(KEY),
            "a later arbitrary load must not inherit successful or failed recovery's scope");
    }
    private PackSelectionTransaction restored(Backend backend) throws Exception {
        var selection=new PackSelectionTransaction(backend);
        selection.prepare(false,LodPreset.QUALITY);selection.restoreForRecovery();return selection;
    }
    @Test void normalRequestSessionUsesTheSameSavedInputScope() throws Exception {
        var backend=generated();var graph=raw(true);var overrides=sidecar(backend);
        var selection=new PackSelectionTransaction(backend);
        var factory=CompileOptionControl.class.getDeclaredMethod("session",PackSelectionTransaction.class,
            CoreUniformFrame.Core.class,OptimizationSnapshot.class,boolean.class,Map.class,Map.class);
        factory.setAccessible(true);
        var session=(CompileOptionControl.Session)factory.invoke(null,selection,
            new CoreUniformFrame.Core(new LodSettings(true,32,96,8,false),LodPreset.PERFORMANCE),
            OptimizationConfig.snapshot(),true,backend.queue,Map.of());
        assertTrue(session.prepare());session.restore();
        try {
            session.recoveryReload(()->{
                CompileOptionControl.beforeReload();
                assertEquals("1",incoming(graph,overrides).getOptionValues().getStringValueOrDefault(KEY));
            });
        } finally {session.releaseRecovery();}
        assertEquals("0",incoming(graph,overrides).getOptionValues().getStringValueOrDefault(KEY));
    }
    @Test void ownedRecoveryIsThreadBoundRejectsNestingAndClearsOnError() throws Exception {
        var backend=generated();var graph=raw(true);var overrides=sidecar(backend);
        var selection=restored(backend);var error=new AssertionError("recovery error");
        try {
            assertSame(error,assertThrows(AssertionError.class,()->CompileOptionControl.recoveryReload(selection,()->{
                assertEquals("1",incoming(graph,overrides).getOptionValues().getStringValueOrDefault(KEY));
                assertEquals("0",CompletableFuture.supplyAsync(()->incoming(graph,overrides).getOptionValues().getStringValueOrDefault(KEY)).join());
                assertThrows(IllegalStateException.class,()->CompileOptionControl.recoveryReload(selection,()->fail("nested recovery")));
                var unowned=new PackSelectionTransaction(new Backend());
                assertThrows(IllegalStateException.class,()->CompileOptionControl.recoveryReload(unowned,()->fail("unowned nested load")));
                throw error;
            })));
        } finally {selection.finishRecovery();}
        assertEquals("0",incoming(graph,overrides).getOptionValues().getStringValueOrDefault(KEY));
    }
    @Test void generationChangeRevokesOwnedRecoveryAndClearsScope() throws Exception {
        var backend=generated();var graph=raw(true);var overrides=sidecar(backend);var selection=restored(backend);
        try {
            assertThrows(IllegalStateException.class,()->CompileOptionControl.recoveryReload(selection,()->{
                OptimizationConfig.replace(OptimizationConfig.snapshot());
                incoming(graph,overrides);
            }));
        } finally {selection.finishRecovery();}
        assertEquals("0",incoming(graph,overrides).getOptionValues().getStringValueOrDefault(KEY));
    }
    @Test void absentOwnedInputsDoNotGrantIncomingBypass() throws Exception {
        var backend=new Backend();var graph=raw(true);var overrides=Map.of(KEY,"1","SHADERLOD_REFINEMENT_ADAPTER","1");
        var selection=new PackSelectionTransaction(backend);selection.restoreForRecovery();
        try {
            assertFalse(selection.requiresOwnedNativeRecovery());
            CompileOptionControl.recoveryReload(selection,()->assertEquals("0",incoming(graph,overrides).getOptionValues().getStringValueOrDefault(KEY)));
            assertThrows(NullPointerException.class,()->CompileOptionControl.recoveryReload(null,()->fail("absent selection")));
        } finally {selection.finishRecovery();}
    }
    @Test void settingsWithoutSelectionDoesNotGrantRecoveryBypass() throws Exception {
        var graph=raw(true);var overrides=Map.of(KEY,"1","SHADERLOD_REFINEMENT_ADAPTER","1");var loads=new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(false,LodPreset.QUALITY,()->{throw new IllegalStateException("no selection transaction");},()->{
            loads.incrementAndGet();assertEquals("0",incoming(graph,overrides).getOptionValues().getStringValueOrDefault(KEY));
        },()->false));
        assertEquals(1,loads.get());assertTrue(ShaderLodConfig.ENABLED.get());assertEquals(LodPreset.PERFORMANCE,ShaderLodConfig.PRESET.get());
    }
}
