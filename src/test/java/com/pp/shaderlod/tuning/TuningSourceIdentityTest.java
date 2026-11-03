package com.pp.shaderlod.tuning;
import org.junit.jupiter.api.Test;
import java.util.Properties;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class TuningSourceIdentityTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void authenticPublicGeneratedPairIsAcceptedAndChangedArchiveIsRefused()throws Exception {
        var original=directory.resolve("Original.zip");
        java.nio.file.Files.copy(java.nio.file.Path.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip"),original);
        var identity=TuningSourceIdentity.capture(directory,"Original.zip");
        identity.requireCurrent(directory,"Original.zip");
        var service=new com.pp.shaderlod.packs.PackSelectionService();
        var generated=service.apply(directory,"Original.zip",true,com.pp.shaderlod.LodPreset.PERFORMANCE,java.util.Map.of("AA_TYPE","2"),false);
        assertTrue(generated.accepted(),generated.detail());assertTrue(generated.generatedArchive());
        identity.requireCurrent(directory,generated.packFile());
        var returned=service.apply(directory,generated.packFile(),false,com.pp.shaderlod.LodPreset.PERFORMANCE,java.util.Map.of(),false);
        assertTrue(returned.accepted(),returned.detail());assertEquals("Original.zip",returned.packFile());
        identity.requireCurrent(directory,"Original.zip");
        java.nio.file.Files.write(directory.resolve(generated.packFile()),new byte[]{7},java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IOException.class,()->identity.requireCurrent(directory,"Original.zip"));
    }
    @Test void recoveryJournalRetainsExactPairAndSourceHash()throws Exception {
        var original=new TuningSourceIdentity("Unbound-A.zip","ShaderLOD-generated-A.zip","state-A.json","digest-A");
        var journal=new Properties();original.store(journal);
        assertEquals(original,TuningSourceIdentity.read(journal));
        journal.setProperty("source.original_file","Unbound-B.zip");
        assertNotEquals(original,TuningSourceIdentity.read(journal));
        journal.setProperty("source.original_file","Unbound-A.zip");journal.setProperty("source.original_sha256","digest-B");
        assertNotEquals(original,TuningSourceIdentity.read(journal));
    }
    @Test void familyOnlyLegacyJournalCannotAuthorizeGraphRecovery() {
        var old=new Properties();old.setProperty("family","complementary-unbound");old.setProperty("pack","Unbound.zip");
        assertThrows(IOException.class,()->TuningSourceIdentity.read(old));
    }
}
