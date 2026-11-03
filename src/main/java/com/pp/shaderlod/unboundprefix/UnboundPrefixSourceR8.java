package com.pp.shaderlod.unboundprefix;
import java.util.*;
import java.util.regex.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
/** Finite Unbound-only function comparison, never a shader rewrite. Numeric spelling
 * and Iris textureGrad spelling are normalized; token order and operators remain exact. */
public final class UnboundPrefixSourceR8 {
 public static final List<String> FUNCTIONS=List.of("shaderlodUnboundPrefixUV","shaderlodUnboundPrefixSupport","shaderlodUnboundPrefixDescriptor","shaderlodUnboundPrefixCertificate","shaderlod_unbound_prefix_continue","ReadNormal","GetParallaxCoord","GetParallaxShadow","GetParallaxSlopeNormal");
 private static final Pattern TOKEN=Pattern.compile("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?[fF]?|[a-zA-Z_][a-zA-Z_0-9]*|[^\\s]");
 public static String function(String text,String name){
  text=text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", "");
  Matcher m=Pattern.compile("\\b(?:vec[234]|float|bool)\\s+"+Pattern.quote(name)+"\\s*\\(").matcher(text);
  if(!m.find())throw new IllegalArgumentException("missing function "+name);
  int start=m.start(),open=text.indexOf('{',m.end()),depth=1,i=open+1;
  if(open<0)throw new IllegalArgumentException("missing body");
  for(;i<text.length()&&depth>0;i++){char c=text.charAt(i);if(c=='{')depth++;if(c=='}')depth--;}
  if(depth!=0||m.find())throw new IllegalArgumentException("duplicate/truncated function");
  var tokens=TOKEN.matcher(text.substring(start,i));var out=new StringBuilder();
  while(tokens.find()){String t=tokens.group();if(Character.isDigit(t.charAt(0))||t.charAt(0)=='.'&&t.length()>1)t=new BigDecimal(t.replaceAll("[fF]$", "")).stripTrailingZeros().toPlainString();
   if(t.equals("texture2DGradARB"))t="textureGrad";out.append(t).append(' ');}
  return out.toString();
 }
 public static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new AssertionError(e);}}
 public static boolean matches(String actual,Map<String,String> expected){
  try{if(!expected.keySet().equals(new HashSet<>(FUNCTIONS)))return false;for(String n:FUNCTIONS)if(!hash(function(actual,n)).equals(expected.get(n)))return false;return true;}catch(RuntimeException e){return false;}
 }
 private UnboundPrefixSourceR8(){}
}
