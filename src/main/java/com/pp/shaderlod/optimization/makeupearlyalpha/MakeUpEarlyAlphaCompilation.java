package com.pp.shaderlod.optimization.makeupearlyalpha;

import com.pp.shaderlod.optimization.CompileOptionControl;
import com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

/** Actual observed compile options, independent of transient runtime owners and permissions. */
public final class MakeUpEarlyAlphaCompilation {
    private MakeUpEarlyAlphaCompilation() {}
    public static boolean enabled(ProgramSource source, ProgramSet set) {
        return source != null && set != null && source.getParent() == set && enabled(set.getPack());
    }
    public static boolean enabled(ShaderPack pack) {
        if (!(pack instanceof IrisCompiledSource.PackView view)) return false;
        var input = view.shaderlod$makeupPackInput();
        return input != null && "1".equals(input.options().values().get(CompileOptionControl.MAKEUP_EARLY_ALPHA))
            && input.options().current(pack.getShaderPackOptions());
    }
}
