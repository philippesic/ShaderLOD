package com.pp.shaderlod.integration;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Legacy NeoForge receiver callers; excluded by other loaders' existing test-source sets. */
class ComplementaryReceiverRuntimeOrderTest {
    private static AbstractInsnNode next(AbstractInsnNode instruction) {
        do { instruction = instruction.getNext(); }
        while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }

    @Test void bothActualDrawsRevokeFirstAndRunModuleAndSourceChecksOnlyInsideAdmission() throws Exception {
        String root = "com/pp/shaderlod/optimization/";
        for (String family : List.of("Reimagined", "Unbound")) {
            String owner = root+family.toLowerCase()+"earlyalpha/"+family+"EarlyAlphaRuntime";
            var node = new ClassNode();
            try (var input = getClass().getClassLoader().getResourceAsStream(owner+".class")) {
                assertNotNull(input); new ClassReader(input).accept(node, 0);
            }
            var draw = node.methods.stream().filter(m -> m.name.equals("receiverDraw")).findFirst().orElseThrow();
            int admission = -1, revoke = -1, grant = -1, sampler = -1;
            Handle callback = null;
            for (int i = 0; i < draw.instructions.size(); i++) {
                var instruction = draw.instructions.get(i);
                if (instruction instanceof InvokeDynamicInsnNode dynamic)
                    for (Object argument : dynamic.bsmArgs)
                        if (argument instanceof Handle handle && handle.getOwner().equals(owner)) callback = handle;
                if (!(instruction instanceof MethodInsnNode call)) continue;
                assertFalse(Set.of("getMods", "loadedStackMatches").contains(call.name), "Module enumeration must be deferred, "+family);
                assertFalse(Set.of("receiverSource", "temporalSource", "drawReceiverSource", "drawTemporalSource").contains(call.name),
                    "Source validation must be deferred, "+family);
                if (call.name.equals("validate") && call.owner.equals(root+"ComplementaryReceiverAdmission")) {
                    admission = i;
                    var jump = next(call);
                    assertInstanceOf(JumpInsnNode.class, jump);
                    assertEquals(Opcodes.IFNE, jump.getOpcode());
                    var refusal=next(jump);boolean returned=false;
                    while(refusal!=null && refusal!=((JumpInsnNode)jump).label) {
                        if(refusal instanceof MethodInsnNode cleanup) {
                            assertEquals("invalidate",cleanup.name,"Only pass-cache refusal cleanup may precede return");
                            assertTrue(cleanup.owner.endsWith("ComplementarySamplerPass$Lane"));
                        }
                        if(refusal.getOpcode()==Opcodes.RETURN){returned=true;break;}
                        refusal=next(refusal);
                    }
                    assertTrue(returned,"Refusal returns after invalidating pass observations, before any grant");
                }
                if (call.name.equals("samplerObservations") && sampler < 0) sampler = i;
                if (call.name.equals("glUniform1i")) {
                    int value = call.getPrevious().getOpcode();
                    if (value == Opcodes.ICONST_0 && revoke < 0) revoke = i;
                    if (value == Opcodes.ICONST_1) grant = i;
                }
            }
            assertTrue(revoke >= 0 && revoke < admission && admission < sampler && sampler < grant, family);
            assertNotNull(callback, "The actual draw must supply a deferred proof");
            String callbackName = callback.getName();
            var proof = node.methods.stream().filter(m -> m.name.equals(callbackName)).findFirst().orElseThrow();
            Set<String> calls = new HashSet<>();
            for (var instruction : proof.instructions)
                if (instruction instanceof MethodInsnNode call) calls.add(call.name);
            assertTrue(calls.containsAll(Set.of("loadedStackMatches", "resourcesMatch",
                "receiverSourceForDraw")), family);
        }
    }
}
