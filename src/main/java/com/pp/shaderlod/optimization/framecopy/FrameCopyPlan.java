package com.pp.shaderlod.optimization.framecopy;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.targets.RenderTargets;

/** Built only on diagnostic activation/reset/resize. Reads Java metadata; no GPU queries or source scans. */
public record FrameCopyPlan(List<FrameCopyCounters.Target> targets, String status) {
    public static FrameCopyPlan read(RenderTargets all, List<?> swaps) {
        try {
            var rows = new ArrayList<FrameCopyCounters.Target>(swaps.size());
            for (Object swap : swaps) {
                int index = (int) field(swap, "target");
                var target = all.get(index);
                var source = (GlFramebuffer) field(swap, "from");
                int destination = (int) field(swap, "targetTexture");
                int width = (int) field(swap, "width"), height = (int) field(swap, "height");
                if (target == null || source.getColorAttachment(0) != target.getAltTexture()
                        || destination != target.getMainTexture() || width != target.getWidth() || height != target.getHeight())
                    return new FrameCopyPlan(List.of(), "SWAP_METADATA_MISMATCH");
                String format = target.getInternalFormat().name();
                rows.add(new FrameCopyCounters.Target(index, source.getId(), target.getAltTexture(), destination,
                        width, height, format, FrameCopyFormat.bytesPerTexel(format)));
            }
            return new FrameCopyPlan(List.copyOf(rows), "OK");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return new FrameCopyPlan(List.of(), "SWAP_METADATA_UNAVAILABLE");
        }
    }
    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
}
