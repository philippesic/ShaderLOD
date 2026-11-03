package com.pp.shaderlod.benchmark;

import com.pp.shaderlod.benchmark.core.BenchmarkAnalysis;
import com.pp.shaderlod.benchmark.core.BenchmarkResult;
import com.pp.shaderlod.benchmark.core.BenchmarkResultWriter;
import com.pp.shaderlod.benchmark.core.BenchmarkStatistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkRunReportTest {
    @TempDir Path directory;
    @Test void runningLifecycleCannotConstructExport() {
        var schedule = BenchmarkScenario.fast(new BenchmarkScenario.Settings(12, 1920, 1200, BenchmarkScenario.DhState.ABSENT));
        var running = new BenchmarkScenarioRun.Result(BenchmarkScenarioRun.Stage.CAPTURE, BenchmarkScenarioRun.Outcome.RUNNING,
                List.of(), null, false, "Capturing");
        assertThrows(IllegalStateException.class, () -> BenchmarkRunReport.manifest(null, "running", schedule, running,
                Map.of(), Map.of(), Map.of(), Map.of(), "", 1));
    }
    @Test void cancellationExportsPartialCoverageAndNoPromotionUsingSharedSamplesAndStatistics() throws Exception {
        var schedule = BenchmarkScenario.fast(new BenchmarkScenario.Settings(12, 1920, 1200, BenchmarkScenario.DhState.ABSENT));
        var first = schedule.cases().getFirst();
        var rows = List.of(BenchmarkRunCaptureTest.sample(1), BenchmarkRunCaptureTest.sample(2));
        var cases = schedule.cases().stream().map(c -> new BenchmarkScenarioRun.CaseResult(c,
                c == first ? BenchmarkScenarioRun.Verdict.CANCELLED : BenchmarkScenarioRun.Verdict.NOT_RUN, null, "Cancelled before completion")).toList();
        var result = new BenchmarkScenarioRun.Result(BenchmarkScenarioRun.Stage.FINISHED, BenchmarkScenarioRun.Outcome.CANCELLED,
                cases, null, true, "Cancelled");
        var report = BenchmarkRunReport.manifest(null, "cancelled-run", schedule, result,
                Map.of(first.id(), rows), Map.of(), Map.of(), Map.of(), "Cancelled", 10_000_000);
        assertTrue(report.decisions().isEmpty());
        assertEquals(0, report.searchCoverage().attemptedCandidates());
        assertEquals(BenchmarkResult.EvidenceVerdict.UNTESTED, report.evidence().quality());
        assertEquals(BenchmarkResult.EvidenceVerdict.UNTESTED, report.evidence().compatibility());
        var window = report.windows().getFirst();
        assertEquals(BenchmarkAnalysis.Validity.INVALID, window.validity());
        assertEquals(rows.stream().map(BenchmarkResult.Sample::from).toList(), window.samples());
        assertEquals(BenchmarkStatistics.summarize(rows.subList(1, rows.size()), window.statistics().policy()), window.statistics());
        assertEquals(BenchmarkAnalysis.Availability.UNAVAILABLE, window.clocks().getLast().availability());
        assertEquals(0, window.clocks().getLast().requested());
        assertEquals(0, window.statistics().missingCount());
        assertEquals("true", window.hostObservations().get("excluded_initial_unavailable_interval"));
        assertEquals(BenchmarkAnalysis.Availability.UNTESTED, report.windows().get(1).availability());
        assertFalse(report.qualificationBlockers().isEmpty());
        Path output = directory.resolve("cancelled.json"); BenchmarkResultWriter.write(output, report);
        String json = Files.readString(output);
        assertTrue(json.contains("\"schemaVersion\":1"));
        assertTrue(json.contains("\"gpuElapsedNs\":null"));
        assertTrue(json.contains("\"case_verdict\":\"NOT_RUN\""));
        assertFalse(json.contains("NaN"));
    }
    @Test void normalAndNeutralObservationsShareEndpointsDenominatorAndDiagnosticContract() {
        var schedule=BenchmarkScenario.fast(new BenchmarkScenario.Settings(12,1920,1200,BenchmarkScenario.DhState.ABSENT));
        var id=schedule.cases().getFirst().id();var rows=List.of(BenchmarkRunCaptureTest.sample(1),BenchmarkRunCaptureTest.sample(2));
        var cpu=new com.pp.shaderlod.measurement.CpuWindowMeasurement(new com.pp.shaderlod.measurement.CpuWindowMeasurement.Snapshot(7,100,10,20));
        for(int n=0;n<3;n++){cpu.frameStarted(7);cpu.frameCompleted(7);}
        var window=cpu.finish(new com.pp.shaderlod.measurement.CpuWindowMeasurement.Snapshot(7,200,40,80));
        // Neutral caller uses this shared projection directly; normal report must carry the same contract.
        var neutral=com.pp.shaderlod.measurement.CpuMeasurementReport.observations(window,rows);
        var report=BenchmarkRunReport.manifest(null,"contract",schedule,null,Map.of(id,rows),Map.of(),Map.of(),Map.of(),"",100,Map.of(id,window));
        var normal=report.windows().getFirst().hostObservations();neutral.forEach((key,value)->assertEquals(value,normal.get(key)));
        assertEquals("3",normal.get("cpu_window.completedFrames"));assertEquals("100",normal.get("cpu_window.elapsedWallNs"));
        assertEquals("10",normal.get("cpu_window.start.threadCpuNs"));assertEquals("40",normal.get("cpu_window.end.threadCpuNs"));
        assertTrue(BenchmarkResultWriter.toJson(report).contains("cpu_window.threadCpuMsPerFrame"));
        assertTrue(report.windows().get(1).hostObservations().get("cpu_window.threadStatus").contains("unavailable"));
        assertTrue(report.decisions().isEmpty());assertEquals(2,report.windows().getFirst().samples().size());
    }
    @Test void missingDhGeometryAndQueuesRemainUnavailableInUserReport() {
        var schedule = BenchmarkScenario.fast(new BenchmarkScenario.Settings(12, 1920, 1200, BenchmarkScenario.DhState.ENABLED));
        var first = schedule.cases().getFirst();
        var gate = new BenchmarkReadiness(first, BenchmarkReadiness.Policy.forCase(first), 0);
        var unavailable = gate.observe(new BenchmarkReadiness.Observation(1, true, first.settings(),
                BenchmarkReadiness.Signal.READY, BenchmarkReadiness.Signal.READY, BenchmarkReadiness.Signal.READY,
                BenchmarkReadiness.Signal.READY, BenchmarkScenario.DhState.ENABLED, BenchmarkReadiness.Signal.UNAVAILABLE,
                BenchmarkReadiness.Signal.UNAVAILABLE, null, Map.of()));
        var cases = schedule.cases().stream().map(c -> new BenchmarkScenarioRun.CaseResult(c,
                c == first ? BenchmarkScenarioRun.Verdict.UNAVAILABLE : BenchmarkScenarioRun.Verdict.NOT_RUN,
                c == first ? unavailable : null, "Missing live DH evidence")).toList();
        var report = BenchmarkRunReport.manifest(null, "missing-dh", schedule,
                new BenchmarkScenarioRun.Result(BenchmarkScenarioRun.Stage.FINISHED, BenchmarkScenarioRun.Outcome.PARTIAL, cases, unavailable, true, "Unavailable"),
                Map.of(), Map.of(), Map.of(), Map.of(), "", 1);
        var window = report.windows().getFirst();
        assertEquals(BenchmarkAnalysis.Availability.UNAVAILABLE, window.availability());
        assertEquals(BenchmarkAnalysis.Validity.UNTESTED, window.validity());
        assertTrue(window.warmup().missingSignals().stream().anyMatch(s -> s.contains("DH loading queue")));
        assertTrue(window.work().stream().filter(w -> w.signal().equals("DH_TERRAIN")).allMatch(w -> w.executed() == null));
        assertTrue(window.samples().isEmpty());
    }
}
