package com.pp.shaderlod.optimization.makeupspatial;

import com.pp.shaderlod.optimization.unboundprime.DepthPrimingPass;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual compiled native/caller boundaries plus the existing submission scope; no GL completion is fabricated. */
class MakeUpSpatialEmptyInputPassTest {
    private static MethodNode method(String owner,String name)throws Exception {
        try(var input=MakeUpSpatialEmptyInputPassTest.class.getClassLoader().getResourceAsStream(owner+".class")) {
            assertNotNull(input,owner);var node=new ClassNode();new ClassReader(input.readAllBytes()).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            return node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();
        }
    }
    private static int call(MethodNode method,String owner,String name) {
        for(int i=0;i<method.instructions.size();i++)if(method.instructions.get(i) instanceof MethodInsnNode m&&m.owner.equals(owner)&&m.name.equals(name))return i;
        return -1;
    }
    @Test void actualSodiumCallerCanSetUpItsProgramBeforeFindingZeroBatches()throws Exception {
        var render=method("net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer","render");
        int setup=call(render,"net/caffeinemc/mods/sodium/client/render/chunk/shader/ChunkShaderInterface","setModelViewMatrix");
        int iterate=call(render,"net/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderListIterable","iterator");
        int empty=call(render,"net/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch","isEmpty");
        int nativeDraw=call(render,"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer","executeDrawBatch");
        assertTrue(setup>=0&&iterate>setup&&empty>iterate&&nativeDraw>empty,
            "The actual injection point precedes native iterator/storage/batch emptiness decisions");
    }
    @Test void compiledMakeUpCallerRequiresActualInputAcknowledgmentBeforeRateAndEmptyReturnCannotDisable()throws Exception {
        String session="com/pp/shaderlod/optimization/makeupspatial/MakeUpSpatialRuntime$Session";
        var prime=method("com/pp/shaderlod/optimization/makeupspatial/mixin/SpatialChunkRendererMixin$1","prime");
        int returned=call(prime,session,"returned"),rate=call(prime,session,"renderRate");assertTrue(returned>=0&&rate>returned);
        assertEquals("(I)Z",((MethodInsnNode)prime.instructions.get(returned)).desc);
        var next=prime.instructions.get(returned).getNext();while(next.getOpcode()<0)next=next.getNext();
        assertInstanceOf(JumpInsnNode.class,next);var gate=(JumpInsnNode)next;assertEquals(Opcodes.IFEQ,gate.getOpcode());
        assertTrue(prime.instructions.indexOf(gate.label)>rate,"False input acknowledgment must bypass rate program retrieval/draw");
        var acknowledge=method(session,"returned");boolean cleared=false,emptyReturned=false;
        for(var instruction:acknowledge.instructions) {
            if(instruction instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.name.equals("returnedValid"))cleared=true;
            if(instruction.getOpcode()==Opcodes.IRETURN){assertEquals(Opcodes.ICONST_0,instruction.getPrevious().getOpcode());emptyReturned=true;break;}
            if(instruction instanceof MethodInsnNode call)
                fail("Empty input branch must return before context, GL, refusal or completion calls: "+call.owner+"."+call.name);
            if(instruction instanceof FieldInsnNode field&&(field.getOpcode()==Opcodes.PUTSTATIC||field.getOpcode()==Opcodes.PUTFIELD))
                assertTrue(Set.of("returnedValid","failure").contains(field.name),"No blocked/pending/counter mutation on empty input: "+field.name);
        }
        assertTrue(cleared&&emptyReturned);
    }
    @Test void existingNativeSubmissionScopeRestoresAnEmptyPassAndAllowsTheNextPositivePass() {
        // This exercises the production finally/scope API. It does not stand in for Session's actual GL validation.
        final class Scope implements DepthPrimingPass.Work {
            int requested,submitted,restored,verified,disabled;boolean sawSubmission;
            public boolean prepare(){return true;}
            public void prime(DepthPrimingPass.Submission scope){for(int i=0;i<requested;i++)scope.draw(()->submitted++);sawSubmission=scope.submitted();}
            public void restore(){restored++;}
            public void verify(){verified++;}
            public void disable(){disabled++;}
        }
        var scope=new Scope();assertTrue(DepthPrimingPass.run(scope));assertFalse(scope.sawSubmission);
        assertEquals(0,scope.submitted);assertEquals(1,scope.restored);assertEquals(1,scope.verified);assertEquals(0,scope.disabled);
        scope.requested=1;assertTrue(DepthPrimingPass.run(scope));assertTrue(scope.sawSubmission);
        assertEquals(1,scope.submitted);assertEquals(2,scope.restored);assertEquals(2,scope.verified);assertEquals(0,scope.disabled);
    }
}
