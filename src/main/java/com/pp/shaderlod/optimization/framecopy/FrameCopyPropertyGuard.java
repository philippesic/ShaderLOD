package com.pp.shaderlod.optimization.framecopy;

import java.util.ArrayList;
import java.util.List;
import net.irisshaders.iris.helpers.StringPair;

/** Setup-only capability shared by GLSL and properties. Persisted options cannot authorize the graph. */
public final class FrameCopyPropertyGuard {
    public static final String DEFINE = "SHADERLOD_FRAMECOPY_RUNTIME_GUARD";
    private FrameCopyPropertyGuard() {}

    /** The ShaderPack constructor passes this same capability to programs and properties. */
    public static com.google.common.collect.ImmutableList<StringPair> constructorDefines(
            com.google.common.collect.ImmutableList<StringPair> input, boolean exactApi) {
        return com.google.common.collect.ImmutableList.copyOf(defines(input, exactApi));
    }

    public static List<StringPair> defines(List<StringPair> input, boolean exactApi) {
        var result=new ArrayList<StringPair>(input.size()+1);
        for(var define:input)if(!DEFINE.equals(define.key()))result.add(define);
        if(exactApi)result.add(new StringPair(DEFINE,"1"));
        return List.copyOf(result);
    }
}
