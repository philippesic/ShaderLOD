package com.pp.shaderlod.tuning;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TuningConfirmationTest {
    @Test void heldOutCaseNeverEntersSearchAndFailureCannotBeReused(){
        var h=new TuningConfirmation("pose-8");h.searched("pose-0");h.searched("pose-1");
        assertThrows(IllegalStateException.class,()->h.searched("pose-8"));
        h.freeze("profile-A");assertThrows(IllegalStateException.class,()->h.searched("pose-2"));
        assertThrows(IllegalStateException.class,()->h.consume("profile-B"));
        h.consume("profile-A");h.complete(false);
        assertTrue(h.consumed());assertFalse(h.passed());
        assertThrows(IllegalStateException.class,()->h.consume("profile-A"));
        assertThrows(IllegalStateException.class,()->h.complete(true));
        assertThrows(IllegalStateException.class,()->h.freeze("profile-B"));
    }
    @Test void cancelledHoldoutIsConsumedBeforeAnyResultsExist(){
        var h=new TuningConfirmation("pose-8");h.freeze("profile-A");h.consume("profile-A");
        assertFalse(h.passed());assertThrows(IllegalStateException.class,()->h.consume("profile-A"));
    }
    @Test void baselineQualityOrRestorationFailureNeverSavesPositiveTiming(){
        assertTrue(TuningConfirmation.maySave(true,true,true,true,true,true,true,true));
        for(int missing=0;missing<8;missing++){
            boolean[] gates={true,true,true,true,true,true,true,true};gates[missing]=false;
            assertFalse(TuningConfirmation.maySave(gates[0],gates[1],gates[2],gates[3],gates[4],gates[5],gates[6],gates[7]));
        }
        var recovery=new TuningRecovery();
        assertEquals(TuningRecovery.Result.RETRY,recovery.attempt(()->{throw new IllegalStateException("baseline Apply failed");}));
        assertEquals(TuningRecovery.Result.RETRY,recovery.attempt(()->{throw new IllegalStateException("scene restoration failed");}));
        assertEquals(TuningRecovery.Result.PARKED,recovery.attempt(()->{throw new IllegalStateException("source restoration failed");}));
        assertTrue(recovery.parked());
    }
    @Test void candidateAdmissionIncludesConfirmationAndFrozenFinalBudget(){
        long screenOnly=TuningWindow.candidateReserveSeconds(2,0);
        long fullCandidate=TuningWindow.candidateReserveSeconds(2,2);
        assertTrue(fullCandidate>=screenOnly+6*(TuningWindow.CONFIRM.warmupNs()+TuningWindow.CONFIRM.sampleNs())/1_000_000_000L);
        long reserve=TuningWindow.finalReserveSeconds(1,2,2);
        assertTrue(reserve>=6*(TuningWindow.FINAL.warmupNs()+TuningWindow.FINAL.sampleNs())/1_000_000_000L+20);
        // A 300-second allocation can admit a bounded session-local candidate after initial controls.
        assertTrue(fullCandidate+reserve+40<=300);
    }
}
