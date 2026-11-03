package com.pp.shaderlod.optimization.sildurspatial;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One-use evidence for Sildur's exact one-hot spatial graph to native graph recovery. */
public final class SildurSpatialFallback {
    private SildurSpatialFallback.Receipt pending;
    public static boolean loadedGraphMatches(String family,boolean enabled,boolean reuse,boolean rate,Map<String,String> actual) {
        if(!SildurSpatialContract.ID.equals(family)||!enabled||!reuse&&!rate)return true;
        return reuse!=rate&&actual!=null&&
            (reuse?"1":"0").equals(actual.get(SildurSpatialContract.REUSE))&&
            (rate?"1":"0").equals(actual.get(SildurSpatialContract.RATE));
    }
    private static boolean oneHot(Map<String,String> options) {
        String reuse=options.get(SildurSpatialContract.REUSE),rate=options.get(SildurSpatialContract.RATE);
        return "1".equals(reuse)&&"0".equals(rate)||"0".equals(reuse)&&"1".equals(rate);
    }
    public record Receipt(Object oldPack,Object newPack,Object oldPipeline,Object newPipeline,Object world,Object dimension,
            long generation,String oldSelected,String newSelected,Map<String,String> before,Map<String,String> after,
            boolean queueUnchanged,String reason) {
        public Receipt {before=Map.copyOf(before);after=Map.copyOf(after);}
        public boolean matches(String family,Object expectedPack,Object actualPack,Object expectedPipeline,Object actualPipeline,
                Object expectedWorld,Object actualDimension,long expectedGeneration,String expectedSelected,
                Map<String,String> expectedOptions,Map<String,String> actualOptions) {
            if(!SildurSpatialContract.ID.equals(family)||oldPack==null||newPack==null||oldPack==newPack||
                    oldPack!=expectedPack||newPack!=actualPack||oldPipeline==null||newPipeline==null||oldPipeline==newPipeline||
                    oldPipeline!=expectedPipeline||newPipeline!=actualPipeline||world==null||world!=expectedWorld||
                    !Objects.equals(dimension,actualDimension)||generation!=expectedGeneration||!queueUnchanged||
                    reason==null||reason.isBlank()||expectedSelected==null||!expectedSelected.equals(oldSelected)||
                    !expectedSelected.equals(newSelected)||!before.equals(expectedOptions)||!after.equals(actualOptions)||
                    !oneHot(before)||!"0".equals(after.get(SildurSpatialContract.REUSE))||
                    !"0".equals(after.get(SildurSpatialContract.RATE)))return false;
            var nativeOptions=new TreeMap<>(before);
            nativeOptions.put(SildurSpatialContract.REUSE,"0");nativeOptions.put(SildurSpatialContract.RATE,"0");
            return before.size()>2&&nativeOptions.equals(after);
        }
    }
    public void publish(Receipt receipt){pending=receipt;}
    public Receipt take(){var receipt=pending;pending=null;return receipt;}
    public void clear(){pending=null;}
}
