package com.pp.shaderlod.mixin;

import com.pp.shaderlod.ShadowVisibilityListsController;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve which original main-container region arrays shadow setup uses for sprite ticking. */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class ShadowSpriteListsMixin {
    @Shadow private int lastUpdatedFrame;

    @Redirect(method = "tickVisibleRenders", at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderList;sectionsWithSpritesIterator()Lnet/caffeinemc/mods/sodium/client/util/iterator/ByteIterator;"), remap = false)
    private ByteIterator shaderlod$originalSpriteMembership(ChunkRenderList original) {
        if (!ShadowVisibilityListsController.isActive() || !ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            return original.sectionsWithSpritesIterator();
        }
        ChunkRenderList shadow = ((ShadowVisibilityListsController.RegionAccess) original.getRegion())
                .shaderlod$shadowRegionList();
        if (shadow != null && ShadowVisibilityListsController.useShadowSprites(true, true,
                true, shadow.getLastVisibleFrame(), lastUpdatedFrame)) {
            return shadow.sectionsWithSpritesIterator();
        }
        return original.sectionsWithSpritesIterator();
    }
}
