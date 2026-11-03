package com.pp.shaderlod.optimization.depthprime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DepthOwnerScopesTest {
    static final class Receipt { boolean certified=true; void clear() { certified=false; } }
    @Test void oldOwnerDestructionCannotRevokeNewOwnerProof() {
        var scopes=new DepthOwnerScopes<Receipt>(Receipt::clear);
        Object oldOwner=new Object(), newOwner=new Object();
        var oldProof=new Receipt(); var newProof=new Receipt();
        scopes.put(oldOwner,oldProof); scopes.put(newOwner,newProof);
        scopes.release(oldOwner);
        assertFalse(oldProof.certified);
        assertTrue(newProof.certified);
        assertSame(newProof,scopes.get(newOwner));
        assertNull(scopes.get(oldOwner));
        assertFalse(scopes.isEmpty());
        scopes.release(oldOwner);
        assertTrue(newProof.certified);
        scopes.release(newOwner);
        assertFalse(newProof.certified);
        assertTrue(scopes.isEmpty());
    }
    @Test void failedConstructionAndExplicitInvalidationHaveDifferentScopes() {
        var scopes=new DepthOwnerScopes<Receipt>(Receipt::clear);
        Object nativeOwner=new Object(), failedOwner=new Object();
        var nativeProof=new Receipt(); var failedProof=new Receipt();
        scopes.put(nativeOwner,nativeProof); scopes.put(failedOwner,failedProof);
        scopes.release(failedOwner);
        assertFalse(failedProof.certified);
        assertTrue(nativeProof.certified);
        scopes.invalidate();
        assertFalse(nativeProof.certified);
        assertSame(nativeProof,scopes.get(nativeOwner));
        assertFalse(scopes.isEmpty());
    }
    @Test void equalOwnerValuesCannotBorrowEachOthersScope() {
        var scopes=new DepthOwnerScopes<Receipt>(Receipt::clear);
        Object first=new String("same"), second=new String("same");
        var receipt=new Receipt(); scopes.put(first,receipt);
        assertNull(scopes.get(second)); scopes.release(second);
        assertTrue(receipt.certified);
        assertSame(receipt,scopes.get(first));
    }
}
