package com.pp.shaderlod.optimization;

import java.lang.reflect.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Diagnostic truth/retention only; no GL state or driver admission is fabricated. */
class AstraAdmissionDiagnosticTest {
    @Test void diagnosticPredicatesPreserveTruthAndRetainFirstFailure()throws Exception {
        for(String name:List.of("AstraSpatialRuntime","AstraWorldPixelRuntime")) {
            Class<?> type=Class.forName("com.pp.shaderlod.optimization.astraspatial."+name);
            Method proof=type.getDeclaredMethod("proof",boolean.class,String.class);
            Method value=type.getDeclaredMethod("proofValue",boolean.class,String.class,int.class);
            Field first=type.getDeclaredField("firstRefusal"),last=type.getDeclaredField("last");
            for(var item:List.of(proof,value,first,last))item.setAccessible(true);
            Object oldFirst=first.get(null),oldLast=last.get(null);
            try {
                first.set(null,"none");last.set(null,"native");
                assertEquals(true,proof.invoke(null,true,"unused"));assertEquals("none",first.get(null));assertEquals("native",last.get(null));
                assertEquals(false,value.invoke(null,false,"native depth format",6402));
                assertEquals("native depth format actual=6402",first.get(null));
                assertEquals(false,proof.invoke(null,false,"later predicate"));assertEquals("native depth format actual=6402",first.get(null));
                assertEquals("native/refused: later predicate",last.get(null));
                assertEquals(true,value.invoke(null,true,"valid sized format",33190));assertEquals("native depth format actual=6402",first.get(null));
            }finally{first.set(null,oldFirst);last.set(null,oldLast);}
        }
    }
}
