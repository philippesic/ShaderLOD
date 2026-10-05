package com.pp.shaderlod;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.pp.shaderlod.lod.Effect;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.Locale;

/** Small in-game diagnostics and A/B controls for the first rendering proof. */
public final class ShaderLodCommands {
    private static String lastRuntimeStatus = "";

    private ShaderLodCommands() {}

    public static void register(RegisterClientCommandsEvent event) {
        var presetCommands = Commands.literal("preset");
        for (var preset : LodPreset.values()) {
            presetCommands.then(Commands.literal(preset.name().toLowerCase(Locale.ROOT)).executes(context -> {
                ShaderLodConfig.PRESET.set(preset);
                ShaderLodConfig.SPEC.save();
                return status(context.getSource());
            }));
        }
        event.getDispatcher().register(Commands.literal("shaderlod")
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(presetCommands)
                .then(effectCommands())
                .then(Commands.literal("terrain")
                        .executes(context -> status(context.getSource()))
                        .then(Commands.literal("enabled").then(Commands.argument("value", BoolArgumentType.bool()).executes(context -> {
                            boolean value = BoolArgumentType.getBool(context, "value");
                            ShaderLodConfig.TERRAIN_ENABLED.set(value);
                            if (!value) GeometryLodController.revokeCurrentScene();
                            ShaderLodConfig.SPEC.save();
                            return status(context.getSource());
                        })))
                        .then(Commands.literal("cap").then(Commands.argument("chunks", IntegerArgumentType.integer(8, 32)).executes(context -> {
                            ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.set(IntegerArgumentType.getInteger(context, "chunks"));
                            GeometryLodController.revokeCurrentScene();
                            ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.set(false);
                            ShaderLodConfig.SPEC.save();
                            return status(context.getSource());
                        })))
                        .then(Commands.literal("confirm").executes(context -> {
                            if (!GeometryLodController.confirmCurrentScene()) {
                                context.getSource().sendFailure(Component.literal("Enable the terrain policy in an active Iris/DH world before confirming inspected coverage."));
                                return 0;
                            }
                            ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.set(true);
                            ShaderLodConfig.SPEC.save();
                            context.getSource().sendSuccess(() -> Component.literal("Confirmed this inspected DH scene. Warmup still applies; moving to another chunk or height section restores detailed terrain."), false);
                            return 1;
                        }))
                        .then(Commands.literal("revoke").executes(context -> {
                            GeometryLodController.revokeCurrentScene();
                            ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.set(false);
                            ShaderLodConfig.SPEC.save();
                            return status(context.getSource());
                        })))
                .then(Commands.literal("enabled")
                        .then(Commands.argument("value", BoolArgumentType.bool()).executes(context -> {
                            ShaderLodConfig.ENABLED.set(BoolArgumentType.getBool(context, "value"));
                            ShaderLodConfig.SPEC.save();
                            return status(context.getSource());
                        })))
                .then(Commands.literal("debug")
                        .then(Commands.argument("value", BoolArgumentType.bool()).executes(context -> {
                            ShaderLodConfig.DEBUG.set(BoolArgumentType.getBool(context, "value"));
                            ShaderLodConfig.SPEC.save();
                            return status(context.getSource());
                        }))));
    }

