package com.pp.shaderlod.optimization.vrs;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BloomVrsSourceProofTest {
    @TempDir Path temp;
    private static Map<String,String> nativeOptions() throws Exception {
        try(var in=BloomVrsSourceProofTest.class.getResourceAsStream("/assets/shaderlod/bslquad/native384.json")){
            var out=new TreeMap<String,String>();
            JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().entrySet().forEach(e->out.put(e.getKey(),e.getValue().getAsString()));
            return out;
        }
    }
    @Test void exactNativeArchiveAndAll384OptionsAreRequired() throws Exception {
        Path original=Path.of("dependencies/upstream/other-pack-research/BSL_v10.1.8.zip");
        assertTrue(Files.isRegularFile(original),"Pinned authentic native input required");
        var options=nativeOptions();assertEquals(384,options.size());
        assertTrue(BloomVrsSourceProof.nativeArchive(original,options));
        for(String key:List.of("TAA","ADVANCED_MATERIALS","DISABLE_BLOOM")){
            var changed=new TreeMap<>(options);changed.put(key,"true".equals(changed.get(key))?"false":"true");
            assertFalse(BloomVrsSourceProof.nativeArchive(original,changed),key);
        }
        var extra=new TreeMap<>(options);extra.put("FOREIGN_OPT","1");
        assertFalse(BloomVrsSourceProof.nativeArchive(original,extra));
        Path foreign=temp.resolve("same-name.zip");Files.writeString(foreign,"not the admitted native archive");
        assertFalse(BloomVrsSourceProof.nativeArchive(foreign,options));
    }
    @Test void symbolicArchiveAndUnknownProfileStayNative() throws Exception {
        Path original=Path.of("dependencies/upstream/other-pack-research/BSL_v10.1.8.zip").toAbsolutePath();
        Path link=temp.resolve("native.zip");Files.createSymbolicLink(link,original);
        assertFalse(BloomVrsSourceProof.nativeArchive(link,nativeOptions()));
        assertFalse(BloomVrsSourceProof.optionsMatch(Map.of()));
    }
}
