package com.pp.shaderlod.optimization.depthprime;
import java.util.*;
import net.irisshaders.iris.gl.blending.*;
import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpOpaqueAlphaSpecialization;
import com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedAlphaInterface;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DepthPatchArgumentsTest {
 @Test void allNineKeepExactUnmodifiedInputsAndRefuseUnrelatedChanges(){
  for(var family:List.of("makeup-ultra-fast-shaders","complementary-reimagined","complementary-unbound","sildurs-vibrant-shaders","bsl-shaders","astralex","bliss-shader","solas-shader","photon")){
   assertTrue(DepthPatchArguments.fragment(family,"void main(){}","void main(){}",AlphaTest.ALWAYS));
   assertFalse(DepthPatchArguments.fragment(family,"void main(){}","void main(){discard;}",AlphaTest.ALWAYS));
   assertFalse(DepthPatchArguments.fragment(family,"void main(){}",null,AlphaTest.ALWAYS));
  }
 }
 @Test void makeupAllowsOnlyTheExistingOpaqueReduction()throws Exception{
  String raw;
  try(var in=getClass().getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/shared/makeup_early_alpha.glsl")){
   assertNotNull(in);raw=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)
    .replace("#if SHADERLOD_MAKEUP_EARLY_ALPHA == 1\n", "").replace("#else\n    return false;\n#endif\n}", "}")
    +"\nvoid main(){vec4 blockColor=vec4(1.0);if(shaderlodMakeupNativeAlphaReject(clamp(blockColor.a,0.0,1.0)))discard;}\n";
  }
  String exact=MakeUpOpaqueAlphaSpecialization.specialize(raw,AlphaTest.ALWAYS,true);assertNotNull(exact);
  assertTrue(DepthPatchArguments.fragment("makeup-ultra-fast-shaders",raw,exact,AlphaTest.ALWAYS));
  assertFalse(DepthPatchArguments.fragment("makeup-ultra-fast-shaders",raw,exact+"\n// unexpected edit",AlphaTest.ALWAYS));
  assertFalse(DepthPatchArguments.fragment("makeup-ultra-fast-shaders",raw,exact,AlphaTests.ONE_TENTH_ALPHA));
 }
 @Test void reimaginedCutoutAcceptsExactNativeAlphaRewriteButNoExtraDiscard(){
  String raw="uniform int shaderlodReimaginedEarlyAlphaPermission; uniform float shaderlodReimaginedEarlyAlphaReference; uniform int shaderlodOptEarlyAlpha;\n"
    +"bool shaderlodReimaginedNativeAlphaReject(float finalAlpha){return false;}\n"
    +"void main(){vec4 color=texture2DLod(tex,uv,0.0); float shadow=shadow2DLod(shadowtex0,coord,0.0); if(shaderlodReimaginedNativeAlphaReject(color.a))discard;}";
  String exact=ReimaginedAlphaInterface.prepare(raw,AlphaTests.ONE_TENTH_ALPHA);assertNotNull(exact);assertNotEquals(raw,exact);
  assertTrue(DepthPatchArguments.fragment("complementary-reimagined",raw,exact,AlphaTests.ONE_TENTH_ALPHA));
  assertFalse(DepthPatchArguments.fragment("complementary-reimagined",raw,exact,AlphaTest.ALWAYS));
  assertFalse(DepthPatchArguments.fragment("complementary-reimagined",raw,exact.replace("void main(){","void main(){discard;"),AlphaTests.ONE_TENTH_ALPHA));
 }
}
