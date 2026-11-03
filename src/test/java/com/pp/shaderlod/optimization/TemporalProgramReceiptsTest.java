package com.pp.shaderlod.optimization;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.pp.shaderlod.optimization.sourcecache.TemporalProgramReceipts;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TemporalProgramReceiptsTest {
    static final class Program {int id;Program(int id){this.id=id;}int handle(){if(id<0)throw new IllegalStateException("deleted");return id;}}
    @Test void repeatedNativeDrawsDoNotCallLegacySourceAndMissingOrStaleRefuses(){
        var receipts=new TemporalProgramReceipts<Program,String>(Program::handle);var sourceCalls=new AtomicInteger();
        java.util.function.BooleanSupplier source=()->{sourceCalls.incrementAndGet();return true;};
        receipts.nativeProgram(7,new Program(7),"current",Map.of("permission",3,"receiver",-1));
        for(int i=0;i<287;i++)assertTrue(receipts.allows(7,"current"::equals,source));
        assertEquals(0,sourceCalls.get());assertEquals(287L,receipts.counters().get("compiled"));
        assertFalse(receipts.allows(7,"changed"::equals,source));
        receipts.nativeProgram(7,new Program(7),null,Map.of());assertFalse(receipts.allows(7,c->true,source));
        assertFalse(receipts.allows(8,c->true,source));assertEquals(0,sourceCalls.get());
        assertEquals(3L,receipts.counters().get("refused"));
    }
    @Test void destroyedAndReusedHandleNeverInheritsProofAndExplicitDerivativeDropsNative(){
        var receipts=new TemporalProgramReceipts<Program,String>(Program::handle);var old=new Program(7);
        receipts.nativeProgram(7,old,"first",Map.of("permission",3));old.id=-1;
        assertFalse(receipts.allows(7,c->true,()->{fail("native must not fall back");return true;}));
        assertNull(receipts.location(7,"permission"));
        receipts.nativeProgram(7,new Program(7),null,Map.of());assertFalse(receipts.allows(7,c->true,()->true));
        receipts.nativeProgram(7,new Program(7),"second",Map.of("permission",8));assertEquals(8,receipts.location(7,"permission"));
        receipts.legacyProgram(7);assertFalse(receipts.nativeProgram(7));assertNull(receipts.location(7,"permission"));
        var calls=new AtomicInteger();assertTrue(receipts.allows(7,c->false,()->{calls.incrementAndGet();return true;}));
        assertEquals(1,calls.get());assertEquals(1L,receipts.counters().get("legacy"));
        receipts.forget(7);assertFalse(receipts.allows(7,c->true,()->true));
    }
    @Test void clearRevokesAllOwnersAndMissingLocationsStayMissing(){
        var receipts=new TemporalProgramReceipts<Program,String>(Program::handle);
        receipts.nativeProgram(2,new Program(2),"proof",Map.of("receiver",-1));
        assertEquals(-1,receipts.location(2,"receiver"));assertNull(receipts.location(2,"absent"));
        receipts.legacyProgram(3);receipts.clear();
        for(int id:List.of(2,3))assertFalse(receipts.allows(id,c->true,()->true));
    }
}
