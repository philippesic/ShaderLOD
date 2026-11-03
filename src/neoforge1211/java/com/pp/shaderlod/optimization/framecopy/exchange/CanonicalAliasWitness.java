package com.pp.shaderlod.optimization.framecopy.exchange;

import java.nio.*;
import java.security.MessageDigest;
import java.util.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Explicit untimed affected-path evidence only; never invoked by the qualified timing route. */
final class CanonicalAliasWitness {
    record Payload(String sha256,int first,boolean uniform) {}
    record Result(String test,long exchanges,int a,int b,int source,int destination,int sourceFbo,int sourceSlot,int observedReadAttachment,int observedDestination,
        Payload beforeA,Payload beforeB,Payload afterA,Payload afterB,boolean canonicalAliases,boolean groupedUnchanged,boolean samplerCanonical) {}
    final String test;final long exchanges;final int source,destination;final Payload beforeA,beforeB;
    CanonicalAliasWitness(String test,CanonicalExchangeProof p,long exchanges,int source,int destination)throws Exception {
        this.test=test;this.exchanges=exchanges;this.source=source;this.destination=destination;
        CanonicalDefinitionWitness.clean("TEST_DISTINGUISHABLE_PRIOR");
        GL44.glClearTexImage(p.a,0,GL11.GL_RGB,GL11.GL_FLOAT,new float[]{.125f,.125f,.125f});
        GL44.glClearTexImage(p.b,0,GL11.GL_RGB,GL11.GL_FLOAT,new float[]{.75f,.75f,.75f});
        GL11.glFinish();CanonicalDefinitionWitness.clean("TEST_DISTINGUISHABLE_DEFINED");
        beforeA=payload(p,p.a);beforeB=payload(p,p.b);
        require(beforeA.uniform&&beforeB.uniform&&!beforeA.sha256.equals(beforeB.sha256),"DISTINGUISHABLE_FULL_BANKS");
    }
    Result finish(CanonicalExchangeProof p,CanonicalExchangeProof.Alias alias,int actualRead,int actualDestination,boolean sampler)throws Exception {
        p.verifyClearPair();var afterA=payload(p,p.a);var afterB=payload(p,p.b);var expected=source==p.a?beforeA:beforeB;
        require(afterA.sha256.equals(expected.sha256)&&afterB.sha256.equals(expected.sha256),"BOTH_BANK_FULL_PAYLOAD_SEED");
        return new Result(test,exchanges,p.a,p.b,source,destination,alias.framebuffer().getId(),alias.slot(),actualRead,actualDestination,beforeA,beforeB,afterA,afterB,true,true,sampler);
    }
    private static Payload payload(CanonicalExchangeProof p,int texture)throws Exception {
        int prior=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),pack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING),alignment=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int row=GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH),skipRows=GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS),skipPixels=GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        ByteBuffer bytes=MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(p.width,p.height),4));
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,4);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGB,GL30.GL_UNSIGNED_INT_10F_11F_11F_REV,bytes);CanonicalDefinitionWitness.clean("TEST_FULL_PAYLOAD_READ");
            var ints=bytes.order(ByteOrder.nativeOrder()).asIntBuffer();int first=ints.get(0);boolean uniform=true;for(int i=1;i<ints.limit();i++)uniform&=ints.get(i)==first;
            var digest=MessageDigest.getInstance("SHA-256");digest.update(bytes);return new Payload(HexFormat.of().formatHex(digest.digest()),first,uniform);
        }finally {
            MemoryUtil.memFree(bytes);GL11.glBindTexture(GL11.GL_TEXTURE_2D,prior);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pack);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,alignment);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,row);GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,skipRows);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,skipPixels);
        }
    }
    private static void require(boolean valid,String reason){if(!valid)throw new IllegalStateException(reason);}
}
