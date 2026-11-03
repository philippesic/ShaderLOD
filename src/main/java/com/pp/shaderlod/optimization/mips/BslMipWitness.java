package com.pp.shaderlod.optimization.mips;

import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.packs.SupportedPackCatalog;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.CompositePass;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
import static com.pp.shaderlod.optimization.mips.BslMipWitnessState.*;

/**
 * Bounded, untimed, opt-in observation of two executed BSL frames, the physical c5 history bridge and reset boundary.
 * Arm BEFORE pack construction/reload; resetCounters also requires new observed compilation callbacks.
 * All control calls, callbacks and snapshot() belong to the render thread. No permission supplier is installed.
 * Temporary texture-query bindings are restored; no texture/sampler parameter, draw, copy or mip call is changed.
 */
public final class BslMipWitness {
    private BslMipWitness() {}
    /** Private observer contract only; never grants an optimization permission. */
    public static boolean nativeDiscriminator(String archive,String source,Map<String,String> values,boolean optedIn) {
        return optedIn
            && "a6cb00f3e71f307452e48ee07a85c607189992ca51668bf61b29686b9b246f11".equals(archive)
            && "205a97debef534fee2832956b4084d0b1192379f8ab2f15f520442035f064220".equals(source)
            && values!=null && values.size()==384
            && (com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions().equals(values)
                || com.pp.shaderlod.optimization.bslprefix.BslPrefixProfile.nativeOptions().equals(values));
    }
    private static volatile boolean observing;
    private static boolean exact, stopped;
    private static long epoch;
    private static Thread thread;
    private static Object context, pipeline, pack, level, compositeOwner, finalOwner, activeOwner;
    private static RenderTargets targets;
    private static RenderTarget rt1, rt2;
    private static int width, height, main1, alt1, main2, alt2, frame = -1, phase, copyWidth = -1, copyHeight = -1;
    private static int pipelineIdentity, targetsIdentity;
    private static Object passList;
    private static boolean candidate, c6Candidate;
    private static String status = "OFF", packName = "", archiveHash = "", sourceDigest = "";
    private static Map<String, String> options = Map.of();
    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static final IdentityHashMap<Program, Compiled> COMPILED = new IdentityHashMap<>();
    private static final List<Frame> FRAMES = new ArrayList<>();
    private static final List<Integer> GENERATIONS = new ArrayList<>();
    private static final List<Copy> COPIES = new ArrayList<>();
    private static final List<List<Texture>> NEXT_RESETS = new ArrayList<>();
    private static Meta c5, c6, c7, fin;
    private static Bridge bridge, pendingBridge;
    private static Stage a, b, c, pending;
    private static Copy pendingCopy;
    private record Compiled(Object owner, Program program, int programId, String name, String vertexHash, String fragmentHash,
            Set<Integer> mips, Map<Integer, Integer> samplerIntent) {
        private Compiled { mips = Set.copyOf(mips); samplerIntent = Map.copyOf(samplerIntent); }
    }
    private record Meta(Object owner, Object pass, Compiled compiled, Set<Integer> mips, Set<Integer> alt,
            List<Integer> draws, GlFramebuffer framebuffer) {}
    public record Source(String name, int program, String vertexSha256, String fragmentSha256, Set<Integer> mips) {
        public Source { mips = Set.copyOf(mips); }
    }
    public record Snapshot(boolean observing, boolean exactApi, boolean complete, long epoch, String status,
            String loadedPack, String archiveSha256, String shaderSourceDigest, Map<String, String> options,
            int pipelineIdentity, int targetsIdentity, List<Source> sources, List<Frame> frames,
            List<List<Texture>> nextFrameResets, List<Integer> pendingGenerationTextures, String evidenceLimit) {
        public Snapshot {
            options = Map.copyOf(options); sources = List.copyOf(sources); frames = List.copyOf(frames);
            nextFrameResets = nextFrameResets.stream().map(List::copyOf).toList();
            pendingGenerationTextures = List.copyOf(pendingGenerationTextures);
        }
    }
    public static boolean observing() { return observing; }
    public static void setObserving(boolean enabled) {
        if (observing == enabled) return;
        if (enabled) { thread = Thread.currentThread(); observing = true; resetCounters(); }
        else { controlThread(); observing = false; epoch++; stopped = true; status += "; observation disabled"; releaseLive(); }
    }
    public static void resetCounters() {
        if (observing) controlThread();
        epoch++; stopped = false; exact = false; status = observing ? "ARMED; observed reload/compilation required" : "OFF";
        FRAMES.clear(); NEXT_RESETS.clear(); GENERATIONS.clear(); COPIES.clear(); COMPILED.clear(); FIELDS.clear();
        a = b = c = pending = null; bridge = pendingBridge = null; pendingCopy = null; c5 = c6 = c7 = fin = null;
        frame = -1; phase = 0; copyWidth = copyHeight = -1; pipelineIdentity = targetsIdentity = 0;
        options = Map.of(); packName = archiveHash = sourceDigest = ""; releaseLive();
        if (!observing) return;
        try {
            exact = BslMipWitnessPins.exactApi(); require(exact, "raw Iris1.8.12 class pin mismatch");
            // Cache exact fields once. No reflective field discovery occurs in a frame callback.
            fields("pipeline.CompositeRenderer", "pipeline", "compositePass", "passes", "renderTargets");
            fields("pipeline.FinalPassRenderer", "pipeline", "finalPass", "renderTargets", "swapPasses", "colorHolder");
            fields("pipeline.CompositeRenderer$Pass", "name", "program", "mipmappedBuffers", "stageReadsFromAlt", "drawBuffers", "framebuffer", "computes");
            fields("pipeline.FinalPassRenderer$Pass", "program", "mipmappedBuffers", "stageReadsFromAlt", "computes");
            fields("pipeline.FinalPassRenderer$SwapPass", "target", "targetTexture", "from", "width", "height");
            fields("gl.program.Program", "samplers"); fields("gl.program.ProgramSamplers", "samplerBindings");
            fields("gl.sampler.SamplerBinding", "textureUnit", "sampler");
        } catch (Exception | LinkageError failure) { fail(failure); }
    }
    public static Snapshot snapshot() {
        var sources = COMPILED.values().stream().map(v -> new Source(v.name, v.programId, v.vertexHash, v.fragmentHash, v.mips))
            .sorted(Comparator.comparing(Source::name)).toList();
        return new Snapshot(observing, exact, FRAMES.size() == 2 && NEXT_RESETS.size() == 2 && status.startsWith("OBSERVED"), epoch,
            status, packName, archiveHash, sourceDigest, options, pipelineIdentity, targetsIdentity,
            sources, FRAMES, NEXT_RESETS, GENERATIONS,
            "Observation only. Raw class pins, observed c5 source/program/mip/draw metadata, returned native generation/copy calls and physical sampler/FBO joins through c5; no GLSL GBA pass-through certification, GPU completion, pixel/history equality, source admission, lifecycle/DH/PBR qualification or timing. Root must certify current source semantics and join full-map/epoch and independent output parity before admission.");
    }
    public static void compiled(Object owner, ProgramSource source, Program program) {
        if (!observing || stopped) return;
        if (source == null || program == null || !Set.of("composite5", "composite6", "composite7", "final").contains(source.getName())) return;
        try {
            checkThread(); require(COMPILED.size() < 8, "compile/reload epoch changed; reset and observe fresh reload");
            var intent = new HashMap<Integer, Integer>();
            for (Object binding : (List<?>)get(get(program, "samplers"), "samplerBindings")) {
                int unit = integer(binding, "textureUnit"), sampler = integer(binding, "sampler");
                require(intent.putIfAbsent(unit, sampler) == null, "duplicate native sampler unit");
            }
            COMPILED.put(program, new Compiled(owner, program, program.getProgramId(), source.getName(), hash(source.getVertexSource().orElseThrow()),
                hash(source.getFragmentSource().orElseThrow()), Set.copyOf(source.getDirectives().getMipmappedBuffers()), intent));
        } catch (Exception | LinkageError failure) { fail(failure); }
    }
    public static void enter(Object owner) {
        if (!observing || stopped) return;
        try {
            checkThread();
            boolean composite = owner.getClass().getName().equals("net.irisshaders.iris.pipeline.CompositeRenderer");
            if (composite && get(owner, "compositePass") != CompositePass.COMPOSITE) return;
            if (composite) {
                if (targets == null) initialize(owner);
                require(owner == compositeOwner && activeOwner == null, "composite renderer/order epoch changed");
                validateEpoch(); validateMeta(c5); validateMeta(c6); validateMeta(c7); if (fin != null) validateMeta(fin);
                require(pending == null && pendingBridge == null && phase == 0, "aborted/incomplete prior frame");
                int now = SystemTimeUniforms.COUNTER.getAsInt();
                require(frame == -1 || now == frame + 1, "frame skipped/repeated or counter epoch changed");
                List<Texture> reset = resetTextures(); require(BslMipWitnessState.reset(reset, main1, alt1), "next-frame RT1 reset changed");
                if (!FRAMES.isEmpty()) NEXT_RESETS.add(reset);
                if (FRAMES.size() == 2) { stopped = true; status = "OBSERVED two frames plus next-frame reset; source/parity/admission external"; return; }
                frame = now; bridge = null; a = b = c = null; GENERATIONS.clear(); COPIES.clear();
                activeOwner = owner;
            } else {
                require(targets != null && phase == 3 && activeOwner == null && get(owner, "pipeline") == pipeline
                    && get(owner, "renderTargets") == targets, "final renderer/order changed");
                validateEpoch();
                if (finalOwner == null) {
                    finalOwner = owner; Object pass = get(owner, "finalPass"); require(pass != null, "native final pass absent");
                    fin = metadata(owner, pass, "final", (GlFramebuffer)get(owner, "colorHolder"));
                    require(fin.mips.isEmpty(), "native final mip set changed");
                }
                require(finalOwner == owner, "final renderer epoch changed"); activeOwner = owner;
            }
        } catch (Exception | LinkageError failure) { fail(failure); }
    }
    private static void initialize(Object owner) throws Exception {
        pipeline = get(owner, "pipeline"); targets = (RenderTargets)get(owner, "renderTargets");
        pipelineIdentity = System.identityHashCode(pipeline); targetsIdentity = System.identityHashCode(targets);
        passList = get(owner, "passes");
        pack = Iris.getCurrentPack().orElseThrow(); level = Minecraft.getInstance().level;
        context = GL.getCapabilities(); require(level != null, "no loaded world"); compositeOwner = owner;
        packName = Iris.getCurrentPackName(); options = Map.copyOf(DeadMipAdmission.effective(Iris.getCurrentPack().orElseThrow()));
        require("true".equals(options.get("TAA")) && "true".equals(options.get("LIGHT_SHAFT"))
            && "false".equals(options.get("RETRO_FILTER")), "native BSL TAA/light-shaft graph inactive");
        String option = options.get("SHADERLOD_BSL_DEAD_MIP_CHAIN");
        require(option == null || option.equals("0") || option.equals("1"), "unknown mip compile option"); candidate = "1".equals(option); c6Candidate = candidate && DeadMipAdmission.c6Capability((ShaderPack)pack);
        Path dir = Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
        String selected = Iris.getIrisConfig().getShaderPackName().orElseThrow();
        require(selected.equals(packName), "selected/loaded pack mismatch"); Path path = dir.resolve(selected).normalize();
        require(path.startsWith(dir) && !path.equals(dir) && !Files.isSymbolicLink(path), "selected source path refused");
        var fingerprint = PackSourceReader.read(path); archiveHash = fingerprint.sourceSha256();
        sourceDigest = SupportedPackCatalog.shaderSourceDigest(fingerprint.memberSha256());
        if(nativeDiscriminator(archiveHash,sourceDigest,options,Boolean.getBoolean("shaderlod.dev.bslNativeMipWitnessRuntime"))) {
            candidate=true;c6Candidate=true;
        }
        rt1 = targets.get(1); rt2 = targets.get(2); require(rt1 != null && rt2 != null, "native color/history target absent");
        width = rt1.getWidth(); height = rt1.getHeight(); main1 = rt1.getMainTexture(); alt1 = rt1.getAltTexture();
        main2 = rt2.getMainTexture(); alt2 = rt2.getAltTexture();
        require(rt2.getWidth() == width && rt2.getHeight() == height, "history/color extent differs");
        var set = Iris.getCurrentPack().orElseThrow().getProgramSet(new net.irisshaders.iris.shaderpack.materialmap.NamespacedId("minecraft", "overworld"));
        var settings = set.getPackDirectives().getRenderTargetDirectives().getRenderTargetSettings();
        require(settings.get(1) != null && settings.get(2) != null && settings.get(1).shouldClear() && !settings.get(2).shouldClear()
            && rt1.getInternalFormat().getGlFormat() == GL11.GL_RGB8 && rt2.getInternalFormat().getGlFormat() == GL11.GL_RGBA16,
            "native RT1 clear / RT2 history / RGB8-RGBA16 descriptor changed");
        for (Object pass : (List<?>)passList) {
            String name = (String)get(pass, "name");
            if (name.equals("composite5")) { require(c5 == null && c6 == null && c7 == null, "duplicate/reordered c5"); c5 = metadata(owner, pass, name, (GlFramebuffer)get(pass, "framebuffer")); }
            else if (name.equals("composite6")) { require(c5 != null && c6 == null && c7 == null, "duplicate/reordered c6"); c6 = metadata(owner, pass, name, (GlFramebuffer)get(pass, "framebuffer")); }
            else if (name.equals("composite7")) { require(c6 != null && c7 == null, "duplicate/reordered c7"); c7 = metadata(owner, pass, name, (GlFramebuffer)get(pass, "framebuffer")); }
            else {
                require(c5 == null && c6 == null && c7 == null, "additional pass between c5/c6/c7 or after c7");
                require(!((Set<?>)get(pass, "mipmappedBuffers")).contains(1), "additional RT1 mip producer");
            }
        }
        require(c5 != null && c6 != null && c7 != null && c5.draws.equals(List.of(1, 2)) && c5.mips.isEmpty()
            && c6.draws.equals(List.of(1)) && c7.draws.equals(List.of(1, 2))
            && c6.mips.equals(c6Candidate ? Set.of() : Set.of(1)) && c7.mips.equals(candidate ? Set.of() : Set.of(1)), "native c5/c6/c7 roles or mip sets changed");
        validateEpoch(); status = "OBSERVING untimed physical BSL c5 bridge/c6/c7/final/history";
    }
    public static void beforeQuad(Object owner) {
        if (!observing || stopped || activeOwner != owner) return;
        try {
            validateEpoch(); int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            Meta meta = owner == compositeOwner ? program == c5.compiled.programId ? c5 : program == c6.compiled.programId ? c6
                : program == c7.compiled.programId ? c7 : null : fin;
            if (meta == null) return;
            require(program == meta.compiled.programId && pending == null && pendingBridge == null, "draw program or nesting changed");
            require(phase == (meta == c5 ? 0 : meta == c6 ? 1 : meta == c7 ? 2 : 3), "executed c5/c6/c7/final order changed");
            validateMeta(meta);
            if (meta == c5) {
                // c5 need not have an active colortex1 uniform. Observe only its actual history sampler and MRTs.
                Sample history = sample(meta, "colortex2", rt2);
                List<Attachment> outputs = outputs(2);
                require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == meta.framebuffer.getId(), "actual bridge framebuffer differs from pass");
                pendingBridge = new Bridge(meta.compiled.name, program, meta.framebuffer.getId(), meta.mips, meta.alt,
                    meta.draws, history, outputs, GENERATIONS.size());
                return;
            }
            Sample color = sample(meta, "colortex1", rt1);
            Sample history = meta == c7 ? sample(meta, "colortex2", rt2) : null;
            List<Attachment> outputs = outputs(meta == c7 ? 2 : 1);
            require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == meta.framebuffer.getId(), "actual draw framebuffer differs from pass");
            pending = new Stage(meta.compiled.name, program, meta.framebuffer.getId(), meta.mips, meta.alt, color, history, outputs, GENERATIONS.size());
        } catch (Exception | LinkageError failure) { fail(failure); }
    }
    public static void afterQuad(Object owner) {
        if (!observing || stopped || activeOwner != owner || (pending == null && pendingBridge == null)) return;
        try {
            validateEpoch();
            if (pendingBridge != null) {
                require(phase == 0 && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == pendingBridge.program()
                    && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == pendingBridge.framebuffer()
                    && outputs(2).equals(pendingBridge.outputs()), "bridge draw changed program/output identity");
                bridge = pendingBridge; pendingBridge = null; phase++;
                return;
            }
            require(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == pending.program()
                && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == pending.framebuffer()
                && outputs(pending.outputs().size()).equals(pending.outputs()), "draw changed program/output identity");
            if (phase == 1) a = pending; else if (phase == 2) b = pending; else c = pending;
            pending = null; phase++;
        } catch (Exception | LinkageError failure) { fail(failure); }
    }
    /** Immediately AFTER the exact native generateMipmaps(II)V invocation, before its filter setter. */
    public static void mipReturned(RenderTarget target, boolean readAlt) {
        if (!observing || stopped || activeOwner == null || target != rt1) return;
        try {
            validateEpoch(); require(activeOwner == compositeOwner && (phase == 1 || phase == 2), "unexpected RT1 generation owner/order");
            require(GENERATIONS.size() < 3, "extra RT1 generation"); GENERATIONS.add(readAlt ? target.getAltTexture() : target.getMainTexture());
        } catch (Exception | LinkageError failure) { fail(failure); }
    }
    /** Observe native history swap copies without replacing the native invocation. */
    public static void beforeSwapCopy(Object owner) {
        if (!observing || stopped || activeOwner != owner || owner != finalOwner) return;
        try {
            validateEpoch(); require(phase == 4 && pendingCopy == null, "history copy order changed");
            int destination = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            require(destination != main1 && destination != alt1, "native clear-listed RT1 unexpectedly copied at final swap");
            if (destination != main2 && destination != alt2) return;
            int source = attachment(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, 0).texture();
            require(GL11.glGetInteger(GL11.GL_READ_BUFFER) == GL30.GL_COLOR_ATTACHMENT0, "history copy read-buffer override");
            boolean planned = false;
            for (Object swap : (List<?>)get(owner, "swapPasses")) if (integer(swap, "target") == 2) {
                require(!planned && integer(swap, "targetTexture") == destination
                    && ((GlFramebuffer)get(swap, "from")).getId() == GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
                    && integer(swap, "width") == width && integer(swap, "height") == height, "history copy metadata changed"); planned = true;
            }
            require(planned, "unplanned physical history copy"); pendingCopy = new Copy(source, destination, width, height);
        } catch (Exception | LinkageError failure) { fail(failure); }
    }
    /** Unchanged primitive arguments, captured at the exact native copy call; never wraps an operation. */
    public static void swapWidth(Object owner, int value) {
        if (observing && !stopped && owner == finalOwner && owner == activeOwner) copyWidth = value;
    }
    public static void swapHeight(Object owner, int value) {
        if (observing && !stopped && owner == finalOwner && owner == activeOwner) copyHeight = value;
    }
    public static void afterSwapCopy(Object owner) {
        if (!observing || stopped || owner != activeOwner || pendingCopy == null) return;
        try {
            validateEpoch(); require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == pendingCopy.destination()
                && attachment(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, 0).texture() == pendingCopy.source(), "history copy binding changed");
            require(copyWidth == pendingCopy.width() && copyHeight == pendingCopy.height(), "executed history copy extent differs from metadata or argument hooks missing");
            COPIES.add(pendingCopy); pendingCopy = null; copyWidth = copyHeight = -1; require(COPIES.size() <= 1, "extra history copy");
        } catch (Exception | LinkageError failure) { fail(failure); }
    }
    public static void leave(Object owner) {
        if (!observing || stopped || activeOwner != owner) return;
        try {
            validateEpoch(); require(pending == null && pendingBridge == null && pendingCopy == null, "incomplete draw/copy");
            if (owner == compositeOwner) require(phase == 3, "c5/c6/c7 missing at composite return");
            else {
                require(phase == 4, "final draw missing");
                var completed = new Frame(epoch, frame, candidate, main1, alt1, main2, alt2, bridge, a, b, c, GENERATIONS, COPIES, resetTextures(), c6Candidate);
                var failures = BslMipWitnessState.failures(completed); require(failures.isEmpty(), String.join("; ", failures));
                if (!FRAMES.isEmpty()) require(historyJoined(FRAMES.getLast(), completed), "prior c7 output / next c5 history input / same-frame c7 history input not joined in observation epoch");
                FRAMES.add(completed); phase = 0;
            }
            activeOwner = null;
        } catch (Exception | LinkageError failure) { fail(failure); }
    }
    public static void lifecycle(Object owner) {
        if (!observing) return;
        // Includes same-size resize callbacks, destruction and reload of an already captured renderer.
        if (owner == compositeOwner || owner == finalOwner || COMPILED.values().stream().anyMatch(v -> v.owner == owner)) {
            epoch++; fail(new IllegalStateException("renderer resize/destroy/reload boundary; reset then observe new compilation"));
        }
    }
    private static Meta metadata(Object owner, Object pass, String name, GlFramebuffer fbo) throws Exception {
        Program program = (Program)get(pass, "program"); Compiled compiled = COMPILED.get(program);
        require(compiled != null && compiled.owner == owner && compiled.name.equals(name), "missing observed native source-to-program compilation; arm before reload");
        for (Object compute : (Object[])get(pass, "computes")) require(compute == null, "compute companion changed");
        Set<Integer> mips = integers(get(pass, "mipmappedBuffers")), alt = integers(get(pass, "stageReadsFromAlt"));
        require(mips.equals(compiled.mips), "compiled/pass mip metadata mismatch");
        List<Integer> draws = name.equals("final") ? List.of() : Arrays.stream((int[])get(pass, "drawBuffers")).boxed().toList();
        return new Meta(owner, pass, compiled, mips, alt, draws, fbo);
    }
    private static void validateMeta(Meta m) throws Exception {
        require(get(m.pass, "program") == m.compiled.program && m.mips.equals(get(m.pass, "mipmappedBuffers"))
            && m.alt.equals(get(m.pass, "stageReadsFromAlt")), "pass metadata epoch changed");
        for (Object compute : (Object[])get(m.pass, "computes")) require(compute == null, "compute companion inserted");
        if (!m.compiled.name.equals("final")) require(m.draws.equals(Arrays.stream((int[])get(m.pass, "drawBuffers")).boxed().toList())
            && get(m.pass, "framebuffer") == m.framebuffer, "draw metadata changed");
    }
    private static Sample sample(Meta meta, String name, RenderTarget expected) throws Exception {
        int program = meta.compiled.programId, location = GL20.glGetUniformLocation(program, name);
        require(location >= 0, "required active sampler absent: " + name);
        int unit = GL20.glGetUniformi(program, location), active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        require(unit >= 0 && unit < GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS), "sampler unit out of bounds");
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            int id = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), sampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            int declared = meta.compiled.samplerIntent.getOrDefault(unit, -1);
            require(id == expected.getMainTexture() || id == expected.getAltTexture(), "sampled physical color/history storage not native");
            Texture storage = texture(id, expected);
            int[] swizzle = {GL11.GL_RED, GL11.GL_GREEN, GL11.GL_BLUE, GL11.GL_ALPHA};
            for (int i = 0; i < 4; i++) require(param(GL33.GL_TEXTURE_SWIZZLE_R + i) == swizzle[i], "texture swizzle override");
            int min = sampler == 0 ? storage.min() : GL33.glGetSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER);
            boolean nativeSampler = sampler == 0 && declared == 0 && storage.nativeSampling();
            // A nonzero sampler is recorded with its effective min and rejected even if it happens to agree.
            return new Sample(name, unit, id, sampler, declared, min, nativeSampler, storage);
        } finally { GL13.glActiveTexture(active); }
    }
    private static Texture texture(int id, RenderTarget expected) {
        require(GL11.glIsTexture(id), "texture destroyed"); int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == id, "texture is not native 2D storage");
            int w = level(0, GL11.GL_TEXTURE_WIDTH), h = level(0, GL11.GL_TEXTURE_HEIGHT), format = level(0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            require(w == expected.getWidth() && h == expected.getHeight() && format == expected.getInternalFormat().getGlFormat(), "driver texture storage epoch changed");
            var levels = new ArrayList<Level>(); int extent = Math.max(w, h);
            for (int i = 0; ; i++) {
                require(i < 32, "mip extent bound"); levels.add(new Level(i, level(i, GL11.GL_TEXTURE_WIDTH), level(i, GL11.GL_TEXTURE_HEIGHT), level(i, GL11.GL_TEXTURE_INTERNAL_FORMAT)));
                if (extent <= 1) break; extent /= 2;
            }
            float aniso = GL.getCapabilities().GL_EXT_texture_filter_anisotropic || GL.getCapabilities().GL_ARB_texture_filter_anisotropic
                ? GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, 0x84FE) : 1f;
            return new Texture(id, format, w, h, param(GL11.GL_TEXTURE_MIN_FILTER), param(GL11.GL_TEXTURE_MAG_FILTER), param(GL12.GL_TEXTURE_BASE_LEVEL),
                param(GL12.GL_TEXTURE_MAX_LEVEL), floatParam(GL12.GL_TEXTURE_MIN_LOD), floatParam(GL12.GL_TEXTURE_MAX_LOD), floatParam(GL14.GL_TEXTURE_LOD_BIAS),
                param(GL11.GL_TEXTURE_WRAP_S), param(GL11.GL_TEXTURE_WRAP_T), param(GL14.GL_TEXTURE_COMPARE_MODE), aniso, levels);
        } finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding); }
    }
    private static List<Texture> resetTextures() { return List.of(texture(main1, rt1), texture(alt1, rt1)); }
    private static List<Attachment> outputs(int count) {
        require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) > 0, "default draw framebuffer unsupported");
        var out = new ArrayList<Attachment>();
        for (int i = 0; i < count; i++) {
            int attachment = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i);
            require(attachment == GL30.GL_COLOR_ATTACHMENT0 + i, "draw-buffer routing changed"); out.add(attachment(GL30.GL_DRAW_FRAMEBUFFER, attachment, i));
        }
        require(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + count) == GL11.GL_NONE, "additional MRT output");
        return List.copyOf(out);
    }
    private static Attachment attachment(int target, int attachment, int index) {
        int type = GL30.glGetFramebufferAttachmentParameteri(target, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        require(type == GL11.GL_TEXTURE, "output/read attachment is not a texture");
        int id = GL30.glGetFramebufferAttachmentParameteri(target, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        int mip = GL30.glGetFramebufferAttachmentParameteri(target, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
        require(id > 0 && mip == 0, "non-base framebuffer attachment");
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == id, "attachment is not native 2D storage");
            return new Attachment(index, attachment, type, id, mip, level(0, GL11.GL_TEXTURE_WIDTH),
                level(0, GL11.GL_TEXTURE_HEIGHT), level(0, GL11.GL_TEXTURE_INTERNAL_FORMAT));
        } finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding); }
    }
    private static void validateEpoch() throws Exception {
        checkThread(); var client = Minecraft.getInstance();
        require(GL.getCapabilities() == context && client.level == level && client.level != null
            && client.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD) && !Iris.isFallback()
            && Iris.getCurrentPack().orElse(null) == pack && Iris.getPipelineManager().getPipelineNullable() == pipeline
            && packName.equals(Iris.getCurrentPackName()) && options.equals(DeadMipAdmission.effective(Iris.getCurrentPack().orElseThrow())), "world/pack/options/context epoch changed");
        require(targets.get(1) == rt1 && targets.get(2) == rt2 && rt1.getMainTexture() == main1 && rt1.getAltTexture() == alt1
            && rt2.getMainTexture() == main2 && rt2.getAltTexture() == alt2 && rt1.getWidth() == width && rt1.getHeight() == height
            && rt2.getWidth() == width && rt2.getHeight() == height, "physical color/history storage epoch changed");
        require(get(compositeOwner, "passes") == passList && get(compositeOwner, "pipeline") == pipeline
            && get(compositeOwner, "renderTargets") == targets
            && (finalOwner == null || (get(finalOwner, "finalPass") == fin.pass && get(finalOwner, "renderTargets") == targets)), "renderer metadata epoch changed");
        if (frame != -1 && activeOwner != null) require(SystemTimeUniforms.COUNTER.getAsInt() == frame, "frame changed inside native render");
    }
    private static int param(int key) { return GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, key); }
    private static float floatParam(int key) { return GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, key); }
    private static int level(int mip, int key) { return GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, mip, key); }
    @SuppressWarnings("unchecked") private static Set<Integer> integers(Object value) { return Set.copyOf((Set<Integer>)value); }
    private static void fields(String type, String... names) throws Exception {
        Class<?> clazz = Class.forName("net.irisshaders.iris." + type, false, BslMipWitness.class.getClassLoader());
        for (String name : names) { Field f = clazz.getDeclaredField(name); f.setAccessible(true); FIELDS.put(clazz.getName() + "/" + name, f); }
    }
    private static Object get(Object owner, String name) throws Exception {
        Field field = FIELDS.get(owner.getClass().getName() + "/" + name); require(field != null, "uncached pinned field: " + name); return field.get(owner);
    }
    private static int integer(Object owner, String name) throws Exception { return FIELDS.get(owner.getClass().getName() + "/" + name).getInt(owner); }
    private static String hash(String text) throws Exception { require(text.length() <= 1048576, "source bound"); return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
    private static void checkThread() { require(Thread.currentThread() == thread, "observer render thread changed"); }
    private static void controlThread() { if (thread != null) checkThread(); }
    private static void require(boolean ok, String reason) { if (!ok) throw new IllegalStateException(reason); }
    private static void fail(Throwable failure) { stopped = true; activeOwner = null; pending = null; pendingBridge = null; pendingCopy = null; status = "REFUSED: " + failure.getClass().getSimpleName() + ": " + failure.getMessage(); }
    private static void releaseLive() { context = pipeline = pack = level = compositeOwner = finalOwner = activeOwner = null; targets = null; rt1 = rt2 = null; }
}
