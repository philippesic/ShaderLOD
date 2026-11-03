package com.pp.shaderlod.mixin;

import com.pp.shaderlod.ShadowTraversalEpochs;
import com.pp.shaderlod.ShadowTraversalEpochs.Producer;
import java.util.ArrayList;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkUpdateType;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.CameraMovement;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exact Sodium 0.6.13 manager producers; never cancels or replaces native work. */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class ShadowTraversalEpochsMixin implements ShadowTraversalEpochs.ManagerAccess {
    @Shadow @Final private ChunkRenderer chunkRenderer;
    @Unique private ShadowTraversalEpochs shaderlod$epochs;

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;ILnet/caffeinemc/mods/sodium/client/gl/device/CommandList;)V",
            at = @At("RETURN"), require = 1)
    private void shaderlod$construct(ClientLevel level, int renderDistance, CommandList commands, CallbackInfo ci) {
        if (ShadowTraversalEpochs.enabledAtConstruction()) {
            shaderlod$epochs = new ShadowTraversalEpochs(this, level, chunkRenderer);
        }
    }

    @Unique @Override
    public ShadowTraversalEpochs shaderlod$shadowTraversalEpochs() { return shaderlod$epochs; }

    @Unique private void shaderlod$begin(Producer producer) {
        if (shaderlod$epochs != null) shaderlod$epochs.beginMutation(producer);
    }

    @Unique private void shaderlod$end(Producer producer) {
        if (shaderlod$epochs != null) shaderlod$epochs.endMutation(producer);
    }

    @Inject(method = "onSectionAdded(III)V", at = @At("HEAD"), require = 1)
    private void shaderlod$addedHead(int x, int y, int z, CallbackInfo ci) {
        shaderlod$begin(Producer.SECTION_ADDED);
    }

    @Inject(method = "onSectionAdded(III)V", at = @At("RETURN"), require = 1)
    private void shaderlod$addedReturn(int x, int y, int z, CallbackInfo ci) {
        shaderlod$end(Producer.SECTION_ADDED);
    }

    @Inject(method = "onSectionRemoved(III)V", at = @At("HEAD"), require = 1)
    private void shaderlod$removedHead(int x, int y, int z, CallbackInfo ci) {
        shaderlod$begin(Producer.SECTION_REMOVED);
    }

    @Inject(method = "onSectionRemoved(III)V", at = @At("RETURN"), require = 1)
    private void shaderlod$removedReturn(int x, int y, int z, CallbackInfo ci) {
        shaderlod$end(Producer.SECTION_REMOVED);
    }

    @Inject(method = "scheduleRebuild(IIIZ)V", at = @At("HEAD"), require = 1)
    private void shaderlod$rebuildHead(int x, int y, int z, boolean important, CallbackInfo ci) {
        shaderlod$begin(Producer.REBUILD_SCHEDULED);
    }

    @Inject(method = "scheduleRebuild(IIIZ)V", at = @At("RETURN"), require = 1)
    private void shaderlod$rebuildReturn(int x, int y, int z, boolean important, CallbackInfo ci) {
        shaderlod$end(Producer.REBUILD_SCHEDULED);
    }

    @Inject(method = "updateSectionInfo(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;Lnet/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionInfo;)Z", at = @At("HEAD"), require = 1)
    private void shaderlod$infoHead(RenderSection section, BuiltSectionInfo info, CallbackInfoReturnable<Boolean> ci) {
        shaderlod$begin(Producer.SECTION_INFO);
    }

    @Inject(method = "updateSectionInfo(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;Lnet/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionInfo;)Z", at = @At("RETURN"), require = 1)
    private void shaderlod$infoReturn(RenderSection section, BuiltSectionInfo info, CallbackInfoReturnable<Boolean> ci) {
        shaderlod$end(Producer.SECTION_INFO);
    }

    @Inject(method = "processChunkBuildResults(Ljava/util/ArrayList;)Z", at = @At("HEAD"), require = 1)
    private void shaderlod$resultsHead(ArrayList<BuilderTaskOutput> results, CallbackInfoReturnable<Boolean> ci) {
        shaderlod$begin(Producer.BUILD_RESULTS);
    }

    @Inject(method = "processChunkBuildResults(Ljava/util/ArrayList;)Z", at = @At("RETURN"), require = 1)
    private void shaderlod$resultsReturn(ArrayList<BuilderTaskOutput> results, CallbackInfoReturnable<Boolean> ci) {
        shaderlod$end(Producer.BUILD_RESULTS);
    }

    @Inject(method = "scheduleSort(JZ)V", at = @At("HEAD"), require = 1)
    private void shaderlod$sortHead(long sectionPos, boolean direct, CallbackInfo ci) {
        shaderlod$begin(Producer.SORT_SCHEDULED);
    }

    @Inject(method = "scheduleSort(JZ)V", at = @At("RETURN"), require = 1)
    private void shaderlod$sortReturn(long sectionPos, boolean direct, CallbackInfo ci) {
        shaderlod$end(Producer.SORT_SCHEDULED);
    }

    @Inject(method = "processGFNIMovement(Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/trigger/CameraMovement;)V", at = @At("HEAD"), require = 1)
    private void shaderlod$movementHead(CameraMovement movement, CallbackInfo ci) {
        shaderlod$begin(Producer.SORT_MOVEMENT);
    }

    @Inject(method = "processGFNIMovement(Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/trigger/CameraMovement;)V", at = @At("RETURN"), require = 1)
    private void shaderlod$movementReturn(CameraMovement movement, CallbackInfo ci) {
        shaderlod$end(Producer.SORT_MOVEMENT);
    }

    @Inject(method = "submitSectionTasks(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkJobCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkUpdateType;Z)V", at = @At("HEAD"), require = 1)
    private void shaderlod$submissionHead(ChunkJobCollector collector, ChunkUpdateType type, boolean ignoreEffortCategory, CallbackInfo ci) {
        shaderlod$begin(Producer.TASK_SUBMISSION);
    }

    @Inject(method = "submitSectionTasks(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkJobCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkUpdateType;Z)V", at = @At("RETURN"), require = 1)
    private void shaderlod$submissionReturn(ChunkJobCollector collector, ChunkUpdateType type, boolean ignoreEffortCategory, CallbackInfo ci) {
        shaderlod$end(Producer.TASK_SUBMISSION);
    }

    @Inject(method = "collectChunkBuildResults()Ljava/util/ArrayList;", at = @At("HEAD"), require = 1)
    private void shaderlod$collectionHead(CallbackInfoReturnable<ArrayList<BuilderTaskOutput>> ci) {
        shaderlod$begin(Producer.RESULT_COLLECTION);
    }

    @Inject(method = "collectChunkBuildResults()Ljava/util/ArrayList;", at = @At("RETURN"), require = 1)
    private void shaderlod$collectionReturn(CallbackInfoReturnable<ArrayList<BuilderTaskOutput>> ci) {
        shaderlod$end(Producer.RESULT_COLLECTION);
    }

    @Inject(method = "markGraphDirty()V", at = @At("HEAD"), require = 1)
    private void shaderlod$dirtyHead(CallbackInfo ci) {
        shaderlod$begin(Producer.GRAPH_DIRTY_UNKNOWN);
    }

    @Inject(method = "markGraphDirty()V", at = @At("RETURN"), require = 1)
    private void shaderlod$dirtyReturn(CallbackInfo ci) {
        shaderlod$end(Producer.GRAPH_DIRTY_UNKNOWN);
    }

    @Inject(method = "createTerrainRenderList(Lnet/minecraft/client/Camera;Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;IZ)V",
            at = @At("HEAD"), require = 1)
    private void shaderlod$frame(Camera camera, Viewport viewport, int frame, boolean spectator, CallbackInfo ci) {
        if (shaderlod$epochs != null) shaderlod$epochs.observeTraversalFrame(frame);
    }

    @Inject(method = "destroy()V", at = @At("HEAD"), require = 1)
    private void shaderlod$destroy(CallbackInfo ci) {
        if (shaderlod$epochs != null) shaderlod$epochs.dispose();
    }
}
