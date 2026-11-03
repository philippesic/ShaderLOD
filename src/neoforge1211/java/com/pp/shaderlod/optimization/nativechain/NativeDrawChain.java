package com.pp.shaderlod.optimization.nativechain;

import com.pp.shaderlod.optimization.astraearlyalpha.AstraEarlyAlphaRuntime;
import com.pp.shaderlod.optimization.astrareceiver.AstraReceiverRuntime;
import com.pp.shaderlod.optimization.astraspatial.AstraSpatialRuntime;
import com.pp.shaderlod.optimization.blissearlyalpha.BlissEarlyAlphaRuntime;
import com.pp.shaderlod.optimization.blissspatial.BlissSpatialDrawScope;
import com.pp.shaderlod.optimization.blisswater.BlissWaterShadowRuntime;
import com.pp.shaderlod.optimization.bslearlyalpha.BslEarlyAlphaRuntime;
import com.pp.shaderlod.optimization.bslexact.BslExactSsrRuntime;
import com.pp.shaderlod.optimization.bsl.BslReceiverRuntime;
import com.pp.shaderlod.optimization.bslspatial.BslSpatialRuntime;
import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime;
import com.pp.shaderlod.optimization.makeupshadow.MakeUpReflectionRuntime;
import com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime;
import com.pp.shaderlod.optimization.photonearlyalpha.PhotonEarlyAlphaRuntime;
import com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime;
import com.pp.shaderlod.optimization.reimaginedspatial.ReimaginedExactSsrRuntime;
import com.pp.shaderlod.optimization.reimaginedspatial.ReimaginedSpatialRuntime;
import com.pp.shaderlod.optimization.reimaginedspatial.ReimaginedWorldPixelRuntime;
import com.pp.shaderlod.optimization.sildurearlyalpha.SildurEarlyAlphaRuntime;
import com.pp.shaderlod.optimization.sildurexact.SildurExactSsrRuntime;
import com.pp.shaderlod.optimization.solasearlyalpha.SolasEarlyAlphaRuntime;
import com.pp.shaderlod.optimization.solasexact.SolasExactSsrRuntime;
import com.pp.shaderlod.optimization.solasexact.SolasReceiverRuntime;
import com.pp.shaderlod.optimization.solasfade.SolasTraceFadeRuntime;
import com.pp.shaderlod.optimization.solasspatial.SolasSpatialRuntime;
import com.pp.shaderlod.optimization.unboundearlyalpha.UnboundEarlyAlphaRuntime;
import com.pp.shaderlod.optimization.unboundspatial.UnboundExactSsrRuntime;
import com.pp.shaderlod.optimization.unboundspatial.UnboundSpatialRuntime;
import com.pp.shaderlod.optimization.unboundspatial.UnboundWorldPixelRuntime;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32C;

