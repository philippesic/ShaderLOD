package com.pp.shaderlod.experiment.bsl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Private NEVER-TIME diagnostic. One full GPU echo per State/arm lifetime.
 * Does not qualify a source pair or establish hardware-PCF equivalence.
 * Root owns invocation at native field-quad normal return and disposal with
 * that pipeline, on the render thread with the same current GL context. */
public final class BslGpuValueEcho implements AutoCloseable {
    public static final String DRIVER = "Apple|Apple M3 Max|4.1 Metal - 91.7";
    public static final String PRODUCER = "727c7a5a0e099cb431476a256d4e5e6627a818ff00a5e754f5a9a45a25a004a0";
    public static final String FRAGMENT = "48d9858950fa278b26239e15cc04cc8b40f6b351b572a45d5fc525e68ffa0505";
    public static final String VERTEX_SOURCE = """
        #version 330 core
        out float iris_FogFragCoord;
        void main() {
            iris_FogFragCoord = 0.0;
            vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
            gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
        }
        """;
    private int texture, fbo, vao, program;
    private boolean attempted, closed;
    private final Map<String,Object> receipt = new LinkedHashMap<>();
    public Map<String,Object> receipt() { return receipt; }
    public static String fragmentSource(BslFieldSourceRecords.Proof proof) {
        if (proof == null || !PRODUCER.equals(proof.sha256())
            || !PRODUCER.equals(BslFieldSourceRecords.completeDigest(proof.stages())))
            throw new IllegalStateException("ECHO_UNREVIEWED_ACTUAL_PRODUCER");
        String actual = proof.stages().get(BslFieldSourceRecords.FRAGMENT);
        if (!FRAGMENT.equals(sha(actual))) throw new IllegalStateException("ECHO_FRAGMENT_PIN");
        int main = actual.indexOf("void main() {");
        if (main < 0 || actual.indexOf("void main() {", main + 1) >= 0)
            throw new IllegalStateException("ECHO_MAIN_COUNT");
        // Exact actual transformed prefix. Same sampler2D name/return component/
        // explicit level/texel coordinates. No sampling derivatives or filtering.
        return actual.substring(0, main) + """
            void main() {
                ivec2 t = ivec2(gl_FragCoord.xy);
                iris_FragData0 = vec4(texelFetch(shadowtex0, t, 0).r, 0.0, 0.0, 1.0);
            }
            """;
    }
    public float[] run(BslFieldSourceRecords.Proof proof, int rawTexture, int rawSampler,
                       int rawUnit, int expectedProducer, Path evidence) {
        if (closed || attempted) throw new IllegalStateException("ECHO_SINGLE_ATTEMPT_PER_ARM");
        attempted = true; // Failure also consumes the echo budget.
        receipt.put("never_time", true); receipt.put("one_echo_per_arm", true);
        receipt.put("producer_actual_source_sha256", proof == null ? null : proof.sha256());
        receipt.put("producer_program", expectedProducer);
        receipt.put("raw_texture", rawTexture); receipt.put("raw_sampler", rawSampler);
        receipt.put("raw_unit", rawUnit); receipt.put("raw_level", 0);
        Throwable failure = null;
        State before = null;
        try {
            check("ECHO_ENTRY_INHERITED_ERRORS");
            String fragment = fragmentSource(proof);
            String driver=GL11.glGetString(GL11.GL_VENDOR)+"|"+GL11.glGetString(GL11.GL_RENDERER)+"|"+GL11.glGetString(GL11.GL_VERSION);
            receipt.put("driver",driver);
            if(!DRIVER.equals(driver))throw new IllegalStateException("ECHO_UNREVIEWED_DRIVER");
            if (rawUnit < 0 || rawUnit >= GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)
                || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != expectedProducer
                || GL11.glGetInteger(GL40.GL_TRANSFORM_FEEDBACK_BUFFER_ACTIVE) != 0)
                throw new IllegalStateException("ECHO_CURRENT_PRODUCER_OR_TF");
            before = State.read(); receipt.put("state_before", before.map());
            Source source = Source.read(rawUnit);
            receipt.put("source_before", source.map());
            if (source.texture != rawTexture || source.sampler != rawSampler
                || !source.valid()) throw new IllegalStateException("ECHO_RAW_SOURCE_REJECTED");
            int[] color = new int[4]; GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, color);
            if (color[0] == 0 || before.polygon[0] != before.polygon[1])
                throw new IllegalStateException("ECHO_RED_MASK_OR_POLYGON_MODE");
            check("ECHO_PRECONDITION_QUERY");
            allocate(fragment, evidence);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
            GL11.glViewport(0,0,2048,2048);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            for (int cap : State.CAPS) GL11.glDisable(cap);
            for (int i=0;i<before.clip.length;i++) GL11.glDisable(GL30.GL_CLIP_DISTANCE0+i);
            GL30.glBindVertexArray(vao); GL20.glUseProgram(program);
            int location = GL20.glGetUniformLocation(program,"shadowtex0");
            if (location < 0) throw new IllegalStateException("ECHO_MISSING_RAW_SAMPLER");
            GL20.glUniform1i(location,rawUnit);
            // Reobserve actual raw binding. Neither texture nor sampler is rebound
            // for input; allocation restored the unit used for scratch allocation.
            if (!source.equals(Source.read(rawUnit))) throw new IllegalStateException("ECHO_SOURCE_CHANGED_BEFORE_DRAW");
            receipt.put("echo_program",program); receipt.put("echo_texture",texture);
            receipt.put("echo_fbo",fbo); receipt.put("echo_viewport",new int[]{0,0,2048,2048});
            int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
            int[] attachment={
                GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE),
                GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME),
                GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL)};
            int samples=GL11.glGetInteger(GL13.GL_SAMPLES);
            int drawBuffer=GL11.glGetInteger(GL20.GL_DRAW_BUFFER0);
            receipt.put("echo_actual_viewport",viewport);receipt.put("echo_actual_attachment_type_name_level",attachment);
            receipt.put("echo_actual_samples",samples);receipt.put("echo_actual_draw_buffer0",drawBuffer);
            if(!Arrays.equals(viewport,new int[]{0,0,2048,2048})
                || !Arrays.equals(attachment,new int[]{GL11.GL_TEXTURE,texture,0})
                || samples!=0 || drawBuffer!=GL30.GL_COLOR_ATTACHMENT0
                || GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("ECHO_ACTUAL_TARGET_STATE");
            check("ECHO_BEFORE_DRAW");
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            check("ECHO_AFTER_DRAW");
            float[] values=readR32f(texture);
            Source after=Source.read(rawUnit); receipt.put("source_after",after.map());
            if (!source.equals(after)) throw new IllegalStateException("ECHO_SOURCE_CHANGED_AFTER_DRAW");
            receipt.put("success",true);
            return values;
        } catch (RuntimeException | Error problem) {
            failure=problem; receipt.put("success",false);
            receipt.put("failure",problem.toString()); throw problem;
        } finally {
            if (before != null) {
                try {
                    before.restore(); check("ECHO_STATE_RESTORE");
                    State after=State.read(); receipt.put("state_after",after.map());
                    boolean equal=before.map().equals(after.map());
                    // Arrays are recorded as lists in map(), giving content equality.
                    receipt.put("state_restored",equal);
                    if (!equal) throw new IllegalStateException("ECHO_STATE_MISMATCH");
                } catch (RuntimeException | Error restore) {
                    receipt.put("restore_failure",restore.toString()); receipt.put("success",false);
                    if (failure != null) failure.addSuppressed(restore); else throw restore;
                }
            }
        }
    }
    private void allocate(String fragment, Path evidence) {
        if (texture!=0 || program!=0) throw new IllegalStateException("ECHO_REPEATED_ALLOCATION");
        int vs=0,fs=0;
        try {
            vs=compile(GL20.GL_VERTEX_SHADER,VERTEX_SOURCE);
            fs=compile(GL20.GL_FRAGMENT_SHADER,fragment);
            program=GL20.glCreateProgram(); GL20.glAttachShader(program,vs); GL20.glAttachShader(program,fs);
            GL20.glLinkProgram(program); check("ECHO_LINK");
            String linkLog=GL20.glGetProgramInfoLog(program); receipt.put("link_log",linkLog);
            if (GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE)
                throw new IllegalStateException("ECHO_LINK_FAILED:"+linkLog);
            Map<Integer,String> actual=Map.of(GL20.GL_VERTEX_SHADER,GL20.glGetShaderSource(vs),
                                             GL20.GL_FRAGMENT_SHADER,GL20.glGetShaderSource(fs));
            check("ECHO_ACTUAL_SOURCE_BEFORE_DETACH");
            String digest=BslFieldSourceRecords.completeDigest(actual);
            if (digest==null || !VERTEX_SOURCE.equals(actual.get(GL20.GL_VERTEX_SHADER))
                || !fragment.equals(actual.get(GL20.GL_FRAGMENT_SHADER)))
                throw new IllegalStateException("ECHO_ACTUAL_SOURCE_CHANGED");
            receipt.put("echo_actual_pre_detach_source_sha256",digest);
            receipt.put("source_capture_route","GL_getShaderSource_after_successful_link_before_detach");
            if (evidence==null) throw new IllegalStateException("ECHO_EVIDENCE_REQUIRED");
            Files.createDirectories(evidence);
            for (var stage:actual.entrySet())
                Files.writeString(evidence.resolve("gpu-echo-"+digest+"-stage-"+stage.getKey()+".glsl"),stage.getValue());
            GL20.glDetachShader(program,vs); GL20.glDetachShader(program,fs);
            vao=GL30.glGenVertexArrays(); fbo=GL30.glGenFramebuffers(); texture=GL11.glGenTextures();
            int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int unpack=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
            try {
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL,0);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LEVEL,0);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_R32F,2048,2048,0,GL11.GL_RED,GL11.GL_FLOAT,0L);
                check("ECHO_ALLOCATE_R32F");
                if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT)!=GL30.GL_R32F
                    || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)!=2048
                    || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)!=2048)
                    throw new IllegalStateException("ECHO_IMAGE_FORMAT_OR_EXTENT");
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,binding); GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,unpack);
            }
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);
            GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("ECHO_INCOMPLETE_FBO");
            receipt.put("echo_image",new int[]{texture,2048,2048,GL30.GL_R32F,0,0});
            check("ECHO_ALLOCATED");
        } catch (java.io.IOException io) { throw new IllegalStateException("ECHO_SOURCE_EXPORT_FAILED",io);
        } finally {
            if(vs!=0)GL20.glDeleteShader(vs); if(fs!=0)GL20.glDeleteShader(fs);
        }
    }
    private static int compile(int type,String text) {
        int shader=GL20.glCreateShader(type);
        try {
            GL20.glShaderSource(shader,text); GL20.glCompileShader(shader); check("ECHO_COMPILE_"+type);
            if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)!=GL11.GL_TRUE)
                throw new IllegalStateException("ECHO_COMPILE_FAILED_"+type+":"+GL20.glGetShaderInfoLog(shader));
            return shader;
        } catch(RuntimeException|Error failure) {GL20.glDeleteShader(shader);throw failure;}
    }
    /** Reads only a positively queried complete 2048² R32F image, never D16.
     * Saves every pixel-pack parameter used by core glGetTexImage. */
    private static float[] readR32f(int texture) {
        int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] names={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,
            GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST,
            GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES};
        int[] saved=new int[names.length];for(int i=0;i<names.length;i++)saved[i]=GL11.glGetInteger(names[i]);
        check("ECHO_READBACK_BEFORE_SETUP");
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);
            for(int i=0;i<names.length;i++)GL11.glPixelStorei(names[i],i==0?4:0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);check("ECHO_READBACK_PACK_SETUP");
            var buffer=BufferUtils.createFloatBuffer(2048*2048);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RED,GL11.GL_FLOAT,buffer);
            check("ECHO_R32F_READBACK");
            float[] result=new float[2048*2048];buffer.get(result);return result;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,previous);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);
            for(int i=0;i<names.length;i++)GL11.glPixelStorei(names[i],saved[i]);
            check("ECHO_READBACK_RESTORE");
        }
    }
    private static final class Source {
        int texture,sampler; int[] params; float[] border; Float aniso;
        static Source read(int unit) {
            int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
            try {
                Source r=new Source();r.texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                r.sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
                int[] textureNames={GL12.GL_TEXTURE_BASE_LEVEL,GL12.GL_TEXTURE_MAX_LEVEL,
                    GL33.GL_TEXTURE_SWIZZLE_R,GL33.GL_TEXTURE_SWIZZLE_G,GL33.GL_TEXTURE_SWIZZLE_B,GL33.GL_TEXTURE_SWIZZLE_A};
                int[] levelNames={GL11.GL_TEXTURE_WIDTH,GL11.GL_TEXTURE_HEIGHT,GL11.GL_TEXTURE_INTERNAL_FORMAT,
                    GL14.GL_TEXTURE_DEPTH_SIZE,GL30.GL_TEXTURE_DEPTH_TYPE};
                int[] samplerNames={GL14.GL_TEXTURE_COMPARE_MODE,GL14.GL_TEXTURE_COMPARE_FUNC,
                    GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_TEXTURE_WRAP_S,GL11.GL_TEXTURE_WRAP_T};
                r.params=new int[17];int i=0;
                for(int n:textureNames)r.params[i++]=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,n);
                for(int n:levelNames)r.params[i++]=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,n);
                for(int n:samplerNames)r.params[i++]=r.sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,n):GL33.glGetSamplerParameteri(r.sampler,n);
                r.border=new float[4];
                if(r.sampler==0)GL11.glGetTexParameterfv(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_BORDER_COLOR,r.border);
                else GL33.glGetSamplerParameterfv(r.sampler,GL11.GL_TEXTURE_BORDER_COLOR,r.border);
                var caps=GL.getCapabilities();
                if(caps.GL_EXT_texture_filter_anisotropic||caps.GL_ARB_texture_filter_anisotropic)
                    r.aniso=r.sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x84FE):GL33.glGetSamplerParameterf(r.sampler,0x84FE);
                check("ECHO_RAW_UNIT_SAFE_QUERY");return r;
            }finally{GL13.glActiveTexture(active);}
        }
        boolean valid() {
            return texture>0 && sampler>=0 && params[0]==0 && params[1]>=0 && params[2]==GL11.GL_RED
                && params[6]==2048 && params[7]==2048
                && params[8]==GL14.GL_DEPTH_COMPONENT16 && params[9]==16 && params[10]==GL30.GL_UNSIGNED_NORMALIZED
                && params[11]==GL11.GL_NONE && simple(params[13]) && simple(params[14])
                && params[15]==GL12.GL_CLAMP_TO_EDGE && params[16]==GL12.GL_CLAMP_TO_EDGE
                && (aniso==null || aniso==1.0f);
        }
        Map<String,Object> map() {var m=new LinkedHashMap<String,Object>();m.put("texture",texture);m.put("sampler",sampler);
            m.put("params_base_max_rgba_width_height_format_bits_type_compare_func_min_mag_wrapST",Arrays.stream(params).boxed().toList());
            m.put("border",Arrays.toString(border));m.put("anisotropy",aniso);return m;}
        @Override public boolean equals(Object o) {return o instanceof Source s && map().equals(s.map());}
        @Override public int hashCode(){return map().hashCode();}
    }
    public static boolean simple(int filter){return filter==GL11.GL_NEAREST || filter==GL11.GL_LINEAR;}
    private static final class State {
        static final int[] CAPS={GL11.GL_SCISSOR_TEST,GL11.GL_STENCIL_TEST,GL11.GL_DEPTH_TEST,GL11.GL_BLEND,
            GL11.GL_CULL_FACE,GL30.GL_RASTERIZER_DISCARD,GL11.GL_POLYGON_OFFSET_FILL,GL11.GL_COLOR_LOGIC_OP,GL13.GL_SAMPLE_ALPHA_TO_COVERAGE,
            GL13.GL_SAMPLE_ALPHA_TO_ONE,GL13.GL_SAMPLE_COVERAGE,GL11.GL_DITHER,GL30.GL_FRAMEBUFFER_SRGB};
        int program,vao,drawFbo,readFbo,active,texture,sampler,unpack,pack;
        int[] viewport,polygon,packParams; boolean[] enabled,clip;
        static State read() {
            State s=new State();s.program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            s.vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);s.drawFbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            s.readFbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);s.active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            s.texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);s.sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            s.unpack=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);s.pack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            s.viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,s.viewport);
            s.polygon=new int[2];GL11.glGetIntegerv(GL11.GL_POLYGON_MODE,s.polygon);
            int[] packNames={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,
                GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST,GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES};
            s.packParams=new int[8];for(int i=0;i<8;i++)s.packParams[i]=GL11.glGetInteger(packNames[i]);
            s.enabled=new boolean[CAPS.length];for(int i=0;i<CAPS.length;i++)s.enabled[i]=GL11.glIsEnabled(CAPS[i]);
            s.clip=new boolean[GL11.glGetInteger(GL30.GL_MAX_CLIP_DISTANCES)];
            for(int i=0;i<s.clip.length;i++)s.clip[i]=GL11.glIsEnabled(GL30.GL_CLIP_DISTANCE0+i);
            check("ECHO_STATE_SNAPSHOT");return s;
        }
        void restore() {
            GL20.glUseProgram(program);GL30.glBindVertexArray(vao);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,drawFbo);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFbo);
            GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,polygon[0]);
            for(int i=0;i<CAPS.length;i++){if(enabled[i])GL11.glEnable(CAPS[i]);else GL11.glDisable(CAPS[i]);}
            for(int i=0;i<clip.length;i++){if(clip[i])GL11.glEnable(GL30.GL_CLIP_DISTANCE0+i);else GL11.glDisable(GL30.GL_CLIP_DISTANCE0+i);}
            GL13.glActiveTexture(active);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pack);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,unpack);
            int[] names={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,
                GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST,GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES};
            for(int i=0;i<8;i++)GL11.glPixelStorei(names[i],packParams[i]);
        }
        Map<String,Object> map() {
            var m=new LinkedHashMap<String,Object>();m.put("program",program);m.put("vao",vao);m.put("draw_fbo",drawFbo);m.put("read_fbo",readFbo);
            m.put("active_texture",active);m.put("active_unit_texture",texture);m.put("active_unit_sampler",sampler);m.put("pack_pbo",pack);m.put("unpack_pbo",unpack);
            m.put("viewport",Arrays.stream(viewport).boxed().toList());m.put("polygon_mode",Arrays.stream(polygon).boxed().toList());
            m.put("pack",Arrays.stream(packParams).boxed().toList());m.put("enabled",Arrays.toString(enabled));m.put("clip",Arrays.toString(clip));return m;
        }
    }
    /** Keep inherited errors visible, including multiple pending flags. */
    public static void check(String operation) {
        var errors=new ArrayList<Integer>();
        for(int i=0;i<32;i++){int e=GL11.glGetError();if(e==GL11.GL_NO_ERROR)break;errors.add(e);}
        if(!errors.isEmpty())throw new IllegalStateException(operation+"_GL_ERRORS_"+errors);
    }
    public static String sha(String text) {
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
    @Override public void close() {
        if(closed)return;closed=true;
        if(program==0 && vao==0 && fbo==0 && texture==0)return;
        RuntimeException inherited=null;
        try{check("ECHO_DISPOSE_ENTRY_INHERITED_ERRORS");}catch(RuntimeException error){inherited=error;}
        if(program!=0)GL20.glDeleteProgram(program);if(vao!=0)GL30.glDeleteVertexArrays(vao);
        if(fbo!=0)GL30.glDeleteFramebuffers(fbo);if(texture!=0)GL11.glDeleteTextures(texture);
        program=vao=fbo=texture=0;
        try{check("ECHO_DISPOSE");}catch(RuntimeException error){if(inherited==null)throw error;inherited.addSuppressed(error);}
        if(inherited!=null)throw inherited;
    }
}
