package com.pp.shaderlod.dev;

import com.google.gson.*;
import com.pp.shaderlod.measurement.RuntimeMeasurement;
import com.pp.shaderlod.optimization.reimaginedao.*;
import com.pp.shaderlod.optimization.unboundao.*;
import com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import org.lwjgl.opengl.*;
import java.nio.*;
import java.util.*;

/** One bounded DEV observation; never changes shader inputs, permission or buffer contents. */
public final class HalfAoUvObservation {
 private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
 private record Source(String vertex,String fragment){}
 private static IrisCompositeCompiledInputs.Family family;
 private static Class<?> runtimeClass;
 private static String prefix;
 private static final Map<Program,Source> sources=new WeakHashMap<>();
 private static boolean quad,armed,pending;private static long deadline;
 private static JsonObject result=new JsonObject();
 private HalfAoUvObservation(){}
 private static Object field(Object object,String name)throws ReflectiveOperationException {
  Class<?> type=object instanceof Class<?> c?c:object.getClass();var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object instanceof Class<?>?null:object);
 }
 private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 private static boolean normal(){
  var pair=RuntimeMeasurement.pair();
  return !System.getProperty("shaderlod.dev.benchmarkTunerProbe","").isBlank()
   &&!RuntimeMeasurement.requested()&&!com.pp.shaderlod.tuning.AutoTuner.active()&&!BenchmarkProfileComparison.active()
   &&(pair==null||!pair.active()&&!pair.restorationPending())&&!com.pp.shaderlod.optimization.RendererWorkDiagnostics.ENABLED
   &&!Boolean.getBoolean("mixin.debug.export")&&!ShaderAnimationClock.fixed();
 }
 public static void compiled(Object scope,Program p){
  if(!normal()||p==null)return;
  try {
   var owners=(Map<?,?>)field(IrisCompositeCompiledInputs.class,"OWNERS");
   if(!owners.containsKey(p))return;
   Object chain=field(scope,"chain");if(chain==null||!Integer.valueOf(6).equals(field(chain,"phase")))return;
   String f=(String)field(chain,"fragment");if(!f.contains("shaderlodReimaginedAoTargetReady")&&!f.contains("shaderlodUnboundAoTargetReady"))return;
   if(sources.size()>8)sources.clear();sources.put(p,new Source((String)field(chain,"vertex"),f));
  }catch(ReflectiveOperationException|RuntimeException ignored){sources.remove(p);}
 }
 public static void arm(){
  require(normal(),"Untimed normal benchmark probe required");require(!armed&&!pending,"Observation already pending");
  try {
   Object pipeline=Iris.getPipelineManager().getPipelineNullable();
   // BTP runs between frames; endComposite deliberately clears the transient Pair.
   // Published link owners persist. Full current Pair/admission is checked at the draw.
   boolean r=false,u=false;
   var owners=(Map<?,?>)field(IrisCompositeCompiledInputs.class,"OWNERS");
   for(Object retained:owners.values()){
    Object context=field(retained,"context");
    if(field(context,"pipeline")!=pipeline)continue;
    Object observedFamily=field(context,"family");
    r|=observedFamily==IrisCompositeCompiledInputs.Family.REIMAGINED;
    u|=observedFamily==IrisCompositeCompiledInputs.Family.UNBOUND;
   }
   require(r!=u,"Exactly one current certified half-AO family required");
   family=r?IrisCompositeCompiledInputs.Family.REIMAGINED:IrisCompositeCompiledInputs.Family.UNBOUND;
   runtimeClass=r?ReimaginedAoRuntime.class:UnboundAoRuntime.class;
   prefix=r?"shaderlodReimaginedAo":"shaderlodUnboundAo";
   require(r?ReimaginedAoApiPins.exact():UnboundAoApiPins.exact(),"Pinned NeoForge1.21.1 quad backend required");
  }catch(ReflectiveOperationException error){throw new IllegalStateException("Current compiled family unavailable",error);}
  result=new JsonObject();result.addProperty("state","armed");result.addProperty("uv_qualified",false);result.addProperty("family",family.name());
  deadline=System.nanoTime()+5_000_000_000L;armed=true;
 }
 public static JsonObject status(){
  require(normal(),"Untimed status required");if(armed&&System.nanoTime()>deadline)fail("No admitted deferred1 draw within five seconds");
  return result.deepCopy();
 }
 public static void quad(boolean value){quad=value;}
 private static void fail(String reason){armed=pending=false;result.addProperty("state","refused");result.addProperty("uv_qualified",false);result.addProperty("error",reason);}
 public static void before(int mode,int count,int type,long offset){
  if(!armed)return;
  if(!normal()){fail("Timing/diagnostic state changed while armed");return;}
  if(System.nanoTime()>deadline){fail("Draw deadline expired");return;}
  if(!quad)return;
  try {
   var pair=(IrisCompositeCompiledInputs.Pair)field(runtimeClass,"compiledPair");
   if(pair==null)return;
   int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(p!=pair.consumer().getProgramId())return;
   armed=false;
   require(pair.family()==family,"Wrong compiled family");
   require(!pair.currentOptions().isEmpty(),"Current captured input certificate required");
   var owners=(Map<?,?>)field(IrisCompositeCompiledInputs.class,"OWNERS");
   require(owners.containsKey(pair.producer())&&owners.containsKey(pair.consumer()),"Published owners missing");
   Object owner=owners.get(pair.consumer());require(Integer.valueOf(p).equals(field(owner,"id")),"Compiled program ID changed");
   Object ownerContext=field(owner,"context");
   require(field(ownerContext,"pipeline")==pair.pipeline()&&field(ownerContext,"renderer")==pair.renderer()&&field(ownerContext,"targets")==pair.targets(),"Compiled owner references differ");
   require(Boolean.TRUE.equals(field(runtimeClass,"activeComposite")),"Outside admitted composite");
   Object epoch=field(runtimeClass,"context");
   long generation=family==IrisCompositeCompiledInputs.Family.REIMAGINED?ReimaginedAoRuntime.sourceGeneration():UnboundAoRuntime.sourceGeneration();
   require(epoch!=null&&Integer.valueOf(p).equals(field(epoch,"consumerProgram"))&&field(epoch,"pipeline")==pair.pipeline()
     &&Long.valueOf(generation).equals(field(epoch,"sourceGeneration"))
     &&Long.valueOf(com.pp.shaderlod.optimization.OptimizationConfig.generation()).equals(field(epoch,"flagGeneration")),"Admitted epoch differs");
   require(pair.pipeline()==Iris.getPipelineManager().getPipelineNullable(),"Pipeline changed");
   require(ReimaginedAoGlProof.integer(p,prefix+"TargetReady")==1,"Consumer not admitted by actual runtime");
   require(mode==GL11.GL_TRIANGLES&&count==6&&offset==0,"Unexpected actual indexed draw");
   var source=sources.get(pair.consumer());require(source!=null,"Successful linked transformed source absent: reload using this DEV build");
   result.addProperty("vertex_source",source.vertex());result.addProperty("fragment_source",source.fragment());
   int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
   var targets=(net.irisshaders.iris.targets.RenderTargets)pair.targets();
   require(Arrays.equals(viewport,new int[]{0,0,targets.getCurrentWidth(),targets.getCurrentHeight()}),"Full zero-origin viewport required");
   int sizeLoc=GL20.glGetUniformLocation(p,prefix+"TargetSize");require(sizeLoc>=0,"Target size absent");float[] size=new float[2];GL20.glGetUniformfv(p,sizeLoc,size);
   require(size[0]==viewport[2]&&size[1]==viewport[3],"Target-size uniform differs");
   int vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),ebo=GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);require(vao>0&&ebo>0,"Actual indexed VAO missing");
   result.addProperty("vao",vao);result.addProperty("ebo",ebo);result.addProperty("program",p);result.addProperty("pass","deferred1");
   result.addProperty("pipeline_reference",System.identityHashCode(pair.pipeline()));result.addProperty("renderer_reference",System.identityHashCode(pair.renderer()));
   result.addProperty("source_generation",generation);result.addProperty("frame_counter",ReimaginedAoGlProof.integer(p,"frameCounter"));
   result.addProperty("mode",mode);result.addProperty("count",count);result.addProperty("index_type",type);result.addProperty("offset",offset);
   result.add("viewport",JSON.toJsonTree(viewport));result.add("actual_target_size_uniform",JSON.toJsonTree(size));result.add("current_options",JSON.toJsonTree(field(ownerContext,"options")));
   result.addProperty("matrix_kind","literal identity in successfully linked transformed vertex source; no GL texture matrix uniform exists");
   result.add("texture_matrix",JSON.toJsonTree(new int[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1}));
   float[][] positions=attribute(p,"Position",3),uvs=attribute(p,"UV0",2);
   float[][] expected={{0,0},{1,0},{1,1},{0,1}};
   for(int i=0;i<4;i++){require(positions[i][0]==expected[i][0]&&positions[i][1]==expected[i][1]&&positions[i][2]==0,"VBO position differs");require(Arrays.equals(uvs[i],expected[i]),"VBO UV differs");}
   int bytes=type==GL11.GL_UNSIGNED_SHORT?2:type==GL11.GL_UNSIGNED_INT?4:type==GL11.GL_UNSIGNED_BYTE?1:0;require(bytes!=0,"Index type unsupported");
   require(GL15.glGetBufferParameteri(GL15.GL_ELEMENT_ARRAY_BUFFER,GL15.GL_BUFFER_SIZE)>=6*bytes,"Index buffer too short");
   var indices=org.lwjgl.BufferUtils.createByteBuffer(6*bytes);GL15.glGetBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER,0,indices);
   int[] actual=new int[6];for(int i=0;i<6;i++)actual[i]=bytes==1?Byte.toUnsignedInt(indices.get(i)):bytes==2?Short.toUnsignedInt(indices.getShort(i*2)):indices.getInt(i*4);
   result.add("indices",JSON.toJsonTree(actual));require(Arrays.equals(actual,new int[]{0,1,2,2,3,0}),"Quad index mapping differs");
   // Fail closed to this exact constant-matrix expression and no second assignment.
   require(!source.vertex().contains("flat out vec2 texCoord")&&!source.fragment().contains("flat in vec2 texCoord"),"Flat UV interpolation refused");
   // The pinned transformer emits optional GLSL float suffixes on these exact constants.
   String v=source.vertex().replaceAll("(?<![\\w.])([012]\\.0)f(?![\\w.])","$1").replaceAll("\\s+","");
   require(v.contains("texCoord=(mat4(1.0)*vec4(UV0,0.0,1.0)).xy;")&&occurs(v,"texCoord=")==1,"Transformed UV expression not exact pinned form");
   require(v.contains("gl_Position=iris_ftransform();")&&occurs(v,"gl_Position=")==1,"Position expression differs");
   require(v.contains("vec4iris_ftransform(){return(mat4(vec4(2.0,0.0,0.0,0.0),vec4(0.0,2.0,0.0,0.0),vec4(0.0),vec4(-1.0,-1.0,0.0,1.0))*mat4(1.0))*vec4(Position,1.0);}"),"Transformed position matrix differs");
   result.addProperty("state","observed_before_gl_call");pending=true;
  }catch(Exception|LinkageError e){fail(e.toString());}
 }
 private static int occurs(String s,String word){return (s.length()-s.replace(word,"").length())/word.length();}
 private static float[][] attribute(int p,String name,int components){
  int a=GL20.glGetAttribLocation(p,name);require(a>=0,"Active attribute absent: "+name);
  int buffer=GL20.glGetVertexAttribi(a,GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
  int stride=GL20.glGetVertexAttribi(a,GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE);
  long offset=GL20.glGetVertexAttribPointer(a,GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER);
  require(GL20.glGetVertexAttribi(a,GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED)==1&&GL20.glGetVertexAttribi(a,GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE)==components
   &&GL20.glGetVertexAttribi(a,GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE)==GL11.GL_FLOAT&&GL20.glGetVertexAttribi(a,GL20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED)==0
   &&GL20.glGetVertexAttribi(a,GL30.GL_VERTEX_ATTRIB_ARRAY_INTEGER)==0&&GL20.glGetVertexAttribi(a,GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR)==0
   &&buffer>0&&stride==20&&offset==(components==3?0:12),"Unexpected attribute format: "+name);
  int before=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);float[][] values=new float[4][components];
  try {
   GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);
   require(GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER,GL15.GL_BUFFER_SIZE)==80,"Expected four POSITION_TEX vertices");
   var data=org.lwjgl.BufferUtils.createByteBuffer(80);GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER,0,data);
   for(int i=0;i<4;i++)for(int j=0;j<components;j++)values[i][j]=data.getFloat((int)offset+i*stride+j*4);
  }finally{GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,before);}
  require(GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)==before,"Array buffer restoration failed");
  var fact=new JsonObject();fact.addProperty("location",a);fact.addProperty("vbo",buffer);fact.addProperty("stride",stride);fact.addProperty("offset",offset);fact.add("values",JSON.toJsonTree(values));result.add(name,fact);return values;
 }
 public static void after(){
  if(!pending)return;
  if(!normal()){fail("Timing state changed at GL return");return;}
  pending=false;result.addProperty("state","observed_gl_call_return");result.addProperty("uv_qualified",true);
  result.addProperty("meaning","Actual admitted draw CPU submission returned; affine texCoord=gl_FragCoord.xy/fullSize from linked constants and actual VBO/indices; no GPU-completion or quality claim");
 }
}
