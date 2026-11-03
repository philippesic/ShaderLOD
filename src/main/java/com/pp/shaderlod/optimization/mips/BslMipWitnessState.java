package com.pp.shaderlod.optimization.mips;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Pure physical-identity checks. Passing these checks never grants optimization permission. */
public final class BslMipWitnessState {
    private BslMipWitnessState() {}
    public static final int LINEAR = 9729, MIP_LINEAR = 9987;
    public record Level(int level, int width, int height, int format) {}
    public record Texture(int id, int format, int width, int height, int min, int mag,
            int baseLevel, int maxLevel, float minLod, float maxLod, float lodBias,
            int wrapS, int wrapT, int compareMode, float anisotropy, List<Level> levels) {
        public Texture { levels = List.copyOf(levels); }
        public boolean nativeSampling() {
            return id > 0 && width > 0 && height > 0 && (min == LINEAR || min == MIP_LINEAR)
                && mag == LINEAR && baseLevel == 0 && maxLevel == 1000
                && minLod == -1000f && maxLod == 1000f && lodBias == 0f
                && wrapS == 33071 && wrapT == 33071 && compareMode == 0 && anisotropy == 1f;
        }
        public boolean completeMipChain() {
            int w = width, h = height, n = 0;
            do {
                if (n >= levels.size()) return false;
                Level l = levels.get(n);
                if (l.level != n || l.width != w || l.height != h || l.format != format) return false;
                if (w == 1 && h == 1) return true;
                w = Math.max(1, w / 2); h = Math.max(1, h / 2); n++;
            } while (n < 32);
            return false;
        }
    }
    /** nativeSampler includes the real bound GL sampler object, not just Iris's intended binding. */
    public record Sample(String uniform, int unit, int texture, int sampler, int declaredSampler,
            int effectiveMin, boolean nativeSampler, Texture storage) {}
    public record Attachment(int drawIndex, int attachment, int objectType, int texture, int level,
            int width, int height, int format) {}
    public record Stage(String name, int program, int framebuffer, Set<Integer> mips, Set<Integer> readsAlt,
            Sample color, Sample history, List<Attachment> outputs, int rt1GenerationsBeforeDraw) {
        public Stage { mips = Set.copyOf(mips); readsAlt = Set.copyOf(readsAlt); outputs = List.copyOf(outputs); }
    }
    /** Physical c5 route only. No active colortex1 uniform or GLSL/pixel pass-through is inferred. */
    public record Bridge(String name, int program, int framebuffer, Set<Integer> mips, Set<Integer> readsAlt,
            List<Integer> drawBuffers, Sample history, List<Attachment> outputs, int rt1GenerationsBeforeDraw) {
        public Bridge {
            mips = Set.copyOf(mips); readsAlt = Set.copyOf(readsAlt);
            drawBuffers = List.copyOf(drawBuffers); outputs = List.copyOf(outputs);
        }
    }
    public record Copy(int source, int destination, int width, int height) {}
    public record Frame(long epoch, int frame, boolean candidate, int main1, int alt1, int main2, int alt2, Bridge composite5,
            Stage composite6, Stage composite7, Stage finalPass, List<Integer> generatedTextures,
            List<Copy> historyCopies, List<Texture> reset, boolean c6Candidate) {
        public Frame { generatedTextures = List.copyOf(generatedTextures); historyCopies = List.copyOf(historyCopies); reset = List.copyOf(reset); }
        public Frame(long epoch, int frame, boolean candidate, int main1, int alt1, int main2, int alt2, Bridge composite5,
                Stage composite6, Stage composite7, Stage finalPass, List<Integer> generatedTextures,
                List<Copy> historyCopies, List<Texture> reset) {
            this(epoch,frame,candidate,main1,alt1,main2,alt2,composite5,composite6,composite7,finalPass,generatedTextures,historyCopies,reset,false);
        }
    }
    public static List<String> failures(Frame f) {
        var errors = new ArrayList<String>();
        Bridge bridge = f.composite5;
        Stage a = f.composite6, b = f.composite7, c = f.finalPass;
        if (bridge == null || bridge.history == null || a == null || b == null || c == null) return List.of("incomplete c5 bridge/c6/c7/final draw stream");
        if (f.epoch <= 0) errors.add("observation epoch absent");
        if (!a.name.equals("composite6") || !b.name.equals("composite7") || !c.name.equals("final")) errors.add("pass roles changed");
        if (f.main1 <= 0 || f.alt1 <= 0 || f.main1 == f.alt1 || f.main2 <= 0 || f.alt2 <= 0 || f.main2 == f.alt2
                || f.main1 == f.main2 || f.main1 == f.alt2 || f.alt1 == f.main2 || f.alt1 == f.alt2) errors.add("physical storage aliases");
        if (f.c6Candidate && !f.candidate) errors.add("c6 experiment requires c7 candidate");
        if (!a.mips.equals(f.c6Candidate ? Set.of() : Set.of(1)) || !b.mips.equals(f.candidate ? Set.of() : Set.of(1)) || !c.mips.isEmpty()) errors.add("mip metadata changed");
        int p = a.color.texture, q = b.color.texture;
        if (!bridge.name.equals("composite5") || bridge.program <= 0 || bridge.framebuffer <= 0
                || !bridge.mips.isEmpty() || !bridge.drawBuffers.equals(List.of(1, 2))
                || bridge.rt1GenerationsBeforeDraw != 0) errors.add("native c5 bridge role/mip/draw metadata changed");
        Sample h = bridge.history;
        if (!h.uniform.equals("colortex2") || h.texture != f.main2
                || h.texture != selected(bridge.readsAlt, 2, f.main2, f.alt2)
                || !outputs(bridge.outputs, List.of(p, f.alt2), h.storage)
                || bridge.outputs.size() != 2 || bridge.outputs.getFirst().format != 32849
                || bridge.outputs.get(1).format != 32859) errors.add("c5 physical history input/MRT bridge changed");
        if (!h.nativeSampler || h.sampler != 0 || h.declaredSampler != 0 || h.texture != h.storage.id
                || !h.storage.nativeSampling() || h.effectiveMin != LINEAR || h.storage.min != LINEAR
                || h.storage.format != 32859) errors.add("c5 history sampler override or native sampling changed");
        if (b.history == null || b.history.texture != f.alt2
                || bridge.outputs.size() != 2 || bridge.outputs.get(1).texture != b.history.texture)
            errors.add("c5 output not joined to same-frame c7 history input");
        if (!pair(p, q, f.main1, f.alt1) || c.color.texture != p) errors.add("P/Q/final input join failed");
        if (p != selected(a.readsAlt, 1, f.main1, f.alt1) || q != selected(b.readsAlt, 1, f.main1, f.alt1)
                || c.color.texture != selected(c.readsAlt, 1, f.main1, f.alt1)) errors.add("actual color sampler differs from stageReadsFromAlt");
        if (!outputs(a, List.of(q)) || b.history == null || !outputs(b, List.of(p, opposite(b.history.texture, f.main2, f.alt2)))) errors.add("physical output/history attachment join failed");
        if (b.history == null || b.history.texture != selected(b.readsAlt, 2, f.main2, f.alt2)) errors.add("actual history input differs from stageReadsFromAlt");
        if (a.outputs.size() != 1 || a.outputs.getFirst().format != 32849 || b.outputs.size() != 2
                || b.outputs.getFirst().format != 32849 || b.outputs.get(1).format != 32859) errors.add("native output RGB8/RGBA16 storage changed");
        if (c.outputs.size() != 1 || !outputs(c, List.of(c.outputs.getFirst().texture)) || c.outputs.getFirst().texture <= 0
                || c.outputs.getFirst().texture == p || c.outputs.getFirst().texture == q
                || c.outputs.getFirst().texture == f.main2 || c.outputs.getFirst().texture == f.alt2) errors.add("final output aliases color/history or is absent");
        for (Stage s : List.of(a, b, c)) {
            for (Sample sample : s.history == null ? List.of(s.color) : List.of(s.color, s.history)) {
                if (!sample.nativeSampler || sample.sampler != 0 || sample.declaredSampler != 0
                        || sample.texture != sample.storage.id || !sample.storage.nativeSampling()) errors.add(s.name + " sampler override or native sampling changed");
            }
        }
        if (a.color.effectiveMin != (f.c6Candidate ? LINEAR : MIP_LINEAR) || b.color.effectiveMin != (f.candidate ? LINEAR : MIP_LINEAR)
                || c.color.effectiveMin != (f.c6Candidate ? LINEAR : MIP_LINEAR) || (b.history != null && b.history.effectiveMin != LINEAR)) errors.add("sampled min filters changed");
        if ((!f.c6Candidate && (!a.color.storage.completeMipChain() || !c.color.storage.completeMipChain()))
                || (!f.candidate && !b.color.storage.completeMipChain())) errors.add("generated physical mip extents incomplete");
        if (a.color.storage.format != 32849 || b.color.storage.format != 32849 || c.color.storage.format != 32849
                || (b.history != null && b.history.storage.format != 32859)) errors.add("native RGB8/RGBA16 formats changed");
        List<Integer> expected = f.c6Candidate ? List.of() : f.candidate ? List.of(p) : List.of(p, q);
        if (!f.generatedTextures.equals(expected) || a.rt1GenerationsBeforeDraw != (f.c6Candidate ? 0 : 1)
                || b.rt1GenerationsBeforeDraw != expected.size() || c.rt1GenerationsBeforeDraw != expected.size()) errors.add("executed RT1 mip generation stream differs");
        if (!reset(f.reset, f.main1, f.alt1)) errors.add("final RT1 reset missing or nonnative");
        if (!f.historyCopies.isEmpty()) errors.add("unexpected native RT2 copy in c5 history bridge graph");
        return List.copyOf(errors);
    }
    public static boolean historyJoined(Frame previous, Frame current) {
        // Both edges need an executed, valid bridge; a direct c7 input or a copy cannot substitute.
        if (!failures(previous).isEmpty() || !failures(current).isEmpty()) return false;
        int output = previous.composite7.outputs.get(1).texture;
        Bridge bridge = current.composite5;
        return previous.epoch == current.epoch && previous.frame + 1 == current.frame && previous.candidate == current.candidate && previous.c6Candidate == current.c6Candidate
            && previous.main1 == current.main1 && previous.alt1 == current.alt1
            && previous.main2 == current.main2 && previous.alt2 == current.alt2
            && previous.composite5.program == bridge.program && previous.composite5.framebuffer == bridge.framebuffer
            && sameProgram(previous.composite6, current.composite6) && sameProgram(previous.composite7, current.composite7)
            && sameProgram(previous.finalPass, current.finalPass)
            && output > 0 && bridge.history.texture == output
            && bridge.outputs.get(1).texture == current.composite7.history.texture;
    }
    public static boolean reset(List<Texture> values, int main, int alt) {
        return values.size() == 2 && values.get(0).id == main && values.get(1).id == alt
            && values.stream().allMatch(t -> t.nativeSampling() && t.min == LINEAR);
    }
    private static boolean outputs(Stage s, List<Integer> expected) {
        return outputs(s.outputs, expected, s.color.storage);
    }
    private static boolean outputs(List<Attachment> values, List<Integer> expected, Texture extent) {
        if (values.size() != expected.size()) return false;
        for (int i = 0; i < expected.size(); i++) {
            Attachment a = values.get(i);
            if (a.drawIndex != i || a.attachment != 36064 + i || a.objectType != 5890 || a.level != 0 || a.texture != expected.get(i)
                    || a.width != extent.width || a.height != extent.height) return false;
        }
        return true;
    }
    private static boolean sameProgram(Stage a, Stage b) { return a.program == b.program && a.framebuffer == b.framebuffer; }
    private static int selected(Set<Integer> alt, int logical, int main, int other) { return alt.contains(logical) ? other : main; }
    private static int opposite(int id, int main, int alt) { return id == main ? alt : id == alt ? main : 0; }
    private static boolean pair(int a, int b, int main, int alt) { return a != b && ((a == main && b == alt) || (a == alt && b == main)); }
}
