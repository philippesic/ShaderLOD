package com.pp.shaderlod.experiment.makeup;
import java.util.regex.Pattern;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;
/** One explicitly owned MakeUp no-op function, after native Iris alpha injection. */
public final class MakeupAlphaInterface {
 private MakeupAlphaInterface(){}
 private static final Pattern STUB=Pattern.compile("bool\\s+shaderlodMakeupNativeAlphaReject\\s*\\(\\s*float\\s+finalAlpha\\s*\\)\\s*\\{\\s*return\\s+false\\s*;\\s*\\}");
 public static String adapt(String nativeFragment,AlphaTest alpha){
  if(nativeFragment==null || alpha==null)return null;
  var stub=STUB.matcher(nativeFragment); if(!stub.find())return null;
  int start=stub.start(),end=stub.end(); if(stub.find())return null;
  if(alpha.function()==AlphaTestFunction.ALWAYS)return nativeFragment;
  // Iris itself defines the non-comparison special case when reference==Float.MAX_VALUE.
  // Require its original consumer intact rather than rebuilding any threshold/operator.
  String late=alpha.toExpression("iris_FragData0.a","iris_currentAlphaTest","").trim();
  if(alpha.function()!=AlphaTestFunction.ALWAYS){
   String compact=nativeFragment.replaceAll("\\s+","");
   String nativeLate=late.replaceAll("\\s+","");
   if(!compact.endsWith(nativeLate+"}"))return null;
   if(alpha.reference()==Float.MAX_VALUE){
    if(!nativeFragment.matches("(?s).*\\bin\\s+float\\s+iris_vertexColorAlpha\\s*;.*"))return null;
   }else if(alpha.function()!=AlphaTestFunction.NEVER && (!Float.isFinite(alpha.reference()) ||
       !nativeFragment.matches("(?s).*\\buniform\\s+float\\s+iris_currentAlphaTest\\s*;.*")))return null;
  }
  String body=alpha.function()==AlphaTestFunction.ALWAYS?"return false;":
      alpha.toExpression("finalAlpha","iris_currentAlphaTest","").replace("discard;","return true;")+"return false;";
  return nativeFragment.substring(0,start)+"bool shaderlodMakeupNativeAlphaReject(float finalAlpha) {\n"+body+"\n}"+nativeFragment.substring(end);
 }
}
