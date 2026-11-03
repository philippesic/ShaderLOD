package com.pp.shaderlod.optimization.framecopy.exchange;

import java.nio.ByteBuffer;
import java.util.*;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Actual full-clear submissions and storage, independent of optimization eligibility. */
final class CanonicalDefinitionWitness {
    final RenderTargets targets;
    final RenderTarget history;
    final long epoch;
    final int a,b,width,height;
    final Set<Integer> started = new HashSet<>(), returned = new HashSet<>();
    final IdentityHashMap<Object,Set<Integer>> pending = new IdentityHashMap<>();
    boolean defined;
    CanonicalDefinitionWitness(RenderTargets targets,long epoch) {
        this.targets=targets; this.epoch=epoch; history=targets.get(3);
        require(history!=null,"FRESH_RT3_MISSING");
        a=history.getMainTexture();b=history.getAltTexture();width=history.getWidth();height=history.getHeight();
        require(a>0&&b>0&&a!=b&&width>0&&height>0,"FRESH_STORAGE_METADATA");
    }
    boolean contains(Object clear)throws Exception {
        var fbo=(GlFramebuffer)CanonicalExchangeProof.field(clear,"framebuffer");
        return ((Map<?,?>)CanonicalExchangeProof.field(fbo,"attachments")).values().stream().anyMatch(x->x.equals(a)||x.equals(b));
    }
    void start(Object clear,int mask,boolean osx)throws Exception {
        require(!defined&&!pending.containsKey(clear),"CLEAR_SEQUENCE");
        require(!osx&&(mask&GL11.GL_COLOR_BUFFER_BIT)!=0,"CLEAR_NATIVE_ERROR_CONSUMER_OR_MASK");
        clean("CLEAR_PRIOR");storage();
        var fbo=(GlFramebuffer)CanonicalExchangeProof.field(clear,"framebuffer");
        require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==fbo.getId()
            &&GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"CLEAR_FBO");
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        require(Arrays.equals(viewport,new int[]{0,0,width,height}),"CLEAR_VIEWPORT");
        float[] color=new float[4];GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,color);
        require(Arrays.equals(color,new float[]{0,0,0,0}),"CLEAR_HISTORY_ZERO");
        var banks=new HashSet<Integer>();
        for(var e:((Map<?,?>)CanonicalExchangeProof.field(fbo,"attachments")).entrySet()) {
            int slot=(int)e.getKey(),texture=(int)e.getValue();if(texture!=a&&texture!=b)continue;
            require(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+slot,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)==texture,"CLEAR_LIVE_ALIAS");
            boolean routed=false;
            for(int i=0;i<GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);i++)if(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i)==GL30.GL_COLOR_ATTACHMENT0+slot) {
                require(!GL30.glIsEnabledi(GL11.GL_SCISSOR_TEST,i),"CLEAR_SCISSOR");
                ByteBuffer masks=BufferUtils.createByteBuffer(4);GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK,i,masks);
                require(masks.get(0)!=0&&masks.get(1)!=0&&masks.get(2)!=0,"CLEAR_RGB_MASK");routed=true;
            }
            require(routed&&started.add(texture),"CLEAR_BANK_ONCE");banks.add(texture);
        }
        require(!banks.isEmpty(),"CLEAR_BANK_REQUIRED");pending.put(clear,banks);clean("CLEAR_PRE_SUBMISSION");
    }
    void returned(Object clear) {
        var banks=pending.remove(clear);require(banks!=null,"CLEAR_RETURN_WITHOUT_START");
        clean("CLEAR_SUBMISSION");returned.addAll(banks);
    }
    void complete() {
        require(!targets.isFullClearRequired()&&pending.isEmpty()&&returned.equals(Set.of(a,b)),"RETURNED_BOTH_CLEAR_REQUIRED");
        clean("FRESH_PREPARE");storage();GL11.glFinish();clean("FRESH_CLEAR_COMPLETION");defined=true;
    }
    private void storage() {
        int prior=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            for(int texture:new int[]{a,b}) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
                require(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)==width
                    &&GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)==height
                    &&GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT)==GL30.GL_R11F_G11F_B10F,"FRESH_STORAGE_ACTUAL");
            }
        }finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,prior);}
        clean("FRESH_STORAGE");
    }
    static void clean(String site) {
        int error=GL11.glGetError(),reset=GL45.glGetGraphicsResetStatus();
        require(error==GL11.GL_NO_ERROR&&reset==GL11.GL_NO_ERROR,site+"_GL_ERROR_OR_RESET:"+error+":"+reset);
    }
    private static void require(boolean valid,String reason){if(!valid)throw new IllegalStateException(reason);}
}
