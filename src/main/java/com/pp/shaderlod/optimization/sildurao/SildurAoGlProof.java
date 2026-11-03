package com.pp.shaderlod.optimization.sildurao;
import net.irisshaders.iris.targets.RenderTargets;import org.lwjgl.opengl.*;import java.util.*;
/** Actual current GL state only; preserves active texture; never allocates, repairs state or reads pixels. */
public final class SildurAoGlProof {
 public record Binding(int texture,int width,int height,int format,int depthBits,int depthType,int base,int min,int mag,int compare,int wrapS,int wrapT,float aniso){}
 public record MaterialBinding(Binding image,int sampler,int maxLevel,int swizzleR,int swizzleG,int swizzleB,int swizzleA,
  int compareFunc,int wrapR,float minLod,float maxLod,float lodBias,float borderR,float borderG,float borderB,float borderA){}
 /** Every proof invocation captures fresh snapshots; runtime compares them across draw boundaries. */
 public record Draw(SildurAoEpoch.Output output,boolean qualified,Binding normal,MaterialBinding material){}
 public record Consumer(Binding field,Binding normal,MaterialBinding material,boolean qualified){}
 private SildurAoGlProof(){}
 public static int integer(int program,String name){int l=GL20.glGetUniformLocation(program,name);return l<0?-1:GL20.glGetUniformi(program,l);}
 private static int parameter(int sampler,int p){return sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,p):GL33.glGetSamplerParameteri(sampler,p);}
 public static Binding binding(int program,String uniform){int unit=integer(program,uniform);if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return null;
  int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);try{GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);if(texture<=0)return null;
   return boundImage(texture,GL11.glGetInteger(GL33.GL_SAMPLER_BINDING));
  }finally{GL13.glActiveTexture(active);}}
 /** Query the selected image with its already captured effective sampler. Caller restores active texture. */
 private static Binding boundImage(int texture,int sampler){var caps=GL.getCapabilities();boolean aniso=caps.GL_EXT_texture_filter_anisotropic||caps.GL_ARB_texture_filter_anisotropic;
   float anisotropy=aniso?(sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x84FE):GL33.glGetSamplerParameterf(sampler,0x84FE)):1;
   int width=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),height=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),format=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT);
   int depthBits=0,depthType=0;
   if(format==GL11.GL_DEPTH_COMPONENT){depthBits=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL14.GL_TEXTURE_DEPTH_SIZE);depthType=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL30.GL_TEXTURE_DEPTH_TYPE);}
   return new Binding(texture,width,height,format,depthBits,depthType,GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL),parameter(sampler,GL11.GL_TEXTURE_MIN_FILTER),parameter(sampler,GL11.GL_TEXTURE_MAG_FILTER),parameter(sampler,GL14.GL_TEXTURE_COMPARE_MODE),parameter(sampler,GL11.GL_TEXTURE_WRAP_S),parameter(sampler,GL11.GL_TEXTURE_WRAP_T),anisotropy);
 }
 /** One complete snapshot; original active texture is restored even on failure. */
 public static MaterialBinding materialBinding(int p){int unit=integer(p,"colortex1");if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return null;
  int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);try{GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
   int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);if(texture<=0)return null;
   int sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);var b=boundImage(texture,sampler);float[] border=new float[4];
   if(sampler==0)GL11.glGetTexParameterfv(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_BORDER_COLOR,border);else GL33.glGetSamplerParameterfv(sampler,GL11.GL_TEXTURE_BORDER_COLOR,border);
   return new MaterialBinding(b,sampler,GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LEVEL),
    GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_R),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_G),
    GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_B),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_A),
    parameter(sampler,GL14.GL_TEXTURE_COMPARE_FUNC),parameter(sampler,GL12.GL_TEXTURE_WRAP_R),
    floating(sampler,GL12.GL_TEXTURE_MIN_LOD),floating(sampler,GL12.GL_TEXTURE_MAX_LOD),floating(sampler,GL14.GL_TEXTURE_LOD_BIAS),border[0],border[1],border[2],border[3]);
  }finally{GL13.glActiveTexture(active);}}
 private static float floating(int sampler,int parameter){return sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,parameter):GL33.glGetSamplerParameterf(sampler,parameter);}
 public static boolean sameMaterial(MaterialBinding produced,MaterialBinding actual){return produced!=null&&produced.equals(actual);}
 private static boolean materialInput(RenderTargets t,MaterialBinding material){var b=material==null?null:material.image();return nativeInput(t,b,1)&&b.format()==GL11.GL_RGB8&&b.wrapS()==GL12.GL_CLAMP_TO_EDGE&&b.wrapT()==GL12.GL_CLAMP_TO_EDGE;}
 private static boolean base(Binding b){return b!=null&&b.base()==0&&b.compare()==GL11.GL_NONE&&b.aniso()==1&&Set.of(GL11.GL_NEAREST,GL11.GL_LINEAR).contains(b.min())&&Set.of(GL11.GL_NEAREST,GL11.GL_LINEAR).contains(b.mag());}
 private static boolean state(int p){java.nio.ByteBuffer mask=org.lwjgl.BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);for(int i=0;i<4;i++)if(mask.get(i)==0)return false;int loc=GL20.glGetUniformLocation(p,"gbufferProjection");float[] matrix=new float[16];if(loc<0)return false;GL20.glGetUniformfv(p,loc,matrix);
  return SildurAoContract.projection(matrix)&&integer(p,"shaderlodBridgeVersion")==2&&integer(p,"shaderlodEnabled")==1&&integer(p,"shaderlodOptimizationFlagsVersion")==1&&integer(p,"shaderlodOptAoRenderScale")==1&&Set.of(0,1,2).contains(integer(p,"shaderlodPreset"))&&integer(p,"frameCounter")>=0&&integer(p,"frameCounter")<16777216&&
   GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==p&&GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==GL11.GL_TRUE&&
   !GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)&&!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&!GL11.glIsEnabled(GL11.GL_STENCIL_TEST)&&!GL11.glIsEnabled(GL11.GL_BLEND)&&!GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD);
 }
 public static Draw producer(RenderTargets t,int p){int w=t.getCurrentWidth(),h=t.getCurrentHeight();var rt=t.get(10);int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
  int tex=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);var depth=binding(p,"depthtex0");var normal=binding(p,"colortex2");var material=materialBinding(p);
  boolean valid=rt!=null&&w>=2&&h>=2&&w<=16384&&h<=16384&&rt.getWidth()==w&&rt.getHeight()==h&&rt.getInternalFormat().name().equals("RGBA32F")&&tex>0&&(tex==rt.getMainTexture()||tex==rt.getAltTexture())&&fbo>0&&
   GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE&&GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)==GL11.GL_TEXTURE&&GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL)==0&&GL11.glGetInteger(GL20.GL_DRAW_BUFFER0)==GL30.GL_COLOR_ATTACHMENT0&&
   SildurAoContract.viewport(v,w,h,true)&&base(depth)&&depth.min()==depth.mag()&&com.pp.shaderlod.optimization.DepthTextureStorage.accepts(depth.format(),depth.depthBits(),depth.depthType())&&depth.texture()==SildurAoDepthId.current(t)&&depth.width()==w&&depth.height()==h&&nativeInput(t,normal,2)&&materialInput(t,material)&&state(p);
  return new Draw(new SildurAoEpoch.Output(tex,fbo,w,h),valid,normal,material);}
 private static boolean fieldSwizzle(int p){int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);try{
  GL13.glActiveTexture(GL13.GL_TEXTURE0+integer(p,"colortex10"));return GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_R)==GL11.GL_RED&&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_G)==GL11.GL_GREEN&&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_B)==GL11.GL_BLUE&&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_A)==GL11.GL_ALPHA;
 }finally{GL13.glActiveTexture(active);}}
 public static Consumer consumer(RenderTargets t,int p){var b=binding(p,"colortex10");var normal=binding(p,"colortex2");var material=materialBinding(p);int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);
  int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);var four=t.get(4);var six=t.get(6);int first=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME),second=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT1,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
  boolean valid=fbo>0&&GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE&&four!=null&&six!=null&&(first==four.getMainTexture()||first==four.getAltTexture())&&(second==six.getMainTexture()||second==six.getAltTexture())&&base(b)&&fieldSwizzle(p)&&b.format()==GL30.GL_RGBA32F&&b.width()==t.getCurrentWidth()&&b.height()==t.getCurrentHeight()&&b.wrapS()==GL12.GL_CLAMP_TO_EDGE&&b.wrapT()==GL12.GL_CLAMP_TO_EDGE&&nativeInput(t,normal,2)&&materialInput(t,material)&&SildurAoContract.viewport(v,b.width(),b.height(),false)&&state(p);return new Consumer(b,normal,material,valid);}
 private static boolean nativeInput(RenderTargets t,Binding b,int index){var target=t.get(index);return target!=null&&base(b)&&b.min()==b.mag()&&(b.texture()==target.getMainTexture()||b.texture()==target.getAltTexture())&&b.width()==t.getCurrentWidth()&&b.height()==t.getCurrentHeight();}
 public static void upload(int p,boolean ready,int w,int h){int loc=GL20.glGetUniformLocation(p,"shaderlodSildurAoTargetReady");if(loc>=0)GL20.glUniform1i(loc,ready?1:0);loc=GL20.glGetUniformLocation(p,"shaderlodSildurAoTargetSize");if(loc>=0)GL20.glUniform2f(loc,ready?w:0,ready?h:0);}
}
