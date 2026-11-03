package com.pp.shaderlod.reimaginedprefix;
import net.irisshaders.iris.pbr.format.*;
import net.irisshaders.iris.pbr.texture.PBRType;
/** Exact pinned Iris native no-parameter-write paths, not GL sampling permission.
 * Null is known ONLY with actual winning texture.properties absence. Both paths
 * leave sampler state untouched; fresh actual GPU sampler qualification remains required. */
public final class ReimaginedPrefixNormalSetupR12 {
 private static final Object NO_FORMAT_NATIVE_NO_SETUP=new Object();
 public static boolean admitted(TextureFormat format,boolean actualMetadataAbsent){
  return format==null?actualMetadataAbsent:format.getClass()==LabPBRTextureFormat.class&&format.canInterpolateValues(PBRType.NORMAL);
 }
 public static Object identity(TextureFormat format,boolean capturedMetadataAbsent){
  return format==null?(capturedMetadataAbsent?NO_FORMAT_NATIVE_NO_SETUP:null):format;
 }
 public static boolean sameNoParameterWrite(TextureFormat actual,TextureFormat captured,boolean capturedMetadataAbsent){
  return actual==captured&&admitted(actual,capturedMetadataAbsent);
 }
 private ReimaginedPrefixNormalSetupR12(){}
}
