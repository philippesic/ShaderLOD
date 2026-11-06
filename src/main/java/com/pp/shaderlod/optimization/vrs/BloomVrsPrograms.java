package com.pp.shaderlod.optimization.vrs;

import com.pp.shaderlod.optimization.sourcecache.CompositeCompilationLinkEvidence;
import com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.shader.*;
import net.irisshaders.iris.pipeline.*;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Only a consumed native-source -> transformed shader objects -> successful live link may own a pass. */
public final class BloomVrsPrograms {
    private BloomVrsPrograms() {}
    private record Context(Object renderer,WorldRenderingPipeline pipeline,RenderTargets targets,
            ProgramSource source,TextureStage stage,IrisCompiledSource.Certificate certificate,
            Object textures,Map<?,?> textureValues,Object gl,Object resources,List<String> selected,int outputTarget) {}
    private record Owner(Context context,int id) {}
    private static final ThreadLocal<Scope> COMPILING = new ThreadLocal<>();
    private static final Map<Program,Owner> OWNERS = new IdentityHashMap<>();
    private static long epoch, published;
    private static String last = "no qualified bloom program";
    public static final class Scope implements AutoCloseable {
        private final Scope previous;
        private final Object renderer;
        private final WorldRenderingPipeline pipeline;
        private final RenderTargets targets;
        private final ProgramSource source;
        private final TextureStage stage;
        private CompositeCompilationLinkEvidence<Context> chain;
        private boolean poisoned;
        private Scope(Object renderer,WorldRenderingPipeline pipeline,RenderTargets targets,ProgramSource source,TextureStage stage) {
            previous = COMPILING.get(); if (previous != null) previous.poison();
            this.renderer=renderer; this.pipeline=pipeline; this.targets=targets; this.source=source; this.stage=stage;
            poisoned = previous != null; COMPILING.set(this);
        }
        private void poison() { poisoned=true; if(chain!=null) chain.poison(); }
        public void complete(Program program) {
            if (poisoned || chain == null || program == null) return;
            var c = chain.complete(program,program.getProgramId());
            if (c != null && GL20.glGetProgrami(program.getProgramId(),GL20.GL_LINK_STATUS) == GL11.GL_TRUE) {
                OWNERS.put(program,new Owner(c,program.getProgramId())); published++; last="native bloom source/shader objects/link verified";
            }
        }
        @Override public void close() { poison(); if(previous==null) COMPILING.remove(); else COMPILING.set(previous); }
    }
    public static Scope open(Object renderer,WorldRenderingPipeline pipeline,RenderTargets targets,ProgramSource source,TextureStage stage) {
        return new Scope(renderer,pipeline,targets,source,stage);
    }
    private static Scope scope() { var s=COMPILING.get(); return s==null||s.poisoned?null:s; }
    public static void raw(String name,String vertex,String geometry,String fragment,TextureStage stage,Object textures) {
        var s=scope(); if(s==null || !BloomVrsRuntime.requested() || !BloomVrsPins.exact() || !BloomVrsSourceProof.PROGRAM.equals(name)) return;
        try {
            if (s.pipeline == null || s.pipeline.getClass() != IrisRenderingPipeline.class || s.targets == null
                    || s.source == null || stage != TextureStage.COMPOSITE_AND_FINAL || s.stage != stage
                    || !name.equals(s.source.getName()) || textures != s.pipeline.getTextureMap()) return;
            var cert = BloomVrsSourceProof.certify(s.source,epoch);
            if (cert == null || !cert.stages().arguments(vertex,geometry,null,null,fragment)) { last="bloom source certificate refused"; return; }
            var client=net.minecraft.client.Minecraft.getInstance();
            var context=new Context(s.renderer,s.pipeline,s.targets,s.source,stage,cert,textures,
                    Collections.unmodifiableMap(new HashMap<>((Map<?,?>)textures)),GL.getCapabilities(),
                    client.getResourceManager(),List.copyOf(client.getResourcePackRepository().getSelectedIds()),
                    BloomVrsSourceProof.outputTarget(cert.input().options().values()));
            s.chain=new CompositeCompilationLinkEvidence<>(context);
        } catch(Exception|LinkageError failed) { s.poison(); last="bloom source refused: "+failed.getClass().getSimpleName(); }
    }
    public static void transformed(Map<PatchShaderType,String> output) {
        var s=scope(); if(s==null||s.chain==null)return;
        if (output==null || output.get(PatchShaderType.GEOMETRY)!=null
                || output.get(PatchShaderType.VERTEX)==null || output.get(PatchShaderType.FRAGMENT)==null) { s.poison(); return; }
        String fragment=output.get(PatchShaderType.FRAGMENT);
        if (fragment.matches("(?s).*\\b(gl_FragDepth|gl_SampleID|gl_SamplePosition|gl_SampleMask|imageStore|imageAtomic\\w*|atomic\\w*)\\b.*")) { s.poison(); return; }
        s.chain.transformed(output.get(PatchShaderType.VERTEX),null,fragment);
    }
    public static void begin(String name,String vertex,String geometry,String fragment) {
        var s=scope();if(s==null||s.chain==null)return;
        if(!BloomVrsSourceProof.PROGRAM.equals(name)){s.poison();return;} s.chain.begin(vertex,geometry,fragment);
    }
    public static void shader(ShaderType type,String source,GlShader shader) {
        var s=scope();if(s==null||s.chain==null)return;
        s.chain.shader(type==ShaderType.VERTEX?CompositeCompilationLinkEvidence.Stage.VERTEX:
                type==ShaderType.FRAGMENT?CompositeCompilationLinkEvidence.Stage.FRAGMENT:null,source,shader);
    }
    public static void linking(String name,GlShader[] shaders) {
        var s=scope();if(s==null||s.chain==null)return;
        if(!BloomVrsSourceProof.PROGRAM.equals(name)||!s.chain.shaders(shaders))s.poison();
    }
    public static void linked(GlShader[] shaders,int id) { var s=scope();if(s!=null&&s.chain!=null)s.chain.linked(shaders,id); }
    public static void builder(Object builder,int id) {var s=scope();if(s!=null&&s.chain!=null)s.chain.builder(builder,id);}
    public static void built(Object builder,Program program,int id) {var s=scope();if(s!=null&&s.chain!=null)s.chain.built(builder,program,id);}
    public static int qualifiedTarget(Object renderer,RenderTargets targets,int id) {
        for(var entry:OWNERS.entrySet()) {
            var o=entry.getValue();var c=o.context();
            if (o.id()!=id || entry.getKey().getProgramId()!=id || c.renderer()!=renderer || c.targets()!=targets) continue;
            var client=net.minecraft.client.Minecraft.getInstance();
            return c.pipeline()==Iris.getPipelineManager().getPipelineNullable() && c.gl()==GL.getCapabilities()
                    && c.resources()==client.getResourceManager() && selectedResourcesMatch(c.selected(),client.getResourcePackRepository().getSelectedIds())
                    && c.textures()==c.pipeline().getTextureMap() && c.textureValues().equals(c.pipeline().getTextureMap())
                    && Iris.getShaderPackOptionQueue().isEmpty() && !Iris.isFallback()
                    && c.certificate().current(Iris.getCurrentPack().orElse(null),Iris.getIrisConfig().getShaderPackName().orElse(null),epoch) ? c.outputTarget() : -1;
        }
        return -1;
    }
    public static boolean qualified(Object renderer,RenderTargets targets,int id){return qualifiedTarget(renderer,targets,id)>=0;}
    // The repository API returns a Collection, not necessarily a List. Preserve exact order/membership.
    static boolean selectedResourcesMatch(List<String> captured,Collection<String> current) {
        if (current == null || captured.size() != current.size()) return false;
        var values=current.iterator();
        for (String expected:captured) if (!values.hasNext() || !Objects.equals(expected,values.next())) return false;
        return !values.hasNext();
    }
    public static void destroyRenderer(Object renderer) { OWNERS.entrySet().removeIf(e->e.getValue().context().renderer()==renderer); }
    public static void invalidate() {epoch++;OWNERS.clear();var s=COMPILING.get();if(s!=null)s.poison();last="invalidated/native";}
    public static Map<String,Object> status(){return Map.of("qualified_programs",OWNERS.size(),"published_links",published,"last",last,"api_pinned",BloomVrsPins.exact());}
}
