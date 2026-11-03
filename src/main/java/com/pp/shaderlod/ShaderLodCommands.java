package com.pp.shaderlod;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.benchmark.BenchmarkRunSession;
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
        var presetCommands = Commands.literal("preset").requires(source->!com.pp.shaderlod.tuning.AutoTuner.active()&&!BenchmarkRunSession.owned());
        for (var preset : LodPreset.values()) {
            presetCommands.then(Commands.literal(preset.name().toLowerCase(Locale.ROOT)).executes(context -> {
                if (!ShaderLodSettings.apply(ShaderLodConfig.ENABLED.get(), preset)) {
                    context.getSource().sendFailure(Component.literal("Could not reload the shader with this LOD preset."));
                    return 0;
                }
                return status(context.getSource());
            }));
        }
        event.getDispatcher().register(Commands.literal("shaderlod")
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(presetCommands)
                .then(effectCommands())
                .then(optimizationCommands())
                .then(timingCommands())
                .then(benchmarkCommands())
                .then(tuneCommands())
                .then(savedTuningCommands())
                .then(Commands.literal("experimental").then(Commands.literal("bloom_vrs")
                        .executes(context -> bloomVrsStatus(context.getSource()))
                        .then(Commands.literal("status").executes(context -> bloomVrsStatus(context.getSource())))
                        .then(Commands.argument("enabled",BoolArgumentType.bool())
                                .requires(source -> !com.pp.shaderlod.tuning.AutoTuner.active() && !BenchmarkRunSession.owned())
                                .executes(context -> {
                                    try {
                                        com.pp.shaderlod.measurement.RuntimeMeasurement.cancelActivePair();
                                        String message=com.pp.shaderlod.optimization.vrs.BloomVrsRuntime.setRequested(
                                                BoolArgumentType.getBool(context,"enabled"));
                                        context.getSource().sendSuccess(() -> Component.literal(message),false);return 1;
                                    } catch(RuntimeException failure) {
                                        context.getSource().sendFailure(Component.literal(failure.getMessage()));return 0;
                                    }
                                }))))
                .then(Commands.literal("terrain").requires(source->!com.pp.shaderlod.tuning.AutoTuner.active()
                        &&!com.pp.shaderlod.tuning.AutoTuner.restorationPending()&&!com.pp.shaderlod.tuning.SavedTunings.pending()&&!BenchmarkRunSession.owned())
                        .executes(context -> status(context.getSource()))
                        .then(Commands.literal("enabled").then(Commands.argument("value", BoolArgumentType.bool()).executes(context -> {
                            boolean value = BoolArgumentType.getBool(context, "value");
                            com.pp.shaderlod.measurement.RuntimeMeasurement.cancelActivePair();
                            var next = com.pp.shaderlod.optimization.OptimizationConfig.snapshot().with(com.pp.shaderlod.optimization.OptimizationId.TERRAIN_RADIUS,value);
                            var applied = com.pp.shaderlod.optimization.OptimizationEditTransaction.apply(next,Iris::reload,Iris::isFallback,com.pp.shaderlod.optimization.CompileOptionControl::open);
                            if(!applied.success()){context.getSource().sendFailure(Component.literal(applied.message()));return 0;}
                            ShaderLodConfig.TERRAIN_ENABLED.set(value);
                            GeometryLodController.revokeCurrentScene();
                            ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.set(false);
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
                                context.getSource().sendFailure(Component.literal(GeometryLodController.confirmationReason()));
                                return 0;
                            }
                            ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.set(true);
                            ShaderLodConfig.SPEC.save();
                            context.getSource().sendSuccess(() -> Component.literal("Confirmed this warmed, inspected DH scene. Moving to another chunk or height section restores detailed terrain."), false);
                            return 1;
                        }))
                        .then(Commands.literal("revoke").executes(context -> {
                            GeometryLodController.revokeCurrentScene();
                            ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.set(false);
                            ShaderLodConfig.SPEC.save();
                            return status(context.getSource());
                        })))
                .then(Commands.literal("enabled").requires(source->!BenchmarkRunSession.owned())
                        .then(Commands.argument("value", BoolArgumentType.bool()).executes(context -> {
                            if (!ShaderLodSettings.apply(BoolArgumentType.getBool(context, "value"), ShaderLodConfig.PRESET.get())) {
                                context.getSource().sendFailure(Component.literal("Could not reload the shader with this LOD setting."));
                                return 0;
                            }
                            return status(context.getSource());
                        })))
                .then(Commands.literal("debug").requires(source->!BenchmarkRunSession.owned())
                        .then(Commands.argument("value", BoolArgumentType.bool()).executes(context -> {
                            ShaderLodConfig.DEBUG.set(BoolArgumentType.getBool(context, "value"));
                            ShaderLodConfig.SPEC.save();
                            return status(context.getSource());
                        }))));
    }

    private static int bloomVrsStatus(net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(com.pp.shaderlod.optimization.vrs.BloomVrsRuntime.status().toString()),false);
        return 1;
    }

    private static LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> optimizationCommands() {
        var root=Commands.literal("optimization").requires(source->!com.pp.shaderlod.tuning.AutoTuner.active()&&!BenchmarkRunSession.owned()).executes(context->{
            context.getSource().sendSuccess(()->Component.literal("Shader="+com.pp.shaderlod.optimization.OptimizationConfig.activeFamily()+"; "+ShaderOptimizationProfiles.status()+"; /shaderlod optimization <feature> <true|false>"),false);return 1;
        });
        for(var id:com.pp.shaderlod.optimization.OptimizationId.values())root.then(Commands.literal(id.key())
            .executes(context->{context.getSource().sendSuccess(()->Component.literal(id.key()+"="+com.pp.shaderlod.optimization.OptimizationConfig.requested(id)+"; "+com.pp.shaderlod.optimization.OptimizationRuntimeContext.implementation(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily(),id)),false);return 1;})
            .then(Commands.argument("value",BoolArgumentType.bool()).executes(context->{
                try {
                    com.pp.shaderlod.measurement.RuntimeMeasurement.cancelActivePair();
                    var next=com.pp.shaderlod.optimization.OptimizationConfig.snapshot().with(id,BoolArgumentType.getBool(context,"value"));
                    var result=com.pp.shaderlod.optimization.OptimizationEditTransaction.apply(next,Iris::reload,Iris::isFallback,com.pp.shaderlod.optimization.CompileOptionControl::open);
                    if(!result.success()){context.getSource().sendFailure(Component.literal(result.message()));return 0;}
                    context.getSource().sendSuccess(()->Component.literal(id.key()+"="+BoolArgumentType.getBool(context,"value")+" for "+com.pp.shaderlod.optimization.OptimizationConfig.activeFamily()),false);return 1;
                }catch(RuntimeException failure){context.getSource().sendFailure(Component.literal(failure.getMessage()));return 0;}
            })));
        return root;
    }
    private static LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> timingCommands() {
        var root=Commands.literal("timing").requires(source->!com.pp.shaderlod.tuning.AutoTuner.active()&&!BenchmarkRunSession.owned()).executes(context->{context.getSource().sendSuccess(()->Component.literal(com.pp.shaderlod.measurement.RuntimeMeasurement.status()),false);return 1;});
        for(boolean enabled:new boolean[]{false,true})root.then(Commands.literal(enabled?"on":"off").executes(context->{com.pp.shaderlod.measurement.RuntimeMeasurement.request(enabled);return 1;}));
        root.then(Commands.literal("cancel").executes(context->{com.pp.shaderlod.measurement.RuntimeMeasurement.cancelActivePair();return 1;}));
        root.then(Commands.literal("export").executes(context->{
            try{var output=com.pp.shaderlod.measurement.RuntimeMeasurement.export(Iris.getShaderpacksDirectory().resolveSibling("shaderlod-measurements"));context.getSource().sendSuccess(()->Component.literal(output.toString()),false);return 1;}
            catch(java.io.IOException failure){context.getSource().sendFailure(Component.literal(failure.getMessage()));return 0;}
        }));
        var compare=Commands.literal("compare");
        for(var id:com.pp.shaderlod.optimization.OptimizationId.values())compare.then(Commands.literal(id.key()).executes(context->{
            try{Minecraft.getInstance().setScreen(null);com.pp.shaderlod.measurement.RuntimeMeasurement.startPair(id,120,240,()->ShaderLodConfig.snapshot().toString()+ShaderLodConfig.PRESET.get());return 1;}
            catch(RuntimeException failure){context.getSource().sendFailure(Component.literal(failure.getMessage()));return 0;}
        }));
        root.then(compare);return root;
    }

    private static LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> benchmarkCommands(){
        var root=Commands.literal("benchmark").executes(c->benchmarkAction(c.getSource(),()->{},false));
        root.then(Commands.literal("start").executes(c->benchmarkAction(c.getSource(),BenchmarkRunSession::start,false))
                .then(Commands.literal("fast").executes(c->benchmarkAction(c.getSource(),BenchmarkRunSession::start,false))));
        root.then(Commands.literal("diagnostic").executes(c->benchmarkAction(c.getSource(),BenchmarkRunSession::startDiagnostic,false)));
        root.then(Commands.literal("status").executes(c->benchmarkAction(c.getSource(),()->{},false)));
        root.then(Commands.literal("cancel").executes(c->benchmarkAction(c.getSource(),BenchmarkRunSession::cancel,false)));
        root.then(Commands.literal("results").executes(c->benchmarkAction(c.getSource(),()->{},true)));
        return root;
    }
    private static int benchmarkAction(net.minecraft.commands.CommandSourceStack source,Runnable action,boolean results){
        try{
            action.run();
            source.sendSuccess(()->results?BenchmarkUi.benchmarkResultsMessage():Component.literal(BenchmarkRunSession.status()),false);
            return 1;
        }catch(RuntimeException failure){source.sendFailure(Component.literal(failure.getMessage()==null?failure.toString():failure.getMessage()));return 0;}
    }
    private static int tuneAction(net.minecraft.commands.CommandSourceStack source,Runnable action){
        try{BenchmarkRunSession.requireIdle();action.run();source.sendSuccess(()->Component.literal(com.pp.shaderlod.tuning.AutoTuner.active()?com.pp.shaderlod.tuning.AutoTuner.status():com.pp.shaderlod.benchmark.BenchmarkWorld.status()),false);return 1;}
        catch(RuntimeException failure){source.sendFailure(Component.literal(failure.getMessage()==null?failure.toString():failure.getMessage()));return 0;}
    }
    private static LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> tuneCommands(){
        var root=Commands.literal("tune").executes(c->{c.getSource().sendSuccess(()->Component.literal(com.pp.shaderlod.tuning.AutoTuner.status()),false);return 1;});
        root.then(Commands.literal("status").executes(c->{c.getSource().sendSuccess(()->Component.literal(com.pp.shaderlod.tuning.AutoTuner.status()),false);return 1;}));
        root.then(Commands.literal("start").executes(c->tuneAction(c.getSource(),()->BenchmarkUi.start(300,false)))
                .then(Commands.argument("seconds",IntegerArgumentType.integer(30,3600)).executes(c->tuneAction(c.getSource(),()->BenchmarkUi.start(IntegerArgumentType.getInteger(c,"seconds"),false)))));
        root.then(Commands.literal("full").executes(c->tuneAction(c.getSource(),()->BenchmarkUi.start(3600,true))));
        for(var goal:com.pp.shaderlod.tuning.TuningGoal.values())root.then(Commands.literal(goal.name().toLowerCase(Locale.ROOT))
            .executes(c->tuneAction(c.getSource(),()->BenchmarkUi.start(com.pp.shaderlod.tuning.TuningRequest.full(goal)))));
        root.then(Commands.literal("here").executes(c->tuneAction(c.getSource(),()->com.pp.shaderlod.tuning.AutoTuner.start(300,false)))
                .then(Commands.argument("seconds",IntegerArgumentType.integer(30,3600)).executes(c->tuneAction(c.getSource(),()->com.pp.shaderlod.tuning.AutoTuner.start(IntegerArgumentType.getInteger(c,"seconds"),false)))));
        root.then(Commands.literal("cancel").executes(c->tuneAction(c.getSource(),com.pp.shaderlod.tuning.AutoTuner::cancel)));
        root.then(Commands.literal("undo").executes(c->tuneAction(c.getSource(),com.pp.shaderlod.tuning.AutoTuner::undo)));
        root.then(Commands.literal("demo")
                .then(Commands.literal("open").executes(c->tuneAction(c.getSource(),BenchmarkUi::openDemo)))
                .then(Commands.literal("flythrough").executes(c->tuneAction(c.getSource(),BenchmarkUi::flythrough)))
                .then(Commands.literal("exit").executes(c->tuneAction(c.getSource(),BenchmarkUi::exitDemo))));
        return root;
    }

    private static LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> savedTuningCommands() {
        var root=Commands.literal("tunings").requires(source->!BenchmarkRunSession.owned()).executes(c->{Minecraft.getInstance().setScreen(new SavedTuningsScreen(Minecraft.getInstance().screen));return 1;});
        root.then(Commands.literal("list").executes(c->savedTuningAction(c.getSource(),()->com.pp.shaderlod.tuning.SavedTunings.list().stream()
                .map(p->p.id()+" — "+p.name()+" ["+p.evidence().provenance()+"]").collect(java.util.stream.Collectors.joining("\n")))));
        root.then(Commands.literal("save").then(Commands.argument("name",com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                .executes(c->savedTuningAction(c.getSource(),()->"Saved "+com.pp.shaderlod.tuning.SavedTunings.saveCurrent(com.mojang.brigadier.arguments.StringArgumentType.getString(c,"name")).id()))));
        root.then(Commands.literal("undo").executes(c->savedTuningAction(c.getSource(),()->{com.pp.shaderlod.tuning.SavedTunings.undo();return "Original saved-tuning configuration restored";})));
        root.then(Commands.literal("apply").then(Commands.argument("id",com.mojang.brigadier.arguments.StringArgumentType.word())
                .executes(c->savedTuningAction(c.getSource(),()->{com.pp.shaderlod.tuning.SavedTunings.apply(com.mojang.brigadier.arguments.StringArgumentType.getString(c,"id"));return "Saved tuning applied; Undo available";}))));
        root.then(Commands.literal("export").then(Commands.argument("id",com.mojang.brigadier.arguments.StringArgumentType.word())
                .executes(c->savedTuningAction(c.getSource(),()->com.pp.shaderlod.tuning.SavedTunings.export(com.mojang.brigadier.arguments.StringArgumentType.getString(c,"id")).toString()))));
        return root;
    }
    private static int savedTuningAction(net.minecraft.commands.CommandSourceStack source,java.util.concurrent.Callable<String> action) {
        try{String result=action.call();source.sendSuccess(()->Component.literal(result.isBlank()?"No saved tunings":result),false);return 1;}
        catch(Exception e){source.sendFailure(Component.literal(e.getMessage()==null?e.toString():e.getMessage()));return 0;}
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
            BenchmarkRunSession.requireIdle();
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
