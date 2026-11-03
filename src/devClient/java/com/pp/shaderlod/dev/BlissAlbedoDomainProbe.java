package com.pp.shaderlod.dev;

import java.util.*;
import java.util.regex.Pattern;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Unscored owned-resource compute enumeration, never an emission permission. */
final class BlissAlbedoDomainProbe {
    private static final int N=65536;
    private BlissAlbedoDomainProbe() {}
    private static String function(String source,String type,String name){
        var matcher=Pattern.compile("\\b"+type+"\\s+"+name+"\\s*\\(").matcher(source);
        if(!matcher.find())throw new IllegalStateException("Native domain function absent: "+name);
        int start=matcher.start(),brace=source.indexOf('{',matcher.end()),end=-1,depth=0;
        if(brace<0)throw new IllegalStateException("Native domain function body absent");
        for(int i=brace;i<source.length();i++){
            if(source.charAt(i)=='{')depth++;
            if(source.charAt(i)=='}'&&--depth==0){end=i+1;break;}
        }
        if(end<0)throw new IllegalStateException("Native domain function incomplete");
        return source.substring(start,end);
    }
    private static String digest(String text)throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    static Map<String,Object> capture(int nativeProgram,String source,int main,int alt)throws Exception {
        if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=nativeProgram||!GL.getCapabilities().OpenGL43)
            throw new IllegalStateException("Actual native program and compute-capable context required");
        String compact=source.replaceAll("(?s)/\\*.*?\\*/|//[^\\n]*","").replaceAll("\\s+","");
        if(!compact.contains("uniformsampler2Dcolortex1;")
            ||!(compact.contains("vec4data=texelFetch(colortex1,ivec2(gl_FragCoord.xy),0);")||compact.contains("vec4data=texelFetch2D(colortex1,ivec2(gl_FragCoord.xy),0);"))
            ||!compact.contains("vec4dataUnpacked0=vec4(decodeVec2(data.x),decodeVec2(data.y));")
            ||!compact.contains("vec4dataUnpacked1=vec4(decodeVec2(data.z),decodeVec2(data.w));")
            ||!compact.contains("vec3albedo=toLinear(vec3(dataUnpacked0.xz,dataUnpacked1.x));"))
            throw new IllegalStateException("Exact packed albedo dataflow required");
        String decode=function(source,"vec2","decodeVec2"),linear=function(source,"vec3","toLinear");
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),location=GL20.glGetUniformLocation(nativeProgram,"colortex1");
        if(location<0)throw new IllegalStateException("Active packed albedo sampler required");
        int unit=GL20.glGetUniformi(nativeProgram,location),inputTexture,width,height,format,base;
        if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))throw new IllegalStateException("Packed albedo unit outside bound");
        try{
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);inputTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            if(inputTexture<=0||(inputTexture!=main&&inputTexture!=alt))throw new IllegalStateException("Packed albedo owner differs");
            width=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH);
            height=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
            format=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT);
            base=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL);
        }finally{GL13.glActiveTexture(active);}
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        if(format!=GL11.GL_RGBA16||base!=0||width!=2560||height!=1600||!Arrays.equals(viewport,new int[]{0,0,width,height}))
            throw new IllegalStateException("Closed UNORM16/in-bounds native albedo input required");
        int slot=-1;
        for(int i=0;i<GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS);i++)
            if(GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,i)==0){slot=i;break;}
        if(slot<0)throw new IllegalStateException("Unused SSBO slot required; no occupied indexed range is changed");
        int originalBuffer=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int unpackBuffer=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int[] unpackKeys={GL11.GL_UNPACK_ALIGNMENT,GL11.GL_UNPACK_ROW_LENGTH,GL11.GL_UNPACK_SKIP_ROWS,GL11.GL_UNPACK_SKIP_PIXELS,
            GL12.GL_UNPACK_IMAGE_HEIGHT,GL12.GL_UNPACK_SKIP_IMAGES,GL11.GL_UNPACK_SWAP_BYTES,GL11.GL_UNPACK_LSB_FIRST};
        int[] unpackValues=new int[unpackKeys.length];for(int i=0;i<unpackKeys.length;i++)unpackValues[i]=GL11.glGetInteger(unpackKeys[i]);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int originalTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),originalSampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        int texture=0,buffer=0,shader=0,program=0;
        java.nio.ShortBuffer table=null;java.nio.FloatBuffer result=null;
        String compute="#version 430\nlayout(local_size_x=16,local_size_y=16) in;\n"
            +"layout(std430,binding="+slot+") buffer DomainOutput {vec4 values[];};\nuniform sampler2D domainInput;\n"
            +decode+"\n"+linear+"\nvoid main(){ivec2 p=ivec2(gl_GlobalInvocationID.xy);int i=p.y*256+p.x;"
            +"float raw=texelFetch(domainInput,p,0).r;float d=decodeVec2(raw).x;float value=toLinear(vec3(d)).x;"
            +"values[i]=vec4(raw,d,value,float(i));}\n";
        double min=Double.POSITIVE_INFINITY,max=0;int zeros=0;
        try{
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("Prior domain diagnostic GL error");
            shader=GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);GL20.glShaderSource(shader,compute);GL20.glCompileShader(shader);
            if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)!=GL11.GL_TRUE)throw new IllegalStateException("Domain compute compile: "+GL20.glGetShaderInfoLog(shader));
            program=GL20.glCreateProgram();GL20.glAttachShader(program,shader);GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE)throw new IllegalStateException("Domain compute link: "+GL20.glGetProgramInfoLog(program));
            table=MemoryUtil.memAllocShort(N*4);result=MemoryUtil.memAllocFloat(N*4);
            for(int i=0;i<N;i++)for(int c=0;c<4;c++){table.put(i*4+c,(short)i);result.put(i*4+c,Float.NaN);}
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
            for(int i=0;i<unpackKeys.length;i++)GL11.glPixelStorei(unpackKeys[i],i==0?2:0);
            GL33.glBindSampler(0,0);texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL,0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LEVEL,0);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA16,256,256,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_SHORT,table);
            buffer=GL15.glGenBuffers();GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffer);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,result,GL15.GL_DYNAMIC_READ);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,slot,buffer);
            GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"domainInput"),0);
            GL43.glDispatchCompute(16,16,1);GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT|GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
            GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,0,result);
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("Domain enumeration GL error");
            for(int i=0;i<N;i++){
                float raw=result.get(i*4),decoded=result.get(i*4+1),value=result.get(i*4+2),index=result.get(i*4+3);
                if(!Float.isFinite(raw)||!Float.isFinite(decoded)||!Float.isFinite(value)||index!=(float)i
                    ||raw<0||raw>1||Math.abs(raw-(float)(i/65535.0))>1e-6)
                    throw new IllegalStateException("Incomplete/invalid domain enumeration at "+i);
                double absolute=Math.abs((double)value);
                if(absolute!=0&&absolute<4e-38||absolute>1e6)throw new IllegalStateException("Native albedo outside conservative guard at "+i);
                if(absolute==0)zeros++;else min=Math.min(min,absolute);max=Math.max(max,absolute);
            }
            if(!Double.isFinite(min)||max<0.5)throw new IllegalStateException("Nontrivial complete native domain required");
        }finally{
            GL20.glUseProgram(nativeProgram);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,slot,0);GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,originalBuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,originalTexture);GL33.glBindSampler(0,originalSampler);
            for(int i=0;i<unpackKeys.length;i++)GL11.glPixelStorei(unpackKeys[i],unpackValues[i]);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,unpackBuffer);GL13.glActiveTexture(active);
            if(program!=0)GL20.glDeleteProgram(program);if(shader!=0)GL20.glDeleteShader(shader);
            if(buffer!=0)GL15.glDeleteBuffers(buffer);if(texture!=0)GL11.glDeleteTextures(texture);
            if(table!=null)MemoryUtil.memFree(table);if(result!=null)MemoryUtil.memFree(result);
        }
        if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("Domain cleanup GL error");
        var row=new LinkedHashMap<String,Object>();row.put("tested_inputs",N);row.put("complete",true);row.put("zeros",zeros);
        row.put("minimum_nonzero_absolute",min);row.put("maximum_absolute",max);row.put("outside_guard",0);
        row.put("texture",inputTexture);row.put("internal_format",format);row.put("width",width);row.put("height",height);
        row.put("function_source_sha256",digest(decode+"\n"+linear));row.put("compute_source_sha256",digest(compute));
        row.put("scope","Untimed actual-backend enumeration of every RGBA16 UNORM scalar through exact attached native decode.x/cubic functions. Owned table/SSBO only, poisoned outputs and complete index/input coverage. Not branch counts, visual parity or cross-backend proof.");
        return Map.copyOf(row);
    }
}
