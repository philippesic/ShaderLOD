package com.pp.shaderlod.dev.timing;

import com.pp.shaderlod.dev.VisibilityListDiagnostics;
import com.pp.shaderlod.dev.VisibilityListDiagnostics.Membership;
import com.pp.shaderlod.dev.VisibilityListDiagnostics.Snapshot;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;

/** Sparse, explicitly armed development snapshots. Never authorizes reuse or changes rendering. */
public final class VisibilityListPhaseObserver {
    private static final boolean ENABLED = Boolean.getBoolean("shaderlod.dev.visibilityLists");
    private static final int MAX_PAIRS = 8, SAMPLE_INTERVAL = 30, MAX_OUTER_REGIONS = 512;
    private static final int MAX_INNER_REGIONS = 8, MAX_SPRITE_SECTIONS = 4096, MAX_ERRORS = 16;
    private static final long FNV_START = 0xcbf29ce484222325L, FNV_PRIME = 0x100000001b3L;
    private static final List<Pair> PAIRS = new ArrayList<>();
    private static final List<String> ERRORS = new ArrayList<>();
    private static boolean armed, shadowFieldChecked, controllerChecked, controllerKnown, regionAccessorChecked;
    private static Field shadowField;
    private static Method controllerActive, regionShadowAccessor;
    private static String resolvedShadowField = "not inspected", controllerAvailability = "not inspected";
    private static Pending pending;
    private static long lastSampleFrame = Long.MIN_VALUE, attempts, phaseCalls, errors, unmatchedShadowSamples;

    private VisibilityListPhaseObserver() {}
    public static boolean enabled() { return ENABLED; }

    /** Explicit warmup boundary; a property alone never starts sampling. Client/render thread only. */
    public static void beginMeasurement() {
        if (!ENABLED) return;
        PAIRS.clear(); ERRORS.clear(); pending = null; armed = true;
        attempts = phaseCalls = errors = unmatchedShadowSamples = 0; lastSampleFrame = Long.MIN_VALUE;
    }

