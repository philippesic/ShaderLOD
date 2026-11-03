package com.pp.shaderlod.integration.bindingcompat.mixin;
import com.pp.shaderlod.integration.bindingcompat.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
@Mixin(targets="com.mojang.blaze3d.pipeline.RenderPipeline",remap=false)
public abstract class BindingInputPipelineMixin {
 @Shadow @Final private VertexFormat[] vertexFormatPerBuffer;
 @WrapMethod(method="getVertexFormatBindings")
 private VertexFormat[] shaderlod$nativeShape(Operation<VertexFormat[]> original){
  VertexFormat[] rewritten=original.call();var context=MakeUpBindingCompatibility.current();
  if(context==null||context.pipeline()!=(Object)this||rewritten==null||rewritten.length==16)return rewritten;
  VertexFormat[] restored=BindingInputContract.restoreOrOriginal(true,vertexFormatPerBuffer,rewritten,context.boundSlots());
  if(restored!=rewritten)MakeUpBindingCompatibility.corrected(rewritten,restored);return restored;
 }
}
