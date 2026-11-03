package com.pp.shaderlod.bslprefix;

import com.google.gson.JsonObject;
import java.util.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Private finite current-image support check; it grants no public admission.
 * Explicit gradients strictly inside the shader's MAG domain are checked against
 * exact level0 nearest values and the measured conservative height ceiling.
 * Source/spec reasoning is still required between sampled coordinates. */
public final class BslPrefixSamplerProbe {
    private BslPrefixSamplerProbe() {}
    private static final String SOURCE="""
        #version 430 core
        layout(local_size_x=64) in;
        layout(std430,binding=0) buffer Results {uvec4 result[];};
        uniform sampler2D normalImage;
        uniform ivec4 rect;
        uniform vec2 extent;
        uniform float upper;
        void main(){
          uint i=gl_GlobalInvocationID.x;
          uint nx=uint(rect.z-rect.x-1),ny=uint(rect.w-rect.y-1);
          uint total=nx*ny*64u;if(i>=total)return;
          uint cell=i/64u,pattern=i%64u;
          ivec2 address=rect.xy+ivec2(1)+ivec2(int(cell%nx),int(cell/nx));
          // 16 subtexel positions including boundary-near values, four explicit gradients.
          float frac[4]=float[4](0.00001,0.25,0.75,0.99999);
          vec2 uv=(vec2(address)+vec2(frac[pattern%4u],frac[(pattern/4u)%4u]))/extent;
          float rho[4]=float[4](0.0,0.03125,0.0625,0.124);
          float r=rho[pattern/16u];vec2 gx=vec2(r,0.0)/extent,gy=vec2(0.0,r)/extent;
          float sampled=textureGrad(normalImage,uv,gx,gy).a;
          float fresh=texelFetch(normalImage,ivec2(floor(uv*extent)),0).a;
          uint bad=uint(isnan(sampled)||isinf(sampled)||sampled>upper||sampled<0.0);
          result[i]=uvec4(bad,uint(floatBitsToUint(sampled)!=floatBitsToUint(fresh)),uint(sampled>0.0),floatBitsToUint(sampled));
        }
        """;
    public static JsonObject check(BslPrefixGpuDiagnostic.Result image){
        if(!"1".equals(System.getProperty("shaderlod.dev.g09PrefixFunctional"))
            ||!"1".equals(System.getProperty("shaderlod.dev.g09PrefixAdmissionOnly"))
            ||!BslPrefixRuntime.supported(image.image())||!GL.getCapabilities().OpenGL43)
            throw new IllegalStateException("Exact private current-image sampler probe required");
        var im=image.image();int nativeProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        if(nativeProgram!=im.program())throw new IllegalStateException("Current image owner differs");
        int generic=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING),indexed=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,0);
        long start=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,0),size=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,0);
        int shader=0,program=0,buffer=0;long tested=0,violations=0,nearestDiff=0,calibration=0;
        try{
            shader=GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);GL20.glShaderSource(shader,SOURCE);GL20.glCompileShader(shader);
            if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)!=GL11.GL_TRUE)throw new IllegalStateException("Support compile: "+GL20.glGetShaderInfoLog(shader));
            program=GL20.glCreateProgram();GL20.glAttachShader(program,shader);GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE)throw new IllegalStateException("Support link: "+GL20.glGetProgramInfoLog(program));
            GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"normalImage"),im.unit());GL20.glUniform2f(GL20.glGetUniformLocation(program,"extent"),im.width(),im.height());
            // No texture or sampler binding is changed: actual consuming unit remains native.
            GL13.glActiveTexture(GL13.GL_TEXTURE0+im.unit());
            if(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=im.texture()||GL11.glGetInteger(GL33.GL_SAMPLER_BINDING)!=im.sampler())throw new IllegalStateException("Actual consuming sampler changed");
            buffer=GL15.glGenBuffers();GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffer);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,buffer);
            for(var p:image.pixels()){
                var r=p.rect();int count=Math.multiplyExact(Math.multiplyExact(r.x1()-r.x0()-1,r.y1()-r.y0()-1),64);
                if(count<1||count>262144)throw new IllegalStateException("Bounded interior samples required");
                GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,16L*count,GL15.GL_STREAM_READ);
                GL20.glUniform4i(GL20.glGetUniformLocation(program,"rect"),r.x0(),r.y0(),r.x1(),r.y1());GL20.glUniform1f(GL20.glGetUniformLocation(program,"upper"),BslPrefixCertificate.upperHeight(p.maximumAlpha()));
                GL43.glDispatchCompute((count+63)/64,1,1);GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT|GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
                var values=MemoryUtil.memAllocInt(count*4);
                try{GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,0,values);
                    for(int i=0;i<values.capacity();i+=4){violations+=Integer.toUnsignedLong(values.get(i));nearestDiff+=Integer.toUnsignedLong(values.get(i+1));calibration+=Integer.toUnsignedLong(values.get(i+2));}
                }finally{MemoryUtil.memFree(values);}tested+=count;
            }
        }finally{
            GL20.glUseProgram(nativeProgram);GL13.glActiveTexture(active);
            if(indexed==0||size==0)GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,indexed);
            else GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,0,indexed,start,size);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,generic);
            if(buffer!=0)GL15.glDeleteBuffers(buffer);if(program!=0)GL20.glDeleteProgram(program);if(shader!=0)GL20.glDeleteShader(shader);
        }
        if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=nativeProgram||GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)!=active||GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)!=generic||GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,0)!=indexed||GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,0)!=start||GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,0)!=size)throw new IllegalStateException("Support GL state restoration differs");
        var out=new JsonObject();out.addProperty("sample_count",tested);out.addProperty("ceiling_violations",violations);out.addProperty("nearest_differences",nearestDiff);out.addProperty("zero_ceiling_fault_detections",calibration);out.addProperty("state_restored",true);
        out.addProperty("scope","Finite native image/unit/sampler explicit-gradient MAG samples, exact level0 comparison and deliberately wrong zero-height ceiling calibration. No universal driver proof, implicit-derivative or rendered prefix equivalence claim.");
        if(tested==0||violations!=0||nearestDiff!=0||calibration==0)throw new IllegalStateException("Native sampler support refused: "+out);
        return out;
    }
}
