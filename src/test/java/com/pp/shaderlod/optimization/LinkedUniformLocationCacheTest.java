package com.pp.shaderlod.optimization;

import com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocationCache;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinkedUniformLocationCacheTest {
    private static final class Program {
        int id;Program(int id){this.id=id;}
        int handle(){if(id<0)throw new IllegalStateException("deleted");return id;}
    }
    @Test void presentAndMissingLocationsAreQueriedOncePerActualLink() {
        var queries=new AtomicInteger();var result=new AtomicInteger(-1);
        var cache=new LinkedUniformLocationCache<Program>(Program::handle,(id,name)->{queries.incrementAndGet();return result.get();});
        Object context=new Object(),pipeline=new Object();cache.register(5,pipeline,context,new Program(5));
        assertEquals(-1,cache.location(5,context,"absent"));result.set(9);
        assertEquals(-1,cache.location(5,context,"absent"));assertEquals(1,queries.get());
        assertEquals(9,cache.location(5,context,"present"));result.set(12);
        assertEquals(9,cache.location(5,context,"present"));assertEquals(2,queries.get());
        cache.register(5,pipeline,context,new Program(5));assertEquals(12,cache.location(5,context,"present"));
    }
    @Test void deletionContextMismatchAndUnknownProgramsRetainLiveLookup() {
        var queries=new AtomicInteger();var cache=new LinkedUniformLocationCache<Program>(Program::handle,(id,name)->queries.incrementAndGet());
        Object context=new Object();var program=new Program(5);cache.register(5,new Object(),context,program);
        assertEquals(1,cache.location(5,context,"p"));
        assertEquals(2,cache.location(5,new Object(),"p"));assertEquals(1,cache.location(5,context,"p"));
        program.id=-1;assertEquals(3,cache.location(5,context,"p"));assertEquals(4,cache.location(5,context,"p"));
        cache.register(5,new Object(),context,new Program(5));assertEquals(5,cache.location(5,context,"p"));
    }
    @Test void reloadAndPipelineDestructionCannotReuseEarlierLocations() {
        var queries=new AtomicInteger();var cache=new LinkedUniformLocationCache<Program>(Program::handle,(id,name)->queries.incrementAndGet());
        Object context=new Object(),a=new Object(),b=new Object();
        cache.register(5,a,context,new Program(5));cache.register(6,b,context,new Program(6));
        assertEquals(1,cache.location(5,context,"p"));assertEquals(2,cache.location(6,context,"p"));
        cache.destroy(a);assertEquals(3,cache.location(5,context,"p"));assertEquals(2,cache.location(6,context,"p"));
        cache.clear();assertEquals(4,cache.location(6,context,"p"));
    }
}
