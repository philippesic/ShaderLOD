package com.pp.shaderlod.optimization.reimaginedearlyalpha.mixin;

import com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedCompilationInputs;
import com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedCompilationInputsView;
import java.util.Map;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Unregistered observation hook. Requires explicit API pins/configuration before activation. */
@Mixin(value = ShaderPackOptions.class, remap = false)
public abstract class ReimaginedCompilationInputsMixin implements ReimaginedCompilationInputsView {
    @Unique private ReimaginedCompilationInputs shaderlod$reimaginedCompilationInputs;

    @Shadow public abstract OptionSet getOptionSet();
    @Shadow public abstract OptionValues getOptionValues();

    @Inject(
            method = "<init>(Lnet/irisshaders/iris/shaderpack/include/IncludeGraph;Ljava/util/Map;)V",
            at = @At("RETURN"), require = 1, allow = 1)
    private void shaderlod$captureCompilationInputs(
            IncludeGraph rawGraph, Map<String, String> changedConfigs, CallbackInfo callback) {
        // Constructor publication is the boundary: these are the same values used by graph.map.
        // An unavailable observation must not turn a successful native construction into a failure.
        shaderlod$reimaginedCompilationInputs = null;
        try {
            shaderlod$reimaginedCompilationInputs = ReimaginedCompilationInputs.capture(
                    rawGraph, getOptionSet(), getOptionValues());
        } catch (RuntimeException | LinkageError unavailable) {
            // No evidence means no certificate; this hook never changes admission itself.
        }
    }

    @Override
    public ReimaginedCompilationInputs shaderlod$reimaginedCompilationInputs() {
        return shaderlod$reimaginedCompilationInputs;
    }
}
