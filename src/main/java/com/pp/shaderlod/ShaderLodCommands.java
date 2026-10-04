package com.pp.shaderlod;

import com.mojang.brigadier.arguments.BoolArgumentType;
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
                + ", debug=" + settings.debug();
    }
}
