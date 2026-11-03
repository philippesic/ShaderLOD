package com.pp.shaderlod.optimization.sourcecache;
import com.pp.shaderlod.optimization.makeupao.*;
import com.pp.shaderlod.optimization.reimaginedao.*;
import com.pp.shaderlod.optimization.unboundao.*;
import com.pp.shaderlod.optimization.sildurao.*;
import com.pp.shaderlod.optimization.sildurspatial.SildurSpatialContract;

import com.pp.shaderlod.optimization.sourcecache.*;
import com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowScopePins;
import java.nio.file.Path;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.*;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.shader.*;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.GL;

/** Certificates for consumed composite inputs and successfully linked, live Program objects. */
public final class IrisCompositeCompiledInputs {
    private IrisCompositeCompiledInputs() {}
    public enum Family {
        MAKEUP("makeup-ultra-fast-shaders","SHADERLOD_MAKEUP_AO_HALF_RES","shaderlodMakeup"),
        REIMAGINED("complementary-reimagined","SHADERLOD_REIMAGINED_AO_HALF_RES","shaderlodReimagined"),
        UNBOUND("complementary-unbound","SHADERLOD_UNBOUND_AO_HALF_RES","shaderlodUnbound"),
        SILDUR("sildurs-vibrant-shaders","SHADERLOD_SILDUR_AO_HALF_RES","shaderlodSildur");
        final String id,option,uniform;
        Family(String id,String option,String uniform){this.id=id;this.option=option;this.uniform=uniform;}
        long epoch(){return switch(this){case MAKEUP->MakeUpAoRuntime.sourceGeneration();case REIMAGINED->ReimaginedAoRuntime.sourceGeneration();case UNBOUND->UnboundAoRuntime.sourceGeneration();case SILDUR->SildurAoRuntime.sourceGeneration();};}
        public boolean options(Map<String,String> values){return switch(this){case MAKEUP->MakeUpAoContract.options(values);case REIMAGINED->ReimaginedAoContract.options(values);case UNBOUND->UnboundAoContract.options(values);case SILDUR->SildurAoContract.options(values);};}
        boolean api(){return switch(this){case MAKEUP->MakeUpAoApiPins.exact();case REIMAGINED->ReimaginedAoApiPins.exact();case UNBOUND->UnboundAoApiPins.exact();case SILDUR->SildurAoApiPins.exact();};}
        String recipe(){return switch(this){case MAKEUP->MakeUpAoSourceProof.RECIPE_SHA;case REIMAGINED->ReimaginedAoSourceProof.RECIPE_SHA;case UNBOUND->UnboundAoSourceProof.RECIPE_SHA;case SILDUR->SildurAoSourceProof.RECIPE_SHA;};}
        public String producer(Map<String,String> values){return this==SILDUR?SildurSpatialContract.aoProducer(values):"deferred";}
        public String consumer(Map<String,String> values){return this==SILDUR?SildurSpatialContract.lighting(values):"deferred1";}
        public boolean admits(String name,Map<String,String> values){return options(values)&&"1".equals(values.get(option))&&(producer(values).equals(name)||consumer(values).equals(name));}

    }
    public static Family family(Map<String,String> values){
        Family match=null;
        for(var candidate:Family.values())if("1".equals(values.get(candidate.option))&&candidate.options(values)){
            if(match!=null)return null;match=candidate;
        }
        return match;
    }
    private static final class Counters {long published,compiledFrames,legacyFrames;}
    private static final Map<Family,Counters> COUNTERS=new EnumMap<>(Family.class);
    private static Counters counters(Family family){return COUNTERS.computeIfAbsent(family,k->new Counters());}
    private static final ThreadLocal<Scope> COMPILING=new ThreadLocal<>();
    private static final Map<Program,Owner> OWNERS=new IdentityHashMap<>();
    private static final Set<Program> REVOKED=Collections.newSetFromMap(new WeakHashMap<>());
    private static String last="not compiled";
    private static final Deque<String> trace=new ArrayDeque<>();
    private static void trace(String event){if(trace.size()==24)trace.removeFirst();trace.addLast(event);}
    private record Resources(Object manager,List<String> selected,List<String> packs) {
        static Resources capture(){var c=net.minecraft.client.Minecraft.getInstance();return new Resources(c.getResourceManager(),List.copyOf(c.getResourcePackRepository().getSelectedIds()),c.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList());}
        boolean current(){var actual=capture();return manager==actual.manager&&selected.equals(actual.selected)&&packs.equals(actual.packs);}
    }
    private record Context(Family family,Object renderer,WorldRenderingPipeline pipeline,RenderTargets targets,
            ProgramSource source,TextureStage stage,IrisCompiledSource.Certificate certificate,
            Object textures,Map<?,?> textureValues,Map<String,String> options,Object gl,Resources resources) {}
    private record Owner(Context context,int id) {}
    public record Pair(Family family,Program producer,Program consumer,Object renderer,Object pipeline,Object targets) {
        public Map<String,String> currentOptions(){
            var a=OWNERS.get(producer);var b=OWNERS.get(consumer);
            if(a==null||b==null||a.context().family()!=family||b.context().family()!=family||!current(producer,a,renderer,pipeline,targets)||!current(consumer,b,renderer,pipeline,targets)
                    ||!family.producer(a.context().options()).equals(a.context().source().getName())||!family.consumer(b.context().options()).equals(b.context().source().getName())
                    ||a.context().certificate().set()!=b.context().certificate().set()
                    ||a.context().certificate().input()!=b.context().certificate().input())return Map.of();
            return a.context().options();
        }
    }
    public enum State { MISSING, STALE, VALID }
    public record Lookup(State state,Pair pair) {}
    public static Lookup lookup(Family family,Program producer,Program consumer,Object renderer,Object pipeline,Object targets){
        return lookup(MakeUpAoCompilationPins.exact(),family,producer,consumer,renderer,pipeline,targets);
    }
    public static Lookup lookup(boolean compilerSupported,Family family,Program producer,Program consumer,Object renderer,Object pipeline,Object targets){
        boolean a=OWNERS.containsKey(producer),b=OWNERS.containsKey(consumer);
        if(REVOKED.contains(producer)||REVOKED.contains(consumer))return new Lookup(State.STALE,null);
        if(!a&&!b){if(compilerSupported)return new Lookup(State.STALE,null);counters(family).legacyFrames++;return new Lookup(State.MISSING,null);}
        var pair=new Pair(family,producer,consumer,renderer,pipeline,targets);
        if(!a||!b||pair.currentOptions().isEmpty())return new Lookup(State.STALE,null);
        counters(family).compiledFrames++;return new Lookup(State.VALID,pair);
    }
    private static boolean current(Program program,Owner owner,Object renderer,Object pipeline,Object targets){
        try{var c=owner.context();var client=net.minecraft.client.Minecraft.getInstance();
            return program.getProgramId()==owner.id()&&c.renderer()==renderer&&c.pipeline()==pipeline&&c.targets()==targets
                &&c.gl()==GL.getCapabilities()&&Iris.getPipelineManager().getPipelineNullable()==pipeline
                &&client.level!=null&&client.level.dimension()==net.minecraft.world.level.Level.OVERWORLD
                &&Iris.getShaderPackOptionQueue().isEmpty()&&c.resources().current()
                &&c.textures()==c.pipeline().getTextureMap()&&c.textureValues().equals(c.pipeline().getTextureMap())
                &&c.certificate().current(Iris.getCurrentPack().orElse(null),Iris.getIrisConfig().getShaderPackName().orElse(null),c.family().epoch());
        }catch(RuntimeException|LinkageError invalid){forget(program);return false;}
    }
    public static void clear(Family family){for(var program:new ArrayList<>(OWNERS.keySet()))if(OWNERS.get(program).context().family()==family)forget(program);var scope=COMPILING.get();if(scope!=null&&scope.family==family)scope.poison();last="invalidated "+family;}
    public static void destroy(Object pipeline){for(var program:new ArrayList<>(OWNERS.keySet()))if(OWNERS.get(program).context().pipeline()==pipeline)forget(program);}
    public static void destroyRenderer(Object renderer){for(var program:new ArrayList<>(OWNERS.keySet()))if(OWNERS.get(program).context().renderer()==renderer)forget(program);}
    public static void forget(Program program){if(OWNERS.remove(program)!=null)REVOKED.add(program);}
    public static Map<String,Object> status(Family family){var count=counters(family);long owners=OWNERS.values().stream().filter(o->o.context().family()==family).count();return Map.of("owners",owners,"published",count.published,"compiled_frames",count.compiledFrames,"legacy_frames",count.legacyFrames,"last",last,"api",MakeUpAoCompilationPins.status(),"trace",List.copyOf(trace));}

