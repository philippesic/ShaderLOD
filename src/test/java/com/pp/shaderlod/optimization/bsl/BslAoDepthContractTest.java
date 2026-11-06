package com.pp.shaderlod.optimization.bsl;

import com.pp.shaderlod.optimization.unbounddepth.UnboundAoDepthContract;
import com.pp.shaderlod.optimization.unbounddepth.UnboundAoDepthContract.Sampler;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BslAoDepthContractTest {
    // Actual scalar values from the Asahi native sampler observation. This is
    // contract coverage, not a substitute for an admitted live draw.
    private static Sampler captured() {
        return new Sampler(74,74,2560,1600,2560,1600,0,0,0x1902,24,0x8C17,
                0,0x2600,0x2600,0,1000,0x812F,0x812F,0,-1000,1000,1);
    }
    private static Sampler changed(Sampler source,String field,Object replacement)throws Exception {
        var components=Sampler.class.getRecordComponents();
        Class<?>[] types=new Class<?>[components.length];Object[] values=new Object[components.length];
        boolean found=false;
        for(int i=0;i<components.length;i++) {
            types[i]=components[i].getType();values[i]=components[i].getAccessor().invoke(source);
            if(components[i].getName().equals(field)){values[i]=replacement;found=true;}
        }
        assertTrue(found,"Unknown sampler field");
        return Sampler.class.getConstructor(types).newInstance(values);
    }
    @Test void capturedUnsizedD24UsesTheSharedRecognition() {
        var sample=captured();
        assertTrue(UnboundAoDepthContract.sampler(sample));
        assertTrue(BslAoDepthContract.sampler(sample));
        assertEquals(0x1902,sample.format());
        assertFalse(BslAoDepthContract.sampler(null));
    }
    @Test void precisionAndTypeAreRequiredRatherThanInferredFromBaseEnum()throws Exception {
        for(int bits:new int[]{0,16,32})assertFalse(BslAoDepthContract.sampler(changed(captured(),"depthBits",bits)));
        for(int type:new int[]{0,0x1406,0x8F9C})assertFalse(BslAoDepthContract.sampler(changed(captured(),"depthType",type)));
        assertFalse(BslAoDepthContract.sampler(changed(captured(),"format",0x1903)));
    }
    @Test void everyOtherSamplerFailureStillRejectsSizedAndUnsizedStorage()throws Exception {
        var failures=List.of(
                new Object[]{"texture",0},new Object[]{"ownedTexture",75},
                new Object[]{"width",0},new Object[]{"height",0},
                new Object[]{"ownerWidth",2559},new Object[]{"ownerHeight",1599},
                new Object[]{"level1Width",1},new Object[]{"level1Height",1},
                new Object[]{"compare",0x884E},new Object[]{"min",0x2703},new Object[]{"mag",0x2601},
                new Object[]{"base",1},new Object[]{"max",-1},
                new Object[]{"wrapS",0x2901},new Object[]{"wrapT",0x2901},
                new Object[]{"bias",1f},new Object[]{"bias",Float.NaN},
                new Object[]{"minLod",1f},new Object[]{"maxLod",-1f},
                new Object[]{"minLod",Float.NaN},new Object[]{"maxLod",Float.POSITIVE_INFINITY},
                new Object[]{"anisotropy",2f});
        for(int format:new int[]{0x1902,0x81A6})for(var failure:failures) {
            var sample=changed(changed(captured(),"format",format),(String)failure[0],failure[1]);
            assertFalse(BslAoDepthContract.sampler(sample),format+":"+failure[0]);
            assertFalse(UnboundAoDepthContract.sampler(sample),format+":"+failure[0]);
        }
    }
    @Test void existingSizedRepresentationsKeepTheirSharedVerdict()throws Exception {
        for(var row:new int[][]{{0x81A5,16,0x8C17},{0x81A6,24,0x8C17},{0x81A7,32,0x8C17},{0x8CAC,32,0x1406}}) {
            var sample=changed(changed(changed(captured(),"format",row[0]),"depthBits",row[1]),"depthType",row[2]);
            assertTrue(UnboundAoDepthContract.sampler(sample));
            assertTrue(BslAoDepthContract.sampler(sample));
        }
    }
}
