package com.pp.shaderlod.optimization.makeupspatial;
import java.util.regex.*;
/** Exact native input prefix and export closure; retains the linked original vertex object. */
public final class MakeUpSpatialPrograms {
 private MakeUpSpatialPrograms() {}
 static Matcher unique(String pattern,String source,String what){Matcher m=Pattern.compile(pattern).matcher(source);if(!m.find())throw new IllegalArgumentException(what+" absent");int start=m.start();if(m.find())throw new IllegalArgumentException(what+" duplicated");m.reset();m.find(start);return m;}
 static int close(String s,int open){int depth=0;for(int i=open;i<s.length();i++){char c=s.charAt(i);if(c=='{')depth++;else if(c=='}'&&--depth==0)return i+1;}throw new IllegalArgumentException("unclosed native function");}
 public static String fragment(String nativeSource){
  if(!com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming.invariantStage(nativeSource,false))throw new IllegalArgumentException("native fragment side-effect/coverage contract refused");
  Matcher stage=unique("const\\s+bool\\s+shaderlodMakeupSpatialExportStage\\s*=\\s*false\\s*;",nativeSource,"export constant");
  String result=nativeSource.substring(0,stage.start())+"const bool shaderlodMakeupSpatialExportStage = true;"+nativeSource.substring(stage.end());
  Matcher main=unique("void\\s+main\\s*\\(\\s*\\)\\s*\\{",result,"native main");int end=close(result,main.end()-1);
  Matcher boundary=unique("float\\s+block_luma\\s*=\\s*luma\\s*\\(\\s*blockColor\\.rgb\\s*\\)\\s*;",result.substring(main.end(),end),"native atlas/tint/luma boundary");int at=main.end()+boundary.end();
  String tail=result.substring(at,end);if(!tail.replaceAll("\\s+","").contains("if(shaderlodMakeupSpatialExportStage){shaderlodMakeupSpatialExportInputs(blockColor,block_luma,dither);return;}"))throw new IllegalArgumentException("retained native export closure absent");
  result=result.substring(0,at)+"\nshaderlodMakeupSpatialExportInputs(blockColor,block_luma,dither);return;\n}"+result.substring(end);
  Matcher consumer=Pattern.compile("bool\\s+shaderlodMakeupSpatialTry\\s*\\([^)]*\\)\\s*\\{").matcher(result);if(consumer.find()){end=close(result,consumer.end()-1);result=result.substring(0,consumer.start())+result.substring(end);}else if(!result.contains("bool shaderlodMakeupWorldTry"))throw new IllegalArgumentException("native consumer absent");
  Matcher rate=Pattern.compile("void\\s+shaderlodMakeupSpatialRateEvaluate\\s*\\(\\s*\\)\\s*\\{").matcher(result);if(rate.find()){int finish=close(result,rate.end()-1);result=result.substring(0,rate.start())+result.substring(finish);}
  Matcher world=Pattern.compile("bool\\s+shaderlodMakeupWorldTry\\s*\\([^)]*\\)\\s*\\{").matcher(result);if(world.find()){int finish=close(result,world.end()-1);result=result.substring(0,world.start())+result.substring(finish);}
  result=result.replaceAll("uniform\\s+sampler2D\\s+colortex9\\s*,\\s*colortex11\\s*;","");
  Matcher fields=unique("uniform\\s+sampler2D\\s+colortex8\\s*,\\s*colortex10\\s*,\\s*colortex12\\s*,\\s*colortex13\\s*,\\s*colortex14\\s*,\\s*colortex15\\s*;",result,"consumer field declaration");result=result.substring(0,fields.start())+result.substring(fields.end());
  if(Pattern.compile("\\bcolortex(?:8|10|12|13|14|15)\\b").matcher(result).find())throw new IllegalArgumentException("producer retained an output-field read");return result;
 }
 public static String rateVertex(){return "#version 330 core\nlayout(location=0)in vec3 a_Position;void main(){gl_Position=vec4(a_Position.xy*2.0-1.0,0.0,1.0);}";}
 public static String rateFragment(String nativeSource){
  if(!com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming.invariantStage(nativeSource,false))throw new IllegalArgumentException("native rate source refused");
  String result=nativeSource;Matcher main=unique("void\\s+main\\s*\\(\\s*\\)\\s*\\{",result,"native main");int end=close(result,main.end()-1);
  result=result.substring(0,main.start())+"void main(){ivec2 origin=ivec2(int(gl_FragCoord.x),int(gl_FragCoord.y)*2);iris_FogFragCoord=texelFetch(colortex14,origin,0).a;isFoliage=0.0;shaderlodMakeupSpatialRateEvaluate();}\n"+result.substring(end);
  Matcher stage=unique("const\\s+bool\\s+shaderlodMakeupSpatialExportStage\\s*=\\s*false\\s*;",result,"native LOD stage");result=result.substring(0,stage.start())+"const bool shaderlodMakeupSpatialExportStage=true;"+result.substring(stage.end());
  // Closed native linked-stage interface: canonical quantities come only from the completed native input images.
  var names=new java.util.HashSet<String>();Matcher inputs=Pattern.compile("(?m)^in\\s+(float|vec2|vec3|vec4)\\s+(\\w+)\\s*;").matcher(result);StringBuffer body=new StringBuffer();while(inputs.find()){names.add(inputs.group(2));inputs.appendReplacement(body,inputs.group(1)+" "+inputs.group(2)+";");}inputs.appendTail(body);
  if(!names.equals(java.util.Set.of("iris_FogFragCoord","texcoord","tintColor","frogAdjust","directLightColor","candleColor","directLightStrength","omniLight","isEmissiveEntity","isEndPortal","isFoliage","shadowPos","shadowDiffuse","flatNormal","viewPositionNormalized","lmcoordAlt","glossFactor","glossPower","lumaFactor","lumaPower")))throw new IllegalArgumentException("native rate interface changed");
  result=body.toString();Matcher outs=Pattern.compile("(?m)^layout\\(location = ([1-5])\\) out vec4 iris_FragData[1-5];").matcher(result);result=outs.replaceAll("");
  // Remove unreachable input export and native consumer closures: no atlas sampling or input-image writes remain.
  for(String function:java.util.List.of("shaderlodMakeupSpatialExportInputs","shaderlodMakeupSpatialTry")){Matcher f=unique("(?:void|bool)\\s+"+function+"\\s*\\([^)]*\\)\\s*\\{",result,function);int finish=close(result,f.end()-1);result=result.substring(0,f.start())+result.substring(finish);}
  return result;
 }

}
