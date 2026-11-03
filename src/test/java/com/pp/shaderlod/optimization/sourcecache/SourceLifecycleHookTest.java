package com.pp.shaderlod.optimization.sourcecache;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies real local loader hook shapes; this does not apply mixins or claim a game launch. */
class SourceLifecycleHookTest {
 private record Method(String name,String descriptor,int returns,List<String> calls){}
 private static List<Method> methods(ZipFile jar,String type)throws Exception {
  var out=new ArrayList<Method>();try(var in=jar.getInputStream(jar.getEntry(type+".class"))){
   new ClassReader(in.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9){
    public MethodVisitor visitMethod(int a,String name,String descriptor,String signature,String[] exceptions){return new MethodVisitor(Opcodes.ASM9){int returns;final List<String> calls=new ArrayList<>();
     public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf){calls.add(owner+"."+name+descriptor);}
     public void visitInsn(int opcode){if(opcode>=Opcodes.IRETURN&&opcode<=Opcodes.RETURN)returns++;}
     public void visitEnd(){out.add(new Method(name,descriptor,returns,List.copyOf(calls)));}
    };}
   },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
  }return out;
 }
 private static Method require(List<Method> methods,String name,String descriptor){return methods.stream().filter(m->m.name().equals(name)&&m.descriptor().equals(descriptor)).findFirst().orElseThrow(()->new AssertionError(name+descriptor));}
 @Test void pinnedRootAndInstalledPortArtifactsExposeTheExactObservedLoadingSeams()throws Exception {
  assertTrue(SourceLifecyclePins.exact(),"The root observer pins match the actual test classpath");
  var artifacts=new ArrayList<Path>();artifacts.add(Path.of("dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar"));
  try(var paths=Files.walk(Path.of("ports"),3)){paths.filter(p->Files.isRegularFile(p)&&p.getParent().getFileName().toString().equals("runtime")&&p.getFileName().toString().matches("iris-(fabric|neoforge)-.*\\.jar")).forEach(artifacts::add);}
  // Root tests require their pinned root runtime; port runtimes are installed separately.
  assertTrue(Files.isRegularFile(artifacts.get(0)), "Root pinned Iris artifact is required");
  for(var artifact:artifacts)try(var jar=new ZipFile(artifact.toFile())){
   String root="net/irisshaders/iris/";
   var iris=methods(jar,root+"Iris");
   var factory=require(iris,"createPipeline","(Lnet/irisshaders/iris/shaderpack/materialmap/NamespacedId;)Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;");
   assertTrue(factory.calls().contains(root+"shaderpack/ShaderPack.getProgramSet(Lnet/irisshaders/iris/shaderpack/materialmap/NamespacedId;)Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;"),artifact.toString());
   assertTrue(factory.calls().contains(root+"pipeline/IrisRenderingPipeline.<init>(Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;)V"),artifact.toString());
   require(iris,"reload","()V");require(iris,"destroyEverything","()V");
   var pack=methods(jar,root+"shaderpack/ShaderPack");
   var constructor=require(pack,"<init>","(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)V");
   assertEquals(2,constructor.calls().stream().filter(c->c.contains("ShaderPackSourceNames.findPresentSources")).count(),artifact.toString());
   assertEquals(3,constructor.calls().stream().filter(c->c.equals("java/nio/file/Files.exists(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z")).count(),artifact.toString());
   assertEquals(3,require(methods(jar,root+"shaderpack/IdMap"),"readProperties","(Ljava/nio/file/Path;Ljava/lang/String;)Ljava/lang/String;").returns(),artifact.toString());
   assertEquals(3,require(pack,"readProperties","(Ljava/nio/file/Path;Ljava/lang/String;)Ljava/lang/String;").returns(),artifact.toString());
   require(pack,"lambda$new$2","(Ljava/util/List;Ljava/lang/String;)Lnet/irisshaders/iris/shaderpack/properties/ShaderProperties;");
   require(methods(jar,root+"shaderpack/option/ShaderPackOptions"),"<init>","(Lnet/irisshaders/iris/shaderpack/include/IncludeGraph;Ljava/util/Map;)V");
   require(methods(jar,root+"shaderpack/programs/ProgramSet"),"<init>","(Lnet/irisshaders/iris/shaderpack/include/AbsolutePackPath;Ljava/util/function/Function;Lnet/irisshaders/iris/shaderpack/properties/ShaderProperties;Lnet/irisshaders/iris/shaderpack/ShaderPack;)V");
  }
 }
 @Test void inputCaptureHasOneRegisteredOwnerAndEveryLoaderPackagesTheLifecycleConfig()throws Exception {
  var name="shaderlod-source-lifecycle.mixins.json";
  assertFalse(Files.readString(Path.of("src/main/resources/shaderlod-makeup-early-alpha.mixins.json")).contains("MakeUpOptionsInputMixin"));
  assertTrue(Files.readString(Path.of("src/main/templates/META-INF/neoforge.mods.toml")).contains(name));
  for(String version:List.of("1.21.11","26.1.2","26.2","26.3"))for(String loader:List.of("fabric","neoforge")){
   var module=Path.of("ports",version,loader);assertTrue(Files.readString(module.resolve("build.gradle")).contains(name));
   var metadata=module.resolve(loader.equals("fabric")?"src/main/resources/fabric.mod.json":"src/main/resources/META-INF/neoforge.mods.toml");
   assertTrue(Files.readString(metadata).contains(name),module.toString());
  }
 }
}
