package com.pp.shaderlod.benchmark;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable requests, not claims of runtime coverage. Native shader/AA clocks remain running. */
public final class BenchmarkScenario {
    public static final String VERSION = "scenario-3";
    public static final String ROUTE_VERSION = BenchmarkScene.VERSION + "/flythrough-1";
    public enum DhState { UNKNOWN, ABSENT, INSTALLED_DISABLED, ENABLED }
    public enum Workload { STEADY, STREAMING }
    /** Authored vanilla time/weather remain pinned; native shader animations, jitter and AA histories run. */
    public enum TemporalPolicy { NATIVE_CLOCKS }
    public enum Weather { CLEAR, RAIN }
    public enum Work { TERRAIN, FOLIAGE_CUTOUTS, WATER, UNDERWATER, MATERIALS, EMISSIVES_GLASS,
        COLD_PRECIPITATION, DISTANT_TERRAIN, DH_TERRAIN, PBR }

    /** Resolution is the actual framebuffer expectation, not logical window size. */
    public record Settings(int renderDistance, int framebufferWidth, int framebufferHeight, DhState dh) {
        public Settings {
            if (renderDistance < 2 || framebufferWidth < 1 || framebufferHeight < 1)
                throw new IllegalArgumentException("Invalid RD/framebuffer expectation");
            Objects.requireNonNull(dh);
            if (dh == DhState.UNKNOWN) throw new IllegalArgumentException("Select an explicit DH state");
        }
    }
    public record Environment(long dayTime, Weather weather) {
        public Environment { Objects.requireNonNull(weather); }
    }
    public record Budgets(Duration apply, Duration acknowledge, Duration warm, Duration capture,
                          Duration drain, Duration total) {
        public Budgets {
            for (Duration d : List.of(apply, acknowledge, warm, capture, drain, total)) positive(d);
            if (total.compareTo(capture) < 0) throw new IllegalArgumentException("Case budget below capture budget");
        }
    }
    public record Case(String id, int poseIndex, Environment environment, Settings settings,
                       Workload workload, int routeTicks, Set<Work> requestedWork, Budgets budgets) {
        public Case {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Case ID is required");
            if (poseIndex < 0 || poseIndex >= BenchmarkScene.POSES.size())
                throw new IllegalArgumentException("Unknown authored pose");
            Objects.requireNonNull(environment); Objects.requireNonNull(settings);
            Objects.requireNonNull(workload); Objects.requireNonNull(budgets);
            requestedWork = Set.copyOf(requestedWork);
            var pose = BenchmarkScene.POSES.get(poseIndex);
            if (environment.dayTime() != pose.time() || (environment.weather() == Weather.RAIN) != pose.rain())
                throw new IllegalArgumentException("Environment must match the existing authored pose");
            if (workload == Workload.STREAMING && (poseIndex != 0 || routeTicks < 40 || routeTicks > 1200))
                throw new IllegalArgumentException("Existing route starts at day/clear pose 0, 40..1200 ticks");
            if (workload == Workload.STEADY && routeTicks != 0)
                throw new IllegalArgumentException("Steady cases have no route");
            if (requestedWork.contains(Work.DH_TERRAIN) && settings.dh() != DhState.ENABLED)
                throw new IllegalArgumentException("DH work requires enabled DH");
        }
        public String poseName() { return BenchmarkScene.POSES.get(poseIndex).name(); }
        public TemporalPolicy temporalPolicy() { return TemporalPolicy.NATIVE_CLOCKS; }
        public String sceneVersion() { return BenchmarkScene.VERSION; }
        public String routeVersion() { return workload == Workload.STREAMING ? ROUTE_VERSION : "none"; }
    }
    public record Schedule(String id, List<Case> cases, Duration totalBudget, Duration recoveryBudget) {
        public Schedule {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Schedule ID is required");
            cases = List.copyOf(cases);
            if (cases.isEmpty() || cases.stream().map(Case::id).distinct().count() != cases.size())
                throw new IllegalArgumentException("Cases must be nonempty with unique IDs");
            positive(totalBudget); positive(recoveryBudget);
            if (totalBudget.compareTo(recoveryBudget) <= 0)
                throw new IllegalArgumentException("Reserve recovery separately from execution");
        }
        public String version() { return VERSION; }
    }

    private BenchmarkScenario() {}

    /** Public immutable metadata without exposing the mutable world construction implementation. */
    public static Environment authoredEnvironment(int poseIndex) {
        if (poseIndex < 0 || poseIndex >= BenchmarkScene.POSES.size()) throw new IllegalArgumentException("Unknown authored pose");
        return environment(poseIndex);
    }

