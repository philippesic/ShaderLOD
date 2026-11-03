package com.pp.shaderlod.optimization.photonreceiver;
import com.pp.shaderlod.optimization.bsl.BslReceiverContract;
/** Resource support for an approximate receiver quadrature, never a visual error bound. */
public final class PhotonReceiverContract {
 private PhotonReceiverContract(){}
 public static boolean sampler(BslReceiverContract.Sampler actual,int ownedTexture,int resolution,float bias,float minLod,float maxLod){
  return BslReceiverContract.depth(actual)&&actual.texture()==ownedTexture&&ownedTexture>0&&resolution==2048&&bias==0&&Float.isFinite(minLod)&&Float.isFinite(maxLod)&&minLod<=0&&maxLod>=0;
 }
 public static boolean controls(boolean enabled,boolean quality,boolean requested,boolean suspended,boolean overworld,boolean queued,boolean fallback){return enabled&&!quality&&requested&&!suspended&&overworld&&!queued&&!fallback;}
}
