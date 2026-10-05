package com.pp.shaderlod;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.pp.shaderlod.lod.DistanceBands;
import com.pp.shaderlod.lod.Effect;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ShaderLodEffectConfigTest {
    private static final Effect EFFECT = Effect.OPAQUE_LIGHTING;
    private Loaded loaded;
    @TempDir Path temporary;

    /** Use FML's actual sealed LoadedConfig, real event bus and temporary TOML persistence. */
    private static final class Loaded {
        private final CommentedConfig config = CommentedConfig.inMemory();
        private final IConfigSpec.ILoadedConfig delegate;
        private final ModConfig modConfig;
        private final Path path;
        private int saves;
        public CommentedConfig config() { return config; }

        Loaded(Path path) throws ReflectiveOperationException {
            this.path = path;
            var bus = BusBuilder.builder().markerType(IModBusEvent.class).build();
            bus.addListener(ShaderLodEffectConfig::onConfigEvent);
            bus.addListener((ModConfigEvent.Reloading event) -> {
                assertFalse(Thread.holdsLock(ShaderLodEffectConfig.class), "Save must not invert FML's reload lock order");
                saves++;
            });
            var info = (IModInfo) Proxy.newProxyInstance(IModInfo.class.getClassLoader(), new Class<?>[]{IModInfo.class},
                    (proxy, method, args) -> method.getName().equals("getModId") ? "shaderlod" : null);
            var container = new ModContainer(info) {
                @Override public IEventBus getEventBus() { return bus; }
            };
            var constructor = ModConfig.class.getDeclaredConstructor(ModConfig.Type.class, IConfigSpec.class,
                    ModContainer.class, String.class, ReentrantLock.class);
            constructor.setAccessible(true);
            modConfig = constructor.newInstance(ModConfig.Type.CLIENT, ShaderLodEffectConfig.SPEC, container,
                    path.getFileName().toString(), new ReentrantLock());
            var loadedConstructor = Class.forName("net.neoforged.fml.config.LoadedConfig")
                    .getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
            loadedConstructor.setAccessible(true);
            delegate = (IConfigSpec.ILoadedConfig) loadedConstructor.newInstance(config, path, modConfig);
        }
    }

    @BeforeEach void load() throws ReflectiveOperationException {
        FMLPaths.loadAbsolutePaths(temporary);
        FMLConfig.load();
        loaded = new Loaded(temporary.resolve("shaderlod-effects-client.toml"));
        ShaderLodEffectConfig.SPEC.correct(loaded.config());
        ShaderLodEffectConfig.SPEC.acceptConfig(loaded.delegate);
        ShaderLodEffectConfig.onConfigEvent(new ModConfigEvent.Loading(loaded.modConfig));
    }

    @AfterEach void unload() {
        ShaderLodEffectConfig.SPEC.acceptConfig(null);
        ShaderLodEffectConfig.refresh();
    }

    @Test void additiveVersionTwoDefaultsKeepAllSevenEffectsInheritedAndUnity() {
        assertEquals(2, ShaderLodEffectConfig.PROTOCOL_VERSION);
        assertEquals(7, ShaderLodEffectConfig.snapshot().size());
        assertEquals("OpaqueLighting", EFFECT.uniformKeySuffix());
        assertEquals("opaque_lighting", ShaderLodEffectConfig.effectKey(EFFECT));
        assertFalse(EFFECT.salientLighting());
        for (var effect : Effect.values()) {
            var control = ShaderLodEffectConfig.snapshot().get(effect);
            assertEquals(ShaderLodEffectConfig.Mode.INHERIT, control.mode());
            assertFalse(control.policy().enabled());
            assertTrue(control.requested().preserveSalientLighting());
            assertEquals(1, control.policy().mediumWorkRatio());
            assertEquals(1, control.policy().farWorkRatio());
            assertFalse(control.repaired());
        }
        assertThrows(UnsupportedOperationException.class, () -> ShaderLodEffectConfig.snapshot().clear());
        assertSame(ShaderLodEffectConfig.snapshot(), ShaderLodEffectConfig.snapshot());
    }

    @Test void validatedEditsPersistAndPublishAnEntireImmutableRequest() throws Exception {
        var before = ShaderLodEffectConfig.snapshot();
        ShaderLodEffectConfig.setMode(EFFECT, ShaderLodEffectConfig.Mode.ADAPTIVE);
        ShaderLodEffectConfig.setBands(EFFECT, 48, 128, 16);
        ShaderLodEffectConfig.setRatios(EFFECT, 1, .25);
        var control = ShaderLodEffectConfig.snapshot().get(EFFECT);
        assertEquals(3, loaded.saves);
        assertTrue(Files.readString(loaded.path).contains("farWorkRatio = 0.25"));
        assertEquals(ShaderLodEffectConfig.Mode.ADAPTIVE, control.mode());
        assertTrue(control.policy().enabled());
        assertEquals(new DistanceBands(48, 128, 16), control.policy().bands());
        assertEquals(.25, control.policy().farWorkRatio());
        assertSame(control.requested(), control.policy());
        assertEquals(1, control.policy().decide(true, EFFECT, 1000, 4, 1).workLimit());
        assertEquals(ShaderLodEffectConfig.Mode.INHERIT, before.get(EFFECT).mode());
        ShaderLodEffectConfig.setMode(EFFECT, ShaderLodEffectConfig.Mode.FULL);
        assertEquals(4, ShaderLodEffectConfig.snapshot().get(EFFECT).policy().decide(true, EFFECT, 1000, 4, 1).workLimit());
    }

    @Test void protectionKeepsRequestedRatiosButTransportsUnityUntilReleased() {
        for (var effect : new Effect[]{Effect.SHADOW_FILTERING, Effect.AMBIENT_OCCLUSION}) {
            ShaderLodEffectConfig.setMode(effect, ShaderLodEffectConfig.Mode.ADAPTIVE);
            ShaderLodEffectConfig.setRatios(effect, .5, .25);
            var protectedControl = ShaderLodEffectConfig.snapshot().get(effect);
            assertEquals(.25, protectedControl.requested().farWorkRatio());
            assertEquals(1, protectedControl.policy().farWorkRatio());
            ShaderLodEffectConfig.setProtection(effect, false);
            assertEquals(.25, ShaderLodEffectConfig.snapshot().get(effect).policy().farWorkRatio());
            ShaderLodEffectConfig.setProtection(effect, true);
            assertEquals(1, ShaderLodEffectConfig.snapshot().get(effect).policy().farWorkRatio());
        }
    }

    @Test void invalidCommandEditsDoNotMutateSaveOrPublish() {
        var before = ShaderLodEffectConfig.snapshot();
        for (double[] bands : new double[][]{{96, 32, 8}, {32, 96, 65}, {0, 96, 8},
                {32, 4097, 8}, {32, 96, Double.NaN}, {32, 96, 513}}) {
            assertThrows(IllegalArgumentException.class, () -> ShaderLodEffectConfig.setBands(EFFECT, bands[0], bands[1], bands[2]));
        }
        for (double[] ratios : new double[][]{{.25, .5}, {-1, 0}, {1.1, 1}, {1, Double.NaN}}) {
            assertThrows(IllegalArgumentException.class, () -> ShaderLodEffectConfig.setRatios(EFFECT, ratios[0], ratios[1]));
        }
        assertSame(before, ShaderLodEffectConfig.snapshot());
        assertEquals(0, loaded.saves);
    }

    @Test void reloadRepairsCrossFieldsAndLeavesOldSnapshotsUnchanged() {
        var old = ShaderLodEffectConfig.snapshot();
        String path = "effects.opaque_lighting.";
        loaded.config.set(path + "mode", ShaderLodEffectConfig.Mode.ADAPTIVE);
        loaded.config.set(path + "near", 96.0);
        loaded.config.set(path + "far", 32.0);
        loaded.config.set(path + "transition", 64.0);
        loaded.config.set(path + "mediumWorkRatio", .25);
        loaded.config.set(path + "farWorkRatio", .75);
        ShaderLodEffectConfig.SPEC.afterReload();
        ShaderLodEffectConfig.onConfigEvent(new ModConfigEvent.Reloading(loaded.modConfig));
        var repaired = ShaderLodEffectConfig.snapshot().get(EFFECT);
        assertEquals(new DistanceBands(96, 97, 1), repaired.policy().bands());
        assertEquals(.25, repaired.policy().farWorkRatio());
        assertTrue(repaired.repaired());
        assertEquals(new DistanceBands(32, 96, 8), old.get(EFFECT).policy().bands());
        assertEquals(ShaderLodEffectConfig.Mode.INHERIT, old.get(EFFECT).mode());
    }

    @Test void malformedNonfiniteRawValuesFailOpenAndNeverReachTransport() {
        var control = ShaderLodEffectConfig.normalize(EFFECT, ShaderLodEffectConfig.Mode.ADAPTIVE,
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, .5, .25, true);
        assertEquals(ShaderLodEffectConfig.Mode.FULL, control.mode());
        assertEquals(new DistanceBands(32, 96, 8), control.policy().bands());
        assertEquals(1, control.policy().farWorkRatio());
        assertTrue(control.repaired());
    }

    @Test void unloadedConfigHasSafeDefaultsAndCannotBeEdited() {
        ShaderLodEffectConfig.setMode(EFFECT, ShaderLodEffectConfig.Mode.ADAPTIVE);
        ShaderLodEffectConfig.onConfigEvent(new ModConfigEvent.Unloading(loaded.modConfig));
        assertEquals(ShaderLodEffectConfig.Mode.INHERIT, ShaderLodEffectConfig.snapshot().get(EFFECT).mode());
        ShaderLodEffectConfig.SPEC.acceptConfig(null);
        ShaderLodEffectConfig.refresh();
        assertEquals(ShaderLodEffectConfig.Mode.INHERIT, ShaderLodEffectConfig.snapshot().get(EFFECT).mode());
        assertThrows(IllegalStateException.class, () -> ShaderLodEffectConfig.setMode(EFFECT, ShaderLodEffectConfig.Mode.ADAPTIVE));
    }
}
