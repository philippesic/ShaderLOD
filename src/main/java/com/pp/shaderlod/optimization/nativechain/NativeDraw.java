package com.pp.shaderlod.optimization.nativechain;
@FunctionalInterface public interface NativeDraw {
    void draw(int mode,long counts,int type,long indices,int drawcount,long bases);
}
