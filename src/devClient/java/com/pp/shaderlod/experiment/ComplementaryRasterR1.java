package com.pp.shaderlod.experiment;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Private pinned experiment only. Parent must integrate and test every seam.
 * Reimagined R1 certificate stays separate from the bounded Unbound temporal branch.
 * Unbound adds only the reviewed compact owners and RGBA16F c2 metadata in GLSL.
 * Main MRT/depth sizes remain native. Actual viewport AND scissor contract here.
 */
public final class ComplementaryRasterR1 {
    public static final double SIDE_SCALE = .75;
    private static final String OPTIONS_SHA = "7744d25cca74c4fee09e7ba383c2f2348fbcfd9bde82e9508bc2e2d9e0b74bef";
    private static final String UNBOUND_OPTIONS_SHA = "b64d94998c99bf867e80d350a4a0a8108c58507e0401eb053a70e3aca81a5036";
    private static final String UNBOUND_ORIGINAL_SHA = "98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a";
    // Frozen only after the sibling source release. Pending pins fail native.
    private static final String UNBOUND_ARCHIVE_SHA = "7aa174f5dbb35d995f6b74a59b73a0a87a770597a14904fe0d359cd766f1240b";
    private static final String UNBOUND_RECEIPT_SHA = "4c48b0f30457d9c8c912926199fbb6d62fe737165a5617f9ce1ec3394b8fb0ae";
    private static final String UNBOUND_OPTION = "SHADERLOD_UNBOUND_TEMPORAL_R1";
    private static final String UNBOUND_HELPER = "SHADERLOD_UNBOUND_TEMPORAL_R1_HELPER";
    private static final String UNBOUND_INCLUDE = "SHADERLOD_UNBOUND_TEMPORAL_R1_HELPER_INCLUDE";
    private static boolean unboundGraph, unboundCoreReset;
    private static String certifiedReceiptProperty;
    private static Object pipeline;
    private static RenderTargets targets;
    private static boolean privateGraph, certified, active, previousActive, havePrevious;
    private static boolean geometry, applyingViewport, savedScissor, shadow, handAuxiliary, frameOpen;
    private static Object certifiedPack;
    private static String certifiedPackName, certifiedArchiveProperty, certifiedOptionsProperty;
    private static int sampledCounter = Integer.MIN_VALUE, sampledWidth, sampledHeight;
    private static Object sampledWorld;
    private static boolean sampledActive;
    private static Field coreFrames;
    private static final int[] oldViewport = new int[4], oldScissor = new int[4];
    private static int width, height, previousWidth, previousHeight;
    private static Object previousWorld;
    private static boolean reset;
    private static Map<String, String> expected;

