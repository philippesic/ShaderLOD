package com.pp.shaderlod.dev;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** Separately timed encoder admission on an existing preserved native screenshot. */
public final class PngCaptureTransportFixture {
    public static void main(String[] args) throws Exception {
        var input = ImageIO.read(Path.of(args[0]).toFile());
        if (input == null || input.getWidth() != 3840 || input.getHeight() != 2160)
            throw new IllegalArgumentException("Actual native 4K RGBA screenshot required");
        int width = input.getWidth(), height = input.getHeight();
        int[] argb = input.getRGB(0, 0, width, height, null, 0, width);
        Path output = Files.createDirectory(Path.of(args[1]));
        for (int index = 0; index < 6; index++) {
            int level = new int[]{0, 1, 1, 0, 0, 1}[index];
            Path png = output.resolve("transport-" + index + "-level-" + level + ".png");
            long start = System.nanoTime();
            long bytes = FastPngWriter.write(png, width, height, (x, y) -> {
                int pixel = argb[y * width + x];
                return (pixel & 0xff00ff00) | ((pixel & 255) << 16) | ((pixel >>> 16) & 255);
            }, level, 64L * 1024 * 1024);
            double encodeSeconds = (System.nanoTime() - start) / 1e9;
            var decoded = ImageIO.read(png.toFile());
            if (bytes != Files.size(png) || !Arrays.equals(argb,
                    decoded.getRGB(0, 0, width, height, null, 0, width)))
                throw new AssertionError("Native RGBA pixels differ after transport");
            System.out.println("{\"level\":" + level + ",\"bytes\":" + bytes
                    + ",\"encode_flush_seconds\":" + encodeSeconds + ",\"rgba_roundtrip\":true}");
        }
    }
}
