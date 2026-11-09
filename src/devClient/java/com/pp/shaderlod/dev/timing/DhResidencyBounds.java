package com.pp.shaderlod.dev.timing;

/** Section AABB must be wholly beyond the vanilla square; center distance is insufficient. */
public final class DhResidencyBounds {
    public static boolean beyond(int x,int z,int width,double cameraX,double cameraZ,int chunks){
        if(width<=0||chunks<=0||!Double.isFinite(cameraX)||!Double.isFinite(cameraZ))return false;
        double dx=Math.max(Math.max(x-cameraX,cameraX-(x+(double)width)),0);
        double dz=Math.max(Math.max(z-cameraZ,cameraZ-(z+(double)width)),0);
        return Math.max(dx,dz)>chunks*16.0;
    }
}
