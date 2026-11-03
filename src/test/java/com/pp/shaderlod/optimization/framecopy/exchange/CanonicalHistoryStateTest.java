package com.pp.shaderlod.optimization.framecopy.exchange;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalHistoryStateTest {
    @Test void alternatesOnlyCompleteProducerAtCommitSeam() {
        var state = new CanonicalHistoryState();
        for (int i=0;i<20;i++) {
            var banks=state.producerStarted();
            assertEquals(i%2,banks.committed());
            assertThrows(IllegalStateException.class,()->state.exchange(banks.committed(),banks.write(),null));
            state.producerCompleted();state.exchange(banks.committed(),banks.write(),null);
        }
        assertEquals(20,state.exchanges());assertEquals(0,state.banks().committed());
    }
    @Test void retainedCopyMustNotSupplyTheNextCommittedBank() {
        var state=new CanonicalHistoryState();state.producerStarted();state.producerCompleted();
        assertThrows(IllegalStateException.class,()->state.exchange(0,1,1));
        state.exchange(0,1,0);assertEquals(1,state.banks().committed());
    }
    @Test void wrongPhysicalReadOrWriteCannotBeHiddenByRetainedCopy() {
        var state=new CanonicalHistoryState();state.producerStarted();state.producerCompleted();
        assertThrows(IllegalStateException.class,()->state.exchange(1,0,0));
        assertThrows(IllegalStateException.class,()->state.exchange(0,0,0));
        assertEquals(0,state.exchanges());
    }
    @Test void nativeReturnSeedsOtherBankForEitherOrientation() {
        for(int rotations=0;rotations<2;rotations++) {
            var state=new CanonicalHistoryState();
            if(rotations==1){state.producerStarted();state.producerCompleted();state.exchange(0,1,null);}
            var repair=state.planNativeReturn();assertEquals(rotations,repair.source());assertEquals(1-rotations,repair.destination());
            state.nativeReturnCompleted();assertEquals(CanonicalHistoryState.Phase.CLOSED,state.phase());
            assertThrows(IllegalStateException.class,state::producerStarted);
        }
    }
    @Test void unknownSubmissionCannotPublishOrBlindlyRepair() {
        var state=new CanonicalHistoryState();state.producerStarted();state.discard();
        assertThrows(IllegalStateException.class,state::producerCompleted);
        assertThrows(IllegalStateException.class,()->state.exchange(0,1,null));
        assertThrows(IllegalStateException.class,state::planNativeReturn);
        assertThrows(IllegalStateException.class,state::banks);
    }
    @Test void unknownNativeCopyOrRepairAlsoDiscardsAllocation() {
        var state=new CanonicalHistoryState();state.producerStarted();state.producerCompleted();state.discard();
        assertThrows(IllegalStateException.class,state::nativeReturnCompleted);
        assertThrows(IllegalStateException.class,()->state.exchange(0,1,0));
    }
    @Test void aNewFullClearedAllocationHasIndependentInitialHistory() {
        var bad=new CanonicalHistoryState();bad.discard();var clean=new CanonicalHistoryState();
        assertEquals(new CanonicalHistoryState.Banks(0,1),clean.banks());
        assertThrows(IllegalStateException.class,bad::banks);
    }
}
