package com.pp.shaderlod.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkScenarioTest {
    private static final BenchmarkScenario.Settings LOW = new BenchmarkScenario.Settings(12, 1920, 1200,
            BenchmarkScenario.DhState.INSTALLED_DISABLED);

    @Test void namedCaseAndRouteOrderIsDeterministicAndUsesEveryAuthoredPose() {
        var first = BenchmarkScenario.fast(LOW);
        assertEquals(first, BenchmarkScenario.fast(LOW));
        assertEquals("scenario-3", first.version());
        assertEquals(List.of("day-terrain", "day-foliage", "day-water", "day-underwater", "day-materials",
                "sunset-clear", "night-clear", "day-rain", "cold-rain", "native-route"),
                first.cases().stream().map(BenchmarkScenario.Case::id).toList());
        for (int i = 0; i < 9; i++) {
            var c = first.cases().get(i);
            assertEquals(i, c.poseIndex());
            assertEquals(BenchmarkScene.POSES.get(i).time(), c.environment().dayTime());
            assertEquals(BenchmarkScene.POSES.get(i).rain(), c.environment().weather() == BenchmarkScenario.Weather.RAIN);
            assertEquals(BenchmarkScenario.Workload.STEADY, c.workload());
            assertEquals(BenchmarkScenario.TemporalPolicy.NATIVE_CLOCKS, c.temporalPolicy());
        }
        var route = first.cases().getLast();
        assertEquals(BenchmarkScenario.Workload.STREAMING, route.workload());
        assertEquals(0, route.poseIndex());
        assertEquals(240, route.routeTicks());
        assertTrue(BenchmarkScenario.ROUTE_VERSION.startsWith(BenchmarkScene.VERSION));
        assertThrows(UnsupportedOperationException.class, () -> first.cases().clear());
    }
    @Test void lowRdDoesNotClaimFarGeometryAndMaterialPanelDoesNotClaimPbr() {
        assertTrue(BenchmarkScenario.fast(LOW).cases().stream().noneMatch(c ->
                c.requestedWork().contains(BenchmarkScenario.Work.DISTANT_TERRAIN)
                        || c.requestedWork().contains(BenchmarkScenario.Work.DH_TERRAIN)
                        || c.requestedWork().contains(BenchmarkScenario.Work.PBR)));
        assertTrue(BenchmarkScenario.pbrDiagnostic(LOW).requestedWork().contains(BenchmarkScenario.Work.PBR));
        var high = new BenchmarkScenario.Settings(24, 2560, 1600, BenchmarkScenario.DhState.ABSENT);
        assertTrue(BenchmarkScenario.fast(high).cases().getFirst().requestedWork()
                .contains(BenchmarkScenario.Work.DISTANT_TERRAIN));
        assertFalse(BenchmarkScenario.fast(high).cases().getFirst().requestedWork()
                .contains(BenchmarkScenario.Work.DH_TERRAIN));
    }
    @Test void diagnosticPinsSelectedSettingsAndEveryStageHasFinitePositiveBudgets() {
        var schedule = BenchmarkScenario.diagnostic(LOW);
        assertTrue(schedule.cases().stream().allMatch(c -> c.budgets().warm().getSeconds() == 60));
        assertTrue(schedule.id().contains("rd12-1920x1200-INSTALLED_DISABLED"));
        for (var c : schedule.cases()) {
            assertEquals(LOW, c.settings());
            var b = c.budgets();
            for (var d : List.of(b.apply(), b.acknowledge(), b.warm(), b.capture(), b.drain(), b.total()))
                assertTrue(d.toNanos() > 0);
        }
        assertTrue(schedule.totalBudget().compareTo(schedule.recoveryBudget()) > 0);
    }
    @Test void refusesAmbiguousDhAndUnsupportedEnvironmentOrRoute() {
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkScenario.Settings(12, 1920, 1200,
                BenchmarkScenario.DhState.UNKNOWN));
        var base = BenchmarkScenario.fast(LOW).cases().getFirst();
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkScenario.Case("bad", 0,
                new BenchmarkScenario.Environment(18000, BenchmarkScenario.Weather.RAIN), LOW,
                base.workload(), 0, base.requestedWork(), base.budgets()));
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkScenario.Case("bad", 1,
                BenchmarkScenario.fast(LOW).cases().get(1).environment(), LOW,
                BenchmarkScenario.Workload.STREAMING, 240, base.requestedWork(), base.budgets()));
    }
}
