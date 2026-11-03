package com.pp.shaderlod.dev.bindingfix;
/** Pure slot contract: restore native unused descriptor slots, never create a bound declaration. */
public final class BindingInputContract {
    private BindingInputContract() {}
    public static boolean knownPassClass(String name) { return "com.mojang.blaze3d.opengl.GlRenderPass".equals(name); }
    /** Shape only; native pipeline declarations/validation decide whether bufferless drawing is valid. */
    public static boolean noBoundSlots(Object[] buffers) {
        if(buffers==null || buffers.length!=16)return false;
        for(Object buffer:buffers)if(buffer!=null)return false;
        return true;
    }
    public static boolean soleBoundSlotZero(Object[] buffers) {
        if(buffers==null || buffers.length!=16 || buffers[0]==null)return false;
        for(int i=1;i<16;i++)if(buffers[i]!=null)return false;
        return true;
    }
    public static boolean canRestore(Object[] nativeBindings,Object[] rewritten,Object[] buffers) {
        if(!soleBoundSlotZero(buffers) || nativeBindings==null || nativeBindings.length!=16 || nativeBindings[0]==null ||
            rewritten==null || rewritten.length!=1 || rewritten[0]==null)return false;
        for(int i=1;i<16;i++)if(nativeBindings[i]!=null)return false;
        return true;
    }
    /** Invalid/absent scope returns the exact original getter object; native validation decides failure. */
    public static <T> T[] restoreOrOriginal(boolean sameContext,T[] nativeBindings,T[] rewritten,Object[] buffers) {
        if(!sameContext || !canRestore(nativeBindings,rewritten,buffers))return rewritten;
        T[] corrected=nativeBindings.clone();corrected[0]=rewritten[0];return corrected;
    }
}
