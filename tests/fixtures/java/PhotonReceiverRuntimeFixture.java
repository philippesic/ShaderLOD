package fixture;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.optimization.photonreceiver.PhotonReceiverRuntime;
import com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.*;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import org.lwjgl.opengl.*;
import java.util.*;

public final class PhotonReceiverRuntimeFixture {
/** Counting GL boundary. It does not model driver execution or image quality. */
public static final class Driver {
    public static final String PERMISSION="shaderlodPhotonReceiverQualified";
    public static int current=7,active=GL13.GL_TEXTURE0+5,calls,currentQueries,locationQueries,grants,revokes;
    public static boolean linked=true,framebuffer=true,compare=true,missing=false,failGrant=false,failRevoke=false;
    public static int[] viewport={0,0,1280,720};
    public static final Map<String,Integer> values=new HashMap<>();
    public static final Map<Integer,String> names=new HashMap<>();
    public static final Map<String,Integer> locations=new HashMap<>();
    public static final Set<Integer> noPermission=new HashSet<>();
    public static final List<String> writes=new ArrayList<>();
    public static final Map<Integer,Integer> permissions=new HashMap<>();
    public static int nextLocation=1;
    public static void resetCounts(){calls=currentQueries=locationQueries=grants=revokes=0;writes.clear();}
    public static int integer(int n){calls++;return switch(n){
        case GL20.GL_CURRENT_PROGRAM -> {currentQueries++;yield current;}
        case GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS -> 32;
        case GL13.GL_ACTIVE_TEXTURE -> active;
        case GL11.GL_TEXTURE_BINDING_2D -> 42;
        case GL33.GL_SAMPLER_BINDING -> 0;
        default -> throw new AssertionError("unhandled integer "+n);
    };}
    public static int location(int program,String name){calls++;locationQueries++;
        if((missing||noPermission.contains(program))&&name.equals(PERMISSION))return -1;
        return locations.computeIfAbsent(program+":"+name,k->{int value=nextLocation++;names.put(value,name);return value;});
    }
    public static int uniform(int program,int location){calls++;String name=names.get(location);
        if(name.equals(PERMISSION))return permissions.getOrDefault(program,0);
        return values.getOrDefault(name,switch(name){
            case "shaderlodBridgeVersion" -> 2;
            case "shaderlodEnabled","shaderlodOptimizationFlagsVersion","shaderlodOptShadowReceiverFootprint" -> 1;
            case "shaderlodPreset" -> 0;
            case "shadowtex1" -> 3;
            default -> -1;
        });
    }
    public static void write(int program,int location,int value){calls++;
        if(!PERMISSION.equals(names.get(location)))throw new AssertionError("wrong cached location");
        permissions.put(program,value);writes.add(program+":"+value);
        if(value==1){grants++;if(failGrant)throw new IllegalStateException("injected grant failure");}
        else {revokes++;if(failRevoke)throw new IllegalStateException("injected revoke failure");}
    }
    public static int parameter(int n){calls++;return switch(n){
        case 0x884C -> compare?0x884E:0;
        case 0x884D -> 0x0203;
        case GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_TEXTURE_MAG_FILTER -> 0x2601;
        case GL12.GL_TEXTURE_BASE_LEVEL -> 0;
        case GL11.GL_TEXTURE_WRAP_S,GL11.GL_TEXTURE_WRAP_T -> 0x812F;
        default -> throw new AssertionError("unhandled parameter "+n);
    };}
    public static float number(int n){calls++;return switch(n){
        case GL14.GL_TEXTURE_LOD_BIAS -> 0;
        case GL12.GL_TEXTURE_MIN_LOD -> -1000;
        case GL12.GL_TEXTURE_MAX_LOD -> 1000;
        case 0x84FE -> 1;
        default -> throw new AssertionError("unhandled number "+n);
    };}
    public static int level(int n){calls++;return switch(n){
        case GL11.GL_TEXTURE_WIDTH,GL11.GL_TEXTURE_HEIGHT -> 2048;
        case GL11.GL_TEXTURE_INTERNAL_FORMAT -> 0x81A6;
        case 0x884A -> 24;
        case 0x8C16 -> 0x8C17;
        default -> throw new AssertionError("unhandled level "+n);
    };}
    public static void deleted(int program){permissions.remove(program);locations.keySet().removeIf(k->k.startsWith(program+":"));}
    public static int permission(int program){return permissions.getOrDefault(program,0);}
    public static void observation(String phase){System.out.println(phase+" gl_calls="+calls+" current_queries="+currentQueries+" location_queries="+locationQueries+" grants="+grants+" revokes="+revokes);}
}

