package com.pp.shaderlod.integration;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Inspect actual bytecode without creating a GL context or mocking driver queries. */
class ComplementaryRuntimeQueryScopeTest {
    private ClassNode read(String owner) throws Exception {
        var node = new ClassNode();
        try (var input = getClass().getClassLoader().getResourceAsStream(owner+".class")) {
            assertNotNull(input); new ClassReader(input).accept(node, 0);
        }
        return node;
    }
    private MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
    private List<AbstractInsnNode> code(MethodNode method) {
        var result = new ArrayList<AbstractInsnNode>();
        for (var instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }
    private long currentQueries(MethodNode method) {
        var code = code(method); long count = 0;
        for (int i=1; i<code.size(); i++)
            if (code.get(i) instanceof MethodInsnNode call && call.owner.equals("org/lwjgl/opengl/GL11")
                    && call.name.equals("glGetInteger") && code.get(i-1) instanceof LdcInsnNode constant
                    && Integer.valueOf(0x8B8D).equals(constant.cst)) count++;
        return count;
    }
    @Test void oneLiveProgramFlowsToAllDrawHelpersWhileStandaloneAndRevokeStayIndependent() throws Exception {
        for (String family : List.of("Reimagined", "Unbound")) {
            String owner = "com/pp/shaderlod/optimization/"+family.toLowerCase()+"earlyalpha/"+family+"EarlyAlphaRuntime";
            var runtime = read(owner); var draw = code(method(runtime, "draw"));
            assertEquals(1, currentQueries(method(runtime, "draw")), family);
            int program = ((VarInsnNode)draw.get(2)).var;
            var helpers = new ArrayList<String>();
            for (int i=0; i<draw.size(); i++) if (draw.get(i) instanceof MethodInsnNode call && call.owner.equals(owner)) {
                assertEquals("(I)V", call.desc);
                assertEquals(Opcodes.ILOAD, draw.get(i-1).getOpcode());
                assertEquals(program, ((VarInsnNode)draw.get(i-1)).var);
                helpers.add(call.name);
            }
            assertEquals(List.of("receiverDraw", "temporalDraw", "alphaDraw"), helpers);
            for (String helper : helpers) assertEquals(0, currentQueries(method(runtime, helper)));
            for (String entry : List.of("drawTemporalForSpatial", "spatialSamplers", "revokeCurrent"))
                assertEquals(1, currentQueries(method(runtime, entry)), entry);
            assertEquals(0,currentQueries(method(runtime,"revoke")),"Cleanup delegates its independent single query");
            for (var m : runtime.methods) for (var instruction : m.instructions)
                if (instruction instanceof MethodInsnNode call)
                    assertFalse(call.name.equals("glUseProgram") || call.name.equals("_glUseProgram"), family);
        }
    }
    @Test void receiverColorContractAndDimensionsReadOneLocalSnapshot() throws Exception {
        for (String family : List.of("Reimagined", "Unbound")) {
            String owner = "com/pp/shaderlod/optimization/"+family.toLowerCase()+"earlyalpha/"+family+"EarlyAlphaRuntime";
            var instructions = code(method(read(owner), "receiverDraw"));
            int captures = 0, snapshot = -1, snapshotChecks = 0, snapshotDimensions = 0;
            for (int i=1; i<instructions.size(); i++) {
                if (!(instructions.get(i) instanceof MethodInsnNode call)) continue;
                if (call.owner.endsWith("ComplementarySamplerPass$Observation") && call.name.equals("color")) {
                    captures++; assertEquals(Opcodes.CHECKCAST,instructions.get(i+1).getOpcode());
                    assertEquals(Opcodes.ASTORE, instructions.get(i+2).getOpcode());
                    snapshot = ((VarInsnNode)instructions.get(i+2)).var;
                }
                if (snapshot < 0) continue;
                if (call.owner.endsWith(family+"EarlyAlphaContract") && call.name.equals("sampler")) {
                    if(instructions.get(i-5) instanceof VarInsnNode load && load.getOpcode()==Opcodes.ALOAD && load.var==snapshot)snapshotChecks++;
                }
                if (call.owner.endsWith("$Sampler") && Set.of("width", "height").contains(call.name)
                        && instructions.get(i-1) instanceof VarInsnNode load && load.var == snapshot) snapshotDimensions++;
            }
            assertEquals(1, captures, family); assertEquals(1, snapshotChecks, family);
            var captureBody=code(method(read(owner),"samplerObservations"));int colorReads=0;
            for(int i=1;i<captureBody.size();i++)if(captureBody.get(i) instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals("sample")
                && captureBody.get(i-1) instanceof LdcInsnNode name && name.cst.equals("shadowcolor0"))colorReads++;
            assertEquals(1,colorReads,"The cache miss also captures color only once");
            assertEquals(2, snapshotDimensions, family);
        }
    }
}
