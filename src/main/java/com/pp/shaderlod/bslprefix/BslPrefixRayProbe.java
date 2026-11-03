package com.pp.shaderlod.bslprefix;

import com.google.gson.*;
import java.util.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Private GPU stress comparison built from the qualified, actual attached ray functions.
 * Synthetic rays exercise the current real normal atlas. This is not a rendered-pixel capture. */
public final class BslPrefixRayProbe {
 private BslPrefixRayProbe() {}
 private static String once(String s,String a,String b){if(s.indexOf(a)<0||s.indexOf(a)!=s.lastIndexOf(a))throw new IllegalStateException("Ray probe anchor changed: "+a);return s.replace(a,b);}
 private static String raw(String actual,String name){
  String clean=actual.replaceAll("(?s)/\\*.*?\\*/"," ").replaceAll("(?m)//.*$"," ");
  var m=java.util.regex.Pattern.compile("\\b(?:vec[234]|float|bool)\\s+"+java.util.regex.Pattern.quote(name)+"\\s*\\(").matcher(clean);
  while(m.find()){
   int close=clean.indexOf(')',m.end());if(name.equals("GetParallaxCoord")&&!clean.substring(m.end(),close).contains("bool"))continue;
   int begin=m.start(),open=clean.indexOf('{',close),depth=1,i=open+1;if(open<0)break;
   for(;i<clean.length()&&depth>0;i++){char c=clean.charAt(i);if(c=='{')depth++;if(c=='}')depth--;}
   if(depth!=0)break;String result=clean.substring(begin,i).replaceAll("\\s+"," ");
   if(!BslPrefixSourceR8.function(actual,name).equals(BslPrefixSourceR8.function(result,name)))throw new IllegalStateException("Raw ray function join differs");return result;
  }
  throw new IllegalStateException("Raw ray function absent: "+name);
 }
 static String shader(String actual){
  String read=raw(actual,"ReadNormal");read=once(read,"{","{ reads += 1u ;");
  String support=raw(actual,"shaderlodBslPrefixSupport");
  int at=support.indexOf("return all"),end=support.indexOf(';',at);
  if(at<0||end<at)throw new IllegalStateException("Support predicate changed");
  String predicate=support.substring(at+7,end);
  support=support.substring(0,at)+"bool okay = "+predicate+"; if (trackLoop) { if(lastSupport==1 && !okay) supportBits|=1u; if(lastSupport==0 && okay) supportBits|=2u; lastSupport=okay?1:0; } return okay;"+support.substring(end+1);
  String ray=raw(actual,"GetParallaxCoord");
  ray=once(ray,"for (int i = 0; i < shaderlodPomSamples; i++) {","for (int i = 0; i < shaderlodPomSamples; i++) { trackLoop=true; if(shaderlodPrefixReady && currentStep>shaderlodPrefixUpper && currentStep-sampleStep<=shaderlodPrefixUpper) supportBits|=8u;");
  ray=once(ray,"coord += stepDir;","vec2 previousCoord=coord; coord += stepDir; if(any(notEqual(floor(previousCoord),floor(coord))))supportBits|=4u;");
  ray=once(ray,"coord = fract(coord.st)", "trackLoop=false; coord = fract(coord.st)");
  return """
   #version 430 core
   layout(local_size_x=64) in;
   layout(std430,binding=0) buffer Results {uvec4 results[];};
   uniform sampler2D normals;
   uniform vec2 shaderlodBslPrefixImageSize;
   uniform vec4 shaderlodBslPrefixRect0,shaderlodBslPrefixRect1,shaderlodBslPrefixRect2;
   uniform vec3 shaderlodBslPrefixHeights;
   uniform vec4 materialTransform;
   vec4 vTexCoordAM; vec2 vTexCoord; vec3 viewVector; vec2 dcdx,dcdy;
   const int shaderlodBridgeVersion=2,shaderlodEnabled=1,shaderlodPreset=1,shaderlodBslPrefixVersion=1;
   const int shaderlodOptPomEmptyHeightPrefix=1,shaderlodOptPomHeightCache=0;
   int shaderlodBslPrefixActive=0;
   bool shaderlod_optimization_enabled(int id){return id==1;}
   // Exact specialization of the inactive projected-POM peer: native128.
   int shaderlod_bsl_projected_pom(float fade,bool opaque){return 128;}
   uint reads=0u,supportBits=0u;bool trackLoop=false;int lastSupport=-1;
   """+raw(actual,"shaderlodBslPrefixUV")+"\n"+support+"\n"
   +raw(actual,"shaderlodBslPrefixDescriptor")+"\n"
   +raw(actual,"shaderlodBslPrefixCertificate")+"\n"+read+"\n"+ray+"\n"+"""
   void main(){
    uint i=gl_GlobalInvocationID.x;if(i>=4096u)return;
    float positions[8]=float[8](-1.125,0.0,0.0625,0.25,0.5,0.9375,0.99999,2.25);
    float directions[4]=float[4](-32.0,-1.0,0.0,32.0);
    float rho[4]=float[4](0.0,0.03125,0.0625,0.124);
    vTexCoordAM=materialTransform;
    vTexCoord=vec2(positions[(i/4u)%8u],positions[(i/32u)%8u]);
    viewVector=vec3(directions[(i/256u)%4u],directions[(i/1024u)%4u],-1.0);
    dcdx=vec2(rho[i%4u],0.0)/shaderlodBslPrefixImageSize;
    dcdy=vec2(0.0,rho[i%4u])/shaderlodBslPrefixImageSize;
    vec2 initial=fract(vTexCoord)*vTexCoordAM.pq+vTexCoordAM.st;
    float nativeDepth,prefixDepth;
    shaderlodBslPrefixActive=0;reads=0u;trackLoop=false;lastSupport=-1;
    vec2 nativeCoord=GetParallaxCoord(initial,0.0,nativeDepth,true);uint nativeReads=reads;
    shaderlodBslPrefixActive=1;reads=0u;trackLoop=false;lastSupport=-1;supportBits=0u;
    vec2 prefixCoord=GetParallaxCoord(initial,0.0,prefixDepth,true);uint prefixReads=reads;
    bool difference=any(notEqual(floatBitsToUint(nativeCoord),floatBitsToUint(prefixCoord))) || floatBitsToUint(nativeDepth)!=floatBitsToUint(prefixDepth);
    bool nonfinite=any(isnan(nativeCoord))||any(isinf(nativeCoord))||any(isnan(prefixCoord))||any(isinf(prefixCoord))||isnan(nativeDepth)||isinf(nativeDepth)||isnan(prefixDepth)||isinf(prefixDepth);
    uint nativeExit=uint(round((1.0-nativeDepth)*128.0));uint prefixExit=uint(round((1.0-prefixDepth)*128.0));
    results[i]=uvec4(uint(difference)+2u*uint(nonfinite)+4u*uint(prefixReads>nativeReads),nativeReads>=prefixReads?nativeReads-prefixReads:0u,supportBits,uint(nativeExit!=prefixExit));
   }
   """;
 }
 public static JsonObject check(BslPrefixGpuDiagnostic.Result image){
  if(!"1".equals(System.getProperty("shaderlod.dev.g09PrefixFunctional"))||!BslPrefixRuntime.supported(image.image()))throw new IllegalStateException("Private current ray probe required");
  var im=image.image();int nativeProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
  if(nativeProgram!=im.program())throw new IllegalStateException("Current ray owner differs");
  int[] n={0},ids=new int[2];GL20.glGetAttachedShaders(nativeProgram,n,ids);String actual=null;
  if(n[0]!=2)throw new IllegalStateException("Actual native attached functions absent");
  for(int id:ids)if(GL20.glGetShaderi(id,GL20.GL_SHADER_TYPE)==GL20.GL_FRAGMENT_SHADER)actual=GL20.glGetShaderSource(id);
  if(actual==null||!BslPrefixSourceR8.matches(actual,com.pp.shaderlod.optimization.bslprefix.BslPrefixProfile.functions()))throw new IllegalStateException("Actual ray function pins differ");
  String source=shader(actual);
  int generic=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING),indexed=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,0);
  long start=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,0),size=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,0);
  int shader=0,program=0,buffer=0;long rays=0,bad=0,saved=0,exitDiff=0;long[] coverage=new long[4];var perMaterial=new JsonArray();
  try{
   shader=GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
   if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)!=1)throw new IllegalStateException("Ray compile: "+GL20.glGetShaderInfoLog(shader));
   program=GL20.glCreateProgram();GL20.glAttachShader(program,shader);GL20.glLinkProgram(program);
   if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=1)throw new IllegalStateException("Ray link: "+GL20.glGetProgramInfoLog(program));
   GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"normals"),im.unit());GL20.glUniform2f(GL20.glGetUniformLocation(program,"shaderlodBslPrefixImageSize"),im.width(),im.height());
   GL13.glActiveTexture(GL13.GL_TEXTURE0+im.unit());
   if(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=im.texture()||GL11.glGetInteger(GL33.GL_SAMPLER_BINDING)!=im.sampler())throw new IllegalStateException("Native ray sampler differs");
   for(int j=0;j<3;j++){var r=image.pixels().get(j).rect();GL20.glUniform4f(GL20.glGetUniformLocation(program,"shaderlodBslPrefixRect"+j),r.x0(),r.y0(),r.x1(),r.y1());}
   GL20.glUniform3f(GL20.glGetUniformLocation(program,"shaderlodBslPrefixHeights"),BslPrefixCertificate.upperHeight(image.pixels().get(0).maximumAlpha()),BslPrefixCertificate.upperHeight(image.pixels().get(1).maximumAlpha()),BslPrefixCertificate.upperHeight(image.pixels().get(2).maximumAlpha()));
   buffer=GL15.glGenBuffers();GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffer);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,buffer);
   for(var material:image.pixels()){
    var r=material.rect();GL20.glUniform4f(GL20.glGetUniformLocation(program,"materialTransform"),r.x0()/(float)im.width(),r.y0()/(float)im.height(),(r.x1()-r.x0()+1)/(float)im.width(),(r.y1()-r.y0()+1)/(float)im.height());
    GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,4096L*16,GL15.GL_STREAM_READ);GL43.glDispatchCompute(64,1,1);GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT|GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
    long materialSaved=0,materialBad=0;var values=MemoryUtil.memAllocInt(4096*4);
    try{GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,0,values);for(int k=0;k<values.capacity();k+=4){materialBad+=Integer.toUnsignedLong(values.get(k));materialSaved+=Integer.toUnsignedLong(values.get(k+1));int bits=values.get(k+2);for(int q=0;q<4;q++)if((bits&(1<<q))!=0)coverage[q]++;exitDiff+=Integer.toUnsignedLong(values.get(k+3));}}
    finally{MemoryUtil.memFree(values);}
    rays+=4096;bad+=materialBad;saved+=materialSaved;var row=new JsonObject();row.add("rect",new Gson().toJsonTree(r));row.addProperty("rays",4096);row.addProperty("mismatch_codes",materialBad);row.addProperty("omitted_ReadNormal_expressions",materialSaved);perMaterial.add(row);
   }
  }finally{
   GL20.glUseProgram(nativeProgram);GL13.glActiveTexture(active);if(indexed==0||size==0)GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,indexed);else GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,0,indexed,start,size);GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,generic);
   if(buffer!=0)GL15.glDeleteBuffers(buffer);if(program!=0)GL20.glDeleteProgram(program);if(shader!=0)GL20.glDeleteShader(shader);
  }
  if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=nativeProgram||GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)!=active||GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)!=generic||GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,0)!=indexed||GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,0)!=start||GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,0)!=size)throw new IllegalStateException("Ray state restoration differs");
  var out=new JsonObject();out.addProperty("rays",rays);out.addProperty("mismatch_codes",bad);out.addProperty("exit_iteration_differences",exitDiff);out.addProperty("omitted_ReadNormal_expressions",saved);out.add("coverage_exit_reentry_wrap_upper_transition",new Gson().toJsonTree(coverage));out.add("materials",perMaterial);out.addProperty("probe_compute_source",source);out.addProperty("actual_fragment_sha256",BslPrefixSourceR8.hash(actual));out.addProperty("state_restored",true);out.addProperty("scope","Actual pinned driver ray function tokens, current real normal atlas and native sampler; synthetic rays compare returned coordinate/depth/derived exit iteration, positive omitted ReadNormal expressions, wrap/support transitions. Projected peer specialized to its inactive native128 result, initial-height cache disabled. Not rendered-pixel equivalence or physical fetch savings.");
  if(rays!=12288||bad!=0||exitDiff!=0||saved==0||Arrays.stream(coverage).anyMatch(v->v==0))throw new IllegalStateException("Private ray comparison refused: "+out);
  return out;
 }
}
