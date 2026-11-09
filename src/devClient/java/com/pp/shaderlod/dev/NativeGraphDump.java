package com.pp.shaderlod.dev;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.loading.*;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.minecraft.client.Minecraft;

/** Untimed export of the already parsed selected graph; never compiles or changes shader state. */
final class NativeGraphDump {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private NativeGraphDump() {}
    static Path write(Path directory,JsonObject context)throws Exception {
        var client=Minecraft.getInstance();var pack=Iris.getCurrentPack().orElseThrow();
        if(client.level==null||Iris.isFallback()||Files.exists(directory))throw new IllegalStateException("Loaded graph and fresh output required");
        if(Files.getFileStore(directory.getParent()).getUsableSpace()<32L*1024*1024*1024+64L*1024*1024)
            throw new IllegalStateException("Graph export storage reserve");
        var key=client.level.dimension().location();
        var dimension=new net.irisshaders.iris.shaderpack.materialmap.NamespacedId(key.getNamespace(),key.getPath());
        var set=pack.getProgramSet(dimension);var programs=new TreeMap<String,ProgramSource>();
        for(var id:ProgramId.values())set.get(id).ifPresent(p->programs.put(p.getName(),p));
        for(var group:List.of(ProgramArrayId.Begin,ProgramArrayId.ShadowComposite,ProgramArrayId.Prepare,ProgramArrayId.Deferred,ProgramArrayId.Composite))
            for(var p:set.getComposite(group))if(p!=null)programs.put(p.getName(),p);
        var manifest=new JsonObject();manifest.add("context",context);
        var options=pack.getShaderPackOptions();var actual=new TreeMap<String,String>();
        options.getOptionSet().getBooleanOptions().keySet().forEach(k->actual.put(k,Boolean.toString(options.getOptionValues().getBooleanValueOrDefault(k))));
        options.getOptionSet().getStringOptions().keySet().forEach(k->actual.put(k,options.getOptionValues().getStringValueOrDefault(k)));
        manifest.add("effective_options",JSON.toJsonTree(actual));manifest.addProperty("dimension",dimension.toString());
        manifest.addProperty("scope","Parsed selected ProgramSource and directives; program availability is not proof of executed draws or active compute/image consumers");
        var rows=new JsonArray();long bytes=0;Files.createDirectory(directory);
        for(var entry:programs.entrySet()) {
            var p=entry.getValue();String name=entry.getKey();if(!name.matches("[A-Za-z0-9_]+"))throw new IllegalStateException("Unexpected program name");
            var row=new JsonObject();row.addProperty("name",name);row.addProperty("valid",p.isValid());var directives=p.getDirectives();
            row.add("draw_buffers",JSON.toJsonTree(directives.getDrawBuffers()));row.addProperty("unknown_draw_buffers",directives.hasUnknownDrawBuffers());
            row.add("mipmapped_buffers",JSON.toJsonTree(directives.getMipmappedBuffers()));row.add("explicit_flips",JSON.toJsonTree(directives.getExplicitFlips()));
            row.addProperty("viewport",String.valueOf(directives.getViewportScale()));row.addProperty("blend",String.valueOf(directives.getBlendModeOverride()));
            row.addProperty("buffer_blends",String.valueOf(directives.getBufferBlendOverrides()));
            for(var stage:List.of(Map.entry("vsh",p.getVertexSource()),Map.entry("fsh",p.getFragmentSource())))if(stage.getValue().isPresent()) {
                byte[] source=stage.getValue().get().getBytes(StandardCharsets.UTF_8);bytes+=source.length;
                if(bytes>64L*1024*1024)throw new IllegalStateException("Graph export exceeds64MiB");
                Files.write(directory.resolve(name+'.'+stage.getKey()),source,StandardOpenOption.CREATE_NEW);
            }
            rows.add(row);
        }
        // All parsed selected compute roots, including source-only inactive programs.
        // Keep availability distinct from execution and inspect image/sampler aliases separately.
        var computes=new TreeMap<String,net.irisshaders.iris.shaderpack.programs.ComputeSource>();
        var arrays=new ArrayList<net.irisshaders.iris.shaderpack.programs.ComputeSource[]>();
        arrays.add(set.getSetup());arrays.add(set.getShadowCompute());arrays.add(set.getFinalCompute());
        for(var group:ProgramArrayId.values())Collections.addAll(arrays,set.getCompute(group));
        for(var array:arrays)if(array!=null)for(var compute:array)if(compute!=null)computes.put(compute.getName(),compute);
        if(computes.size()>256)throw new IllegalStateException("Bounded compute source inventory");
        var computeRows=new JsonArray();
        for(var entry:computes.entrySet()) {
            var row=new JsonObject();String name=entry.getKey();
            if(!name.matches("[A-Za-z0-9_]+"))throw new IllegalStateException("Unexpected compute name");
            row.addProperty("name",name);row.addProperty("valid",entry.getValue().isValid());
            var source=entry.getValue().getSource();
            if(source.isPresent()) {
                byte[] data=source.get().getBytes(StandardCharsets.UTF_8);bytes+=data.length;
                if(bytes>64L*1024*1024)throw new IllegalStateException("Graph export exceeds64MiB");
                Files.write(directory.resolve(name+".csh"),data,StandardOpenOption.CREATE_NEW);
            }
            computeRows.add(row);
        }
        manifest.add("compute_programs",computeRows);
        if(Iris.getCurrentPack().orElse(null)!=pack)throw new IllegalStateException("Selected graph changed during export");
        manifest.add("programs",rows);manifest.addProperty("source_bytes",bytes);
        manifest.add("linked_programs",linked(directory));
        manifest.add("target_layout",targetLayout());
        manifest.add("custom_textures",JSON.toJsonTree(MipFamilyProbe.customTextureInventory(Iris.getPipelineManager().getPipelineNullable())));
        Files.writeString(directory.resolve("manifest.json"),JSON.toJson(manifest),StandardOpenOption.CREATE_NEW);
        return directory;
    }

