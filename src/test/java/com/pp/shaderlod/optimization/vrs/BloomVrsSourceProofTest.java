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
        Path original=Path.of("dependencies/shortlist-test-inputs/originals/BSL_v10.1.8.zip");
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
    @Test void complementaryNativeProfilesOwnOnlyTheirActualBloomTarget() throws Exception {
        var reimagined=com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.nativeOptions();
        var unbound=com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.nativeOptions();
        Path r=Path.of("dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip");
        Path u=Path.of("dependencies/upstream/other-pack-research/ComplementaryUnbound_r5.9.3.zip");
        assertEquals(398,reimagined.size());assertEquals(398,unbound.size());
        assertEquals(3,BloomVrsSourceProof.outputTarget(reimagined));assertEquals(3,BloomVrsSourceProof.outputTarget(unbound));
        assertEquals(1,BloomVrsSourceProof.outputTarget(nativeOptions()));
        assertTrue(BloomVrsSourceProof.nativeArchive(r,reimagined));assertTrue(BloomVrsSourceProof.nativeArchive(u,unbound));
        assertFalse(BloomVrsSourceProof.nativeArchive(r,unbound));assertFalse(BloomVrsSourceProof.nativeArchive(u,reimagined));
        for(var options:List.of(reimagined,unbound))for(String key:List.of("TAA","RP_MODE","BLOOM_STRENGTH")) {
            var changed=new TreeMap<>(options);changed.put(key,"foreign");
            assertEquals(-1,BloomVrsSourceProof.outputTarget(changed));
        }
    }
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    @Test void symbolicArchiveAndUnknownProfileStayNative() throws Exception {
        Path original=Path.of("dependencies/shortlist-test-inputs/originals/BSL_v10.1.8.zip").toAbsolutePath();
        Path link=temp.resolve("native.zip");Files.createSymbolicLink(link,original);
        assertFalse(BloomVrsSourceProof.nativeArchive(link,nativeOptions()));
        assertFalse(BloomVrsSourceProof.optionsMatch(Map.of()));
    }
}