    public static void phaseBoundary(Object manager, Object world, SortedRenderLists mainLists,
                                     int managerFrame, int traversalFrame, boolean shadowPhase, long renderFrame) {
        if (!ENABLED || !armed || PAIRS.size() >= MAX_PAIRS || (attempts >= MAX_PAIRS && pending == null)) return;
        phaseCalls++;
        if (pending != null && (pending.manager != manager || pending.world != world
                || renderFrame < pending.renderFrame || renderFrame - pending.renderFrame > 2)) {
            unmatchedShadowSamples++;
            pending = null;
        }
        if (shadowPhase) {
            if (pending != null) {
                unmatchedShadowSamples++;
                pending = null;
                error("Sample discarded: another shadow traversal occurred before a main traversal");
            }
            if (lastSampleFrame != Long.MIN_VALUE && renderFrame - lastSampleFrame < SAMPLE_INTERVAL) return;
            lastSampleFrame = renderFrame;
            attempts++;
            try {
                SortedRenderLists shadowLists = shadowLists(manager);
                if (shadowLists == null) {
                    error("Iris shadow outer lists unavailable at shadow RETURN");
                    return;
                }
                OuterCapture shadow = outer(shadowLists), main = outer(mainLists);
                List<RegionBefore> selected = new ArrayList<>();
                for (ChunkRenderList list : shadow.lists) {
                    if (selected.size() == MAX_INNER_REGIONS) break;
                    RenderRegion region = list.getRegion();
                    selected.add(new RegionBefore(region, list, membership(list), VisibilityListDiagnostics.capture(region)));
                }
                pending = new Pending(manager, world, renderFrame, managerFrame, traversalFrame,
                        shadow, main, selected, spriteModel(main, shadow, managerFrame, true));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                error("Shadow snapshot unavailable: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
            return;
        }
        if (pending == null) return;
        Pending before = pending;
        pending = null;
        try {
            OuterCapture mainAfter = outer(mainLists), shadowAfter = outer(shadowLists(manager));
            List<RegionPair> regions = new ArrayList<>();
            for (RegionBefore region : before.regions) {
                Snapshot after = VisibilityListDiagnostics.capture(region.region);
                Membership sourceAfter = membership(region.source);
                Boolean overlaps = find(mainAfter, region.region) != null ? Boolean.TRUE
                        : mainAfter.summary.complete() ? Boolean.FALSE : null;
                boolean available = region.regionBefore != null && after != null;
                boolean separate = available && region.regionBefore.shadow() != null && region.regionBefore.original() != null
                        && region.regionBefore.shadow().identity() != region.regionBefore.original().identity();
                regions.add(new RegionPair(System.identityHashCode(region.region), region.region.getChunkX(),
                        region.region.getChunkY(), region.region.getChunkZ(), overlaps,
                        region.sourceBefore, sourceAfter, region.regionBefore, after,
                        separate, separate && Objects.equals(region.regionBefore.shadow(), after.shadow()),
                        available && !Objects.equals(region.regionBefore.original(), after.original()),
                        !Objects.equals(region.sourceBefore, sourceAfter)));
            }
            PAIRS.add(new Pair(before.renderFrame, renderFrame, before.managerFrame, managerFrame,
                    before.traversalFrame, traversalFrame, System.identityHashCode(manager), System.identityHashCode(world),
                    before.shadow.summary, shadowAfter.summary, before.main.summary, mainAfter.summary,
                    before.shadow.summary.identity() == shadowAfter.summary.identity(),
                    before.shadow.summary.identityOrderHash() == shadowAfter.summary.identityOrderHash(),
                    before.shadow.summary.sourceCountsGenerationHash() == shadowAfter.summary.sourceCountsGenerationHash(),
                    List.copyOf(regions), before.spriteModel, spriteModel(mainAfter, shadowAfter, managerFrame, false),
                    "Observed phase fingerprints/model only; no traversal-reuse, sprite-consumer or GPU fidelity authorization"));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            error("Main snapshot unavailable: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private static SortedRenderLists shadowLists(Object manager) throws ReflectiveOperationException {
        if (!shadowFieldChecked) {
            shadowFieldChecked = true;
            // Pinned Iris declares @Unique shadowRenderLists. Some merged naming conventions prefix iris$.
            for (String name : new String[] {"iris$shadowRenderLists", "shadowRenderLists"}) {
                try {
                    Field candidate = manager.getClass().getDeclaredField(name);
                    if (candidate.getType() != SortedRenderLists.class) continue;
                    candidate.setAccessible(true);
                    shadowField = candidate;
                    resolvedShadowField = name;
                    break;
                } catch (NoSuchFieldException ignored) { /* Only audited names are supported. */ }
            }
            if (shadowField == null) resolvedShadowField = "unavailable; audited field names not present";
        }
        return shadowField == null ? null : (SortedRenderLists) shadowField.get(manager);
    }

    private static OuterCapture outer(SortedRenderLists lists) {
        if (lists == null) return new OuterCapture(new OuterSummary(0, false, 0, 0, 0, 0,
                "outer list unavailable"), List.of());
        List<ChunkRenderList> selected = new ArrayList<>();
        var iterator = lists.iterator(false);
        long identities = FNV_START, coordinates = FNV_START, source = FNV_START;
        while (iterator.hasNext() && selected.size() < MAX_OUTER_REGIONS) {
            ChunkRenderList list = iterator.next();
            selected.add(list);
            RenderRegion region = list.getRegion();
            identities = hash(hash(identities, System.identityHashCode(region)), System.identityHashCode(list));
            coordinates = hash(hash(hash(coordinates, region.getChunkX()), region.getChunkY()), region.getChunkZ());
            source = hash(hash(hash(hash(source, list.getLastVisibleFrame()), list.getSectionsWithGeometryCount()),
                    list.getSectionsWithSpritesCount()), list.getSectionsWithEntitiesCount());
        }
        boolean complete = !iterator.hasNext();
        return new OuterCapture(new OuterSummary(System.identityHashCode(lists), complete, selected.size(),
                identities, coordinates, source, complete ? "complete bounded outer scan" : "outer region limit exceeded"), selected);
    }

    private static ChunkRenderList find(OuterCapture outer, RenderRegion region) {
        for (ChunkRenderList list : outer.lists) if (list.getRegion() == region) return list;
        return null;
    }

    /** Read-only model of section membership supplied to tickVisibleRenders; never marks actual sprites active. */
    private static SpriteModel spriteModel(OuterCapture main, OuterCapture shadow, int managerFrame, boolean shadowPhase)
            throws ReflectiveOperationException {
        Boolean active = storageActive();
        long chosenHash = FNV_START, currentSharedHash = FNV_START;
        int chosenSections = 0, sharedSections = 0, shadowChoices = 0, fallbackChoices = 0, regions = 0;
        boolean complete = main.summary.complete() && shadow.summary.complete() && active != null;
        for (ChunkRenderList original : main.lists) {
            RenderRegion region = original.getRegion();
            ChunkRenderList regionShadow = Boolean.TRUE.equals(active) ? regionShadow(region) : null;
            ChunkRenderList selected = Boolean.TRUE.equals(active) && shadowPhase && regionShadow != null
                    && regionShadow.getLastVisibleFrame() == managerFrame ? regionShadow : original;
            // Current-traversal shared-array hypothesis, independently based on actual Iris shadow outer membership.
            ChunkRenderList currentShadow = find(shadow, region);
            ChunkRenderList shared = shadowPhase && currentShadow != null
                    && currentShadow.getLastVisibleFrame() == managerFrame ? currentShadow : original;
            int nextChosen = chosenSections + selected.getSectionsWithSpritesCount();
            int nextShared = sharedSections + shared.getSectionsWithSpritesCount();
            if (nextChosen > MAX_SPRITE_SECTIONS || nextShared > MAX_SPRITE_SECTIONS) { complete = false; break; }
            if (selected == regionShadow && regionShadow != null) shadowChoices++; else fallbackChoices++;
            if (Boolean.TRUE.equals(active) && regionShadowAccessor == null) complete = false;
            chosenHash = spriteHash(chosenHash, region, selected);
            currentSharedHash = spriteHash(currentSharedHash, region, shared);
            chosenSections = nextChosen; sharedSections = nextShared; regions++;
        }
        return new SpriteModel(active, shadowPhase, managerFrame, complete, regions, chosenSections, sharedSections,
                shadowChoices, fallbackChoices, chosenHash, currentSharedHash,
                complete ? chosenHash == currentSharedHash && chosenSections == sharedSections : null,
                "Modeled section indices only, not observed markSpriteActive calls or animated texture contents; "
                        + "shared hypothesis uses this traversal, not alternate prior baseline histories");
    }

    private static Boolean storageActive() throws ReflectiveOperationException {
        if (!controllerChecked) {
            controllerChecked = true;
            try {
                Class<?> controller = Class.forName("com.pp.shaderlod.ShadowVisibilityListsController", false,
                        VisibilityListPhaseObserver.class.getClassLoader());
                controllerActive = controller.getMethod("isActive");
                controllerAvailability = "public ShaderLOD isActive() observed";
                controllerKnown = true;
            } catch (ClassNotFoundException absentMod) {
                controllerAvailability = "ShaderLOD class absent; shared baseline";
                controllerKnown = true;
            }
        }
        if (!controllerKnown) return null;
        if (controllerActive == null) return false;
        Object value = controllerActive.invoke(null);
        return value instanceof Boolean bool ? bool : null;
    }

    private static ChunkRenderList regionShadow(RenderRegion region) throws ReflectiveOperationException {
        if (!regionAccessorChecked) {
            regionAccessorChecked = true;
            try { regionShadowAccessor = region.getClass().getMethod("shaderlod$shadowRegionList"); }
            catch (NoSuchMethodException absentStorage) { return null; }
        }
        return regionShadowAccessor == null ? null : (ChunkRenderList) regionShadowAccessor.invoke(region);
    }

    private static long spriteHash(long value, RenderRegion region, ChunkRenderList selected) {
        value = hash(hash(hash(value, region.getChunkX()), region.getChunkY()), region.getChunkZ());
        value = hash(value, selected.getSectionsWithSpritesCount());
        ByteIterator iterator = selected.sectionsWithSpritesIterator();
        if (iterator != null) while (iterator.hasNext()) value = hash(value, iterator.nextByteAsInt());
        return value;
    }

    private static Membership membership(ChunkRenderList list) {
        if (list == null) return null;
        return new Membership(System.identityHashCode(list), list.getLastVisibleFrame(), list.getSectionsWithGeometryCount(),
                list.getSectionsWithSpritesCount(), list.getSectionsWithEntitiesCount(),
                hash(list.sectionsWithGeometryIterator(false)), hash(list.sectionsWithSpritesIterator()),
                hash(list.sectionsWithEntitiesIterator()));
    }
    private static long hash(ByteIterator iterator) {
        long value = FNV_START;
        if (iterator != null) while (iterator.hasNext()) value = hash(value, iterator.nextByteAsInt());
        return value;
    }
    private static long hash(long value, int input) { return (value ^ input) * FNV_PRIME; }
    private static void error(String text) {
        errors++;
        if (ERRORS.size() < MAX_ERRORS) ERRORS.add(text == null ? "unknown observer failure" : text);
    }

    public static void endMeasurement() {
        if (!ENABLED) return;
        armed = false;
        if (pending != null) unmatchedShadowSamples++;
        pending = null; // Release borrowed live world/region/list references.
    }

    public static Report snapshot() {
        return new Report(ENABLED, armed, "diagnostic only; exclude from all timing profiles", MAX_PAIRS,
                SAMPLE_INTERVAL, MAX_OUTER_REGIONS, MAX_INNER_REGIONS, MAX_SPRITE_SECTIONS,
                phaseCalls, attempts, PAIRS.size(), unmatchedShadowSamples, errors,
                resolvedShadowField, controllerAvailability, pending == null ? null : pending.renderFrame,
                List.copyOf(PAIRS), List.copyOf(ERRORS));
    }

    private record OuterCapture(OuterSummary summary, List<ChunkRenderList> lists) {}
    private record RegionBefore(RenderRegion region, ChunkRenderList source, Membership sourceBefore, Snapshot regionBefore) {}
    private record Pending(Object manager, Object world, long renderFrame, int managerFrame, int traversalFrame,
                           OuterCapture shadow, OuterCapture main, List<RegionBefore> regions, SpriteModel spriteModel) {}
    public record OuterSummary(int identity, boolean complete, int capturedRegions, long identityOrderHash,
                               long regionCoordinateOrderHash, long sourceCountsGenerationHash, String availability) {}
    public record RegionPair(int regionIdentity, int chunkX, int chunkY, int chunkZ, Boolean mainOuterOverlap,
                             Membership sourceBefore, Membership sourceAfter,
                             Snapshot regionBefore, Snapshot regionAfter,
                             boolean separateShadowObjectObserved, boolean shadowFingerprintUnchanged,
                             boolean originalFingerprintChanged, boolean borrowedShadowSourceFingerprintChanged) {}
    public record SpriteModel(Boolean storageActive, boolean shadowPhase, int managerLastUpdatedFrame,
                              boolean complete, int scannedMainRegions, int modeledSections, int currentSharedHypothesisSections,
                              int shadowGenerationChoices, int originalFallbackChoices,
                              long modeledSectionOrderHash, long currentSharedHypothesisOrderHash,
                              Boolean matchesCurrentTraversalSharedHypothesis, String scope) {}
    public record Pair(long shadowRenderFrame, long mainRenderFrame, int shadowManagerFrame, int mainManagerFrame,
                       int shadowTraversalFrame, int mainTraversalFrame, int managerIdentity, int worldIdentity,
                       OuterSummary shadowOuterBefore, OuterSummary shadowOuterAfter,
                       OuterSummary mainOuterBefore, OuterSummary mainOuterAfter,
                       boolean shadowOuterIdentityUnchanged, boolean shadowOuterIdentityOrderUnchanged,
                       boolean shadowOuterCountsGenerationHashUnchanged, List<RegionPair> regions,
                       SpriteModel shadowSpriteModel, SpriteModel mainSpriteModel, String scope) {}
    public record Report(boolean enabled, boolean armed, String scope, int maximumPairs, int renderFrameInterval,
                         int maximumOuterRegions, int maximumInnerRegions, int maximumSpriteSections,
                         long eligiblePhaseCalls, long attemptedShadowSamples, int completedPairs,
                         long unmatchedShadowSamples, long captureErrors, String resolvedIrisShadowField,
                         String storageControllerAvailability, Long pendingShadowRenderFrame,
                         List<Pair> pairs, List<String> errors) {}
}