    /** Existing Iris target-object layout only; not GPU residency or image data. */
    private static JsonArray targetLayout()throws Exception {
        var rows=new JsonArray();var pipeline=Iris.getPipelineManager().getPipelineNullable();
        if(!(pipeline instanceof net.irisshaders.iris.pipeline.IrisRenderingPipeline))return rows;
        var targets=(net.irisshaders.iris.targets.RenderTargets)field(pipeline,"renderTargets");
        if(targets.getRenderTargetCount()>32)throw new IllegalStateException("Bounded target inventory");
        for(int index=0;index<targets.getRenderTargetCount();index++) {
            var target=targets.get(index);if(target==null)continue;var row=new JsonObject();
            row.addProperty("index",index);row.addProperty("width",target.getWidth());row.addProperty("height",target.getHeight());
            row.addProperty("format",target.getInternalFormat().toString());row.addProperty("main_texture",target.getMainTexture());row.addProperty("alt_texture",target.getAltTexture());rows.add(row);
        }
        if(Iris.getPipelineManager().getPipelineNullable()!=pipeline)throw new IllegalStateException("Target owner changed");
        return rows;
    }

    /** Actual attached compiled stages, kept distinct from parsed ProgramSource. */
    private static JsonArray linked(Path directory)throws Exception {
        var result=new JsonArray();var pipeline=Iris.getPipelineManager().getPipelineNullable();
        if(!(pipeline instanceof net.irisshaders.iris.pipeline.IrisRenderingPipeline iris))return result;
        int bound=org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM);
        var terrain=Map.of("solid",net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.SOLID,
            "cutout",net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.CUTOUT,
            "translucent",net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.TRANSLUCENT);
        for(var entry:terrain.entrySet()) {
            var program=iris.getSodiumPrograms().getProgram(entry.getValue());
            if(program!=null)result.add(stages(directory,"sodium_"+entry.getKey(),program.handle()));
        }
        for(String group:List.of("deferredRenderer","compositeRenderer")) {
            var renderer=field(pipeline,group);if(renderer==null)continue;
            var passes=(List<?>)field(renderer,"passes");if(passes.size()>100)throw new IllegalStateException("Bounded pass inventory");
            int index=0;
            for(var pass:passes) {
                var program=(net.irisshaders.iris.gl.program.Program)field(pass,"program");
                if(program!=null)result.add(stages(directory,group+"_"+(index++),program.getProgramId()));
            }
        }
        if(Iris.getPipelineManager().getPipelineNullable()!=pipeline
                ||org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM)!=bound)
            throw new IllegalStateException("Read-only compiled source export changed state");
        return result;
    }
    private static Object field(Object owner,String name)throws Exception {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private static JsonObject stages(Path directory,String name,int program)throws Exception {
        var row=new JsonObject();row.addProperty("name",name);row.addProperty("program",program);
        row.addProperty("linked",org.lwjgl.opengl.GL20C.glGetProgrami(program,org.lwjgl.opengl.GL20C.GL_LINK_STATUS)==1);
        var controls=new JsonObject();var samplers=new JsonObject();
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
            var size=stack.mallocInt(1);var type=stack.mallocInt(1);
            int uniforms=org.lwjgl.opengl.GL20C.glGetProgrami(program,org.lwjgl.opengl.GL20C.GL_ACTIVE_UNIFORMS);
            if(uniforms>4096)throw new IllegalStateException("Bounded active uniform inventory");
            for(int i=0;i<uniforms;i++) {
                String uniform=org.lwjgl.opengl.GL20C.glGetActiveUniform(program,i,1024,size,type);
                if(size.get(0)!=1)continue;
                int location=org.lwjgl.opengl.GL20C.glGetUniformLocation(program,uniform),kind=type.get(0);
                if(location<0)continue;
                if(kind==org.lwjgl.opengl.GL20C.GL_SAMPLER_2D||kind==org.lwjgl.opengl.GL20C.GL_SAMPLER_2D_SHADOW)
                    samplers.addProperty(uniform,org.lwjgl.opengl.GL20C.glGetUniformi(program,location));
                if(!uniform.startsWith("shaderlod"))continue;
                if(kind==org.lwjgl.opengl.GL20C.GL_INT||kind==org.lwjgl.opengl.GL20C.GL_BOOL)
                    controls.addProperty(uniform,org.lwjgl.opengl.GL20C.glGetUniformi(program,location));
                else if(kind==org.lwjgl.opengl.GL20C.GL_FLOAT)
                    controls.addProperty(uniform,org.lwjgl.opengl.GL20C.glGetUniformf(program,location));
            }
        }
        row.add("scalar_controls",controls);row.add("sampler_units",samplers);
        int count=org.lwjgl.opengl.GL20C.glGetProgrami(program,org.lwjgl.opengl.GL20C.GL_ATTACHED_SHADERS);
        if(count<0||count>8)throw new IllegalStateException("Bounded attached stage inventory");
        var rows=new JsonArray();
        if(count>0) {
            int[] shaders=new int[count];org.lwjgl.opengl.GL20C.glGetAttachedShaders(program,(int[])null,shaders);
            for(int shader:shaders) {
                String text=org.lwjgl.opengl.GL20C.glGetShaderSource(shader);
                byte[] data=text.getBytes(StandardCharsets.UTF_8);
                if(data.length>1024*1024)throw new IllegalStateException("Bounded compiled stage export");
                int type=org.lwjgl.opengl.GL20C.glGetShaderi(shader,org.lwjgl.opengl.GL20C.GL_SHADER_TYPE);
                String file="linked_"+name+"_"+type+".glsl";
                Files.write(directory.resolve(file),data,StandardOpenOption.CREATE_NEW);
                var stage=new JsonObject();stage.addProperty("file",file);stage.addProperty("type",type);
                stage.addProperty("bytes",data.length);rows.add(stage);
            }
        }
        row.add("stages",rows);row.addProperty("scope","Actual attached GL source; empty stages mean unavailable, never inferred equivalent");
        return row;
    }
}
