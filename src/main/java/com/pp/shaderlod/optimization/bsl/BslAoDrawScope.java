package com.pp.shaderlod.optimization.bsl;
import net.irisshaders.iris.gl.program.Program;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
/** BSL-only render-thread scope around actual native fullscreen draw ABI.
 * No new renderpass/pipeline/buffer allocation and no encoder replay. */
public final class BslAoDrawScope {
 private static Object pipeline;private static Program program;private static boolean active,inside;
 private static Geometry geometry;
 private BslAoDrawScope() {}
 private record Geometry(int vao,int arrayBuffer,int elementBuffer,int mode,int count,int type,long pointer) {}
 public static void begin(Object owner){if(active)throw new IllegalStateException("Nested BSL composite draw scope");pipeline=owner;program=null;active=true;}
 public static void program(Program current){program=current;}
 public static void end(){pipeline=null;program=null;geometry=null;active=inside=false;}
 public static void draw(int mode,int count,int type,long pointer,Runnable original) {
  if(!active||inside||program==null||!BslAoNormalBridge.owns(pipeline,program)){original.run();return;}
  inside=true;
  try {
   geometry=new Geometry(GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
    GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING),mode,count,type,pointer);
   if(!drawShape(mode,count,type,pointer)||geometry.vao()<=0||geometry.elementBuffer()<=0||
      GL15.glGetBufferParameteri(GL15.GL_ELEMENT_ARRAY_BUFFER,GL15.GL_BUFFER_SIZE)<6*(type==GL11.GL_UNSIGNED_SHORT?2:4))
     throw new IllegalStateException("Actual BSL fullscreen native indexed geometry unavailable");
   BslAoNormalBridge.quad(pipeline,program,original);
  } finally {geometry=null;inside=false;}
 }
 public static boolean drawShape(int mode,int count,int type,long pointer){return mode==GL11.GL_TRIANGLES&&count==6&&pointer==0L&&(type==GL11.GL_UNSIGNED_SHORT||type==GL11.GL_UNSIGNED_INT);}
 private static boolean bindings(){return geometry!=null&&GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)==geometry.vao()&&
  GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)==geometry.arrayBuffer()&&GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING)==geometry.elementBuffer();}
 /** Both stages use byte-identical maintained lighting vertex source. Prove the
  * linked native producer's active inputs resolve to the SAME prepared VAO
  * locations/types as the already-prepared actual consumer before reusing it. */
 public static void beforeNative(Program nativeProgram,Program consumer) {
  if(geometry==null)return; // Legacy uses its original FullScreenQuad operation.
  if(!bindings())throw new IllegalStateException("Encoder fullscreen geometry changed before native recovery");
  try(var stack=MemoryStack.stackPush()) {
   var size=stack.mallocInt(1);var type=stack.mallocInt(1);int count=GL20.glGetProgrami(nativeProgram.getProgramId(),GL20.GL_ACTIVE_ATTRIBUTES);
   if(count<=0||count>16)throw new IllegalStateException("Native producer input layout unavailable");
   for(int index=0;index<count;index++) {
    String name=GL20.glGetActiveAttrib(nativeProgram.getProgramId(),index,size,type);int nativeSize=size.get(0),nativeType=type.get(0);
    boolean sameType=false;for(int ci=0;ci<GL20.glGetProgrami(consumer.getProgramId(),GL20.GL_ACTIVE_ATTRIBUTES);ci++){
     String cn=GL20.glGetActiveAttrib(consumer.getProgramId(),ci,size,type);if(cn.equals(name)){sameType=size.get(0)==nativeSize&&type.get(0)==nativeType;break;}
    }
    int location=GL20.glGetAttribLocation(nativeProgram.getProgramId(),name);
    if(!sameType||location<0||GL20.glGetVertexAttribi(location,GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR)!=0||GL20.glGetAttribLocation(consumer.getProgramId(),name)!=location||
      GL20.glGetVertexAttribi(location,GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED)!=GL11.GL_TRUE||
      GL20.glGetVertexAttribi(location,GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING)<=0)
      throw new IllegalStateException("Native AO inputs differ from actual prepared consumer VAO");
   }
  }
 }
 public static void afterNative(){if(geometry!=null&&!bindings())throw new IllegalStateException("Native recovery changed active encoder VAO/index bindings");}
}
