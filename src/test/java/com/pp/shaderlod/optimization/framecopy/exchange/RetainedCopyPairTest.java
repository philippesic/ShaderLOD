package com.pp.shaderlod.optimization.framecopy.exchange;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RetainedCopyPairTest {
    @Test void twoReturnsRemainPendingUntilSharedCompletion() {
        var p=new RetainedCopyPair();p.start(0);p.returned(0);p.start(1);p.returned(1);
        assertFalse(p.completed());assertFalse(p.quiescent());assertThrows(IllegalStateException.class,p::requireCompletedBeforeHistory);
        p.completionSucceeded();p.requireCompletedBeforeHistory();assertTrue(p.quiescent());p.nextFrame();assertEquals(0,p.started());
    }
    @Test void missingFirstOrSecondReturnCannotDisappearAtNewFrame() {
        var p=new RetainedCopyPair();p.start(0);assertThrows(IllegalStateException.class,()->p.start(1));assertThrows(IllegalStateException.class,p::nextFrame);
        p.returned(0);p.start(1);assertThrows(IllegalStateException.class,p::completionSucceeded);assertThrows(IllegalStateException.class,p::nextFrame);
    }
    @Test void orderAndDuplicateCompletionAreRejected() {
        var p=new RetainedCopyPair();assertThrows(IllegalStateException.class,()->p.start(1));p.start(0);p.returned(0);
        assertThrows(IllegalStateException.class,()->p.returned(0));p.start(1);p.returned(1);p.completionSucceeded();assertThrows(IllegalStateException.class,p::completionSucceeded);
    }
    @Test void returnedPairCannotBeResetBeforeWitness() {
        var p=new RetainedCopyPair();p.start(0);p.returned(0);p.start(1);p.returned(1);assertThrows(IllegalStateException.class,p::nextFrame);
        assertEquals(3,p.started());assertEquals(3,p.returned());assertFalse(p.completed());
    }
}
