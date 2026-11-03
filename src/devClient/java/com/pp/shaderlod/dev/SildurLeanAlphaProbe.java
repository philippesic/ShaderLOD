package com.pp.shaderlod.dev;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.optimization.sildurearlyalpha.SildurEarlyAlphaContract;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.CustomTextureManager;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.RenderTargets;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import org.lwjgl.opengl.*;

/** Private original-derived alpha permission. Source approval is explicit and untimed.
 * Every setup rechecks actual owner, native alpha input and effective samplers.
 * Permission ends at reset, before another setup, or on explicit disarm. */
public final class SildurLeanAlphaProbe {
    private SildurLeanAlphaProbe() {}
    private record Certificate(IrisRenderingPipeline pipeline, Object pack, Object context, Object shader,
        Object world, long generation, int program, String selected, List<String> resources,
        RenderTargets targets, ShadowRenderTargets shadows, CustomTextureManager textures,
        Map<String,Integer> locations) {}
    private static Certificate certificate;
    private static long setups, grants, refusals, drawCalls;
    private static Object setupShader;
    private static String last = "disarmed";
    private static final String PERMISSION = "shaderlodN04AlphaPermission", REFERENCE = "shaderlodN04AlphaReference";

    public static boolean enabled() { return Boolean.getBoolean("shaderlod.dev.sildurLeanAlpha"); }
    private static Object field(Object owner, String name) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static boolean privateContext() {
        var core = OptimizationRuntimeContext.core();
        if (core == null || core.settings().enabled() || !Iris.getShaderPackOptionQueue().isEmpty()
            || Iris.isFallback() || MakeUpQuadLifecycle.suspended()
            || !MakeUpQuadRuntime.loadedStackMatches()
            || net.neoforged.fml.ModList.get().isLoaded("distanthorizons")
            || net.neoforged.fml.ModList.get().isLoaded("voxy")) return false;
        for (var id : OptimizationId.values()) if (OptimizationConfig.requested(id)) return false;
        var client = net.minecraft.client.Minecraft.getInstance();
        return client.level != null && client.level.dimension() == net.minecraft.world.level.Level.OVERWORLD
            && com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()
            && MakeUpQuadRuntime.resourcesMatch(client.getResourcePackRepository().getSelectedIds());
    }

