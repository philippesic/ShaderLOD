package com.pp.shaderlod.optimization.makeupspatial;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One-use evidence for MakeUp's exact one-hot reuse/rate/world graph to native graph recovery. */
public final class MakeUpSpatialFallback {
    private MakeUpSpatialFallback.Receipt pending;
    public static int requestCount(boolean reuse,boolean rate,boolean world){return (reuse?1:0)+(rate?1:0)+(world?1:0);}
    public static boolean loadedGraphMatches(String family,boolean enabled,boolean reuse,boolean rate,boolean world,Map<String,String> actual) {
        if(!MakeUpSpatialContract.ID.equals(family)||!enabled||requestCount(reuse,rate,world)==0)return true;
        return requestCount(reuse,rate,world)==1&&actual!=null&&
            (reuse?"1":"0").equals(actual.get(MakeUpSpatialContract.KEY))&&
            (rate?"1":"0").equals(actual.get(MakeUpSpatialContract.RATE))&&
            (world?"1":"0").equals(actual.get(MakeUpSpatialContract.WORLD));
    }
    private static boolean oneHot(Map<String,String> options) {
        String reuse=options.get(MakeUpSpatialContract.KEY),rate=options.get(MakeUpSpatialContract.RATE),world=options.get(MakeUpSpatialContract.WORLD);
        return java.util.Set.of("0","1").contains(Objects.toString(reuse,"invalid"))&&
            java.util.Set.of("0","1").contains(Objects.toString(rate,"invalid"))&&
            java.util.Set.of("0","1").contains(Objects.toString(world,"invalid"))&&
            requestCount("1".equals(reuse),"1".equals(rate),"1".equals(world))==1;
    }
    public record Receipt(Object oldPack,Object newPack,Object oldPipeline,Object newPipeline,Object world,Object dimension,
            long generation,String oldSelected,String newSelected,Map<String,String> before,Map<String,String> after,
            boolean queueUnchanged,String reason) {
        public Receipt {before=Map.copyOf(before);after=Map.copyOf(after);}
        public boolean matches(String family,Object expectedPack,Object actualPack,Object expectedPipeline,Object actualPipeline,
                Object expectedWorld,Object actualDimension,long expectedGeneration,String expectedSelected,
                Map<String,String> expectedOptions,Map<String,String> actualOptions) {
            if(!MakeUpSpatialContract.ID.equals(family)||oldPack==null||newPack==null||oldPack==newPack||
                    oldPack!=expectedPack||newPack!=actualPack||oldPipeline==null||newPipeline==null||oldPipeline==newPipeline||
                    oldPipeline!=expectedPipeline||newPipeline!=actualPipeline||world==null||world!=expectedWorld||
                    !Objects.equals(dimension,actualDimension)||generation!=expectedGeneration||!queueUnchanged||
                    reason==null||reason.isBlank()||expectedSelected==null||!expectedSelected.equals(oldSelected)||
                    !expectedSelected.equals(newSelected)||!before.equals(expectedOptions)||!after.equals(actualOptions)||
                    !oneHot(before)||!"0".equals(after.get(MakeUpSpatialContract.KEY))||
                    !"0".equals(after.get(MakeUpSpatialContract.RATE))||!"0".equals(after.get(MakeUpSpatialContract.WORLD)))return false;
            var nativeOptions=new TreeMap<>(before);
            nativeOptions.put(MakeUpSpatialContract.KEY,"0");nativeOptions.put(MakeUpSpatialContract.RATE,"0");nativeOptions.put(MakeUpSpatialContract.WORLD,"0");
            return before.size()>3&&nativeOptions.equals(after);
        }
    }
    public void publish(Receipt receipt){pending=receipt;}
    public Receipt take(){var receipt=pending;pending=null;return receipt;}
    public void clear(){pending=null;}
}
