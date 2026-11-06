package com.pp.shaderlod.dev;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Small lossless PNG encoder for owned quality captures; no Minecraft, GL or STB state. */
final class FastPngWriter {
    static final String ENCODING = "PNG RGBA8; filter None; non-interlaced; independent Java zlib Deflater";
    static final long MAX_PIXELS = 4096L * 2160;
    private static final int CHUNK_BYTES = 256 * 1024;
    private static final byte[] SIGNATURE = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
    private static final byte[] IHDR = {73, 72, 68, 82};
    private static final byte[] IDAT = {73, 68, 65, 84};
    private static final byte[] IEND = {73, 69, 78, 68};

    private FastPngWriter() {}

    /** Unsigned RGBA bytes represented as the canonical integer 0xAABBGGRR. */
    @FunctionalInterface
    interface AbgrPixels { int get(int x, int y); }

    /**
     * Reads each source pixel once in its existing top-to-bottom row order.
     * Uses one row, one fixed compressed chunk and a fixed file buffer, rather
     * than a second full image or a queue. Caller retains/then closes the source.
     * A failed file is retained, and existing files are never overwritten.
     */
    static long write(Path path, int width, int height, AbgrPixels pixels,
                      int compressionLevel, long maximumBytes) throws IOException {
        if (width < 1 || height < 1 || (long)width * height > MAX_PIXELS) throw new IllegalArgumentException("PNG dimensions exceed pixel bound");
        if (compressionLevel < 0 || compressionLevel > 1) throw new IllegalArgumentException("PNG compression level must be 0 or 1");
        if (maximumBytes < 1) throw new IllegalArgumentException("PNG byte bound must be positive");
        if (pixels == null) throw new IllegalArgumentException("PNG source pixels missing");
        byte[] row = new byte[Math.addExact(Math.multiplyExact(width, 4), 1)];
        byte[] compressed = new byte[CHUNK_BYTES];
        // false selects PNG's required zlib wrapper, including Adler-32.
        Deflater deflater = new Deflater(compressionLevel, false);
        try (var channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             var stream = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel), 64 * 1024))) {
            PngChunks chunks = new PngChunks(stream, maximumBytes);
            chunks.signature();
            byte[] header = new byte[13];
            integer(header, 0, width);
            integer(header, 4, height);
            header[8] = 8; // Bit depth.
            header[9] = 6; // Truecolour with alpha, RGBA in byte order.
            // Compression/filter/interlace method bytes remain zero.
            chunks.write(IHDR, header, header.length);
            for (int y = 0; y < height; y++) {
                row[0] = 0; // Filter None: preserve exact channel bytes.
                for (int x = 0, index = 1; x < width; x++, index += 4) {
                    int abgr = pixels.get(x, y);
                    row[index] = (byte)abgr;
                    row[index + 1] = (byte)(abgr >>> 8);
                    row[index + 2] = (byte)(abgr >>> 16);
                    row[index + 3] = (byte)(abgr >>> 24);
                }
                deflater.setInput(row);
                while (!deflater.needsInput()) {
                    int length = deflater.deflate(compressed);
                    if (length != 0) chunks.write(IDAT, compressed, length);
                    else if (!deflater.needsInput()) throw new IOException("PNG deflater made no input progress");
                }
            }
            deflater.finish();
            while (!deflater.finished()) {
                int length = deflater.deflate(compressed);
                if (length == 0 && !deflater.finished()) throw new IOException("PNG deflater made no finish progress");
                if (length != 0) chunks.write(IDAT, compressed, length);
            }
            chunks.write(IEND, header, 0);
            stream.flush();
            // This runs only on the bounded quality writer, never the render
            // thread. Drain each image's contents rather than accumulate a
            // multi-gigabyte dirty-file backlog across a capture sequence.
            channel.force(false);
            return chunks.bytes;
        } finally {
            deflater.end(); // Includes open/write/source-pixel failures.
        }
    }

    private static void integer(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte)(value >>> 24);
        bytes[offset + 1] = (byte)(value >>> 16);
        bytes[offset + 2] = (byte)(value >>> 8);
        bytes[offset + 3] = (byte)value;
    }

    private static final class PngChunks {
        private final DataOutputStream stream;
        private final long maximumBytes;
        private final CRC32 crc = new CRC32();
        private long bytes;

        private PngChunks(DataOutputStream stream, long maximumBytes) {
            this.stream = stream;
            this.maximumBytes = maximumBytes;
        }
        private void reserve(long count) throws IOException {
            if (count < 0 || count > maximumBytes - bytes) throw new IOException("PNG exceeds configured byte bound");
            bytes += count;
        }
        private void signature() throws IOException {
            reserve(SIGNATURE.length);
            stream.write(SIGNATURE);
        }
        private void write(byte[] type, byte[] data, int length) throws IOException {
            reserve(12L + length);
            stream.writeInt(length);
            stream.write(type);
            stream.write(data, 0, length);
            crc.reset();
            crc.update(type);
            crc.update(data, 0, length);
            stream.writeInt((int)crc.getValue());
        }
    }
}
