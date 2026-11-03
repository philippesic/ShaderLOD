package com.pp.shaderlod.tuning;

import com.pp.shaderlod.optimization.OptimizationId;
import com.pp.shaderlod.optimization.OptimizationSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Full-search coverage only. No timing samples or decisions are carried between segments. */
final class TuningContinuation {
    final String scope,enginePreset;
    final Set<String> completed=new LinkedHashSet<>();
    OptimizationSnapshot incumbent=OptimizationSnapshot.noneRequested();
    boolean resumed,validatedIncumbent;
    final Properties originalRecovery=new Properties(),terminalProfile=new Properties();
    TuningContinuation(String scope,Collection<String> inventory,Properties saved) {this(scope,inventory,saved,"PERFORMANCE");}
    TuningContinuation(String scope,Collection<String> inventory,Properties saved,String enginePreset) {
        this.scope=scope;this.enginePreset=Objects.requireNonNull(enginePreset);
        if(!"1".equals(saved.getProperty("schema"))||!scope.equals(saved.getProperty("scope")))return;
        var allowed=new HashSet<>(inventory);
        for(String name:saved.getProperty("completed","").split(","))if(!name.isBlank()) {
            if(!allowed.contains(name))return;
        }
        var flags=OptimizationSnapshot.noneRequested();
        for(var id:OptimizationId.values()) {
            String value=saved.getProperty("incumbent."+id.key(),"false");
            if(!Set.of("true","false").contains(value))return;
            flags=flags.with(id,Boolean.parseBoolean(value));
        }
        for(String name:saved.getProperty("completed","").split(","))if(!name.isBlank())completed.add(name);
        incumbent=flags;validatedIncumbent="true".equals(saved.getProperty("validated_incumbent"));resumed=!completed.isEmpty();
        for(String key:saved.stringPropertyNames()) {
            if(key.startsWith("recovery."))originalRecovery.setProperty(key.substring(9),saved.getProperty(key));
            if(key.startsWith("terminal."))terminalProfile.setProperty(key.substring(9),saved.getProperty(key));
        }
    }
    Properties saved(OptimizationSnapshot validatedProfile,boolean savedProfile) {
        if(savedProfile){incumbent=validatedProfile;validatedIncumbent=true;}
        var result=new Properties();result.setProperty("schema","1");result.setProperty("scope",scope);
        result.setProperty("completed",String.join(",",completed));result.setProperty("validated_incumbent",Boolean.toString(validatedIncumbent));
        result.setProperty("incumbent_policy","Previously saved requests only; fresh calibration and native-relative final validation required in every segment; old timings never reused");
        for(var id:OptimizationId.values())result.setProperty("incumbent."+id.key(),Boolean.toString(incumbent.enabled(id)));
        for(String key:originalRecovery.stringPropertyNames())result.setProperty("recovery."+key,originalRecovery.getProperty(key));
        for(String key:terminalProfile.stringPropertyNames())result.setProperty("terminal."+key,terminalProfile.getProperty(key));
        return result;
    }
    boolean acceptsStart(boolean enabled,String preset,OptimizationSnapshot flags) {
        if(!resumed)return true;
        if(validatedIncumbent&&enabled&&enginePreset.equals(preset)&&incumbent.equals(flags))return true;
        return matches(originalRecovery,enabled,preset,flags)||matches(terminalProfile,enabled,preset,flags);
    }
    void terminal(boolean enabled,String preset,OptimizationSnapshot flags) {
        terminalProfile.clear();terminalProfile.setProperty("enabled",Boolean.toString(enabled));terminalProfile.setProperty("preset",preset);
        for(var id:OptimizationId.values())terminalProfile.setProperty(id.key(),Boolean.toString(flags.enabled(id)));
    }
    private static boolean matches(Properties snapshot,boolean enabled,String preset,OptimizationSnapshot flags) {
        String expected=snapshot.getProperty("enabled");
        if(!Set.of("true","false").contains(Objects.toString(expected,""))
                ||enabled!=Boolean.parseBoolean(expected)||!preset.equals(snapshot.getProperty("preset")))return false;
        for(var id:OptimizationId.values()) {
            String value=snapshot.getProperty(id.key());
            if(!Set.of("true","false").contains(Objects.toString(value,""))||flags.enabled(id)!=Boolean.parseBoolean(value))return false;
        }
        return true;
    }
    /** Same minimal boundary as MakeUpQuadRuntime.resourcesMatch; external IDs do not bind content. */
    static boolean resourcesAllowResume(Collection<String> selected) {
        return selected!=null&&selected.size()==2&&selected.contains("vanilla")&&selected.contains("mod_resources");
    }
    static String scope(Map<String,String> pins) {
        try {
            var text=new StringBuilder();new TreeMap<>(pins).forEach((key,value)->text.append(key).append('\0').append(value).append('\n'));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
