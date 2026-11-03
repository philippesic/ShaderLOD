package com.pp.shaderlod.optimization;

import java.lang.reflect.Field;
import java.util.*;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.pipeline.FinalPassRenderer;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.shadows.ShadowCompositeRenderer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.program.ComputeProgram;

/** Exact pinned Iris 1.8.12 graph identity at normal Apply, never on a draw. */
final class NativeCpuGraphIdentity {
    private NativeCpuGraphIdentity() {}
    record Snapshot(List<Object> objects,List<Integer> handles) {
        boolean same(Snapshot other) {
            if(other==null||objects.size()!=other.objects.size()||!handles.equals(other.handles))return false;
            for(int i=0;i<objects.size();i++)if(objects.get(i)!=other.objects.get(i))return false;
            return true;
        }
    }
    private static Object field(Object owner,Class<?> exact,String name)throws ReflectiveOperationException {
        if(owner==null||owner.getClass()!=exact)throw new NoSuchFieldException(name+" owner");
        Field field=exact.getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private static void add(List<Object> objects,List<Integer> handles,Object value) {
        objects.add(value);
        handles.add(value instanceof Program program?program.getProgramId():
            value instanceof ComputeProgram compute?compute.getProgramId():
            value instanceof net.minecraft.client.renderer.ShaderInstance shader?shader.getId():0);
    }
    private static void computes(List<Object> objects,List<Integer> handles,Object value) {
        if(value!=null && !(value instanceof ComputeProgram[]))throw new IllegalArgumentException("Pinned compute array changed");
        add(objects,handles,value);
        if(value!=null)for(var compute:(ComputeProgram[])value)add(objects,handles,compute);
    }
    private static void passes(List<Object> objects,List<Integer> handles,Object renderer,Class<?> rendererClass,
            String passClassName)throws ReflectiveOperationException {
        if(renderer==null){add(objects,handles,null);return;}
        if(renderer.getClass()!=rendererClass)throw new IllegalArgumentException("Pinned composite renderer changed");
        add(objects,handles,renderer);
        Object list=field(renderer,rendererClass,"passes");
        if(!(list instanceof com.google.common.collect.ImmutableList<?> values))throw new IllegalArgumentException("Pinned pass list changed");
        add(objects,handles,list);
        for(var pass:values) {
            if(pass==null||!pass.getClass().getName().equals(passClassName))throw new IllegalArgumentException("Pinned pass class changed");
            add(objects,handles,pass);
            var program=field(pass,pass.getClass(),"program");
            if(program!=null&&!(program instanceof Program))throw new IllegalArgumentException("Pinned pass program changed");
            add(objects,handles,program);
            computes(objects,handles,field(pass,pass.getClass(),"computes"));
        }
    }
    static Snapshot capture(IrisRenderingPipeline pipeline) {
        try {
            if(pipeline==null||pipeline.getClass()!=IrisRenderingPipeline.class)return null;
            var objects=new ArrayList<Object>();var handles=new ArrayList<Integer>();
            var shaderMap=pipeline.getShaderMap();
            if(shaderMap==null||shaderMap.getClass()!=net.irisshaders.iris.pipeline.programs.ShaderMap.class)return null;
            add(objects,handles,shaderMap);
            for(var key:ShaderKey.values())add(objects,handles,shaderMap.getShader(key));
            computes(objects,handles,field(pipeline,IrisRenderingPipeline.class,"setup"));
            computes(objects,handles,field(pipeline,IrisRenderingPipeline.class,"shadowComputes"));
            for(var name:List.of("beginRenderer","prepareRenderer","deferredRenderer","compositeRenderer"))
                passes(objects,handles,field(pipeline,IrisRenderingPipeline.class,name),CompositeRenderer.class,
                    "net.irisshaders.iris.pipeline.CompositeRenderer$Pass");
            passes(objects,handles,field(pipeline,IrisRenderingPipeline.class,"shadowCompositeRenderer"),
                ShadowCompositeRenderer.class,"net.irisshaders.iris.shadows.ShadowCompositeRenderer$Pass");
            var finalRenderer=field(pipeline,IrisRenderingPipeline.class,"finalPassRenderer");
            if(finalRenderer==null){add(objects,handles,null);}
            else {
                if(finalRenderer.getClass()!=FinalPassRenderer.class)return null;
                add(objects,handles,finalRenderer);
                var pass=field(finalRenderer,FinalPassRenderer.class,"finalPass");
                if(pass==null){add(objects,handles,null);}
                else {
                    if(!pass.getClass().getName().equals("net.irisshaders.iris.pipeline.FinalPassRenderer$Pass"))return null;
                    add(objects,handles,pass);
                    var program=field(pass,pass.getClass(),"program");
                    if(program!=null&&!(program instanceof Program))return null;
                    add(objects,handles,program);
                    computes(objects,handles,field(pass,pass.getClass(),"computes"));
                }
            }
            return new Snapshot(Collections.unmodifiableList(objects),List.copyOf(handles));
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return null;}
    }
}
