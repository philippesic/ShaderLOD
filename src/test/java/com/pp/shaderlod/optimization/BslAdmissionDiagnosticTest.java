package com.pp.shaderlod.optimization;

import java.lang.reflect.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Diagnostic truth/retention only; no GL state or driver admission is fabricated. */
class BslAdmissionDiagnosticTest {
    @Test void diagnosticPredicatesPreserveTruthAndRetainFirstFailure()throws Exception {
        for(String name:List.of("BslSpatialRuntime")) {
            Class<?> type=Class.forName("com.pp.shaderlod.optimization.bslspatial."+name);
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
                first.set(null,"none");
                Method pair=type.getDeclaredMethod("samplerPair",boolean.class,String.class,String.class,int.class,int.class);pair.setAccessible(true);
                assertEquals(false,pair.invoke(null,false,"shadowtex0","comparison mode",0,34894));
                assertEquals("shadowtex0: comparison mode actual=0 expected=34894",first.get(null));
                assertEquals(true,pair.invoke(null,true,"shadowtex1","comparison mode",34894,34894));
                assertEquals("shadowtex0: comparison mode actual=0 expected=34894",first.get(null));
                first.set(null,"none");last.set(null,"native");
                Method binding=type.getDeclaredMethod("comparisonProof",boolean.class,String.class,int.class,int.class,int.class,int.class,int.class);binding.setAccessible(true);
                assertEquals(true,binding.invoke(null,true,"shadowtex0",34894,34894,4,19,7));assertEquals("none",first.get(null));assertEquals("native",last.get(null));
                assertEquals(false,binding.invoke(null,false,"shadowtex0",0,34894,4,19,0));
                String witness="shadowtex0: comparison mode actual=0 expected=34894 unit=4 texture=19 sampler=0";
                assertEquals(witness,first.get(null));assertEquals("native/refused: "+witness,last.get(null));
                assertEquals(false,binding.invoke(null,false,"shadowtex1",0,34894,5,20,8));assertEquals(witness,first.get(null));
                assertTrue(last.get(null).toString().endsWith("unit=5 texture=20 sampler=8"));
            }finally{first.set(null,oldFirst);last.set(null,oldLast);}
        }
    }
}
