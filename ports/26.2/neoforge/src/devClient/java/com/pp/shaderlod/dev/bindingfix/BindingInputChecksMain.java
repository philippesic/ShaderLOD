package com.pp.shaderlod.dev.bindingfix;
/** Pure recorded shape checks, never a synthetic GL rendering pass. Parent runs after real compile. */
public final class BindingInputChecksMain {
    private static void check(boolean pass) {if(!pass)throw new AssertionError("Binding input contract mismatch");}
    private static void modeAndCacheChecks() {
        var original=new java.util.Properties();original.setProperty("shaderlod.dev.portBindingInputFix","true");
        original.setProperty("shaderlod.dev.portRoundtrip","true");original.setProperty("shaderlod.dev.portOriginalOnly","true");
        check(BindingInputModes.mode(original)==BindingInputModes.Mode.ORIGINAL_ONLY);
        check(BindingInputModes.coreAllows(BindingInputModes.Mode.ORIGINAL_ONLY,true,false,false,true));
        check(!BindingInputModes.coreAllows(BindingInputModes.Mode.ORIGINAL_ONLY,true,true,false,true));
        var speed=new java.util.Properties();speed.putAll(original);speed.setProperty("shaderlod.dev.portOriginalOnly","false");
        speed.setProperty("shaderlod.dev.portRendererSpeedProbe","true");speed.setProperty("shaderlod.experimentalRendererPort092","true");
        check(BindingInputModes.mode(speed)==BindingInputModes.Mode.RENDERER_SPEED);
        for(boolean enabled:new boolean[]{false,true,false})check(BindingInputModes.coreAllows(BindingInputModes.Mode.RENDERER_SPEED,true,enabled,false,true));
        for(String flag:java.util.List.of("shaderlod.dev.portRoundtripUi","shaderlod.dev.portQualityRoundtrip","shaderlod.dev.portRoundtripDh",
            "shaderlod.dev.portOriginalOnly","shaderlod.experimentalRendererResearch","shaderlod.photonQuadOriginalGraphR1","shaderlod.dev.portUnknown")) {
            var conflict=new java.util.Properties();conflict.putAll(speed);conflict.setProperty(flag,"true");check(BindingInputModes.mode(conflict)==BindingInputModes.Mode.REFUSED);
        }
        var missing=new java.util.Properties();missing.putAll(speed);missing.remove("shaderlod.experimentalRendererPort092");check(BindingInputModes.mode(missing)==BindingInputModes.Mode.REFUSED);
        var malformed=new java.util.Properties();malformed.putAll(speed);malformed.setProperty("shaderlod.dev.portRendererSpeedProbe","yes");check(BindingInputModes.mode(malformed)==BindingInputModes.Mode.REFUSED);
        check(!BindingInputModes.coreAllows(BindingInputModes.Mode.RENDERER_SPEED,false,false,false,true));
        check(!BindingInputModes.coreAllows(BindingInputModes.Mode.RENDERER_SPEED,true,false,true,true));
        check(!BindingInputModes.coreAllows(BindingInputModes.Mode.RENDERER_SPEED,true,false,false,false));
        var control=new java.util.Properties();control.setProperty("shaderlod.dev.portBindingInputFix","true");control.setProperty("shaderlod.dev.portRoundtrip","true");control.setProperty("shaderlod.dev.portRoundtripUi","true");control.setProperty("shaderlod.dev.portNativeFamily","makeup-volume");
        check(BindingInputModes.mode(control)==BindingInputModes.Mode.MAKEUP_CONTROL);
        check(BindingInputModes.coreAllows(BindingInputModes.Mode.MAKEUP_CONTROL,true,false,false,"BALANCED"));check(BindingInputModes.coreAllows(BindingInputModes.Mode.MAKEUP_CONTROL,true,true,false,"BALANCED"));check(BindingInputModes.coreAllows(BindingInputModes.Mode.MAKEUP_CONTROL,true,true,false,"QUALITY"));
        for(String preset:java.util.List.of("PERFORMANCE","UNKNOWN"))check(!BindingInputModes.coreAllows(BindingInputModes.Mode.MAKEUP_CONTROL,true,true,false,preset));check(!BindingInputModes.coreAllows(BindingInputModes.Mode.MAKEUP_CONTROL,true,false,false,"QUALITY"));check(!BindingInputModes.coreAllows(BindingInputModes.Mode.MAKEUP_CONTROL,true,true,true,"BALANCED"));
        for(String flag:java.util.List.of("shaderlod.dev.portOriginalOnly","shaderlod.dev.portRendererSpeedProbe","shaderlod.experimentalRendererPort092","shaderlod.dev.portQualityRoundtrip","shaderlod.dev.portRoundtripDh")){var wrong=new java.util.Properties();wrong.putAll(control);wrong.setProperty(flag,"true");check(BindingInputModes.mode(wrong)==BindingInputModes.Mode.REFUSED);}
        var unknown=new java.util.Properties();unknown.putAll(control);unknown.setProperty("shaderlod.dev.portNativeFamily","other");check(BindingInputModes.mode(unknown)==BindingInputModes.Mode.REFUSED);
        Object pack=new Object(),pipeline=new Object(),world=new Object(),values=new Object();
        var cache=new BindingInputCertificateCache();var booleans=new java.util.HashMap<String,Boolean>();var strings=new java.util.HashMap<String,String>();
        // Recorded native inputs have no override changes; actual native100/AA2 map is separately hash-certified.
        var resources=java.util.List.of("vanilla","mod_resources");String name="MakeUp-UltraFast-9.5g.zip";Object stamp="source-stamp-A";
        long generation=com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation();
        check(!cache.matches(pack,pipeline,world,values,generation,name,booleans,strings,resources,stamp));
        cache.remember(pack,pipeline,world,values,generation,name,booleans,strings,resources,stamp,true);
        check(cache.matches(pack,pipeline,world,values,generation,name,booleans,strings,resources,stamp) && cache.eligible());
        java.util.Collection<String> orderedResources=new java.util.LinkedHashSet<>(resources);
        // Actual API promises Collection, not List equality; same order must reuse without copying.
        check(cache.matches(pack,pipeline,world,values,generation,name,booleans,strings,orderedResources,stamp));
        check(!cache.matches(pack,pipeline,world,values,generation,name,booleans,strings,new java.util.LinkedHashSet<>(java.util.List.of("mod_resources","vanilla")),stamp));
        check(!cache.matches(pack,pipeline,world,values,generation,name,booleans,strings,java.util.List.of("vanilla","vanilla"),stamp));
        check(!cache.matches(pack,pipeline,world,values,generation,name,booleans,strings,null,stamp));
        // Actual lifecycle transaction makes identical object/map inputs stale at both begin and end.
        com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.beginSettingsApply();
        try {check(!cache.matches(pack,pipeline,world,values,com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation(),name,booleans,strings,resources,stamp));}
        finally {com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.endSettingsApply();}
        check(!cache.matches(pack,pipeline,world,values,com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation(),name,booleans,strings,resources,stamp));
        check(!cache.matches(new Object(),pipeline,world,values,generation,name,booleans,strings,resources,stamp));
        check(!cache.matches(pack,new Object(),world,values,generation,name,booleans,strings,resources,stamp));
        check(!cache.matches(pack,pipeline,new Object(),values,generation,name,booleans,strings,resources,stamp));
        check(!cache.matches(pack,pipeline,world,new Object(),generation,name,booleans,strings,resources,stamp));
        booleans.put("FOREIGN_OPTION",true);check(!cache.matches(pack,pipeline,world,values,generation,name,booleans,strings,resources,stamp));booleans.clear();
        strings.put("AA_TYPE","1");check(!cache.matches(pack,pipeline,world,values,generation,name,booleans,strings,resources,stamp));strings.clear();
        check(!cache.matches(pack,pipeline,world,values,generation,"other.zip",booleans,strings,resources,stamp));
        check(!cache.matches(pack,pipeline,world,values,generation,name,booleans,strings,java.util.List.of("vanilla","foreign"),stamp));
        check(!cache.matches(pack,pipeline,world,values,generation,name,booleans,strings,resources,"source-stamp-B"));
        cache.invalidate();check(!cache.eligible());check(!cache.matches(pack,pipeline,world,values,generation,name,booleans,strings,resources,stamp));
        cache.remember(pack,pipeline,world,values,generation,name,booleans,strings,resources,stamp,false);check(!cache.eligible());
    }
    public static void main(String[] args) throws Exception {
        java.nio.file.Path archive=java.nio.file.Path.of(args.length==1?args[0]:"dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip");
        var nativeOptions=com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.nativeOptions();
        check(nativeOptions.size()==100 && "2".equals(nativeOptions.get("AA_TYPE")));
        check(com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.validate(archive,nativeOptions).eligible());
        check(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.validate(java.nio.file.Path.of("definitely-missing-MakeUp.zip"),nativeOptions).eligible());
        var changedOptions=new java.util.TreeMap<>(nativeOptions);changedOptions.put("AA_TYPE","1");
        check(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.validate(archive,changedOptions).eligible());
        modeAndCacheChecks();
        Object format=new Object(),irisFormat=new Object(),buffer=new Object();
        // Authentic observed shape: native16 descriptor slots, actual16 buffers, only slot0 declared/bound, Iris return1.
        Object[] emptyDescriptor=new Object[16],emptyBuffers=new Object[16];
        // Authentic screenquad native shape: no attributes/buffers, gl_VertexID. It is ineligible for correction.
        check(BindingInputContract.noBoundSlots(emptyBuffers));check(!BindingInputContract.soleBoundSlotZero(emptyBuffers));
        check(!BindingInputContract.canRestore(emptyDescriptor,emptyDescriptor,emptyBuffers));
        check(BindingInputContract.restoreOrOriginal(true,emptyDescriptor,emptyDescriptor,emptyBuffers)==emptyDescriptor);
        check(!BindingInputContract.noBoundSlots(new Object[1]));check(!BindingInputContract.noBoundSlots(null));
        Object[] descriptor=new Object[16],buffers=new Object[16],rewritten={irisFormat};descriptor[0]=format;buffers[0]=buffer;
        check(!BindingInputContract.noBoundSlots(buffers));
        // Actual collection consumer binds a slot after scope entry, before its getter/VA call.
        Object[] lateBuffers=new Object[16];
        check(BindingInputContract.restoreOrOriginal(true,descriptor,rewritten,lateBuffers)==rewritten);
        lateBuffers[0]=buffer;
        Object[] lateCorrected=BindingInputContract.restoreOrOriginal(true,descriptor,rewritten,lateBuffers);
        check(lateCorrected.length==16 && lateCorrected[0]==irisFormat && lateCorrected!=rewritten);
        lateBuffers[1]=buffer;
        check(BindingInputContract.restoreOrOriginal(true,descriptor,rewritten,lateBuffers)==rewritten);
        check(BindingInputContract.canRestore(descriptor,rewritten,buffers));
        check(BindingInputContract.restoreOrOriginal(false,descriptor,rewritten,buffers)==rewritten);
        Object[] corrected=BindingInputContract.restoreOrOriginal(true,descriptor,rewritten,buffers);
        check(corrected!=descriptor && corrected!=rewritten && corrected.length==16 && corrected[0]==irisFormat && descriptor[0]==format);
        for(int i=1;i<16;i++)check(corrected[i]==null);
        buffers[1]=buffer;check(!BindingInputContract.canRestore(descriptor,rewritten,buffers));check(BindingInputContract.restoreOrOriginal(true,descriptor,rewritten,buffers)==rewritten);buffers[1]=null;
        descriptor[1]=format;check(!BindingInputContract.canRestore(descriptor,rewritten,buffers));check(BindingInputContract.restoreOrOriginal(true,descriptor,rewritten,buffers)==rewritten);descriptor[1]=null;
        buffers[0]=null;check(!BindingInputContract.canRestore(descriptor,rewritten,buffers));check(BindingInputContract.restoreOrOriginal(true,descriptor,rewritten,buffers)==rewritten);buffers[0]=buffer;
        descriptor[0]=null;check(!BindingInputContract.canRestore(descriptor,rewritten,buffers));check(BindingInputContract.restoreOrOriginal(true,descriptor,rewritten,buffers)==rewritten);descriptor[0]=format;
        check(!BindingInputContract.canRestore(descriptor,new Object[]{null},buffers));
        check(!BindingInputContract.canRestore(new Object[1],rewritten,buffers));
        check(!BindingInputContract.canRestore(descriptor,rewritten,new Object[1]));
        check(!BindingInputContract.canRestore(descriptor,new Object[16],buffers));
        check(!BindingInputContract.knownPassClass("unknown.backend.Pass"));check(!BindingInputContract.knownPassClass(null));
        check(BindingInputContract.knownPassClass("com.mojang.blaze3d.opengl.GlRenderPass"));
        System.out.println("Named original-speed modes/lifecycle-input-cache/bufferless-pass-through/single-slot-correction/extra-bound/extra-declared/missing/unknown contract cases passed; real native validation/driver unverified");
    }
}
