package com.pp.shaderlod.optimization.framecopy.rotation.mixin;

import com.pp.shaderlod.optimization.framecopy.rotation.HistoryRotationPins;
import com.pp.shaderlod.optimization.framecopy.rotation.HistoryRotationRuntime;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Default-off startup selection plus mandatory, bounded transformed call-site shape checks. */
public final class HistoryRotationMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String mixin) {
        return Boolean.getBoolean(HistoryRotationRuntime.STARTUP_PROPERTY) && HistoryRotationPins.exactApi();
    }
    public void acceptTargets(Set<String> own, Set<String> other) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (mixin.endsWith("HistoryRotationDrawMixin")) {
            shape(node, "_drawElements", Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glDrawElements", 1);
        } else if (mixin.endsWith("HistoryRotationPipelineMixin")) {
            shape(node, "beginLevelRendering", Opcodes.INVOKEVIRTUAL, "net/irisshaders/iris/pipeline/CompositeRenderer", "renderAll", 1);
            shape(node, "destroy", Opcodes.RETURN, null, null, 1);
        } else if (mixin.endsWith("HistoryRotationFrameMixin")) {
            int methods = 0;
            for (MethodNode method : node.methods) if (method.desc.equals("(Lnet/minecraft/client/DeltaTracker;)V")
                   ) methods++;
            if (methods != 1) throw new IllegalStateException("HISTORY_ROTATION_OUTER_FRAME_SHAPE");
        } else if (mixin.endsWith("HistoryRotationCompositeMixin")) {
            shape(node, "createProgram", Opcodes.INVOKESTATIC, "net/irisshaders/iris/gl/program/ProgramBuilder", "begin", 1);
            shape(node, "renderAll", Opcodes.GETFIELD, "net/irisshaders/iris/pipeline/CompositeRenderer$Pass", "framebuffer", 1);
            shape(node, "renderAll", Opcodes.INVOKEVIRTUAL, "net/irisshaders/iris/pathways/FullScreenQuadRenderer", "renderQuad", 1);
        } else if (mixin.endsWith("HistoryRotationFinalMixin")) {
            shape(node, "renderFinalPass", Opcodes.GETFIELD, "net/irisshaders/iris/pipeline/FinalPassRenderer", "swapPasses", 1);
            shape(node, "renderFinalPass", Opcodes.INVOKESTATIC, "com/mojang/blaze3d/platform/GlStateManager", "_glCopyTexSubImage2D", 1);
        } else if (mixin.endsWith("HistoryRotationSamplerMixin")) {
            shape(node, "lambda$addRenderTargetSamplers$0", Opcodes.IRETURN, null, null, 2);
        } else if (mixin.endsWith("HistoryRotationTargetsMixin")) {
            shape(node, "resizeIfNeeded", Opcodes.IRETURN, null, null, 1);
            shape(node, "destroy", Opcodes.RETURN, null, null, 1);
        }
    }
    private static void shape(ClassNode node, String method, int opcode, String owner, String member, int expected) {
        int count = 0, methods = 0;
        for (MethodNode m : node.methods) {
            if (!m.name.equals(method)) continue; methods++;
            for (AbstractInsnNode instruction : m.instructions) {
                if (instruction.getOpcode() != opcode) continue;
                if (owner == null || instruction instanceof FieldInsnNode f && f.owner.equals(owner) && f.name.equals(member)
                        || instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(member)) count++;
            }
        }
        if (methods != 1 || count != expected) throw new IllegalStateException("HISTORY_ROTATION_HOOK_SHAPE:" + method);
    }
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        HistoryRotationPins.hookVerified(mixin.substring(mixin.lastIndexOf('.') + 1));
    }
}
