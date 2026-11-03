package com.pp.shaderlod.integration;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.minecraft.SharedConstants;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

/** Observed pinned-source failures, not a platform support policy.
 * Evidence: docs/RUNTIME_SLICE_R2.md, original/private AstraLex and Solas reports.
 * Source identities hash executable/control members, independently of ZIP names/metadata.
 */
public final class KnownRuntimeBoundary {
    private KnownRuntimeBoundary() {}
    public static final String ASTRALEX_ORIGINAL = "e89fe2686d5a73d18ae9aae5104da24f6cb23ae79f1012f95b79aa78e655485a";
    public static final String ASTRALEX_PRIVATE = "5512a3f003fcb881903d5d1db6d230903c4259967962423de745d49bb75f6c7e";
    public static final String SOLAS_ORIGINAL = "f38d0fb61e858690ce3b40b0dfc8a0bd3212b4f3dbd42d0aa1c354dcdda35640";
    public static final String SOLAS_PRIVATE = "7e5171e0c1e780bc33e57401a8561d296d73b826d3bfd0d0d21965080e213a6d";
    public record Facts(String loader, String minecraft, String iris, String os, String osVersion,
            String vendor, String renderer, String glVersion, Boolean clearTextureAvailable) {
        public Facts(String loader, String minecraft, String iris, String os, String osVersion,
                String vendor, String renderer, String glVersion) {
            this(loader, minecraft, iris, os, osVersion, vendor, renderer, glVersion, null);
        }
        public static Facts unknown() { return new Facts(null, null, null, null, null, null, null, null); }
    }
    private static final Facts FAILED = new Facts("neoforge", "1.21.1",
            "1.8.12-snapshot+mc1.21.1-local", "Mac OS X", "27.0", "Apple", "Apple M3 Max", "4.1 Metal - 91.7");

    public static boolean pinnedSource(String digest) {
        return ASTRALEX_ORIGINAL.equals(digest) || (ASTRALEX_PRIVATE.equals(digest) || "c14c01825206f756891b3ead87d155c2c056a12c0de9edca8f55b9738397f64f".equals(digest) || "cab95ff343802da0addf3e01d3fefed2fa570a5be9b001f6082dfe0f49cf9692".equals(digest) || "d518b6c300a61d6a3153db52ac73fcd34275b52303c47a4cad8d3087b3b28154".equals(digest) || "6c4d1975c211e0501b7db35d77d01a28fff69fb598272a107dceadb1de5c473e".equals(digest) || "50312462b68e7096e4b74472e0db287beaebe8d91e7e9dbeea5758f2e74e1d3d".equals(digest) || "8b090761b1222e22136932d82c9577c84a6890ca286e65ec7e58dcde3dce5071".equals(digest) || "980352de42d27726a4dd324e16635e7572f5590ba138f7d7898f68bdc38b3e8b".equals(digest) || "9c29b0a9e3b5d69d9abb38702f9c1fa92472ffb5a7d8ed9e6add65f72b476f88".equals(digest) || "64074489150514b0e4b180f1ff59986712dd9984a0d5978051e88677d371944f".equals(digest) || "7a59f0be267d52c977371e9cf621e401a93030a7107f6b26410599e80ed4ae91".equals(digest))
                || solasSource(digest);
    }

    private static boolean solasSource(String digest) {
        return SOLAS_ORIGINAL.equals(digest) || (SOLAS_PRIVATE.equals(digest) || "4c25194a3d95b8ea1cb3886fbeab21d38c71b21cfda0265a2103e16fd3ebf19c".equals(digest) || "0adb01addbfd444ce9b7407c588d3accf4a0e0664e2751c12004a348afaa8ec1".equals(digest) || "0ddecbc96a5521f1f519e44fe7aec5814bf03447fc07928ed49ce4c2691b4039".equals(digest) || "469a65f1654793dd5e9dca70c91746e98aef2df240a601f8da3d5cc5934f6cf5".equals(digest) || "8b96d3a6b8cfc4580ae47604ad8fd114de802e79ac75206374d2f5c97b129480".equals(digest));
    }

    /** Empty means only that this particular failure was not matched; never runtime validation.
     * A missing fact cannot rule out this tuple. For these pinned sources only, retain the current
     * selection until the check can run. A known different fact rules it out even if others are missing.
     */
    public static Optional<String> refusal(String digest, Facts facts) {
        if (!pinnedSource(digest)) return Optional.empty();
        Objects.requireNonNull(facts);
        boolean solas = solasSource(digest);
        // Check the actual function pointer used by Iris, not a version/extension guess.
        if (solas && Boolean.TRUE.equals(facts.clearTextureAvailable)) return Optional.empty();
        String[] actual = {facts.loader, facts.minecraft, facts.iris, facts.os, facts.osVersion,
                facts.vendor, facts.renderer, facts.glVersion};
        List<String> expected = List.of(FAILED.loader, FAILED.minecraft, FAILED.iris, FAILED.os,
                FAILED.osVersion, FAILED.vendor, FAILED.renderer, FAILED.glVersion);
        boolean unavailable = false;
        for (int i = 0; i < actual.length; i++) {
            if (actual[i] == null || actual[i].isBlank()) unavailable = true;
            else if (!actual[i].equals(expected.get(i))) return Optional.empty();
        }
        if (solas) return Optional.of(unavailable || facts.clearTextureAvailable == null
                ? "Solas 3.7b Apply was not run: runtime details or texture-clear capability are unavailable to check a known failure. Choose another shader pack. Previous selection retained."
                : "Solas 3.7b Apply was blocked: this Iris version calls unavailable glClearTexImage on Apple M3 Max OpenGL 4.1 Metal - 91.7, disabling shaders. Choose another shader pack. Previous selection retained.");
        return Optional.of(unavailable
                ? "AstraLex 93.0 Apply was not run: runtime details are unavailable to check a known native crash. Previous selection retained."
                : "AstraLex 93.0 Apply was blocked: its original and LOD copy crash the Apple M3 Max OpenGL 4.1 Metal - 91.7 linker with this Iris version. Previous selection retained.");
    }

    /** Called only by the Iris backend, for a pinned source, with its existing current GL context. */
    public static Facts currentIrisFacts() {
        String loader = null, minecraft = null, iris = null, os = null, osVersion = null;
        try {
            var platform = IrisPlatformHelpers.getInstance();
            if (platform.isModLoaded("neoforge")) loader = "neoforge";
            else if (platform.isModLoaded("fabricloader")) loader = "fabric";
            minecraft = SharedConstants.getCurrentVersion().getName();
            iris = Iris.getVersion();
            os = System.getProperty("os.name"); osVersion = System.getProperty("os.version");
            if (RenderSystem.isOnRenderThread()) {
                var capabilities = GL.getCapabilities(); // Never create or move a context.
                return new Facts(loader, minecraft, iris, os, osVersion,
                        GL11.glGetString(GL11.GL_VENDOR), GL11.glGetString(GL11.GL_RENDERER),
                        GL11.glGetString(GL11.GL_VERSION), capabilities.glClearTexImage != 0L);
            }
        } catch (RuntimeException | LinkageError unavailable) {
            // Keep partial facts: an identified different tuple can still be ruled out safely.
        }
        return new Facts(loader, minecraft, iris, os, osVersion, null, null, null);
    }
    public static boolean isRefusal(Exception failure) { return failure instanceof Refusal; }
    static final class Refusal extends IOException {
        Refusal(String detail) { super(detail); }
    }
}
