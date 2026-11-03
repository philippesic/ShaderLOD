package com.pp.shaderlod.optimization.unboundspatial;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.optimization.unboundao.UnboundAoApiPins;
import com.pp.shaderlod.optimization.unboundearlyalpha.UnboundEarlyAlphaRuntime;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.packs.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.*;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.pipeline.*;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.targets.RenderTargets;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.*;
import org.lwjgl.opengl.*;
import java.nio.file.*;
import java.util.*;

/** Owns one native SOLID return, never a later composited scene. */
public final class UnboundSpatialRuntime {
    public static final String REUSE="SHADERLOD_UNBOUND_SPATIAL_REUSE", RATE="SHADERLOD_UNBOUND_SPATIAL_RATE";
    public static final String SOURCE="14ee368b6f2890b07cc8b31ebab56da0dfd5e2bc237784768ae745b9f2bec0d7";
    private static final class Graph {
        final Object pipeline; final ShaderPack pack; final RenderTargets targets; final long sourceEpoch;
        Program producer,resolver; UnboundSpatialBank bank; long frame; boolean activeLayer,exported,failed,preflight; int frameCounter=-1; long generation; int mode; float near;
        Graph(Object p,ShaderPack s,RenderTargets t){pipeline=p;pack=s;targets=t;sourceEpoch=epoch;}
    }
    private record Owner(Graph graph,long epoch){}
    private static final Map<Object,Graph> GRAPHS=new IdentityHashMap<>();
    private static final Map<Integer,Owner> EXPORTERS=new HashMap<>();
    private static final com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache SOURCE_CACHE=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache();
    private static long epoch,frames,qualifiedDrawEntries,completedLayers,blockedGeneration;
    private static boolean installed,pending,recovering,blocked;
    private static String failure="not consumed";
    private UnboundSpatialRuntime(){}
    public static void install(){if(installed)return;installed=true;for(var id:List.of(OptimizationId.SPATIAL_LIGHTING_REUSE,OptimizationId.SPATIAL_LIGHTING_RATE))CompileOptionControl.configureAdmission("complementary-unbound",id,UnboundSpatialRuntime::setup);}
    public static String status(){return "native SOLID current-frame lighting; qualified draw entries="+qualifiedDrawEntries+"; completed private resolves="+completedLayers+"; pending="+pending+"; last="+failure+"; "+resources()+"; counts are callbacks, not GPU avoided work or eligible pixel counts";}
    private static String resources(){var out=new ArrayList<String>();for(var g:GRAPHS.values()){var b=g.bank;if(b!=null)out.add("frame="+g.frame+",mode="+g.mode+",size="+b.width+"x"+b.height+",scene="+b.scene()+",inputs="+java.util.stream.IntStream.range(0,5).map(b::input).boxed().toList()+",diffuse="+b.diffuse()+",highlight="+b.highlight()+",resolved="+b.color()+",producer="+(g.producer==null?0:g.producer.getProgramId())+",resolver="+(g.resolver==null?0:g.resolver.getProgramId()));}return out.toString();}
    /** Explicit dev probe only. Synchronous readback must stay outside timing windows. */
    public static Map<String,Object> coverageProbe(){
        if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())throw new IllegalStateException("Unbound coverage probe requires render thread");
        var g=GRAPHS.get(Iris.getPipelineManager().getPipelineNullable());if(g==null||g.bank==null||g.activeLayer||g.frameCounter<0)return Map.of("available",false);
        var b=g.bank;int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),pack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] keys={GL11.GL_PACK_ALIGNMENT,GL12.GL_PACK_ROW_LENGTH,GL12.GL_PACK_SKIP_ROWS,GL12.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SWAP_BYTES};int[] saved=new int[keys.length];for(int i=0;i<keys.length;i++)saved[i]=GL11.glGetInteger(keys[i]);
        try{GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);for(int i=0;i<keys.length;i++)GL11.glPixelStorei(keys[i],i==0?4:0);if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("pre-existing GL error at explicit Unbound coverage probe");float[] values=new float[Math.multiplyExact(Math.multiplyExact(b.width,b.height),4)];long inputs=0,diffuse=0,highlight=0;
            class GStats {
                long tagged,valid,positive;float min,max;
                void sample(float value){tagged++;if(!Float.isFinite(value)||value<0||value>1)return;if(valid==0){min=value;max=value;}else{min=Math.min(min,value);max=Math.max(max,value);}valid++;if(value>0)positive++;}
                Map<String,Object> report(){var out=new LinkedHashMap<String,Object>();out.put("taggedPixels",tagged);out.put("validSamples",valid);out.put("invalidSamples",tagged-valid);out.put("positiveSamples",positive);if(valid>0){out.put("min",min);out.put("max",max);}return Map.copyOf(out);}
            }
            var nativeG=new GStats();var gByMaterial=new TreeMap<Integer,GStats>();int[] materialAt=new int[values.length/4];
            
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,b.input(3));GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,values);if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("Unbound coverage readback failed");for(int i=0;i<values.length;i+=4)if(values[i+3]==g.frameCounter&&values[i+1]>0&&values[i+2]>=.56f&&values[i+2]<1){inputs++;float material=values[i+1];if(material<16777216&&material==(int)material)materialAt[i/4]=(int)material;}
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,b.input(4));GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,values);if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("Unbound native G readback failed");for(int p=0;p<materialAt.length;p++)if(materialAt[p]>0){float value=values[4*p+3];nativeG.sample(value);gByMaterial.computeIfAbsent(materialAt[p],ignored->new GStats()).sample(value);}
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,b.diffuse());GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,values);if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("Unbound coverage readback failed");for(int i=0;i<values.length;i+=4)if(values[i+3]==g.frameCounter)diffuse++;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,b.highlight());GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,values);if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("Unbound coverage readback failed");for(int i=0;i<values.length;i+=4)if(values[i+3]>=0)highlight++;
            var result=new LinkedHashMap<String,Object>(Map.of("available",true,"frame",g.frameCounter,"mode",g.mode,"size",List.of(b.width,b.height),"exportedPixels",inputs,"diffuseFrameTags",diffuse,"highlightAnchorTags",highlight,"synchronousReadback",true,"resources",resources()));var report=new LinkedHashMap<String,Object>(nativeG.report());var materials=new TreeMap<String,Object>();gByMaterial.forEach((id,stats)->materials.put(Integer.toString(id),stats.report()));report.put("byMaterial",Map.copyOf(materials));result.put("nativeG",Map.copyOf(report));return Map.copyOf(result);
        }finally{for(int i=0;i<keys.length;i++)GL11.glPixelStorei(keys[i],saved[i]);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pack);GL11.glBindTexture(GL11.GL_TEXTURE_2D,bound);com.mojang.blaze3d.platform.GlStateManager._activeTexture(active);}
    }
    public static boolean api(){return UnboundSpatialPins.exact()&&com.pp.shaderlod.optimization.unboundprime.mixin.DepthMixinPlugin.exactApi()&&UnboundAoApiPins.exact()&&com.pp.shaderlod.optimization.unboundearlyalpha.mixin.UnboundEarlyAlphaPlugin.exactApi()&&com.pp.shaderlod.optimization.blissspatial.BlissSpatialSamplerPins.exact();}
    private static Map<String,String> options(ShaderPack p,boolean raw){
        return p==null?Map.of():com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(p.getShaderPackOptions());
    }
    public static boolean profile(Map<String,String> actual){if(actual==null||!Set.of("0","1").contains(actual.getOrDefault("SHADERLOD_UNBOUND_PIXEL_GRAPH","invalid")))return false;if(actual==null||!Set.of("0","1").contains(actual.getOrDefault(REUSE,"invalid"))||!Set.of("0","1").contains(actual.getOrDefault(RATE,"invalid")))return false;var nativeOptions=new TreeMap<>(actual);nativeOptions.remove(REUSE);nativeOptions.remove(RATE);nativeOptions.put(REUSE,actual.getOrDefault(REUSE,"invalid"));nativeOptions.put(RATE,actual.getOrDefault(RATE,"invalid"));if(!Set.of("2","3","4","5").contains(nativeOptions.getOrDefault("SHADOW_QUALITY","invalid")))return false;nativeOptions.put("SHADOW_QUALITY","2");return com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("complementary-unbound",nativeOptions);}
    private static boolean declarations(ShaderPack p){if(p==null)return false;for(String k:List.of(REUSE,RATE)){var o=p.getShaderPackOptions().getOptionSet().getStringOptions().get(k);if(o==null||!o.getOption().getDefaultValue().equals("0")||!Set.copyOf(o.getOption().getAllowedValues()).equals(Set.of("0","1")))return false;}return true;}
    private static Path selected(){var name=Iris.getIrisConfig().getShaderPackName().orElse(null);if(name==null)return null;var d=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var p=d.resolve(name).normalize();return p.startsWith(d)&&!p.equals(d)?p:null;}
    private static boolean sourceUncached(ShaderPack pack,Map<String,String> values){try{var p=selected();return p!=null&&SOURCE.equals(SupportedPackCatalog.shaderSourceDigest(PackSourceReader.read(p).memberSha256()))&&"complementary-unbound".equals(PackSelectionService.verifyGeneratedRendererGraph(p,values));}catch(Exception|LinkageError unavailable){return false;}}
    private static boolean source(ShaderPack pack,Map<String,String> values){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(UnboundSpatialRuntime.class,"source",pack,values),()->{if(pack==null){SOURCE_CACHE.invalidate();return false;}try{var o=pack.getShaderPackOptions();var client=net.minecraft.client.Minecraft.getInstance();return SOURCE_CACHE.check(UnboundSpatialRuntime.class,Iris.getShaderpacksDirectory(),Iris.getIrisConfig().getShaderPackName().orElse(null),"complementary-unbound",values,pack,o.getOptionValues(),o.getOptionSet(),Iris.getPipelineManager().getPipelineNullable(),client.level,epoch,Arrays.asList(OptimizationRuntimeContext.core(),Map.copyOf(Iris.getShaderPackOptionQueue())),client.getResourcePackRepository().getSelectedIds(),()->sourceUncached(pack,values));}catch(Exception|LinkageError unavailable){SOURCE_CACHE.invalidate();return false;}
  });
 }
    public static boolean worldSource(ShaderPack pack){return profile(options(pack,false))&&source(pack,options(pack,false));}
    public static boolean setup(){try{if(blocked&&blockedGeneration==OptimizationConfig.generation()||!api()||Iris.isFallback())return false;if(blocked)blocked=false;var mods=net.irisshaders.iris.platform.IrisPlatformHelpers.getInstance();if(mods.isModLoaded("distanthorizons")||mods.isModLoaded("voxy"))return false;var p=Iris.getCurrentPack().orElse(null);return declarations(p)&&profile(options(p,true))&&source(p,options(p,true))&&GL.getCapabilities().OpenGL33&&GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS)>=8&&GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS)>=8&&GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS)>=16;}catch(RuntimeException|LinkageError unavailable){return false;}}
    private static boolean environment(){var c=net.minecraft.client.Minecraft.getInstance();if(c.level==null||c.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()||!Iris.getShaderPackOptionQueue().isEmpty())return false;var mods=com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.loadedStack();return com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.stackMatches(mods)&&com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.resourcesMatch(c.getResourcePackRepository().getSelectedIds());}
    private static boolean requested(OptimizationId id){var c=OptimizationRuntimeContext.core();return c!=null&&c.settings().enabled()&&c.preset()!=LodPreset.QUALITY&&c.optimizations().enabled(id)&&OptimizationConfig.requested(id)&&!com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended();}
    private static int mode(Graph g){var o=options(g.pack,false);int m="1".equals(o.get(REUSE))&&requested(OptimizationId.SPATIAL_LIGHTING_REUSE)?1:"1".equals(o.get(RATE))&&requested(OptimizationId.SPATIAL_LIGHTING_RATE)?2:0;return m>0&&Boolean.getBoolean("shaderlod.unbound.spatial.fullRateProof")?3:m;}
    private static Graph graph(Object pipeline,RenderTargets targets,ShaderPack pack){var g=GRAPHS.get(pipeline);if(g==null){g=new Graph(pipeline,pack,targets);GRAPHS.put(pipeline,g);}if(g.targets!=targets||g.pack!=pack||g.sourceEpoch!=epoch)throw new IllegalStateException("Unbound private graph identity changed");return g;}
    public static boolean privateSource(ProgramSource s){return s!=null&&s.getParent()!=null&&Set.of("prepare98","prepare99").contains(s.getName())&&s.getFragmentSource().orElse("").contains("shaderlodUnboundSpatialEvaluate");}
    private static boolean privateAdmitted(ProgramSource source){if(!privateSource(source)||!api())return false;var pack=source.getParent().getPack();var raw=options(pack,true);return declarations(pack)&&profile(raw)&&source(pack,raw);}
    public static void samplers(Object pipeline,RenderTargets targets,ProgramSource source,SamplerHolder holder){if(!privateAdmitted(source))return;var g=graph(pipeline,targets,source.getParent().getPack());for(int i=0;i<5;i++){final int index=i;holder.addDynamicSampler(TextureType.TEXTURE_2D,()->g.bank==null?0:g.bank.input(index),null,"shaderlodUnboundSpatialInput"+i);}holder.addDynamicSampler(TextureType.TEXTURE_2D,()->g.bank==null?0:g.bank.diffuse(),null,"shaderlodUnboundSpatialDiffuse");holder.addDynamicSampler(TextureType.TEXTURE_2D,()->g.bank==null?0:g.bank.highlight(),null,"shaderlodUnboundSpatialHighlight");holder.addDynamicSampler(TextureType.TEXTURE_2D,()->g.bank==null?0:g.bank.scene(),net.irisshaders.iris.gl.sampler.GlSampler.NEAREST,"shaderlodUnboundSpatialScene");}
    /** The actual factory's builder is separate from its sampler interceptor. */
    public static void defaults(ProgramSource source,ProgramBuilder b){if(!privateAdmitted(source))return;b.uniform1i(UniformUpdateFrequency.PER_FRAME,"shaderlodUnboundSpatialResources",()->0);b.uniform1i(UniformUpdateFrequency.PER_FRAME,"shaderlodUnboundSpatialMode",()->0);b.uniform1i(UniformUpdateFrequency.PER_FRAME,"shaderlodUnboundTemporalShadowSamplingQualified",()->0);}
    public static void built(Object p,RenderTargets t,ProgramSource s,Program program){if(program==null||!privateAdmitted(s))return;var g=graph(p,t,s.getParent().getPack());int[] expected=new int[]{11};if(!Arrays.equals(s.getDirectives().getDrawBuffers(),expected)||program.getActiveImages()!=0)throw new IllegalStateException("Unbound private program directives changed");if(s.getName().equals("prepare98"))g.producer=program;else g.resolver=program;}
    public static boolean skipNormal(Object p,Program program){var g=GRAPHS.get(p);return g!=null&&(program==g.producer||program==g.resolver);}
    public static void own(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource s,int id){if(p==null||id<=0||pass!=SodiumPrograms.Pass.TERRAIN||s==null||s.getParent()==null||!s.getName().equals("gbuffers_terrain")||!s.getFragmentSource().orElse("").contains("shaderlodSpatialExport"))return;var g=GRAPHS.get(p);if(g!=null&&g.pack==s.getParent().getPack()&&g.producer!=null&&g.resolver!=null&&source(g.pack,options(g.pack,false)))EXPORTERS.put(id,new Owner(g,epoch));}
    /** Caller proves the linked derived fragment is the exact transformed native exporter. */
    public static boolean ownDerived(Object p,Object pack,int id,String fragment){var g=GRAPHS.get(p);if(g==null||g.pack!=pack||id<=0||fragment==null||!fragment.contains("shaderlodUnboundSpatialExportReady")||!fragment.contains("shaderlodSpatialExport")||!source(g.pack,options(g.pack,false)))return false;EXPORTERS.put(id,new Owner(g,epoch));return true;}
    public static boolean temporalHalfViewport(Object p,Object pack,int program,int[] viewport,int w,int h){var g=GRAPHS.get(p);return g!=null&&g.pack==pack&&g.sourceEpoch==epoch&&g.mode==2&&g.producer!=null&&g.producer.getProgramId()==program&&Arrays.equals(viewport,new int[]{0,0,w/2,h/2});}
    public static boolean ownsPrivateProgram(Object p,Object pack,int program){var g=GRAPHS.get(p);return g!=null&&g.pack==pack&&g.sourceEpoch==epoch&&((g.producer!=null&&g.producer.getProgramId()==program)||(g.resolver!=null&&g.resolver.getProgramId()==program));}
    public static boolean ownsExporter(Object p,Object pack,int program){var own=EXPORTERS.get(program);return own!=null&&own.graph().pipeline==p&&own.graph().pack==pack&&own.epoch()==epoch;}
    public static void forgetDerived(int program){EXPORTERS.remove(program);}
    public static void beginFrame(Object p,RenderTargets t){frames++;var g=GRAPHS.get(p);if(g==null)return;g.frame=frames;g.activeLayer=false;g.exported=false;g.frameCounter=-1;g.mode=0;try{if(!setup()||mode(g)==0)return;int w=t.getCurrentWidth(),h=t.getCurrentHeight();if(!UnboundSpatialBank.supported(w,h)){lose("private MRT8/size unavailable");return;}if(g.bank==null||g.bank.width!=w||g.bank.height!=h){if(g.bank!=null)g.bank.close();g.bank=new UnboundSpatialBank(w,h);}g.bank.clearFrame();}catch(RuntimeException|LinkageError unavailable){lose("private frame setup: "+unavailable.getClass().getSimpleName());}}
    public static void beginLayer(TerrainRenderPass pass){if(pass!=DefaultTerrainRenderPasses.SOLID||net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered())return;var p=Iris.getPipelineManager().getPipelineNullable();var g=GRAPHS.get(p);if(g==null||g.bank==null||g.producer==null||g.resolver==null||g.pack!=Iris.getCurrentPack().orElse(null)||g.frame!=frames)return;try{if(!environment()||!setup())return;g.mode=mode(g);if(g.mode==0)return;g.generation=OptimizationConfig.generation();g.activeLayer=true;g.exported=false;g.failed=false;g.preflight=false;}catch(RuntimeException|LinkageError unavailable){lose("SOLID setup unavailable");}}
    private static int integer(int p,String name){if(p<=0)return -1;int l=GL20.glGetUniformLocation(p,name);return l<0?-1:GL20.glGetUniformi(p,l);}
    private static void write(int p,String name,int value){if(p<=0)return;int l=GL20.glGetUniformLocation(p,name);if(l>=0)GL20.glUniform1i(l,value);}
    private static boolean bridge(int p){return integer(p,"shaderlodBridgeVersion")==2&&integer(p,"shaderlodEnabled")==1&&integer(p,"shaderlodOptimizationFlagsVersion")==1&&Set.of(0,1,2).contains(integer(p,"shaderlodPreset"));}
    private static boolean nativeAttachments(Graph g){if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)return false;int[] members={0,6,4};for(int i=0;i<3;i++){var target=g.targets.get(members[i]);int attachment=GL30.GL_COLOR_ATTACHMENT0+i;if(target==null||target.getWidth()!=g.bank.width||target.getHeight()!=g.bank.height||GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)!=GL11.GL_TEXTURE||GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL)!=0)return false;int texture=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);if(texture!=target.getMainTexture()&&texture!=target.getAltTexture())return false;}return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)==GL11.GL_TEXTURE&&GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)==g.targets.getDepthTexture();}
    private static boolean depthOnly(Graph g,int program){if(!(g.pipeline instanceof IrisRenderingPipeline p)||!(p.getSodiumPrograms() instanceof com.pp.shaderlod.optimization.unboundprime.DepthProgramAccess owner))return false;var depth=owner.shaderlod$depthProgram(DefaultTerrainRenderPasses.SOLID);if(depth==null||depth.handle()!=program||!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)||!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)||!nativeAttachments(g))return false;try(var stack=org.lwjgl.system.MemoryStack.stackPush()){var mask=stack.malloc(4);for(int i=0;i<GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);i++){GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK,i,mask);for(int j=0;j<4;j++)if(mask.get(j)!=0)return false;}}return true;}
    public static void draw(Runnable nativeDraw){
        var current=GRAPHS.get(Iris.getPipelineManager().getPipelineNullable());
        if(current==null||!current.activeLayer){nativeDraw.run();return;}
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        write(program,"shaderlodUnboundSpatialExportReady",0);write(program,"shaderlodUnboundSpatialSolid",0);
        var owner=EXPORTERS.get(program);var g=owner==null?null:owner.graph();
        if(g!=current||owner.epoch()!=epoch||g.pack!=Iris.getCurrentPack().orElse(null)){
            if(depthOnly(current,program)){nativeDraw.run();return;}
            current.failed=true;nativeDraw.run();return;
        }
        UnboundSpatialBank.Lease lease=null;boolean eligible=false;
        try{
            lease=g.bank.exportLease(program);eligible=nativeAttachments(g)&&lease.attach();
            if(!eligible){g.failed=true;lease.close();lease=null;}
            int f=integer(program,"frameCounter");
            boolean control=g.generation==OptimizationConfig.generation()&&bridge(program)&&f>=0&&f<16777216&&(g.frameCounter<0||g.frameCounter==f);
            if(!control)g.failed=true;if(eligible&&control&&!g.failed&&!g.preflight){g.preflight=preflight(g,f,program);if(!g.preflight)g.failed=true;}
            // Even a later disabled draw retains its input MRTs, writing invalid tags
            // to any native-full pixels. On any failure the whole SOLID call replays.
            if(eligible&&control&&!g.failed){g.frameCounter=f;write(program,"shaderlodUnboundSpatialExportMode",g.mode);write(program,"shaderlodUnboundSpatialSolid",1);write(program,"shaderlodUnboundSpatialExportReady",1);qualifiedDrawEntries++;}
        }catch(RuntimeException|LinkageError unavailable){g.failed=true;failure="private exporter proof unavailable";}
        try{nativeDraw.run();if(eligible&&!g.failed)g.exported=true;}
        finally{write(program,"shaderlodUnboundSpatialExportReady",0);write(program,"shaderlodUnboundSpatialSolid",0);if(lease!=null)try{lease.close();}catch(RuntimeException|LinkageError unavailable){g.failed=true;failure="private MRT lease restoration unavailable";}}
    }
    private static boolean privateBindings(Graph g,Program p){
        if(GL20.glGetProgrami(p.getProgramId(),GL20.GL_LINK_STATUS)!=GL11.GL_TRUE||!UnboundEarlyAlphaRuntime.spatialSamplers(g.pipeline,p.getProgramId()))return false;
        for(int i=0;i<5;i++){String name="shaderlodUnboundSpatialInput"+i;if(GL20.glGetUniformLocation(p.getProgramId(),name)<0)continue;var b=com.pp.shaderlod.optimization.unboundao.UnboundAoGlProof.binding(p.getProgramId(),name);if(b==null||b.texture()!=g.bank.input(i)||b.width()!=g.bank.width||b.height()!=g.bank.height||b.format()!=GL30.GL_RGBA32F||b.base()!=0||b.compare()!=GL11.GL_NONE||b.min()!=GL11.GL_NEAREST||b.mag()!=GL11.GL_NEAREST||b.aniso()!=1)return false;}
        for(String name:List.of("shaderlodUnboundSpatialDiffuse","shaderlodUnboundSpatialHighlight","shaderlodUnboundSpatialScene")){if(GL20.glGetUniformLocation(p.getProgramId(),name)<0)continue;var b=com.pp.shaderlod.optimization.unboundao.UnboundAoGlProof.binding(p.getProgramId(),name);int expected=name.endsWith("Diffuse")?g.bank.diffuse():name.endsWith("Highlight")?g.bank.highlight():g.bank.scene();int format=name.endsWith("Scene")?GL30.GL_R11F_G11F_B10F:GL30.GL_RGBA32F;if(b==null||b.texture()!=expected||b.width()!=g.bank.width||b.height()!=g.bank.height||b.format()!=format||b.base()!=0||b.compare()!=GL11.GL_NONE)return false;}
        return true;
    }
    private static boolean preflight(Graph g,int frame,int nativeProgram){int nativeNear=GL20.glGetUniformLocation(nativeProgram,"shaderlodNear");if(nativeNear<0)return false;g.near=GL20.glGetUniformf(nativeProgram,nativeNear);if(!Float.isFinite(g.near))return false;try(var state=new UnboundSpatialState()){for(Program p:List.of(g.producer,g.resolver)){p.use();int loc=GL20.glGetUniformLocation(p.getProgramId(),"shaderlodNear");if(loc<0||GL20.glGetUniformf(p.getProgramId(),loc)!=g.near||integer(p.getProgramId(),"frameCounter")!=frame||!privateBindings(g,p))return false;}return true;}}
    private static void uniforms(Graph g,Program p,int mode){int id=p.getProgramId();if(!privateBindings(g,p))throw new IllegalStateException("Unbound private input/native shadow sampler bindings changed");write(id,"shaderlodUnboundSpatialResources",1);write(id,"shaderlodUnboundSpatialMode",mode);int nearLoc=GL20.glGetUniformLocation(id,"shaderlodNear");if(nearLoc<0||GL20.glGetUniformf(id,nearLoc)!=g.near||integer(id,"frameCounter")!=g.frameCounter)throw new IllegalStateException("Unbound private frame differs from exporter");}
    public static boolean endLayer(TerrainRenderPass pass){if(pass!=DefaultTerrainRenderPasses.SOLID||net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered())return true;var g=GRAPHS.get(Iris.getPipelineManager().getPipelineNullable());if(g==null||!g.activeLayer)return true;g.activeLayer=false;if(g.failed){lose("private SOLID proof changed; replay full native layer");return false;}if(!g.exported)return true;
        // Finish captured inputs with native full coefficients if intent changed mid-layer.
        int m=g.generation==OptimizationConfig.generation()&&mode(g)>0?g.mode:3;
        try(var state=new UnboundSpatialState()){state.fullscreen();var bank=g.bank;var quad=FullScreenQuadRenderer.INSTANCE;boolean begun=false;try{quad.begin();begun=true;
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,bank.producerFbo());com.mojang.blaze3d.platform.GlStateManager._viewport(0,0,m==2?bank.width/2:bank.width,m==2?bank.height/2:bank.height);g.producer.use();uniforms(g,g.producer,m);UnboundEarlyAlphaRuntime.ownTemporalSpatial(g.pipeline,g.pack,g.producer.getProgramId());UnboundEarlyAlphaRuntime.drawTemporalForSpatial();try{quad.renderQuad();}finally{UnboundEarlyAlphaRuntime.revoke();}write(g.producer.getProgramId(),"shaderlodUnboundSpatialResources",0);
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,bank.resolverFbo());com.mojang.blaze3d.platform.GlStateManager._viewport(0,0,bank.width,bank.height);g.resolver.use();uniforms(g,g.resolver,m);UnboundEarlyAlphaRuntime.ownTemporalSpatial(g.pipeline,g.pack,g.resolver.getProgramId());UnboundEarlyAlphaRuntime.drawTemporalForSpatial();try{quad.renderQuad();}finally{UnboundEarlyAlphaRuntime.revoke();}write(g.resolver.getProgramId(),"shaderlodUnboundSpatialResources",0);bank.copyResolved();completedLayers++;failure="completed SOLID private resolve before overlays";
        }finally{write(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),"shaderlodUnboundSpatialResources",0);if(begun)quad.end();}}
        catch(RuntimeException|LinkageError unavailable){lose("private SOLID resolve failed: "+unavailable.getClass().getSimpleName());return false;}
        finally{g.exported=false;}
        return true;
    }
    public static void finishOrReplay(java.util.function.BooleanSupplier finish,Runnable nativeLayer){if(!finish.getAsBoolean())nativeLayer.run();}
    public static void abortLayer(){var g=GRAPHS.get(Iris.getPipelineManager().getPipelineNullable());if(g!=null){g.activeLayer=false;if(g.exported)lose("native SOLID call failed; native exception retained");}}
    public static void restoreLayer(){var g=GRAPHS.get(Iris.getPipelineManager().getPipelineNullable());if(g==null)return;g.activeLayer=false;int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);write(p,"shaderlodUnboundSpatialExportReady",0);write(p,"shaderlodUnboundSpatialSolid",0);}
    private static void lose(String reason){failure=reason;pending=true;blocked=true;blockedGeneration=OptimizationConfig.generation();}
    public static void destroyed(Object p){UnboundExactSsrRuntime.destroy(p);UnboundWorldPixelRuntime.destroy(p);SOURCE_CACHE.invalidate();var g=GRAPHS.remove(p);if(g!=null&&g.bank!=null)g.bank.close();EXPORTERS.entrySet().removeIf(e->e.getValue().graph()==g);}
    public static void reload(){UnboundExactSsrRuntime.invalidate();UnboundWorldPixelRuntime.invalidate();SOURCE_CACHE.invalidate();for(var g:GRAPHS.values())if(g.bank!=null)g.bank.close();GRAPHS.clear();EXPORTERS.clear();epoch++;}
    public static WorldRenderingPipeline beforePrepare(NamespacedId dimension){install();if(!pending||recovering)return null;recovering=true;try{nativeReload(Iris.getShaderPackOptionQueue(),Iris::reload);pending=false;return Iris.getPipelineManager().preparePipeline(dimension);}catch(Exception failure){throw new IllegalStateException("Unbound spatial native graph recovery failed",failure);}finally{recovering=false;}}
    @FunctionalInterface public interface Reload{void run()throws Exception;}
    public static void nativeReload(Map<String,String> queue,Reload reload)throws Exception{var previous=Map.copyOf(queue);previous.forEach((k,v)->queue.remove(k,v));queue.put(REUSE,"0");queue.put(RATE,"0");queue.put("SHADERLOD_UNBOUND_PIXEL_GRAPH","0");try{reload.run();}finally{queue.remove(REUSE,"0");queue.remove(RATE,"0");queue.remove("SHADERLOD_UNBOUND_PIXEL_GRAPH","0");previous.forEach((k,v)->{if(!k.equals(REUSE)&&!k.equals(RATE)&&!k.equals("SHADERLOD_UNBOUND_PIXEL_GRAPH"))queue.putIfAbsent(k,v);});}}

