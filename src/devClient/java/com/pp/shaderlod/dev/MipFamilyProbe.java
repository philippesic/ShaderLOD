package com.pp.shaderlod.dev;

import java.util.*;
import java.lang.reflect.Field;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.shader.GlShader;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.*;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import org.lwjgl.opengl.*;

/** Explicitly untimed, bounded native pass/image observations across mip families.
 * No deletion permission, filter mutation, image upload, copy or mip dispatch is installed. */
public final class MipFamilyProbe {
    private MipFamilyProbe() {}
    private static boolean observing,stopped;
    private static String error="";
    private static int frame=-1,complete,lastSystemFrame=-1;
    private static boolean frameCompleted;
    private static long sourceBytes;
    private static boolean settled;
    private static String settledScreenshot="";
    private static Map<String,Object> aoBefore=Map.of();
    private static Object pack,pipeline,context;
    private static final ThreadLocal<Scope> BUILD=new ThreadLocal<>();
    private static final Map<Integer,Map<String,Object>> SOURCES=new LinkedHashMap<>();
    private static final List<Map<String,Object>> EVENTS=new ArrayList<>();
    public static boolean observing(){return observing;}
    public static void observe(boolean enabled){
        if(enabled){
            if(!Boolean.getBoolean("shaderlod.dev.mipFamilyRuntime")||System.getProperty("shaderlod.dev.benchmarkControlProbe","").isBlank()
                ||!com.pp.shaderlod.optimization.mips.BslMipWitnessPins.exactApi())throw new IllegalStateException("Owned exact Iris mip observation required");
            stopped=false;error="";frame=-1;lastSystemFrame=-1;complete=0;frameCompleted=false;sourceBytes=0;pack=pipeline=context=null;SOURCES.clear();EVENTS.clear();settled=false;settledScreenshot="";aoBefore=Map.of();
        }
        observing=enabled;
        if(!enabled){pack=pipeline=context=null;SOURCES.clear();EVENTS.clear();sourceBytes=0;}
    }
    /** Reuse the exact linked-source receipts after reload/remeshing has settled. */
    public static void rearmSettled()throws Exception {
        var client=net.minecraft.client.Minecraft.getInstance();
        if(!observing||!stopped||complete!=2||!error.isEmpty()||SOURCES.isEmpty()
            ||!(Boolean.getBoolean("shaderlod.dev.blissAoCoverage")||SolasLeanAlphaProbe.enabled()||SildurLeanAlphaProbe.enabled()||Boolean.getBoolean("shaderlod.dev.sildurAoObservation"))||!client.levelRenderer.hasRenderedAllSections())
            throw new IllegalStateException("Completed source capture and settled AO scene required");
        stopped=false;
        try {if(!currentEpoch())throw new IllegalStateException("Settled source epoch differs");}
        catch(Exception e){stopped=true;throw e;}
        frame=-1;lastSystemFrame=-1;complete=0;frameCompleted=false;EVENTS.clear();settled=true;settledScreenshot="";aoBefore=Map.of();
    }
    public static final class Scope implements AutoCloseable {
        final Scope previous=BUILD.get();final Object renderer;final WorldRenderingPipeline pipeline;final ProgramSource source;
        List<String> stages=List.of();int linked;
        Scope(Object r,WorldRenderingPipeline p,ProgramSource s){renderer=r;pipeline=p;source=s;BUILD.set(this);}
        public void complete(Program program){if(!observing||stopped)return;
            if(program==null||program.getProgramId()!=linked||stages.size()!=2){fail("Actual pre-detach/link/program receipt absent");return;}
            if(SOURCES.size()>=128){fail("Source inventory bound");return;}
            sourceBytes+=stages.get(0).length()+stages.get(1).length();if(sourceBytes>64L*1024*1024){fail("Source bytes exceed bound");return;}
            var row=new LinkedHashMap<String,Object>();row.put("name",source.getName());row.put("program",linked);row.put("vertex_source",stages.get(0));row.put("fragment_source",stages.get(1));row.put("parsed_mips",new TreeSet<>(source.getDirectives().getMipmappedBuffers()));row.put("parsed_flips",source.getDirectives().getExplicitFlips());row.put("parsed_draw_buffers",source.getDirectives().getDrawBuffers());SOURCES.put(linked,row);
        }
        public void close(){if(previous==null)BUILD.remove();else BUILD.set(previous);}
    }
    public static Scope open(Object r,WorldRenderingPipeline p,ProgramSource s){return new Scope(r,p,s);}
    public static boolean wanted(String name){var s=BUILD.get();return observing&&!stopped&&s!=null&&s.previous==null&&s.source!=null&&s.source.getName().equals(name);}
    public static List<String> sources(GlShader[] shaders){return G08CloudProbe.sources(shaders);}
    public static void linked(List<String> stages,int program,boolean unchanged){var s=BUILD.get();if(s==null)return;if(!unchanged||stages.size()!=2||program<=0||!GL20.glIsProgram(program)||GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=1){fail("Actual link receipt refused");return;}s.stages=stages;s.linked=program;}
    private static void fail(String reason){error=reason;stopped=true;}
    private static Object field(Object o,String name)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static boolean currentEpoch()throws Exception {
        if(!observing||stopped)return false;
        Object p=Iris.getPipelineManager().getPipelineNullable(),s=Iris.getCurrentPack().orElse(null),g=GL.getCapabilities();
        if(p==null||s==null||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty() )throw new IllegalStateException("Native current pipeline/queue mismatch");
        if(pack==null){pack=s;pipeline=p;context=g;}else if(pack!=s||pipeline!=p||context!=g)throw new IllegalStateException("Observed epoch changed");
        return true;
    }
    private static boolean epoch(Object renderer)throws Exception {return currentEpoch()&&field(renderer,"pipeline")==pipeline;}
    private static void frameStart(RenderTargets targets)throws Exception {
        int now=SystemTimeUniforms.COUNTER.getAsInt();
        if(now==lastSystemFrame)return;
        if(frame>=0&&!frameCompleted)throw new IllegalStateException("Prior frame final boundary absent");
        lastSystemFrame=now;frame=settled?complete+1:now;frameCompleted=false;var images=new ArrayList<Map<String,Object>>();
        for(int i=0;i<targets.getRenderTargetCount();i++){var t=targets.get(i);if(t!=null)images.add(Map.of("target",i,"main",texture(t.getMainTexture()),"alt",texture(t.getAltTexture())));}
        event("frame_start",Map.of("images",images,"custom_textures",customTextureInventory(pipeline)));if(complete>=2)stopped=true;
    }
    /** Current Iris-owned custom resources, distinct from similarly named render targets. */
    static List<Map<String,Object>> customTextureInventory(Object owner)throws Exception {
        var customTextures=new ArrayList<Map<String,Object>>();
        var custom=(net.irisshaders.iris.pipeline.CustomTextureManager)field(owner,"customTextureManager");
        for(var stage:custom.getCustomTextureIdMap().entrySet())for(var entry:stage.getValue().entrySet()) {
            var access=entry.getValue();
            if(access.getType()!=net.irisshaders.iris.gl.texture.TextureType.TEXTURE_2D)continue;
            if(customTextures.size()>=128)throw new IllegalStateException("Bounded custom texture ownership inventory");
            customTextures.add(Map.of("stage",stage.getKey().name(),"name",entry.getKey(),
                "texture",texture(access.getTextureId().getAsInt())));
        }
        return List.copyOf(customTextures);
    }
    public static void enter(Object renderer){try{
        if(!epoch(renderer))return;
        frameStart((RenderTargets)field(renderer,"renderTargets"));
    }catch(Exception|LinkageError e){fail(e.toString());}}
    public static void generated(RenderTarget target,boolean alt){try{
        if(!observing||stopped)return;event("mip_return",Map.of("texture",texture(alt?target.getAltTexture():target.getMainTexture()),"read_alt",alt));
    }catch(Exception|LinkageError e){fail(e.toString());}}
    public static void beforeQuad(Object renderer){try{
        if(!observing||!settled||!epoch(renderer))return;
        int id=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);var source=SOURCES.get(id);
        if(Boolean.getBoolean("shaderlod.dev.blissAoCoverage")&&source!=null&&"composite1".equals(source.get("name")))
            aoBefore=aoDepth(id,source);
    }catch(Exception|LinkageError e){fail(e.toString());}}
    private static Map<String,Object> aoDepth(int id,Map<String,Object> source)throws Exception {
        var targets=(RenderTargets)field(pipeline,"renderTargets");
        return BlissAoCoverageProbe.capture(id,(String)source.get("fragment_source"),targets.getDepthTextureNoTranslucents().getTextureId());
    }
    public static void quad(Object renderer){try{
        if(!epoch(renderer))return;
        int id=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);var source=SOURCES.get(id);if(source==null)throw new IllegalStateException("Consumed program lacks actual link/source receipt");
        var row=new LinkedHashMap<String,Object>(sampling(id));row.put("name",source.get("name"));row.put("program",id);
        if(Boolean.getBoolean("shaderlod.dev.blissAoCoverage")&&"composite1".equals(source.get("name")))
            {row.put("bliss_ao_depth_census",aoDepth(id,source));if(settled)row.put("bliss_ao_depth_census_before",aoBefore);}
        if(Boolean.getBoolean("shaderlod.dev.sildurAoObservation")&&"deferred".equals(source.get("name"))){
            int location=GL20.glGetUniformLocation(id,"isEyeInWater");
            if(location<0)throw new IllegalStateException("Actual Sildur underwater uniform absent");
            row.put("sildur_ao_uniform",Map.of("location",location,"is_eye_in_water",GL20.glGetUniformi(id,location),
                "scope","Actual consumer uniform, not executed branches or avoided samples"));
        }
        if(Boolean.getBoolean("shaderlod.dev.blissAlbedoDomain")&&"composite2".equals(source.get("name"))){
            var targets=(RenderTargets)field(pipeline,"renderTargets");var input=targets.get(1);
            if(input==null)throw new IllegalStateException("Actual packed albedo owner absent");
            row.put("bliss_albedo_domain",BlissAlbedoDomainProbe.capture(id,(String)source.get("fragment_source"),input.getMainTexture(),input.getAltTexture()));
        }
        event("completed_quad",row);
    }catch(Exception|LinkageError e){fail(e.toString());}}
    private static Map<String,Object> sampling(int id)throws Exception {
        var reads=new ArrayList<Map<String,Object>>();int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try{
            for(int target=0;target<16;target++){
                int loc=GL20.glGetUniformLocation(id,"colortex"+target);if(loc<0)continue;int unit=GL20.glGetUniformi(id,loc);
                if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))throw new IllegalStateException("Sampler unit out of range");
                GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int tex=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
                reads.add(Map.of("target",target,"unit",unit,"sampler",sampler,"texture",texture(tex),"sampler_min",sampler==0?0:GL33.glGetSamplerParameteri(sampler,GL11.GL_TEXTURE_MIN_FILTER)));
            }
        }finally{GL13.glActiveTexture(active);}
        var writes=new ArrayList<Map<String,Object>>();int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if(fbo!=0)for(int i=0;i<8;i++){
            int attachment=GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i);if(attachment<GL30.GL_COLOR_ATTACHMENT0||attachment>GL30.GL_COLOR_ATTACHMENT15)continue;
            int type=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if(type==GL11.GL_TEXTURE){int tex=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);writes.add(Map.of("location",i,"attachment",attachment,"texture",tex));}
        }
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        var mask=org.lwjgl.BufferUtils.createByteBuffer(4);GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK,0,mask);
        var raster=Map.of("viewport",List.of(viewport[0],viewport[1],viewport[2],viewport[3]),
            "color_mask0",List.of(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0),
            "scissor",GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),"depth_test",GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
            "rasterizer_discard",GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD),"blend0",GL30.glIsEnabledi(GL11.GL_BLEND,0));
        var main=net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
        var presentation=Map.of("texture",main.getColorTextureId(),"framebuffer",main.frameBufferId,"width",main.width,"height",main.height);
        // Include operative permission uniforms as well as public request flags.
        // Read only while this explicitly untimed observer owns the current draw.
        var controls=new TreeMap<String,Number>();
        var auxiliarySamplers=new ArrayList<Map<String,Object>>();
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
            var size=stack.mallocInt(1);var type=stack.mallocInt(1);
            int count=GL20.glGetProgrami(id,GL20.GL_ACTIVE_UNIFORMS);
            if(count<0||count>4096)throw new IllegalStateException("Bounded current-draw scalar inventory");
            for(int i=0;i<count;i++){
                String name=GL20.glGetActiveUniform(id,i,1024,size,type);
                if(size.get(0)==1&&(type.get(0)==GL20.GL_SAMPLER_2D||type.get(0)==GL20.GL_SAMPLER_2D_SHADOW)){
                    int location=GL20.glGetUniformLocation(id,name);if(location<0)continue;
                    int unit=GL20.glGetUniformi(id,location);
                    if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))throw new IllegalStateException("Auxiliary sampler unit out of range");
                    try{
                        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
                        int tex=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
                        var image=texture(tex);var depth=new TreeMap<String,Integer>();
                        if(image.get("format") instanceof Integer format&&Set.of(0x1902,0x81A5,0x81A6,0x81A7,0x8CAC,0x88F0,0x8CAD).contains(format)){
                            depth.put("bits",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x884A));
                            depth.put("type",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x8C16));
                        }
                        auxiliarySamplers.add(Map.of("name",name,"type",type.get(0),"unit",unit,"sampler",sampler,"texture",image,"depth_storage",depth,"sampler_min",sampler==0?0:GL33.glGetSamplerParameteri(sampler,GL11.GL_TEXTURE_MIN_FILTER)));
                    }finally{GL13.glActiveTexture(active);}
                }
                if(!name.startsWith("shaderlod")||size.get(0)!=1)continue;
                int location=GL20.glGetUniformLocation(id,name);if(location<0)continue;
                if(type.get(0)==GL20.GL_INT||type.get(0)==GL20.GL_BOOL)controls.put(name,GL20.glGetUniformi(id,location));
                else if(type.get(0)==GL20.GL_FLOAT)controls.put(name,GL20.glGetUniformf(id,location));
            }
        }
        return Map.of("reads",reads,"writes",writes,"draw_fbo",fbo,"raster_state",raster,"presentation_target",presentation,
            "scalar_controls",controls,"auxiliary_2d_sampler_bindings",auxiliarySamplers,
            "auxiliary_scope","Raw bound scalar 2D samplers, including aliases; no executed-fetch, ownership or dead-lifetime certificate");
    }
    public static void terrainReady(Object shader){try{
        if(!observing||stopped||net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()||!currentEpoch())return;
        if(!(pipeline instanceof net.irisshaders.iris.pipeline.IrisRenderingPipeline iris))throw new IllegalStateException("Actual Iris pipeline required");
        frameStart((RenderTargets)field(pipeline,"renderTargets"));if(stopped)return;
        int id=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);String name=null;
        var passes=List.of(net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.SOLID,net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.CUTOUT,net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.TRANSLUCENT);
        for(int i=0;i<passes.size();i++){var p=iris.getSodiumPrograms().getProgram(passes.get(i));if(p!=null&&p.getInterface()==shader&&p.handle()==id)name=List.of("sodium_solid","sodium_cutout","sodium_translucent").get(i);}
        if(name==null)throw new IllegalStateException("Actual native Sodium owner/program differs");
        if(!SOURCES.containsKey(id)){
            int[] count={0},ids=new int[2];GL20.glGetAttachedShaders(id,count,ids);String vertex=null,fragment=null;
            if(count[0]!=2||GL20.glGetProgrami(id,GL20.GL_LINK_STATUS)!=1)throw new IllegalStateException("Actual Sodium attached/link receipt absent");
            for(int stage:ids){if(GL20.glGetShaderi(stage,GL20.GL_COMPILE_STATUS)!=1)throw new IllegalStateException("Actual Sodium compile refused");int type=GL20.glGetShaderi(stage,GL20.GL_SHADER_TYPE);String text=GL20.glGetShaderSource(stage);if(type==GL20.GL_VERTEX_SHADER)vertex=text;else if(type==GL20.GL_FRAGMENT_SHADER)fragment=text;else throw new IllegalStateException("Unknown Sodium stage");}
            if(vertex==null||fragment==null||SOURCES.size()>=128||(sourceBytes+=vertex.length()+fragment.length())>64L*1024*1024)throw new IllegalStateException("Bounded Sodium source receipt required");
            SOURCES.put(id,Map.of("name",name,"program",id,"vertex_source",vertex,"fragment_source",fragment,"parsed_mips",List.of(),"scope","Actual attached compiled/link status and current native Sodium program/interface owner; setup binding, not completed geometry"));
        }
        var row=new LinkedHashMap<String,Object>(sampling(id));row.put("name",name);row.put("program",id);event("terrain_setup",row);
    }catch(Exception|LinkageError e){fail(e.toString());}}
    public static void finalReturn(Object renderer){try{if(!epoch(renderer))return;if(frameCompleted)throw new IllegalStateException("Repeated final boundary");frameCompleted=true;complete++;event("final_return",Map.of("completed_frames",complete));
        if(settled&&complete==2){var c=net.minecraft.client.Minecraft.getInstance();String name="probe-ao-settled-"+UUID.randomUUID()+".png";
            net.minecraft.client.Screenshot.grab(c.gameDirectory,name,c.getMainRenderTarget(),message->{});
            settledScreenshot=c.gameDirectory.toPath().resolve("screenshots").resolve(name).toString();}
    }catch(Exception|LinkageError e){fail(e.toString());}}
    public static void copy(Object renderer){try{
        if(!epoch(renderer))return;
        int fbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),buffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
        if(fbo==0||buffer<GL30.GL_COLOR_ATTACHMENT0||buffer>GL30.GL_COLOR_ATTACHMENT15)throw new IllegalStateException("Copy source attachment unavailable");
        int source=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,buffer,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        event("swap_copy_return",Map.of("read_fbo",fbo,"read_buffer",buffer,"source_texture",source,"destination_texture",GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)));
    }catch(Exception|LinkageError e){fail(e.toString());}}
    private static Map<String,Object> texture(int id){
        if(id<=0||!GL11.glIsTexture(id))return Map.of("id",id,"exists",false);
        int prior=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);try{GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);return Map.of("id",id,"exists",true,"min_filter",GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER),"mag_filter",GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER),"base_level",GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL),"width",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),"height",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),"format",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT));}finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,prior);}
    }
    private static void event(String kind,Map<String,Object> fields){if(EVENTS.size()>=1024)throw new IllegalStateException("Mip event bound");var row=new LinkedHashMap<String,Object>(fields);row.put("kind",kind);row.put("frame",frame);if(settled)row.put("iris_frame",lastSystemFrame);EVENTS.add(row);}
    public static Map<String,Object> status(){return Map.of("observing",observing,"stopped",stopped,"error",error,"completed_frames",complete,"sources",List.copyOf(SOURCES.values()),"events",List.copyOf(EVENTS),"settled_rearm",settled,"settled_screenshot",settledScreenshot,"scope","Two completed final boundaries plus next-frame reset; actual pre-detach linked sources, physical sampler reads/writer attachments/mip-return/copy receipts. Source liveness, compute/image/terrain consumers and pixel parity need separate proof.");}
}
