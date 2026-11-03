package com.pp.shaderlod.tuning;

import com.pp.shaderlod.optimization.makeupspatial.MakeUpSpatialContract;
import com.pp.shaderlod.optimization.makeupspatial.MakeUpSpatialFallback;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual maintained MakeUp110 maps; receipt ownership tests do not assert GL/replay completion. */
class MakeUpSpatialFallbackTest {
    private final Object oldPack=new Object(),newPack=new Object(),oldPipeline=new Object(),newPipeline=new Object(),world=new Object();
    private final String dimension="minecraft:overworld",selected="MakeUp-generated.zip";
    private Map<String,String> options(int mode) {
        var m=new TreeMap<>(MakeUpSpatialContract.generated("0"));
        m.put(MakeUpSpatialContract.KEY,mode==1?"1":"0");m.put(MakeUpSpatialContract.RATE,mode==2?"1":"0");m.put(MakeUpSpatialContract.WORLD,mode==3?"1":"0");return Map.copyOf(m);
    }
    private MakeUpSpatialFallback.Receipt receipt(Map<String,String> before,Map<String,String> after) {
        return new MakeUpSpatialFallback.Receipt(oldPack,newPack,oldPipeline,newPipeline,world,dimension,29,selected,selected,before,after,true,"actual refused producer clause");
    }
    private boolean matches(MakeUpSpatialFallback.Receipt receipt,Map<String,String> before,Map<String,String> after) {
        return receipt.matches(MakeUpSpatialContract.ID,oldPack,newPack,oldPipeline,newPipeline,world,dimension,29,selected,before,after);
    }
    @Test void exactOneHotReuseRateAndWorldNativeRecoveryIsOneUse() {
        for(int mode:List.of(1,2,3)) {
            var before=options(mode);var after=options(0);assertEquals(114,before.size());assertTrue(MakeUpSpatialContract.profile(before));assertTrue(MakeUpSpatialContract.profile(after));
            assertTrue(matches(receipt(before,after),before,after));var slot=new MakeUpSpatialFallback();slot.publish(receipt(before,after));
            assertTrue(matches(slot.take(),before,after));assertNull(slot.take());slot.publish(receipt(before,after));slot.clear();assertNull(slot.take());
        }
    }
    @Test void noOtherNativePrivateOptionOrContextChangeCanAuthorizeTheTransition() {
        var before=options(2);var after=options(0);var receipt=receipt(before,after);
        for(String key:List.of("AA_TYPE","COLORED_SHADOW","SHADERLOD_MAKEUP_AO_HALF_RES")) {
            var changed=new TreeMap<>(after);changed.put(key,"changed");assertFalse(matches(receipt(before,changed),before,changed),key);
        }
        var missing=new TreeMap<>(after);missing.remove("AA_TYPE");assertFalse(matches(receipt(before,missing),before,missing));
        assertFalse(receipt.matches("solas-shader",oldPack,newPack,oldPipeline,newPipeline,world,dimension,29,selected,before,after));
        assertFalse(receipt.matches(MakeUpSpatialContract.ID,new Object(),newPack,oldPipeline,newPipeline,world,dimension,29,selected,before,after));
        assertFalse(receipt.matches(MakeUpSpatialContract.ID,oldPack,new Object(),oldPipeline,newPipeline,world,dimension,29,selected,before,after));
        assertFalse(receipt.matches(MakeUpSpatialContract.ID,oldPack,newPack,new Object(),newPipeline,world,dimension,29,selected,before,after));
        assertFalse(receipt.matches(MakeUpSpatialContract.ID,oldPack,newPack,oldPipeline,newPipeline,new Object(),dimension,29,selected,before,after));
        assertFalse(receipt.matches(MakeUpSpatialContract.ID,oldPack,newPack,oldPipeline,newPipeline,world,"minecraft:the_nether",29,selected,before,after));
        assertFalse(receipt.matches(MakeUpSpatialContract.ID,oldPack,newPack,oldPipeline,newPipeline,world,dimension,30,selected,before,after));
        assertFalse(receipt.matches(MakeUpSpatialContract.ID,oldPack,newPack,oldPipeline,newPipeline,world,dimension,29,"Other.zip",before,after));
        var queued=new MakeUpSpatialFallback.Receipt(oldPack,newPack,oldPipeline,newPipeline,world,dimension,29,selected,selected,before,after,false,"refused");assertFalse(matches(queued,before,after));
        assertFalse(matches(receipt,before,before));
    }
    @Test void activeRequestMustMatchTheActualOneHotLoadedGraph() {
        for(int mode:List.of(1,2,3)) {
            boolean reuse=mode==1,rate=mode==2,rgb=mode==3;
            assertTrue(MakeUpSpatialFallback.loadedGraphMatches(MakeUpSpatialContract.ID,true,reuse,rate,rgb,options(mode)));
            assertFalse(MakeUpSpatialFallback.loadedGraphMatches(MakeUpSpatialContract.ID,true,reuse,rate,rgb,options(0)));
            assertFalse(MakeUpSpatialFallback.loadedGraphMatches(MakeUpSpatialContract.ID,true,reuse,rate,rgb,Map.of()));
        }
        assertFalse(MakeUpSpatialFallback.loadedGraphMatches(MakeUpSpatialContract.ID,true,true,true,false,options(1)));
        assertTrue(MakeUpSpatialFallback.loadedGraphMatches(MakeUpSpatialContract.ID,false,true,false,false,Map.of()));
        assertTrue(MakeUpSpatialFallback.loadedGraphMatches("photon",true,true,false,false,Map.of()));
    }
    @Test void actualResourceSelectionPinCannotFollowAnExternalEdit() {
        var repositorySelection=new ArrayList<>(List.of("vanilla","mod_resources"));
        var captured=AutoTuner.captureSelectedResources(repositorySelection);
        assertDoesNotThrow(()->AutoTuner.requireResourceSelection(captured,repositorySelection));
        assertThrows(UnsupportedOperationException.class,()->captured.add("file/PBR.zip"));
        repositorySelection.add("file/PBR.zip");
        assertEquals(List.of("vanilla","mod_resources"),captured,"Live repository mutation cannot rebase the captured pin");
        assertThrows(IllegalStateException.class,()->AutoTuner.requireResourceSelection(captured,repositorySelection));
        assertThrows(IllegalStateException.class,()->AutoTuner.requireResourceSelection(captured,List.of("mod_resources","vanilla")));
        assertThrows(IllegalStateException.class,()->AutoTuner.requireResourceSelection(captured,List.of("vanilla")));
        assertThrows(IllegalStateException.class,()->AutoTuner.requireResourceSelection(null,captured));
    }
    private static List<String> instructions(String resource,String method)throws Exception {
        var result=new ArrayList<String>();
        try(var input=MakeUpSpatialFallbackTest.class.getClassLoader().getResourceAsStream(resource+".class")) {
            assertNotNull(input,resource);
            new org.objectweb.asm.ClassReader(input.readAllBytes()).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9){
                @Override public org.objectweb.asm.MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                    if(!name.equals(method))return null;
                    return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9){
                        @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf){result.add(owner+"."+name);}
                        @Override public void visitTypeInsn(int opcode,String type){if(opcode==org.objectweb.asm.Opcodes.NEW)result.add("NEW "+type);}
                        @Override public void visitInsn(int opcode){if(opcode==org.objectweb.asm.Opcodes.ATHROW)result.add("ATHROW");}
                    };
                }
            },org.objectweb.asm.ClassReader.SKIP_DEBUG|org.objectweb.asm.ClassReader.SKIP_FRAMES);
        }
        return result;
    }
    @Test void compiledApplyMismatchRemainsFatalAndOwnedReceiptUsesCurrentNativeAndResourceFacts()throws Exception {
        String job="com/pp/shaderlod/tuning/AutoTuner$Job",runtime="com/pp/shaderlod/optimization/makeupspatial/MakeUpSpatialRuntime";
        String resourceCheck=job+".requireSelectedResources",nativeCheck=runtime+".nativeEnvironment";
        var apply=instructions(job,"apply");int match=apply.indexOf("com/pp/shaderlod/optimization/makeupspatial/MakeUpSpatialFallback.loadedGraphMatches");
        assertTrue(match>=0);int throwsAt=apply.subList(match,apply.size()).indexOf("ATHROW");assertTrue(throwsAt>=0);
        var unmatched=apply.subList(match,match+throwsAt+1);
        assertTrue(unmatched.contains("NEW java/lang/IllegalStateException"));
        assertFalse(unmatched.contains(job+".apply"),"Unmatched synchronous graph change cannot become baseline-recovered UNAVAILABLE");
        assertFalse(unmatched.stream().anyMatch(call->call.contains("CandidateApplyFailure")));
        assertTrue(apply.indexOf(resourceCheck)<match);assertTrue(Collections.frequency(apply,resourceCheck)>=2,"Check both sides of the synchronous Apply");
        assertTrue(instructions(job,"validateLive").contains(resourceCheck));
        assertTrue(instructions(job,"start").contains("com/pp/shaderlod/tuning/AutoTuner.captureSelectedResources"));
        var owned=instructions(job,"rejectOwnedMakeUpSpatialFallback");int receiptMatch=owned.indexOf("com/pp/shaderlod/optimization/makeupspatial/MakeUpSpatialFallback$Receipt.matches");
        assertTrue(receiptMatch>=0);assertTrue(owned.indexOf(resourceCheck)>=0&&owned.indexOf(resourceCheck)<receiptMatch);
        assertTrue(owned.indexOf(nativeCheck)>=0&&owned.indexOf(nativeCheck)<receiptMatch);
        assertFalse(owned.contains(runtime+".setup"),"Generation-blocked setup is not the current native environment fact");
        assertEquals(List.of(runtime+".environment"),instructions(runtime,"nativeEnvironment"));
        var environment=instructions(runtime,"environment");
        assertTrue(environment.contains("com/pp/shaderlod/integration/makeupquad/MakeUpQuadRuntime.resourcesMatch"));
        assertTrue(environment.contains("com/pp/shaderlod/integration/makeupquad/MakeUpQuadRuntime.stackMatches"));
    }
}
