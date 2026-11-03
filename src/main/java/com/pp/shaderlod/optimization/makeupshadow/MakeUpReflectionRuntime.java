package com.pp.shaderlod.optimization.makeupshadow;

import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import org.lwjgl.opengl.*;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.optimization.makeupao.*;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle;

/** Exact filtered depth query reuse and unchanged native next-event recurrence; current owned water draw only. */
public final class MakeUpReflectionRuntime {
    private record Owner(Object pipeline, ShaderPack pack, long epoch) {}
    private static final Map<Integer, Owner> OWNERS = new HashMap<>();
    private static long epoch;
    private static Object sourcePack;
    private static Map<String,String> sourceOptions=Map.of();
    private static boolean sourceReady;
    private static String last="not consumed";
    private MakeUpReflectionRuntime() {}
    public static String status(){return "MakeUp exact query reuse; NEAREST next-cell/unchanged-point events; permission0 outside draw; owned="+OWNERS.size()+"; "+last;}
    public static void invalidate(){epoch++;OWNERS.clear();frame();}
    public static void frame(){sourcePack=null;sourceOptions=Map.of();sourceReady=false;}
    public static void destroy(Object pipeline){OWNERS.entrySet().removeIf(e->e.getValue().pipeline()==pipeline);frame();}
    public static void own(IrisRenderingPipeline pipeline,ShaderPack pack,String role,int program){
        if(pipeline!=null&&pack!=null&&"gbuffers_water".equals(role)&&program>0)
            OWNERS.put(program,new Owner(pipeline,pack,epoch));
    }
    private static int integer(int program,String name){int l=com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.location(program,name);return l<0?-1:GL20.glGetUniformi(program,l);}
    public static boolean profile(Map<String,String> actual){
        if(actual==null||!Set.of("true","false").contains(actual.getOrDefault("COLORED_SHADOW","invalid")))return false;
        var n=new TreeMap<>(actual);n.put("COLORED_SHADOW","true");return MakeUpAoContract.options(n);
    }
    private static boolean source(ShaderPack pack){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(MakeUpReflectionRuntime.class,"source",pack),()->{sourcePack=null;
        var options=pack.getShaderPackOptions();
        if(!MakeUpAoOptionGuard.declaration(options.getOptionSet()))return false;
        var actual=MakeUpAoOptionGuard.snapshot(options.getOptionSet(),options.getOptionValues(),true);
        if(!profile(actual))return false;
        if(sourcePack!=pack||!sourceOptions.equals(actual)){sourcePack=pack;sourceOptions=actual;sourceReady=false;
            try(var in=MakeUpReflectionRuntime.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/makeup-ultra-fast-shaders.json")){
                if(in==null||!MakeUpAoSourceProof.RECIPE_SHA.equals(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))))return false;
                String name=Iris.getIrisConfig().getShaderPackName().orElse(null);if(name==null)return false;
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var p=dir.resolve(name).normalize();
                if(!p.startsWith(dir)||p.equals(dir))return false;
                sourceReady="makeup-ultra-fast-shaders".equals(com.pp.shaderlod.packs.PackSelectionService.verifyGeneratedRendererGraph(p,actual));
            }catch(Exception|LinkageError refused){return false;}
        }
        return sourceReady&&sourceOptions.equals(actual);
    
  });
 }
    /** Immutable actual depthtex1; nonmip equal filters prove native implicit==LOD0. */
    public static boolean sampler(int texture,int expected,int width,int height,int w,int h,int format,int bits,int type,int compare,int min,int mag,int base,float anisotropy){
        return texture>0&&texture==expected&&width==w&&height==h&&w>0&&h>0&&
            ((format==0x81A5&&bits==16&&type==0x8C17)||(format==0x81A6&&bits==24&&type==0x8C17)||(format==0x81A7&&bits==32&&type==0x8C17)||(format==0x8CAC&&bits==32&&type==0x1406))&&
            compare==GL11.GL_NONE&&min==mag&&(min==GL11.GL_LINEAR||min==GL11.GL_NEAREST)&&base==0&&anisotropy==1.0f;
    }
    private static int parameter(int sampler,int key){return sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,key):GL33.glGetSamplerParameteri(sampler,key);}
    private static int boundSampler(int program,net.irisshaders.iris.targets.RenderTargets t){
        int unit=integer(program,"depthtex1");if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return 0;
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try{GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),s=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            var cap=GL.getCapabilities();float aniso=cap.GL_EXT_texture_filter_anisotropic||cap.GL_ARB_texture_filter_anisotropic?
                (s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x84FE):GL33.glGetSamplerParameterf(s,0x84FE)):1.0f;
            return GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x8E42)==GL11.GL_RED && sampler(texture,t.getDepthTextureNoTranslucents().getTextureId(),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),t.getCurrentWidth(),t.getCurrentHeight(),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x884A),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x8C16),parameter(s,0x884C),parameter(s,GL11.GL_TEXTURE_MIN_FILTER),parameter(s,GL11.GL_TEXTURE_MAG_FILTER),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x813C),aniso) ? (parameter(s,GL11.GL_TEXTURE_MIN_FILTER)==GL11.GL_NEAREST?1:2):0;
        }finally{GL13.glActiveTexture(active);}
    }
    private static void draw(){
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(program<=0)return;
        int permission=com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.location(program,"shaderlodMakeupReflectionDepthPermission");if(permission<0)return;
        GL20.glUniform1i(permission,0);last="native/refused";
        try{var core=OptimizationRuntimeContext.core();var id=OptimizationId.REFLECTION_PROJECTED_REFINEMENT; var next=OptimizationId.REFLECTION_NEXT_EVENT_REFINEMENT;
            if(!GL.getCapabilities().OpenGL41||!Iris.getShaderPackOptionQueue().isEmpty()||core==null||!core.settings().enabled()||core.preset()==LodPreset.QUALITY||!((core.optimizations().enabled(id)&&OptimizationConfig.requested(id))||(core.optimizations().enabled(next)&&OptimizationConfig.requested(next)))||MakeUpQuadLifecycle.suspended()||!MakeUpShadowPins.exact()||!"Legacy".equals(MakeUpShadowPins.kind())||Iris.isFallback())return;
            var pipe=Iris.getPipelineManager().getPipelineNullable();var pack=Iris.getCurrentPack().orElse(null);var owner=OWNERS.get(program);
            if(owner==null||owner.pipeline()!=pipe||owner.pack()!=pack||owner.epoch()!=epoch||pack==null||!source(pack))return;
            var client=net.minecraft.client.Minecraft.getInstance();var platform=net.irisshaders.iris.platform.IrisPlatformHelpers.getInstance();
            if(client.level==null||client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||platform.isModLoaded("distanthorizons")||platform.isModLoaded("voxy")||!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())return;
            if(net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered())return;
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE||integer(program,"shaderlodBridgeVersion")!=2||integer(program,"shaderlodEnabled")!=1||integer(program,"shaderlodOptimizationFlagsVersion")!=1||!((core.optimizations().enabled(id)&&OptimizationConfig.requested(id)&&integer(program,id.uniform())==1)||(core.optimizations().enabled(next)&&OptimizationConfig.requested(next)&&integer(program,next.uniform())==1))||integer(program,"shaderlodPreset")!=core.preset().ordinal())return;
            if(!(pipe instanceof com.pp.shaderlod.optimization.makeupshadow.mixin.ShadowPipelineView view))return;var t=view.shadowlod$targets();if(t==null||t.getCurrentWidth()>16384||t.getCurrentHeight()>16384)return;int mode=boundSampler(program,t);if(mode==0)return;
            int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);if(!Arrays.equals(viewport,new int[]{0,0,t.getCurrentWidth(),t.getCurrentHeight()})||client.getWindow().getWidth()!=viewport[2]||client.getWindow().getHeight()!=viewport[3])return;
            if(Iris.getCurrentPack().orElse(null)!=pack||Iris.getPipelineManager().getPipelineNullable()!=pipe||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=program||!Iris.getShaderPackOptionQueue().isEmpty())return;
            int px=com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.location(program,"pixelSizeX"),py=com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.location(program,"pixelSizeY");if(px<0||py<0||GL20.glGetUniformf(program,px)!=1.0f/t.getCurrentWidth()||GL20.glGetUniformf(program,py)!=1.0f/t.getCurrentHeight())return;
            GL20.glUniform1i(permission,mode);last="qualified current owned water; permission mode="+mode+" (1 NEAREST cell, 2 LINEAR exact query/point)";
        }catch(RuntimeException|LinkageError refused){GL20.glUniform1i(permission,0);last=refused.getClass().getSimpleName();}
    }
    /** Same normal draw boundary used by the existing water sampler supplier. */
    public static void scoped(Runnable before,Runnable nativeDraw,Runnable revoke){Throwable primary=null;try{before.run();nativeDraw.run();}catch(RuntimeException|Error f){primary=f;throw f;}finally{try{revoke.run();}catch(RuntimeException|Error f){if(primary!=null)primary.addSuppressed(f);else throw f;}}}
    public static void currentDraw(Runnable nativeDraw){int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);scoped(MakeUpReflectionRuntime::draw,nativeDraw,()->{if(p>0){int l=com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.location(p,"shaderlodMakeupReflectionDepthPermission");if(l>=0){if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==p)GL20.glUniform1i(l,0);else if(GL.getCapabilities().OpenGL41)GL41.glProgramUniform1i(p,l,0);}}});}

public static void scoped(Runnable before,int mode,long counts,int type,long indices,int drawcount,long bases,com.pp.shaderlod.optimization.nativechain.NativeDraw nativeDraw,Runnable revoke){Throwable primary=null;try{before.run();nativeDraw.draw(mode,counts,type,indices,drawcount,bases);}catch(RuntimeException|Error f){primary=f;throw f;}finally{try{revoke.run();}catch(RuntimeException|Error f){if(primary!=null)primary.addSuppressed(f);else throw f;}}}
public static void currentDraw(int mode,long counts,int type,long indices,int drawcount,long bases,com.pp.shaderlod.optimization.nativechain.NativeDraw nativeDraw){int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);scoped(MakeUpReflectionRuntime::draw,mode,counts,type,indices,drawcount,bases,nativeDraw,()->{if(p>0){int l=com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.location(p,"shaderlodMakeupReflectionDepthPermission");if(l>=0){if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==p)GL20.glUniform1i(l,0);else if(GL.getCapabilities().OpenGL41)GL41.glProgramUniform1i(p,l,0);}}});}

 /** Invoked synchronously by the authentic pipeline build; never by a rendering callback. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){try{source(pack);}catch(Exception|LinkageError refused){}}
}
