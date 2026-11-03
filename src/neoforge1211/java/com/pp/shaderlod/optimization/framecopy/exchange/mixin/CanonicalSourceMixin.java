package com.pp.shaderlod.optimization.framecopy.exchange.mixin;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.pp.shaderlod.optimization.framecopy.exchange.CanonicalBankExchange;
import java.util.*;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.*;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value={CompositeRenderer.class,FinalPassRenderer.class},remap=false)
public abstract class CanonicalSourceMixin {
    @Unique private Map<?,String> exchange$source=Map.of();
    @ModifyExpressionValue(method="createProgram",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchComposite(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/shaderpack/texture/TextureStage;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"),require=1,allow=1)
    private Map<?,String> exchange$source(Map<?,String> value){exchange$source=new HashMap<>(value);return value;}
    @Inject(method="createProgram",at=@At("RETURN"),require=1,allow=1)
    private void exchange$linked(ProgramSource source,ImmutableSet<Integer> flipped,ImmutableSet<Integer> once,Supplier<ShadowRenderTargets> shadows,CallbackInfoReturnable<Program> ci){CanonicalBankExchange.compiled(source,ci.getReturnValue(),exchange$source);exchange$source=Map.of();}
}