public static void draw(int mode,long counts,int type,long indices,int drawcount,long bases,com.pp.shaderlod.optimization.nativechain.NativeDraw nativeDraw){
        var current=GRAPHS.get(Iris.getPipelineManager().getPipelineNullable());
        if(current==null||!current.activeLayer){nativeDraw.draw(mode,counts,type,indices,drawcount,bases);return;}
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        write(program,"shaderlodUnboundSpatialExportReady",0);write(program,"shaderlodUnboundSpatialSolid",0);
        var owner=EXPORTERS.get(program);var g=owner==null?null:owner.graph();
        if(g!=current||owner.epoch()!=epoch||g.pack!=Iris.getCurrentPack().orElse(null)){
            if(depthOnly(current,program)){nativeDraw.draw(mode,counts,type,indices,drawcount,bases);return;}
            current.failed=true;nativeDraw.draw(mode,counts,type,indices,drawcount,bases);return;
        }
        UnboundSpatialBank.Lease lease=null;boolean eligible=false;
        try{
            lease=g.bank.exportLease(program);eligible=nativeAttachments(g)&&lease.attach();
            if(!eligible){g.failed=true;lease.close();lease=null;}
            int f=integer(program,"frameCounter");
            boolean control=g.generation==OptimizationConfig.generation()&&bridge(program)&&f>=0&&f<16777216&&(g.frameCounter<0||g.frameCounter==f);
            if(!control)g.failed=true;if(eligible&&control&&!g.failed&&!g.preflight){g.preflight=preflight(g,f,program);if(!g.preflight)g.failed=true;}
            // Even a later disabled draw retains its input MRTs, writing invalid tags
            // to any native-full pixels. On any failure the whole SOLID call replays.
            if(eligible&&control&&!g.failed){g.frameCounter=f;write(program,"shaderlodUnboundSpatialExportMode",g.mode);write(program,"shaderlodUnboundSpatialSolid",1);write(program,"shaderlodUnboundSpatialExportReady",1);qualifiedDrawEntries++;}
        }catch(RuntimeException|LinkageError unavailable){g.failed=true;failure="private exporter proof unavailable";}
        try{nativeDraw.draw(mode,counts,type,indices,drawcount,bases);if(eligible&&!g.failed)g.exported=true;}
        finally{write(program,"shaderlodUnboundSpatialExportReady",0);write(program,"shaderlodUnboundSpatialSolid",0);if(lease!=null)try{lease.close();}catch(RuntimeException|LinkageError unavailable){g.failed=true;failure="private MRT lease restoration unavailable";}}
    }

 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){source(pack,options(pack,true));}
}
