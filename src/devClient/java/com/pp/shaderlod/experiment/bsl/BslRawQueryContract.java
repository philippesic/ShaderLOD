package com.pp.shaderlod.experiment.bsl;

/** A separately specified raw query. Never issues R1 filtered-shadow support.
 * This class has no configuration/file-hash qualification route. */
public final class BslRawQueryContract {
    private BslRawQueryContract() {}
    /** Root fills ONLY after inspecting actual linked/exported source and value
     * path for this exact driver. An empty inventory keeps staging native.
     * Pair: driver + "|" + producer complete-stage SHA256 + "|" + terrain SHA256.
     * Control/candidate macros produce different terrain source pairs. */
    private static final java.util.Set<String> REVIEWED_LINK_PAIRS = java.util.Set.of(
        "Apple|Apple M3 Max|4.1 Metal - 91.7|727c7a5a0e099cb431476a256d4e5e6627a818ff00a5e754f5a9a45a25a004a0|2d71a43132c04261b960e86cfb0489cfffd2081c2aee054d71f2ef38d5f5bed8",
        "Apple|Apple M3 Max|4.1 Metal - 91.7|727c7a5a0e099cb431476a256d4e5e6627a818ff00a5e754f5a9a45a25a004a0|ea64e42f4e3e3f2c6e6cbe51f2a25627373a3c2e17eba496e08e6ec2997f7653",
        "Apple|Apple M3 Max|4.1 Metal - 91.7|727c7a5a0e099cb431476a256d4e5e6627a818ff00a5e754f5a9a45a25a004a0|efede257944d5c65b04c87e33feaef6f22bd7527a664bf03b5a30c3a50720c50",
        "Apple|Apple M3 Max|4.1 Metal - 91.7|727c7a5a0e099cb431476a256d4e5e6627a818ff00a5e754f5a9a45a25a004a0|2d35051b73f245bd37535bb07d3e8d14968d35db8b724d4b9714a71194ee170b"
    );
    public record Raw(int texture,int sampler,int compare,int min,int mag,int width,int height,
                      int format,int base,int max,int bits,int type,int wrapS,int wrapT,
                      boolean anisoSupported,Float aniso,int redSwizzle) {}
    public static boolean format(Raw b) {
        return b != null && ((b.format==0x81A5 && b.bits==16 && b.type==0x8C17)
            || (b.format==0x81A6 && b.bits==24 && b.type==0x8C17)
            || (b.format==0x81A7 && b.bits==32 && b.type==0x8C17)
            || (b.format==0x8CAC && b.bits==32 && b.type==0x1406));
    }
    public static boolean resourcesMatch(Raw p,Raw t) {
        return format(p) && format(t) && p.equals(t) && p.texture>0 && p.sampler>=0
            && p.compare==0 && (p.min==0x2600 || p.min==0x2601)
            && (p.mag==0x2600 || p.mag==0x2601) && p.width==2048 && p.height==2048
            && p.base==0 && p.max>=0 && p.wrapS==0x812F && p.wrapT==0x812F
            && (!p.anisoSupported || p.aniso!=null && p.aniso==1.0f)
            && p.redSwizzle==0x1903;
    }
    public static boolean sourcesReviewed(String driver,String producer,String terrain) {
        return driver!=null && producer!=null && terrain!=null
            && producer.matches("[0-9a-f]{64}") && terrain.matches("[0-9a-f]{64}")
            && REVIEWED_LINK_PAIRS.contains(driver+"|"+producer+"|"+terrain);
    }
    /** Complete source/value-path admission, populated only by root after actual
     * linked-source and runtime inventory review. No property/file setter. */
    public record SteadyLink(String driver,String producer,String terrain,String environment) {}
    private static final java.util.Set<SteadyLink> REVIEWED_STEADY_LINKS=java.util.Set.of(
        new SteadyLink("Apple|Apple M3 Max|4.1 Metal - 91.7","727c7a5a0e099cb431476a256d4e5e6627a818ff00a5e754f5a9a45a25a004a0","2d71a43132c04261b960e86cfb0489cfffd2081c2aee054d71f2ef38d5f5bed8","bc6a449d1d707f1a5bdb5d173ddf1e110c5c071e768d6ed116eee89a0b1de01b"),
        new SteadyLink("Apple|Apple M3 Max|4.1 Metal - 91.7","727c7a5a0e099cb431476a256d4e5e6627a818ff00a5e754f5a9a45a25a004a0","ea64e42f4e3e3f2c6e6cbe51f2a25627373a3c2e17eba496e08e6ec2997f7653","bc6a449d1d707f1a5bdb5d173ddf1e110c5c071e768d6ed116eee89a0b1de01b"),
        new SteadyLink("Apple|Apple M3 Max|4.1 Metal - 91.7","727c7a5a0e099cb431476a256d4e5e6627a818ff00a5e754f5a9a45a25a004a0","efede257944d5c65b04c87e33feaef6f22bd7527a664bf03b5a30c3a50720c50","bc6a449d1d707f1a5bdb5d173ddf1e110c5c071e768d6ed116eee89a0b1de01b"),
        new SteadyLink("Apple|Apple M3 Max|4.1 Metal - 91.7","727c7a5a0e099cb431476a256d4e5e6627a818ff00a5e754f5a9a45a25a004a0","2d35051b73f245bd37535bb07d3e8d14968d35db8b724d4b9714a71194ee170b","bc6a449d1d707f1a5bdb5d173ddf1e110c5c071e768d6ed116eee89a0b1de01b")
    );
    public static boolean steadyProducerReviewed(String driver,String producer,String environment) {
        return steadyProducerReviewed(REVIEWED_STEADY_LINKS,driver,producer,environment);
    }
    static boolean steadyProducerReviewed(java.util.Set<SteadyLink> rows,String driver,String producer,String environment) {
        return validSha(producer)&&validSha(environment)&&driver!=null&&rows.stream().anyMatch(r ->
            r.driver.equals(driver)&&r.producer.equals(producer)&&r.environment.equals(environment)&&validSha(r.terrain));
    }
    public static boolean steadySourcesReviewed(String driver,String producer,String terrain,String environment) {
        return steadySourcesReviewed(REVIEWED_STEADY_LINKS,driver,producer,terrain,environment);
    }
    static boolean steadySourcesReviewed(java.util.Set<SteadyLink> rows,String driver,String producer,String terrain,String environment) {
        return validSha(producer)&&validSha(terrain)&&validSha(environment)&&driver!=null
            &&rows.contains(new SteadyLink(driver,producer,terrain,environment));
    }
    private static boolean validSha(String s) { return s!=null&&s.matches("[0-9a-f]{64}"); }
    /** D16 normalized storage has no NaN/Inf/subnormal members. A positive
     * stored integer converts to >=1/65535, far above float MIN_NORMAL.
     * Source review must establish the SAME raw fetch conversion and exact
     * operand-selecting min/R32F store/fetch; finite readback is not that proof.
     * D24/D32/D32F remain outside this first steady certificate. */
    public static boolean steadyFiniteDomain(Raw b) {
        return b!=null&&b.format==0x81A5&&b.bits==16&&b.type==0x8C17;
    }
    public static boolean normalDepth(float value) {
        return Float.isFinite(value) && value>=0 && value<=1
            && (value==0 || value>=Float.MIN_NORMAL);
    }
}
