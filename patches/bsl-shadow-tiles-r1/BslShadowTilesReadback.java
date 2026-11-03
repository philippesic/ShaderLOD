package com.pp.shaderlod.dev;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;

/** Staged, UNWIRED, UNCOMPILED root helper. One untimed same-epoch readback.
 * Root associates actual texture/sampler/program identities and calls only after
 * terrain and before deferred/composite/clear changes either source image.
 * Does not allocate textures, change filters, replay geometry or collect total
 * receiver invocations. A coordinate image is surviving visible terrain only.
 */
public final class BslShadowTilesReadback {
    private static final int[] PACK_KEYS = {GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH,
        GL11.GL_PACK_SKIP_ROWS, GL11.GL_PACK_SKIP_PIXELS, GL11.GL_PACK_SWAP_BYTES,
        GL11.GL_PACK_LSB_FIRST, GL12.GL_PACK_IMAGE_HEIGHT, GL12.GL_PACK_SKIP_IMAGES};
    public record Image(int object, int width, int height, int format) {}
    public record Capture(Image depth, Image coordinates, int shadowSampler,
                          int compareMode, int compareFunc, int minFilter, int magFilter,
                          List<Integer> restoredPack, int restoredPackBuffer,
                          int restoredActiveTexture, int restoredTexture2d) {}
    private BslShadowTilesReadback() {}

    /** Pass exact bound native depth0 texture and sampler IDs from the executed
     * terrain program, never guessed Iris resource fields or fixed texture units.
     * Caller must verify disabled blend/MSAA and coordinate sentinel/format.
     * Caller owns full archive/options/current producerEpoch provenance receipt.
     */
    public static Capture capture(Path directory, int depth0, int depthSampler, int coordinate0) throws Exception {
        if (!ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN))
            throw new IllegalStateException("F32LE_READER_REQUIRES_LITTLE_ENDIAN_HOST");
        if (depth0 == coordinate0 || !GL11.glIsTexture(depth0) || !GL11.glIsTexture(coordinate0))
            throw new IllegalStateException("UNKNOWN_OR_ALIASED_TEXTURE_OBJECT");
        if (depthSampler != 0 && !GL33.glIsSampler(depthSampler))
            throw new IllegalStateException("UNKNOWN_NATIVE_SAMPLER_OBJECT");
        if (Files.exists(directory)) throw new IllegalStateException("USE_FRESH_READBACK_DIRECTORY");
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] pack = new int[PACK_KEYS.length];
        for (int i = 0; i < pack.length; i++) pack[i] = GL11.glGetInteger(PACK_KEYS[i]);
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            for (int key : PACK_KEYS) GL11.glPixelStorei(key, key == GL11.GL_PACK_ALIGNMENT ? 1 : 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, depth0);
            Image depth = image(depth0);
            int mode = parameter(depthSampler, GL14.GL_TEXTURE_COMPARE_MODE);
            int func = parameter(depthSampler, GL14.GL_TEXTURE_COMPARE_FUNC);
            int min = parameter(depthSampler, GL11.GL_TEXTURE_MIN_FILTER);
            int mag = parameter(depthSampler, GL11.GL_TEXTURE_MAG_FILTER);
            if (mode != GL30.GL_COMPARE_REF_TO_TEXTURE || func != GL11.GL_LEQUAL ||
                min != GL11.GL_LINEAR || mag != GL11.GL_LINEAR)
                throw new IllegalStateException("UNQUALIFIED_NATIVE_COMPARE_SUPPORT");
            if (depth.format != GL14Depth.D16 && depth.format != GL14Depth.D24 &&
                depth.format != GL14Depth.D32 && depth.format != GL30.GL_DEPTH_COMPONENT32F)
                throw new IllegalStateException("UNQUALIFIED_DEPTH_FORMAT");
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, coordinate0);
            Image coordinates = image(coordinate0);
            if (coordinates.format != GL30.GL_RGBA32F)
                throw new IllegalStateException("CUE_REQUIRES_ACTUAL_RGBA32F");
            Files.createDirectories(directory);
            write(directory.resolve("depth.f32le"), depth, GL11.GL_DEPTH_COMPONENT, 1);
            write(directory.resolve("coordinates.rgba32f"), coordinates, GL11.GL_RGBA, 4);
            return new Capture(depth, coordinates, depthSampler, mode, func, min, mag,
                java.util.Arrays.stream(pack).boxed().toList(), packBuffer, active, bound);
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            for (int i = 0; i < pack.length; i++) GL11.glPixelStorei(PACK_KEYS[i], pack[i]);
            GL13.glActiveTexture(active);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        }
    }

    private static int parameter(int sampler, int key) {
        return sampler == 0 ? GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, key) : GL33.glGetSamplerParameteri(sampler, key);
    }
    private static Image image(int object) {
        return new Image(object,
            GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH),
            GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT),
            GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT));
    }
    private static void write(Path file, Image image, int externalFormat, int components) throws Exception {
        int bytes = Math.multiplyExact(Math.multiplyExact(image.width, image.height), components * 4);
        if (bytes <= 0) throw new IllegalStateException("INVALID_READBACK_EXTENT");
        ByteBuffer buffer = MemoryUtil.memAlloc(bytes);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, image.object);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, externalFormat, GL11.GL_FLOAT, buffer);
            byte[] raw = new byte[bytes]; buffer.get(raw);
            Files.write(file, raw, StandardOpenOption.CREATE_NEW);
        } finally { MemoryUtil.memFree(buffer); }
    }
    // Core OpenGL constants; no non-4.1 capability is assumed.
    private static final class GL14Depth {
        static final int D16 = 0x81A5, D24 = 0x81A6, D32 = 0x81A7;
    }
}
