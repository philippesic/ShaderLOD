package com.pp.shaderlod.fabric.dev.earlyalpha;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
/** One-shot raw observations at real native pre-submission. No filter classifier or shader change. */
public final class BslPrototypeDrawCapture {
    private static TerrainRenderPass current;
    private static final JsonObject OBS=new JsonObject();
    private BslPrototypeDrawCapture() {}
    public static TerrainRenderPass enter(TerrainRenderPass pass) {var prior=current;current=pass;return prior;}
    public static void restore(TerrainRenderPass prior) {current=prior;}
    public static boolean complete() {return OBS.has("SOLID")&&OBS.has("CUTOUT");}
    public static JsonObject observations() {return OBS.deepCopy();}
    public static void draw(TerrainRenderPass pass) {
        current=pass;
        if(!BslEarlyAlphaObserver.armed() || ShadowRenderingState.areShadowsCurrentlyBeingRendered() || (current!=DefaultTerrainRenderPasses.SOLID && current!=DefaultTerrainRenderPasses.CUTOUT)) return;
        String role=current==DefaultTerrainRenderPasses.SOLID?"SOLID":"CUTOUT";if(OBS.has(role))return;
        int priorUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),priorProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),priorFbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        var r=new JsonObject();int errorBefore=GL11.glGetError();
        try(var stack=MemoryStack.stackPush()) {
            r.addProperty("role",role);r.addProperty("nativeEpoch",BslEarlyAlphaObserver.epoch());r.addProperty("renderOrdinal",BslEarlyAlphaObserver.renderOrdinal());r.addProperty("captureUtc",java.time.Instant.now().toString());r.addProperty("nativeDrawBoundary","DefaultChunkRenderer.executeDrawBatch HEAD after begin/setupState/model uniforms; before beginTessellating and real multiDraw");
            int expected=((net.irisshaders.iris.pipeline.IrisRenderingPipeline)Iris.getPipelineManager().getPipelineNullable()).getSodiumPrograms().getProgram(current).handle();
            r.addProperty("pipelineIdentity",System.identityHashCode(Iris.getPipelineManager().getPipelineNullable()));r.addProperty("packIdentity",System.identityHashCode(Iris.getCurrentPack().orElseThrow()));
            r.addProperty("currentProgram",priorProgram);r.addProperty("expectedRoleProgram",expected);r.addProperty("programIdentityMatches",expected==priorProgram);
            if(expected!=priorProgram || priorProgram==0) throw new IllegalStateException("actual consuming program differs from role");
            int count=GL20.glGetProgrami(priorProgram,GL20.GL_ATTACHED_SHADERS);var ids=stack.mallocInt(Math.max(1,count));var n=stack.mallocInt(1);GL20.glGetAttachedShaders(priorProgram,n,ids);var shaders=new JsonArray();int fragments=0;
            for(int i=0;i<n.get(0);i++) {int id=ids.get(i);int type=GL20.glGetShaderi(id,GL20.GL_SHADER_TYPE);var sh=new JsonObject();sh.addProperty("id",id);sh.addProperty("type",type);if(type==GL20.GL_FRAGMENT_SHADER) {fragments++;
                String source=GL20.glGetShaderSource(id);byte[] bytes=source.getBytes(java.nio.charset.StandardCharsets.UTF_8);String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                Path output=net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("shaderlod-dev/early-alpha-"+role+"-fragment.glsl");Files.createDirectories(output.getParent());Files.write(output,bytes);sh.addProperty("sourcePath",output.toString());sh.addProperty("sourceSha256",hash);sh.addProperty("source",source);
            }shaders.add(sh);}r.add("attachedShaders",shaders);r.addProperty("attachedShaderCount",count);r.addProperty("attachedFragmentCount",fragments);if(fragments!=1)throw new IllegalStateException("exactly one attached native fragment required");
            int loc=GL20.glGetUniformLocation(priorProgram,"iris_currentAlphaTest");r.addProperty("nativeAlphaLocation",loc);if(loc>=0)r.addProperty("nativeAlphaValue",GL20.glGetUniformf(priorProgram,loc));
            for(String name:java.util.List.of("shaderlodBslAlphaEnabled","shaderlodBslAlphaQualified","shaderlodBslAlphaVersion","shaderlodBslAlphaPreset")){int location=GL20.glGetUniformLocation(priorProgram,name);r.addProperty(name+"Location",location);if(location>=0)r.addProperty(name+"Readback",GL20.glGetUniformi(priorProgram,location));}
            int privateRef=GL20.glGetUniformLocation(priorProgram,"shaderlodBslAlphaReference");r.addProperty("privateAlphaLocation",privateRef);if(privateRef>=0)r.addProperty("privateAlphaReadback",GL20.glGetUniformf(priorProgram,privateRef));
            var uniforms=new JsonArray();int total=GL20.glGetProgrami(priorProgram,GL20.GL_ACTIVE_UNIFORMS);int max=GL20.glGetProgrami(priorProgram,GL20.GL_ACTIVE_UNIFORM_MAX_LENGTH);
            for(int i=0;i<total;i++) {var size=stack.mallocInt(1);var type=stack.mallocInt(1);String name=GL20.glGetActiveUniform(priorProgram,i,max,size,type);var u=new JsonObject();u.addProperty("name",name);u.addProperty("type",type.get(0));u.addProperty("size",size.get(0));
                if(type.get(0)==GL20.GL_SAMPLER_2D || type.get(0)==GL20.GL_SAMPLER_2D_SHADOW) {
                    int location=GL20.glGetUniformLocation(priorProgram,name);int unit=GL20.glGetUniformi(priorProgram,location);u.addProperty("unit",unit);GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);int sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);u.addProperty("texture",texture);u.addProperty("sampler",sampler);
                    for(int pname:new int[]{GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_TEXTURE_MAG_FILTER,GL14.GL_TEXTURE_COMPARE_MODE,GL14.GL_TEXTURE_COMPARE_FUNC,GL11.GL_TEXTURE_WRAP_S,GL11.GL_TEXTURE_WRAP_T})u.addProperty("effective_"+pname,sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,pname):GL33.glGetSamplerParameteri(sampler,pname));
                    for(int pname:new int[]{GL12.GL_TEXTURE_BASE_LEVEL,GL12.GL_TEXTURE_MAX_LEVEL})u.addProperty("texture_"+pname,GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,pname));
                    for(int pname:new int[]{GL12.GL_TEXTURE_MIN_LOD,GL12.GL_TEXTURE_MAX_LOD,GL14.GL_TEXTURE_LOD_BIAS})u.addProperty("effective_"+pname,sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,pname):GL33.glGetSamplerParameterf(sampler,pname));
                    boolean aniso=GL.getCapabilities().GL_EXT_texture_filter_anisotropic;u.addProperty("anisotropyExtension",aniso);if(aniso)u.addProperty("effectiveAnisotropy",sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT):GL33.glGetSamplerParameterf(sampler,EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT));
                    int highestLevel=Math.min(15,31-Integer.numberOfLeadingZeros(GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE)));u.addProperty("highestTextureLevelQueried",highestLevel);var levels=new JsonArray();for(int level=0;level<=highestLevel;level++){var l=new JsonObject();l.addProperty("level",level);l.addProperty("width",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,level,GL11.GL_TEXTURE_WIDTH));l.addProperty("height",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,level,GL11.GL_TEXTURE_HEIGHT));l.addProperty("internalFormat",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,level,GL11.GL_TEXTURE_INTERNAL_FORMAT));levels.add(l);}u.add("levels0ThroughHardwareBoundedMaximum",levels);
                }uniforms.add(u);
            }r.add("activeUniformsIncludingAllSamplers",uniforms);
            for(int flag:new int[]{GL13.GL_SAMPLE_ALPHA_TO_COVERAGE,GL13.GL_SAMPLE_ALPHA_TO_ONE,GL13.GL_MULTISAMPLE,GL13.GL_SAMPLE_COVERAGE,GL32.GL_SAMPLE_MASK,GL11.GL_DEPTH_TEST,GL11.GL_STENCIL_TEST,GL11.GL_BLEND,GL30.GL_RASTERIZER_DISCARD})r.addProperty("enabled_"+flag,GL11.glIsEnabled(flag));
            r.addProperty("sampleBuffers",GL11.glGetInteger(GL13.GL_SAMPLE_BUFFERS));r.addProperty("samples",GL11.glGetInteger(GL13.GL_SAMPLES));r.addProperty("sampleCoverageValue",GL11.glGetFloat(GL13.GL_SAMPLE_COVERAGE_VALUE));r.addProperty("sampleCoverageInvert",GL11.glGetBoolean(GL13.GL_SAMPLE_COVERAGE_INVERT));r.addProperty("sampleMaskWord0",GL30.glGetIntegeri(GL32.GL_SAMPLE_MASK_VALUE,0));r.addProperty("depthFunc",GL11.glGetInteger(GL11.GL_DEPTH_FUNC));r.addProperty("depthWrite",GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));r.addProperty("drawFramebuffer",priorFbo);
        } catch(Exception | LinkageError e) {r.addProperty("failure",e.toString());}
        finally {GL13.glActiveTexture(priorUnit);r.addProperty("glErrorBefore",errorBefore);r.addProperty("glErrorAfter",GL11.glGetError());r.addProperty("activeUnitRestored",GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)==priorUnit);r.addProperty("programUnchanged",GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==priorProgram);r.addProperty("framebufferUnchanged",GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==priorFbo);}
        r.addProperty("samplerQualityQualified",false);OBS.add(role,r);
        if(r.has("failure") || errorBefore!=0 || r.get("glErrorAfter").getAsInt()!=0 || !r.get("activeUnitRestored").getAsBoolean() || !r.get("programUnchanged").getAsBoolean() || !r.get("framebufferUnchanged").getAsBoolean())
            BslEarlyAlphaObserver.failure(new IllegalStateException("native alpha raw observation failed: "+role));
    }
}
