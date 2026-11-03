package com.pp.shaderlod;

import java.util.Objects;
import net.irisshaders.iris.Iris;
import com.pp.shaderlod.integration.PackSelectionTransaction;

/** Reloads after budget changes until cross-preset temporal transitions are qualified. */
public final class ShaderLodSettings {
    private static String failureMessage = "Could not apply settings. See the game log.";
    private ShaderLodSettings() {}

    @FunctionalInterface interface Reload { void run() throws Exception; }
    @FunctionalInterface interface ForwardReload { void run(PackSelectionTransaction selection,Reload reload) throws Exception; }
    public static boolean apply(boolean enabled, LodPreset preset) {
        // Capture route eligibility before settings suspension disables live renderer work.
        boolean nativeRendererAvailable = com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.nativeGraphAvailable();
        return apply(enabled, preset, () -> PackSelectionTransaction.iris(nativeRendererAvailable), Iris::reload, Iris::isFallback,
                (selection,reload)->com.pp.shaderlod.optimization.CompileOptionControl.reloadSettings(selection,reload::run));
    }
    static boolean apply(boolean enabled, LodPreset preset,
            java.util.function.Supplier<PackSelectionTransaction> transactions, Reload reload,
            java.util.function.BooleanSupplier fallback) {
        return apply(enabled,preset,transactions,reload,fallback,(selection,action)->selection.reload(action::run));
    }
    static boolean apply(boolean enabled,LodPreset preset,
            java.util.function.Supplier<PackSelectionTransaction> transactions,Reload reload,
            java.util.function.BooleanSupplier fallback,ForwardReload forward) {
        Objects.requireNonNull(preset, "preset");
        boolean previousEnabled = ShaderLodConfig.ENABLED.get();
        LodPreset previousPreset = ShaderLodConfig.PRESET.get();
        PackSelectionTransaction selection = null;
        com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.beginSettingsApply();
        try {
            selection = transactions.get();
            boolean changedPack = selection.prepare(enabled, preset);
            boolean changed = previousEnabled != enabled || previousPreset != preset
                    || changedPack;
            if (!changed) return true;
            ShaderLodConfig.ENABLED.set(enabled);
            ShaderLodConfig.PRESET.set(preset);
            ShaderLodConfig.SPEC.save();
            forward.run(selection,reload);
            if (fallback.getAsBoolean()) throw new IllegalStateException("Shader reload fell back");
            return true;
        } catch (Exception failure) {
            failureMessage = PackSelectionTransaction.failureMessage(failure);
            if (com.pp.shaderlod.integration.KnownRuntimeBoundary.isRefusal(failure)) {
                ShaderLod.LOGGER.warn("ShaderLOD retained the current selection: {}", failureMessage);
                return false;
            }
            ShaderLod.LOGGER.error("Unable to apply ShaderLOD preset; restoring previous settings", failure);
            ShaderLodConfig.ENABLED.set(previousEnabled);
            ShaderLodConfig.PRESET.set(previousPreset);
            try {
                ShaderLodConfig.SPEC.save();
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
                    if (selection == null) reload.run();
                    else com.pp.shaderlod.optimization.CompileOptionControl.recoveryReload(selection,reload::run);
                    if (fallback.getAsBoolean()) throw new IllegalStateException("Previous shader reload fell back");
                }
            } catch (Exception restoreFailure) {
                ShaderLod.LOGGER.error("Unable to reload previous shader settings", restoreFailure);
            } finally {
                if (selection != null) selection.finishRecovery();
            }
            return false;
        } finally {
            // Tentative config must never activate renderer savings before successful
            // Apply or the completed recovery attempt. Both boundaries revoke proofs.
            com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.endSettingsApply();
        }
    }

    public static String failureMessage() { return failureMessage; }
}
