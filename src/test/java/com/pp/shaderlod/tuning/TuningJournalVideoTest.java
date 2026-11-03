package com.pp.shaderlod.tuning;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.OptimizationSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TuningJournalVideoTest {
    @TempDir Path directory;
    @Test void undoRetainsEffectiveWindowLimitSeparatelyFromSavedOption() throws Exception {
        var original=new TuningJournal.Original("makeup-ultra-fast-shaders",false,LodPreset.PERFORMANCE,
            OptimizationSnapshot.noneRequested(),true,120,90,"MakeUp.zip",
            new TuningSourceIdentity("MakeUp.zip","Generated.zip","state.json","source-digest"));
        var journal=new TuningJournal(directory,original,Map.of(),300,false);
        journal.finish("RESTORED",original.flags());
        assertEquals(original,TuningJournal.read(journal.activeFile));
        // Previously delivered journals omitted the Window value; retain their native saved option.
        var old=TuningJournal.properties(journal.activeFile);old.remove("window_fps");
        TuningJournal.atomic(journal.activeFile,old);
        var restored=TuningJournal.read(journal.activeFile);
        assertEquals(120,restored.windowFps());assertEquals(original.source(),restored.source());
        assertEquals(original.flags(),restored.flags());assertTrue(restored.vsync());
    }
    @Test void parkedRecoveryPersistsExactOriginalAcrossRestartAndExplicitRetry() throws Exception {
        var original=new TuningJournal.Original("complementary-unbound",false,LodPreset.QUALITY,
            OptimizationSnapshot.noneRequested(),true,144,120,"Original.zip",new TuningSourceIdentity("Original.zip","Generated.zip","state.json","digest"));
        var journal=new TuningJournal(directory,original,Map.of(),3600,true);
        journal.parkRecovery("source mismatch after three failures",3);
        assertEquals("PENDING",TuningJournal.properties(journal.activeFile).getProperty("state"));
        assertEquals("RESTORATION_PENDING",TuningJournal.properties(journal.directory.resolve("metadata.properties")).getProperty("result"));
        assertEquals(original,TuningJournal.read(journal.activeFile)); // Actual normal Undo reader after a fresh JVM.
        var retried=TuningJournal.properties(journal.activeFile);assertEquals(original,TuningJournal.read(retried));
        // Successful explicit Undo acknowledges only after the caller has restored this snapshot.
        retried.setProperty("state","UNDONE");TuningJournal.atomic(journal.activeFile,retried);
        assertEquals("UNDONE",TuningJournal.properties(journal.activeFile).getProperty("state"));
        assertEquals(original,TuningJournal.read(journal.activeFile));
    }
}
