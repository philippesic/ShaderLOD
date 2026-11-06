package com.pp.shaderlod.optimization.makeupprime;
import java.nio.*;
import java.util.Random;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL32C.*;
class C01BoundsTest {
 @Test void coreHasOnePolygonModeWhileCompatibilityRequiresBothFaces(){
  var range=new double[]{0,1};
  assertTrue(C01Bounds.conventionalRaster(range,new int[]{GL_FILL,0},GL_CONTEXT_CORE_PROFILE_BIT));
  assertTrue(C01Bounds.conventionalRaster(range,new int[]{GL_FILL,GL_FILL},GL_CONTEXT_CORE_PROFILE_BIT));
  assertTrue(C01Bounds.conventionalRaster(range,new int[]{GL_FILL,GL_FILL},GL_CONTEXT_COMPATIBILITY_PROFILE_BIT));
  for(int profile:new int[]{GL_CONTEXT_CORE_PROFILE_BIT,GL_CONTEXT_COMPATIBILITY_PROFILE_BIT}){
   assertFalse(C01Bounds.conventionalRaster(range,new int[]{GL_LINE,GL_FILL},profile));
   assertFalse(C01Bounds.conventionalRaster(new double[]{0,.5},new int[]{GL_FILL,GL_FILL},profile));
   assertFalse(C01Bounds.conventionalRaster(new double[]{Double.NaN,1},new int[]{GL_FILL,GL_FILL},profile));
  }
  assertFalse(C01Bounds.conventionalRaster(range,new int[]{GL_FILL,0},GL_CONTEXT_COMPATIBILITY_PROFILE_BIT));
  assertFalse(C01Bounds.conventionalRaster(range,new int[]{GL_FILL,GL_LINE},GL_CONTEXT_COMPATIBILITY_PROFILE_BIT));
  assertFalse(C01Bounds.conventionalRaster(range,new int[]{GL_FILL,GL_FILL},0));
  assertFalse(C01Bounds.conventionalRaster(range,new int[]{GL_FILL,GL_FILL},GL_CONTEXT_CORE_PROFILE_BIT|GL_CONTEXT_COMPATIBILITY_PROFILE_BIT));
 }
 private static ByteBuffer mesh(int material,int section){
  var b=ByteBuffer.allocateDirect(4*28).order(ByteOrder.nativeOrder());
  for(int i=0;i<4;i++){int x=(i&1)==0?262144:786432,y=i<2?262144:786432,z=524288;
   b.putInt(i*28,((x>>>10)&1023)|(((y>>>10)&1023)<<10)|(((z>>>10)&1023)<<20));
   b.putInt(i*28+4,(x&1023)|((y&1023)<<10)|((z&1023)<<20));b.putInt(i*28+16,section<<24);b.putInt(i*28+20,(material+1)<<1);
  }return b;
 }
 @Test void exhaustive20BitDecodeMatchesPinnedShader(){
  for(int value=0;value<1048576;value++){
   int hi=value>>>10,lo=value&1023;
   float expected=value*(32.0f/1048576.0f)-8;
   for(int a=0;a<3;a++)assertEquals(expected,C01MeshBounds.decode(hi<<(10*a),lo<<(10*a),a));
  }
 }
 @Test void exactEncodedBoundsAndFullMeshValidation(){
  var b=mesh(1,173);var bounds=C01MeshBounds.scan(b,28,20,new int[]{4,0,0,0,0,0,0});
  assertNotNull(bounds);assertArrayEquals(new float[]{0,0,8},bounds.min);assertArrayEquals(new float[]{16,16,8},bounds.max);assertEquals(173,bounds.section);
  b.putInt(3*28+16,172<<24);assertNull(C01MeshBounds.scan(b,28,20,new int[]{4,0,0,0,0,0,0}));
  assertNull(C01MeshBounds.scan(mesh(1,0),28,20,new int[]{3,0,0,0,0,0,0}));
  assertNull(C01MeshBounds.scan(mesh(1,0),32,20,new int[]{4,0,0,0,0,0,0}));
 }
 @Test void anyWavingVertexExcludesWholeMesh(){
  for(int id:new int[]{10031,10175,10176,10059,10018}){
   var b=mesh(1,0);b.putInt(3*28+20,((id+1)<<1)|1);assertNull(C01MeshBounds.scan(b,28,20,new int[]{4,0,0,0,0,0,0}));
  }
 }
 @Test void finiteDevOnlyContract(){
  for(String mode:new String[]{"off","join","project","split","query","observe","conditional","conditional_wait","audit","witness","witness_query","witness_wait","deferred","deferred_wait","deferred_query","deferred_observe","witness_deferred","witness_deferred_wait","witness_deferred_query","deferred_cpu","witness_deferred_cpu"})C01Bounds.validate(mode,4,16,64);
  C01Bounds.validate("observe",4,16,20000);C01Bounds.validate("audit",0,1,1);
  assertThrows(IllegalArgumentException.class,()->C01Bounds.validate("on",4,16,1));
  assertThrows(IllegalArgumentException.class,()->C01Bounds.validate("conditional",4,0,1));
  assertThrows(IllegalArgumentException.class,()->C01Bounds.validate("conditional",4,16,20001));
 }
 @Test void clippedOrCameraInsideBoundsFailOpen(){
  var q=new C01BoundsProjection();var p=new Matrix4f().perspective((float)Math.toRadians(75),1.6f,.05f,500);q.matrices(p.get(new float[16]),new Matrix4f().get(new float[16]));float[] out=new float[5];
  assertFalse(q.project(new float[]{-1,-1,-1},new float[]{1,1,1},new float[3],0,0,1920,1200,out));
  assertFalse(q.project(new float[]{1,1,-.01f},new float[]{2,2,.01f},new float[3],0,0,1920,1200,out));
  assertFalse(q.project(new float[]{1,1,1},new float[]{2,2,2},new float[3],0,0,1920,1200,out));
 }
 @Test void projectedFullVolumeEnclosesNativeFloatCornersAndInteriors(){
  var random=new Random(12867);var q=new C01BoundsProjection();int admitted=0;
  for(int trial=0;trial<200;trial++){
   var p=new Matrix4f().perspective((float)Math.toRadians(75),1.6f,.05f,500);
   var m=new Matrix4f().rotateY((random.nextFloat()-.5f)*.5f).rotateX((random.nextFloat()-.5f)*.2f);
   q.matrices(p.get(new float[16]),m.get(new float[16]));
   float[] min={random.nextFloat()*30-15,random.nextFloat()*20-10,-20-random.nextFloat()*150};float[] max={min[0]+16,min[1]+16,min[2]+16};float[] out=new float[5];
   float jx=(random.nextFloat()-.5f)/1920,jy=(random.nextFloat()-.5f)/1200;
   if(!q.project(min,max,new float[3],jx,jy,1920,1200,out))continue;admitted++;
   var combined=new Matrix4f(p).mul(m);
   for(int sample=0;sample<32;sample++){
    float x=sample<8?((sample&1)==0?min[0]:max[0]):min[0]+random.nextFloat()*16;
    float y=sample<8?((sample&2)==0?min[1]:max[1]):min[1]+random.nextFloat()*16;
    float z=sample<8?((sample&4)==0?min[2]:max[2]):min[2]+random.nextFloat()*16;
    for(boolean associated:new boolean[]{false,true}){
     var v=new Vector4f(x,y,z,1);if(associated){m.transform(v);p.transform(v);}else combined.transform(v);
     v.x+=jx*v.w;v.y+=jy*v.w;
     assertTrue(v.x/v.w>=out[0]&&v.x/v.w<=out[2]);assertTrue(v.y/v.w>=out[1]&&v.y/v.w<=out[3]);assertTrue(v.z/v.w>=out[4]);
    }
   }
  }assertTrue(admitted>100);
 }
}
