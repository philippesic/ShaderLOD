package com.pp.shaderlod.fabric.dev.earlyalpha;
import java.util.regex.Pattern;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;
/** Owned BSL interface: candidate prepared before pruning; control may legitimately lose its unused stub. */
public final class BslAlphaInterface {
 private BslAlphaInterface(){}
 private static final Pattern STUB=Pattern.compile("bool\\s+shaderlodBslNativeAlphaReject\\s*\\(\\s*float\\s+finalAlpha\\s*\\)\\s*\\{\\s*return\\s+false\\s*;\\s*\\}");
 private static final Pattern CALL=Pattern.compile("shaderlodBslNativeAlphaReject\\s*\\(\\s*albedo\\.a\\s*\\)");
 public static boolean candidate(String fragment){return fragment!=null&&CALL.matcher(fragment).find();}
 private static boolean protocol(String f){return f!=null&&f.contains("shaderlodBslAlphaEnabled")&&f.contains("shaderlodBslAlphaQualified")&&f.contains("shaderlodBslAlphaVersion")&&f.contains("shaderlodBslAlphaPreset")&&f.contains("shaderlodBslAlphaReference")&&f.contains("shaderlodBslShadowColor")&&(f.contains("textureLod")||f.contains("texture2DLod"))&&(f.contains("shadow2DLod")||f.contains("textureLod"));}
 /** Exact native rule, private reference mirrored from Iris at the actual draw. No reserved iris_ input names. */
 public static String prepare(String source,AlphaTest alpha){
  if(!candidate(source))return source;
  if(!protocol(source)||alpha==null)return null;
  if(alpha.function()==AlphaTestFunction.ALWAYS)return source;
  if(alpha.function()!=AlphaTestFunction.GREATER||alpha.reference()!=0.5f)return null;
  var stub=STUB.matcher(source);if(!stub.find())return null;int start=stub.start(),end=stub.end();if(stub.find())return null;
  String body=alpha.toExpression("finalAlpha","shaderlodBslAlphaReference","").replace("discard;","return true;")+"return false;";
  return source.substring(0,start)+"bool shaderlodBslNativeAlphaReject(float finalAlpha) {\n"+body+"\n}"+source.substring(end);
 }
 /** Post-transform admission: all strict native-tail and owned protocol proofs remain required. */
 public static String adapt(String fragment,AlphaTest alpha,boolean expectedCandidate){
  if(fragment==null||alpha==null||!protocol(fragment)||candidate(fragment)!=expectedCandidate)return null;
  if(alpha.function()==AlphaTestFunction.ALWAYS)return fragment; // SOLID remains inactive at actual draw.
  if(alpha.function()!=AlphaTestFunction.GREATER||alpha.reference()!=0.5f)return null;
  String compact=fragment.replaceAll("\\s+","");String late=alpha.toExpression("iris_FragData0.a","iris_currentAlphaTest","").replaceAll("\\s+","");
  if(!compact.endsWith(late+"}")||!fragment.matches("(?s).*\\buniform\\s+float\\s+iris_currentAlphaTest\\s*;.*"))return null;
  if(expectedCandidate){
   if(STUB.matcher(fragment).find()||!compact.contains("!(finalAlpha>shaderlodBslAlphaReference)")||!compact.contains("returntrue;"))return null;
  }
  // Control has NO early call; an unused stub may have been pruned. Never invent a candidate stub.
  return fragment;
 }
}
