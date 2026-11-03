package com.pp.shaderlod.optimization.solasao;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the maintained Solas option map; no GL or live rendering claim. */
class SolasAoFrameDiagnosticTest {
    private final Object pack=new Object(),pipeline=new Object(),targets=new Object();
    private final Map<String,String> options=SolasAoContract.generated("1");
    private SolasAoFrameProof.Key key(){
        return new SolasAoFrameProof.Key(pack,pipeline,targets,7,8,9,3840,2160,"shaderpacks/Solas.zip","controls");
    }
    private SolasAoFrameProof qualified(){
        var proof=new SolasAoFrameProof();
        assertTrue(SolasAoContract.options(options));
        assertTrue(proof.qualify(key(),true,true,()->options));
        return proof;
    }
    @Test void unchangedValuesRemainCurrentAndDiagnosticsDoNotResetProof(){
        var proof=qualified();var equal=new SolasAoFrameProof.Key(pack,pipeline,targets,7,8,9,3840,2160,
            new String("shaderpacks/Solas.zip"),new String("controls"));
        assertNull(proof.currentFailure(equal,new TreeMap<>(options)));
        assertTrue(proof.current(equal,options));
        assertEquals("current key missing",proof.currentFailure(null,options));
        assertTrue(proof.current(key(),options),"A diagnostic must not mutate the captured proof");
        proof.reset();assertEquals("key missing",proof.currentFailure(key(),options));
    }
    @Test void eachKeyMismatchNamesTheExactFirstField(){
        var cases=new LinkedHashMap<String,SolasAoFrameProof.Key>();
        cases.put("pack identity",new SolasAoFrameProof.Key(new Object(),pipeline,targets,7,8,9,3840,2160,"shaderpacks/Solas.zip","controls"));
        cases.put("pipeline identity",new SolasAoFrameProof.Key(pack,new Object(),targets,7,8,9,3840,2160,"shaderpacks/Solas.zip","controls"));
        cases.put("targets identity",new SolasAoFrameProof.Key(pack,pipeline,new Object(),7,8,9,3840,2160,"shaderpacks/Solas.zip","controls"));
        cases.put("frame serial",new SolasAoFrameProof.Key(pack,pipeline,targets,70,8,9,3840,2160,"shaderpacks/Solas.zip","controls"));
        cases.put("source generation",new SolasAoFrameProof.Key(pack,pipeline,targets,7,80,9,3840,2160,"shaderpacks/Solas.zip","controls"));
        cases.put("flag generation",new SolasAoFrameProof.Key(pack,pipeline,targets,7,8,90,3840,2160,"shaderpacks/Solas.zip","controls"));
        cases.put("width",new SolasAoFrameProof.Key(pack,pipeline,targets,7,8,9,1920,2160,"shaderpacks/Solas.zip","controls"));
        cases.put("height",new SolasAoFrameProof.Key(pack,pipeline,targets,7,8,9,3840,1080,"shaderpacks/Solas.zip","controls"));
        cases.put("selected path",new SolasAoFrameProof.Key(pack,pipeline,targets,7,8,9,3840,2160,"other.zip","controls"));
        cases.put("controls",new SolasAoFrameProof.Key(pack,pipeline,targets,7,8,9,3840,2160,"shaderpacks/Solas.zip","changed"));
        var proof=qualified();
        cases.forEach((reason,key)->{
            assertEquals(reason,proof.currentFailure(key,options));
            assertFalse(proof.current(key,options));
            assertEquals(reason,proof.currentFailure(key,Map.of()),"Key comparison precedes option comparison");
        });
        var multiple=new SolasAoFrameProof.Key(new Object(),new Object(),targets,70,80,90,1920,1080,"other.zip","changed");
        assertEquals("pack identity",proof.currentFailure(multiple,Map.of()));
    }
    @Test void actualOptionMutationsReportChangedKeysWithoutValues(){
        var proof=qualified();assertTrue(options.containsKey("TAA"));
        var changed=new TreeMap<>(options);changed.put("TAA","changed");
        assertEquals("options changed keys=[TAA]",proof.currentFailure(key(),changed));
        changed.remove("TAA");
        assertEquals("options changed keys=[TAA]",proof.currentFailure(key(),changed));
        changed=new TreeMap<>(options);changed.put("UNDECLARED_TEST_OPTION","value");
        assertEquals("options changed keys=[UNDECLARED_TEST_OPTION]",proof.currentFailure(key(),changed));
        assertEquals("options changed keys=["+SolasAoContract.OPTION+"]",
            proof.currentFailure(key(),SolasAoContract.generated("0")));
        assertEquals("current options missing",proof.currentFailure(key(),null));
        assertTrue(proof.currentFailure(key(),Map.of()).contains("TAA"));
        assertTrue(proof.current(key(),options));
    }
    @Test void failedQualificationNeverProducesAUsableProof(){
        var proof=new SolasAoFrameProof();assertFalse(proof.qualify(key(),true,true,Map::of));
        assertEquals("qualified options missing",proof.currentFailure(key(),options));
        assertFalse(proof.current(key(),options));
    }
}
