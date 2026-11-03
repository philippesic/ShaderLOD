package com.pp.shaderlod.optimization.sourcecache;

import com.google.common.collect.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.irisshaders.iris.config.IrisConfig;
import net.irisshaders.iris.gl.blending.*;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.programs.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs actual consumer methods with a synchronous proof/validator platform fixture.
 * The fixture substitutes Iris's client singleton and archive verifier, never the
 * consumer registration, proof keys, loading callbacks or render lookup methods.
 * No native GL draw or mixin application is implied.
 */
public class ConsumerLoadingRegressionTest {
 @TempDir Path directory;
 private static final String BASE="com.pp.shaderlod.optimization.";
 private static final String SPATIAL=BASE+"bslspatial.BslSpatialRuntime";
 private static final String RECEIVER=BASE+"bsl.BslReceiverRuntime";
 private static final String AO_DEPTH=BASE+"bsl.BslAoDepthRuntime";
 private static final String SILDUR=BASE+"sildurprime.SildurDepthPriming";
 private static SourceProofEpoch epoch;
 private static SourceProofEpoch.Attempt attempt;
 private static Object pipeline;
 private static ShaderPack pack;
 private static Path sourceDirectory;
 private static IrisConfig config;
 private static final AtomicInteger sourceCalls=new AtomicInteger(),archiveCalls=new AtomicInteger();
 private static String expectedBytes;
 private static boolean qualification=true;
 public static boolean loading(){return attempt!=null;}
 public static boolean proof(Object key,IrisSourceLifecycle.Validation validation)throws Exception {
  return attempt!=null?attempt.validate(key,()->{try{return validation.run();}catch(Exception e){throw new RuntimeException(e);}}):epoch.current(pipeline,key);
 }
 public static boolean profile(Map<String,String> ignored){return true;}
 public static boolean alphaSource(ShaderPack ignored){sourceCalls.incrementAndGet();return qualification;}
 public static boolean accepts(String family,Map<String,String> ignored){return family.equals("bsl-shaders");}
 public static Optional<ShaderPack> currentPack(){return Optional.ofNullable(pack);}
 public static Path shaderpacks(){return sourceDirectory;}
 public static IrisConfig config(){return config;}
 public static boolean fallback(){return false;}
 public static String verify(Path archive,Map<String,String> ignored)throws Exception {
  archiveCalls.incrementAndGet();return Files.readString(archive).equals(expectedBytes)?"bsl-shaders":null;
 }
 private static final class FixtureLoader extends ClassLoader {
  FixtureLoader(){super(ConsumerLoadingRegressionTest.class.getClassLoader());}
  @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
   synchronized(getClassLoadingLock(name)) {
    var found=findLoadedClass(name);if(found!=null)return found;
    byte[] bytes=null;
    if(name.equals(IrisSourceLifecycle.class.getName()))bytes=shim(name,Map.of("loading","loading","proof","proof"));
    else if(name.equals(BASE+"bslearlyalpha.BslEarlyAlphaRuntime"))bytes=shim(name,Map.of("profile","profile","source","alphaSource"));
    else if(name.equals("net.irisshaders.iris.Iris"))bytes=shim(name,Map.of("getCurrentPack","currentPack","getShaderpacksDirectory","shaderpacks","getIrisConfig","config","isFallback","fallback"));
    else if(name.equals("com.pp.shaderlod.packs.PackSelectionService"))bytes=shim(name,Map.of("verifyGeneratedRendererGraph","verify"));
    else if(name.equals("com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract"))bytes=shim(name,Map.of("accepts","accepts"));
    else if(name.equals(SPATIAL)||name.startsWith(SPATIAL+"$")||name.equals(RECEIVER)||name.equals(AO_DEPTH)||name.equals(SILDUR)) {
     try(var in=getParent().getResourceAsStream(name.replace('.','/')+".class")){bytes=Objects.requireNonNull(in).readAllBytes();}
     catch(Exception e){throw new ClassNotFoundException(name,e);}
    }
    if(bytes==null)return super.loadClass(name,resolve);
    var type=defineClass(name,bytes,0,bytes.length);if(resolve)resolveClass(type);return type;
   }
  }
  private static byte[] shim(String name,Map<String,String> methods) {
   var writer=new ClassWriter(0);writer.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,name.replace('.','/'),null,"java/lang/Object",null);
   for(var entry:methods.entrySet()) {
    Method target=Arrays.stream(ConsumerLoadingRegressionTest.class.getMethods()).filter(m->m.getName().equals(entry.getValue())).findFirst().orElseThrow();
    String descriptor=Type.getMethodDescriptor(target);var method=writer.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,entry.getKey(),descriptor,null,null);method.visitCode();int slot=0;
    for(var argument:Type.getArgumentTypes(descriptor)){method.visitVarInsn(argument.getOpcode(Opcodes.ILOAD),slot);slot+=argument.getSize();}
    method.visitMethodInsn(Opcodes.INVOKESTATIC,Type.getInternalName(ConsumerLoadingRegressionTest.class),target.getName(),descriptor,false);
    method.visitInsn(Type.getReturnType(descriptor).getOpcode(Opcodes.IRETURN));method.visitMaxs(Math.max(slot,1),slot);method.visitEnd();
   }
   writer.visitEnd();return writer.toByteArray();
  }
 }
 private static <T> T allocate(Class<T> type)throws Exception {
  var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);return type.cast(((sun.misc.Unsafe)field.get(null)).allocateInstance(type));
 }
 private static void field(Object owner,String name,Object value)throws Exception {
  var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);
 }
 private static final class Pack extends ShaderPack {
  ShaderPackOptions options;
  private Pack()throws Exception {super(Path.of("unused"),ImmutableList.of(),false);}
  @Override public ShaderPackOptions getShaderPackOptions(){return options;}
 }
 private FixtureLoader prepare()throws Exception {
  epoch=new SourceProofEpoch();attempt=null;pipeline=new Object();qualification=true;sourceCalls.set(0);archiveCalls.set(0);
  var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);constructor.setAccessible(true);
  var path=AbsolutePackPath.fromAbsolutePath("/gbuffers_terrain.fsh");
  var raw=constructor.newInstance(ImmutableMap.of(path,new FileNode(path,ImmutableList.of("#define SHADERLOD_BSL_DEPTH_PRIMING 0 // [0 1]","void main(){int value=SHADERLOD_BSL_DEPTH_PRIMING;}"))),ImmutableMap.of());
  var fixture=allocate(Pack.class);fixture.options=new ShaderPackOptions(raw,Map.of());EffectiveOptions.capture(fixture.options);pack=fixture;
  sourceDirectory=directory;config=allocate(IrisConfig.class);config.setShaderPackName("source.zip");expectedBytes="loaded inputs";Files.writeString(directory.resolve("source.zip"),expectedBytes);
  return new FixtureLoader();
 }
 private static Object owner(Class<?> type,int id)throws Exception {
  var field=type.getDeclaredField("OWNERS");field.setAccessible(true);return ((Map<?,?>)field.get(null)).get(id);
 }
 @Test void bslSuccessfulRegistrationProducesTheReceiptConsumedBySpatialRendering()throws Exception {
  var loader=prepare();var type=loader.loadClass(SPATIAL);var nativePipeline=allocate(IrisRenderingPipeline.class);
  var set=allocate(ProgramSet.class);field(set,"pack",pack);
  var source=allocate(ProgramSource.class);field(source,"parent",set);field(source,"name","gbuffers_terrain");field(source,"fragmentSource","shaderlodBslSpatialPhase");
  type.getMethod("prepared",ProgramSource.class,AlphaTest.class,String.class).invoke(null,source,new AlphaTest(AlphaTestFunction.ALWAYS,0),"shaderlodBslSpatialPhase");
  attempt=epoch.begin();
  type.getMethod("own",IrisRenderingPipeline.class,SodiumPrograms.Pass.class,ProgramSource.class,int.class).invoke(null,nativePipeline,SodiumPrograms.Pass.TERRAIN,source,37);
  var registered=owner(type,37);assertNotNull(registered);assertEquals(1,sourceCalls.get(),"The actual own callback must qualify the owner while loading");
  assertTrue(attempt.publish(pipeline,()->true));attempt.close();attempt=null;
  var sourceLookup=type.getDeclaredMethod("source",registered.getClass());sourceLookup.setAccessible(true);
  qualification=false;Files.writeString(directory.resolve("source.zip"),"changed after linking");
  for(int i=0;i<1000;i++)assertEquals(true,sourceLookup.invoke(null,registered));
  assertEquals(1,sourceCalls.get(),"Draw lookup must not execute the source supplier again");
  epoch.invalidate();assertEquals(false,sourceLookup.invoke(null,registered));
 }
 @Test void bslReceiverLoadingProducerSupportsDelayedReceiverAndAoDepthActivation()throws Exception {
  var loader=prepare();var type=loader.loadClass(RECEIVER);var lookup=type.getDeclaredMethod("source");lookup.setAccessible(true);
  var aoLookup=loader.loadClass(AO_DEPTH).getDeclaredMethod("sourceForDraw");aoLookup.setAccessible(true);
  var consumers=SourceValidationWarmup.class.getDeclaredField("CONSUMERS");consumers.setAccessible(true);
  assertTrue(Arrays.asList((String[])consumers.get(null)).contains(RECEIVER),"The real loading warmup registers this producer");
  assertEquals(false,lookup.invoke(null));assertEquals(0,archiveCalls.get());
  attempt=epoch.begin();type.getMethod("qualifyLoadedSource",ShaderPack.class).invoke(null,pack);
  assertEquals(1,archiveCalls.get());assertTrue(attempt.publish(pipeline,()->true));attempt.close();attempt=null;
  Files.writeString(directory.resolve("source.zip"),"changed before first requested draw");
  for(int i=0;i<1000;i++){assertEquals(true,lookup.invoke(null));assertEquals(true,aoLookup.invoke(null));}
  assertEquals(1,archiveCalls.get(),"Delayed activation uses loaded inputs even after disk mutation");
  epoch.invalidate();assertEquals(false,lookup.invoke(null));
  attempt=epoch.begin();type.getMethod("qualifyLoadedSource",ShaderPack.class).invoke(null,pack);assertTrue(attempt.publish(pipeline,()->true));attempt.close();attempt=null;
  assertEquals(false,lookup.invoke(null));assertEquals(2,archiveCalls.get(),"Fresh loading validates its own changed bytes and refuses them");
  expectedBytes=Files.readString(directory.resolve("source.zip"));attempt=epoch.begin();type.getMethod("qualifyLoadedSource",ShaderPack.class).invoke(null,pack);
  assertTrue(attempt.publish(pipeline,()->true));attempt.close();attempt=null;assertEquals(true,lookup.invoke(null));
 }
 @Test void sildurPreparedSourceReceiptSurvivesWorldSwitchAndCachedPipelineReturn()throws Exception {
  var type=prepare().loadClass(SILDUR);var overworld=epoch;Object overworldPipeline=pipeline;
  // Source qualification seam: simulate a successful immutable source/profile check before
  // a client world exists. The real selected/candidatePack lookup and key are exercised below.
  try(var load=epoch.begin()) {
   assertTrue(load.validate(Arrays.asList(type,"selected",true),()->true));
   assertTrue(load.validate(Arrays.asList(type,"selected",false),()->true));
   assertTrue(load.publish(pipeline,()->true));
  }
  assertEquals(true,type.getMethod("candidatePack").invoke(null),"Preparation does not need a current Minecraft level");
  epoch=new SourceProofEpoch();pipeline=new Object();assertEquals(false,type.getMethod("candidatePack").invoke(null),"Another dimension cannot borrow the old pipeline receipt");
  epoch=overworld;pipeline=overworldPipeline;assertEquals(true,type.getMethod("candidatePack").invoke(null),"Returning to the cached dimension consumes its own immutable proof");
  assertEquals(true,type.getMethod("setup").invoke(null));
  assertSildurDimensionSeparation();
 }
 private static void assertSildurDimensionSeparation()throws Exception {
  var sourceFields=new ArrayList<String>();var drawFields=new ArrayList<String>();var drawCalls=new ArrayList<String>();
  try(var in=ConsumerLoadingRegressionTest.class.getClassLoader().getResourceAsStream(SILDUR.replace('.','/')+".class")) {
   new ClassReader(in.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
    public MethodVisitor visitMethod(int a,String name,String d,String s,String[] e) {
     if(!name.startsWith("lambda$selected$")&&!name.equals("selectedUncached")&&!name.equals("drawEnabled"))return null;
     return new MethodVisitor(Opcodes.ASM9) {
      public void visitFieldInsn(int op,String owner,String field,String descriptor){(name.equals("drawEnabled")?drawFields:sourceFields).add(field);}
      public void visitMethodInsn(int op,String owner,String method,String descriptor,boolean itf){if(name.equals("drawEnabled"))drawCalls.add(method);}
     };
    }
   },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
  }
  assertFalse(sourceFields.contains("level"),"World state must never enter the cached source predicate");
  assertTrue(drawFields.contains("level"));assertTrue(drawFields.contains("OVERWORLD"));assertTrue(drawCalls.contains("dimension"));
 }
}
