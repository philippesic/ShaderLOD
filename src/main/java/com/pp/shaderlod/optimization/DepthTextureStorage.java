package com.pp.shaderlod.optimization;

/** Exact depth storage representations; no GL calls or inferred precision.
 * GL_TEXTURE_DEPTH_SIZE/TYPE describe the bound level's actual storage.
 * https://registry.khronos.org/OpenGL-Refpages/gl4/html/glGetTexLevelParameter.xhtml
 */
public final class DepthTextureStorage {
    public static final int UNSIZED_DEPTH=0x1902, UNSIGNED_NORMALIZED=0x8C17, FLOAT=0x1406;
    private DepthTextureStorage() {}
    /** Complete observed representation, separate from the explicitly sized private allocation. */
    public record Storage(int rawFormat,int depthBits,int depthType,int privateFormat) {}
    /** Null refuses unknown precision/type. Sized cases retain their existing allocation exactly. */
    public static Storage resolve(int rawFormat,int depthBits,int depthType) {
        if(!accepts(rawFormat,depthBits,depthType))return null;
        int sized=rawFormat;
        if(rawFormat==UNSIZED_DEPTH)sized=depthType==FLOAT?0x8CAC:switch(depthBits) {
            case 16->0x81A5;case 24->0x81A6;case 32->0x81A7;
            default->throw new AssertionError("Unadmitted depth precision");
        };
        return new Storage(rawFormat,depthBits,depthType,sized);
    }
    public static boolean accepts(int rawFormat,int depthBits,int depthType) {
        if(rawFormat==UNSIZED_DEPTH)
            return depthType==UNSIGNED_NORMALIZED&&(depthBits==16||depthBits==24||depthBits==32)
                ||depthType==FLOAT&&depthBits==32;
        // Preserve the existing six sized format cases, including depth-stencil.
        return switch(rawFormat) {
            case 0x81A5,0x81A6,0x81A7,0x8CAC,0x88F0,0x8CAD -> true;
            default -> false;
        };
    }
}
