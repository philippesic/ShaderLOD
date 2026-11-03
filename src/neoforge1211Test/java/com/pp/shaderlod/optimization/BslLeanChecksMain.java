package com.pp.shaderlod.optimization;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonParser;
import com.pp.shaderlod.*;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import com.pp.shaderlod.optimization.mips.*;
import com.pp.shaderlod.packs.PackSelectionService;
import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Pinned ZIP -> real producer -> Iris's actual program discovery/include parser -> owned reloads. */
public class BslLeanChecksMain {
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("owned temporary receiver required");
        var methods=List.of("producerRouteUsesExplicitNextAndOwnsUnchangedZip","incomingAuthenticLeanFullMapColdVetoPreservesNativeAndPeers",
            "all62PeersAndLegacyCannotSelectLean","failedOffAndQualityRecoverSavedLeanOneAfterOriginalBecameCurrent",
            "actualRequestApplyAndUniformModeDecisionUseNextSnapshot","actualLeanProducerCapabilitiesConsumeBothRealC6AndC7Directives",
            "failedModeRequestsRestorePriorOwnershipAndFlags","exactPbrSingletonPreservesNativeAndRestoresOff","solasSingletonNativeGraphPreservesOptionsAndOff","astraSingletonNativeGraphPreservesAaAndOff");
        for(String name:methods){var fixture=new BslLeanChecksMain();fixture.dir=Files.createTempDirectory(Path.of(args[0]),"case-");
            fixture.setup();try{BslLeanChecksMain.class.getDeclaredMethod(name).invoke(fixture);}finally{fixture.restore();}
            System.out.println("PASS "+name);
        }
        System.out.println("PASS 10 actual pinned BSL/Solas/Astra reachable-parser/producer/transaction/directive groups; no GL or live Mixin claim");
    }
    Path dir;
    private OptimizationConfig.State saved;
    private Object priorLoading,priorIrisConfig;
    private java.lang.reflect.Field loadingField,irisConfigField;
    static final String KEY=CompileOptionControl.BSL_DEAD_MIP;
    static OptimizationSnapshot dead(){return OptimizationSnapshot.noneRequested().with(OptimizationId.DEAD_MIP_CHAIN,true);}
    static BslLeanGraph.Route route(OptimizationSnapshot flags){return new BslLeanGraph.Route(flags,true,"bsl-shaders",true,true);}
    void setup()throws Exception {
        loadingField=net.neoforged.fml.loading.LoadingModList.class.getDeclaredField("INSTANCE");loadingField.setAccessible(true);
        priorLoading=loadingField.get(null);
        net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        irisConfigField=net.irisshaders.iris.Iris.class.getDeclaredField("irisConfig");irisConfigField.setAccessible(true);
        priorIrisConfig=irisConfigField.get(null);
        irisConfigField.set(null,new net.irisshaders.iris.config.IrisConfig(dir.resolve("iris.properties"),dir.resolve("excluded.json")));
        saved=OptimizationConfig.state();OptimizationConfig.load(dir.resolve("requests.properties"));
        OptimizationConfig.replace(OptimizationSnapshot.noneRequested());
        Files.copy(Path.of("dependencies/upstream/other-pack-research/BSL_v10.1.8.zip"),dir.resolve("Original.zip"));
    }
    void restore()throws Exception {
        if(saved!=null)OptimizationConfig.restoreState(saved);
        if(irisConfigField!=null)irisConfigField.set(null,priorIrisConfig);
        if(loadingField!=null)loadingField.set(null,priorLoading);
    }
    static ShaderPackOptions parse(Path archive,Map<String,String> changed)throws Exception {
        try(var zip=FileSystems.newFileSystem(archive,Map.of())) {
            return new ShaderPackOptions(raw(zip.getPath("/shaders")),changed);
        }
    }
    static IncludeGraph raw(Path shaders)throws Exception {
        var roots=ImmutableList.<AbsolutePackPath>builder();
        for(String dimension:List.of("/","/world0/","/world-1/","/world1/"))
            ShaderPackSourceNames.findPresentSources(roots,shaders,AbsolutePackPath.fromAbsolutePath(dimension),ShaderPackSourceNames.POTENTIAL_STARTS);
        var entries=roots.build();assertFalse(entries.isEmpty());
        var graph=new IncludeGraph(shaders,entries,false);assertTrue(graph.getFailures().isEmpty());return graph;
    }
    static Map<String,String> values(ShaderPackOptions options){var result=new TreeMap<String,String>();CompileOptionControl.readOptions(options,Map.of(),false).forEach((key,value)->result.put(key,value.value()));return result;}
    Map<String,String> sidecar(String selected)throws Exception {
        var p=new Properties();var path=dir.resolve(selected+".txt");if(Files.exists(path))try(var in=Files.newInputStream(path)){p.load(in);}
        var out=new TreeMap<String,String>();p.forEach((k,v)->out.put(k.toString(),v.toString()));return out;
    }
    final class Backend implements PackSelectionTransaction.Backend,CompileOptionControl.ReloadInputs {
        String selected="Original.zip";ShaderPackOptions current;BslLeanGraph.Route route=BslLeanChecksMain.route(dead());
        final Map<String,String> queue=new LinkedHashMap<>();final List<Map<String,String>> loads=new ArrayList<>();
        boolean fail;int failLoad;LodPreset preset=LodPreset.PERFORMANCE;boolean enabled=true;
        Backend()throws Exception{current=parse(dir.resolve(selected),Map.of());assertEquals(384,values(current).size());}
        public Path directory(){return dir;}public String selected(){return selected;}public Map<String,String> queue(){return queue;}
        public Map<String,String> effectiveOptions(){return values(current);}public BslLeanGraph.Route routingContext(){return route;}
        public boolean nativeRendererAvailable(){return true;}public void select(String name){selected=name;}public void save(){}
        public Map<String,CompileOptionControl.NativeOption> loaded(){return CompileOptionControl.readOptions(current,Map.of(),false);}
        public boolean fallback(){return false;}public BslLeanGraph.Route route(){return route;}
        public CoreUniformFrame.Core core(){return new CoreUniformFrame.Core(new LodSettings(enabled,32,96,8,false),preset);}
        public Map<String,String> desired(){
            if(!loaded().containsKey(KEY))return Map.of();
            boolean owned=PackSelectionService.ownedBslLean(dir.resolve(selected),values(current));
            boolean broad="bsl-shaders".equals(PackSelectionService.verifyGeneratedRendererGraph(dir.resolve(selected),values(current)))
                &&DeadMipAdmission.expectedC6Options(values(current));
            return Map.of(KEY,enabled&&preset!=LodPreset.QUALITY&&route.requests().enabled(OptimizationId.DEAD_MIP_CHAIN)
                &&(owned&&BslLeanGraph.onlyDeadMip(route.requests())||broad)?"1":"0");
        }
        void load()throws Exception {
            if(!CompileOptionControl.recoveringSavedInputs())CompileOptionControl.synchronizeBeforeReload(queue,this::desired,this::loaded);
            var changed=new TreeMap<>(sidecar(selected));changed.putAll(queue);
            try(var zip=FileSystems.newFileSystem(dir.resolve(selected),Map.of())) {
                var shaders=zip.getPath("/shaders");var raw=raw(shaders);
                boolean owned=BslMipRuntimeGuard.ownedConstructorSource(shaders,dir.resolve(selected));
                current=CompileOptionControl.filterIncomingBslC6(raw,changed,new ShaderPackOptions(raw,changed),false,
                    owned&&CompileOptionControl.leanRequestAllowed(route,core()));
            }
            loads.add(values(current));
            var sparse=new Properties();var defaults=values(parse(dir.resolve(selected),Map.of()));
            values(current).forEach((k,v)->{if(!v.equals(defaults.get(k)))sparse.setProperty(k,v);});
            var output=dir.resolve(selected+".txt");if(sparse.isEmpty())Files.deleteIfExists(output);else try(var out=Files.newOutputStream(output)){sparse.store(out,null);}
            PackSelectionTransaction.irisOptionsWritten(output,sparse);queue.clear();
            if(fail||failLoad==loads.size()){fail=false;failLoad=0;throw new java.io.IOException("owned forward failure");}
        }
        PackSelectionTransaction prepare()throws Exception {
            var tx=new PackSelectionTransaction(this);tx.prepare(enabled,preset);return tx;
        }
        void apply()throws Exception {var tx=prepare();CompileOptionControl.reloadTwoPhase(tx,this::load,this);tx.finishRecovery();}
        OptimizationEditTransaction.Result request(OptimizationSnapshot next) {
            return OptimizationEditTransaction.apply(next,this::load,()->false,requested->{
                route=BslLeanChecksMain.route(requested);var tx=new PackSelectionTransaction(this);
                return new CompileOptionControl.Session(){
                    public boolean prepare()throws Exception{return tx.prepare(true,LodPreset.PERFORMANCE);}
                    public void reload(OptimizationEditTransaction.Reload ignored)throws Exception{CompileOptionControl.reloadTwoPhase(tx,Backend.this::load,Backend.this);}
                    public void restore()throws Exception{tx.restoreForRecovery();}
                    public void recoveryReload(OptimizationEditTransaction.Reload ignored)throws Exception{CompileOptionControl.recoveryReload(tx,Backend.this::load);}
                    public void finish(){tx.finishRecovery();}
                };
            });
        }
    }
    void producerRouteUsesExplicitNextAndOwnsUnchangedZip()throws Exception {
        var b=new Backend();b.route=route(dead().with(OptimizationId.AO_BUDGET,true));b.apply();
        String generated=b.selected;byte[] archive=Files.readAllBytes(dir.resolve(generated));
        assertEquals("1",values(b.current).get(BslLeanGraph.ADAPTER));assertEquals("1",values(b.current).get(KEY),"old broad graph already admits1 before same-ZIP mode change");
        var diagnostic=new TreeMap<>(values(b.current));diagnostic.put(BslLeanGraph.ADAPTER,"0");
        assertNull(PackSelectionService.verifyGeneratedRendererGraph(dir.resolve(generated),diagnostic));
        b.route=route(dead());b.loads.clear();b.apply();
        assertEquals(2,b.loads.size());assertEquals("0",b.loads.get(0).get(KEY));assertEquals("1",b.loads.get(1).get(KEY));
        assertEquals("0",values(b.current).get(BslLeanGraph.ADAPTER));assertArrayEquals(archive,Files.readAllBytes(dir.resolve(generated)));
        assertEquals(OptimizationSnapshot.noneRequested(),OptimizationConfig.snapshot(),"explicit next beats old globals");
        assertTrue(PackSelectionService.ownedBslLean(dir.resolve(generated),values(b.current)));
        assertNotNull(PackSelectionService.captureGeneratedRendererInputs(dir.resolve(generated),values(b.current)));
        try(var paths=Files.list(dir)){var state=paths.filter(p->p.getFileName().toString().startsWith(".shaderlod-selection-")).findFirst().orElseThrow();
            assertEquals(JsonParser.parseString("{\"SHADERLOD_REFINEMENT_ADAPTER\":\"0\"}"),JsonParser.parseString(Files.readString(state)).getAsJsonObject().get("appliedOverrides"));}
        for(String nativeKey:List.of("TAA","FXAA_SUBPIXEL","ADVANCED_MATERIALS")) {
            var invalid=new TreeMap<>(values(b.current));invalid.put(nativeKey,"changed");assertFalse(DeadMipAdmission.expectedLeanOptions(invalid));
            assertNull(PackSelectionService.verifyGeneratedRendererGraph(dir.resolve(generated),invalid));
        }
        var extra=new TreeMap<>(values(b.current));extra.put("EXTRA","1");assertFalse(DeadMipAdmission.expectedLeanOptions(extra));
        var missing=new TreeMap<>(values(b.current));missing.remove("TAA");assertFalse(DeadMipAdmission.expectedLeanOptions(missing));
        b.route=route(dead().with(OptimizationId.AO_BUDGET,true));b.apply();assertEquals("1",values(b.current).get(BslLeanGraph.ADAPTER));
        assertArrayEquals(archive,Files.readAllBytes(dir.resolve(generated)));
    }
    void incomingAuthenticLeanFullMapColdVetoPreservesNativeAndPeers()throws Exception {
        var b=new Backend();b.apply();var actual=new TreeMap<>(values(b.current));
        try(var zip=FileSystems.newFileSystem(dir.resolve(b.selected),Map.of())) {
            var shaders=zip.getPath("/shaders");var raw=raw(shaders);
            assertTrue(BslMipRuntimeGuard.ownedConstructorSource(shaders,dir.resolve(b.selected)));
            assertFalse(BslMipRuntimeGuard.ownedConstructorSource(shaders,dir.resolve("Original.zip")));
            var incoming=new ShaderPackOptions(raw,actual);
            assertSame(incoming,CompileOptionControl.filterIncomingBslC6(raw,actual,incoming,false,true));
            // All unauthorized cold contexts share the actual normal caller's false authorization result.
            for(var bad:List.of(new BslLeanGraph.Route(dead(),false,"bsl-shaders",true,true),
                    new BslLeanGraph.Route(dead(),true,"photon",true,true),route(OptimizationSnapshot.noneRequested()),
                    new BslLeanGraph.Route(dead(),true,"bsl-shaders",false,true),new BslLeanGraph.Route(dead(),true,"bsl-shaders",true,false))) {
                assertFalse(bad.eligible(true,LodPreset.PERFORMANCE));
                var safe=CompileOptionControl.filterIncomingBslC6(raw,actual,incoming,false,bad.eligible(true,LodPreset.PERFORMANCE));
                assertEquals("0",values(safe).get(KEY));var before=new TreeMap<>(actual);before.put(KEY,"0");assertEquals(before,values(safe));
            }
            assertFalse(route(dead()).eligible(false,LodPreset.PERFORMANCE));assertFalse(route(dead()).eligible(true,LodPreset.QUALITY));
            for(String key:List.of("FXAA_SUBPIXEL",CompileOptionControl.BSL_AO_HALF)) {
                var bad=new TreeMap<>(actual);bad.put(key,key.equals("FXAA_SUBPIXEL")?"0.75":"1");
                var safe=CompileOptionControl.filterIncomingBslC6(raw,bad,new ShaderPackOptions(raw,bad),false,true);
                assertEquals("0",values(safe).get(KEY));assertEquals(bad.get(key),values(safe).get(key));
            }
        }
    }
    void all62PeersAndLegacyCannotSelectLean()throws Exception {
        assertEquals(64,OptimizationId.values().length);
        for(var id:OptimizationId.values())if(id!=OptimizationId.DEAD_MIP_CHAIN)assertFalse(route(dead().with(id,true)).eligible(true,LodPreset.PERFORMANCE),id.name());
        assertFalse(BslLeanGraph.LEGACY.eligible(true,LodPreset.PERFORMANCE));
        var service=new PackSelectionService();var result=service.apply(dir,"Original.zip",true,LodPreset.PERFORMANCE,values(parse(dir.resolve("Original.zip"),Map.of())),false);
        assertTrue(result.accepted(),result.detail());assertEquals("1",sidecar(result.packFile()).get(BslLeanGraph.ADAPTER));
    }
    void failedOffAndQualityRecoverSavedLeanOneAfterOriginalBecameCurrent()throws Exception {
        for(boolean quality:List.of(false,true)) {
            var b=new Backend();b.apply();String lean=b.selected;
            byte[] prior=Files.readAllBytes(dir.resolve(lean+".txt"));
            var modes=Files.getPosixFilePermissions(dir.resolve(lean+".txt"));
            b.enabled=quality;b.preset=quality?LodPreset.QUALITY:LodPreset.PERFORMANCE;
            var tx=b.prepare();assertEquals("Original.zip",b.selected);
            b.fail=true;assertThrows(java.io.IOException.class,()->CompileOptionControl.reloadTwoPhase(tx,b::load,b));
            tx.restoreForRecovery();CompileOptionControl.recoveryReload(tx,b::load);tx.finishRecovery();
            assertEquals(lean,b.selected);assertEquals("1",values(b.current).get(KEY));assertEquals("0",values(b.current).get(BslLeanGraph.ADAPTER));
            assertArrayEquals(prior,Files.readAllBytes(dir.resolve(lean+".txt")));assertEquals(modes,Files.getPosixFilePermissions(dir.resolve(lean+".txt")));
            assertFalse(CompileOptionControl.leanRequestAllowed(b.route,b.core()),"recovery cannot escape its owned load");
            b.enabled=true;b.preset=LodPreset.PERFORMANCE;
            var off=new PackSelectionTransaction(b);off.prepare(false,LodPreset.PERFORMANCE);off.finishRecovery();
        }
    }
    void actualRequestApplyAndUniformModeDecisionUseNextSnapshot()throws Exception {
        var b=new Backend();var result=b.request(dead());
        assertTrue(result.success(),result.message());assertEquals(dead(),OptimizationConfig.snapshot());assertEquals("1",values(b.current).get(KEY));
        assertTrue(CompileOptionControl.linkedModeMatchesNext("bsl-shaders",b.loaded(),route(dead()),b.core()));
        for(var flags:List.of(OptimizationSnapshot.noneRequested(),OptimizationSnapshot.noneRequested().with(OptimizationId.AO_BUDGET,true),dead().with(OptimizationId.QUAD_COMMAND_COMPACTION,true)))
            assertFalse(CompileOptionControl.linkedModeMatchesNext("bsl-shaders",b.loaded(),route(flags),b.core()),"linked lean must leave for uniform/CPU/alloff");
    }

    void actualLeanProducerCapabilitiesConsumeBothRealC6AndC7Directives()throws Exception {
        var b=new Backend();b.apply();assertTrue(DeadMipAdmission.expectedLeanOptions(values(b.current)));
        assertEquals(398,values(b.current).size(),"real Iris reachable option profile, rather than a synthesized all-file map");
        try(var zip=FileSystems.newFileSystem(dir.resolve(b.selected),Map.of())) {
            var shaders=zip.getPath("/shaders");
            var input=ImmutableList.<net.irisshaders.iris.helpers.StringPair>builder();
            for(var entry:Map.of(KEY,"1","IS_IRIS","1","MC_VERSION","12101","IRIS_VERSION","10812","OVERWORLD","1","LIGHT_SHAFT","1","TAA","1","FXAA","1").entrySet())
                input.add(new net.irisshaders.iris.helpers.StringPair(entry.getKey(),entry.getValue()));
            var defines=input.build();
            var enabled=BslMipRuntimeGuard.constructorDefines(defines,shaders,dir.resolve(b.selected),true,true);
            assertTrue(enabled.stream().anyMatch(v->v.key().equals(BslMipRuntimeGuard.C6_DEFINE)));
            for(String name:List.of("composite6","composite7")) {
                String source=Files.readString(shaders.resolve("program/"+name+".glsl"));
                String directive=source.substring(source.indexOf("#ifdef LIGHT_SHAFT"),source.indexOf("//Common Functions//"));
                assertTrue(net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor.glslPreprocessSource(directive,enabled).contains("colortex1MipmapEnabled = false"),name);
                assertTrue(net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor.glslPreprocessSource(directive,BslMipRuntimeGuard.constructorDefines(defines,shaders,dir.resolve(b.selected),false,true)).contains("colortex1MipmapEnabled = true"),name+" unsupported API");
            }
        }
    }

    void astraSingletonNativeGraphPreservesAaAndOff()throws Exception {
        Path original;
        try(var paths=Files.list(Path.of("dependencies/upstream/pack-expansion"))){original=paths.filter(p->p.getFileName().toString().contains("Astra")&&p.getFileName().toString().contains("V93")).findFirst().orElseThrow();}
        Files.copy(original,dir.resolve("Original.zip"),StandardCopyOption.REPLACE_EXISTING);
        var nativeOptions=values(parse(dir.resolve("Original.zip"),Map.of()));assertEquals(1015,nativeOptions.size());assertEquals("3",nativeOptions.get("AA"));
        assertTrue(AstraMipLeanGraph.nativeProfile(nativeOptions));
        var context=new BslLeanGraph.Route(dead(),true,"astralex",true,true);assertTrue(context.eligible(true,LodPreset.PERFORMANCE));
        var service=new PackSelectionService();var plan=PackSelectionService.planSelection(dir,"Original.zip",true,LodPreset.PERFORMANCE);
        var applied=service.applyPlanned(dir,"Original.zip",true,LodPreset.PERFORMANCE,nativeOptions,true,nativeOptions,Set.of(),plan,context);
        assertTrue(applied.generatedArchive(),applied.toString());
        var actual=values(parse(dir.resolve(applied.packFile()),sidecar(applied.packFile())));assertEquals("0",actual.get(BslLeanGraph.ADAPTER));
        for(var e:nativeOptions.entrySet())assertEquals(e.getValue(),actual.get(e.getKey()));assertTrue(AstraMipLeanGraph.profile(actual));
        actual=new TreeMap<>(actual);actual.put(CompileOptionControl.ASTRALEX_DEAD_MIP,"1");
        assertEquals("astralex",PackSelectionService.verifyGeneratedRendererGraph(dir.resolve(applied.packFile()),actual));assertTrue(AstraMipLeanGraph.profile(actual));
        var invalid=new TreeMap<>(actual);invalid.put("AA","2");assertFalse(AstraMipLeanGraph.profile(invalid));assertFalse(AstraMipLeanGraph.nativeProfile(invalid));
        var offPlan=PackSelectionService.planSelection(dir,applied.packFile(),false,LodPreset.PERFORMANCE);
        var off=service.applyPlanned(dir,applied.packFile(),false,LodPreset.PERFORMANCE,actual,true,Map.of(),Set.of(),offPlan,context);
        assertEquals("Original.zip",off.packFile());assertEquals(nativeOptions,values(parse(dir.resolve(off.packFile()),sidecar(off.packFile()))));
    }

    void solasSingletonNativeGraphPreservesOptionsAndOff()throws Exception {
        Files.copy(Path.of("dependencies/upstream/pack-expansion/Solas Shader V3.7b.zip"),dir.resolve("Original.zip"),StandardCopyOption.REPLACE_EXISTING);
        var nativeOptions=values(parse(dir.resolve("Original.zip"),Map.of()));assertEquals(428,nativeOptions.size());
        assertTrue(com.pp.shaderlod.optimization.solasmips.SolasMipLeanGraph.nativeProfile(nativeOptions));
        var context=new BslLeanGraph.Route(dead(),true,"solas-shader",true,true);assertTrue(context.eligible(true,LodPreset.PERFORMANCE));
        var service=new PackSelectionService();var plan=PackSelectionService.planSelection(dir,"Original.zip",true,LodPreset.PERFORMANCE);
        var applied=service.applyPlanned(dir,"Original.zip",true,LodPreset.PERFORMANCE,nativeOptions,true,nativeOptions,Set.of(),plan,context);
        assertTrue(applied.generatedArchive(),applied.toString());
        var actual=values(parse(dir.resolve(applied.packFile()),sidecar(applied.packFile())));assertEquals("0",actual.get(BslLeanGraph.ADAPTER));
        for(var e:nativeOptions.entrySet())assertEquals(e.getValue(),actual.get(e.getKey()));
        assertTrue(com.pp.shaderlod.optimization.solasmips.SolasMipLeanGraph.profile(actual));
        actual=new TreeMap<>(actual);actual.put(CompileOptionControl.SOLAS_DEAD_MIP,"1");
        assertEquals("solas-shader",PackSelectionService.verifyGeneratedRendererGraph(dir.resolve(applied.packFile()),actual));
        assertTrue(com.pp.shaderlod.optimization.solasmips.SolasDeadMipAdmission.profile(actual));
        var invalid=new TreeMap<>(actual);invalid.put("MOTION_BLUR","true");assertFalse(com.pp.shaderlod.optimization.solasmips.SolasMipLeanGraph.profile(invalid));
        var offPlan=PackSelectionService.planSelection(dir,applied.packFile(),false,LodPreset.PERFORMANCE);
        var off=service.applyPlanned(dir,applied.packFile(),false,LodPreset.PERFORMANCE,actual,true,Map.of(),Set.of(),offPlan,context);
        assertEquals("Original.zip",off.packFile());assertEquals(nativeOptions,values(parse(dir.resolve(off.packFile()),sidecar(off.packFile()))));
    }

    void exactPbrSingletonPreservesNativeAndRestoresOff()throws Exception {
        var b=new Backend();Files.writeString(dir.resolve("Original.zip.txt"),"ADVANCED_MATERIALS=true\n");
        b.current=parse(dir.resolve(b.selected),Map.of("ADVANCED_MATERIALS","true"));
        var nativeOptions=values(b.current);assertTrue(BslLeanGraph.nativeProfile(nativeOptions));
        b.apply();assertEquals("0",values(b.current).get(BslLeanGraph.ADAPTER));assertEquals("1",values(b.current).get(KEY));
        for(var entry:nativeOptions.entrySet())assertEquals(entry.getValue(),values(b.current).get(entry.getKey()));
        assertTrue(PackSelectionService.ownedBslLean(dir.resolve(b.selected),values(b.current)));
        try(var zip=FileSystems.newFileSystem(dir.resolve(b.selected),Map.of())){assertTrue(BslMipRuntimeGuard.ownedConstructorSource(zip.getPath("/shaders"),dir.resolve(b.selected)));}
        var invalid=new TreeMap<>(values(b.current));invalid.put("PARALLAX_QUALITY","64");assertFalse(DeadMipAdmission.expectedLeanOptions(invalid));
        b.enabled=false;b.apply();assertEquals("Original.zip",b.selected);assertEquals(nativeOptions,values(b.current));
        assertEquals("true",sidecar(b.selected).get("ADVANCED_MATERIALS"));
    }

    void failedModeRequestsRestorePriorOwnershipAndFlags()throws Exception {
        for(int variant:List.of(0,1,2)) {
            var previous=variant==0?dead():dead().with(OptimizationId.AO_BUDGET,true);
            var next=variant==0?dead().with(OptimizationId.AO_BUDGET,true):dead();
            OptimizationConfig.replace(previous);var b=new Backend();b.route=route(previous);b.apply();
            String selected=b.selected;byte[] options=Files.readAllBytes(dir.resolve(selected+".txt"));
            Path state;try(var files=Files.list(dir)){state=files.filter(p->p.getFileName().toString().startsWith(".shaderlod-selection-")).findFirst().orElseThrow();}
            byte[] stateBytes=Files.readAllBytes(state);var before=values(b.current);
            b.loads.clear();b.failLoad=variant==2?2:1;var result=b.request(next);
            assertFalse(result.success(),"injected mode/activation failure");assertTrue(result.recoveryComplete(),result.message());
            assertEquals(previous,OptimizationConfig.snapshot());assertEquals(selected,b.selected);assertEquals(before,values(b.current));
            assertArrayEquals(options,Files.readAllBytes(dir.resolve(selected+".txt")));assertArrayEquals(stateBytes,Files.readAllBytes(state));assertTrue(b.queue.isEmpty());
        }
    }

}
