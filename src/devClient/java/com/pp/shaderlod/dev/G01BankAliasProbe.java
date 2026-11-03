package com.pp.shaderlod.dev;

import com.google.gson.*;
import java.util.*;
import java.util.function.IntSupplier;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.targets.RenderTargets;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.*;

/** Explicit untimed census of existing linked programs, suppliers and cached attachments. */
final class G01BankAliasProbe {
    private static final Gson JSON = new Gson();
    private G01BankAliasProbe() {}
    static JsonObject capture() throws Exception {
        Object p = Iris.getPipelineManager().getPipelineNullable();
        RenderTargets targets = (RenderTargets)field(p, "renderTargets");
        int beforeProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        var result = new JsonObject(); var programs = new JsonArray();
        var shaders = (Collection<?>)field(p,"loadedShaders");
        for (Object value : shaders) {
            ShaderInstance shader = (ShaderInstance)value;
            programs.add(program("loadedShaders:"+shader.getName(),shader.getId(),shader));
        }
        Object sodium = field(p,"sodiumPrograms");
        for (var entry : ((Map<?,?>)field(sodium,"shaders")).entrySet()) {
            var value = (net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?>)entry.getValue();
            programs.add(program("sodium:"+entry.getKey(),value.handle(),value.getInterface()));
        }
        for (String name : List.of("beginRenderer","prepareRenderer","deferredRenderer","compositeRenderer","finalPassRenderer","shadowCompositeRenderer")) {
            Object renderer = field(p,name); if (renderer == null) continue;
            List<?> passes;
            if (name.equals("finalPassRenderer")) { Object pass=field(renderer,"finalPass"); passes=pass==null?List.of():List.of(pass); }
            else passes=(List<?>)field(renderer,"passes");
            for (int i=0;i<passes.size();i++) {
                Object pass=passes.get(i); Object value=optional(pass,"program");
                if(value instanceof Program actual) programs.add(program(name+":"+i,actual.getProgramId(),actual));
                Object computes=optional(pass,"computes"); if(computes instanceof Object[] array) addComputes(programs,name+":"+i,array);
            }
        }
        addComputes(programs,"setup",(Object[])field(p,"setup"));
        addComputes(programs,"shadowComputes",(Object[])field(p,"shadowComputes"));
        result.add("programs",programs);
        var framebuffers=new JsonArray();
        var owned=(List<?>)field(targets,"ownedFramebuffers"); if(owned.size()>512)throw new IllegalStateException("FBO bound");
        int priorRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            for(Object value:owned) {
                var fbo=(GlFramebuffer)value; var row=new JsonObject();row.addProperty("id",fbo.getId());
                var attachments=new JsonArray();fbo.bindAsReadBuffer();
                for(var entry:((Map<?,?>)field(fbo,"attachments")).entrySet()) {
                    int slot=(int)entry.getKey(),texture=(int)entry.getValue();var a=new JsonObject();a.addProperty("slot",slot);a.addProperty("cached_texture",texture);
                    a.addProperty("live_texture",GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+slot,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME));attachments.add(a);
                }
                row.add("attachments",attachments);framebuffers.add(row);
            }
        } finally { com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,priorRead); }
        result.add("owned_framebuffers",framebuffers);
        result.add("sodium_framebuffers",JSON.toJsonTree(((Map<?,?>)field(sodium,"framebuffers")).entrySet().stream().map(e->Map.of("pass",e.getKey().toString(),"fbo",((GlFramebuffer)e.getValue()).getId())).toList()));
        var simple=new JsonObject();
        for(String name:List.of("flippedBeforeShadow","flippedAfterPrepare","flippedAfterTranslucent","customImages","clearImages","setup","shadowComputes")) {
            Object value=field(p,name);simple.addProperty(name+"_class",value==null?"null":value.getClass().getName());
            if(value instanceof Collection<?> c)simple.addProperty(name+"_size",c.size());
            if(value instanceof Object[] a)simple.addProperty(name+"_non_null",Arrays.stream(a).filter(Objects::nonNull).count());
            if(name.startsWith("flipped"))simple.add(name,JSON.toJsonTree(value));
        }
        for(String name:List.of("defaultFB","defaultFBAlt"))simple.addProperty(name,((GlFramebuffer)field(p,name)).getId());
        result.add("pipeline_inputs",simple);
        if(beforeProgram!=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM))throw new IllegalStateException("Program changed during census");
        result.addProperty("scope","Read-only linked active-uniform/supplier/attachment snapshot, all eager Extended/Fallback and Sodium programs plus fullscreen/compute. Supplier resolution only; no binds or contents changed except read FBO restored in finally. Not a timing sample or ownership proof by itself.");
        return result;
    }
    private static void addComputes(JsonArray rows,String name,Object[] array)throws Exception {
        for(int i=0;i<array.length;i++)if(array[i]!=null) {
            Object value=array[i]; int id=(int)value.getClass().getMethod("getProgramId").invoke(value);rows.add(program(name+":"+i,id,value));
        }
    }
    private static JsonObject program(String name,int id,Object owner)throws Exception {
        var row=new JsonObject();row.addProperty("path",name);row.addProperty("program",id);row.addProperty("owner_class",owner.getClass().getName());
        var uniforms=new JsonArray();int n=GL20.glGetProgrami(id,GL20.GL_ACTIVE_UNIFORMS);if(n>4096)throw new IllegalStateException("Uniform bound");
        var size=org.lwjgl.BufferUtils.createIntBuffer(1);var type=org.lwjgl.BufferUtils.createIntBuffer(1);
        for(int i=0;i<n;i++){String uniform=GL20.glGetActiveUniform(id,i,size,type);var u=new JsonObject();u.addProperty("name",uniform);u.addProperty("type",type.get(0));u.addProperty("size",size.get(0));u.addProperty("location",GL20.glGetUniformLocation(id,uniform));uniforms.add(u);}
        row.add("active_uniforms",uniforms);
        for(String kind:List.of("samplers","images")) {
            Object container=optional(owner,kind);var bindings=new JsonArray();
            if(container!=null)for(Object b:(List<?>)field(container,kind.equals("samplers")?"samplerBindings":"imageBindings")) {
                var item=new JsonObject();String f=kind.equals("samplers")?"texture":"textureID";var supplier=(IntSupplier)field(b,f);
                item.addProperty("supplier_class",supplier.getClass().getName());item.addProperty("resolved_texture",supplier.getAsInt());
                item.addProperty("unit",(int)field(b,kind.equals("samplers")?"textureUnit":"imageUnit"));bindings.add(item);
            }
            row.add(kind,bindings);
        }
        for(String nameFbo:List.of("writingToBeforeTranslucent","writingToAfterTranslucent")) {
            Object f=optional(owner,nameFbo);if(f instanceof GlFramebuffer framebuffer)row.addProperty(nameFbo,framebuffer.getId());
        }
        return row;
    }
    private static Object optional(Object owner,String name)throws Exception {try{return field(owner,name);}catch(NoSuchFieldException absent){return null;}}
    private static Object field(Object owner,String name)throws Exception {
        for(Class<?> c=owner.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(NoSuchFieldException absent){}
        throw new NoSuchFieldException(owner.getClass().getName()+":"+name);
    }
}
