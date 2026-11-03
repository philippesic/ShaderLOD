package com.pp.shaderlod.optimization.mips;

import com.google.common.collect.ImmutableList;
import net.irisshaders.iris.helpers.StringPair;

/** Constructor-only capability: persisted controls cannot suppress work without the pinned API hook. */
public final class BslMipRuntimeGuard {
    public static final String DEFINE="SHADERLOD_BSL_MIP_RUNTIME_GUARD";
    public static final String C6_DEFINE="SHADERLOD_BSL_C6_MIP_RUNTIME_GUARD";
    private BslMipRuntimeGuard() {}
    /** The constructor must consume the exact archive tree whose active singleton0 state is verified. */
    public static boolean ownedConstructorSource(java.nio.file.Path shaders,java.nio.file.Path selected) {
        try {
            if(shaders==null||!"jar".equals(shaders.toUri().getScheme()))return false;
            String uri=shaders.toUri().getRawSchemeSpecificPart();int split=uri.indexOf("!/");
            if(split<0||!uri.substring(split).equals("!/shaders"))return false;
            var archive=java.nio.file.Path.of(java.net.URI.create(uri.substring(0,split))).toAbsolutePath().normalize();
            if(!archive.equals(selected.toAbsolutePath().normalize()))return false;
            for(var profile:DeadMipAdmission.leanProfiles()) {
                var captured=com.pp.shaderlod.packs.PackSelectionService.captureGeneratedRendererInputs(archive,profile);
                if(com.pp.shaderlod.packs.PackSelectionService.ownedBslLean(archive,profile)
                    &&com.pp.shaderlod.optimization.sourcecache.IrisInputSnapshot.capture(shaders).matches(captured))return true;
            }
            return false;
        }catch(Exception|LinkageError unavailable){return false;}
    }
    public static ImmutableList<StringPair> constructorDefines(ImmutableList<StringPair> input,java.nio.file.Path path,boolean exactApi) {
        boolean normal=com.pp.shaderlod.optimization.CompileOptionControl.normalLeanConstructor(path);
        return defines(input,exactApi,normal||Boolean.getBoolean("shaderlod.experimentalBslC6Mip")&&BslMipWitnessPins.exactApi());
    }
    /** Explicit caller-input seam; production's normalLeanConstructor supplies the same source proof. */
    public static ImmutableList<StringPair> constructorDefines(ImmutableList<StringPair> input,java.nio.file.Path path,
            java.nio.file.Path selected,boolean exactApi,boolean authorized) {
        return defines(input,exactApi,authorized&&ownedConstructorSource(path,selected));
    }
    public static ImmutableList<StringPair> defines(ImmutableList<StringPair> input,boolean exactApi) {
        return defines(input,exactApi,Boolean.getBoolean("shaderlod.experimentalBslC6Mip") && BslMipWitnessPins.exactApi());
    }
    /** The third argument is an explicit experiment on the separately pinned c6 API. */
    public static ImmutableList<StringPair> defines(ImmutableList<StringPair> input,boolean exactApi,boolean exactC6Experiment) {
        var output=ImmutableList.<StringPair>builder();
        for(var value:input)if(!DEFINE.equals(value.key())&&!C6_DEFINE.equals(value.key()))output.add(value);
        if(exactApi)output.add(new StringPair(DEFINE,"1"));
        if(exactApi&&exactC6Experiment)output.add(new StringPair(C6_DEFINE,"1"));
        return output.build();
    }
}
