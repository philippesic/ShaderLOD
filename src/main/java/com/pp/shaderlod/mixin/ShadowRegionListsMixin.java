package com.pp.shaderlod.mixin;

import com.pp.shaderlod.ShadowListStorage;
import com.pp.shaderlod.ShadowVisibilityListsController;
import java.util.function.Function;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Per-region inner membership isolation; every existing graph traversal still runs. */
@Mixin(value = RenderRegion.class, remap = false)
public abstract class ShadowRegionListsMixin implements ShadowVisibilityListsController.RegionAccess {
    @Shadow @Final private ChunkRenderList renderList;
    @Unique private ShadowListStorage<ChunkRenderList> shaderlod$storage;
    @Unique private static final Function<RenderRegion, ChunkRenderList> shaderlod$factory = ChunkRenderList::new;

    /**
     * @author ShaderLOD
     * @reason Keep shadow membership in a separate owner-local list without skipping upstream updates.
     */
    @Overwrite(remap = false)
    public ChunkRenderList getRenderList() {
        if (!ShadowVisibilityListsController.isActive() || !ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            return renderList;
        }
        if (shaderlod$storage == null) shaderlod$storage = new ShadowListStorage<>(renderList);
        return shaderlod$storage.select(true, true, (RenderRegion) (Object) this, shaderlod$factory);
    }

    @Unique @Override
    public ChunkRenderList shaderlod$originalRegionList() { return renderList; }

    @Unique @Override
    public ChunkRenderList shaderlod$shadowRegionList() {
        return shaderlod$storage == null ? null : shaderlod$storage.shadowOrNull();
    }
}
