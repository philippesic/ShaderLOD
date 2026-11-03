package com.pp.shaderlod.optimization;

import com.pp.shaderlod.optimization.sourcecache.NativePassSamplerCache;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePassSamplerCacheTest {
    @Test void failedNestedEntryStillReleasesOuterAndAllowsAFreshInvocation() {
        var cache=new NativePassSamplerCache<String,String>();
        try(var outer=cache.open(true)){
            cache.arm();cache.remember("old","observations");
            cache.granted(()->{throw new IllegalStateException("revocation failed");});
            assertThrows(IllegalStateException.class,()->cache.open(true));
        }
        assertFalse(cache.active(),"failed nested entry did not return a scope to close");
        try(var fresh=cache.open(true)){
            cache.arm();cache.remember("fresh","new observations");assertEquals("new observations",cache.reuse("fresh"));
        }
        assertFalse(cache.active());
    }
    @Test void initialSetupIsAllowedButLaterMutationCannotRevive() {
        var cache=new NativePassSamplerCache<String,String>();
        try(var ignored=cache.open(true)){
            cache.invalidate();cache.arm();cache.remember("program/targets","observations");
            assertEquals("observations",cache.reuse("program/targets"));
            cache.invalidate();cache.arm();cache.remember("program/targets","stale");
            assertNull(cache.reuse("program/targets"));
        }
        assertFalse(cache.active());
    }
    @Test void mutationAfterGrantRevokesBeforeTheWriterAndHandlesReentry() {
        var cache=new NativePassSamplerCache<String,String>();var revoked=new AtomicInteger();
        try(var ignored=cache.open(true)){
            cache.arm();cache.remember("key","observations");
            cache.granted(()->{revoked.incrementAndGet();cache.invalidate();});
            cache.invalidate();assertEquals(1,revoked.get());assertNull(cache.reuse("key"));
        }
        assertEquals(1,revoked.get());
    }
    @Test void nestingBeforeArmingPoisonsOuterInvocationPermanently() {
        var cache=new NativePassSamplerCache<String,String>();
        try(var outer=cache.open(true)){
            try(var inner=cache.open(false)){cache.arm();cache.remember("key","nested");}
            cache.arm();cache.remember("key","outer");assertNull(cache.reuse("key"));
        }
        assertFalse(cache.active());
    }
    @Test void exceptionExitRevokesAndNextPassMustValidateAgain() {
        var cache=new NativePassSamplerCache<String,String>();var revoked=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->{
            try(var ignored=cache.open(true)){cache.arm();cache.remember("key","old");cache.granted(revoked::incrementAndGet);throw new IllegalStateException();}
        });
        assertEquals(1,revoked.get());assertFalse(cache.active());
        try(var ignored=cache.open(true)){cache.arm();assertNull(cache.reuse("key"));}
    }
    @Test void changedFlipResizeOrProgramIdentityCannotRestoreEarlierObservation() {
        var cache=new NativePassSamplerCache<String,String>();
        try(var ignored=cache.open(true)){
            cache.arm();cache.remember("program1/depth7/color8/res840","old");
            assertNull(cache.reuse("program1/depth7/color9/res840"));
            cache.remember("program1/depth7/color8/res840","restored");
            assertNull(cache.reuse("program1/depth7/color8/res840"));
        }
    }
}
