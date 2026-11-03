package com.pp.shaderlod.optimization;

import com.pp.shaderlod.optimization.sourcecache.LinkedProgramLocations;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinkedProgramLocationsTest {
    private static final class Program {
        int id;
        Program(int id) { this.id=id; }
        int handle() { if(id<0)throw new IllegalStateException("deleted");return id; }
    }
    @Test void deletedObjectCannotLendLocationsToReusedGlName() {
        var cache=new LinkedProgramLocations<Program,String>(Program::handle);
        var original=new Program(7);
        cache.put(7,original,"old link",Map.of("permission",3,"missing",-1));
        assertEquals(-1,cache.current(7).locations().get("missing"));
        original.id=-1;
        assertNull(cache.current(7));
        var reused=new Program(7);cache.observe(7,reused);
        assertNull(cache.current(7));
        cache.put(7,reused,"new link",Map.of("permission",9));
        assertEquals(9,cache.current(7).locations().get("permission"));
    }
    @Test void eitherNativeRegistrationOrderPreservesOnlyExactObject() {
        var cache=new LinkedProgramLocations<Program,String>(Program::handle);
        var first=new Program(4);
        cache.observe(4,first);cache.put(4,first,"proof",Map.of("p",2));
        cache.observe(4,first);assertEquals("proof",cache.current(4).certificate());
        cache.observe(4,new Program(4));assertNull(cache.current(4));
        cache.put(4,first,"proof",Map.of("p",2));cache.forget(4);
        assertNull(cache.current(4),"uncertified supplier/derived registration cannot inherit native proof");
    }
    @Test void locationsAreImmutableAndLifecycleRevocationIsScoped() {
        var cache=new LinkedProgramLocations<Program,String>(Program::handle);
        var locations=new HashMap<>(Map.of("p",2));
        cache.put(4,new Program(4),"pipeline A",locations);locations.put("p",8);
        cache.put(5,new Program(5),"pipeline B",Map.of("p",3));
        assertEquals(2,cache.current(4).locations().get("p"));
        assertThrows(UnsupportedOperationException.class,()->cache.current(4).locations().put("p",9));
        cache.removeIf("pipeline A"::equals);assertNull(cache.current(4));assertNotNull(cache.current(5));
        cache.clear();assertNull(cache.current(5));
    }
    @Test void rejectedReusedNameEvictsCertificateAndLegacyOwnerInBothRegistrationPaths() {
        for(boolean nativeHook:new boolean[]{true,false}) {
            var cache=new LinkedProgramLocations<Program,String>(Program::handle);
            var owners=new HashMap<Integer,String>();var terrain=new Program(7);
            cache.registerNative(owners,7,terrain,"terrain");
            cache.put(7,terrain,"certificate",Map.of("permission",2));
            terrain.id=-1;var rejected=new Program(7);
            if(nativeHook)cache.registerNative(owners,7,rejected,null);
            else cache.registerUncertified(owners,7,null);
            assertFalse(owners.containsKey(7),"rejected registration must remove old terrain ownership");
            assertNull(cache.current(7));
        }
    }
    @Test void nativeAndCertifiedHookOrderKeepsCurrentLegacyFallbackAndExactProof() {
        var cache=new LinkedProgramLocations<Program,String>(Program::handle);
        var owners=new HashMap<Integer,String>();var terrain=new Program(7);
        cache.registerUncertified(owners,7,"terrain");
        assertEquals("terrain",owners.get(7));assertNull(cache.current(7));
        cache.put(7,terrain,"certificate",Map.of("p",2));
        cache.registerNative(owners,7,terrain,"terrain");
        assertEquals("certificate",cache.current(7).certificate());
        assertEquals("terrain",owners.get(7));
    }
}