    /** Proposed warm-client 180-second ceiling; exhaustion produces partial coverage. */
    public static Schedule fast(Settings settings) {
        var cases = new ArrayList<Case>();
        cases.add(pose("day-terrain", 0, settings, distant(settings, Work.TERRAIN)));
        cases.add(pose("day-foliage", 1, settings, Set.of(Work.FOLIAGE_CUTOUTS)));
        cases.add(pose("day-water", 2, settings, Set.of(Work.WATER)));
        cases.add(pose("day-underwater", 3, settings, Set.of(Work.UNDERWATER)));
        cases.add(pose("day-materials", 4, settings, Set.of(Work.MATERIALS)));
        cases.add(pose("sunset-clear", 5, settings, Set.of(Work.TERRAIN)));
        cases.add(pose("night-clear", 6, settings, Set.of(Work.EMISSIVES_GLASS)));
        cases.add(pose("day-rain", 7, settings, Set.of(Work.TERRAIN)));
        cases.add(pose("cold-rain", 8, settings, Set.of(Work.COLD_PRECIPITATION)));
        cases.add(new Case("native-route", 0, environment(0), settings, Workload.STREAMING, 240,
                Set.of(Work.TERRAIN), // route admission covers terrain; effect surfaces have their own still cases
                new Budgets(seconds(2), seconds(3), seconds(8), seconds(16), seconds(2), seconds(35))));
        return new Schedule("FAST", cases, seconds(180), seconds(5));
    }

    /** Caller selects RD/resolution/DH; different selections are distinct diagnostic schedules. */
    public static Schedule diagnostic(Settings settings) {
        var cases = List.of(diagnosticPose("diagnostic-terrain", 0, settings, distant(settings, Work.TERRAIN)),
                diagnosticPose("diagnostic-materials", 4, settings, Set.of(Work.MATERIALS)),
                diagnosticPose("diagnostic-sunset", 5, settings, Set.of(Work.TERRAIN)),
                diagnosticPose("diagnostic-night", 6, settings, Set.of(Work.EMISSIVES_GLASS)),
                diagnosticPose("diagnostic-rain", 7, settings, Set.of(Work.TERRAIN)));
        return new Schedule("DIAGNOSTIC-rd" + settings.renderDistance() + "-"
                + settings.framebufferWidth() + "x" + settings.framebufferHeight() + "-" + settings.dh(),
                cases, seconds(900), seconds(15));
    }

    /** Explicit opt-in: a material panel by itself never requests or certifies active PBR. */
    public static Case pbrDiagnostic(Settings settings) {
        return pose("diagnostic-pbr", 4, settings, Set.of(Work.MATERIALS, Work.PBR));
    }

    /** A specific authored far face needs its own proof; generic DH geometry is insufficient. */
    public static Case farFaceDiagnostic(Settings settings) {
        return diagnosticPose("diagnostic-far-identity", 0, settings, Set.of(Work.DISTANT_TERRAIN));
    }
    private static Case diagnosticPose(String id, int index, Settings settings, Set<Work> work) {
        return new Case(id, index, environment(index), settings, Workload.STEADY, 0, work,
                new Budgets(seconds(2), seconds(3), seconds(60), seconds(5), seconds(2), seconds(80)));
    }

    private static Set<Work> distant(Settings settings, Work near) {
        // The authored far face is >320 blocks from pose 0. Low vanilla RD cannot establish it.
        // Even high RD only requests this coverage; live eligibility/execution evidence is mandatory.
        if (settings.dh() == DhState.ENABLED)
            return Set.of(near, Work.DH_TERRAIN); // actual DH submissions; specific far-face identity stays unproved
        return settings.renderDistance() >= 24 ? Set.of(near, Work.DISTANT_TERRAIN) : Set.of(near);
    }
    private static Case pose(String id, int index, Settings settings, Set<Work> work) {
        return new Case(id, index, environment(index), settings, Workload.STEADY, 0, work,
                new Budgets(seconds(2), seconds(3), seconds(6), seconds(3), seconds(1), seconds(15)));
    }
    private static Environment environment(int index) {
        var pose = BenchmarkScene.POSES.get(index);
        return new Environment(pose.time(), pose.rain() ? Weather.RAIN : Weather.CLEAR);
    }
    private static Duration seconds(int n) { return Duration.ofSeconds(n); }
    static void positive(Duration d) {
        if (d == null || d.isNegative() || d.isZero()) throw new IllegalArgumentException("Budget must be positive");
        d.toNanos(); // Reject overflow now, not during a live run.
    }
}
