package com.pp.shaderlod.optimization.vrs;

/** One context-owned immutable rate image; every draw restores all modified NV state.
 * This class has no shader admission policy. Only a qualified caller may open a scope. */
public final class NvBloomVrsBackend {
    public static final int RATE_2X2 = 0x9568;
    public interface Driver {
        long context();
        VrsCapabilities capabilities();
        int tileWidth();
        int tileHeight();
        int maxCoarseSamples();
        int createZeroRateImage(int width, int height);
        void deleteImage(int texture);
        void imageBarrier();
        int boundImage();
        int paletteZero();
        boolean enabledZero();
        void bindImage(int texture);
        void paletteZero(int rate);
        void enabledZero(boolean enabled);
    }
    private record State(int image, int palette, boolean enabled) {}
    private final Driver driver;
    private long context;
    private int image, width, height;
    private Scope active;
    private boolean failed;
    private long activated, restored;
    private String last = "not requested";
    public NvBloomVrsBackend(Driver driver) { this.driver = driver; }

    public Scope open(boolean qualified, int framebuffer, int frameWidth, int frameHeight) {
        if (!qualified || framebuffer == 0 || frameWidth <= 0 || frameHeight <= 0) return null;
        if (!driver.capabilities().supported()) { last = "unavailable: " + driver.capabilities().missing(); return null; }
        long current = driver.context();
        if (current == 0) return null;
        if (context != current) {
            // Retain ownership until the original context can release its object.
            if (image != 0 || active != null) { last = "foreign context; native, original resource retained"; return null; }
            context = current; image = width = height = 0; failed = false;
        }
        if (failed || active != null) return null;
        int tw = driver.tileWidth(), th = driver.tileHeight();
        if (tw <= 0 || th <= 0 || driver.maxCoarseSamples() < 4) {
            last = "unavailable NV image footprint/coarse sample limit"; return null;
        }
        int w = Math.toIntExact(((long)frameWidth + tw - 1) / tw);
        int h = Math.toIntExact(((long)frameHeight + th - 1) / th);
        State before = new State(driver.boundImage(), driver.paletteZero(), driver.enabledZero());
        try {
            if (image == 0 || width != w || height != h) {
                if (image != 0) {
                    if (before.image() == image) return null; // Foreign use of our private object: retain it.
                    driver.deleteImage(image); image = 0;
                }
                image = driver.createZeroRateImage(w, h);
                if (image <= 0) throw new IllegalStateException("Rate image allocation failed");
                width = w; height = h;
                driver.imageBarrier();
            }
            active = new Scope(before, current);
            driver.paletteZero(RATE_2X2);
            driver.bindImage(image);
            driver.enabledZero(true);
            if (driver.boundImage() != image || driver.paletteZero() != RATE_2X2 || !driver.enabledZero())
                throw new IllegalStateException("NV activation readback mismatch");
            activated++; last = "actual NV image/palette/viewport0 enable readback verified";
            return active;
        } catch (RuntimeException | LinkageError failure) {
            failed = true;
            if (active == null) active = new Scope(before,current);
            active.close(); // Failed restoration retains its snapshot and stops this frame.
            last = "native fallback after setup failure: " + failure.getClass().getSimpleName();
            return null;
        }
    }
    private void restore(State before) {
        Throwable problem = null;
        try { driver.enabledZero(false); } catch (RuntimeException | LinkageError e) { problem = e; }
        try { driver.bindImage(before.image()); } catch (RuntimeException | LinkageError e) { problem = append(problem,e); }
        try { driver.paletteZero(before.palette()); } catch (RuntimeException | LinkageError e) { problem = append(problem,e); }
        try { driver.enabledZero(before.enabled()); } catch (RuntimeException | LinkageError e) { problem = append(problem,e); }
        try {
            if (driver.boundImage() != before.image() || driver.paletteZero() != before.palette()
                    || driver.enabledZero() != before.enabled())
                throw new IllegalStateException("NV restoration readback mismatch");
        } catch (RuntimeException | LinkageError e) { problem = append(problem,e); }
        if (problem != null) throw new IllegalStateException("Could not restore scoped NV state", problem);
        restored++;
    }
    private static Throwable append(Throwable first, Throwable next) {
        if (first == null) return next;
        first.addSuppressed(next); return first;
    }
    public final class Scope implements AutoCloseable {
        private final State before;
        private final long owner;
        private boolean closed;
        private Scope(State before,long owner) { this.before = before; this.owner = owner; }
        @Override public void close() {
            if (closed) return;
            if (driver.context() != owner) throw new IllegalStateException("Context changed inside VRS draw");
            try {
                restore(before);
                closed = true; active = null;
            } catch (RuntimeException | LinkageError failure) { failed = true; throw failure; }
        }
    }
    public void release() {
        if (active != null) active.close();
        if (image != 0 && driver.context() != context) { last = "release deferred to original context"; return; }
        if (image != 0 && driver.context() == context) {
            if (driver.boundImage() == image) throw new IllegalStateException("Private VRS image unexpectedly bound outside scope");
            driver.deleteImage(image);
        }
        image = width = height = 0; context = 0; failed = false; last = "released/native";
    }
    public java.util.Map<String,Object> status() {
        return java.util.Map.of("activated_draws",activated,"restored_scopes",restored,
                "owned_image",image,"image_width",width,"image_height",height,"failed",failed,"last",last);
    }
}
