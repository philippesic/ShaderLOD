package com.pp.shaderlod.fabric;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import java.io.IOException;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.irisshaders.iris.Iris;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One shader reload per explicit Apply; failed changes restore the complete old config. */
public final class FabricLodSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger("shaderlod");
    private static String failureMessage = "Could not apply settings. See the game log.";
    @FunctionalInterface interface Reload { void run() throws Exception; }
    private FabricLodSettings() {}
    @FunctionalInterface interface Preflight { void run() throws Exception; }
    public static boolean apply(boolean enabled, LodPreset preset) {
        // Capture route eligibility before settings suspension disables live renderer work.
        boolean nativeRendererAvailable = com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.nativeGraphAvailable();
        return apply(enabled, preset, com.pp.shaderlod.integration.RendererBackend::requireApplySupported,
                () -> PackSelectionTransaction.iris(nativeRendererAvailable), Iris::reload, Iris::isFallback);
    }
    static boolean apply(boolean enabled, LodPreset preset, Preflight preflight,
            java.util.function.Supplier<PackSelectionTransaction> transactions, Reload reload, BooleanSupplier fallback) {
        Objects.requireNonNull(preset);
        FabricLodConfig.Values previous = null;
        PackSelectionTransaction selection = null;
        com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.beginSettingsApply();
        try {
            preflight.run();
            previous = FabricLodConfig.snapshot();
            selection = transactions.get();
            boolean changedPack = selection.prepare(enabled, preset);
            boolean changedConfig = previous.enabled() != enabled || previous.preset() != preset;
            if (!changedPack && !changedConfig) return true;
            if (!FabricLodConfig.replace(new FabricLodConfig.Values(enabled, preset, previous.near(),
                    previous.far(), previous.transition(), previous.debug()))) throw new IOException("Could not persist settings");
            if (changedPack || changedConfig) selection.reload(reload::run);
            if (fallback.getAsBoolean()) throw new IllegalStateException("Shader reload fell back");
            return true;
        } catch (Exception failure) {
            if (com.pp.shaderlod.integration.KnownRuntimeBoundary.isRefusal(failure)) {
                failureMessage = com.pp.shaderlod.integration.PackSelectionTransaction.failureMessage(failure);
                LOGGER.warn("ShaderLOD retained the current selection: {}", failureMessage);
                return false;
            }
            failureMessage = PackSelectionTransaction.failureMessage(failure);
            LOGGER.error("Unable to apply ShaderLOD selection; restoring previous settings", failure);
            if (previous == null) return false; // No config snapshot/mutation was reached.
            if (!FabricLodConfig.replace(previous)) LOGGER.error("Could not persist previous ShaderLOD config");
            boolean recover = true;
            try {
                if (selection != null) selection.restoreForRecovery();
            } catch (Exception restoreFailure) {
                LOGGER.error("Unable to restore previous Iris selection/files", restoreFailure);
                if (selection != null && selection.requiresOwnedNativeRecovery()) recover = false;
            }
            try {
                if (recover) {
                    if (selection == null) reload.run(); else selection.recoveryReload(reload::run);
                    if (fallback.getAsBoolean()) throw new IllegalStateException("Previous shader reload fell back");
                }
            } catch (Exception restoreFailure) {
                LOGGER.error("Unable to reload previous shader settings", restoreFailure);
            } finally {
                if (selection != null) selection.finishRecovery();
            }
            return false;
        } finally {
            com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.endSettingsApply();
        }
    }
    public static String failureMessage() { return failureMessage; }
    static boolean apply(boolean enabled, LodPreset preset, Reload reload, BooleanSupplier fallback) {
        Objects.requireNonNull(preset);
        var previous = FabricLodConfig.snapshot();
        if (previous.enabled() == enabled && previous.preset() == preset) return true;
        var next = new FabricLodConfig.Values(enabled, preset, previous.near(), previous.far(),
                previous.transition(), previous.debug());
        com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.beginSettingsApply();
        try {
            if (!FabricLodConfig.replace(next)) throw new IOException("Could not persist requested settings");
            reload.run();
            if (fallback.getAsBoolean()) throw new IllegalStateException("Shader reload fell back");
            return true;
        } catch (Exception failure) {
            LOGGER.error("Unable to apply ShaderLOD Fabric settings; restoring previous config", failure);
            try {
                if (!FabricLodConfig.replace(previous)) throw new IOException("Could not restore previous config");
                reload.run();
                if (fallback.getAsBoolean()) throw new IllegalStateException("Previous shader reload fell back");
            } catch (Exception restoreFailure) {
                LOGGER.error("Unable to restore previous ShaderLOD shader state", restoreFailure);
            }
            return false;
        } finally {
            com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.endSettingsApply();
        }
    }
}
