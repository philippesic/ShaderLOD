package com.pp.shaderlod.dev;

import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;

/** Explicit developer snapshots only; no hook calls this during a timing arm. */
public final class VisibilityListDiagnostics {
    private static final boolean ENABLED = Boolean.getBoolean("shaderlod.dev.visibilityLists");
    private VisibilityListDiagnostics() {}

    public record Membership(int identity, int generation, int geometryCount, int spriteCount,
                             int entityCount, long geometryHash, long spriteHash, long entityHash) {}
    public record Snapshot(int regionIdentity, Membership original, Membership shadow) {}

    /** Caller owns the render thread and must select a non-timing diagnostic run. */
    public static Snapshot capture(RenderRegion region) {
        if (!ENABLED) return null;
        // Dev-only reflection supports the physical absent-mod baseline too.
        // No timing/render hook invokes this snapshot method.
        try {
            ChunkRenderList original = (ChunkRenderList) region.getClass()
                    .getMethod("shaderlod$originalRegionList").invoke(region);
            ChunkRenderList shadow = (ChunkRenderList) region.getClass()
                    .getMethod("shaderlod$shadowRegionList").invoke(region);
            return new Snapshot(System.identityHashCode(region), membership(original), membership(shadow));
        } catch (NoSuchMethodException absentMod) {
            return new Snapshot(System.identityHashCode(region), membership(region.getRenderList()), null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Visibility-list diagnostic access failed", failure);
        }
    }

    private static Membership membership(ChunkRenderList list) {
        if (list == null) return null;
        return new Membership(System.identityHashCode(list), list.getLastVisibleFrame(),
                list.getSectionsWithGeometryCount(), list.getSectionsWithSpritesCount(), list.getSectionsWithEntitiesCount(),
                hash(list.sectionsWithGeometryIterator(false)), hash(list.sectionsWithSpritesIterator()),
                hash(list.sectionsWithEntitiesIterator()));
    }

    private static long hash(ByteIterator iterator) {
        long result = 0xcbf29ce484222325L;
        if (iterator != null) while (iterator.hasNext()) result = (result ^ iterator.nextByteAsInt()) * 0x100000001b3L;
        return result;
    }
}