    private static int status(net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(describe()), false);
        return 1;
    }

    /** Effect commands edit requested config, not inferred shader capabilities. */
    static LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> effectCommands() {
        var effects = LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("effects")
                .executes(context -> effectsStatus(context.getSource(), null))
                .then(LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("status")
                        .executes(context -> effectsStatus(context.getSource(), null)));
        for (Effect effect : Effect.values()) {
            var entry = LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal(ShaderLodEffectConfig.effectKey(effect))
                    .executes(context -> effectsStatus(context.getSource(), effect))
                    .then(LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("status")
                            .executes(context -> effectsStatus(context.getSource(), effect)));
            var modes = LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("mode");
            for (var mode : ShaderLodEffectConfig.Mode.values()) {
                modes.then(LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal(mode.name().toLowerCase(Locale.ROOT))
                        .executes(context -> editEffect(context.getSource(), effect,
                                () -> ShaderLodEffectConfig.setMode(effect, mode))));
            }
            entry.then(modes);
            entry.then(LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("bands")
                    .then(RequiredArgumentBuilder.<net.minecraft.commands.CommandSourceStack, Double>argument("near", DoubleArgumentType.doubleArg(1, 4095))
                    .then(RequiredArgumentBuilder.<net.minecraft.commands.CommandSourceStack, Double>argument("far", DoubleArgumentType.doubleArg(2, 4096))
                    .then(RequiredArgumentBuilder.<net.minecraft.commands.CommandSourceStack, Double>argument("transition", DoubleArgumentType.doubleArg(0, 512))
                            .executes(context -> editEffect(context.getSource(), effect, () -> ShaderLodEffectConfig.setBands(effect,
                                    DoubleArgumentType.getDouble(context, "near"), DoubleArgumentType.getDouble(context, "far"),
                                    DoubleArgumentType.getDouble(context, "transition"))))))));
            entry.then(LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("ratios")
                    .then(RequiredArgumentBuilder.<net.minecraft.commands.CommandSourceStack, Double>argument("medium", DoubleArgumentType.doubleArg(0, 1))
                    .then(RequiredArgumentBuilder.<net.minecraft.commands.CommandSourceStack, Double>argument("far", DoubleArgumentType.doubleArg(0, 1))
                            .executes(context -> editEffect(context.getSource(), effect, () -> ShaderLodEffectConfig.setRatios(effect,
                                    DoubleArgumentType.getDouble(context, "medium"), DoubleArgumentType.getDouble(context, "far")))))));
            entry.then(LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("protection")
                    .then(RequiredArgumentBuilder.<net.minecraft.commands.CommandSourceStack, Boolean>argument("value", BoolArgumentType.bool())
                            .executes(context -> editEffect(context.getSource(), effect,
                                    () -> ShaderLodEffectConfig.setProtection(effect, BoolArgumentType.getBool(context, "value"))))));
            effects.then(entry);
        }
        return effects;
    }

    private static int editEffect(net.minecraft.commands.CommandSourceStack source, Effect effect, Runnable edit) {
        try {
            edit.run();
            return effectsStatus(source, effect);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal("Effects request rejected: " + exception.getMessage()));
            return 0;
        }
    }

    private static int effectsStatus(net.minecraft.commands.CommandSourceStack source, Effect effect) {
        source.sendSuccess(() -> Component.literal(effect == null ? describeEffects() : describeEffect(effect)), false);
        return 1;
    }

    static String describeEffects() {
        var controls = ShaderLodEffectConfig.snapshot();
        var result = new StringBuilder("effectsProtocol=").append(ShaderLodEffectConfig.PROTOCOL_VERSION)
                .append(", IrisRegistrationObserved=").append(ShaderLodEffectConfig.irisRegistrationObserved())
                .append(", requestedModes=");
        for (Effect effect : Effect.values()) {
            result.append(ShaderLodEffectConfig.effectKey(effect)).append(':').append(controls.get(effect).mode()).append(' ');
        }
        return result.append("; shader consumption unverified").toString();
    }

    static String describeEffect(Effect effect) {
        var control = ShaderLodEffectConfig.snapshot().get(effect);
        var request = control.requested();
        var transport = control.policy();
        return "effect=" + ShaderLodEffectConfig.effectKey(effect) + ", requestedMode=" + control.mode()
                + ", requestedBands=" + request.bands().nearBlocks() + "/" + request.bands().farBlocks() + "/" + request.bands().transitionBlocks()
                + ", requestedRatios=" + request.mediumWorkRatio() + "/" + request.farWorkRatio()
                + ", transportRatios=" + transport.mediumWorkRatio() + "/" + transport.farWorkRatio()
                + ", preserveSalientLighting=" + request.preserveSalientLighting() + ", repaired=" + control.repaired()
                + ", effectsProtocol=" + ShaderLodEffectConfig.PROTOCOL_VERSION
                + ", IrisRegistrationObserved=" + ShaderLodEffectConfig.irisRegistrationObserved()
                + "; shader consumption unverified";
    }

    public static void reportRuntime(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) {
            lastRuntimeStatus = "";
            return;
        }
        String status = describe();
        if (!status.equals(lastRuntimeStatus)) {
            ShaderLod.LOGGER.info("ShaderLOD runtime: {}", status);
            lastRuntimeStatus = status;
        }
    }

    private static String describe() {
        var settings = ShaderLodConfig.snapshot();
        String pack = Iris.getCurrentPackName();
        return "pack=" + pack
                + ", patchedPackName=" + "Photon-ShaderLOD.zip".equals(pack)
                + ", shadersInUse=" + IrisApi.getInstance().isShaderPackInUse()
                + ", fallback=" + Iris.isFallback()
                + ", DH=" + ModList.get().isLoaded("distanthorizons")
                + ", enabled=" + settings.enabled()
                + ", preset=" + ShaderLodConfig.PRESET.get()
                + ", near/far=" + settings.near() + "/" + settings.far()
                + ", transition=" + settings.transition()
                + ", debug=" + settings.debug()
                + ", detailedChunks=" + GeometryLodController.status().effectiveChunks()
                + ", terrainPolicy=" + GeometryLodController.status().reason()
                + ", effectsProtocolDeclared=" + ShaderLodEffectConfig.PROTOCOL_VERSION
                + ", effectsIrisRegistrationObserved=" + ShaderLodEffectConfig.irisRegistrationObserved()
                + "; per-effect requests: /shaderlod effects (consumption unverified)";
    }
}
