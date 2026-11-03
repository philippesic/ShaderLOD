package com.pp.shaderlod.optimization;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One-use evidence of the explicitly supported native-AO safety reload, never a timing result. */
public final class AoNativeFallback {
    private static final Map<String,String> OPTIONS=Map.ofEntries(
        Map.entry("solas-shader","SHADERLOD_SOLAS_AO_HALF_RES"),
        Map.entry("astralex","SHADERLOD_ASTRALEX_AO_HALF_RES"),
        Map.entry("makeup-ultra-fast-shaders","SHADERLOD_MAKEUP_AO_HALF_RES"),
        Map.entry("complementary-reimagined","SHADERLOD_REIMAGINED_AO_HALF_RES"),
        Map.entry("complementary-unbound","SHADERLOD_UNBOUND_AO_HALF_RES"),
        Map.entry("sildurs-vibrant-shaders","SHADERLOD_SILDUR_AO_HALF_RES"),
        Map.entry("bliss-shader","SHADERLOD_BLISS_AO_HALF_RES"),
        Map.entry("bsl-shaders","SHADERLOD_BSL_AO_HALF_RES"));
    public static String option(String family){return OPTIONS.get(family);}
    /** Disabled requests need no half graph; an active known request must actually load graph1. */
    public static boolean loadedGraphMatches(String family,boolean enabled,boolean requested,Map<String,String> actual) {
        String option=OPTIONS.get(family);
        return !enabled||!requested||option==null||"1".equals(actual.get(option));
    }
    public record Receipt(String family,Object oldPack,Object newPack,Object oldPipeline,Object newPipeline,Object world,Object dimension,long generation,
            String oldSelected,String newSelected,Map<String,String> before,Map<String,String> after,
            boolean queueUnchanged,String reason) {
        public Receipt {before=Map.copyOf(before);after=Map.copyOf(after);}
        public boolean matches(String expectedFamily,Object expectedPack,Object actualPack,Object expectedPipeline,Object actualPipeline,Object expectedWorld,Object actualDimension,
                long expectedGeneration,String expectedSelected,Map<String,String> expectedOptions,Map<String,String> actualOptions) {
            String option=OPTIONS.get(family);
            if(option==null||!Objects.equals(family,expectedFamily)||oldPack==null||newPack==null||oldPack==newPack||oldPack!=expectedPack||newPack!=actualPack
                    ||oldPipeline==null||newPipeline==null||oldPipeline==newPipeline||oldPipeline!=expectedPipeline||newPipeline!=actualPipeline
                    ||world==null||world!=expectedWorld||!Objects.equals(dimension,actualDimension)
                    ||generation!=expectedGeneration||!queueUnchanged||reason==null||reason.isBlank()
                    ||expectedSelected==null||!expectedSelected.equals(oldSelected)||!expectedSelected.equals(newSelected)
                    ||!before.equals(expectedOptions)||!after.equals(actualOptions)
                    ||!"1".equals(before.get(option))||!"0".equals(after.get(option)))return false;
            var nativeZero=new TreeMap<>(before);nativeZero.put(option,"0");
            return before.size()>1&&nativeZero.equals(after);
        }
    }
    private Receipt pending;
    public void publish(Receipt receipt){pending=receipt;}
    public Receipt take(){var receipt=pending;pending=null;return receipt;}
    public void clear(){pending=null;}
}
