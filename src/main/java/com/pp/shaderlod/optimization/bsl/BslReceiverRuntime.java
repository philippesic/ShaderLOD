package com.pp.shaderlod.optimization.bsl;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle;
import com.pp.shaderlod.packs.PackSelectionService;
import java.util.*;
import net.irisshaders.iris.Iris;
import org.lwjgl.opengl.*;
/** Actual bound native/Sodium setup caller. No observer readback, property bypass or old driver certificate. */
public final class BslReceiverRuntime {
    private static int issuedProgram,issuedLocation=-1;
    private BslReceiverRuntime() {}
    public static void invalidate() {issuedProgram=0;issuedLocation=-1;}
    /** The renderer consumes the graph proof published by the pipeline loading transaction. */
    static boolean source() {
        return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(
            List.of(BslReceiverRuntime.class,"source"),()->false);
    }
    public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack) {
        com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(List.of(BslReceiverRuntime.class,"source"),()->{
            if(pack==null||pack!=Iris.getCurrentPack().orElse(null)||Iris.isFallback())return false;
            var options=com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(pack.getShaderPackOptions());
            if(!com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("bsl-shaders",options))return false;
            var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
            var name=Iris.getIrisConfig().getShaderPackName().orElse(null);
            if(name==null)return false;var selected=dir.resolve(name).normalize();
            return selected.startsWith(dir)&&!selected.equals(dir)&&
                "bsl-shaders".equals(PackSelectionService.verifyGeneratedRendererGraph(selected,options));
        });
    }
    private static int integer(int program,String name) {
        int location=GL20.glGetUniformLocation(program,name);
        return location<0 ? -1 : GL20.glGetUniformi(program,location);
    }
    private static float number(int program,String name) {
        int location=GL20.glGetUniformLocation(program,name);
        return location<0 ? Float.NaN : GL20.glGetUniformf(program,location);
    }
    private static int parameter(int sampler,int name) {
        return sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,name):GL33.glGetSamplerParameteri(sampler,name);
    }
    private static BslReceiverContract.Sampler depth(int program,String name) {
        int unit=integer(program,name);if(unit<0 || unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return null;
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        try {
            int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);if(texture<=0)return null;
            int sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING); // GL4.1 non-indexed query on active unit.
            var caps=GL.getCapabilities();boolean aniso=caps.GL_EXT_texture_filter_anisotropic||caps.GL_ARB_texture_filter_anisotropic;
            float anisotropy=aniso?(sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x84FE):GL33.glGetSamplerParameterf(sampler,0x84FE)):1;
            return new BslReceiverContract.Sampler(texture,
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT),
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x884A),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x8C16),
                parameter(sampler,0x884C),parameter(sampler,0x884D),parameter(sampler,GL11.GL_TEXTURE_MIN_FILTER),
                parameter(sampler,GL11.GL_TEXTURE_MAG_FILTER),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x813C),
                parameter(sampler,GL11.GL_TEXTURE_WRAP_S),parameter(sampler,GL11.GL_TEXTURE_WRAP_T),anisotropy);
        }finally {GL13.glActiveTexture(active);}
    }
    private static boolean color(int program) {
        int unit=integer(program,"shadowcolor0");if(unit<0)return true; // Unlinked native color branch has no color lookup.
        if(unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return false;
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        try {
            int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            var caps=GL.getCapabilities();boolean aniso=caps.GL_EXT_texture_filter_anisotropic||caps.GL_ARB_texture_filter_anisotropic;
            float anisotropy=aniso?(sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x84FE):GL33.glGetSamplerParameterf(sampler,0x84FE)):1;
            return texture>0 && parameter(sampler,0x884C)==0 &&
                Set.of(GL11.GL_NEAREST,GL11.GL_LINEAR).contains(parameter(sampler,GL11.GL_TEXTURE_MIN_FILTER)) &&
                Set.of(GL11.GL_NEAREST,GL11.GL_LINEAR).contains(parameter(sampler,GL11.GL_TEXTURE_MAG_FILTER)) &&
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)==2048 &&
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)==2048 &&
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x813C)==0 &&
                parameter(sampler,GL11.GL_TEXTURE_WRAP_S)==GL12.GL_CLAMP_TO_EDGE &&
                parameter(sampler,GL11.GL_TEXTURE_WRAP_T)==GL12.GL_CLAMP_TO_EDGE && anisotropy==1;
        }finally {GL13.glActiveTexture(active);}
    }
    private static boolean requested(){var c=OptimizationRuntimeContext.core();return c!=null&&c.settings().enabled()&&c.preset()!=LodPreset.QUALITY&&!MakeUpQuadLifecycle.suspended()&&c.optimizations().enabled(OptimizationId.SHADOW_RECEIVER_FOOTPRINT)&&OptimizationConfig.requested(OptimizationId.SHADOW_RECEIVER_FOOTPRINT);}
    /** No new GL query outside the actual BSL owner/request; setup always revokes independently. */
    public static void beforeDraw(){if(requested()&&com.pp.shaderlod.optimization.bslspatial.BslSpatialRuntime.hasOwner())uploadCurrent();}
    /** Same-handler entry only: revoke is explicit-program and cannot change the current binding. */
    public static void beforeDraw(int program){if(requested()&&com.pp.shaderlod.optimization.bslspatial.BslSpatialRuntime.hasOwner()){revoke();upload(program);}}
    /** Direct owned-program revoke after success/throw and before native end, including empty batches. */
    public static void revoke(){int p=issuedProgram,l=issuedLocation;issuedProgram=0;issuedLocation=-1;if(p>0&&l>=0)GL41.glProgramUniform1f(p,l,0.0f);}
    public static void uploadCurrent() {
        revoke();
        upload(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
    }
    private static void upload(int program) {
        if(program<=0)return;
        int permission=GL20.glGetUniformLocation(program,"shaderlodReceiverReductionPermission");if(permission<0)return;
        GL20.glUniform1f(permission,0); // Every invocation revokes previous draw, including disabled/mismatched state.
        try {
            var core=OptimizationRuntimeContext.core();
            if(MakeUpQuadLifecycle.suspended() || core==null || !core.settings().enabled() || core.preset()==LodPreset.QUALITY ||
                !core.optimizations().enabled(OptimizationId.SHADOW_RECEIVER_FOOTPRINT) || !OptimizationConfig.requested(OptimizationId.SHADOW_RECEIVER_FOOTPRINT) || !BslResourceApiPins.exactApi() || !source())return;
            // Permission exists only in the exact pinned MC/Iris source-context pairs, Overworld/no-PBR; actual sampler state qualifies every OS.
            if(integer(program,"shaderlodBridgeVersion")!=2 || integer(program,"shaderlodEnabled")!=1 ||
                integer(program,"shaderlodOptimizationFlagsVersion")!=1 || integer(program,"shaderlodOptShadowReceiverFootprint")!=1 ||
                !Set.of(1,2).contains(integer(program,"shaderlodPreset")))return;
            int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
            if(!BslReceiverContract.viewport(viewport[0],viewport[1],viewport[2],viewport[3],number(program,"viewWidth"),number(program,"viewHeight")))return;
            // Compile-time field alias has the same HW sampler contract; raw samplers never qualify here.
            var zero=depth(program,"shadowtex0HW");if(zero==null)zero=depth(program,"shadowtex0");
            var one=depth(program,"shadowtex1HW");if(one==null)one=depth(program,"shadowtex1");
            if(!BslReceiverContract.depth(zero) || !BslReceiverContract.depth(one) || !color(program))return;
            GL20.glUniform1f(permission,1);issuedProgram=program;issuedLocation=permission;
        }catch(RuntimeException|LinkageError unavailable) {GL20.glUniform1f(permission,0);}
    }
}
