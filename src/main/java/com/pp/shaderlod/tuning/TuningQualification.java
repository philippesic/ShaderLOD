package com.pp.shaderlod.tuning;

import com.pp.shaderlod.benchmark.core.BenchmarkResult;
import java.nio.file.Path;
import java.util.*;

/** Explicit external evidence join, evaluated for the exact frozen profile after timing. */
public final class TuningQualification {
    private TuningQualification() {}
    public record Scope(String sessionId,String sourceIdentity,String goal,String frozenProfile,
                        String heldOutCase,Path reportDirectory) {}
    public record Finding(BenchmarkResult.EvidenceVerdict verdict,List<String> links,String reason) {
        public Finding {
            Objects.requireNonNull(verdict);links=List.copyOf(links);Objects.requireNonNull(reason);
            if(reason.isBlank())throw new IllegalArgumentException("Qualification reason required");
        }
    }
    /** A reference alone is not a pass. Findings must assert PASS and bind this exact run scope. */
    public record Evidence(Scope scope,Map<String,Finding> findings) {
        public Evidence { findings=Map.copyOf(findings); }
        public boolean matches(Scope expected){return expected!=null&&expected.equals(scope);}
        public boolean has(String category){
            var finding=findings.get(category);
            return finding!=null&&finding.verdict()==BenchmarkResult.EvidenceVerdict.PASS
                &&finding.links().stream().anyMatch(s->!s.isBlank());
        }
        public Map<String,List<String>> references(){
            var result=new TreeMap<String,List<String>>();findings.forEach((key,value)->result.put(key,value.links()));return Map.copyOf(result);
        }
        public static Evidence untested(){return new Evidence(null,Map.of());}
    }
}
