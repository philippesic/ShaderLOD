package com.pp.shaderlod.tuning;

import com.pp.shaderlod.optimization.*;
import java.io.IOException;
import java.util.*;

/** Pure assembly for the normal post-SAVED hook. Does not run or certify a measurement. */
final class TuningMeasuredPreset {
    private static final Set<String> EVIDENCE=Set.of("metadata.properties","decisions.csv","samples.csv","undo-original.properties");
    private TuningMeasuredPreset(){}
    static TuningPreset assemble(TuningPreset current,OptimizationSnapshot flags,String goal,String runId,
            Properties metadata,TuningJournal.Original original,Map<String,String> fileHashes)throws IOException {
        if(!"SAVED".equals(metadata.getProperty("result")))throw new IOException("Only an actual caller's SAVED terminal state may be assembled");
        final TuningGoal target;
        try{target=TuningGoal.valueOf(goal);}catch(RuntimeException invalid){throw new IOException("Unknown measured search goal",invalid);}
        if(!goal.equals(metadata.getProperty("tuning_goal"))||!target.enginePreset().name().equals(metadata.getProperty("tuning_engine_preset"))
                ||current.enginePreset()!=target.enginePreset()||!TuningTierPolicy.VERSION.equals(metadata.getProperty("tuning_policy")))
            throw new IOException("Measured goal/engine/policy binding differs");
        var measuredPins=new TreeMap<String,String>();for(String key:metadata.stringPropertyNames())measuredPins.put(key,metadata.getProperty(key));
        try{TuningApplyPins.requireValid(current.context());TuningApplyPins.requireValid(measuredPins);}
        catch(IllegalStateException invalid){throw new IOException("Invalid measured source/artifact/device identity",invalid);}
        for(String key:List.of("mod_artifact_sha256","mod_artifact_manifest_sha256","gpu_vendor","gpu_renderer","gl_version","resolution","java","os","render_distance"))
            if(current.context().get(key)==null||current.context().get(key).isBlank()||!Objects.equals(current.context().get(key),metadata.getProperty(key)))throw new IOException("Measured context differs: "+key);
        if(!original.family().equals(current.family())||!original.source().originalSourceSha256().equals(current.originalSourceSha256()))
            throw new IOException("Measured original source/family differs from the selected tuning");
        var requestKeys=new TreeSet<String>();for(var id:OptimizationId.values())requestKeys.add(id.key());
        var terminal=prefix(metadata,"final_profile.");
        if(!terminal.keySet().equals(requestKeys))throw new IOException("Measured result must contain exactly all canonical requests");
        for(var id:OptimizationId.values())if(!Boolean.toString(flags.enabled(id)).equals(terminal.get(id.key())))throw new IOException("Tuning result flags mismatch: "+id.key());
        if(current.terrainPolicy()==null
                ||!Integer.toString(current.terrainPolicy().chunkCap()).equals(metadata.getProperty("final.terrain_cap"))
                ||!Integer.toString(current.terrainPolicy().warmupSeconds()).equals(metadata.getProperty("final.terrain_warmup_seconds"))
                ||!current.enabled()||!current.requests().equals(flags)||!current.nativeOptions().equals(prefix(metadata,"final.native_option."))
                ||!current.effectOptions().equals(prefix(metadata,"final.effect_option."))
                ||!Double.toString(current.bands().near()).equals(metadata.getProperty("final.near"))
                ||!Double.toString(current.bands().far()).equals(metadata.getProperty("final.far"))
                ||!Double.toString(current.bands().transition()).equals(metadata.getProperty("final.transition"))
                ||!Boolean.toString(current.bands().debug()).equals(metadata.getProperty("final.debug"))
                ||!Boolean.toString(current.enabled()).equals(metadata.getProperty("final_enabled"))
                ||!current.enginePreset().name().equals(metadata.getProperty("final_preset")))throw new IOException("Current full configuration differs from measured result");
        var rejected=TuningTierPolicy.rejectedVector(target,current.family(),flags,current.nativeOptions(),current.effectOptions());
        if(!rejected.isEmpty())throw new IOException("Measured configuration violates goal/source policy: "+rejected);
        if(!fileHashes.keySet().equals(EVIDENCE))throw new IOException("Complete normal evidence hashes required");
        try{for(String hash:fileHashes.values())TuningPreset.requireHash(hash);}
        catch(IllegalArgumentException invalid){throw new IOException("Invalid evidence hash",invalid);}
        if(runId==null||!runId.matches("tune-[A-Za-z0-9._-]{1,180}"))throw new IOException("Invalid owned run ID");
        var context=new TreeMap<>(current.context());context.put("tuning_goal",goal);context.put("evidence_configuration_sha256",current.configurationSha256());
        return new TuningPreset(current.id(),current.name(),current.family(),current.originalSourceSha256(),current.enabled(),current.enginePreset(),
            current.bands(),current.terrainPolicy(),current.requests(),current.nativeOptions(),current.effectOptions(),context,
            new TuningPreset.Evidence(TuningPreset.Provenance.MEASURED_LOCAL,goal,runId,fileHashes,"UNREVIEWED"),current.createdAt());
    }
    private static Map<String,String> prefix(Properties p,String prefix){
        var values=new TreeMap<String,String>();for(String key:p.stringPropertyNames())if(key.startsWith(prefix))values.put(key.substring(prefix.length()),p.getProperty(key));return values;
    }
}
