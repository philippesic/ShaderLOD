package com.pp.shaderlod.optimization.photon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure actual input-validation/equality boundaries; these fixtures are not driver observations. */
class PhotonSpatialDepthStorageTest {
 private static PhotonSpatialGl.Texture data(){return new PhotonSpatialGl.Texture(1,64,32,0x805B,0,0,0x2600,0x2600);}
 private static PhotonSpatialGl.Texture depth(int format,int bits,int type){return new PhotonSpatialGl.Texture(3,64,32,format,0,0,0x2600,0x2600,bits,type);}
 private static boolean qualifies(PhotonSpatialGl.Texture depth){return PhotonSpatialGl.inputImagesQualified(data(),depth,64,32,1,2,3);}
 @Test void existingSizedDepthSnapshotsRetainTheirValidation(){for(int format:new int[]{0x81A5,0x81A6,0x81A7,0x8CAC,0x88F0,0x8CAD})assertTrue(qualifies(depth(format,0,0)));}
 @Test void rawDepthNeedsActualAcceptedPrecisionAndType(){
  for(int bits:new int[]{16,24,32})assertTrue(qualifies(depth(0x1902,bits,0x8C17)));
  assertTrue(qualifies(depth(0x1902,32,0x1406)));
  for(var d:new PhotonSpatialGl.Texture[]{depth(0x1902,0,0),depth(0x1902,24,0x1406),depth(0x1902,16,0x1404),depth(0x1902,8,0x8C17),depth(0x1908,24,0x8C17)})assertFalse(qualifies(d));
  assertFalse(qualifies(new PhotonSpatialGl.Texture(3,64,32,0x1902,0,0,0x2600,0x2600)),"Old untyped constructor cannot admit raw depth");
 }
 @Test void precisionAndTypeChangesInvalidateCompletedInputIdentity(){
  var output=new PhotonSpatialEpoch.Output(9,10,11);var a=new PhotonSpatialGl.Proof(true,output,data(),depth(0x1902,24,0x8C17));
  assertTrue(PhotonSpatialGl.sameInputs(a,new PhotonSpatialGl.Proof(true,output,data(),depth(0x1902,24,0x8C17))));
  assertFalse(PhotonSpatialGl.sameInputs(a,new PhotonSpatialGl.Proof(true,output,data(),depth(0x1902,32,0x8C17))));
  var b=new PhotonSpatialGl.Proof(true,output,data(),depth(0x1902,32,0x8C17));
  assertFalse(PhotonSpatialGl.sameInputs(b,new PhotonSpatialGl.Proof(true,output,data(),depth(0x1902,32,0x1406))));
 }
}
