package com.pp.shaderlod.sildurprefix;

import java.util.List;

/** CPU admission ledger only. The staged binding deliberately does not install it.
 * Every value is a current consuming-image witness, never an authored maximum. */
public final class SildurPrefixCertificate {
    public static final int VERSION = 1;
    public record Rect(int x0, int y0, int x1, int y1) {
        public boolean valid(int w,int h) {return x0>=0&&y0>=0&&x1>=x0&&y1>=y0&&x1<w&&y1<h;}
    }
    public record Material(String name, Object actualSprite, Rect rect, boolean staticSprite,
                           boolean exactResourceAndFrameSource, int gpuMaxAlpha, String gpuBytesSha256) {}
    public record Current(Object pipeline, Object atlas, Object normalImage, Object resourceManager,
                          long resourceEpoch, long atlasUploadEpoch, long textureStorageEpoch,
                          int program, int texture, int sampler, int width, int height,
                          int format, int alphaBits, int swizzleA, int baseLevel,
                          int minFilter, int magFilter, int compareMode, float anisotropy,
                          float lodBias, float minLod, float maxLod,
                          String exactPropertySentinel, String consumingSourceAndFilterIdentity) {}
    public record Evidence(Current current, List<Material> materials,
                           boolean actualDriverBaseMagSupport, boolean actualDriverRoundingSupport,
                           String supportWitnessSha256) {
        public Evidence {materials=List.copyOf(materials);}
    }
    private Evidence evidence;
    private long generation;
    public void invalidate() {evidence=null; generation++;}
    public long generation() {return generation;}
    public boolean install(Evidence e) {
        invalidate();
        if(!qualified(e)) return false;
        evidence=e;return true;
    }
    public boolean active(Current now) {
        if(evidence==null) return false;
        var old=evidence.current();
        // Compare object identity as well as IDs and upload/storage epochs. ID reuse is insufficient.
        boolean same=now!=null&&old.pipeline()==now.pipeline()&&old.atlas()==now.atlas()
            &&old.normalImage()==now.normalImage()&&old.resourceManager()==now.resourceManager()
            &&old.equals(now);
        if(!same) invalidate();
        return same;
    }
    public Evidence evidence() {return evidence;}
    private static boolean hash(String s) {return s!=null&&s.matches("[0-9a-f]{64}");}
    private static boolean qualified(Evidence e) {
        if(e==null||e.current()==null||!e.actualDriverBaseMagSupport()||!e.actualDriverRoundingSupport()
           ||!hash(e.supportWitnessSha256())||e.materials().size()!=3) return false;
        var c=e.current();
        if(c.pipeline()==null||c.atlas()==null||c.normalImage()==null||c.resourceManager()==null
           ||c.resourceEpoch()<=0||c.atlasUploadEpoch()<=0||c.textureStorageEpoch()<=0
           ||c.program()<=0||c.texture()<=0||c.sampler()<0||c.width()<1||c.height()<1
           ||c.width()>16384||c.height()>16384||c.baseLevel()!=0||c.compareMode()!=0
           ||c.format()!=0x8058||c.alphaBits()!=8||c.swizzleA()!=0x1906
           ||c.anisotropy()!=1f||!Float.isFinite(c.lodBias())||c.lodBias()!=0f
           ||!Float.isFinite(c.minLod())||c.minLod()>0f||!Float.isFinite(c.maxLod())||c.maxLod()<0f
           ||(c.magFilter()!=0x2600&&c.magFilter()!=0x2601)
           ||!(c.minFilter()==0x2600||c.minFilter()==0x2601||(c.minFilter()>=0x2700&&c.minFilter()<=0x2703))
           ||!hash(c.exactPropertySentinel())||!hash(c.consumingSourceAndFilterIdentity()))return false;
        var names=List.of("minecraft:block/stone_n","minecraft:block/cobblestone_n","minecraft:block/oak_planks_n");
        for(int i=0;i<3;i++) {
            var m=e.materials().get(i);
            if(m==null||!names.get(i).equals(m.name())||m.actualSprite()==null||m.rect()==null
               ||!m.rect().valid(c.width(),c.height())||!m.staticSprite()||!m.exactResourceAndFrameSource()
               ||m.gpuMaxAlpha()<0||m.gpuMaxAlpha()>255||!hash(m.gpuBytesSha256())) return false;
            for(int j=0;j<i;j++) {
                var r=e.materials().get(j).rect();var s=m.rect();
                if(r.x0()<=s.x1()&&s.x0()<=r.x1()&&r.y0()<=s.y1()&&s.y0()<=r.y1())return false;
            }
        }
        return true;
    }
    /** One whole 8-bit code above the measured GPU maximum, plus upward float conversion.
     * Driver-specific filtering/rounding support is separately mandatory. */
    public static float upperHeight(int gpuMaximum) {
        if(gpuMaximum<0||gpuMaximum>255)throw new IllegalArgumentException("8-bit GPU alpha required");
        if(gpuMaximum>=254)return 1f;
        double upper=(gpuMaximum+1)/255.0;
        float value=(float)upper;
        return value<upper?Math.nextUp(value):value;
    }
}
