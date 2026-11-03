package com.pp.shaderlod.tuning;

import com.pp.shaderlod.benchmark.core.BenchmarkResult;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TuningQualificationTest {
    @Test void linkedEvidenceNeedsExplicitPassAndExactFrozenScope(){
        var scope=new TuningQualification.Scope("session-A","source-A","PERFORMANCE","profile-A","pose-8",Path.of("report-A"));
        var pass=new TuningQualification.Finding(BenchmarkResult.EvidenceVerdict.PASS,List.of("quality.json"),"Exact scoped still comparison passed");
        var untested=new TuningQualification.Finding(BenchmarkResult.EvidenceVerdict.UNTESTED,List.of("pending.json"),"Motion not checked");
        var evidence=new TuningQualification.Evidence(scope,Map.of("still-quality",pass,"motion-quality",untested));
        assertTrue(evidence.matches(scope));assertTrue(evidence.has("still-quality"));
        assertFalse(evidence.has("motion-quality"));assertFalse(evidence.has("DH-PBR-compatibility"));
        assertFalse(evidence.matches(new TuningQualification.Scope("session-B","source-A","PERFORMANCE","profile-A","pose-8",Path.of("report-A"))));
        assertFalse(evidence.matches(new TuningQualification.Scope("session-A","source-A","PERFORMANCE","profile-B","pose-8",Path.of("report-A"))));
        assertFalse(new TuningQualification.Evidence(scope,Map.of("still-quality",new TuningQualification.Finding(BenchmarkResult.EvidenceVerdict.PASS,List.of(),"Claim without evidence"))).has("still-quality"));
        assertFalse(TuningQualification.Evidence.untested().matches(scope));
    }
}