    private ComplementaryRasterR1() {}
    private static String currentPass = "";
    public static void debugGroup(String name) { currentPass = name; }
    public static String passName() { return currentPass; }
    public static int activeUniform() { return active ? 1 : 0; }
    public static boolean ownsPrivateGraph() { return privateGraph; }
    public static boolean isActive() { return active; }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static Map<String, String> readUnboundExpected() throws Exception {
        if (expected != null) return expected;
        byte[] bytes = Files.readAllBytes(Path.of(System.getProperty("shaderlod.unboundTemporalR1.optionsPin")));
        if (!sha(bytes).equals(UNBOUND_OPTIONS_SHA)) throw new IllegalStateException("Unbound temporal exact native398 pin mismatch");
        expected = new Gson().fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8),
                new TypeToken<Map<String, String>>() {}.getType());
        if (expected.size() != 398) throw new IllegalStateException("Unbound temporal exact398 map missing");
        return expected;
    }
    private static boolean unboundReceiptValid(Map<String, String> receipt, String archiveSha) {
        return receipt != null && "shaderlod-unbound-temporal-controller-r1".equals(receipt.get("schema"))
                && UNBOUND_ORIGINAL_SHA.equals(receipt.get("original_archive_sha256"))
                && UNBOUND_OPTIONS_SHA.equals(receipt.get("native_map_sha256"))
                && archiveSha.equals(receipt.get("candidate_archive_sha256"))
                && UNBOUND_OPTION.equals(receipt.get("source_option"))
                && UNBOUND_HELPER.equals(receipt.get("helper_option"))
                && "1".equals(receipt.get("source_option_value"))
                && "1".equals(receipt.get("helper_value"))
                && UNBOUND_INCLUDE.equals(receipt.get("include_option"))
                && "true".equals(receipt.get("include_value"))
                && "RGBA16F".equals(receipt.get("c2_format"))
                && "1920x1200".equals(receipt.get("output_extent"))
                && "0..720719-wrap720720".equals(receipt.get("frame_counter_domain"))
                && "c6-header1731-fresh-seed".equals(receipt.get("native_fallback"))
                && "deferred2:10,composite2:9".equals(receipt.get("compact_graph"))
                && "true".equals(receipt.get("internal_composite5"));
    }
    private static void certifyUnbound() throws Exception {
        if (UNBOUND_ARCHIVE_SHA.length() != 64 || UNBOUND_RECEIPT_SHA.length() != 64) return;
        var pack = Iris.getCurrentPack().orElseThrow();
        var directory = Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
        var source = directory.resolve(Iris.getCurrentPackName()).normalize();
        if (!source.startsWith(directory) || source.equals(directory)) return;
        String archiveProperty = System.getProperty("shaderlod.unboundTemporalR1.archiveSha256", "");
        if (!UNBOUND_ARCHIVE_SHA.equals(archiveProperty) || !sha(Files.readAllBytes(source)).equals(UNBOUND_ARCHIVE_SHA)) return;
        String receiptProperty = System.getProperty("shaderlod.unboundTemporalR1.receiptPin");
        byte[] bytes = Files.readAllBytes(Path.of(receiptProperty));
        if (!sha(bytes).equals(UNBOUND_RECEIPT_SHA)) return;
        Map<String, String> receipt = new Gson().fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8),
                new TypeToken<Map<String, String>>() {}.getType());
        if (!unboundReceiptValid(receipt, UNBOUND_ARCHIVE_SHA)) return;
        certifiedPack = pack; certifiedPackName = Iris.getCurrentPackName();
        certifiedArchiveProperty = archiveProperty;
        certifiedOptionsProperty = System.getProperty("shaderlod.unboundTemporalR1.optionsPin");
        certifiedReceiptProperty = receiptProperty;
        // A recognized private graph owns its extra copies even in native mode0.
        privateGraph = unboundGraph = true;
        readUnboundExpected();
        if (!frameOptionsMatch()) return;
        if (!ModList.get().isLoaded("shaderlod")) return;
        if (!ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString().equals("21.1.253")) return;
        for (String unsupported : new String[]{"distanthorizons", "voxy", "create", "flywheel"})
            if (ModList.get().isLoaded(unsupported)) return;
        certified = true;
    }
    private static Map<String, String> readExpected() throws Exception {
        if (expected != null) return expected;
        byte[] bytes = Files.readAllBytes(Path.of(System.getProperty("shaderlod.rasterR1.optionsPin")));
        if (!sha(bytes).equals(OPTIONS_SHA)) throw new IllegalStateException("R1 exact native options pin mismatch");
        expected = new Gson().fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8),
                new TypeToken<Map<String, String>>() {}.getType());
        if (expected.size() != 398) throw new IllegalStateException("R1 exact398 map missing");
        return expected;
    }
    private static boolean exactIrisClasses() throws Exception {
        String[][] pins = {
            {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class", "95b3eb4425306964328cf771c7cc79b8bfffdf90e15bce813ad1a7bd38f34579"},
            {"net/irisshaders/iris/pipeline/CompositeRenderer.class", "710afa36c1d800b5f83f0ae69cbc52d9707332436c2c9fd32f443490e3eabfbd"},
            {"net/irisshaders/iris/targets/RenderTargets.class", "8a981c44321f7b0a4ef61d86e94fe3a9c0cdd5c651645b4f3ffb0bbd66847b6a"},
            {"net/irisshaders/iris/pathways/HandRenderer.class", "7c2c8ba4bb9aad824cfdc25ab0b3c5f520d319e4a62d06a02e2b031d8d9628b0"},
            {"net/irisshaders/iris/uniforms/ViewportUniforms.class", "3e2dcbb09869ae7b1fefec294975da00f95ea0f67c11c929d99c990b512411a5"},
            {"net/irisshaders/iris/pathways/CenterDepthSampler.class", "6e4f877607588471f48529aa1d4ec315d9f9153b79511b8e4959c69cd68450b3"},
            {"net/irisshaders/iris/uniforms/FrameUpdateNotifier.class", "a196fc5ef7354f603967dc11bc8c57d1900dadd1f84d93a99b40e5a917298c5a"},
            {"net/irisshaders/iris/gl/program/ProgramUniforms.class", "92d557456c18f45abf2ef7895e484bf0ebca648b331ed70fc84567982adb878f"}
        };
        for (var pin : pins) try (var stream = Iris.class.getClassLoader().getResourceAsStream(pin[0])) {
            if (stream == null || !sha(stream.readAllBytes()).equals(pin[1])) return false;
        }
        return true;
    }
    private static void certify(Object current) {
        privateGraph = certified = unboundGraph = false;
        try {
            if (!SharedConstants.getCurrentVersion().getName().equals("1.21.1")
                    || !exactIrisClasses() || Iris.isFallback()) return;
            // Explicit private archive hash supplied by the assembly receipt.
            var pack = Iris.getCurrentPack().orElse(null);
            if (pack == null) return;
            // Never reinterpret the closed Reimagined R1 certificate as Unbound.
            if (pack.getShaderPackOptions().getOptionSet().getStringOptions().containsKey(UNBOUND_OPTION)) {
                certifyUnbound();
                return;
            }
            var directory = Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
            var source = directory.resolve(Iris.getCurrentPackName()).normalize();
            if (!source.startsWith(directory) || source.equals(directory)) return;
            String requestedSha = System.getProperty("shaderlod.rasterR1.archiveSha256", "");
            if (requestedSha.length() != 64 || !sha(Files.readAllBytes(source)).equals(requestedSha)) return;
            certifiedPack = pack;
            certifiedPackName = Iris.getCurrentPackName();
            certifiedArchiveProperty = requestedSha;
            certifiedOptionsProperty = System.getProperty("shaderlod.rasterR1.optionsPin");
            var options = pack.getShaderPackOptions();
            var values = options.getOptionValues();
            var actual = new TreeMap<String, String>();
            options.getOptionSet().getBooleanOptions().keySet().forEach(k -> actual.put(k, Boolean.toString(values.getBooleanValueOrDefault(k))));
            options.getOptionSet().getStringOptions().keySet().forEach(k -> actual.put(k, values.getStringValueOrDefault(k)));
            String option = actual.remove("SHADERLOD_RASTER_R1");
            String helperGuard = actual.remove("SHADERLOD_RASTER_R1_HELPER");
            privateGraph = option != null;
            if (!"1".equals(option) || !"true".equals(helperGuard) || !actual.equals(readExpected())) return;
            if (!ModList.get().isLoaded("shaderlod")) return;
            if (!ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString().equals("21.1.253")) return;
            for (String unsupported : new String[]{"distanthorizons", "voxy", "create", "flywheel"})
                if (ModList.get().isLoaded(unsupported)) return;
            certified = true;
        } catch (Exception | LinkageError unavailable) {
            // Missing/unknown pin or absent optional classes retain native budget.
            certified = false;
        }
    }
    public static void begin(Object current, RenderTargets renderTargets) {
        geometry = shadow = handAuxiliary = false;
        frameOpen = true;
        targets = renderTargets;
        if (pipeline != current) {
            pipeline = current;
            havePrevious = false;
            expected = null; // reread the canonical pin only at a new pipeline epoch
            sampledCounter = Integer.MIN_VALUE;
            certify(current);
        }
        var client = Minecraft.getInstance();
        width = client.getMainRenderTarget().width;
        height = client.getMainRenderTarget().height;
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, oldViewport);
        savedScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, oldScissor);
        // The native notifier has not yet updated its shared core snapshot.
        // Never read live main-mod config or advertise active before that seam.
        active = false;
        reset = false;
    }
    private static boolean frameOptionsMatch() {
        var options = Iris.getCurrentPack().orElseThrow().getShaderPackOptions();
        var values = options.getOptionValues();
        var actual = new TreeMap<String, String>();
        options.getOptionSet().getBooleanOptions().keySet().forEach(k -> actual.put(k, Boolean.toString(values.getBooleanValueOrDefault(k))));
        options.getOptionSet().getStringOptions().keySet().forEach(k -> actual.put(k, values.getStringValueOrDefault(k)));
        String option = actual.remove(UNBOUND_OPTION);
        String helperGuard = actual.remove(UNBOUND_HELPER);
        String includeGuard = actual.remove(UNBOUND_INCLUDE);
        return "1".equals(option) && "1".equals(helperGuard) && "true".equals(includeGuard)
                && actual.equals(expected);
    }
    private static boolean frameCertificate() {
        if (unboundGraph) {
            return certified && ModList.get().isLoaded("shaderlod") && !Iris.isFallback()
                    && Iris.getCurrentPack().orElse(null) == certifiedPack
                    && java.util.Objects.equals(Iris.getCurrentPackName(), certifiedPackName)
                    && java.util.Objects.equals(System.getProperty("shaderlod.unboundTemporalR1.archiveSha256"), certifiedArchiveProperty)
                    && java.util.Objects.equals(System.getProperty("shaderlod.unboundTemporalR1.optionsPin"), certifiedOptionsProperty)
                    && java.util.Objects.equals(System.getProperty("shaderlod.unboundTemporalR1.receiptPin"), certifiedReceiptProperty)
                    && frameOptionsMatch();
        }
        if (!certified || !ModList.get().isLoaded("shaderlod")) return false;
        if (Iris.isFallback() || Iris.getCurrentPack().orElse(null) != certifiedPack
                || !java.util.Objects.equals(Iris.getCurrentPackName(), certifiedPackName)
                || !java.util.Objects.equals(System.getProperty("shaderlod.rasterR1.archiveSha256"), certifiedArchiveProperty)
                || !java.util.Objects.equals(System.getProperty("shaderlod.rasterR1.optionsPin"), certifiedOptionsProperty)) return false;
        // No per-frame archive or pin-file hashing: they cannot redefine an
        // already compiled pipeline. Rebuild/reload is the source epoch boundary.
        var options = Iris.getCurrentPack().orElseThrow().getShaderPackOptions();
        var values = options.getOptionValues();
        var actual = new TreeMap<String, String>();
        options.getOptionSet().getBooleanOptions().keySet().forEach(k -> actual.put(k, Boolean.toString(values.getBooleanValueOrDefault(k))));
        options.getOptionSet().getStringOptions().keySet().forEach(k -> actual.put(k, values.getStringValueOrDefault(k)));
        String option = actual.remove("SHADERLOD_RASTER_R1");
        String helperGuard = actual.remove("SHADERLOD_RASTER_R1_HELPER");
        return "1".equals(option) && "true".equals(helperGuard) && actual.equals(expected);
    }
    /** Observe an already registered main-mod core state; never create one.
     * There is deliberately no static type/linkage reference to a main-mod class.
     * A dev-only missing-mod lane must load this controller without those classes.
     */
    private static boolean enabledRegisteredCore(FrameUpdateNotifier notifier) {
        if (!ModList.get().isLoaded("shaderlod")) return false;
        try {
            boolean injected = false;
            for (var method : CommonUniforms.class.getDeclaredMethods())
                if (method.getName().endsWith("$shaderlod$register")) injected = true;
            if (!injected) return false;
            var coreClass = Class.forName("com.pp.shaderlod.CoreUniformFrame", false, CommonUniforms.class.getClassLoader());
            if (coreClass.getField("BRIDGE_VERSION").getInt(null) != 2) return false;
            if (coreFrames == null) {
                coreFrames = coreClass.getDeclaredField("FRAMES");
                coreFrames.setAccessible(true);
            }
            Object state;
            synchronized (coreClass) { state = ((Map<?, ?>) coreFrames.get(null)).get(notifier); }
            if (state == null) return false;
            Object frame = coreClass.getMethod("current").invoke(state);
            Object core = frame.getClass().getMethod("core").invoke(frame);
            Object settings = core.getClass().getMethod("settings").invoke(core);
            Object preset = core.getClass().getMethod("preset").invoke(core);
            return Boolean.TRUE.equals(settings.getClass().getMethod("enabled").invoke(settings))
                    && preset instanceof Enum<?> choice && choice.ordinal() >= 0 && choice.ordinal() <= 2;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return false;
        }
    }
    private static boolean enabledUnboundCore(FrameUpdateNotifier notifier) {
        // Read the same notifier snapshot even while Off/Quality so a required
        // transition clears once; a permanently unknown bridge must not clear
        // native TAA on every frame. The shader header guards unknown fallback.
        unboundCoreReset = false;
        if (!ModList.get().isLoaded("shaderlod")) return false;
        try {
            boolean injected = false;
            for (var method : CommonUniforms.class.getDeclaredMethods())
                if (method.getName().endsWith("$shaderlod$register")) injected = true;
            if (!injected) return false;
            var coreClass = Class.forName("com.pp.shaderlod.CoreUniformFrame", false, CommonUniforms.class.getClassLoader());
            if (coreClass.getField("BRIDGE_VERSION").getInt(null) != 2) return false;
            if (coreFrames == null) {
                coreFrames = coreClass.getDeclaredField("FRAMES");
                coreFrames.setAccessible(true);
            }
            Object state;
            synchronized (coreClass) { state = ((Map<?, ?>) coreFrames.get(null)).get(notifier); }
            if (state == null) return false;
            Object frame = coreClass.getMethod("current").invoke(state);
            if (!Integer.valueOf(1).equals(frame.getClass().getMethod("temporalVersion").invoke(frame))
                    || !Integer.valueOf(1).equals(frame.getClass().getMethod("scaleHistoryVersion").invoke(frame))) return false;
            unboundCoreReset = Boolean.TRUE.equals(frame.getClass().getMethod("historyReset").invoke(frame))
                    || Boolean.TRUE.equals(frame.getClass().getMethod("scaleTransition").invoke(frame));
            Object core = frame.getClass().getMethod("core").invoke(frame);
            Object settings = core.getClass().getMethod("settings").invoke(core);
            Object preset = core.getClass().getMethod("preset").invoke(core);
            return Boolean.TRUE.equals(settings.getClass().getMethod("enabled").invoke(settings))
                    && preset instanceof Enum<?> choice && choice.ordinal() >= 0 && choice.ordinal() <= 2;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return false; }
    }
    /** Exact native onNewFrame RETURN, before any consuming Program.use. */
    public static void notified(FrameUpdateNotifier notifier) {
        var client = Minecraft.getInstance();
        boolean eligible = false;
        unboundCoreReset = false;
        boolean coreEligible = unboundGraph && enabledUnboundCore(notifier);
        try {
            eligible = frameCertificate() && client.level != null && client.level.dimension().equals(Level.OVERWORLD)
                    && width > 0 && height > 0 && width % 8 == 0 && height % 8 == 0
                    && (!unboundGraph || (width == 1920 && height == 1200))
                    && (unboundGraph ? coreEligible : enabledRegisteredCore(notifier));
        } catch (RuntimeException | LinkageError unavailable) { eligible = false; }
        int counter = SystemTimeUniforms.COUNTER.getAsInt();
        if (sampledCounter == counter && (sampledActive != eligible || sampledWidth != width
                || sampledHeight != height || sampledWorld != client.level))
            throw new IllegalStateException("R1 changed duplicate world transaction before consumers; reload required");
        sampledCounter = counter; sampledActive = eligible;
        sampledWidth = width; sampledHeight = height; sampledWorld = client.level;
        active = eligible;
        reset = privateGraph && (!havePrevious || previousActive != active || previousWidth != width
                || previousHeight != height || previousWorld != client.level
                || (unboundGraph && unboundCoreReset));
    }

    /** Called after native target resize and before any scene producers. */
    public static void resized() {
        if (!reset) return;
        int oldDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int scratch = GL30.glGenFramebuffers();
        RenderSystem.disableScissor();
        try {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scratch);
            for (int index : new int[]{1, 2, 7}) {
                RenderTarget target = targets.get(index);
                if (target == null) continue;
                for (int texture : new int[]{target.getMainTexture(), target.getAltTexture()}) {
                    GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
                    GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                    if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE)
                        throw new IllegalStateException("R1 history reset FBO incomplete");
                    GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[]{0, 0, 0, 0});
                }
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
            GL30.glDeleteFramebuffers(scratch);
            restoreScissor();
        }
    }
    private static void restoreScissor() {
        RenderSystem.enableScissor(oldScissor[0], oldScissor[1], oldScissor[2], oldScissor[3]);
        if (!savedScissor) RenderSystem.disableScissor();
    }
    private static void viewport(int x, int y, int w, int h) {
        applyingViewport = true;
        try { RenderSystem.viewport(x, y, w, h); }
        finally { applyingViewport = false; }
    }
    public static void beginShadow() { shadow = true; leaveGeometry(); }
    public static void endShadow() { shadow = false; geometry(); }
    /** Exact beginHand auxiliary interval. Preserve native 1x1 viewport,
     * native .5/.5 sampling coordinates and pre-hand full-size depth copy.
     * centerDepthSmooth is dead in the admitted WORLD_BLUR0 map; no center-depth
     * coordinate equivalence is claimed for a future live consumer.
     */
    public static void beginHandAuxiliary() { handAuxiliary = true; leaveGeometry(); }
    public static void endHandAuxiliary() { handAuxiliary = false; geometry(); }
    public static void geometry() {
        if (shadow || handAuxiliary || !frameOpen) return;
        geometry = true;
        if (active) {
            viewport(0, 0, (int)(width * SIDE_SCALE), (int)(height * SIDE_SCALE));
            RenderSystem.enableScissor(0, 0, (int)(width * SIDE_SCALE), (int)(height * SIDE_SCALE));
        }
    }
    public static boolean replaceGeometryViewport(int x, int y, int w, int h) {
        if (!geometry || !active || applyingViewport) return false;
        if (x != 0 || y != 0 || w != width || h != height)
            throw new IllegalStateException("R1 unreviewed geometry viewport; stop this candidate");
        viewport(0, 0, (int)(width * SIDE_SCALE), (int)(height * SIDE_SCALE));
        RenderSystem.enableScissor(0, 0, (int)(width * SIDE_SCALE), (int)(height * SIDE_SCALE));
        return true;
    }
    public static void leaveGeometry() { geometry = false; if (privateGraph) restoreScissor(); }
    public static void passViewport(String pass, int x, int y, int w, int h) {
        if (active && (pass.equals("deferred1") || pass.equals("composite") || pass.equals("composite1") || pass.equals("composite3")
                || (unboundGraph && pass.equals("composite5")))) {
            w = Math.max(1, (int)(w * SIDE_SCALE)); h = Math.max(1, (int)(h * SIDE_SCALE));
            viewport(x, y, w, h);
            RenderSystem.enableScissor(x, y, w, h);
        } else {
            viewport(x, y, w, h);
            if (privateGraph) restoreScissor();
        }
    }
    public static boolean auxiliary(String pass) { return pass.equals("deferred2") || pass.equals("composite2"); }
    public static boolean skipMip(String pass, RenderTarget target) {
        if (!privateGraph) return false;
        if (!active && (target == targets.get(9) || target == targets.get(10))) return true;
        return active && ((pass.equals("composite") && target == targets.get(5))
                || (pass.equals("composite4") && target == targets.get(0)));
    }
    public static void finish() {
        leaveGeometry();
        if (privateGraph) viewport(oldViewport[0], oldViewport[1], oldViewport[2], oldViewport[3]);
        previousActive = active; previousWidth = width; previousHeight = height;
        previousWorld = Minecraft.getInstance().level; havePrevious = true; frameOpen = false;
    }
    public static void dispose(Object current) {
        if (pipeline != current) return;
        // Destruction normally occurs after finish, potentially in GUI/reload.
        // Restore only a currently open transaction; never replay stale GL state.
        if (frameOpen) finish();
        pipeline = null; targets = null; certifiedPack = null;
        privateGraph = certified = active = geometry = shadow = handAuxiliary = frameOpen = false;
        havePrevious = false; expected = null; sampledCounter = Integer.MIN_VALUE;
        unboundGraph = false; unboundCoreReset = true; certifiedReceiptProperty = null;
    }
}
