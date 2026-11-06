package com.pp.shaderlod.optimization.makeupprime;

import java.util.*;
import java.util.function.Consumer;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Finite dev-only cheap volume query. Native order and prefix are retained. */
public final class C01Bounds {
 private C01Bounds(){}
 private static final int CAPACITY=2048;
 private static String mode="off",label="off",refusal="";
 private static int remaining,prefix=4,groupSize=16,regionIndex,slot,frames,proxy,vao,rectLocation,depthLocation;
 private static long lastSourceEpoch,deadline,nativeRegions,groups,queried,fallback,commands,indices,skippedCommands,skippedIndices,contradictions;
 private static boolean frame;
 private static MakeUpDepthPriming.State state;
 private static final int[] queries=new int[CAPACITY],references=new int[CAPACITY];
 private static final int[] starts=new int[256],ends=new int[256];
 private static final boolean[] eligible=new boolean[256];
 private static final float[][] minima=new float[256][3],maxima=new float[256][3];
 private static final float[] projection=new float[16],model=new float[16],regionOffset=new float[3],jitter=new float[2],zero=new float[3],rectangle=new float[5];
 private static final int[] viewport=new int[4];
 private static final C01BoundsProjection PROJECTOR=new C01BoundsProjection();
 private static MultiDrawBatch scratch;
 private static int preparedGroups,preparedCommands,lastNativeProgram,lastDrawFbo,lastDepthObject;
 private static final double[] depthRange=new double[2];
 private static final int[] polygonMode=new int[2];
 private static int contextProfile;
 private static boolean completeJoin;
 private static C01AsyncWitness witness;
 private static long frameIndicesStart,frameQueriesStart;
 private static final List<Map<String,Object>> records=new ArrayList<>();
 private static final Map<String,Object> source=new LinkedHashMap<>();
 private static final C01DeferredBatch[] deferred=new C01DeferredBatch[256];
 private static int deferredCount,maxDeferred,batchLimit=256;
 private static final boolean[] visible=new boolean[CAPACITY];
 private static long cpuResultReads,cpuHiddenCommands,cpuHiddenIndices;
 private static long auditAvailable,auditUnavailable;
 private static long deferredRegions,deferredFlushes;
 /** The native renderer reacquires its live index buffer/tessellation and region uniforms once per replayed region. */
 @FunctionalInterface public interface Replay {void run(Consumer<Consumer<MultiDrawBatch>> groups);}
 private static boolean deferredMode(){return mode.contains("deferred");}
 private static boolean cpuDecision(){return mode.endsWith("_cpu");}
 private static void enqueue(MultiDrawBatch batch,int count,int firstSlot,Replay replay){long[] cost=C01Cost.begin();try{
  if(deferredCount==deferred.length)flushDeferred();
  var item=deferred[deferredCount];if(item==null)deferred[deferredCount]=item=new C01DeferredBatch();
  item.capture(batch,count,firstSlot,regionIndex,starts,ends,eligible,replay);
  deferredCount++;deferredRegions++;maxDeferred=Math.max(maxDeferred,deferredCount);
  if(deferredCount>=batchLimit)flushDeferred();
 }finally{C01Cost.end("enqueue_copy_inclusive",cost);}}
 private static void unchanged(MultiDrawBatch batch,Consumer<MultiDrawBatch> draw,Replay replay){
  if(deferredMode()&&deferredCount>0)enqueue(batch,0,0,replay);else draw.accept(batch);
 }
 private static void flushDeferred(){long[] cost=C01Cost.begin();try{
  if(deferredCount==0)return;deferredFlushes++;int count=deferredCount;
  try{
   // All current-batch proxy results are collected before ANY queued native draw.
   // Read the LAST issued eligible query first. Completion of that query covers
   // all preceding proxy commands; reading the first query first does not.
   // Native replay below retains its original order, independently of collection.
   if(cpuDecision())for(int i=count-1;i>=0;i--){
    var item=deferred[i];for(int g=item.groups-1;g>=0;g--)if(item.eligible[g]){
     long[] readCost=C01Cost.begin();visible[item.firstSlot+g]=GL15C.glGetQueryObjecti(queries[item.firstSlot+g],GL15C.GL_QUERY_RESULT)!=0;cpuResultReads++;if(readCost!=null)C01Cost.end("result_read_reverse",readCost,Map.of("region",item.region,"group",g,"slot",item.firstSlot+g,"read_sequence",cpuResultReads,"flush",deferredFlushes));
    }
   }
   for(int i=0;i<count;i++){
   var item=deferred[i];
   try{item.replay.run(draw->{if(item.groups==0)draw.accept(item.batch);else drawGroups(item.batch,draw,item.groups,item.firstSlot,item.starts,item.ends,item.eligible,item.region);});}
   finally{item.release();}
  }}finally{for(int i=0;i<count;i++)deferred[i].release();deferredCount=0;}
 }finally{C01Cost.end("flush_inclusive_wait_replay",cost);}}
 public static void validate(String mode,int prefix,int group,int frames){
  if(!Set.of("off","join","project","split","query","observe","conditional","conditional_wait","audit","witness","witness_query","witness_wait","deferred","deferred_wait","deferred_query","deferred_observe","witness_deferred","witness_deferred_wait","witness_deferred_query","deferred_cpu","witness_deferred_cpu").contains(mode)||prefix<0||prefix>256||group<1||group>256||frames<0||frames>20000)throw new IllegalArgumentException("C01 bounds finite mode/prefix/group/frame limits");
 }
 public static void configure(String name,String value,int first,int group,int count){configure(name,value,first,group,count,256);}
 public static void configure(String name,String value,int first,int group,int count,int delay){
  validate(value,first,group,count);if(delay<1||delay>256)throw new IllegalArgumentException("Finite deferred batch limit");stop();batchLimit=delay;label=name;mode=value;prefix=first;groupSize=group;remaining=count;refusal="";C01Cost.reset();
  records.clear();source.clear();cpuResultReads=cpuHiddenCommands=cpuHiddenIndices=0;auditAvailable=auditUnavailable=0;deferredRegions=deferredFlushes=0;maxDeferred=0;nativeRegions=groups=queried=fallback=commands=indices=skippedCommands=skippedIndices=contradictions=0;frames=0;
  deadline=System.nanoTime()+180_000_000_000L;
  if(!value.equals("off")){
   if(!Boolean.getBoolean("shaderlod.dev.c01Bounds")){mode="off";throw new IllegalStateException("Explicit producer cache JVM property required");}
   try{GL15C.glGenQueries(queries);GL15C.glGenQueries(references);scratch=new MultiDrawBatch(1792);createProxy();if(value.startsWith("witness"))witness=new C01AsyncWitness();}
   catch(RuntimeException|Error failure){stop();throw failure;}
  }
 }
 public static void stop(){
  if(witness!=null){witness.close();witness=null;}
  frame=false;remaining=0;mode="off";state=null;
  for(int i=0;i<deferred.length;i++)if(deferred[i]!=null){deferred[i].close();deferred[i]=null;}deferredCount=0;
  for(int i=0;i<CAPACITY;i++){if(queries[i]!=0)GL15C.glDeleteQueries(queries[i]);if(references[i]!=0)GL15C.glDeleteQueries(references[i]);queries[i]=references[i]=0;}
  if(scratch!=null){scratch.delete();scratch=null;}
  if(proxy!=0)GL20C.glDeleteProgram(proxy);if(vao!=0)GL30C.glDeleteVertexArrays(vao);proxy=vao=0;
 }
 private static void createProxy(){
  String vertex="#version 330 core\nuniform vec4 rect;uniform float nearestDepth;void main(){const vec2 p[6]=vec2[6](vec2(0,0),vec2(1,0),vec2(1,1),vec2(0,0),vec2(1,1),vec2(0,1));gl_Position=vec4(mix(rect.xy,rect.zw,p[gl_VertexID]),nearestDepth,1.0);}";
  int vs=compile(GL20C.GL_VERTEX_SHADER,vertex),fs=0;
  try{fs=compile(GL20C.GL_FRAGMENT_SHADER,"#version 330 core\nvoid main(){}");proxy=GL20C.glCreateProgram();GL20C.glAttachShader(proxy,vs);GL20C.glAttachShader(proxy,fs);GL20C.glLinkProgram(proxy);if(GL20C.glGetProgrami(proxy,GL20C.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20C.glGetProgramInfoLog(proxy));}
  finally{GL20C.glDeleteShader(vs);if(fs!=0)GL20C.glDeleteShader(fs);}
  rectLocation=GL20C.glGetUniformLocation(proxy,"rect");depthLocation=GL20C.glGetUniformLocation(proxy,"nearestDepth");vao=GL30C.glGenVertexArrays();
 }
 private static int compile(int type,String text){int id=GL20C.glCreateShader(type);GL20C.glShaderSource(id,text);GL20C.glCompileShader(id);if(GL20C.glGetShaderi(id,GL20C.GL_COMPILE_STATUS)==0){String log=GL20C.glGetShaderInfoLog(id);GL20C.glDeleteShader(id);throw new IllegalStateException(log);}return id;}
 public static boolean active(){return frame;}
 public static void beginFrame(boolean solid){
  if(deferredCount!=0)throw new IllegalStateException("Unflushed C01 native commands");
  frame=false;state=null;regionIndex=slot=0;preparedGroups=0;completeJoin=false;
  if(!solid||mode.equals("off")||remaining<=0||System.nanoTime()>=deadline)return;
  if(!C01Prefix.eligible()){refusal="source/profile/config/world/lifecycle guard";return;}
  remaining--;frames++;C01Cost.frame(frames);frame=true;
  frameIndicesStart=indices;frameQueriesStart=queried;if(witness!=null)witness.begin();
 }
 /** Reproduce only native section command metadata; no vertex scan, hashes or maps. */
 public interface Visibility {int mask(int cx,int cy,int cz,int x,int y,int z);}
 public static void prepare(RenderRegion region,SectionRenderDataStorage storage,ChunkRenderList list,CameraTransform camera,boolean faceCulling,MultiDrawBatch batch,Visibility visibility){long[] cost=C01Cost.begin();try{
  preparedGroups=preparedCommands=0;completeJoin=false;if(!frame)return;
  // Prefix regions are drawn unchanged before any proxy. Their command/bounds join is never consumed.
  if(regionIndex<prefix&&!mode.equals("join"))return;
  var sections=list.sectionsWithGeometryIterator(false);if(sections==null){completeJoin=batch.size==0;return;}
  int sectionCount=0,group=-1;long offset;
  while(sections.hasNext()){
   int section=sections.nextByteAsInt();long pointer=storage.getDataPointer(section);
   int x=region.getChunkX()+LocalSectionIndex.unpackX(section),y=region.getChunkY()+LocalSectionIndex.unpackY(section),z=region.getChunkZ()+LocalSectionIndex.unpackZ(section);
   int mask=(faceCulling?visibility.mask(camera.intX,camera.intY,camera.intZ,x,y,z):127)&SectionRenderDataUnsafe.getSliceMask(pointer);
   if(mask==0)continue;
   if(sectionCount++%groupSize==0){group=preparedGroups++;starts[group]=preparedCommands;eligible[group]=true;Arrays.fill(minima[group],Float.POSITIVE_INFINITY);Arrays.fill(maxima[group],Float.NEGATIVE_INFINITY);}
   var bounds=((C01MeshBounds.Storage)storage).shaderlod$bounds(section);
   if(bounds==null)eligible[group]=false;
   offset=0;
   for(int facing=0;facing<7;facing++){
    if(bounds!=null){
     if(SectionRenderDataUnsafe.getElementCount(pointer,facing)!=(long)(bounds.counts[facing]/4)*6)eligible[group]=false;
     if(facing>0&&SectionRenderDataUnsafe.getVertexOffset(pointer,facing)!=SectionRenderDataUnsafe.getVertexOffset(pointer,0)+offset)eligible[group]=false;
     offset+=bounds.counts[facing];
    }
    if((mask&(1<<facing))==0)continue;
    if(preparedCommands>=batch.size)return;
    if(MemoryUtil.memGetInt(batch.pBaseVertex+4L*preparedCommands)!=(int)SectionRenderDataUnsafe.getVertexOffset(pointer,facing)
       ||Integer.toUnsignedLong(MemoryUtil.memGetInt(batch.pElementCount+4L*preparedCommands))!=SectionRenderDataUnsafe.getElementCount(pointer,facing)
       ||MemoryUtil.memGetAddress(batch.pElementPointer+8L*preparedCommands)!=0)return;
    preparedCommands++;
   }
   // Store section-local extrema plus exact local draw translation; region translation is read at actual draw below.
   if(bounds!=null)for(int a=0;a<3;a++){
    int translation=(a==0?LocalSectionIndex.unpackX(section):a==1?LocalSectionIndex.unpackY(section):LocalSectionIndex.unpackZ(section))*16;
    minima[group][a]=Math.min(minima[group][a],bounds.min[a]+translation);
    maxima[group][a]=Math.max(maxima[group][a],bounds.max[a]+translation);
   }
   ends[group]=preparedCommands;
  }
  completeJoin=preparedCommands==batch.size;
 }finally{C01Cost.end("metadata_bounds_join",cost);}}
 public static void draw(int nativeProgram,MultiDrawBatch batch,Consumer<MultiDrawBatch> nativeDraw,Replay replay){long[] cost=C01Cost.begin();try{
  if(!frame){nativeDraw.accept(batch);return;}
  nativeRegions++;commands+=batch.size;indices+=C01Observation.indices(batch);
  if(mode.equals("join")){nativeDraw.accept(batch);return;}
  if(regionIndex++<prefix){fallback+=batch.size;nativeDraw.accept(batch);return;}
  if(!completeJoin||preparedGroups==0){fallback+=batch.size;unchanged(batch,nativeDraw,replay);return;}
  if(state==null){
   if(!C01Prefix.admit(nativeProgram)||GL15C.glGetQueryi(GL33C.GL_ANY_SAMPLES_PASSED,GL15C.GL_CURRENT_QUERY)!=0
      ||GL15C.glGetQueryi(GL15C.GL_SAMPLES_PASSED,GL15C.GL_CURRENT_QUERY)!=0||GL15C.glGetQueryi(GL30C.GL_PRIMITIVES_GENERATED,GL15C.GL_CURRENT_QUERY)!=(witness==null?0:witness.activeQuery())
      ||GL11C.glGetBoolean(GL40C.GL_TRANSFORM_FEEDBACK_BUFFER_ACTIVE)){
    frame=false;refusal="native program/query/transform feedback guard";nativeDraw.accept(batch);return;
   }
   // Core has one polygon mode for both faces; compatibility has two. Some
   // core drivers write only that one enum. Keep two slots for either ABI, but
   // never treat an untouched second slot as core raster state (GL3.2+/4.6
   // core state tables). Sentinels prevent accepting incomplete query output.
   Arrays.fill(depthRange,Double.NaN);Arrays.fill(polygonMode,0);
   contextProfile=GL11C.glGetInteger(GL32C.GL_CONTEXT_PROFILE_MASK);
   GL11C.glGetDoublev(GL11C.GL_DEPTH_RANGE,depthRange);GL11C.glGetIntegerv(GL11C.GL_POLYGON_MODE,polygonMode);
   if(!conventionalRaster(depthRange,polygonMode,contextProfile)){frame=false;refusal="nonconventional depth range/polygon mode: depth="+Arrays.toString(depthRange)+", polygon="+Arrays.toString(polygonMode)+", profile="+contextProfile;nativeDraw.accept(batch);return;}
   for(int i=0;i<8;i++)if(GL11C.glIsEnabled(GL30C.GL_CLIP_DISTANCE0+i)){frame=false;refusal="clip distance enabled";nativeDraw.accept(batch);return;}
   state=MakeUpDepthPriming.State.capture(nativeProgram,false);
   if(state==null||!loadUniform(nativeProgram,"iris_ProjectionMatrix",projection)||!loadUniform(nativeProgram,"iris_ModelViewMatrix",model)||!loadUniform(nativeProgram,"taaOffset",jitter)){
    frame=false;refusal="native raster/FBO/uniform guard";nativeDraw.accept(batch);return;
   }
   PROJECTOR.matrices(projection,model);GL11C.glGetIntegerv(GL11C.GL_VIEWPORT,viewport);
   lastNativeProgram=nativeProgram;lastDrawFbo=state.drawFbo;lastDepthObject=state.depthObject;lastSourceEpoch=MakeUpDepthPriming.sourceEpoch();
  }
  if(!loadUniform(nativeProgram,"u_RegionOffset",regionOffset)||slot+preparedGroups>CAPACITY){fallback+=batch.size;unchanged(batch,nativeDraw,replay);return;}
  int firstSlot=slot;slot+=preparedGroups;
  boolean cpuOnly=mode.equals("project")||mode.equals("split");
  int oldVao=GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);boolean cull=GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
  if(!cpuOnly){GL11C.glColorMask(false,false,false,false);GL11C.glDepthMask(false);GL11C.glDisable(GL11C.GL_CULL_FACE);GL20C.glUseProgram(proxy);GL30C.glBindVertexArray(vao);}
  try{
   for(int g=0;g<preparedGroups;g++){
    groups++;
    // Two adds: gamma(2)*sum(abs(inputs)). With decoded>=-8 and sectionTranslation>=0,
    // abs(decoded)+translation <= abs(decoded+translation)+16. Both associations are covered.
    for(int a=0;a<3;a++){
     float low=minima[g][a],high=maxima[g][a];double e=2*0x1p-24/(1-2*0x1p-24)*(Math.max(Math.abs(low),Math.abs(high))+Math.abs(regionOffset[a])+16);
     minima[g][a]=Math.nextDown((float)(low+regionOffset[a]-e));maxima[g][a]=Math.nextUp((float)(high+regionOffset[a]+e));
    }
    long[] projectCost=C01Cost.begin();boolean projected=eligible[g]&&PROJECTOR.project(minima[g],maxima[g],zero,jitter[0],jitter[1],viewport[2],viewport[3],rectangle);C01Cost.end("projection",projectCost);if(!projected){eligible[g]=false;continue;}
    if(cpuOnly)continue;
    long[] issueCost=C01Cost.begin();GL20C.glUniform4f(rectLocation,Math.max(-1,rectangle[0]),Math.max(-1,rectangle[1]),Math.min(1,rectangle[2]),Math.min(1,rectangle[3]));GL20C.glUniform1f(depthLocation,rectangle[4]);
    GL15C.glBeginQuery(GL33C.GL_ANY_SAMPLES_PASSED,queries[firstSlot+g]);try{GL11C.glDrawArrays(GL11C.GL_TRIANGLES,0,6);queried++;}finally{GL15C.glEndQuery(GL33C.GL_ANY_SAMPLES_PASSED);C01Cost.end("proxy_issue",issueCost);}
   }
  }finally{if(!cpuOnly){GL30C.glBindVertexArray(oldVao);GL20C.glUseProgram(nativeProgram);if(cull)GL11C.glEnable(GL11C.GL_CULL_FACE);GL11C.glDepthMask(true);GL11C.glColorMask(true,true,true,true);}}
  if(mode.equals("project")){nativeDraw.accept(batch);return;}
  if(deferredMode()){enqueue(batch,preparedGroups,firstSlot,replay);return;}
  drawGroups(batch,nativeDraw,preparedGroups,firstSlot,starts,ends,eligible,regionIndex);
 }finally{C01Cost.end("draw_inclusive_guards_projection_issue_enqueue_native",cost);}}
 private static void drawGroups(MultiDrawBatch batch,Consumer<MultiDrawBatch> nativeDraw,int countGroups,int firstSlot,int[] starts,int[] ends,boolean[] eligible,int regionOrdinal){long[] cost=C01Cost.begin();try{
  for(int g=0;g<countGroups;g++){
   int count=ends[g]-starts[g];if(count==0)continue;
   scratch.clear();scratch.size=count;
   MemoryUtil.memCopy(batch.pElementPointer+8L*starts[g],scratch.pElementPointer,8L*count);MemoryUtil.memCopy(batch.pElementCount+4L*starts[g],scratch.pElementCount,4L*count);MemoryUtil.memCopy(batch.pBaseVertex+4L*starts[g],scratch.pBaseVertex,4L*count);
   if(!eligible[g]){fallback+=count;nativeDraw.accept(scratch);continue;}
   int q=queries[firstSlot+g],reference=references[firstSlot+g];boolean observe=mode.endsWith("observe"),audit=mode.equals("audit");
   // Untimed audit only: availability reads can themselves alter scheduling.
   boolean availableBefore=audit&&GL15C.glGetQueryObjecti(q,GL15C.GL_QUERY_RESULT_AVAILABLE)!=0;
   if(audit){if(availableBefore)auditAvailable++;else auditUnavailable++;}
   int target=observe?GL15C.GL_SAMPLES_PASSED:GL30C.GL_PRIMITIVES_GENERATED;
   if(observe||audit)GL15C.glBeginQuery(target,reference);
   try{
    if(cpuDecision()){
     if(visible[firstSlot+g])nativeDraw.accept(scratch);
     else{cpuHiddenCommands+=count;cpuHiddenIndices+=C01Observation.indices(scratch);}
    }else if(observe||mode.equals("split")||mode.equals("query")||mode.endsWith("_query"))nativeDraw.accept(scratch);
    else{GL30C.glBeginConditionalRender(q,mode.endsWith("_wait")?GL30C.GL_QUERY_WAIT:GL30C.GL_QUERY_NO_WAIT);try{nativeDraw.accept(scratch);}finally{GL30C.glEndConditionalRender();}}
   }finally{if(observe||audit)GL15C.glEndQuery(target);}
   if(observe||audit){
    boolean passed=GL15C.glGetQueryObjecti(q,GL15C.GL_QUERY_RESULT)!=0;long actual=GL33C.glGetQueryObjectui64(reference,GL15C.GL_QUERY_RESULT);long indexCount=C01Observation.indices(scratch);
    if(observe&&!passed&&actual>0)contradictions++;
    if(audit&&actual==0){skippedCommands+=count;skippedIndices+=indexCount;}
    if(records.size()<20000)records.add(Map.of("frame",frames,"region",regionOrdinal,"group",g,"commands",count,"indices",indexCount,"proxy_passed",passed,observe?"native_samples":"actual_primitives",actual));
   }
  }
 }finally{C01Cost.end("groups_inclusive_copy_submit_observer_wait",cost);}}
 private static boolean loadUniform(int program,String name,float[] out){int loc=GL20C.glGetUniformLocation(program,name);if(loc<0)return false;GL20C.glGetUniformfv(program,loc,out);for(float v:out)if(!Float.isFinite(v))return false;return true;}
 static boolean conventionalRaster(double[] range,int[] polygon,int profile){
  return range.length==2&&polygon.length==2&&range[0]==0&&range[1]==1&&polygon[0]==GL11C.GL_FILL
    &&(profile==GL32C.GL_CONTEXT_CORE_PROFILE_BIT
       ||profile==GL32C.GL_CONTEXT_COMPATIBILITY_PROFILE_BIT&&polygon[1]==GL11C.GL_FILL);
 }
 public static void endFrame(int nativeProgram){try{flushDeferred();if(state!=null){state.assertRestored(nativeProgram);MakeUpDepthPriming.assertNoGlError("C01 bounds restore");}}finally{if(witness!=null)witness.finish(frames,indices-frameIndicesStart,queried-frameQueriesStart,frame&&state!=null&&refusal.isEmpty());frame=false;state=null;}}
 /** Explicit untimed diagnostic of the actual attached native sources and last consumed uniforms. */
 public static Map<String,Object> sources(){
  if(com.pp.shaderlod.measurement.RuntimeMeasurement.requested())throw new IllegalStateException("Untimed source diagnostic required");
  if(mode.equals("off")||lastSourceEpoch!=MakeUpDepthPriming.sourceEpoch()||lastNativeProgram==0||!GL20C.glIsProgram(lastNativeProgram))throw new IllegalStateException("No live admitted native program");
  int[] attached=new int[8],count=new int[1];GL20C.glGetAttachedShaders(lastNativeProgram,count,attached);
  var x=new LinkedHashMap<String,Object>();x.put("admission",C01Prefix.sourceSnapshot());x.put("projection",projection.clone());x.put("model_view",model.clone());x.put("taa_offset",jitter.clone());x.put("region_offset",regionOffset.clone());x.put("viewport",viewport.clone());x.put("draw_fbo",lastDrawFbo);x.put("depth_object",lastDepthObject);
  for(int i=0;i<count[0];i++){int type=GL20C.glGetShaderi(attached[i],GL20C.GL_SHADER_TYPE);x.put(type==GL20C.GL_VERTEX_SHADER?"vertex_source":"fragment_source",GL20C.glGetShaderSource(attached[i]));}return x;
 }
 public static Map<String,Object> snapshot(){
  // The last observed admission, without introducing state queries in status.
  var raster=Map.of("depth_range",Arrays.toString(depthRange),"polygon_modes",List.of(polygonMode[0],polygonMode[1]),"context_profile_mask",contextProfile);
  var x=new LinkedHashMap<String,Object>();x.put("last_raster_state",raster);x.put("cost",C01Cost.snapshot());x.put("label",label);x.put("mode",mode);x.put("remaining_frames",remaining);x.put("frames",frames);x.put("refusal",refusal);x.put("source",Map.of("native_program",lastNativeProgram,"position","decoded+regionOffset+sectionTranslation; (Projection*ModelView)*position; exact native taaOffset"));x.put("native_admission",C01Prefix.sourceSnapshot());x.put("draw_fbo",lastDrawFbo);x.put("depth_object",lastDepthObject);x.put("deferred_batch_limit",batchLimit);x.put("audit_available_before_native",auditAvailable);x.put("audit_unavailable_before_native",auditUnavailable);x.put("audit_availability_scope","Untimed audit only; availability read and primitive query can change scheduling. Lean conditional path has neither witness.");x.put("cpu_result_reads",cpuResultReads);x.put("cpu_hidden_commands",cpuHiddenCommands);x.put("cpu_hidden_indices",cpuHiddenIndices);x.put("deferred_regions",deferredRegions);x.put("deferred_flushes",deferredFlushes);x.put("max_deferred_regions",maxDeferred);x.put("pending_deferred_regions",deferredCount);x.put("deferred_scope","Current-frame prefix depth only; preserve native region/group order; copy command metadata, reacquire native tessellation/index buffer and exact region uniforms at replay; no historical query reuse");x.put("native_regions",nativeRegions);x.put("native_commands",commands);x.put("native_indices",indices);x.put("groups",groups);x.put("proxy_queries",queried);x.put("fallback_commands",fallback);x.put("actual_zero_primitive_commands",skippedCommands);x.put("actual_zero_primitive_indices",skippedIndices);x.put("false_hidden_observations",contradictions);x.put("mesh_scans",C01MeshBounds.scans.get());x.put("mesh_admitted",C01MeshBounds.admitted.get());x.put("mesh_waving_excluded",C01MeshBounds.wavingExcluded.get());x.put("records",List.copyOf(records));if(witness!=null)x.put("async_witness",witness.snapshot(remaining<=0));return x;
 }
}
