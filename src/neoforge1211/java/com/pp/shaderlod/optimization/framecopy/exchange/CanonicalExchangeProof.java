package com.pp.shaderlod.optimization.framecopy.exchange;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.IntSupplier;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.*;

/** Setup-only closure over the pinned current S graph; no external proof attestations. */
final class CanonicalExchangeProof {
    record Source(String name,String vertex,String fragment,boolean geometry) {}
    record Alias(GlFramebuffer framebuffer,int slot,int originalBank) {}
    record FinalCopy(int target,GlFramebuffer from,int destination,int width,int height) {}
    private static final Map<Program,Source> SOURCES=new WeakHashMap<>();
    private static final Map<String,List<String>> EXPECTED=Map.ofEntries(
        Map.entry("prepare",List.of("e679094d53599470d0e301e955f78f499b6c22ae0d3f7d88e4df652f019091d9","e9a95baa4beb86a9a9d83ce3d59114a2a2ddab1735bc27d150fff2bd62974b37")),
        Map.entry("deferred",List.of("1f35e005923e1378249b778820876e504f7fd013bab69e0b2b469d24c35586a3","f90790fba814d2515d2d2bd31a5bc8d7aa96455ee2dc66c12e10d66bcfd0d4c5")),
        Map.entry("composite",List.of("7a25c891e6c709d7ff7840bfe4d8b56bf8d0fee8d3d180f29942b39073189b0c","57f03214a938a64d4d9650950df45407f0fb6eabde9f3bc939a8786233486b94")),
        Map.entry("composite1",List.of("83a0b61cf2b244d4bd38069219a672be05ad7e582dc85cbb5efa9f1f006de33c","4813301bac832b68d9b9d2dbd3cb0b0bcc11ebdce2bded48e7660de699d83518")),
        Map.entry("composite2",List.of("d380b5367df5c7464a844ab9e1216ff962c7f63c178c0477e10228266cc5cf99","2cc97b982657eb4f2d9f9c320006a2e3af15573a00925bf84cb02974b7503b0e")),
        Map.entry("composite3",List.of("d380b5367df5c7464a844ab9e1216ff962c7f63c178c0477e10228266cc5cf99","e30e2853c1d6a5a86df1d4c8fd3c324b4a7814c75cd8991587175bf93148c5d4")),
        Map.entry("final",List.of("6d6ca5020d37fdef371d5bd517904e0a7e80c686f7eb2ad428e504dfb55bd0f9","345406775e0a401ede57940475eb14142350090adc6ba8fdd1d2cfdc7a799b73"))
    );
    static void compiled(ProgramSource source,Program program,Map<?,String> transformed) {
        if(program==null)return;
        String vertex=null,fragment=null;boolean geometry=false;
        for(var entry:transformed.entrySet())if(entry.getValue()!=null) {
            switch(entry.getKey().toString().toLowerCase(Locale.ROOT)) {
                case "vertex" -> vertex=sha(entry.getValue());
                case "fragment" -> fragment=sha(entry.getValue());
                default -> geometry=true;
            }
        }
        SOURCES.put(program,new Source(source.getName(),vertex,fragment,geometry));
    }
    final Object pipeline,finalRenderer,swap3;
    final RenderTargets targets;
    final RenderTarget rt3;
    final Program producer;
    final GlFramebuffer producerFbo;
    final Field swapDestination;
    final int a,b,width,height,rt1,programCount,framebufferCount;
    final List<Alias> aliases,clearAliases,exchangeAliases;
    final List<Layout> layouts;
    private boolean testAliasFailure;
    final List<FinalCopy> finalCopies;
    CanonicalExchangeProof(Object pipeline)throws Exception {
        this.pipeline=pipeline;targets=(RenderTargets)field(pipeline,"renderTargets");rt3=targets.get(3);
        require(rt3!=null && !targets.isFullClearRequired(),"DEFINED_RT3_REQUIRED");
        a=rt3.getMainTexture();b=rt3.getAltTexture();width=rt3.getWidth();height=rt3.getHeight();
        require(a>0&&b>0&&a!=b&&width>0&&height>0&&rt3.getInternalFormat().name().equals("R11F_G11F_B10F"),"RT3_STORAGE");
        Object composite=field(pipeline,"compositeRenderer"),chosen=null;
        for(Object pass:(List<?>)field(composite,"passes"))if("composite3".equals(field(pass,"name"))) {require(chosen==null,"UNIQUE_PRODUCER");chosen=pass;}
        require(chosen!=null,"NO_PRODUCER");producer=(Program)field(chosen,"program");producerFbo=(GlFramebuffer)field(chosen,"framebuffer");
        require(Arrays.equals((int[])field(chosen,"drawBuffers"),new int[]{1,3})
            &&!((Set<?>)field(chosen,"stageReadsFromAlt")).contains(3)
            &&((Set<?>)field(chosen,"mipmappedBuffers")).isEmpty()
            &&field(chosen,"blendModeOverride")==null&&!producerFbo.hasDepthAttachment()
            &&(int)field(chosen,"viewWidth")==width&&(int)field(chosen,"viewHeight")==height,"PRODUCER_GRAPH");
        require(producerFbo.getColorAttachment(1)==b,"PRODUCER_WRITE_BANK");rt1=producerFbo.getColorAttachment(0);
        require(rt1>0&&rt1!=a&&rt1!=b,"RT1_DISTINCT");
        require(((Set<?>)field(pipeline,"customImages")).isEmpty(),"CUSTOM_IMAGES");
        for(String name:List.of("setup","shadowComputes","clearImages"))for(Object value:(Object[])field(pipeline,name))require(value==null,"EARLY_COMPUTE_OR_IMAGE");
        var allPrograms=new IdentityHashMap<Object,Integer>();
        for(Object shader:(Collection<?>)field(pipeline,"loadedShaders")) {
            require(shader.getClass().getName().equals("net.irisshaders.iris.pipeline.programs.ExtendedShader"),"NON_PINNED_GEOMETRY_PATH");
            allPrograms.put(shader,((ShaderInstance)shader).getId());
        }
        Object sodium=field(pipeline,"sodiumPrograms");
        for(Object value:((Map<?,?>)field(sodium,"shaders")).values()) {
            var gl=(net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?>)value;allPrograms.put(gl.getInterface(),gl.handle());
        }
        var sourceNames=new HashSet<String>();
        for(String name:List.of("beginRenderer","prepareRenderer","deferredRenderer","compositeRenderer","finalPassRenderer","shadowCompositeRenderer")) {
            Object renderer=field(pipeline,name);if(renderer==null)continue;
            List<?> passes;
            if(name.equals("finalPassRenderer")){Object pass=field(renderer,"finalPass");passes=pass==null?List.of():List.of(pass);}
            else passes=(List<?>)field(renderer,"passes");
            for(Object pass:passes) {
                Object computes=optional(pass,"computes");if(computes instanceof Object[] array)for(Object value:array)require(value==null,"COMPUTE_PASS");
                Object value=optional(pass,"program");require(value instanceof Program,"NON_GRAPHICS_PASS");var program=(Program)value;
                var source=SOURCES.get(program);require(source!=null&&!source.geometry&&EXPECTED.containsKey(source.name)
                    &&EXPECTED.get(source.name).equals(List.of(source.vertex,source.fragment))&&sourceNames.add(source.name),"CURRENT_CONSUMED_SOURCE_JOIN");
                allPrograms.put(program,program.getProgramId());
            }
        }
        require(sourceNames.equals(EXPECTED.keySet()),"EXACT_FULLSCREEN_PROGRAM_SET");
        int historyBindings=0;
        for(var entry:allPrograms.entrySet()) {
            Object images=field(entry.getKey(),"images");require(((List<?>)field(images,"imageBindings")).isEmpty(),"ACTIVE_IMAGES");
            Object samplers=field(entry.getKey(),"samplers");
            for(Object binding:(List<?>)field(samplers,"samplerBindings")) {
                int texture=((IntSupplier)field(binding,"texture")).getAsInt();
                if(texture==a||texture==b) {
                    require(entry.getKey()==producer && texture==a,"UNOWNED_OR_WRONG_EARLY_RT3_READER");historyBindings++;
                }
            }
            // Includes legacy alias and image paths, independently of supplier metadata.
            int location=GL20.glGetUniformLocation(entry.getValue(),"colortex3");
            if(location>=0)require(entry.getKey()==producer,"UNOWNED_COLORTEX3");
            require(GL20.glGetUniformLocation(entry.getValue(),"composite")==-1
                &&GL20.glGetUniformLocation(entry.getValue(),"colorimg3")==-1,"LEGACY_OR_IMAGE_HISTORY_ALIAS");
        }
        require(allPrograms.size()==84&&historyBindings==1,"EXACT_PINNED_READER_CENSUS");programCount=allPrograms.size();
        finalRenderer=field(pipeline,"finalPassRenderer");var swaps=(List<?>)field(finalRenderer,"swapPasses");
        require(swaps.size()==3,"CURRENT_S_FINAL_ORDER");var copies=new ArrayList<FinalCopy>();Object selected=null;
        for(int i=0;i<swaps.size();i++) {
            Object swap=swaps.get(i);int target=(int)field(swap,"target");require(target==new int[]{8,1,3}[i],"CURRENT_S_FINAL_ORDER");
            var rt=targets.get(target);var from=(GlFramebuffer)field(swap,"from");int destination=(int)field(swap,"targetTexture");
            require(from.getColorAttachment(0)==rt.getAltTexture()&&destination==rt.getMainTexture(),"FINAL_NATIVE_ENDPOINTS");
            copies.add(new FinalCopy(target,from,destination,(int)field(swap,"width"),(int)field(swap,"height")));if(target==3)selected=swap;
        }
        swap3=Objects.requireNonNull(selected);swapDestination=declared(swap3,"targetTexture");finalCopies=List.copyOf(copies);
        var rows=new ArrayList<Alias>();var owned=(List<?>)field(targets,"ownedFramebuffers");require(owned.size()<=256,"FBO_CENSUS_BOUND");framebufferCount=owned.size();
        int previousRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            for(Object value:owned) {
                var fbo=(GlFramebuffer)value;fbo.bindAsReadBuffer();
                for(var entry:((Map<?,?>)field(fbo,"attachments")).entrySet()) {
                    int slot=(int)entry.getKey(),texture=(int)entry.getValue();
                    require(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+slot,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)==texture,"FBO_CACHE_LIVE_JOIN");
                    if(texture==a||texture==b)rows.add(new Alias(fbo,slot,texture==a?0:1));
                }
            }
        }finally{com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,previousRead);}
        require(rows.size()==4&&rows.stream().filter(x->x.originalBank==0).count()==1,"COMPLETE_FOUR_ALIAS_GRAPH");
        var clears=Collections.newSetFromMap(new IdentityHashMap<GlFramebuffer,Boolean>());
        for(Object clear:(List<?>)field(pipeline,"clearPassesFull"))clears.add((GlFramebuffer)field(clear,"framebuffer"));
        require(rows.stream().filter(x->clears.contains(x.framebuffer)).count()==2
            &&rows.stream().anyMatch(x->x.framebuffer==producerFbo&&x.slot==1&&x.originalBank==1)
            &&rows.stream().anyMatch(x->x.framebuffer==finalCopies.get(2).from&&x.slot==0&&x.originalBank==1),"EVERY_CACHED_ALIAS_HAS_OWNER");
        aliases=List.copyOf(rows);clearAliases=rows.stream().filter(x->clears.contains(x.framebuffer)).toList();
        exchangeAliases=rows.stream().filter(x->!clears.contains(x.framebuffer)).toList();
        require(clearAliases.size()==2&&clearAliases.stream().filter(x->x.originalBank==0).count()==1&&clearAliases.stream().filter(x->x.originalBank==1).count()==1
            &&clearAliases.get(0).framebuffer!=clearAliases.get(1).framebuffer,"CANONICAL_CLEAR_PAIR");
        var settings=(net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives.RenderTargetSettings)((Map<?,?>)field(targets,"targetSettingsMap")).get(3);
        require(settings!=null&&!settings.shouldClear(),"RT3_MUST_PERSIST");
        for(Object clear:(List<?>)field(pipeline,"clearPasses")) {
            var fbo=(GlFramebuffer)field(clear,"framebuffer");
            require(((Map<?,?>)field(fbo,"attachments")).values().stream().noneMatch(x->x.equals(a)||x.equals(b)),"ORDINARY_CLEAR_RT3_CONSUMER");
        }
        var pinned=new ArrayList<Layout>();
        for(var alias:aliases)pinned.add(new Layout(alias));layouts=List.copyOf(pinned);
        verifyClearPair();require(GL11.glGetError()==GL11.GL_NO_ERROR,"PREPARE_GL_ERROR");
    }
    /** Immutable physical A/B clear FBOs; only producer/final source and destination change. */
    void selectAliases(int committed,int write)throws Exception {
        require((committed==a&&write==b)||(committed==b&&write==a),"ALIAS_BANK_PAIR");
        CanonicalDefinitionWitness.clean("ALIAS_PRIOR");
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            for(var alias:exchangeAliases) {
                alias.framebuffer.addColorAttachment(alias.slot,write);
                if(testAliasFailure){testAliasFailure=false;throw new IllegalStateException("TEST_PARTIAL_ALIAS_UPDATE");}
            }
            swapDestination.setInt(swap3,committed);
            require((int)swapDestination.get(swap3)==committed,"FINAL_DESTINATION_CACHE");
            CanonicalDefinitionWitness.clean("ALIAS_UPDATE");
        }finally {
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
        }
    }
    Alias clearSource(int texture)throws Exception {
        require(texture==a||texture==b,"KNOWN_CLEAR_SOURCE_BANK");verifyClearPair();
        return clearAliases.stream().filter(x->(x.originalBank==0?a:b)==texture).findFirst().orElseThrow();
    }
    void verifyClearPair()throws Exception {
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {for(var layout:layouts)if(clearAliases.contains(layout.alias))layout.verify(layout.alias.originalBank==0?a:b);}
        finally {
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
        }
        CanonicalDefinitionWitness.clean("CANONICAL_CLEAR_PAIR");
    }
    void verifyNativeAliases()throws Exception {
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            for(var layout:layouts)layout.verify(clearAliases.contains(layout.alias)?(layout.alias.originalBank==0?a:b):b);
            require(swapDestination.getInt(swap3)==a,"CANONICAL_FINAL_DESTINATION");
        }finally{com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);}
        CanonicalDefinitionWitness.clean("CANONICAL_NATIVE_ALIASES");
    }
    Map<String,Object> observeAliases(int committed,int write)throws Exception {
        verifyClearPair();var observed=new ArrayList<Map<String,Object>>();
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {for(var layout:layouts) {
            var alias=layout.alias;boolean clear=clearAliases.contains(alias);int expected=clear?(alias.originalBank==0?a:b):write;layout.verify(expected);
            var grouped=new TreeMap<Integer,Integer>();for(var e:layout.attachments.entrySet())grouped.put(e.getKey(),alias.framebuffer.getColorAttachment(e.getKey()));
            observed.add(Map.of("role",clear?"IMMUTABLE_CLEAR":alias.framebuffer==producerFbo?"PRODUCER":"FINAL_SOURCE","fbo",alias.framebuffer.getId(),"slot",alias.slot,"originalBank",alias.originalBank,
                "expected",expected,"cached",alias.framebuffer.getColorAttachment(alias.slot),"actual",GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+alias.slot,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME),"grouped",grouped,"routing",layout.routing.clone()));
        }}finally{com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);}
        CanonicalDefinitionWitness.clean("UNTIMED_ALIAS_OBSERVATION");
        return Map.of("a",a,"b",b,"committed",committed,"write",write,"cachedDestination",swapDestination.getInt(swap3),"aliases",observed);
    }
    void verifyStorage() {
        int prior=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {for(int texture:new int[]{a,b}) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            require(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)==width
                &&GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)==height
                &&GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT)==GL30.GL_R11F_G11F_B10F,"KNOWN_RETURN_STORAGE");
        }}finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,prior);}
        CanonicalDefinitionWitness.clean("KNOWN_RETURN_STORAGE");
    }
    void testPartialAliasFailure(){testAliasFailure=true;}
    /** Setup-owned immutable attachment/routing inventory, including grouped non-RT3 slots. */
    private final class Layout {
        final Alias alias;final Map<Integer,Integer> attachments;final Map<?,?> cache;final int[] routing;
        Layout(Alias alias)throws Exception {
            this.alias=alias;cache=(Map<?,?>)field(alias.framebuffer,"attachments");var map=new HashMap<Integer,Integer>();
            for(var e:cache.entrySet())map.put((int)e.getKey(),(int)e.getValue());
            attachments=Map.copyOf(map);routing=new int[GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS)];
            int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            try{alias.framebuffer.bindAsDrawBuffer();for(int i=0;i<routing.length;i++)routing[i]=GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i);}
            finally{com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);}
            require(!alias.framebuffer.hasDepthAttachment(),"RT3_ALIAS_DEPTH_EXCLUDED");
        }
        void verify(int expected)throws Exception {
            alias.framebuffer.bindAsDrawBuffer();
            require(cache.size()==attachments.size(),"GROUPED_ATTACHMENT_COUNT");
            for(var e:attachments.entrySet()) {
                int texture=e.getKey()==alias.slot?expected:e.getValue();
                require(Objects.equals(cache.get(e.getKey()),texture)
                    &&GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+e.getKey(),GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)==GL11.GL_TEXTURE
                    &&GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+e.getKey(),GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)==texture
                    &&GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+e.getKey(),GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL)==0,"GROUPED_ATTACHMENT_CACHE_LIVE");
            }
            require(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"ALIAS_FBO_COMPLETE");
            for(int i=0;i<routing.length;i++)require(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i)==routing[i],"GROUPED_ROUTING_UNCHANGED");
        }
    }
    static Object optional(Object owner,String name)throws Exception {try{return field(owner,name);}catch(NoSuchFieldException absent){return null;}}
    static Object field(Object owner,String name)throws Exception{return declared(owner,name).get(owner);}
    private static Field declared(Object owner,String name)throws Exception {
        for(Class<?> c=owner.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException absent){}
        throw new NoSuchFieldException(name);
    }
    private static String sha(String text) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
    private static void require(boolean valid,String reason){if(!valid)throw new IllegalStateException(reason);}
}
