package com.pp.shaderlod.tuning;

import com.pp.shaderlod.benchmark.core.*;
import com.pp.shaderlod.measurement.FrameMeasurement;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TuningPolicyTest {
    private final TuningPolicy.CasePolicy policy=new TuningPolicy.CasePolicy(
        new PracticalCriterion(.4,.3,.4,"Fixture: useful saving in pose-0 stable-pipeline at this clock"),
        new AnalysisPolicy(20,100,3,2,1,"Fixture only: 20-frame blocks cover imposed correlation; one session, no cross-session claim",50),"fixture pose-0/stable-pipeline");
    private final BenchmarkAnalysis.Stratum stratum=new BenchmarkAnalysis.Stratum(Map.of("case","pose-0","control_class","stable-pipeline","observer","PERFORMANCE"));
    private List<FrameMeasurement.Sample> samples(double ms,double blockSwing) {
        var rows=new ArrayList<FrameMeasurement.Sample>();
        for(int i=0;i<120;i++)rows.add(new FrameMeasurement.Sample(i,1,(long)((ms+(i/20%2==0?blockSwing:-blockSwing))*1e6),1,null,null,null,"performance: unavailable"));
        return rows;
    }
    private List<BenchmarkAnalysis.Bracket> brackets(double a,double b,double c,double swing){
        var result=new ArrayList<BenchmarkAnalysis.Bracket>();
        for(int i=0;i<2;i++)result.add(TuningPolicy.bracket("bracket-"+i,"one-JVM-session",stratum,samples(a,swing),samples(b,swing),samples(c,swing),policy));
        return result;
    }
    private BenchmarkAnalysis.Decision decide(List<BenchmarkAnalysis.Bracket> brackets,BenchmarkAnalysis.ControlKind kind){
        return TuningPolicy.analyze("fixture",brackets,policy,kind,BenchmarkAnalysis.Availability.AVAILABLE,BenchmarkAnalysis.Activation.ACTIVE,List.of());
    }
    @Test void correlatedNullCannotWinDespiteManyNearlyIdenticalAdjacentFrames(){
        var d=decide(brackets(10,9.9,10,1),BenchmarkAnalysis.ControlKind.OPTIMIZATION);
        assertEquals(BenchmarkAnalysis.Verdict.INCONCLUSIVE,d.verdict());
        assertTrue(d.lowerEnvelopeMs()<0);assertEquals(1,d.independentRepeatCount());
        assertEquals(AnalysisPolicy.InferenceScope.WITHIN_SESSION,d.inferenceScope());
        assertFalse(decide(brackets(10,8,10,0),BenchmarkAnalysis.ControlKind.KNOWN_NULL).positive());
    }
    @Test void knownPositiveUsesExplicitCaseCriterionAndRawChronology(){
        var d=decide(brackets(10,9,10.02,.01),BenchmarkAnalysis.ControlKind.OPTIMIZATION);
        assertTrue(d.positive(),d.reasons().toString());assertEquals(.4,d.criterion().minimumBenefitMs());
        assertEquals(BenchmarkAnalysis.Verdict.NEGATIVE,decide(brackets(10,11,10,0),BenchmarkAnalysis.ControlKind.OPTIMIZATION).verdict());
    }
    @Test void driftWrongStrataAndInactiveUnavailableAreDistinct(){
        assertEquals(BenchmarkAnalysis.Verdict.INCONCLUSIVE,decide(brackets(10,8,11,0),BenchmarkAnalysis.ControlKind.OPTIMIZATION).verdict());
        var all=new ArrayList<>(brackets(10,9,10,0));var first=all.getFirst();
        var wrong=new BenchmarkAnalysis.Window(first.candidate().id(),new BenchmarkAnalysis.Stratum(Map.of("case","motion")),first.candidate().summary());
        all.set(0,new BenchmarkAnalysis.Bracket(first.id(),first.repeatId(),first.before(),wrong,first.returned()));
        assertEquals(BenchmarkAnalysis.Validity.INVALID,decide(all,BenchmarkAnalysis.ControlKind.OPTIMIZATION).validity());
        var inactive=TuningPolicy.analyze("inactive",all,policy,BenchmarkAnalysis.ControlKind.OPTIMIZATION,BenchmarkAnalysis.Availability.AVAILABLE,BenchmarkAnalysis.Activation.INACTIVE,List.of());
        assertEquals(BenchmarkAnalysis.Verdict.UNTESTED,inactive.verdict());
        var unavailable=TuningPolicy.analyze("missing",all,policy,BenchmarkAnalysis.ControlKind.OPTIMIZATION,BenchmarkAnalysis.Availability.UNAVAILABLE,BenchmarkAnalysis.Activation.UNKNOWN,List.of());
        assertEquals(BenchmarkAnalysis.Verdict.UNAVAILABLE,unavailable.verdict());
    }
    @Test void chronologyCannotBeSortedIntoAWinAndCriteriaDoNotLeakBetweenCases(){
        var rows=new ArrayList<>(samples(9,0));Collections.swap(rows,10,11);
        var broken=TuningPolicy.bracket("broken","one-JVM-session",stratum,samples(10,0),rows,samples(10,0),policy);
        assertEquals(BenchmarkAnalysis.Validity.INVALID,decide(List.of(broken),BenchmarkAnalysis.ControlKind.OPTIMIZATION).validity());
        var config=new TuningPolicy.Configuration(Map.of("pose-0|stable-pipeline",policy));
        assertEquals(policy,config.forCase("pose-0","stable-pipeline"));
        assertTrue(config.forCase("pose-1","stable-pipeline").analysis().independenceEvidence().isBlank());
        assertTrue(config.forCase("pose-0","reload").analysis().independenceEvidence().isBlank());
        var stricter=new TuningPolicy.CasePolicy(new PracticalCriterion(2,.3,.4,"Different declared use needs 2ms"),policy.analysis(),"fixture stricter case");
        assertFalse(TuningPolicy.analyze("stricter",brackets(10,9,10,0),stricter,BenchmarkAnalysis.ControlKind.OPTIMIZATION,
            BenchmarkAnalysis.Availability.AVAILABLE,BenchmarkAnalysis.Activation.ACTIVE,List.of()).positive());
    }
    @Test void noSummaryOnlyFacadeCanManufactureAWin(){
        var legacy=new TuningPolicy.Stats(10000,1,1,0);
        assertEquals(TuningPolicy.Verdict.INCONCLUSIVE,TuningPolicy.decide(new TuningPolicy.Stats(10000,10,10,0),legacy,new TuningPolicy.Stats(10000,10,10,0)).verdict());
        assertFalse(TuningPolicy.repeatable(legacy,legacy));
    }
    @Test void missingOrUncalibratedRawEvidenceCannotPass(){
        var uncalibrated=new TuningPolicy.Configuration(Map.of()).forCase("pose-0","stable-pipeline");
        var d=TuningPolicy.analyze("uncalibrated",brackets(10,8,10,0),uncalibrated,BenchmarkAnalysis.ControlKind.OPTIMIZATION,
            BenchmarkAnalysis.Availability.AVAILABLE,BenchmarkAnalysis.Activation.ACTIVE,List.of());
        assertFalse(d.positive());
        var stats=TuningPolicy.stats(samples(10,0));assertEquals(120,stats.count());assertTrue(Double.isNaN(stats.standardErrorMs()));
        assertEquals(10,stats.summary().medianMs());assertTrue(stats.summary().tailStatus().contains("descriptive"));
    }
}