/** Exact 17-wrapper NeoForge 1.21.1 native-site continuation, outermost first. */
public final class NativeDrawChain {
    private NativeDrawChain() {}
    public static void draw(int mode,long counts,int type,long indices,int drawcount,long bases) {
        sildurExact(mode,counts,type,indices,drawcount,bases);
    }
    private static void sildurExact(int mode,long counts,int type,long indices,int drawcount,long bases) {
        SildurExactSsrRuntime.currentDraw(mode,counts,type,indices,drawcount,bases,NativeDrawChain::solasReceiver);
    }
    private static void solasReceiver(int mode,long counts,int type,long indices,int drawcount,long bases) {
        SolasReceiverRuntime.currentDraw(mode,counts,type,indices,drawcount,bases,NativeDrawChain::solasExact);
    }
    private static void solasExact(int mode,long counts,int type,long indices,int drawcount,long bases) {
        SolasExactSsrRuntime.currentDraw(mode,counts,type,indices,drawcount,bases,NativeDrawChain::reimaginedExact);
    }
    private static void reimaginedExact(int mode,long counts,int type,long indices,int drawcount,long bases) {
        ReimaginedExactSsrRuntime.currentDraw(mode,counts,type,indices,drawcount,bases,NativeDrawChain::reimaginedBefore);
    }
    private static void reimaginedBefore(int mode,long counts,int type,long indices,int drawcount,long bases) {
        ReimaginedWorldPixelRuntime.beforeNativeDraw();
        ReimaginedSpatialRuntime.draw(mode,counts,type,indices,drawcount,bases,NativeDrawChain::unboundExact);
    }
    private static void unboundExact(int mode,long counts,int type,long indices,int drawcount,long bases) {
        UnboundExactSsrRuntime.currentDraw(mode,counts,type,indices,drawcount,bases,NativeDrawChain::unboundBefore);
    }
    private static void unboundBefore(int mode,long counts,int type,long indices,int drawcount,long bases) {
        UnboundWorldPixelRuntime.beforeNativeDraw();
        UnboundSpatialRuntime.draw(mode,counts,type,indices,drawcount,bases,NativeDrawChain::bslExact);
    }
    private static void bslExact(int mode,long counts,int type,long indices,int drawcount,long bases) {
        BslExactSsrRuntime.currentDraw(mode,counts,type,indices,drawcount,bases,NativeDrawChain::blissWater);
    }
    private static void blissWater(int mode,long counts,int type,long indices,int drawcount,long bases) {
        BlissSpatialDrawScope.draw(BlissWaterShadowRuntime::draw,mode,counts,type,indices,drawcount,bases,
            NativeDrawChain::solasFade,BlissWaterShadowRuntime::returned,BlissWaterShadowRuntime::failed,BlissWaterShadowRuntime::revoke);
    }
    private static void solasFade(int mode,long counts,int type,long indices,int drawcount,long bases) {
        try { SolasTraceFadeRuntime.draw(); unboundAlpha(mode,counts,type,indices,drawcount,bases); }
        finally { SolasTraceFadeRuntime.revoke(); }
    }
    private static void unboundAlpha(int mode,long counts,int type,long indices,int drawcount,long bases) {
        try { UnboundEarlyAlphaRuntime.draw(); photonAlpha(mode,counts,type,indices,drawcount,bases); }
        finally { UnboundEarlyAlphaRuntime.revoke(); }
    }
    private static void photonAlpha(int mode,long counts,int type,long indices,int drawcount,long bases) {
        try {
            try { PhotonEarlyAlphaRuntime.draw(); } catch(RuntimeException|LinkageError ignored) {}
            astraAlpha(mode,counts,type,indices,drawcount,bases);
        } finally {
            try { PhotonEarlyAlphaRuntime.revoke(); } catch(RuntimeException|LinkageError ignored) {}
        }
    }
    private static void astraAlpha(int mode,long counts,int type,long indices,int drawcount,long bases) {
        try {
            AstraSpatialRuntime.beforeNativeDraw();
            AstraEarlyAlphaRuntime.draw();
            AstraReceiverRuntime.draw();
            solasAlpha(mode,counts,type,indices,drawcount,bases);
        } finally {
            try { AstraReceiverRuntime.revoke(); }
            finally { AstraEarlyAlphaRuntime.revoke(); }
        }
    }
    private static void solasAlpha(int mode,long counts,int type,long indices,int drawcount,long bases) {
        try {
            SolasSpatialRuntime.beforeNativeDraw();
            SolasEarlyAlphaRuntime.draw();
            blissAlpha(mode,counts,type,indices,drawcount,bases);
        } finally { SolasEarlyAlphaRuntime.revoke(); }
    }
    private static void blissAlpha(int mode,long counts,int type,long indices,int drawcount,long bases) {
        try { BlissEarlyAlphaRuntime.draw(); sildurAlpha(mode,counts,type,indices,drawcount,bases); }
        finally { BlissEarlyAlphaRuntime.revoke(); }
    }
    private static void sildurAlpha(int mode,long counts,int type,long indices,int drawcount,long bases) {
        try { SildurEarlyAlphaRuntime.draw(); reimaginedAlpha(mode,counts,type,indices,drawcount,bases); }
        finally { SildurEarlyAlphaRuntime.revoke(); }
    }
    private static void reimaginedAlpha(int mode,long counts,int type,long indices,int drawcount,long bases) {
        try { ReimaginedEarlyAlphaRuntime.draw(); bslAlpha(mode,counts,type,indices,drawcount,bases); }
        finally { ReimaginedEarlyAlphaRuntime.revoke(); }
    }
    private static void bslAlpha(int mode,long counts,int type,long indices,int drawcount,long bases) {
        Throwable primary=null;
        try {
            int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            BslSpatialRuntime.beforeNativeDraw(p);
            BslReceiverRuntime.beforeDraw(p);
            BslEarlyAlphaRuntime.draw(p);
            makeupAlpha(mode,counts,type,indices,drawcount,bases);
        } catch(RuntimeException|Error failure) { primary=failure; throw failure; }
        finally {
            try { BslSpatialRuntime.scoped(BslEarlyAlphaRuntime::revoke,BslReceiverRuntime::revoke); }
            catch(RuntimeException|Error cleanup) {
                if(primary==null) throw cleanup;
                if(primary!=cleanup) try { primary.addSuppressed(cleanup); } catch(Throwable ignored) {}
            }
        }
    }
    private static void makeupAlpha(int mode,long counts,int type,long indices,int drawcount,long bases) {
        try { MakeUpEarlyAlphaRuntime.draw(); makeupReflection(mode,counts,type,indices,drawcount,bases); com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlphaWitness.completedDraw(drawcount); }
        finally { MakeUpEarlyAlphaRuntime.revoke(); }
    }
    private static void makeupReflection(int mode,long counts,int type,long indices,int drawcount,long bases) {
        MakeUpReflectionRuntime.currentDraw(mode,counts,type,indices,drawcount,bases,NativeDrawChain::makeupShadow);
    }
    private static void makeupShadow(int mode,long counts,int type,long indices,int drawcount,long bases) {
        MakeUpShadowRuntime.currentDraw(mode,counts,type,indices,drawcount,bases,NativeDrawChain::nativeCall);
    }
    private static void nativeCall(int mode,long counts,int type,long indices,int drawcount,long bases) {
        GL32C.nglMultiDrawElementsBaseVertex(mode,counts,type,indices,drawcount,bases);
    }
}
