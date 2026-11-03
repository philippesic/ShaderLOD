package com.pp.shaderlod.optimization.bsl;
/** Resource support only: approximate quadrature remains an unvalidated quality hypothesis. */
public final class BslReceiverContract {
    private BslReceiverContract() {}
    public record Sampler(int texture,int width,int height,int format,int depthBits,int depthType,
        int compare,int compareFunction,int min,int mag,int base,int wrapS,int wrapT,float anisotropy) {}
    public static boolean depth(Sampler s) {
        return s!=null && s.texture()>0 && s.width()==2048 && s.height()==2048 &&
            ((s.format()==0x81A5 && s.depthBits()==16 && s.depthType()==0x8C17) ||
             (s.format()==0x81A6 && s.depthBits()==24 && s.depthType()==0x8C17) ||
             (s.format()==0x81A7 && s.depthBits()==32 && s.depthType()==0x8C17) ||
             (s.format()==0x8CAC && s.depthBits()==32 && s.depthType()==0x1406)) &&
            s.compare()==0x884E && s.compareFunction()==0x0203 &&
            s.min()==0x2601 && s.mag()==0x2601 && s.base()==0 &&
            s.wrapS()==0x812F && s.wrapT()==0x812F && s.anisotropy()==1.0f;
    }
    public static boolean viewport(int x,int y,int width,int height,float nativeWidth,float nativeHeight) {
        return x==0 && y==0 && width>0 && height>0 && nativeWidth==width && nativeHeight==height;
    }
}
