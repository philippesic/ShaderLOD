package com.pp.shaderlod.optimization.depthprime;

import com.pp.shaderlod.optimization.makeupmerged.MergedLinkageGate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DepthCompiledProgramsTest {
    private DepthCompiledPrograms family() {
        return new DepthCompiledPrograms("test","gbuffers_terrain","/absent","absent","DEPTH",
                options->false,(source,vertex)->false);
    }
    @Test void destroyingOldPipelineKeepsNewCompilationScopeAndExplicitDisableKeepsOwnerIdentity() {
        var family=family();
        Object oldOwner=new Object(), newOwner=new Object();
        DepthCompiledPrograms oldProof, newProof;
        try (var construction=MergedLinkageGate.beginConstruction()) {
            oldProof=family.session(oldOwner); construction.commit();
        }
        try (var construction=MergedLinkageGate.beginConstruction()) {
            newProof=family.session(newOwner); construction.commit();
        }
        assertNotSame(oldProof,newProof);
        family.release(oldOwner);
        assertTrue(family.hasSessions());
        assertSame(newProof,family.session(newOwner));
        family.clear();
        assertSame(newProof,family.session(newOwner));
        family.release(newOwner);
        assertFalse(family.hasSessions());
    }
    @Test void actualConstructorFailureCleanupCannotReleaseCommittedSibling() {
        var family=family(); Object goodOwner=new Object(), failedOwner=new Object();
        DepthCompiledPrograms goodProof;
        try (var construction=MergedLinkageGate.beginConstruction()) {
            goodProof=family.session(goodOwner); construction.commit();
        }
        try (var construction=MergedLinkageGate.beginConstruction()) {
            family.session(failedOwner);
            construction.closeAfterFailure(new IllegalStateException("constructor failed"));
        }
        assertTrue(family.hasSessions());
        assertSame(goodProof,family.session(goodOwner));
        family.release(goodOwner);
        assertFalse(family.hasSessions());
    }
    @Test void missingConstructionCannotCreateStoredOrReusableProof() {
        var family=family(); Object owner=new Object();
        assertFalse(family.currentOwner(owner,null,null,null,0));
        assertFalse(family.hasSessions());
        var missing=family.session(owner);
        assertFalse(family.hasSessions());
        assertNotSame(missing,family.session(owner));
    }
    @Test void compilationDiagnosticsAreBoundedAndInvalidatedWithoutGrantingOwners() {
        var proof=new DepthCompiledPrograms("photon","gbuffers_terrain_solid","/absent","absent","DEPTH",
                options->false,(source,vertex)->false);
        for(int i=0;i<100;i++)
            proof.prepare(null,null,null,null,null,null,null,null,null,0,
                    ()->{throw new AssertionError("wrong program must stop before source validation");});
        assertTrue(proof.status().contains("WRONG_PROGRAM=100"));
        proof.transformed(null,null,null,null);
        assertTrue(proof.status().contains("MISSING_PREPARATION=1"));
        proof.own(null,null,null,null,null,null,null,null,0);
        assertTrue(proof.status().contains("MISSING_DERIVATIVE=1"));
        assertTrue(proof.status().contains("owners=0"));
        proof.clear();
        assertTrue(proof.status().contains("compilationCounts={}"));
        assertTrue(proof.status().contains("compilationLast=invalidated"));
    }
    @Test void diagnosticsStayLimitedToRequestedFamilies() {
        var proof=family();
        proof.prepare(null,null,null,null,null,null,null,null,null,0,()->false);
        assertFalse(proof.status().contains("compilationCounts"));
    }
}
