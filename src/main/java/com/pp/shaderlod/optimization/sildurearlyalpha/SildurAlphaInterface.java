package com.pp.shaderlod.optimization.sildurearlyalpha;import java.util.regex.*;import net.irisshaders.iris.gl.blending.*;
public final class SildurAlphaInterface{private SildurAlphaInterface(){}private static final Pattern STUB=Pattern.compile("bool\\s+shaderlodSildurNativeAlphaReject\\s*\\(\\s*float\\s+finalAlpha\\s*\\)\\s*\\{\\s*return\\s+false\\s*;\\s*\\}");public static boolean candidate(String s){return s!=null&&Pattern.compile("shaderlodSildurNativeAlphaReject\\s*\\(\\s*albedo\\.\\s*a\\s*\\)").matcher(s).find();}private static boolean protocol(String s){return s!=null&&s.contains("shaderlodSildurEarlyAlphaPermission")&&s.contains("shaderlodSildurEarlyAlphaReference")&&s.contains("shaderlodOptEarlyAlpha")&&(s.contains("texture2DLod")||s.contains("textureLod"));}
public static String prepare(String s,AlphaTest a){if(!candidate(s))return s;if(!protocol(s)||a==null)return null;if(a.function()==AlphaTestFunction.ALWAYS)return s;if(a.function()!=AlphaTestFunction.GREATER||!Float.isFinite(a.reference())||a.reference()<0||a.reference()>1)return null;var m=STUB.matcher(s);if(!m.find())return null;int begin=m.start(),end=m.end();if(m.find())return null;String body=a.toExpression("finalAlpha","shaderlodSildurEarlyAlphaReference","").replace("discard;","return true;")+"return false;";return s.substring(0,begin)+"bool shaderlodSildurNativeAlphaReject(float finalAlpha){\n"+body+"\n}"+s.substring(end);}
public static boolean transformed(String s,AlphaTest a){if(!protocol(s)||a==null||a.function()!=AlphaTestFunction.GREATER||!Float.isFinite(a.reference())||a.reference()<0||a.reference()>1)return false;String c=s.replaceAll("\\s+","");String late=a.toExpression("iris_FragData0.a","iris_currentAlphaTest","").replaceAll("\\s+","");return c.endsWith(late+"}")&&!STUB.matcher(s).find()&&c.contains("!(finalAlpha>shaderlodSildurEarlyAlphaReference)")&&c.contains("returntrue;")&&s.matches("(?s).*\\buniform\\s+float\\s+iris_currentAlphaTest\\s*;.*");}
private static final Pattern EARLY_UNIFORM=Pattern.compile("\\buniform\\s+int\\s+shaderlodOptEarlyAlpha\\s*;");
private static final Pattern EARLY_CONST=Pattern.compile("\\bconst\\s+int\\s+shaderlodOptEarlyAlpha\\s*=\\s*0\\s*;");
/** Only the unique pinned declaration is changed; no native alpha test or helper body changes. */
public static String absent(String raw){
 if(!candidate(raw)||!protocol(raw))return null;
 var m=EARLY_UNIFORM.matcher(raw);if(!m.find())return null;
 int start=m.start(),end=m.end();if(m.find()||EARLY_CONST.matcher(raw).find())return null;
 return raw.substring(0,start)+"const int shaderlodOptEarlyAlpha = 0;"+raw.substring(end);
}
public static boolean containsAbsentDeclaration(String text){return text!=null&&EARLY_CONST.matcher(text).find();}
public static boolean absentTransformed(String text,AlphaTest alpha){
 if(!protocol(text)||alpha==null||alpha.function()!=AlphaTestFunction.GREATER||
    !Float.isFinite(alpha.reference())||alpha.reference()<0||alpha.reference()>1||
    EARLY_UNIFORM.matcher(text).find())return false;
 var m=EARLY_CONST.matcher(text);if(!m.find()||m.find())return false;
 String compact=text.replaceAll("\\s+","");
 String late=alpha.toExpression("iris_FragData0.a","iris_currentAlphaTest","").replaceAll("\\s+","");
 return compact.endsWith(late+"}")&&text.matches("(?s).*\\buniform\\s+float\\s+iris_currentAlphaTest\\s*;.*");
}
}
