package com.pp.shaderlod.dev;

import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.shader.GlShader;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import org.lwjgl.opengl.*;

/** Private observation of actual pre-detach shader objects, successful link and consuming quad.
 * This grants no production permission and never changes GL state. */
public final class G08CloudProbe {
    private G08CloudProbe() {}
    private static final ThreadLocal<Scope> BUILD=new ThreadLocal<>();
    private static final Map<Object,Owner> OWNERS=new IdentityHashMap<>();
    private static String last="no cloud link";
    private static boolean observing;
    public static boolean observing(){return observing;}
    public static void observe(boolean enabled){
        if(enabled&&(!Boolean.getBoolean("shaderlod.dev.g08CloudRuntime")||System.getProperty("shaderlod.dev.benchmarkControlProbe","").isBlank()))throw new IllegalStateException("G08 observer requires owned benchmark");
        observing=enabled;if(!enabled){G08QueuedCapture.cancel();OWNERS.clear();}
    }
    private static final class Owner {
        final Object renderer,pack,gl;
        final WorldRenderingPipeline pipeline;
        final Program program;
        final String vertex,fragment,sourceName;
        long draws; int counter,framebuffer,workTexture,frameCounter; int[] viewport=new int[4]; boolean fullDraw;
        Owner(Scope s,Program program){renderer=s.renderer;pipeline=s.pipeline;pack=s.source.getParent().getPack();
            gl=GL.getCapabilities();this.program=program;vertex=s.vertex;fragment=s.fragment;sourceName=s.source.getName();}
        boolean current(){return pipeline==Iris.getPipelineManager().getPipelineNullable()
            && pack==Iris.getCurrentPack().orElse(null) && gl==GL.getCapabilities()
            && !Iris.isFallback() && Iris.getShaderPackOptionQueue().isEmpty()
            && GL20.glIsProgram(program.getProgramId());}
    }
    public static final class Scope implements AutoCloseable {
        final Scope previous=BUILD.get();
        final Object renderer; final WorldRenderingPipeline pipeline; final ProgramSource source;
        String vertex,fragment; int linked;
        private Scope(Object renderer,WorldRenderingPipeline pipeline,ProgramSource source){
            this.renderer=renderer;this.pipeline=pipeline;this.source=source;BUILD.set(this);}
        public void complete(Program program){
            if(program!=null&&linked>0&&program.getProgramId()==linked&&vertex!=null&&fragment!=null&&fragment.contains("shaderlodG08DiagnosticSchema")){
                OWNERS.put(renderer,new Owner(this,program));last="pre-detach objects/link/returned program joined";
            }
        }
        @Override public void close(){if(previous==null)BUILD.remove();else BUILD.set(previous);}
    }
    public static Scope open(Object renderer,WorldRenderingPipeline pipeline,ProgramSource source){return new Scope(renderer,pipeline,source);}
    public static boolean wanted(String name){var s=BUILD.get();return s!=null&&s.previous==null&&s.source!=null
        && s.source.getParent()!=null&&s.source.getName().equals(name)&&"deferred1".equals(name)
        && s.source.getFragmentSource().orElse("").contains("shaderlodG08DiagnosticSchema");}
    public static List<String> sources(GlShader[] shaders){
        if(shaders==null||shaders.length!=2)return List.of();
        String vertex=null,fragment=null;
        for(var shader:shaders){
            if(shader==null||!GL20.glIsShader(shader.getHandle())||GL20.glGetShaderi(shader.getHandle(),GL20.GL_COMPILE_STATUS)!=GL11.GL_TRUE)return List.of();
            String text=GL20.glGetShaderSource(shader.getHandle());if(text==null||text.isEmpty()||text.length()>2*1024*1024)return List.of();
            int type=GL20.glGetShaderi(shader.getHandle(),GL20.GL_SHADER_TYPE);
            if(type==GL20.GL_VERTEX_SHADER&&vertex==null)vertex=text;
            else if(type==GL20.GL_FRAGMENT_SHADER&&fragment==null)fragment=text;else return List.of();
        }
        return vertex==null||fragment==null?List.of():List.of(vertex,fragment);
    }
    public static void linked(List<String> stages,int id,boolean unchanged){
        var s=BUILD.get();if(s==null)return;
        if(!unchanged||stages.size()!=2||id<=0||!GL20.glIsProgram(id)||GL20.glGetProgrami(id,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE){last="link observation refused";return;}
        s.vertex=stages.get(0);s.fragment=stages.get(1);s.linked=id;
    }
    public static void draw(Object renderer){
        if(!observing)return;
        var o=OWNERS.get(renderer);if(o==null||!o.current()||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=o.program.getProgramId())return;
        o.draws++;o.counter=SystemTimeUniforms.COUNTER.getAsInt();o.framebuffer=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);o.workTexture=0;
        int buffer=GL11.glGetInteger(GL20.GL_DRAW_BUFFER0);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,o.viewport);
        int location=GL20.glGetUniformLocation(o.program.getProgramId(),"frameCounter");o.frameCounter=location<0?-1:GL20.glGetUniformi(o.program.getProgramId(),location);
        o.fullDraw=!GL30.glIsEnabledi(GL11.GL_BLEND,0)&&!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)&&!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&!GL11.glIsEnabled(GL11.GL_STENCIL_TEST)&&!GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD);
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()){var mask=stack.malloc(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);for(int i=0;i<4;i++)o.fullDraw&=mask.get(i)!=0;}
        if(buffer>=GL30.GL_COLOR_ATTACHMENT0&&buffer<=GL30.GL_COLOR_ATTACHMENT15
            &&GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,buffer,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)==GL11.GL_TEXTURE)
            o.workTexture=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,buffer,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        G08QueuedCapture.afterDraw(o.frameCounter);
    }
    public static void destroy(Object renderer){observing=false;OWNERS.remove(renderer);}
    public static Map<String,Object> status(){
        for(var o:OWNERS.values())if(o.current()){
            var x=new LinkedHashMap<String,Object>();x.put("qualified",true);x.put("observing",observing);x.put("program_source",o.sourceName);x.put("draws",o.draws);x.put("program",o.program.getProgramId());
            x.put("draw_counter",o.counter);x.put("frame_counter",o.frameCounter);x.put("viewport",o.viewport.clone());x.put("full_draw",o.fullDraw);x.put("draw_framebuffer",o.framebuffer);x.put("work_texture",o.workTexture);
            x.put("vertex_source",o.vertex);x.put("fragment_source",o.fragment);
            x.put("scope","Actual shader objects before ProgramCreator detach, successful returned cloud program and completed native quad; branch data requires joined diagnostic texture");return x;
        }
        return Map.of("qualified",false,"last",last,"observing",observing);
    }
}
