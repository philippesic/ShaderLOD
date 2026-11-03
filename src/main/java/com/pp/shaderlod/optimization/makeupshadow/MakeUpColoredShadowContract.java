package com.pp.shaderlod.optimization.makeupshadow;

/** Fixed native840 shadow resources; no permission is shared with other shadow controls. */
public final class MakeUpColoredShadowContract {
    private MakeUpColoredShadowContract() {}
    public record Sampler(int texture,int width,int height,int format,int depthBits,int depthType,
        int compare,int function,int min,int mag,int base,int wrapS,int wrapT,float bias,float minLod,float maxLod,float anisotropy) {}
    private static boolean common(Sampler s,int owned){
        return s!=null&&owned>0&&s.texture()==owned&&s.width()==840&&s.height()==840
            &&s.min()==s.mag()&&(s.min()==0x2600||s.min()==0x2601)&&s.base()==0
            &&s.wrapS()==0x812F&&s.wrapT()==0x812F&&s.bias()==0&&Float.isFinite(s.minLod())
            &&Float.isFinite(s.maxLod())&&s.minLod()<=0&&s.maxLod()>=0&&s.anisotropy()==1;
    }
    public static boolean depth(Sampler s,int owned){
        if(!common(s,owned)||s.min()!=0x2601||s.compare()!=0x884E||s.function()!=0x0203)return false;
        return s.format()==0x81A5&&s.depthBits()==16&&s.depthType()==0x8C17
            ||s.format()==0x81A6&&s.depthBits()==24&&s.depthType()==0x8C17
            ||s.format()==0x81A7&&s.depthBits()==32&&s.depthType()==0x8C17
            ||s.format()==0x8CAC&&s.depthBits()==32&&s.depthType()==0x1406;
    }
    public static boolean color(Sampler s,int owned){return common(s,owned)&&s.format()==0x8058&&s.depthBits()==0&&s.depthType()==0&&s.compare()==0;}
}