    public static final class Scope implements AutoCloseable {
        private final Scope previous;
        private final Object renderer;
        private final WorldRenderingPipeline pipeline;
        private final RenderTargets targets;
        private final ProgramSource source;
        private final TextureStage stage;
        private CompositeCompilationLinkEvidence<Context> chain;
        private Family family;
        private boolean poisoned;
        private Scope(Object renderer,WorldRenderingPipeline pipeline,RenderTargets targets,ProgramSource source,TextureStage stage){
            previous=COMPILING.get();if(previous!=null)previous.poison();
            this.renderer=renderer;this.pipeline=pipeline;this.targets=targets;this.source=source;this.stage=stage;
            poisoned=previous!=null;COMPILING.set(this);
            if(source!=null&&Set.of("deferred","deferred1","deferred2").contains(source.getName()))trace("open "+source.getName()+" stage="+stage+" nested="+(previous!=null));
        }
        private void poison(){poisoned=true;if(chain!=null)chain.poison();}
        public void complete(Program program){
            try{if(source!=null&&Set.of("deferred","deferred1","deferred2").contains(source.getName()))trace("complete "+source.getName()+" poisoned="+poisoned+" chain="+(chain!=null)+" chainPoison="+(chain!=null&&chain.poisoned()));if(poisoned||chain==null||program==null)return;
                var context=chain.complete(program,program.getProgramId());
                if(context==null||!context.certificate().current(source.getParent().getPack(),Iris.getIrisConfig().getShaderPackName().orElse(null),context.family().epoch()))return;
                REVOKED.remove(program);OWNERS.put(program,new Owner(context,program.getProgramId()));counters(context.family()).published++;last="verified consumed composite stages/shader objects/link/program";
            }catch(RuntimeException|LinkageError unavailable){poison();last="publication refused: "+unavailable.getClass().getSimpleName();}
        }
        public void close(){poison();if(previous==null)COMPILING.remove();else COMPILING.set(previous);}
    }
    public static Scope open(Object renderer,WorldRenderingPipeline pipeline,RenderTargets targets,ProgramSource source,TextureStage stage){return new Scope(renderer,pipeline,targets,source,stage);}
    private static Scope scope(){var s=COMPILING.get();return s==null||s.poisoned?null:s;}
    private static boolean recipePinned(Family family){
        try(var in=IrisCompositeCompiledInputs.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/"+family.id+".json")){
            return in!=null&&family.recipe().equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
        }catch(Exception unavailable){return false;}
    }
    public static void raw(String name,String vertex,String geometry,String fragment,TextureStage stage,Object textures){
        var s=scope();if(s==null)return;
        try{var source=s.source;var set=source==null?null:source.getParent();
            if(Set.of("deferred","deferred1","deferred2").contains(name))trace("raw "+name+" stage="+stage+" bootstrap="+MakeUpShadowScopePins.bootstrapSafe()+" pipeline="+s.pipeline.getClass().getName());
            if(!MakeUpAoCompilationPins.exact()||!MakeUpShadowScopePins.bootstrapSafe()||set==null
                    ||s.pipeline==null||s.pipeline.getClass()!=IrisRenderingPipeline.class||s.targets==null
                    ||!Set.of("deferred","deferred1","deferred2").contains(name)||!name.equals(source.getName())||stage!=TextureStage.DEFERRED||s.stage!=stage
                    ||textures!=s.pipeline.getTextureMap()||!(set.getPack() instanceof IrisCompiledSource.PackView view))return;
            var input=view.shaderlod$makeupPackInput();
            if(input==null)return;
            var values=input.options().values();Family family=family(values);
            if(family==null||!family.admits(name,values)||!family.api()||!recipePinned(family))return;
            s.family=family;trace("input "+family+" "+name+" half=1");
            String selected=Iris.getIrisConfig().getShaderPackName().orElse(null);
            if(selected==null||!Path.of(selected).getFileName().toString().equals(selected))return;
            var certificate=IrisCompiledSource.certify(source,set,Iris.getShaderpacksDirectory().resolve(selected),selected,family.epoch(),family.id,name);
            if(certificate==null||!certificate.stages().arguments(vertex,geometry,null,null,fragment)){trace("certificate refused "+name+" "+IrisCompiledSource.status());last="raw composite arguments refused: "+IrisCompiledSource.status();return;}
            var context=new Context(family,s.renderer,s.pipeline,s.targets,source,stage,certificate,textures,
                Collections.unmodifiableMap(new HashMap<>((Map<?,?>)textures)),input.options().values(),GL.getCapabilities(),Resources.capture());
            s.chain=new CompositeCompilationLinkEvidence<>(context);trace("certified "+name);last="verified raw composite inputs";
        }catch(RuntimeException|LinkageError unavailable){s.poison();last="raw composite refused: "+unavailable.getClass().getSimpleName();}
    }
    public static boolean supportedStages(Map<PatchShaderType,String> output){
        return output!=null&&output.get(PatchShaderType.VERTEX)!=null&&output.get(PatchShaderType.FRAGMENT)!=null
            &&output.entrySet().stream().allMatch(e->e.getKey()==PatchShaderType.VERTEX||e.getKey()==PatchShaderType.FRAGMENT||e.getValue()==null);
    }
    public static void transformed(Map<PatchShaderType,String> output){
        var s=scope();if(s==null||s.chain==null)return;
        trace("transformed "+s.source.getName()+" keys="+(output==null?null:output.keySet()));
        if(!supportedStages(output)){s.poison();return;}
        String fragment=output.get(PatchShaderType.FRAGMENT);
        if(fragment==null||!fragment.contains(s.family.uniform+"AoTargetReady")||!fragment.contains(s.family.uniform+"AoTargetSize")||!fragment.contains("shaderlodOptAoRenderScale")){trace("fragment ABI refused "+s.source.getName());s.poison();return;}
        s.chain.transformed(output.get(PatchShaderType.VERTEX),output.get(PatchShaderType.GEOMETRY),fragment);
    }
    public static void begin(String name,String vertex,String geometry,String fragment){var s=scope();if(s==null||s.chain==null)return;if(!s.source.getName().equals(name)){s.poison();return;}s.chain.begin(vertex,geometry,fragment);}
    public static void shader(ShaderType type,String source,GlShader shader){
        var s=scope();if(s==null||s.chain==null)return;
        var stage=type==ShaderType.VERTEX?CompositeCompilationLinkEvidence.Stage.VERTEX:type==ShaderType.FRAGMENT?CompositeCompilationLinkEvidence.Stage.FRAGMENT:null;
        s.chain.shader(stage,source,shader);
    }
    public static void linking(String name,GlShader[] shaders){var s=scope();if(s==null||s.chain==null)return;if(!s.source.getName().equals(name)||!s.chain.shaders(shaders))s.poison();}
    public static void linked(GlShader[] shaders,int id){var s=scope();if(s!=null&&s.chain!=null)s.chain.linked(shaders,id);}
    public static void builder(Object builder,int id){var s=scope();if(s!=null&&s.chain!=null)s.chain.builder(builder,id);}
    public static void built(Object builder,Program program,int id){var s=scope();if(s!=null&&s.chain!=null)s.chain.built(builder,program,id);}
}
