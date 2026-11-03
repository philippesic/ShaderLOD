package com.pp.shaderlod.optimization.mips;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.pp.shaderlod.optimization.mips.BslMipWitnessState.*;

/** Pure physical-graph falsifiers; intentionally no Iris, client, GL or permission supplier. */
class BslMipWitnessStateTest {
    private static Texture texture(int id, int min, boolean history) {
        int format = history ? 32859 : 32849;
        return new Texture(id, format, 4, 2, min, LINEAR, 0, 1000, -1000f, 1000f, 0f,
            33071, 33071, 0, 1f, List.of(new Level(0, 4, 2, format), new Level(1, 2, 1, format), new Level(2, 1, 1, format)));
    }
    private static Sample sample(int id, int min, boolean history) {
        return new Sample(history ? "colortex2" : "colortex1", history ? 2 : 1, id, 0, 0, min, true, texture(id, min, history));
    }
    private static Attachment output(int index, int id) { return new Attachment(index, 36064 + index, 5890, id, 0, 4, 2, id == 71 || id == 72 ? 32859 : 32849); }
    private static Frame frame(boolean candidate) {
        var a = new Stage("composite6", 101, 201, Set.of(1), Set.of(1), sample(63, MIP_LINEAR, false), null, List.of(output(0, 62)), 1);
        var b = new Stage("composite7", 102, 202, candidate ? Set.of() : Set.of(1), Set.of(2),
            sample(62, candidate ? LINEAR : MIP_LINEAR, false), sample(72, LINEAR, true), List.of(output(0, 63), output(1, 71)), candidate ? 1 : 2);
        var c = new Stage("final", 103, 203, Set.of(), Set.of(1), sample(63, MIP_LINEAR, false), null, List.of(output(0, 30)), candidate ? 1 : 2);
        // Observed route: c5 71 -> 72, c6 63 -> 62, c7 62/72 -> 63/71, final reads 63.
        var bridge = new Bridge("composite5", 100, 200, Set.of(), Set.of(), List.of(1, 2),
            sample(71, LINEAR, true), List.of(output(0, 63), output(1, 72)), 0);
        return new Frame(7, 100, candidate, 62, 63, 71, 72, bridge, a, b, c, candidate ? List.of(63) : List.of(63, 62),
            List.of(), List.of(texture(62, LINEAR, false), texture(63, LINEAR, false)));
    }
    private static Frame replace(Frame f, Stage a, Stage b, Stage c, List<Integer> generations, List<Copy> copies, List<Texture> reset) {
        return new Frame(f.epoch(), f.frame(), f.candidate(), f.main1(), f.alt1(), f.main2(), f.alt2(), f.composite5(), a, b, c, generations, copies, reset);
    }
    private static Frame next(Frame f) {
        return new Frame(f.epoch(), f.frame() + 1, f.candidate(), f.main1(), f.alt1(), f.main2(), f.alt2(), f.composite5(), f.composite6(), f.composite7(),
            f.finalPass(), f.generatedTextures(), f.historyCopies(), f.reset());
    }
    @Test void nativeAndC7OnlyCandidateBothPreservePhysicalGraph() {
        assertEquals(List.of(), failures(frame(false)));
        assertEquals(List.of(), failures(frame(true)));
        assertEquals(List.of(63), frame(true).generatedTextures());
        assertTrue(historyJoined(frame(false), next(frame(false))));
        assertFalse(historyJoined(frame(false), next(frame(true))));
        assertFalse(historyJoined(frame(false), frame(false)));
    }
    @Test void planWithoutReturnedGenerationOrExtraGenerationFails() {
        Frame f = frame(false);
        assertFalse(failures(replace(f, f.composite6(), f.composite7(), f.finalPass(), List.of(63), f.historyCopies(), f.reset())).isEmpty());
        f = frame(true);
        assertFalse(failures(replace(f, f.composite6(), f.composite7(), f.finalPass(), List.of(63, 62), f.historyCopies(), f.reset())).isEmpty());
    }
    @Test void bufferNumberCannotStandInForActualSampleOrOutputIdentity() {
        Frame f = frame(false); Stage c = f.finalPass();
        Stage wrongFinal = new Stage(c.name(), c.program(), c.framebuffer(), c.mips(), c.readsAlt(), sample(62, MIP_LINEAR, false), null, c.outputs(), c.rt1GenerationsBeforeDraw());
        assertFalse(failures(replace(f, f.composite6(), f.composite7(), wrongFinal, f.generatedTextures(), f.historyCopies(), f.reset())).isEmpty());
        Stage b = f.composite7();
        Stage feedback = new Stage(b.name(), b.program(), b.framebuffer(), b.mips(), b.readsAlt(), b.color(), b.history(), List.of(output(0, 62), output(1, 71)), b.rt1GenerationsBeforeDraw());
        assertFalse(failures(replace(f, f.composite6(), feedback, c, f.generatedTextures(), f.historyCopies(), f.reset())).isEmpty());
    }
    @Test void nonzeroSamplerOverrideIsRejectedEvenWhenEffectiveFilterMatches() {
        Frame f = frame(false); Stage b = f.composite7(); Sample original = b.color();
        Sample override = new Sample(original.uniform(), original.unit(), original.texture(), 55, 0, MIP_LINEAR, false, original.storage());
        Stage changed = new Stage(b.name(), b.program(), b.framebuffer(), b.mips(), b.readsAlt(), override, b.history(), b.outputs(), b.rt1GenerationsBeforeDraw());
        assertFalse(failures(replace(f, f.composite6(), changed, f.finalPass(), f.generatedTextures(), f.historyCopies(), f.reset())).isEmpty());
    }
    @Test void candidateCannotRemoveC6OrChangePhysicalFlipMetadata() {
        Frame f = frame(true); Stage a = f.composite6(), b = f.composite7();
        Stage noC6Mips = new Stage(a.name(), a.program(), a.framebuffer(), Set.of(), a.readsAlt(), a.color(), null, a.outputs(), a.rt1GenerationsBeforeDraw());
        assertFalse(failures(replace(f, noC6Mips, b, f.finalPass(), f.generatedTextures(), f.historyCopies(), f.reset())).isEmpty());
        Stage wrongFlip = new Stage(b.name(), b.program(), b.framebuffer(), b.mips(), Set.of(), b.color(), b.history(), b.outputs(), b.rt1GenerationsBeforeDraw());
        assertFalse(failures(replace(f, a, wrongFlip, f.finalPass(), f.generatedTextures(), f.historyCopies(), f.reset())).isEmpty());
        Stage wrongFormat = new Stage(b.name(), b.program(), b.framebuffer(), b.mips(), b.readsAlt(), b.color(), b.history(),
            List.of(output(0, 63), new Attachment(1, 36065, 5890, 71, 0, 4, 2, 32849)), b.rt1GenerationsBeforeDraw());
        assertFalse(failures(replace(f, a, wrongFormat, f.finalPass(), f.generatedTextures(), f.historyCopies(), f.reset())).isEmpty());
    }
    private static Frame bridge(Frame f, Bridge bridge) {
        return new Frame(f.epoch(), f.frame(), f.candidate(), f.main1(), f.alt1(), f.main2(), f.alt2(), bridge,
            f.composite6(), f.composite7(), f.finalPass(), f.generatedTextures(), f.historyCopies(), f.reset());
    }
    private static Bridge bridge(Bridge b, Set<Integer> mips, Set<Integer> alt, List<Integer> draws, Sample history,
            List<Attachment> outputs, int generations) {
        return new Bridge(b.name(), b.program(), b.framebuffer(), mips, alt, draws, history, outputs, generations);
    }
    private static void rejectedBridge(Frame f, Bridge changed) {
        Frame wrong = bridge(next(f), changed);
        assertFalse(failures(wrong).isEmpty());
        assertFalse(historyJoined(f, wrong));
    }
    @Test void historyRouteJoinsPriorC7ThroughExecutedC5WithoutNativeCopy() {
        for (boolean candidate : List.of(false, true)) {
            Frame f = frame(candidate), next = next(f);
            assertTrue(f.historyCopies().isEmpty());
            assertEquals(71, f.composite7().outputs().get(1).texture());
            assertEquals(71, next.composite5().history().texture());
            assertEquals(72, next.composite5().outputs().get(1).texture());
            assertEquals(72, next.composite7().history().texture());
            assertNotEquals(f.composite7().outputs().get(1).texture(), next.composite7().history().texture());
            assertTrue(historyJoined(f, next));
        }
    }
    @Test void missingBridgeOrEitherWrongPhysicalEdgeCannotJoinHistory() {
        Frame f = frame(false); Bridge b = f.composite5();
        rejectedBridge(f, null);
        rejectedBridge(f, bridge(b, b.mips(), b.readsAlt(), b.drawBuffers(), null, b.outputs(), 0));
        rejectedBridge(f, bridge(b, b.mips(), Set.of(2), b.drawBuffers(), sample(72, LINEAR, true), b.outputs(), 0));
        rejectedBridge(f, bridge(b, b.mips(), b.readsAlt(), b.drawBuffers(), b.history(), List.of(output(0, 63), output(1, 71)), 0));
        Stage c7 = f.composite7();
        Stage directPrior = new Stage(c7.name(), c7.program(), c7.framebuffer(), c7.mips(), Set.of(), c7.color(),
            sample(71, LINEAR, true), List.of(output(0, 63), output(1, 72)), c7.rt1GenerationsBeforeDraw());
        Frame direct = next(replace(f, f.composite6(), directPrior, f.finalPass(), f.generatedTextures(), List.of(), f.reset()));
        assertFalse(failures(direct).isEmpty());
        assertFalse(historyJoined(f, direct));
    }
    @Test void nativeCopyCannotSubstituteForOrFollowTheBridge() {
        Frame f = frame(false);
        for (Copy copy : List.of(new Copy(71, 71, 4, 2), new Copy(72, 71, 4, 2), new Copy(71, 72, 4, 2))) {
            Frame copied = replace(f, f.composite6(), f.composite7(), f.finalPass(), f.generatedTextures(), List.of(copy), f.reset());
            assertFalse(failures(copied).isEmpty());
            assertFalse(historyJoined(copied, next(f)));
            assertFalse(historyJoined(f, next(copied)));
        }
    }
    @Test void bridgeMustHaveNativeMipDrawAttachmentAndGenerationMetadata() {
        Frame f = frame(false); Bridge b = f.composite5();
        rejectedBridge(f, bridge(b, Set.of(2), b.readsAlt(), b.drawBuffers(), b.history(), b.outputs(), 0));
        rejectedBridge(f, bridge(b, b.mips(), b.readsAlt(), List.of(2, 1), b.history(), b.outputs(), 0));
        rejectedBridge(f, bridge(b, b.mips(), Set.of(2), b.drawBuffers(), b.history(), b.outputs(), 0));
        rejectedBridge(f, bridge(b, b.mips(), b.readsAlt(), b.drawBuffers(), b.history(), b.outputs(), 1));
        rejectedBridge(f, bridge(b, b.mips(), b.readsAlt(), b.drawBuffers(), b.history(), List.of(output(0, 63)), 0));
        rejectedBridge(f, bridge(b, b.mips(), b.readsAlt(), b.drawBuffers(), b.history(), List.of(output(0, 62), output(1, 72)), 0));
        rejectedBridge(f, bridge(b, b.mips(), b.readsAlt(), b.drawBuffers(), b.history(),
            List.of(output(0, 63), new Attachment(1, 36065, 5890, 72, 0, 4, 2, 32849)), 0));
        rejectedBridge(f, bridge(b, b.mips(), b.readsAlt(), b.drawBuffers(), b.history(),
            List.of(output(0, 63), new Attachment(1, 36065, 5890, 72, 1, 4, 2, 32859)), 0));
        rejectedBridge(f, bridge(b, b.mips(), b.readsAlt(), b.drawBuffers(), b.history(),
            List.of(output(0, 63), new Attachment(1, 36065, 5890, 72, 0, 2, 2, 32859)), 0));
    }
    @Test void bridgeSamplerMustBeActualNativeLinearHistoryStorage() {
        Frame f = frame(false); Bridge b = f.composite5(); Sample h = b.history();
        rejectedBridge(f, bridge(b, b.mips(), b.readsAlt(), b.drawBuffers(), sample(71, MIP_LINEAR, true), b.outputs(), 0));
        rejectedBridge(f, bridge(b, b.mips(), b.readsAlt(), b.drawBuffers(),
            new Sample(h.uniform(), h.unit(), 71, 55, 0, LINEAR, false, h.storage()), b.outputs(), 0));
        rejectedBridge(f, bridge(b, b.mips(), b.readsAlt(), b.drawBuffers(),
            new Sample(h.uniform(), h.unit(), 71, 0, 55, LINEAR, false, h.storage()), b.outputs(), 0));
        rejectedBridge(f, bridge(b, b.mips(), b.readsAlt(), b.drawBuffers(),
            new Sample(h.uniform(), h.unit(), 71, 0, 0, LINEAR, true, texture(72, LINEAR, true)), b.outputs(), 0));
        rejectedBridge(f, bridge(b, b.mips(), b.readsAlt(), b.drawBuffers(), sample(71, LINEAR, false), b.outputs(), 0));
    }
    @Test void adjacentFrameNumbersAndReusedTextureIdsCannotCrossEpochOrProgram() {
        Frame f = frame(false), n = next(f);
        Frame offEpoch = new Frame(n.epoch() + 1, n.frame(), n.candidate(), n.main1(), n.alt1(), n.main2(), n.alt2(), n.composite5(),
            n.composite6(), n.composite7(), n.finalPass(), n.generatedTextures(), n.historyCopies(), n.reset());
        assertEquals(List.of(), failures(offEpoch));
        assertFalse(historyJoined(f, offEpoch));
        Bridge b = n.composite5();
        Frame recompiled = bridge(n, new Bridge(b.name(), b.program() + 1, b.framebuffer(), b.mips(), b.readsAlt(), b.drawBuffers(), b.history(), b.outputs(), 0));
        assertFalse(historyJoined(f, recompiled));
        Frame retargeted = bridge(n, new Bridge(b.name(), b.program(), b.framebuffer() + 1, b.mips(), b.readsAlt(), b.drawBuffers(), b.history(), b.outputs(), 0));
        assertFalse(historyJoined(f, retargeted));
        assertFalse(historyJoined(f, next(n)));
    }
    @Test void mipsMustExistOnGeneratedPhysicalTextureAndResetBothImages() {
        Frame f = frame(false); Texture t = texture(63, MIP_LINEAR, false);
        var incomplete = new Texture(t.id(), t.format(), t.width(), t.height(), t.min(), t.mag(), t.baseLevel(), t.maxLevel(),
            t.minLod(), t.maxLod(), t.lodBias(), t.wrapS(), t.wrapT(), t.compareMode(), t.anisotropy(), List.of(t.levels().getFirst()));
        assertFalse(incomplete.completeMipChain());
        assertFalse(reset(List.of(texture(63, LINEAR, false), texture(62, MIP_LINEAR, false)), 63, 62));
        assertFalse(failures(replace(f, f.composite6(), f.composite7(), f.finalPass(), f.generatedTextures(), f.historyCopies(), List.of())).isEmpty());
    }
    @Test void baseClampAndStorageFormatChangesAreNotNativeSampling() {
        Texture t = texture(63, LINEAR, false);
        var changed = new Texture(t.id(), t.format(), t.width(), t.height(), t.min(), t.mag(), 1, t.maxLevel(), t.minLod(), t.maxLod(),
            t.lodBias(), t.wrapS(), t.wrapT(), t.compareMode(), t.anisotropy(), t.levels());
        assertFalse(changed.nativeSampling());
        var wrongMipFormat = new Texture(t.id(), t.format(), t.width(), t.height(), t.min(), t.mag(), t.baseLevel(), t.maxLevel(), t.minLod(), t.maxLod(),
            t.lodBias(), t.wrapS(), t.wrapT(), t.compareMode(), t.anisotropy(), List.of(t.levels().getFirst(), new Level(1, 2, 1, 0)));
        assertFalse(wrongMipFormat.completeMipChain());
    }
    @Test void allPublishedNestedCollectionsAreImmutableCopies() {
        Frame f = frame(false); var values = new ArrayList<>(f.generatedTextures());
        Frame copied = replace(f, f.composite6(), f.composite7(), f.finalPass(), values, f.historyCopies(), f.reset());
        values.clear(); assertEquals(List.of(63, 62), copied.generatedTextures());
        assertThrows(UnsupportedOperationException.class, () -> copied.generatedTextures().clear());
        assertThrows(UnsupportedOperationException.class, () -> copied.composite5().mips().clear());
        assertThrows(UnsupportedOperationException.class, () -> copied.composite5().readsAlt().clear());
        assertThrows(UnsupportedOperationException.class, () -> copied.composite5().drawBuffers().clear());
        assertThrows(UnsupportedOperationException.class, () -> copied.composite5().outputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> copied.composite7().mips().clear());
        assertThrows(UnsupportedOperationException.class, () -> copied.composite7().color().storage().levels().clear());
        assertThrows(UnsupportedOperationException.class, () -> failures(f).add("fabricated"));
    }
    private static Frame bothFrame(int number) {
        Frame f=frame(true);Stage a=f.composite6(),b=f.composite7(),c=f.finalPass();
        a=new Stage(a.name(),a.program(),a.framebuffer(),Set.of(),a.readsAlt(),sample(63,LINEAR,false),null,a.outputs(),0);
        b=new Stage(b.name(),b.program(),b.framebuffer(),Set.of(),b.readsAlt(),b.color(),b.history(),b.outputs(),0);
        c=new Stage(c.name(),c.program(),c.framebuffer(),Set.of(),c.readsAlt(),sample(63,LINEAR,false),null,c.outputs(),0);
        return new Frame(f.epoch(),number,true,f.main1(),f.alt1(),f.main2(),f.alt2(),f.composite5(),a,b,c,List.of(),List.of(),f.reset(),true);
    }
    @Test void scopedC6AndC7SuppressionHasNoGenerationAndPreservesHistoryBridge() {
        Frame f=bothFrame(100);
        assertEquals(List.of(),failures(f));
        assertTrue(historyJoined(f,bothFrame(101)));
        assertFalse(historyJoined(frame(true),bothFrame(101)));
        assertFalse(failures(new Frame(f.epoch(),f.frame(),f.candidate(),f.main1(),f.alt1(),f.main2(),f.alt2(),f.composite5(),
            f.composite6(),f.composite7(),f.finalPass(),List.of(63),List.of(),f.reset(),true)).isEmpty());
        assertFalse(failures(new Frame(f.epoch(),f.frame(),f.candidate(),f.main1(),f.alt1(),f.main2(),f.alt2(),f.composite5(),
            f.composite6(),f.composite7(),f.finalPass(),List.of(),List.of(),f.reset(),false)).isEmpty());
    }

}
