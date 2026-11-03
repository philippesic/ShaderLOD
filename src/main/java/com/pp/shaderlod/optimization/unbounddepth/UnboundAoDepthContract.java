package com.pp.shaderlod.optimization.unbounddepth;
/** Scalar resource proof. Positive synthetic fixtures are contracts, not GL observations. */
public final class UnboundAoDepthContract {
 private UnboundAoDepthContract(){}
 public record Sampler(int texture,int ownedTexture,int width,int height,int ownerWidth,int ownerHeight,
  int level1Width,int level1Height,int format,int depthBits,int depthType,int compare,int min,int mag,
  int base,int max,int wrapS,int wrapT,float bias,float minLod,float maxLod,float anisotropy){}
 public static boolean sampler(Sampler s){return s!=null&&s.texture()>0&&s.texture()==s.ownedTexture()&&
  s.width()>0&&s.height()>0&&s.width()==s.ownerWidth()&&s.height()==s.ownerHeight()&&
  s.level1Width()==0&&s.level1Height()==0&&
  ((s.format()==0x81A5&&s.depthBits()==16&&s.depthType()==0x8C17)||
   (s.format()==0x81A6&&s.depthBits()==24&&s.depthType()==0x8C17)||
   (s.format()==0x81A7&&s.depthBits()==32&&s.depthType()==0x8C17)||
   (s.format()==0x8CAC&&s.depthBits()==32&&s.depthType()==0x1406))&&
  s.compare()==0&&(s.min()==0x2600||s.min()==0x2601)&&s.min()==s.mag()&&s.base()==0&&s.max()>=0&&
  s.wrapS()==0x812F&&s.wrapT()==0x812F&&s.bias()==0&&s.minLod()<=0&&s.maxLod()>=0&&
  Float.isFinite(s.minLod())&&Float.isFinite(s.maxLod())&&s.anisotropy()==1;}
 public static boolean viewport(int x,int y,int w,int h,float width,float height){return x==0&&y==0&&w>0&&h>0&&w==width&&h==height;}
 /** Classification only. Current full source/API/profile/sampler proof still gates permission. */
 public static int sourceRole(String name,String fragment){
  if(fragment==null||!fragment.contains("shaderlodUnboundAODepthSamplingQualified"))return -1;
  if("deferred1".equals(name))return 0;
  return "deferred".equals(name)&&fragment.contains("shaderlodUnboundAoKernel")?1:-1;
 }
 public static boolean viewport(int x,int y,int w,int h,float width,float height,boolean halfProducer){
  if(!halfProducer)return viewport(x,y,w,h,width,height);
  return width>=2&&height>=2&&width<=16384&&height<=16384&&width==(int)width&&height==(int)height&&
   x==0&&y==0&&w==(int)width/2&&h==(int)height/2;
 }
 public static boolean context(Object registeredPipeline,Object currentPipeline,Object registeredTargets,Object currentTargets,
  int registeredProgram,int currentProgram,boolean source,boolean api,boolean active,int bridge,int flags,int feature,int preset){
  return registeredPipeline!=null&&registeredPipeline==currentPipeline&&registeredTargets!=null&&registeredTargets==currentTargets&&
   registeredProgram>0&&registeredProgram==currentProgram&&source&&api&&active&&bridge==2&&flags==1&&feature==1&&preset>=0&&preset<=2;}
}
