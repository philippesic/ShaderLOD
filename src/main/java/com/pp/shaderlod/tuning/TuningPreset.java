package com.pp.shaderlod.tuning;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.OptimizationId;
import com.pp.shaderlod.optimization.OptimizationSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Portable settings and evidence, never authority to bypass a selected consumer's guards. */
public record TuningPreset(String id, String name, String family, String originalSourceSha256,
        boolean enabled, LodPreset enginePreset, Bands bands, TerrainPolicy terrainPolicy, OptimizationSnapshot requests,
        Map<String,String> nativeOptions, Map<String,String> effectOptions, Map<String,String> context, Evidence evidence, String createdAt) {
    public enum Provenance {
        MANUAL, MEASURED_LOCAL, IMPORTED_UNVERIFIED;
        public String label(){return switch(this){case MANUAL->"Custom settings";case MEASURED_LOCAL->"Measured result";case IMPORTED_UNVERIFIED->"Imported settings";};}
    }
    public record Bands(double near, double far, double transition, boolean debug) {
        public Bands {
            if(!Double.isFinite(near)||!Double.isFinite(far)||!Double.isFinite(transition)
                    ||near<1||near>4095||far<2||far>4096||far<=near||transition<0||transition>512)
                throw new IllegalArgumentException("Invalid tuning distance bands");
        }
    }
    public record TerrainPolicy(int chunkCap,int warmupSeconds) {
        public TerrainPolicy {
            if(chunkCap<8||chunkCap>32||warmupSeconds<5||warmupSeconds>120)throw new IllegalArgumentException("Invalid terrain tuning preferences");
        }
    }
    /** Reading an older unmeasured preset preserves unspecified terrain preferences. */
    public TuningPreset(String id,String name,String family,String source,boolean enabled,LodPreset enginePreset,Bands bands,OptimizationSnapshot requests,
            Map<String,String> nativeOptions,Map<String,String> effects,Map<String,String> context,Evidence evidence,String createdAt) {
        this(id,name,family,source,enabled,enginePreset,bands,null,requests,nativeOptions,effects,context,evidence,createdAt);
    }
    public record Evidence(Provenance provenance, String goal, String runId,
            Map<String,String> fileSha256, String visualReview) {
        public Evidence {
            Objects.requireNonNull(provenance);Objects.requireNonNull(goal);Objects.requireNonNull(runId);
            fileSha256=boundedMap(fileSha256,16,256,64);
            for(var e:fileSha256.entrySet()) {
                if(!Set.of("metadata.properties","decisions.csv","samples.csv","undo-original.properties").contains(e.getKey()))
                    throw new IllegalArgumentException("Unknown evidence member");
                requireHash(e.getValue());
            }
            if(!runId.isEmpty()&&!runId.matches("tune-[A-Za-z0-9._-]{1,180}"))throw new IllegalArgumentException("Invalid evidence run ID");
            if(provenance==Provenance.MEASURED_LOCAL&&(runId.isEmpty()||!fileSha256.keySet().containsAll(Set.of("metadata.properties","decisions.csv","samples.csv"))))
                throw new IllegalArgumentException("Measured tuning needs retained local evidence");
            if(!"UNREVIEWED".equals(visualReview))throw new IllegalArgumentException("Automatic tunings do not certify visual quality");
        }
        public static Evidence manual(){return new Evidence(Provenance.MANUAL,"CUSTOM","",Map.of(),"UNREVIEWED");}
        public Evidence imported(){return new Evidence(Provenance.IMPORTED_UNVERIFIED,goal,runId,fileSha256,"UNREVIEWED");}
    }
    public TuningPreset {
        UUID.fromString(id);name=Objects.requireNonNull(name).strip();
        if(name.isEmpty()||name.codePointCount(0,name.length())>64||name.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Use a tuning name with 1–64 printable characters");
        if(!Set.of("photon","solas-shader","astralex","complementary-unbound","complementary-reimagined",
                "bliss-shader","bsl-shaders","sildurs-vibrant-shaders","makeup-ultra-fast-shaders").contains(family))
            throw new IllegalArgumentException("Unknown tuning shader family");
        requireHash(originalSourceSha256);Objects.requireNonNull(enginePreset);Objects.requireNonNull(bands);
        Objects.requireNonNull(requests);Objects.requireNonNull(evidence);Instant.parse(createdAt);
        if(terrainPolicy==null&&requests.enabled(OptimizationId.TERRAIN_RADIUS))throw new IllegalArgumentException("This older tuning lacks the terrain preferences needed by its terrain-radius request");
        if(terrainPolicy==null&&evidence.provenance()==Provenance.MEASURED_LOCAL)evidence=evidence.imported();
        nativeOptions=boundedMap(nativeOptions,2048,256,2048);
        if(nativeOptions.isEmpty())throw new IllegalArgumentException("Tuning lacks native shader settings");
        effectOptions=boundedMap(effectOptions,256,256,2048);
        effectOptions=com.pp.shaderlod.lod.EffectTuningValues.canonical(effectOptions);
        context=boundedMap(context,128,256,65536);
    }
    public TuningPreset renamed(String nextName) {
        return new TuningPreset(id,nextName,family,originalSourceSha256,enabled,enginePreset,bands,terrainPolicy,requests,nativeOptions,effectOptions,context,evidence,createdAt);
    }
    public TuningPreset importedCopy() {
        return new TuningPreset(UUID.randomUUID().toString(),name,family,originalSourceSha256,enabled,enginePreset,bands,terrainPolicy,requests,
                nativeOptions,effectOptions,context,evidence.imported(),Instant.now().toString());
    }
    /** Display names and provenance do not change the executable configuration. */
    public String configurationSha256() {
        return configurationSha256(true,true);
    }
    /** Check the stored 61-flag identity before accepting a schema 1/2 migration. */
    String legacyConfigurationSha256() {
        return configurationSha256(false,false);
    }
    String previousConfigurationSha256() {return configurationSha256(true,false);}
    private String configurationSha256(boolean includeDeadFrameCopy,boolean includeExact) {
        var fields=new TreeMap<String,String>();fields.put("family",family);fields.put("source",originalSourceSha256);
        fields.put("enabled",Boolean.toString(enabled));fields.put("engine",enginePreset.name());fields.put("bands",bands.toString());
        if(terrainPolicy!=null){fields.put("terrain.chunk_cap",Integer.toString(terrainPolicy.chunkCap()));fields.put("terrain.warmup_seconds",Integer.toString(terrainPolicy.warmupSeconds()));}
        for(var id:OptimizationId.values()) {
            if((includeDeadFrameCopy||id!=OptimizationId.DEAD_FRAME_COPY)&&(includeExact||!Set.of(OptimizationId.TRANSLUCENT_FASTPATH,OptimizationId.AO_ATTENUATION).contains(id)))
                fields.put("request."+id.key(),Boolean.toString(requests.enabled(id)));
        }
        nativeOptions.forEach((k,v)->fields.put("native."+k,v));
        effectOptions.forEach((k,v)->fields.put("effect."+k,v));
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            fields.forEach((k,v)->{digest.update((k.length()+":"+k+v.length()+":"+v).getBytes(StandardCharsets.UTF_8));});
            return HexFormat.of().formatHex(digest.digest());
        }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    public void requireCompatible(String selectedFamily,String source,Map<String,String> nativeValues) {
        if(!family.equals(selectedFamily))throw new IllegalArgumentException("Select "+familyLabel()+" before applying this tuning");
        if(!originalSourceSha256.equals(source))throw new IllegalArgumentException("Use the original shader version this tuning was saved for");
        if(!nativeOptions.equals(nativeValues)) {
            var differences=new ArrayList<String>();
            for(var e:nativeOptions.entrySet())if(!Objects.equals(e.getValue(),nativeValues.get(e.getKey()))) {
                differences.add(e.getKey()+"="+e.getValue()+" (now "+Objects.toString(nativeValues.get(e.getKey()),"missing")+")");
                if(differences.size()==3)break;
            }
            throw new IllegalArgumentException("Native shader settings differ: "+String.join("; ",differences)+". Select the saved native settings first.");
        }
    }
    public String familyLabel(){return switch(family){case "photon"->"Photon";case "solas-shader"->"Solas";case "astralex"->"AstraLex";case "bsl-shaders"->"BSL";case "bliss-shader"->"Bliss";case "makeup-ultra-fast-shaders"->"MakeUp UltraFast";case "sildurs-vibrant-shaders"->"Sildur's Vibrant";case "complementary-reimagined"->"Complementary Reimagined";default->"Complementary Unbound";};}
    static void requireHash(String hash){if(hash==null||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid SHA-256");}
    static Map<String,String> boundedMap(Map<String,String> source,int count,int keyLength,int valueLength) {
        Objects.requireNonNull(source);if(source.size()>count)throw new IllegalArgumentException("Tuning map exceeds limit");
        var out=new TreeMap<String,String>();source.forEach((k,v)->{
            if(k==null||v==null||k.length()>keyLength||v.length()>valueLength||k.indexOf('\0')>=0||v.indexOf('\0')>=0)
                throw new IllegalArgumentException("Invalid tuning map entry");out.put(k,v);
        });return Collections.unmodifiableMap(out);
    }
}
