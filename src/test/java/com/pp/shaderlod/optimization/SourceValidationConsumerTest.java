package com.pp.shaderlod.optimization;
import com.pp.shaderlod.optimization.sourcecache.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.irisshaders.iris.shaderpack.ShaderPack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceValidationConsumerTest {
 @Test void actualNineFamilySourceSeamsRefuseWithoutAnAuthenticLoadingProducer()throws Exception {
  IrisSourceLifecycle.invalidate();
  for(String family:List.of("photon","makeup","bsl","reimagined","unbound","sildur","bliss","astra","solas")){
   String title=family.equals("makeup")?"MakeUp":family.equals("bsl")?"Bsl":Character.toUpperCase(family.charAt(0))+family.substring(1);
   var type=Class.forName("com.pp.shaderlod.optimization."+family+"earlyalpha."+title+"EarlyAlphaRuntime");
   var source=type.getMethod("source",ShaderPack.class);
   for(int frame=0;frame<1000;frame++)assertEquals(false,source.invoke(null,(Object)null),family+" has no render-time fallback validation");
  }
 }
 @Test void actualSharedCacheNeverHashesOrCallsItsSupplierOnAColdRenderingLookup()throws Exception {
  IrisSourceLifecycle.invalidate();var cache=new EarlyAlphaSourceCache();var calls=new AtomicInteger();
  for(String family:List.of("photon","makeup-ultra-fast-shaders","bsl-shaders","complementary-reimagined","complementary-unbound","sildurs-vibrant-shaders","bliss-shader","astralex","solas-shader"))
   for(int frame=0;frame<1000;frame++)assertFalse(cache.check(getClass(),Path.of("missing source directory"),"missing.zip",family,Map.of(),new Object(),new Object(),new Object(),new Object(),new Object(),0,null,List.of(),()->{calls.incrementAndGet();throw new AssertionError("rendering reached source validator");}));
  assertEquals(0,calls.get());
 }
 @Test void steadyMethodsHaveNoDirectSourceIoOrInventoryConstruction()throws Exception {
  assertLookup("com/pp/shaderlod/optimization/sourcecache/EarlyAlphaSourceCache","check");
  assertLookup("com/pp/shaderlod/optimization/sourcecache/IrisSourceLifecycle","proof");
  assertLookup("com/pp/shaderlod/optimization/sourcecache/EffectiveOptions","current");
  assertLookup("com/pp/shaderlod/integration/makeupquad/MakeUpQuadRuntime","nativeGraphAvailable");
  assertLookup("com/pp/shaderlod/optimization/sourcecache/LoadedRendererSources","allows");
  assertLookup("com/pp/shaderlod/optimization/bsl/BslReceiverRuntime","source");
 }
 private static void assertLookup(String type,String selected)throws Exception {
  var calls=new ArrayList<String>();
  try(var in=SourceValidationConsumerTest.class.getClassLoader().getResourceAsStream(type+".class")){
   assertNotNull(in);new org.objectweb.asm.ClassReader(in.readAllBytes()).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9){
    public org.objectweb.asm.MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){return n.equals(selected)?new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9){
     public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){calls.add(owner+"."+name);}
    }:null;}
   },org.objectweb.asm.ClassReader.SKIP_DEBUG|org.objectweb.asm.ClassReader.SKIP_FRAMES);
  }
  for(String call:calls)assertFalse(call.contains("SourceArchiveDigest.sha256")||call.contains("PackSourceReader.read")||call.contains("PackSelectionService.verifyGeneratedRendererGraph")||call.contains("PackSelectionService.captureGeneratedRendererInputs")||call.contains(".getResourceAsStream")||call.contains("java/util/zip/ZipFile.<init>")||call.contains("java/nio/file/Files.read")||call.contains("EarlyAlphaSourceCache.fingerprint")||call.endsWith("OptionSet.getBooleanOptions")||call.endsWith("OptionSet.getStringOptions"),type+"."+selected+" -> "+call);
 }
 @Test void uniformOnlySessionUsesTheLoadedGraphAndRetainsFailureRecovery()throws Exception {
  var current=new java.util.concurrent.atomic.AtomicBoolean(true);var reloads=new AtomicInteger();
  var session=CompileOptionControl.uniformSession(current::get);
  assertFalse(session.prepare());session.finish();session.restore();session.recoveryReload(reloads::incrementAndGet);assertEquals(0,reloads.get());
  current.set(false);assertThrows(IllegalStateException.class,session::prepare);assertThrows(IllegalStateException.class,session::finish);
 }
 @Test void uniformSessionRequiresTheExactPublishedReceiptThroughFinishAndRecovery()throws Exception {
  // Same family/options/pack still have distinct loaded graph owners after pipeline replacement.
  record Graph(String family,Object pack,Object input,Object pipeline){}
  var pack=new Object();var input=new Object();
  Object original=new Graph("bsl-shaders",pack,input,new Object());
  var current=new java.util.concurrent.atomic.AtomicReference<>(original);var reloads=new AtomicInteger();
  var session=CompileOptionControl.uniformSession(original,current::get);
  assertFalse(session.prepare());session.finish();session.recoveryReload(reloads::incrementAndGet);
  assertTrue(CompileOptionControl.sameLoadedGraph(original,current.get()));
  for(Object replacement:List.of(new Graph("bsl-shaders",new Object(),input,new Object()),
       new Graph("bsl-shaders",pack,new Object(),new Object()),new Graph("bsl-shaders",pack,input,new Object()))) {
   current.set(replacement);
   assertFalse(CompileOptionControl.sameLoadedGraph(original,replacement));
   assertThrows(IllegalStateException.class,session::prepare);assertThrows(IllegalStateException.class,session::finish);
   session.restore();assertThrows(IllegalStateException.class,()->session.recoveryReload(reloads::incrementAndGet));
  }
  current.set(null);assertFalse(CompileOptionControl.sameLoadedGraph(original,null));
  current.set(original);session.finish();assertEquals(0,reloads.get());
 }
}
