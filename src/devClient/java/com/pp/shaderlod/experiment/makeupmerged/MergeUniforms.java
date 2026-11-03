package com.pp.shaderlod.experiment.makeupmerged;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import java.nio.IntBuffer;
import java.util.*;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
/** One typed manual TBO binding. Census once after actual native sampler initialization; no observer. */
public final class MergeUniforms implements AutoCloseable {
    public static final String SAMPLER="shaderlod_makeup_region_offsets", ENABLED="shaderlod_makeup_merged_input_enabled", PAGES="shaderlod_makeup_region_page_count";
    private record Plan(int sampler,int enabled,int pages,int unit,int maxPages,int combined,int vertex,int nativeElements,Set<Integer> nativeUnits) {}
    private static final Map<Integer,Plan> admitted=new HashMap<>();
    private static final Set<Integer> rejected=new HashSet<>(),tableReceipts=new HashSet<>();
    private final int program;
    private final Plan plan;
    private int oldActive,oldTexture,oldSamplerObject;
    private boolean bound,closed;
    private MergeUniforms(int program,Plan plan){this.program=program;this.plan=plan;
        // Always assign the unique valid unit, including FALSE control. Unit0 would alias native sampler2D.
        GL20C.glUniform1i(plan.sampler,plan.unit);GL20C.glUniform1i(plan.enabled,0);GL20C.glUniform1i(plan.pages,0);
    }
    public static void forget(int program){admitted.remove(program);rejected.remove(program);tableReceipts.remove(program);}
    public static MergeUniforms open(int program) {
        if(!GL.getCapabilities().OpenGL41 || GL.getCapabilities().glTexBuffer==0 || GL11C.glGetError()!=GL11C.GL_NO_ERROR)return null;
        if(program<=0 || GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM)!=program || rejected.contains(program))return null;
        Plan plan=admitted.get(program);boolean fresh=plan==null;
        if(fresh){plan=census(program);if(plan==null){rejected.add(program);return null;}}
        var uniforms=new MergeUniforms(program,plan);
        if(GL20C.glGetUniformi(program,plan.sampler)!=plan.unit || GL20C.glGetUniformi(program,plan.enabled)!=0 ||
            GL20C.glGetUniformi(program,plan.pages)!=0 || GL11C.glGetError()!=GL11C.GL_NO_ERROR){rejected.add(program);return null;}
        if(fresh){admitted.put(program,plan);System.out.println("[ShaderLOD merged] ABI program="+program+
            " samplerType=usamplerBuffer samplerSize=1 enabledType=bool pagesType=int unit="+plan.unit+
            " nativeSamplerElements="+plan.nativeElements+" nativeUsedUnits="+new TreeSet<>(plan.nativeUnits)+
            " combinedLimit="+plan.combined+" vertexLimit="+plan.vertex+" maxBufferTexels="+plan.maxPages+" enabled=0 pages=0");}
        return uniforms;
    }
    private static Plan census(int program) {
        int sampler=GL20C.glGetUniformLocation(program,SAMPLER),flag=GL20C.glGetUniformLocation(program,ENABLED),pages=GL20C.glGetUniformLocation(program,PAGES);
        if(sampler<0||flag<0||pages<0)return null;
        int combined=GL11C.glGetInteger(GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS),vertex=GL11C.glGetInteger(GL20C.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS);
        int maxPages=GL11C.glGetInteger(GL31C.GL_MAX_TEXTURE_BUFFER_SIZE);
        if(combined<=0||vertex<=0||maxPages<=0)return null;
        Set<Integer> used=new HashSet<>();int elements=0,own=0,ownFlag=0,ownPages=0;
        try(MemoryStack stack=MemoryStack.stackPush()) {
            IntBuffer size=stack.mallocInt(1),type=stack.mallocInt(1);
            int n=GL20C.glGetProgrami(program,GL20C.GL_ACTIVE_UNIFORMS);
            for(int i=0;i<n;i++) {
                String name=GL20C.glGetActiveUniform(program,i,size,type);int t=type.get(0),length=size.get(0),location=GL20C.glGetUniformLocation(program,name);
                if(location==sampler){if(t!=GL31C.GL_UNSIGNED_INT_SAMPLER_BUFFER||length!=1)return null;own++;continue;}
                if(location==flag){if(t!=GL20C.GL_BOOL||length!=1)return null;ownFlag++;}
                if(location==pages){if(t!=GL11C.GL_INT||length!=1)return null;ownPages++;}
                if(!samplerType(t))continue;
                if(length<1||length>combined||elements>combined-length)return null;elements+=length;
                String base=name.endsWith("[0]")?name.substring(0,name.length()-3):name;
                for(int element=0;element<length;element++) {
                    int loc=GL20C.glGetUniformLocation(program,length==1?name:base+"["+element+"]");if(loc<0)return null;
                    int value=GL20C.glGetUniformi(program,loc);if(value<0||value>=combined)return null;used.add(value);
                }
            }
        }
        // GL4.1 has no per-stage active-uniform query. Count ALL native sampler elements against vertex limit.
        // Conservative admission avoids assuming the combined limit is the vertex budget.
        if(own!=1||ownFlag!=1||ownPages!=1)return null;
        int unit=chooseUnit(combined,vertex,elements,used);return unit<0?null:new Plan(sampler,flag,pages,unit,maxPages,combined,vertex,elements,Set.copyOf(used));
    }
    static int chooseUnit(int combined,int vertex,int nativeElements,Set<Integer> used) {
        if(combined<=0||vertex<=0||nativeElements<0||nativeElements>=vertex||nativeElements>=combined)return -1;
        for(int value:used)if(value<0||value>=combined)return -1;
        for(int unit=combined-1;unit>=0;unit--)if(!used.contains(unit))return unit;return -1;
    }
    // Complete desktop GL4.1 scalar and array sampler type set, including signed/unsigned and multisample.
    static boolean samplerType(int t) {
        return (t>=0x8B5D&&t<=0x8B64)||(t>=0x8DC0&&t<=0x8DC5)||(t>=0x8DC9&&t<=0x8DD8)
            ||(t>=0x9108&&t<=0x910D)||(t>=0x900C&&t<=0x900F);
    }
    public void enable(int texture,int buffer,int pageCount) {
        if(closed||bound||texture<=0||buffer<=0||pageCount<=0||pageCount>plan.maxPages)throw new IllegalArgumentException("TBO/page table");
        if(GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM)!=program)throw new IllegalStateException("derivative no longer bound");
        oldActive=GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);GL13C.glActiveTexture(GL13C.GL_TEXTURE0+plan.unit);
        oldTexture=GL11C.glGetInteger(GL31C.GL_TEXTURE_BINDING_BUFFER);oldSamplerObject=GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING);bound=true;
        try {
            GL33C.glBindSampler(plan.unit,0);GL11C.glBindTexture(GL31C.GL_TEXTURE_BUFFER,texture);
            GL31C.glTexBuffer(GL31C.GL_TEXTURE_BUFFER,GL30C.GL_RGBA32UI,buffer);
            int oldRead=GL11C.glGetInteger(GL31C.GL_COPY_READ_BUFFER);long bytes;
            try{GL15C.glBindBuffer(GL31C.GL_COPY_READ_BUFFER,buffer);bytes=GL32C.glGetBufferParameteri64(GL31C.GL_COPY_READ_BUFFER,GL15C.GL_BUFFER_SIZE);}
            finally{GL15C.glBindBuffer(GL31C.GL_COPY_READ_BUFFER,oldRead);}
            // Verify the actual data store and typed attached texel extent before enabling any fetch.
            if(bytes!=(long)pageCount*16 || GL11C.glGetTexLevelParameteri(GL31C.GL_TEXTURE_BUFFER,0,GL11C.GL_TEXTURE_WIDTH)!=pageCount ||
                GL11C.glGetTexLevelParameteri(GL31C.GL_TEXTURE_BUFFER,0,GL11C.GL_TEXTURE_INTERNAL_FORMAT)!=GL30C.GL_RGBA32UI ||
                GL11C.glGetTexLevelParameteri(GL31C.GL_TEXTURE_BUFFER,0,GL31C.GL_TEXTURE_BUFFER_DATA_STORE_BINDING)!=buffer)
                throw new IllegalStateException("TBO typed extent mismatch");
            GL20C.glUniform1i(plan.pages,pageCount);GL20C.glUniform1i(plan.enabled,1);
            // Preserve the original short-circuit query/error-consumption order, with a distinct first failure.
            int actual=GL20C.glGetUniformi(program,plan.sampler);
            if(actual!=plan.unit)throw new IllegalStateException("TBO/uniform sampler unit expected="+plan.unit+" actual="+actual);
            actual=GL20C.glGetUniformi(program,plan.pages);
            if(actual!=pageCount)throw new IllegalStateException("TBO/uniform pages expected="+pageCount+" actual="+actual);
            actual=GL20C.glGetUniformi(program,plan.enabled);
            if(actual!=1)throw new IllegalStateException("TBO/uniform enabled expected=1 actual="+actual);
            int error=GL11C.glGetError();
            if(error!=GL11C.GL_NO_ERROR)throw new IllegalStateException("TBO/uniform GL error=0x"+Integer.toHexString(error));
            if(tableReceipts.add(program))System.out.println("[ShaderLOD merged] table program="+program+" texture="+texture+
                " buffer="+buffer+" unit="+plan.unit+" format=RGBA32UI bytes="+bytes+" texels="+pageCount+" enabled=1 pages="+pageCount);
        }finally{GL13C.glActiveTexture(oldActive);}
    }
    public void disable() {
        if(closed)return;
        GL20C.glUniform1i(plan.enabled,0);GL20C.glUniform1i(plan.pages,0);
        if(bound){GL13C.glActiveTexture(GL13C.GL_TEXTURE0+plan.unit);GL11C.glBindTexture(GL31C.GL_TEXTURE_BUFFER,oldTexture);
            GL33C.glBindSampler(plan.unit,oldSamplerObject);GL13C.glActiveTexture(oldActive);bound=false;}
    }
    public void close(){if(!closed){disable();closed=true;}}
    /** Renderer owns activeProgram, VAO/native device cache, draw submission and cancellation. */
    public static Activation activate(GlProgram<ChunkShaderInterface> original,GlProgram<ChunkShaderInterface> derivative,
        ChunkRenderMatrices matrices,GlFramebuffer framebuffer) {
        if(original==null||derivative==null||original==derivative||matrices==null||framebuffer==null||
            GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM)!=original.handle()||
            GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING)!=framebuffer.getId())return null;
        try {
            original.getInterface().resetState();derivative.bind();derivative.getInterface().setupState();
            framebuffer.bind();derivative.getInterface().setProjectionMatrix(matrices.projection());derivative.getInterface().setModelViewMatrix(matrices.modelView());
            MergeUniforms uniforms=open(derivative.handle());
            if(uniforms!=null)return new Activation(original,derivative,matrices,framebuffer,uniforms);
        }catch(RuntimeException|LinkageError unavailable) {
            // No draw occurred. Native restoration below permits untouched original loop.
        }
        restore(original,derivative,matrices,framebuffer);return null;
    }
    private static void restore(GlProgram<ChunkShaderInterface> original,GlProgram<ChunkShaderInterface> derivative,
        ChunkRenderMatrices matrices,GlFramebuffer framebuffer) {
        try{derivative.getInterface().resetState();}finally {
            original.bind();original.getInterface().setupState();framebuffer.bind();
            original.getInterface().setProjectionMatrix(matrices.projection());original.getInterface().setModelViewMatrix(matrices.modelView());
        }
    }
    public static final class Activation implements AutoCloseable {
        private final GlProgram<ChunkShaderInterface> original,derivative;
        private final ChunkRenderMatrices matrices;private final GlFramebuffer framebuffer;private final MergeUniforms uniforms;
        private boolean closed;
        private Activation(GlProgram<ChunkShaderInterface> original,GlProgram<ChunkShaderInterface> derivative,ChunkRenderMatrices matrices,GlFramebuffer framebuffer,MergeUniforms uniforms) {
            this.original=original;this.derivative=derivative;this.matrices=matrices;this.framebuffer=framebuffer;this.uniforms=uniforms;
        }
        public MergeUniforms uniforms(){return uniforms;}
        public GlProgram<ChunkShaderInterface> program(){return derivative;}
        public void close(){if(!closed){closed=true;try{uniforms.close();}finally{restore(original,derivative,matrices,framebuffer);}}}
    }
}
