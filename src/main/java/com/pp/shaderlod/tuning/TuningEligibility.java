package com.pp.shaderlod.tuning;

import com.pp.shaderlod.optimization.OptimizationId;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Source applicability is separate from timed outcomes and observed pixel coverage. */
public final class TuningEligibility {
    public enum State { DECLARED_CONSUMER, INACTIVE_NATIVE_OPTION, UNSUPPORTED, UNQUALIFIED_PROTOTYPE, DUPLICATE_LABEL, UNKNOWN }
    public record Entry(State state,String reason) {}
    public static final Set<String> FAMILIES=Set.of("photon","complementary-reimagined","complementary-unbound","bsl-shaders",
        "makeup-ultra-fast-shaders","sildurs-vibrant-shaders","solas-shader","bliss-shader","astralex");
    private static final Map<String,Map<OptimizationId,Entry>> DECLARATIONS=declarations();
    private static final Set<OptimizationId> POM=Set.of(OptimizationId.PROJECTED_POM,OptimizationId.POM_HEIGHT_CACHE,OptimizationId.POM_EMPTY_HEIGHT_PREFIX);
    private static final Set<OptimizationId> AO=Set.of(OptimizationId.AO_BUDGET,OptimizationId.AO_DEPTH_REUSE,OptimizationId.AO_RENDER_SCALE,OptimizationId.AO_SKY_ELISION);
    private TuningEligibility() {}
    public static Entry classify(String family,OptimizationId id) {return classify(family,id,Map.of());}
    public static Entry classify(String family,OptimizationId id,Map<String,String> nativeOptions) {
        Objects.requireNonNull(nativeOptions);
        if(!FAMILIES.contains(family))return new Entry(State.UNKNOWN,"Unrecognized family; no support inferred");
        if(id==OptimizationId.TERRAIN_RADIUS)return new Entry(State.UNSUPPORTED,
            "MANUAL_CONFIRMATION_REQUIRED: implemented DH terrain control; each tuner Apply revokes inspected warm-scene coverage. Use the separate manual confirmation path; automatic comparisons retain native radius.");
        var declared=DECLARATIONS.getOrDefault(family,Map.of()).get(id);
        if(declared!=null&&declared.state()!=State.DECLARED_CONSUMER)return declared;
        String inactive=inactive(family,id,nativeOptions);
        if(inactive!=null&&declared!=null)return new Entry(State.INACTIVE_NATIVE_OPTION,inactive);
        if(declared!=null)return declared;
        return switch(id) {
            case SHADOW_PREFIX->new Entry(State.DUPLICATE_LABEL,TuningCandidates.unavailableReason(id));
            case PHYSICAL_RENDER_SCALE->new Entry(State.UNQUALIFIED_PROTOTYPE,TuningCandidates.unavailableReason(id));
            case SPATIAL_SHADOW_REUSE->new Entry(State.UNSUPPORTED,TuningCandidates.unavailableReason(id));
            default->new Entry(State.UNKNOWN,FAMILIES.contains(family)?"No explicit family/native-option applicability declaration; timing a request does not prove an active consumer":"Unrecognized family; no support inferred");
        };
    }
    /** Exact native options only; missing values never imply inactive work. */
    private static String inactive(String family,OptimizationId id,Map<String,String> o) {
        if(family.equals("makeup-ultra-fast-shaders")&&AO.contains(id)&&"0".equals(o.get("AO")))return "Native MakeUp AO=0; no selected AO work";
        if(family.equals("bsl-shaders")) {
            if(AO.contains(id)&&"false".equals(o.get("AO")))return "Native BSL AO=false";
            if(id==OptimizationId.AURORA_BUDGET&&"0".equals(o.get("AURORA")))return "Native BSL AURORA=0";
            if(POM.contains(id)&&("false".equals(o.get("ADVANCED_MATERIALS"))||"false".equals(o.get("PARALLAX"))))return "Native BSL material POM requires ADVANCED_MATERIALS and PARALLAX";
        }
        if(family.equals("sildurs-vibrant-shaders")) {
            if(POM.contains(id)&&Set.of("0","1").contains(Objects.toString(o.get("nMap"),"")))return "Native Sildur material POM requires nMap=2";
            if(AO.contains(id)&&"false".equals(o.get("SSDO")))return "Native Sildur SSDO=false";
            if(id==OptimizationId.VOLUMETRIC_LIGHT_BUDGET&&"false".equals(o.get("Volumetric_Lighting")))return "Native Sildur Volumetric_Lighting=false; Godrays is a separate consumer";
            if(Set.of(OptimizationId.SHADOW_SAMPLE_BUDGET,OptimizationId.TEMPORAL_SHADOW_FILTER,OptimizationId.SHADOW_RECEIVER_FOOTPRINT).contains(id)&&"false".equals(o.get("Shadows")))return "Native Sildur Shadows=false";
            if(id==OptimizationId.WATER_SHADOW_ZERO&&"true".equals(o.get("Caustics")))return "Sildur water-shadow-zero requires native Caustics disabled";
        }
        if(family.equals("photon")) {
            // This flag only changes the rough opaque multi-ray branch. The smooth
            // and water reflection paths do not consume it, even if SSR is active.
            if(id==OptimizationId.REFLECTION_RAY_BUDGET&&List.of("SSR_ROUGHNESS_SUPPORT","SPECULAR_MAPPING").stream()
                    .anyMatch(key->"false".equals(o.get(key))))
                return "Photon rough reflection ray budget requires native SSR_ROUGHNESS_SUPPORT and SPECULAR_MAPPING; smooth/water reflections do not consume this ray count";
            // The early-alpha source/compile guard accepts only the optional material profile.
            // A request against native defaults cannot prepare or own its CUTOUT consumer.
            // Missing values retain the existing unknown-coverage behavior.
            if(id==OptimizationId.EARLY_ALPHA&&List.of("POM","NORMAL_MAPPING","SPECULAR_MAPPING").stream()
                    .anyMatch(key->"false".equals(o.get(key))))
                return "Photon early-alpha admission requires native POM, NORMAL_MAPPING and SPECULAR_MAPPING enabled; selected options cannot qualify its compiled consumer";
            if(POM.contains(id)&&"false".equals(o.get("POM")))return "Native Photon POM=false";
            if(id==OptimizationId.PIXEL_SCALE&&("false".equals(o.get("TAA"))||"false".equals(o.get("TAAU"))))return "Native Photon pixel budget requires both TAA and TAAU; native AA/upscaling choices are preserved";
            if(id==OptimizationId.WATER_PARALLAX_BUDGET&&"false".equals(o.get("WATER_PARALLAX")))return "Native Photon WATER_PARALLAX=false";
        }
        if(family.equals("solas-shader")) {
            if(Set.of(OptimizationId.PROJECTED_POM,OptimizationId.POM_EMPTY_HEIGHT_PREFIX).contains(id)
                    &&("false".equals(o.get("ADVANCED_MATERIALS"))||"false".equals(o.get("PARALLAX"))))return "Native Solas material POM requires ADVANCED_MATERIALS and PARALLAX";
            if(id==OptimizationId.AO_RENDER_SCALE&&"false".equals(o.get("SSAO")))return "Native Solas SSAO=false";
            if(id==OptimizationId.DEAD_MIP_CHAIN&&"true".equals(o.get("MOTION_BLUR")))return "Solas motion blur keeps this mip chain live";
        }
        if(family.equals("astralex")) {
            if(id==OptimizationId.SHADOW_SAMPLE_BUDGET&&"true".equals(o.get("SHADOW_FILTER"))&&"true".equals(o.get("TAA_SHADOW_FILTER")))
                return "Native AstraLex SHADOW_FILTER=true and TAA_SHADOW_FILTER=true select the separate temporal_shadow_filter consumer";
            if(Set.of(OptimizationId.PROJECTED_POM,OptimizationId.POM_EMPTY_HEIGHT_PREFIX).contains(id)&&"false".equals(o.get("PARALLAX")))return "Native AstraLex PARALLAX=false";
            if(id==OptimizationId.AO_RENDER_SCALE&&"false".equals(o.get("AO")))return "Native AstraLex AO=false";
            if(id==OptimizationId.WATER_PARALLAX_BUDGET&&"false".equals(o.get("WATER_PARALLAX")))return "Native AstraLex WATER_PARALLAX=false";
        }
        if(family.equals("complementary-unbound")||family.equals("complementary-reimagined")) {
            if(POM.contains(id)&&("false".equals(o.get("POM"))||Set.of("0","1").contains(Objects.toString(o.get("RP_MODE"),""))))return "Native Complementary material POM requires RP_MODE>=2 and POM enabled";
            if(Set.of(OptimizationId.CLOUD_SECONDARY_DETAIL,OptimizationId.CLOUD_SECONDARY_BUDGET).contains(id)) {
                String override=o.get("CLOUD_STYLE_DEFINE");
                if(Set.of("0","3","50").contains(Objects.toString(override,""))
                        ||"-1".equals(override)&&"4".equals(o.get("SHADER_STYLE")))return "This secondary sun-lighting consumer requires native Reimagined cloud style1; selected cloud style uses a different algorithm";
            }
        }
        if(family.equals("bliss-shader")) {
            if(POM.contains(id)&&"false".equals(o.get("POM")))return "Native Bliss POM=false";
            if(id==OptimizationId.REFLECTION_PROJECTED_REFINEMENT
                    &&("false".equals(o.get("Specular_Reflections"))||"false".equals(o.get("Screen_Space_Reflections"))))return "Bliss exact depth-query reuse requires native Specular_Reflections and Screen_Space_Reflections";
        }
        return null;
    }
    private static Map<String,Map<OptimizationId,Entry>> declarations() {
        try(var input=TuningEligibility.class.getResourceAsStream("/assets/shaderlod/tuning/applicability-v1.json")) {
            if(input==null)return Map.of();
            var root=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("schema").getAsInt()!=1)return Map.of();
            var families=root.getAsJsonObject("families");
            if(!families.keySet().equals(FAMILIES))return Map.of();
            var keys=new HashSet<String>();for(var id:OptimizationId.values())keys.add(id.key());
            var result=new HashMap<String,Map<OptimizationId,Entry>>();
            for(String family:FAMILIES) {
                var cells=families.getAsJsonObject(family);if(!cells.keySet().equals(keys))return Map.of();
                var entries=new EnumMap<OptimizationId,Entry>(OptimizationId.class);
                for(var id:OptimizationId.values()) {
                    var cell=cells.getAsJsonObject(id.key());String reason=cell.get("reason").getAsString();
                    if(reason.isBlank())return Map.of();
                    entries.put(id,new Entry(State.valueOf(cell.get("state").getAsString()),reason));
                }
                result.put(family,Map.copyOf(entries));
            }
            return Map.copyOf(result);
        }catch(Exception invalid){return Map.of();} // Unknown is honest if declarations cannot be verified.
    }
    static String signature(String family,Map<String,String> nativeOptions) {
        return Arrays.stream(OptimizationId.values()).map(id->id.key()+"="+classify(family,id,nativeOptions)).toList().toString();
    }
}