    /** Controller must first approve the complete native/candidate attached-source delta.
     * Hashes bind that approval to this exact current linked pair, never just a program number. */
    static void arm(String selected, String archiveSha, String vertexSha, String fragmentSha) throws Exception {
        disarm();
        if (!enabled() || System.getProperty("shaderlod.dev.benchmarkControlProbe", "").isBlank()
            || !com.pp.shaderlod.optimization.mips.BslMipWitnessPins.exactApi()
            || !privateContext() || !DiagnosticArchiveIdentity.allows(selected, archiveSha)
            || !Objects.equals(selected, Iris.getIrisConfig().getShaderPackName().orElse(null)))
            throw new IllegalStateException("Owned private native Sildur context required");
        var path = Iris.getShaderpacksDirectory().resolve(selected);
        if (Files.isSymbolicLink(path) || !sha(Files.readAllBytes(path)).equals(archiveSha))
            throw new IllegalStateException("Actual private archive differs");
        if (!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline))
            throw new IllegalStateException("Actual Iris pipeline required");
        var p = pipeline.getSodiumPrograms().getProgram(DefaultTerrainRenderPasses.CUTOUT);
        if (p == null) throw new IllegalStateException("Actual CUTOUT program required");
        int program = p.handle();
        if (GL20.glGetProgrami(program, GL20.GL_ATTACHED_SHADERS) != 2)
            throw new IllegalStateException("Exactly two attached shaders required");
        int[] count = {0}, shaders = new int[2];
        GL20.glGetAttachedShaders(program, count, shaders);
        if (count[0] != 2 || GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != 1)
            throw new IllegalStateException("Actual linked attached pair required");
        boolean vertex = false, fragment = false;
        for (int shader : shaders) {
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != 1)
                throw new IllegalStateException("Actual shader compile failed");
            String digest = sha(GL20.glGetShaderSource(shader).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            int type = GL20.glGetShaderi(shader, GL20.GL_SHADER_TYPE);
            if (type == GL20.GL_VERTEX_SHADER && digest.equals(vertexSha)) vertex = true;
            else if (type == GL20.GL_FRAGMENT_SHADER && digest.equals(fragmentSha)) fragment = true;
            else throw new IllegalStateException("Approved actual source pair differs");
        }
        if (!vertex || !fragment) throw new IllegalStateException("Both source stages required");
        var locations = new HashMap<String,Integer>();
        for (String name : List.of(PERMISSION, REFERENCE, "iris_currentAlphaTest", "shadowtex0")) {
            int location = GL20.glGetUniformLocation(program, name);
            if (location < 0) throw new IllegalStateException("Active native/private uniform absent: " + name);
            locations.put(name, location);
        }
        var client = net.minecraft.client.Minecraft.getInstance();
        certificate = new Certificate(pipeline, Iris.getCurrentPack().orElseThrow(), GL.getCapabilities(), p.getInterface(),
            client.level, MakeUpQuadLifecycle.generation(), program, selected,
            List.copyOf(client.getResourcePackRepository().getSelectedIds()),
            (RenderTargets)field(pipeline,"renderTargets"), (ShadowRenderTargets)field(pipeline,"shadowRenderTargets"),
            (CustomTextureManager)field(pipeline,"customTextureManager"), Map.copyOf(locations));
        setups = grants = refusals = 0; last = "armed; permission remains zero until verified setup";
    }

    /** Only the currently bound program is touched: never reuse a stale numeric handle. */
    public static void revoke() {
        if (!enabled() && certificate == null) return;
        int p = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (p > 0) {
            int location = GL20.glGetUniformLocation(p, PERMISSION);
            if (location >= 0) GL20.glUniform1i(location, 0);
        }
    }
    static void disarm() { revoke(); certificate = null; setupShader=null; last = "disarmed"; }

    public static void beginSetup() { revoke(); setupShader=null; }
    public static void ready(Object shader) {
        if (!enabled()) { disarm(); return; }
        revoke();setupShader=shader;
    }
    public static void draw() {
        if(!enabled())return;
        drawCalls++;
        qualify(setupShader);
    }
    private static void qualify(Object shader) {
        if (!enabled()) { disarm(); return; }
        revoke();
        var c = certificate;
        if (c == null || shader != c.shader()) return;
        setups++;
        boolean accepted = false;
        try {
            var client = net.minecraft.client.Minecraft.getInstance();
            int p = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            var actual = c.pipeline().getSodiumPrograms().getProgram(DefaultTerrainRenderPasses.CUTOUT);
            if (!privateContext() || shader != c.shader() || actual == null || actual.getInterface() != shader
                || actual.handle() != p || p != c.program() || c.pipeline() != Iris.getPipelineManager().getPipelineNullable()
                || c.pack() != Iris.getCurrentPack().orElse(null) || c.context() != GL.getCapabilities()
                || c.world() != client.level || c.generation() != MakeUpQuadLifecycle.generation()
                || !c.selected().equals(Iris.getIrisConfig().getShaderPackName().orElse(null))
                || !c.resources().equals(List.copyOf(client.getResourcePackRepository().getSelectedIds()))
                || net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()
                || GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) != 1) { last = "owner/context/native requests differ"; return; }
            float reference = GL20.glGetUniformf(p, c.locations().get("iris_currentAlphaTest"));
            if (!SildurEarlyAlphaContract.alpha(reference) || GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE)
                || GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_ONE) || GL11.glIsEnabled(GL13.GL_SAMPLE_COVERAGE)
                || GL11.glIsEnabled(GL32.GL_SAMPLE_MASK)) { last = "native alpha/sample state differs"; return; }
            int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            if (!Arrays.equals(viewport, new int[]{0,0,c.targets().getCurrentWidth(),c.targets().getCurrentHeight()})) {
                last = "native viewport differs"; return;
            }
            if (c.shadows() == null) { last="actual native shadow owner absent"; return; }
            var shadowSampler=sample(c,"shadowtex0");
            int ownedShadow=c.shadows().getDepthTexture().getTextureId();
            if (!SildurEarlyAlphaContract.sampler(shadowSampler, ownedShadow, false)) {
                // Failure-only evidence, not a permission or sampler-state change.
                last = "native draw nonmip sampler proof refused: observed="+shadowSampler+"; owned="+ownedShadow;
                return;
            }
            GL20.glUniform1f(c.locations().get(REFERENCE), reference);
            GL20.glUniform1i(c.locations().get(PERMISSION), 1);
            grants++; accepted = true; last = "qualified owned CUTOUT native draw boundary";
        } catch (Exception | LinkageError e) { last = e.toString(); revoke(); }
        finally { if (!accepted) refusals++; }
    }

    private static int parameter(int sampler, int name) {
        return sampler == 0 ? GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, name) : GL33.glGetSamplerParameteri(sampler, name);
    }
    private static float parameterf(int sampler, int name) {
        return sampler == 0 ? GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, name) : GL33.glGetSamplerParameterf(sampler, name);
    }
    private static SildurEarlyAlphaContract.Sampler sample(Certificate c, String name) {
        int unit = GL20.glGetUniformi(c.program(), c.locations().get(name));
        if (unit < 0 || unit >= GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)) return null;
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), sampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            if (texture <= 0 || !GL11.glIsTexture(texture)) return null;
            var caps = GL.getCapabilities();
            float anisotropy = caps.GL_EXT_texture_filter_anisotropic || caps.GL_ARB_texture_filter_anisotropic ? parameterf(sampler, 0x84FE) : 1;
            return new SildurEarlyAlphaContract.Sampler(texture,
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),
                parameter(sampler,GL11.GL_TEXTURE_MIN_FILTER),parameter(sampler,GL11.GL_TEXTURE_MAG_FILTER),
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL),
                parameter(sampler,GL14.GL_TEXTURE_COMPARE_MODE),parameter(sampler,GL14.GL_TEXTURE_COMPARE_FUNC),
                parameterf(sampler,GL14.GL_TEXTURE_LOD_BIAS),parameterf(sampler,GL12.GL_TEXTURE_MIN_LOD),
                parameterf(sampler,GL12.GL_TEXTURE_MAX_LOD),anisotropy);
        } finally { GL13.glActiveTexture(active); }
    }
    static Map<String,Object> status() {
        return Map.of("armed",certificate != null,"setups",setups,"draw_calls",drawCalls,"grants",grants,"refusals",refusals,"last",last,
            "scope","Private native115 CUTOUT native-delegate permission checks; draw_calls include unqualified calls. Not GPU completions, avoided fragments or query counts");
    }
}
