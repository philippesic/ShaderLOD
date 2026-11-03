package com.pp.shaderlod.optimization.astraao;
import net.irisshaders.iris.targets.RenderTargets;import org.lwjgl.opengl.*;import java.util.*;
/** Actual current GL state only; preserves active texture; never allocates, repairs state or reads pixels. */
public final class AstraAoGlProof {
 public record Binding(int texture,int width,int height,int format,int depthBits,int depthType,int base,int min,int mag,int compare,int wrapS,int wrapT,float aniso){}
 public record Draw(AstraAoEpoch.Output output,boolean qualified){}
 private AstraAoGlProof(){}
 public static int integer(int program,String name){int l=GL20.glGetUniformLocation(program,name);return l<0?-1:GL20.glGetUniformi(program,l);}
 private static int parameter(int sampler,int p){return sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,p):GL33.glGetSamplerParameteri(sampler,p);}
 public static Binding binding(int program,String uniform){int unit=integer(program,uniform);if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return null;
  int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);try{GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);if(texture<=0)return null;
   int sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);var caps=GL.getCapabilities();boolean aniso=caps.GL_EXT_texture_filter_anisotropic||caps.GL_ARB_texture_filter_anisotropic;
   float anisotropy=aniso?(sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x84FE):GL33.glGetSamplerParameterf(sampler,0x84FE)):1;
   int width=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),height=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),format=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT);
   int depthBits=0,depthType=0;
   if(format==GL11.GL_DEPTH_COMPONENT){depthBits=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL14.GL_TEXTURE_DEPTH_SIZE);depthType=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL30.GL_TEXTURE_DEPTH_TYPE);}
   return new Binding(texture,width,height,format,depthBits,depthType,GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL),parameter(sampler,GL11.GL_TEXTURE_MIN_FILTER),parameter(sampler,GL11.GL_TEXTURE_MAG_FILTER),parameter(sampler,GL14.GL_TEXTURE_COMPARE_MODE),parameter(sampler,GL11.GL_TEXTURE_WRAP_S),parameter(sampler,GL11.GL_TEXTURE_WRAP_T),anisotropy);
  }finally{GL13.glActiveTexture(active);}}
 private static boolean base(Binding b){return b!=null&&b.base()==0&&b.compare()==GL11.GL_NONE&&b.aniso()==1&&Set.of(GL11.GL_NEAREST,GL11.GL_LINEAR).contains(b.min())&&Set.of(GL11.GL_NEAREST,GL11.GL_LINEAR).contains(b.mag());}
 private static boolean state(int p){java.nio.ByteBuffer mask=org.lwjgl.BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);for(int i=0;i<4;i++)if(mask.get(i)==0)return false;int loc=GL20.glGetUniformLocation(p,"gbufferProjection");float[] matrix=new float[16];if(loc<0)return false;GL20.glGetUniformfv(p,loc,matrix);
  return AstraAoContract.projection(matrix)&&integer(p,"shaderlodBridgeVersion")==2&&integer(p,"shaderlodEnabled")==1&&integer(p,"shaderlodOptimizationFlagsVersion")==1&&integer(p,"shaderlodOptAoRenderScale")==1&&Set.of(0,1,2).contains(integer(p,"shaderlodPreset"))&&integer(p,"frameCounter")>=0&&integer(p,"frameCounter")<16777216&&
   GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==p&&GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==GL11.GL_TRUE&&
   !GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)&&!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&!GL11.glIsEnabled(GL11.GL_STENCIL_TEST)&&!GL11.glIsEnabled(GL11.GL_BLEND)&&!GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD);
 }
 public static Draw producer(RenderTargets t,int p){int w=t.getCurrentWidth(),h=t.getCurrentHeight();var rt=t.get(15);int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
  int tex=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);var depth=binding(p,"depthtex0");
  boolean valid=rt!=null&&w>=2&&h>=2&&w<=16384&&h<=16384&&rt.getWidth()==w&&rt.getHeight()==h&&rt.getInternalFormat().name().equals("RGBA32F")&&tex>0&&(tex==rt.getMainTexture()||tex==rt.getAltTexture())&&fbo>0&&
   GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE&&GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)==GL11.GL_TEXTURE&&GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL)==0&&GL11.glGetInteger(GL20.GL_DRAW_BUFFER0)==GL30.GL_COLOR_ATTACHMENT0&&
   AstraAoContract.viewport(v,w,h,true)&&base(depth)&&depth.min()==depth.mag()&&com.pp.shaderlod.optimization.DepthTextureStorage.accepts(depth.format(),depth.depthBits(),depth.depthType())&&depth.texture()==AstraAoDepthId.current(t)&&depth.width()==w&&depth.height()==h&&state(p);
  return new Draw(new AstraAoEpoch.Output(tex,fbo,w,h),valid);}
 public static boolean consumer(RenderTargets t,int p,int produced){var b=binding(p,"colortex15");int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);
  int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);var one=t.get(0);var four=t.get(5);var normal=t.get(14);int first=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME),second=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT1,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
  int third=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT2,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
  return normal!=null&&(third==normal.getMainTexture()||third==normal.getAltTexture())&&fbo>0&&GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE&&one!=null&&four!=null&&(first==one.getMainTexture()||first==one.getAltTexture())&&(second==four.getMainTexture()||second==four.getAltTexture())&&base(b)&&b.texture()==produced&&b.format()==GL30.GL_RGBA32F&&b.width()==t.getCurrentWidth()&&b.height()==t.getCurrentHeight()&&b.wrapS()==GL12.GL_CLAMP_TO_EDGE&&b.wrapT()==GL12.GL_CLAMP_TO_EDGE&&AstraAoContract.viewport(v,b.width(),b.height(),false)&&state(p);}
 public static void upload(int p,boolean ready,int w,int h){int loc=GL20.glGetUniformLocation(p,"shaderlodAstraAoTargetReady");if(loc>=0)GL20.glUniform1i(loc,ready?1:0);loc=GL20.glGetUniformLocation(p,"shaderlodAstraAoTargetSize");if(loc>=0)GL20.glUniform2f(loc,ready?w:0,ready?h:0);}
}
