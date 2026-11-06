package com.pp.shaderlod.dev.mixin;

import java.util.Arrays;
import java.util.ArrayList;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import java.util.function.IntSupplier;

/** Failure-only evidence. Never changes bindings, limits, admission or shader source. */
@Mixin(targets="net.irisshaders.iris.gl.program.ProgramSamplers$Builder",remap=false)
public abstract class SamplerAllocationFailureMixin {
    @Shadow @Final private int program;
    @Shadow private int remainingUnits;

    @Inject(method="addDynamicSampler(Lnet/irisshaders/iris/gl/texture/TextureType;Ljava/util/function/IntSupplier;Lnet/irisshaders/iris/gl/sampler/GlSampler;ZLnet/irisshaders/iris/gl/state/ValueUpdateNotifier;[Ljava/lang/String;)Z",at=@At("HEAD"),require=1)
    private void shaderlod$allocationFailure(TextureType texture,IntSupplier supplier,GlSampler sampler,
            boolean defaultSampler,ValueUpdateNotifier notifier,String[] names,CallbackInfoReturnable<Boolean> ci) {
        if (remainingUnits>0 || Arrays.stream(names).noneMatch(name->GL20C.glGetUniformLocation(program,name)>=0)) return;
        try (var stack=MemoryStack.stackPush()) {
            var size=stack.mallocInt(1);var type=stack.mallocInt(1);
            var uniforms=new ArrayList<String>();
            int count=GL20C.glGetProgrami(program,GL20C.GL_ACTIVE_UNIFORMS);
            for(int i=0;i<count;i++) {
                String name=GL20C.glGetActiveUniform(program,i,1024,size,type);
                uniforms.add(name+":"+Integer.toHexString(type.get(0))+"["+size.get(0)+"]");
            }
            System.err.println("ShaderLOD sampler allocation failure: program="+program+
                " fragmentLimit="+GL20C.glGetInteger(GL20C.GL_MAX_TEXTURE_IMAGE_UNITS)+
                " requested="+Arrays.toString(names)+" activeUniforms="+uniforms);
        }
    }
}