    private static IrisRenderingPipeline pipeline;
    private static Program program;
    private static int assertions;
    private static void check(boolean condition,String reason){assertions++;if(!condition)throw new AssertionError(reason);}
    private static Program program(int id)throws Exception{
        var ctor=Program.class.getDeclaredConstructor(int.class,ProgramUniforms.class,ProgramSamplers.class,ProgramImages.class);
        ctor.setAccessible(true);return ctor.newInstance(id,new ProgramUniforms(),new ProgramSamplers(),new ProgramImages());
    }
    private static void bind(Program p){p.use();PhotonReceiverRuntime.mark(pipeline,pipeline.targets,p);}
    private static void setup()throws Exception{
        PhotonReceiverRuntime.invalidate();Driver.deleted(7);Driver.deleted(8);Driver.values.clear();Driver.noPermission.clear();
        Driver.linked=Driver.framebuffer=Driver.compare=true;Driver.missing=Driver.failGrant=Driver.failRevoke=false;
        Driver.viewport=new int[]{0,0,1280,720};Driver.active=GL13.GL_TEXTURE0+5;
        EarlyAlphaSourceCache.allowed=true;EarlyAlphaSourceCache.fail=false;EarlyAlphaSourceCache.checks=0;
        OptimizationRuntimeContext.current=new OptimizationRuntimeContext.Core();OptimizationConfig.requested=true;
        com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended=false;
        Iris.fallback=false;Iris.queue.clear();net.irisshaders.iris.platform.IrisPlatformHelpers.loaded="";
        pipeline=new IrisRenderingPipeline();Iris.pipeline=pipeline;program=program(7);
        PhotonReceiverRuntime.built(pipeline,pipeline.targets,new ProgramSource("deferred4"),program);bind(program);Driver.resetCounts();
    }
    private static void nativeDraw(){PhotonReceiverRuntime.currentQuad(()->check(Driver.permission(7)==0,"native draw has permission"));}
    private static void dormant(String name){Driver.resetCounts();nativeDraw();Driver.observation(name);check(Driver.calls==0,name+" touched GL");}
    private static void granted(){PhotonReceiverRuntime.currentQuad(()->check(Driver.permission(7)==1,"active draw lacks permission"));check(Driver.permission(7)==0,"grant leaked after draw");}
    public static void main(String[] args)throws Exception{
        boolean legacy=args.length>0&&args[0].equals("old");
        setup();OptimizationRuntimeContext.current.enabled=false;nativeDraw();Driver.observation("off");
        if(!legacy)check(Driver.calls==0,"Off touched GL");
        setup();bind(program(8));Driver.resetCounts();PhotonReceiverRuntime.currentQuad(()->{});Driver.observation("never_owned");
        if(!legacy)check(Driver.calls==0,"never-owned touched GL");
        setup();bind(program(8));Driver.noPermission.add(8);Driver.resetCounts();PhotonReceiverRuntime.currentQuad(()->{});Driver.observation("never_owned_absent");
        if(!legacy)check(Driver.calls==0,"never-owned absent uniform touched GL");
        setup();granted();Driver.observation("active_first");Driver.resetCounts();granted();Driver.observation("active_repeat");
        if(!legacy){check(Driver.currentQueries==1,"active current program must be checked exactly once");check(Driver.locationQueries==0,"linked locations not cached");}
        Driver.compare=false;Driver.resetCounts();nativeDraw();Driver.observation("sampler_refusal");
        check(Driver.grants==0,"invalid sampler granted");
        Driver.compare=true;Driver.resetCounts();granted();Driver.observation("active_return");
        OptimizationRuntimeContext.current.enabled=false;Driver.resetCounts();nativeDraw();Driver.observation("off_return");
        if(legacy){System.out.println("PASS legacy observations assertions="+assertions);return;}
        check(Driver.calls==0,"return to Off touched GL");

        setup();OptimizationRuntimeContext.current=null;dormant("no_core");
        setup();OptimizationRuntimeContext.current.preset=LodPreset.QUALITY;dormant("quality");
        setup();OptimizationConfig.requested=false;dormant("unrequested");
        setup();com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended=true;dormant("failed_apply_suspended");
        setup();Iris.queue.put("TAA","changed");dormant("queued_reload");
        setup();Iris.fallback=true;dormant("fallback");
        setup();Iris.pipeline=new IrisRenderingPipeline();dormant("pipeline_mismatch");
        setup();net.irisshaders.iris.platform.IrisPlatformHelpers.loaded="distanthorizons";dormant("dh_refusal");
        setup();net.irisshaders.iris.platform.IrisPlatformHelpers.loaded="voxy";dormant("voxy_refusal");

        setup();Driver.current=8;PhotonReceiverRuntime.currentQuad(()->{});
        check(Driver.currentQueries==1&&Driver.locationQueries==0&&Driver.grants==0,"current program mismatch");
        setup();Driver.values.put("shaderlodEnabled",0);nativeDraw();check(Driver.grants==0,"mutable bridge refusal");
        Driver.values.remove("shaderlodEnabled");granted();Driver.values.put("shaderlodPreset",2);nativeDraw();
        check(Driver.permission(7)==0,"cached mutable preset");
        setup();Driver.missing=true;nativeDraw();Driver.resetCounts();nativeDraw();check(Driver.locationQueries==0,"missing location not cached");
        setup();EarlyAlphaSourceCache.allowed=false;nativeDraw();check(Driver.grants==0&&EarlyAlphaSourceCache.checks==1,"source refusal skipped");
        setup();EarlyAlphaSourceCache.fail=true;nativeDraw();check(Driver.grants==0,"source failure granted");
        setup();Driver.linked=false;nativeDraw();check(Driver.locationQueries==0,"unlinked program locations queried");
        setup();Driver.viewport[2]=640;nativeDraw();check(Driver.grants==0,"viewport mismatch granted");
        setup();Driver.framebuffer=false;nativeDraw();check(Driver.grants==0,"incomplete FBO granted");

        setup();int activeTexture=Driver.active;granted();check(Driver.active==activeTexture,"sampler active texture not restored");
        setup();var drawFailure=new AssertionError("draw failed");
        try{PhotonReceiverRuntime.currentQuad(()->{throw drawFailure;});throw new AssertionError("failure swallowed");}
        catch(AssertionError actual){check(actual==drawFailure,"draw exception replaced");}
        check(Driver.permission(7)==0,"failed draw grant leaked");
        setup();Driver.failGrant=true;nativeDraw();check(Driver.permission(7)==0&&Driver.revokes==1,"exceptional grant not revoked");
        setup();Driver.failRevoke=true;
        try{PhotonReceiverRuntime.currentQuad(()->{throw drawFailure;});throw new AssertionError("failure swallowed");}
        catch(AssertionError actual){check(actual==drawFailure&&actual.getSuppressed().length==1,"cleanup failure did not preserve original");}
        Driver.failRevoke=false;OptimizationRuntimeContext.current.enabled=false;nativeDraw();check(Driver.permission(7)==0,"cleanup retry lost");

        setup();PhotonReceiverRuntime.currentQuad(()->Driver.current=8);
        check(Driver.permission(7)==0&&!Driver.permissions.containsKey(8),"cleanup touched rebound program");
        setup();PhotonReceiverRuntime.beforeDraw();check(Driver.permission(7)==1,"standalone grant absent");
        OptimizationRuntimeContext.current.enabled=false;nativeDraw();check(Driver.revokes==1,"Off did not revoke outstanding grant");
        setup();PhotonReceiverRuntime.beforeDraw();PhotonReceiverRuntime.leave();check(Driver.permission(7)==0,"scope exit leaked grant");
        setup();PhotonReceiverRuntime.beforeDraw();PhotonReceiverRuntime.invalidate();check(Driver.permission(7)==0,"reload leaked grant");dormant("after_reload");
        setup();PhotonReceiverRuntime.beforeDraw();PhotonReceiverRuntime.destroy(pipeline);check(Driver.permission(7)==0,"destroy leaked grant");dormant("after_destroy");

        setup();var context=GL.context;PhotonReceiverRuntime.beforeDraw();GL.context=new GLCapabilities();Driver.resetCounts();
        PhotonReceiverRuntime.invalidate();PhotonReceiverRuntime.currentQuad(()->{});check(Driver.calls==0,"old grant touched foreign context");
        GL.context=context;Driver.resetCounts();nativeDraw();check(Driver.revokes==1&&Driver.grants==0,"deferred revoke lost across reload/context");
        setup();context=GL.context;GL.context=new GLCapabilities();dormant("context_mismatch");GL.context=context;

        setup();granted();program.destroy();Driver.resetCounts();nativeDraw();check(Driver.calls==0,"deleted object queried GL");
        var replacement=program(7);bind(replacement);dormant("reused_unowned_handle");
        PhotonReceiverRuntime.built(pipeline,pipeline.targets,new ProgramSource("deferred4"),replacement);bind(replacement);
        Driver.resetCounts();granted();check(Driver.locationQueries>0,"reused handle retained deleted locations");
        setup();PhotonReceiverRuntime.currentQuad(()->{program.destroy();Driver.permissions.put(7,37);});
        check(Driver.permission(7)==37,"cleanup wrote reused deleted handle");Driver.deleted(7);
        setup();PhotonReceiverRuntime.beforeDraw();PhotonReceiverRuntime.built(pipeline,pipeline.targets,new ProgramSource("composite"),program);
        nativeDraw();check(Driver.grants==1&&Driver.revokes==1,"ownership loss dropped explicit revocation");
        PhotonReceiverRuntime.leave();System.out.println("PASS production receiver + pinned Iris Program assertions="+assertions);
    }
}
