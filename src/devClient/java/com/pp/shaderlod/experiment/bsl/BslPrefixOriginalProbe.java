package com.pp.shaderlod.experiment.bsl;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.lwjgl.opengl.*;

/** Finite original-PBR observation. Never installs/arms a certificate. */
public final class BslPrefixOriginalProbe {
    private static final String PIN="36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5";
    private static final String REPL="6a967ac89bd1f05c42c0d0fa2f606b5b894b3b52bcc624c72c48b33a1d48a2c6";
    private static final Map<Object,Built> BUILT=new IdentityHashMap<>();
    private record Built(IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,ProgramSource source,GlProgram<?> program) {}
    private static boolean done;
    private static int firstFrame=-1;
    public static boolean requested() {return "1".equals(System.getProperty("shaderlod.dev.bslPrefixProbeR3"))
        &&!System.getProperty("shaderlod.dev.bslPrefixProbeOutputR3","").isBlank()
        &&!System.getProperty("shaderlod.dev.bslPrefixNativeOptionsR3","").isBlank();}
    public static void built(IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,ProgramSource source,GlProgram<?> program) {
        if(!requested()||done||pass!=SodiumPrograms.Pass.TERRAIN||program==null||source==null)return;
        if(BUILT.size()>=2){done=true;writeFailure("construction limit");return;}
        BUILT.put(program.getInterface(),new Built(pipeline,pass,source,program));
    }
    public static void ready(Object shader) {
        if(!requested()||done)return;
        var b=BUILT.get(shader);if(b==null)return;
        var client=Minecraft.getInstance();
        if(client.level==null||client.screen!=null||client.isPaused()||!client.isWindowActive())return;
        int frame=SystemTimeUniforms.COUNTER.getAsInt();
        if(firstFrame<0){firstFrame=frame;return;}
        if(frame-firstFrame<180)return; // CPU-only wait; no per-frame GL/source/image queries.
        done=true; // one finite attempt, including failure; never auto-retry after reload.
        var report=new JsonObject();
        try {
            if(Iris.isFallback()||Iris.getPipelineManager().getPipelineNullable()!=b.pipeline()
                ||!Iris.getCurrentPackName().equals("BSL_v10.1.8.zip")||!b.source().getName().equals("gbuffers_terrain")
                ||b.source().getParent().getPack()!=Iris.getCurrentPack().orElseThrow())
                throw new IllegalStateException("current original source/pipeline identity mismatch");
            Path archive=Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName());
            if(!hash(Files.readAllBytes(archive)).equals(PIN))throw new IllegalStateException("actual original archive differs");
            var options=Iris.getCurrentPack().orElseThrow().getShaderPackOptions();
            Map<String,String> actual=new TreeMap<>();
            options.getOptionSet().getBooleanOptions().keySet().forEach(k->actual.put(k,Boolean.toString(options.getOptionValues().getBooleanValueOrDefault(k))));
            options.getOptionSet().getStringOptions().keySet().forEach(k->actual.put(k,options.getOptionValues().getStringValueOrDefault(k)));
            var expected=JsonParser.parseString(Files.readString(Path.of(System.getProperty("shaderlod.dev.bslPrefixNativeOptionsR3")))).getAsJsonObject();
            var actualJson=new Gson().toJsonTree(actual).getAsJsonObject();
            if(!actualJson.equals(expected))throw new IllegalStateException("full native PBR option map mismatch");
            report.add("actual_options",actualJson);
            var camera=client.getCameraEntity();
            if(camera==null||camera.getX()!=492.5||camera.getY()!=68||camera.getZ()!=660.5
                ||camera.getYRot()!=90f||camera.getXRot()!=3.7f||client.level.getDayTime()!=6000
                ||client.level.isRaining()||!client.options.getCameraType().name().equals("FIRST_PERSON"))
                throw new IllegalStateException("fixed pbr_wall near view/time mismatch");
            var selected=client.options.resourcePacks;
            if(!selected.equals(List.of("vanilla","file/replete-1.2.0+mc1.20-1.21.4.zip","mod_resources")))
                throw new IllegalStateException("selected resource order mismatch");
            Path replete=client.gameDirectory.toPath().resolve("resourcepacks/replete-1.2.0+mc1.20-1.21.4.zip");
            if(!hash(Files.readAllBytes(replete)).equals(REPL))throw new IllegalStateException("actual Replete archive mismatch");
            report.add("loaded_resource_pack_ids",new Gson().toJsonTree(client.getResourceManager().listPacks().map(p->p.packId()).toList()));
            int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            if(program!=b.program().handle())throw new IllegalStateException("actual consuming program mismatch");
            var sources=driverStages(program);
            report.add("actual_driver_stages",new Gson().toJsonTree(sources));
            report.addProperty("program_source_fragment_sha256",hash(b.source().getFragmentSource().orElseThrow().getBytes(StandardCharsets.UTF_8)));
            report.addProperty("program_source_vertex_sha256",hash(b.source().getVertexSource().orElseThrow().getBytes(StandardCharsets.UTF_8)));
            var base=(TextureAtlas)client.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
            var found=BslPrefixSpriteDiscovery.current(base,client.getResourceManager());
            var rects=found.sprites().stream().map(BslPrefixSpriteDiscovery.Sprite::rect).toList();
            var image=BslPrefixReadbackReceipt.observe(report,BslPrefixOriginalProbe::readbackState,
                ()->new BslPrefixGpuDiagnostic().captureOnce(program,found.normal().getId(),rects,report));
            if(SystemTimeUniforms.COUNTER.getAsInt()!=frame||Iris.getPipelineManager().getPipelineNullable()!=b.pipeline()
                ||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=program)
                throw new IllegalStateException("frame/pipeline/program changed during observation");
            report.add("actual_gpu_image",new Gson().toJsonTree(image));
            var spriteJson=new JsonArray();for(var s:found.sprites()){
                var j=new JsonObject();j.addProperty("name",s.name());j.addProperty("pbr_sprite_identity",System.identityHashCode(s.pbrSprite()));
                j.addProperty("base_sprite_identity",System.identityHashCode(s.baseSprite()));j.addProperty("winning_resource_source_identity",System.identityHashCode(s.resourceSource()));
                j.addProperty("winning_pack",s.sourcePackId());j.addProperty("resource_sha256",s.resourceSha256());spriteJson.add(j);
            }report.add("actual_sprites",spriteJson);
            report.addProperty("pipeline_identity",System.identityHashCode(b.pipeline()));report.addProperty("program_owner_identity",System.identityHashCode(b.program()));
            report.addProperty("resource_manager_identity",System.identityHashCode(client.getResourceManager()));
            report.addProperty("normal_image_object_identity",System.identityHashCode(found.normal()));report.addProperty("atlas_identity",System.identityHashCode(base));
            report.addProperty("iris_frame",frame);report.addProperty("current_pack",Iris.getCurrentPackName());
            var view=new JsonObject();view.addProperty("x",camera.getX());view.addProperty("y",camera.getY());view.addProperty("z",camera.getZ());
            view.addProperty("yaw",camera.getYRot());view.addProperty("pitch",camera.getXRot());view.addProperty("day_time",client.level.getDayTime());
            view.addProperty("fov",client.options.fov().get());view.addProperty("render_distance",client.options.renderDistance().get());
            view.addProperty("focused",client.isWindowActive());view.addProperty("fullscreen",client.getWindow().isFullscreen());
            view.addProperty("main_width",client.getMainRenderTarget().width);view.addProperty("main_height",client.getMainRenderTarget().height);report.add("actual_view",view);
            report.addProperty("actual_original_sha256",PIN);report.addProperty("actual_replete_sha256",REPL);
            report.addProperty("status","OBSERVED_ORIGINAL_CURRENT_IMAGE_NO_ADMISSION");
            var uniforms=new JsonObject();for(String name:List.of("shaderlodBridgeVersion","shaderlodEnabled","shaderlodPreset","shaderlodBslPrefixVersion","shaderlodBslPrefixActive")){
                int loc=GL20.glGetUniformLocation(program,name);var u=new JsonObject();u.addProperty("location",loc);if(loc>=0)u.addProperty("actual_value",GL20.glGetUniformi(program,loc));uniforms.add(name,u);
            }report.add("normal_linked_uniforms",uniforms);
            report.addProperty("staged_supplier_prefix_active",0);
            report.addProperty("resource_upload_storage_epoch","UNKNOWN_NO_LIFECYCLE_HOOK");
            report.addProperty("native_MAG_numeric_support","UNVERIFIED_DRIVER_SUPPORT_REQUIRED");
            report.addProperty("certificate_active",false);
            write(report);
        }catch(Exception|Error failure){report.addProperty("status","FAILED_ORIGINAL_PREFIX_OBSERVATION_NO_ADMISSION");report.addProperty("failure",failure.toString());report.addProperty("certificate_active",false);write(report);}
        finally {BUILT.clear();}
    }
    private static Map<Integer,String> driverStages(int program) throws Exception {
        if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE||GL20.glGetProgrami(program,GL20.GL_ATTACHED_SHADERS)!=2)
            throw new IllegalStateException("actual current linked stages unavailable");
        int[] count={0},ids=new int[2];GL20.glGetAttachedShaders(program,count,ids);
        if(count[0]!=2||ids[0]<=0||ids[0]==ids[1])throw new IllegalStateException("actual stage identity invalid");
        Map<Integer,String> result=new TreeMap<>();
        for(int id:ids){int type=GL20.glGetShaderi(id,GL20.GL_SHADER_TYPE),length=GL20.glGetShaderi(id,GL20.GL_SHADER_SOURCE_LENGTH);String text=GL20.glGetShaderSource(id);
            if(length<=1||length>1048576||text==null||text.getBytes(StandardCharsets.UTF_8).length+1!=length||result.put(type,text)!=null)
                throw new IllegalStateException("driver stage absent/duplicate/truncated");}
        if(!result.keySet().equals(Set.of(GL20.GL_VERTEX_SHADER,GL20.GL_FRAGMENT_SHADER)))throw new IllegalStateException("unexpected stage types");return result;
    }
    private static int[] readbackState(){int[] names={GL13.GL_ACTIVE_TEXTURE,GL30.GL_READ_FRAMEBUFFER_BINDING,GL11.GL_READ_BUFFER,
        GL21.GL_PIXEL_PACK_BUFFER_BINDING,GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,
        GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST,GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES,
        GL30.GL_DRAW_FRAMEBUFFER_BINDING,GL20.GL_CURRENT_PROGRAM};int[] values=new int[names.length];
        for(int i=0;i<names.length;i++)values[i]=GL11.glGetInteger(names[i]);return values;}
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void writeFailure(String failure){var j=new JsonObject();j.addProperty("status","FAILED_NO_ADMISSION");j.addProperty("failure",failure);write(j);}
    private static void write(JsonObject report){try{var path=Path.of(System.getProperty("shaderlod.dev.bslPrefixProbeOutputR3"));Files.createDirectories(path.toAbsolutePath().getParent());var tmp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(report));Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception fail){System.err.println("BSL prefix finite receipt failed: "+fail);}}
    private BslPrefixOriginalProbe(){}
}
