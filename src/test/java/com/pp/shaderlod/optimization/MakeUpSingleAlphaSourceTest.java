package com.pp.shaderlod.optimization;
import com.pp.shaderlod.optimization.makeupearlyalpha.*;
import java.nio.charset.StandardCharsets;
import net.irisshaders.iris.gl.blending.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MakeUpSingleAlphaSourceTest {
 private static final AlphaTest ALPHA=new AlphaTest(AlphaTestFunction.GREATER,.1f);
 private static String nativeStage()throws Exception{try(var in=MakeUpSingleAlphaSourceTest.class.getResourceAsStream("/com/pp/shaderlod/optimization/g03-native-cutout.glsl")){assertNotNull(in);return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
 @Test void exactDeltaOnlyRemovesFinalTestAndRetainsSamplingAndOutput()throws Exception{
  String nativeSource=nativeStage(),current=MakeUpPostSampleAlphaSource.rewrite(nativeSource,ALPHA),single=MakeUpSingleAlphaSource.rewrite(current,ALPHA);assertEquals(MakeUpSingleAlphaSource.CURRENT_SHA,java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(current.getBytes(StandardCharsets.UTF_8))));assertNotNull(single);
  String late="if (!(iris_FragData0.a > iris_currentAlphaTest)) {\ndiscard;\n}";int at=current.lastIndexOf(late);
  assertEquals(current.substring(0,at)+current.substring(at+late.length()),single);
  assertEquals(2,current.split("discard;",-1).length-1);assertEquals(1,single.split("discard;",-1).length-1);
  int kill=single.indexOf("if (!(clamp(blockColor.a");assertTrue(kill>single.indexOf("fog_texture = texture(gaux4"));
  assertFalse(java.util.regex.Pattern.compile("\\b(texture|textureLod|shadow2D|dFdx|dFdy|fwidth)\\s*\\(").matcher(single.substring(kill)).find());
  assertTrue(single.contains("blockColor = clamp(blockColor, vec4(0.0f), vec4(vec3(50.0f), 1.0f));\niris_FragData0 = blockColor;"));
  assertEquals(nativeSource.substring(0,nativeSource.indexOf("void main()")),single.substring(0,single.indexOf("void main()")));
  var writes=java.util.regex.Pattern.compile("(?m)^.*blockColor.*$").matcher(single.substring(kill));while(writes.find()){String line=writes.group();assertTrue(line.contains("blockColor.rgb")||line.startsWith("if (!(clamp(blockColor.a")||line.startsWith("blockColor = clamp(")||line.equals("iris_FragData0 = blockColor;"),line);}
 }
 @Test void wholeStageAlphaAndMetadataPoliciesRefuse()throws Exception{
  String current=MakeUpPostSampleAlphaSource.rewrite(nativeStage(),ALPHA);
  for(String mutation:new String[]{current+"\n",current.replace("blockColor.rgb = mix","blockColor.rgba = mix"),current.replace("iris_currentAlphaTest","changed"),current.replace("73.334f","73.333f"),nativeStage()})assertNull(MakeUpSingleAlphaSource.rewrite(mutation,ALPHA));
  for(AlphaTest a:new AlphaTest[]{null,AlphaTest.ALWAYS,new AlphaTest(AlphaTestFunction.LESS,.1f),new AlphaTest(AlphaTestFunction.GREATER,.2f),new AlphaTest(AlphaTestFunction.GREATER,Float.NaN)})assertNull(MakeUpSingleAlphaSource.rewrite(current,a));
  assertNull(MakeUpSingleAlphaSource.rewrite(null,ALPHA));
 }
}
