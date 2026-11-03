package com.pp.shaderlod.optimization.mips;
import com.google.gson.JsonParser;
import com.pp.shaderlod.packs.SupportedPackCatalog;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** A producer update must not silently strand the normal mip feature on a legacy source pin. */
class DeadMipSourcePinJoinTest {
 @Test void exactProofPinsFollowCurrentProducerAndPackagedResource()throws Exception {
  var proof=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/shaderlod/adapters/qualification/dead-mip-admission.json"))).getAsJsonObject();
  for(var row:proof.getAsJsonObject("families").entrySet()) {
   var pack=SupportedPackCatalog.inventory().stream().filter(p->p.id().equals(row.getKey())).findFirst().orElseThrow();
   assertEquals(pack.adapterSourceDigest(),row.getValue().getAsJsonObject().get("source").getAsString(),row.getKey());
  }
  assertFalse(DeadMipAdmission.leanProfile().isEmpty(),"Compiled qualification resource checksum must match");
 }
}
