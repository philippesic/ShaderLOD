package com.pp.shaderlod.optimization.solasmerged;
import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.HexFormat;
import com.pp.shaderlod.optimization.makeupmerged.MakeupMergedVertexInterface;
/** Exact pinned Solas/Iris SIMPLE or INDENTED emitter seams; every other byte remains native. */
public final class SolasMergedVertexInterface {
 private SolasMergedVertexInterface(){}
 public static final String SIMPLE_MAIN="void main() {\n_vert_init();\n";
 public static final String SIMPLE_MERGED="void main() {\n_vert_init();\nshaderlodMakeupRegionOffsetInit();\n";
 public static String adapt(String source,String helper,boolean qualified){
  if(!qualified||source==null||helper==null)return null;
  for(String builtin:new String[]{"gl_VertexID","gl_BaseVertex","gl_DrawID","gl_BaseInstance","gl_InstanceID"})if(source.contains(builtin))return null;
  try {
   if(!MakeupMergedVertexInterface.HELPER_SHA256.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(helper.getBytes(StandardCharsets.UTF_8)))))return null;
   String main,merged;
   int simple=count(source,SIMPLE_MAIN),indented=count(source,MakeupMergedVertexInterface.MAIN_NATIVE);
   if(simple==1&&indented==0){main=SIMPLE_MAIN;merged=SIMPLE_MERGED;}
   else if(simple==0&&indented==1){main=MakeupMergedVertexInterface.MAIN_NATIVE;merged=MakeupMergedVertexInterface.MAIN_MERGED;}
   else return null;
   if(count(source,MakeupMergedVertexInterface.OFFSET_ANCHOR)!=1||count(source,MakeupMergedVertexInterface.POSITION_NATIVE)!=1)return null;
   return source.replace(MakeupMergedVertexInterface.OFFSET_ANCHOR,MakeupMergedVertexInterface.OFFSET_ANCHOR+helper).replace(MakeupMergedVertexInterface.POSITION_NATIVE,MakeupMergedVertexInterface.POSITION_MERGED).replace(main,merged);
  }catch(Exception unavailable){return null;}
 }
 private static int count(String source,String seam){int n=0,i=0;while((i=source.indexOf(seam,i))>=0){n++;i+=seam.length();}return n;}
}
