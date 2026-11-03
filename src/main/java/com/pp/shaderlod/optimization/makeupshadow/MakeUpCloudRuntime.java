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

/** MakeUp reflected cloud native angular support; permission never controls primary clouds. */
public final class MakeUpCloudRuntime {
    private record Owner(Object pipeline, ShaderPack pack, long epoch) {}
    private static final Map<Integer, Owner> OWNERS = new HashMap<>();
    private static long epoch;
    private static Object sourcePack;
    private static Map<String,String> sourceOptions=Map.of();
    private static boolean sourceReady;
    private static String last="not consumed";
    private MakeUpCloudRuntime() {}
    public static String status(){return "MakeUp reflected native angular support (historical distance-exit category); owned="+OWNERS.size()+"; "+last;}
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
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(MakeUpCloudRuntime.class,"source",pack),()->{sourcePack=null;
        var options=pack.getShaderPackOptions();
        if(!MakeUpAoOptionGuard.declaration(options.getOptionSet()))return false;
        var actual=MakeUpAoOptionGuard.snapshot(options.getOptionSet(),options.getOptionValues(),true);
        if(!profile(actual))return false;
        if(sourcePack!=pack){sourcePack=pack;sourceOptions=actual;sourceReady=false;
            try(var in=MakeUpCloudRuntime.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/makeup-ultra-fast-shaders.json")){
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
    /** Normalized finite texels; equal nonmip filters make native implicit vs LOD0 identical. */
    public static boolean sampler(int texture,int width,int height,int format,int compare,int min,int mag,int base,float anisotropy){
        return texture>0&&width==512&&height==512&&Set.of(0x8229,0x822B,0x8051,0x8058,0x8C41,0x8C43).contains(format)
            &&compare==GL11.GL_NONE&&min==mag&&(min==GL11.GL_LINEAR||min==GL11.GL_NEAREST)&&base==0&&anisotropy==1.0f;
    }
    private static int parameter(int sampler,int key){return sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,key):GL33.glGetSamplerParameteri(sampler,key);}
    private static boolean boundSampler(int program){
        int unit=integer(program,"gaux2");if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return false;
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try{GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),s=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            var cap=GL.getCapabilities();float aniso=cap.GL_EXT_texture_filter_anisotropic||cap.GL_ARB_texture_filter_anisotropic?
                (s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x84FE):GL33.glGetSamplerParameterf(s,0x84FE)):1.0f;
            return sampler(texture,GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT),parameter(s,0x884C),parameter(s,GL11.GL_TEXTURE_MIN_FILTER),parameter(s,GL11.GL_TEXTURE_MAG_FILTER),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x813C),aniso);
        }finally{GL13.glActiveTexture(active);}
    }
    public static void draw(){
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(program<=0)return;
        int permission=com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.location(program,"shaderlodMakeupCloudSamplerPermission");if(permission<0)return;
        GL20.glUniform1i(permission,0);last="native/refused";
        try{var core=OptimizationRuntimeContext.core();var id=OptimizationId.REFLECTED_CLOUD_DISTANCE_EXIT;
            if(core==null||!core.settings().enabled()||core.preset()==LodPreset.QUALITY||!core.optimizations().enabled(id)||!OptimizationConfig.requested(id)||MakeUpQuadLifecycle.suspended()||!MakeUpShadowPins.exact()||!"Legacy".equals(MakeUpShadowPins.kind())||Iris.isFallback())return;
            var pipe=Iris.getPipelineManager().getPipelineNullable();var pack=Iris.getCurrentPack().orElse(null);var owner=OWNERS.get(program);
            if(owner==null||owner.pipeline()!=pipe||owner.pack()!=pack||owner.epoch()!=epoch||pack==null||!source(pack))return;
            if(net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered())return;
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE||integer(program,"shaderlodBridgeVersion")!=2||integer(program,"shaderlodEnabled")!=1||integer(program,"shaderlodOptimizationFlagsVersion")!=1||integer(program,id.uniform())!=1||!Set.of(0,1,2).contains(integer(program,"shaderlodPreset")))return;
            if(!boundSampler(program))return;GL20.glUniform1i(permission,1);last="qualified current water draw; native zero angular contribution";
        }catch(RuntimeException|LinkageError refused){GL20.glUniform1i(permission,0);last=refused.getClass().getSimpleName();}
    }

 /** Invoked synchronously by the authentic pipeline build; never by a rendering callback. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){try{source(pack);}catch(Exception|LinkageError refused){}}
}
