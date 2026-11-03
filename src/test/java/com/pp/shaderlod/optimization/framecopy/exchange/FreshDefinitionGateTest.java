package com.pp.shaderlod.optimization.framecopy.exchange;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FreshDefinitionGateTest {
    @Test void reloadReturnDoesNotGrantReadPermission() {
        var g=new FreshDefinitionGate();var old=new Object();g.discard(old);assertFalse(g.readAllowed());
        g.reconstructionReturned();assertFalse(g.readAllowed());
        var next=new Object();g.bind(next,7);assertFalse(g.readAllowed());
        g.positivelyDefined(next,7);assertTrue(g.readAllowed());
    }
    @Test void sameOrChangedAllocationCannotQualify() {
        var g=new FreshDefinitionGate();var old=new Object();g.discard(old);g.reconstructionReturned();
        assertThrows(IllegalStateException.class,()->g.bind(old,1));var next=new Object();g.bind(next,2);
        assertThrows(IllegalStateException.class,()->g.bind(new Object(),2));
        assertThrows(IllegalStateException.class,()->g.positivelyDefined(next,3));assertFalse(g.readAllowed());
    }
    @Test void failedReplacementStopsInsteadOfRetryingOrReading() {
        var g=new FreshDefinitionGate();g.discard(new Object());g.reconstructionReturned();g.bind(new Object(),2);
        assertThrows(IllegalStateException.class,()->g.discard(new Object()));
        assertEquals(FreshDefinitionGate.Phase.STOPPED,g.phase());assertFalse(g.readAllowed());
        assertThrows(IllegalStateException.class,g::reconstructionReturned);
    }
    @Test void eachSuccessfullyDefinedIncidentGetsOnlyOneReplacement() {
        var g=new FreshDefinitionGate();Object old=new Object();
        for(int i=0;i<5;i++){g.discard(old);g.reconstructionReturned();var next=new Object();g.bind(next,i);g.positivelyDefined(next,i);old=next;}
        assertTrue(g.readAllowed());
    }
}
