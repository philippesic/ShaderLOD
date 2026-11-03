package com.pp.shaderlod.optimization.makeupspatial;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Iris backup/caller contract plus pure polygon commands; no GL context or successful draw is invented. */
class MakeUpSpatialPolygonRestoreTest {
    private static final int POINT=0x1b00,LINE=0x1b01,FILL=0x1b02,FRONT=0x404,BACK=0x405,BOTH=0x408;
    @Test void coreProfileOnlyRestoresTheOneActualModeUsingFrontAndBack() {
        for(int mode:new int[]{POINT,LINE,FILL}) {
            assertArrayEquals(new int[]{BOTH,mode},MakeUpSpatialRuntime.polygonRestorePlan(true,mode,0),"No core back-face state is inferred from the second slot");
            assertArrayEquals(new int[]{BOTH,mode},MakeUpSpatialRuntime.polygonRestorePlan(true,mode,mode==LINE?FILL:LINE));
        }
    }
    @Test void compatibilityProfileRetainsBothNativeFacesAndEqualModesUseOneCommand() {
        assertArrayEquals(new int[]{FRONT,LINE,BACK,FILL},MakeUpSpatialRuntime.polygonRestorePlan(false,LINE,FILL));
        assertArrayEquals(new int[]{FRONT,POINT,BACK,LINE},MakeUpSpatialRuntime.polygonRestorePlan(false,POINT,LINE));
        assertArrayEquals(new int[]{BOTH,FILL},MakeUpSpatialRuntime.polygonRestorePlan(false,FILL,FILL));
    }
    @Test void invalidActualModeFailsBeforeChangingRenderState() {
        assertThrows(IllegalStateException.class,()->MakeUpSpatialRuntime.polygonRestorePlan(true,0,FILL));
        assertThrows(IllegalStateException.class,()->MakeUpSpatialRuntime.polygonRestorePlan(false,FILL,0));
    }
    @Test void onlyAnEnteredUnreturnedQuadNeedsThePendingIrisBackupConsumed() {
        assertFalse(MakeUpSpatialRuntime.polygonBackupPending(false,false),"No owned override was entered");
        assertTrue(MakeUpSpatialRuntime.polygonBackupPending(true,false),"Exceptional quad leaves its owned backup pending");
        assertFalse(MakeUpSpatialRuntime.polygonBackupPending(true,true),"Successful Iris quad already consumed its backup");
    }
    private static MethodNode method(String owner,String name)throws Exception {
        try(var input=MakeUpSpatialPolygonRestoreTest.class.getClassLoader().getResourceAsStream(owner+".class")) {
            assertNotNull(input,owner);var type=new ClassNode();new ClassReader(input.readAllBytes()).accept(type,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            return type.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();
        }
    }
    private static List<String> calls(MethodNode method){
        var calls=new ArrayList<String>();for(var node:method.instructions)if(node instanceof MethodInsnNode call)calls.add(call.owner+"."+call.name);return calls;
    }
    @Test void actualIrisBalancesNormalQuadAndMakeUpOnlyCleansUpAnUnreturnedOverride()throws Exception {
        String iris="net/irisshaders/iris/gl/IrisRenderSystem",quad="net/irisshaders/iris/pathways/FullScreenQuadRenderer";
        var nativeQuad=calls(method(quad,"renderQuad"));
        assertTrue(nativeQuad.indexOf(iris+".overridePolygonMode")>=0);
        int draw=nativeQuad.indexOf("com/mojang/blaze3d/vertex/VertexBuffer.draw");assertTrue(draw>=0);
        assertTrue(nativeQuad.indexOf(iris+".restorePolygonMode")>draw);
        var setter=method(iris,"setPolygonMode");boolean nativeBoth=false;
        for(var node:setter.instructions)if(node instanceof MethodInsnNode call&&call.name.equals("glPolygonMode")) {
            var mode=node.getPrevious();while(mode.getOpcode()<0)mode=mode.getPrevious();
            var face=mode.getPrevious();while(face.getOpcode()<0)face=face.getPrevious();
            assertInstanceOf(IntInsnNode.class,face);assertEquals(BOTH,((IntInsnNode)face).operand);nativeBoth=true;
        }
        assertTrue(nativeBoth,"Actual Iris setter is already core-safe");
        var restore=method(iris,"restorePolygonMode");assertTrue(calls(restore).contains(iris+".setPolygonMode"));
        assertTrue(Arrays.stream(restore.instructions.toArray()).anyMatch(n->n instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTSTATIC&&f.name.equals("backupPolygonMode")),"Native restore consumes its backup");
        String runtime="com/pp/shaderlod/optimization/makeupspatial/MakeUpSpatialRuntime";
        var rate=method(runtime+"$Session","renderRate");var rateCalls=calls(rate);
        assertTrue(rateCalls.contains(runtime+".polygonRestorePlan"));assertTrue(rateCalls.contains(quad+".end"));
        assertTrue(rateCalls.contains("org/lwjgl/opengl/GL30.glBindVertexArray"));assertTrue(rateCalls.contains("org/lwjgl/opengl/GL15.glBindBuffer"));
        assertTrue(rateCalls.contains("org/lwjgl/opengl/GL11.glViewport"));
        int cleanupCalls=0;
        for(var node:rate.instructions)if(node instanceof MethodInsnNode call&&call.owner.equals(runtime)&&call.name.equals("polygonBackupPending")) {
            var next=node.getNext();while(next.getOpcode()<0)next=next.getNext();
            assertInstanceOf(JumpInsnNode.class,next);var gate=(JumpInsnNode)next;assertEquals(Opcodes.IFEQ,gate.getOpcode());
            boolean restoreInside=false;for(var cursor=next.getNext();cursor!=gate.label;cursor=cursor.getNext()) {
                assertNotNull(cursor);if(cursor instanceof MethodInsnNode invoke&&invoke.owner.equals(iris)&&invoke.name.equals("restorePolygonMode"))restoreInside=true;
            }
            assertTrue(restoreInside);cleanupCalls++;
        }
        assertTrue(cleanupCalls>0);
        assertEquals(cleanupCalls,rateCalls.stream().filter(c->c.equals(iris+".restorePolygonMode")).count(),"No unconditional duplicate Iris restore");
    }
}
