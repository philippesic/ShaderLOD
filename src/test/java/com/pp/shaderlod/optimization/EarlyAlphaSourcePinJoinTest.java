package com.pp.shaderlod.optimization;

import com.pp.shaderlod.packs.SupportedPackCatalog;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the actual compiled guards against their packaged inputs without loading Minecraft.
 * This catches stale identity joins; it does not certify GL consumption or performance. */
class EarlyAlphaSourcePinJoinTest {
    private static final Map<String,String> RUNTIMES=Map.ofEntries(
        Map.entry("photon","photonearlyalpha/PhotonEarlyAlphaRuntime"),
        Map.entry("astralex","astraearlyalpha/AstraEarlyAlphaRuntime"),
        Map.entry("bliss-shader","blissearlyalpha/BlissEarlyAlphaRuntime"),
        Map.entry("bsl-shaders","bslearlyalpha/BslEarlyAlphaRuntime"),
        Map.entry("complementary-reimagined","reimaginedearlyalpha/ReimaginedEarlyAlphaRuntime"),
        Map.entry("complementary-unbound","unboundearlyalpha/UnboundEarlyAlphaRuntime"),
        Map.entry("makeup-ultra-fast-shaders","makeupearlyalpha/MakeUpEarlyAlphaRuntime"),
        Map.entry("sildurs-vibrant-shaders","sildurearlyalpha/SildurEarlyAlphaRuntime"),
        Map.entry("solas-shader","solasearlyalpha/SolasEarlyAlphaRuntime"));

    @TestFactory Stream<DynamicTest> compiledGuardsMatchPackagedProducerInputs() {
        return SupportedPackCatalog.inventory().stream().map(pack->DynamicTest.dynamicTest(pack.id(),()->{
            String recipe="assets/shaderlod/adapters/patches/"+(pack.id().equals("photon")
                ?"photon-experiments/live-pixel-budget.json":"adapters/"+pack.id()+".json");
            Set<String> literals=new HashSet<>(),calls=new HashSet<>();
            var loader=getClass().getClassLoader();
            try(var in=loader.getResourceAsStream("com/pp/shaderlod/optimization/"+RUNTIMES.get(pack.id())+".class")) {
                assertNotNull(in);
                new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                        if(!name.equals("sourceUncached"))return null;
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitLdcInsn(Object value){if(value instanceof String s)literals.add(s);}
                            @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf){calls.add(owner+"."+name);}
                        };
                    }
                },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            }
            assertTrue(literals.contains(recipe),"Compiled guard must read its actual packaged recipe");
            try(var in=loader.getResourceAsStream(recipe)) {
                assertNotNull(in);
                String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
                assertTrue(literals.contains(sha),"Compiled recipe pin is stale: expected "+sha);
            }
            // MakeUp delegates complete executable identity to the normal graph verifier.
            if(!pack.id().equals("makeup-ultra-fast-shaders"))
                assertTrue(literals.contains(pack.adapterSourceDigest()),"Compiled generated-source pin is stale");
            assertTrue(calls.contains("com/pp/shaderlod/packs/PackSelectionService.verifyGeneratedRendererGraph"),
                "The guarded input must reach the actual normal graph verifier");
        }));
    }
}
