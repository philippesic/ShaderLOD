package com.pp.shaderlod.tuning;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.packs.PackSelectionService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/** Recovery admits only the captured original and its strictly verified generated pair. */
record TuningSourceIdentity(String originalFile,String generatedFile,String stateFile,String originalSourceSha256) {
    static TuningSourceIdentity capture(Path directory,String selected)throws IOException {
        // This existing read-only operation validates generated marker, recipe, state and original archive.
        var pair=PackSelectionService.planSelection(directory,selected,false,LodPreset.QUALITY);
        if(pair==null)throw new IOException("No verified original/generated pair for tuner recovery");
        return new TuningSourceIdentity(pair.originalFile(),pair.generatedFile(),pair.stateFile(),
            PackSourceReader.read(directory.resolve(pair.originalFile())).sourceSha256());
    }
    void requireCurrent(Path directory,String selected)throws IOException {
        var current=capture(directory,selected);
        if(!equals(current)||!Objects.equals(selected,originalFile)&&!Objects.equals(selected,generatedFile))
            throw new IOException("Selected shader is outside the captured original/generated source pair; graph recovery refused");
    }
    void store(Properties p){p.setProperty("source.original_file",originalFile);p.setProperty("source.generated_file",generatedFile);p.setProperty("source.state_file",stateFile);p.setProperty("source.original_sha256",originalSourceSha256);}
    static TuningSourceIdentity read(Properties p)throws IOException {
        for(String k:new String[]{"original_file","generated_file","state_file","original_sha256"})if(p.getProperty("source."+k)==null)throw new IOException("Undo journal lacks strict source identity; graph recovery refused");
        return new TuningSourceIdentity(p.getProperty("source.original_file"),p.getProperty("source.generated_file"),p.getProperty("source.state_file"),p.getProperty("source.original_sha256"));
    }
}
