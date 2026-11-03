package com.pp.shaderlod;

import java.util.Objects;
import net.irisshaders.iris.Iris;
import com.pp.shaderlod.integration.PackSelectionTransaction;

/** Reloads after budget changes until cross-preset temporal transitions are qualified. */
public final class ShaderLodSettings {
    private static String failureMessage = "Could not apply settings. See the game log.";
    @FunctionalInterface interface Preflight { void run() throws Exception; }
    static Preflight applyPreflight = com.pp.shaderlod.integration.RendererBackend::requireApplySupported;
    static java.util.function.Supplier<PackSelectionTransaction> selectionFactory = PackSelectionTransaction::iris;
    static Reload applyReload = Iris::reload;
    static java.util.function.BooleanSupplier applyFallback = Iris::isFallback;
    private ShaderLodSettings() {}

    @FunctionalInterface interface Reload { void run() throws Exception; }
    public static boolean apply(boolean enabled, LodPreset preset) {
        return apply(enabled, preset, selectionFactory, applyReload, applyFallback);
    }
    static boolean apply(boolean enabled, LodPreset preset,
            java.util.function.Supplier<PackSelectionTransaction> transactions, Reload reload,
            java.util.function.BooleanSupplier fallback) {
        Objects.requireNonNull(preset, "preset");
        boolean previousEnabled = false;
        LodPreset previousPreset = null;
        PackSelectionTransaction selection = null;
        com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.beginSettingsApply();
        try {
            applyPreflight.run();
            previousEnabled = ShaderLodConfig.ENABLED.get();
            previousPreset = ShaderLodConfig.PRESET.get();
            selection = transactions.get();
            boolean changedPack = selection.prepare(enabled, preset);
            boolean changed = previousEnabled != enabled || previousPreset != preset
                    || changedPack;
            if (!changed) return true;
            ShaderLodConfig.ENABLED.set(enabled);
            ShaderLodConfig.PRESET.set(preset);
            ShaderLodConfig.SPEC.save();
            selection.reload(reload::run);
            if (fallback.getAsBoolean()) throw new IllegalStateException("Shader reload fell back");
            return true;
        } catch (Exception failure) {
            failureMessage = PackSelectionTransaction.failureMessage(failure);
            if (com.pp.shaderlod.integration.KnownRuntimeBoundary.isRefusal(failure)) {
                ShaderLod.LOGGER.warn("ShaderLOD retained the current selection: {}", failureMessage);
                return false;
            }
            ShaderLod.LOGGER.error("Unable to apply ShaderLOD preset; restoring previous settings", failure);
            if (previousPreset != null) {
                ShaderLodConfig.ENABLED.set(previousEnabled);
                ShaderLodConfig.PRESET.set(previousPreset);
            }
            try {
                if (previousPreset != null) ShaderLodConfig.SPEC.save();
            } catch (Exception restoreFailure) {
                ShaderLod.LOGGER.error("Unable to persist previous ShaderLOD settings", restoreFailure);
            }
            boolean recover = true;
            try {
                if (selection != null) selection.restoreForRecovery();
            } catch (Exception restoreFailure) {
                ShaderLod.LOGGER.error("Unable to restore previous Iris selection/files", restoreFailure);
                if (selection != null && selection.requiresOwnedNativeRecovery()) recover = false;
            }
            try {
                if (recover) {
                    if (selection == null) reload.run(); else selection.recoveryReload(reload::run);
                    if (fallback.getAsBoolean()) throw new IllegalStateException("Previous shader reload fell back");
                }
            } catch (Exception restoreFailure) {
                ShaderLod.LOGGER.error("Unable to reload previous shader settings", restoreFailure);
            } finally {
                if (selection != null) selection.finishRecovery();
            }
            return false;
        } finally {
            com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.endSettingsApply();
        }
    }

    public static String failureMessage() { return failureMessage; }
}
