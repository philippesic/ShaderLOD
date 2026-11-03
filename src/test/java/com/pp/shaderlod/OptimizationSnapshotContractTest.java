package com.pp.shaderlod;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import com.pp.shaderlod.optimization.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class OptimizationSnapshotContractTest {
    @Test void immutableSnapshotAndEditedSnapshotStayIndependent() {
        var input=new EnumMap<OptimizationId,Boolean>(OptimizationId.class);
        for(var id:OptimizationId.values())input.put(id,false);
        var before=new OptimizationSnapshot(input);
        var selected=OptimizationId.values()[0];
        input.put(selected,true);
        var after=before.with(selected,true);
        for(var id:OptimizationId.values()) {
            assertFalse(before.enabled(id));
            assertEquals(id==selected,after.enabled(id));
            assertEquals(after.flags().get(id)?1:0,after.uniform(id));
        }
        assertThrows(UnsupportedOperationException.class,()->after.flags().compute(selected,(k,v)->fail("Immutable map must not invoke callback")));
        assertThrows(UnsupportedOperationException.class,()->after.flags().entrySet().iterator().next().setValue(false));
        assertThrows(NullPointerException.class,()->after.enabled(null));
    }

    @Test void publicMapAndRecordSerializationRemainCompatible() throws Exception {
        var snapshot=OptimizationSnapshot.defaults();
        var gson=new Gson();
        assertEquals(snapshot,gson.fromJson(gson.toJson(snapshot),OptimizationSnapshot.class));
        var oldView=Collections.unmodifiableMap(new EnumMap<>(snapshot.flags()));
        assertArrayEquals(serialized(oldView),serialized(snapshot.flags()));
        assertEquals(oldView,snapshot.flags());
        assertEquals(oldView.hashCode(),snapshot.flags().hashCode());
    }

    private static byte[] serialized(Object value) throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var stream=new ObjectOutputStream(bytes)){stream.writeObject(value);}
        return bytes.toByteArray();
    }
}
