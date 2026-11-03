package com.pp.shaderlod.optimization;

import com.pp.shaderlod.optimization.makeupearlyalpha.mixin.MakeUpEarlyAlphaPlugin;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual pinned observer and compile/link targets; runtime injection still requires a game test. */
class MakeUpCompilationApiTest {
    private Set<String> methods(String resource) throws Exception {
        var result=new HashSet<String>();
        try(var in=getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in);
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                    result.add(name+descriptor);return null;
                }
            },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        return result;
    }
    @Test void allObservedPinnedConstructorsAndPropertiesMethodActuallyExist() throws Exception {
        assertTrue(MakeUpEarlyAlphaPlugin.exactApi());
        var pack=methods("net/irisshaders/iris/shaderpack/ShaderPack.class");
        assertTrue(pack.contains("<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)V"));
        assertTrue(pack.contains("readProperties(Ljava/nio/file/Path;Ljava/lang/String;)Ljava/lang/String;"));
        assertTrue(pack.contains("lambda$new$2(Ljava/util/List;Ljava/lang/String;)Lnet/irisshaders/iris/shaderpack/properties/ShaderProperties;"));
        assertTrue(methods("net/irisshaders/iris/shaderpack/option/ShaderPackOptions.class").contains(
            "<init>(Lnet/irisshaders/iris/shaderpack/include/IncludeGraph;Ljava/util/Map;)V"));
        assertTrue(methods("net/irisshaders/iris/shaderpack/programs/ProgramSet.class").contains(
            "<init>(Lnet/irisshaders/iris/shaderpack/include/AbsolutePackPath;Ljava/util/function/Function;Lnet/irisshaders/iris/shaderpack/properties/ShaderProperties;Lnet/irisshaders/iris/shaderpack/ShaderPack;)V"));
    }
}
