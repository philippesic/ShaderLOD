package com.pp.shaderlod.tuning;

import com.pp.shaderlod.benchmark.core.*;
import com.pp.shaderlod.measurement.FrameMeasurement;
import java.util.*;

/** Tuner facade over the shared chronological analyzer. No frame-IID inference. */
public final class TuningPolicy {
    public enum Verdict { POSITIVE, NEGATIVE, INCONCLUSIVE, INVALID, UNAVAILABLE, SKIPPED, UNTESTED }
    /** Legacy descriptive facade. The four-number constructor deliberately has no raw provenance. */
    public record Stats(int count,double meanMs,double p95Ms,double standardErrorMs,
                        BenchmarkStatistics.Summary summary) {
        public Stats(int count,double meanMs,double p95Ms,double standardErrorMs){this(count,meanMs,p95Ms,standardErrorMs,null);}
    }
    public record Decision(Verdict verdict,double gainMs,double driftMs,double thresholdMs,String reason) {}
    public record CasePolicy(PracticalCriterion criterion,AnalysisPolicy analysis,String calibrationScope) {
        public CasePolicy {
            Objects.requireNonNull(criterion);Objects.requireNonNull(analysis);Objects.requireNonNull(calibrationScope);
            if(calibrationScope.isBlank())throw new IllegalArgumentException("Explicit calibration scope required");
            if(analysis.minimumMatchedBrackets()>4)throw new IllegalArgumentException("live tuner supports at most four predeclared brackets per case");
        }
    }
    /** Keys are caseId + "|" + control class; policies are frozen before the first capture. */
    public record Configuration(Map<String,CasePolicy> cases) {
        public Configuration { cases=Map.copyOf(cases); }
        public String canonicalDescription(){return new TreeMap<>(cases).toString();}
        public CasePolicy forCase(String caseId,String controlClass) {
            var declared=cases.get(caseId+"|"+controlClass);
            if(declared!=null)return declared;
            return new CasePolicy(new PracticalCriterion(.5,.5,.5,
                    "Uncalibrated provisional millisecond criterion for "+caseId+"/"+controlClass+"; cannot qualify adoption"),
                new AnalysisPolicy(30,100,3,2,1,"",50),"UNCONFIGURED: "+caseId+"/"+controlClass);
        }
    }
    private static Configuration configuration=new Configuration(Map.of());
    private TuningPolicy() {}
    /** Caller must configure before starting; AutoTuner snapshots this immutable record. */
    public static synchronized void configure(Configuration value){configuration=Objects.requireNonNull(value);}
    public static synchronized Configuration configuration(){return configuration;}
    public static Stats stats(List<FrameMeasurement.Sample> samples) {
        var s=BenchmarkStatistics.summarize(samples,configuration().forCase("descriptive","none").analysis());
        return new Stats(s.count(),s.meanMs(),s.p95Ms(),Double.NaN,s);
    }
    /** Summary-only legacy callers cannot manufacture calibrated evidence or independent repeats. */
    public static Decision decide(Stats before,Stats candidate,Stats returned) {
        return new Decision(Verdict.INCONCLUSIVE,Double.NaN,Double.NaN,Double.NaN,
                "Legacy summaries are descriptive only; use matched chronological raw windows with a predeclared case policy");
    }
    public static boolean repeatable(Stats first,Stats second){return false;}
    public static boolean nonRegressing(Stats before,Stats candidate,Stats returned){return false;}
    public static BenchmarkAnalysis.Bracket bracket(String id,String sessionId,BenchmarkAnalysis.Stratum stratum,
            List<FrameMeasurement.Sample> before,List<FrameMeasurement.Sample> candidate,
            List<FrameMeasurement.Sample> returned,CasePolicy policy) {
        return new BenchmarkAnalysis.Bracket(id,sessionId,
            window(id+"-before",stratum,before,policy),window(id+"-candidate",stratum,candidate,policy),
            window(id+"-return",stratum,returned,policy));
    }
    private static BenchmarkAnalysis.Window window(String id,BenchmarkAnalysis.Stratum stratum,
            List<FrameMeasurement.Sample> rows,CasePolicy policy) {
        return new BenchmarkAnalysis.Window(id,stratum,BenchmarkStatistics.summarize(rows,policy.analysis()));
    }
    public static BenchmarkAnalysis.Decision analyze(String id,List<BenchmarkAnalysis.Bracket> brackets,
            CasePolicy policy,BenchmarkAnalysis.ControlKind kind,BenchmarkAnalysis.Availability availability,
            BenchmarkAnalysis.Activation activation,List<String> invalidity) {
        return BenchmarkAnalysis.analyze(new BenchmarkAnalysis.Request(id,kind,availability,activation,brackets,
            policy.criterion(),policy.analysis(),invalidity));
    }
    public static Decision facade(BenchmarkAnalysis.Decision d) {
        return new Decision(Verdict.valueOf(d.verdict().name()),number(d.gainMs()),number(d.maximumReturnDriftMs()),
            d.criterion().minimumBenefitMs(),String.join("; ",d.reasons()));
    }
    private static double number(Double n){return n==null?Double.NaN:n;}
}
