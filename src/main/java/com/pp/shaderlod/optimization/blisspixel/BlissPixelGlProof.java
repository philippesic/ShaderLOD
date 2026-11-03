package com.pp.shaderlod.optimization.blisspixel;
import org.lwjgl.opengl.*;
public final class BlissPixelGlProof {
 private BlissPixelGlProof(){}
 public static boolean sample(int program,String name,int expected,int width,int height){int l=GL20.glGetUniformLocation(program,name);if(l<0)return false;int unit=GL20.glGetUniformi(program,l);if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return false;int old=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);try{GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int tex=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);int sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);int compare=sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_COMPARE_MODE):GL33.glGetSamplerParameteri(sampler,GL14.GL_TEXTURE_COMPARE_MODE);return tex==expected&&expected>0&&GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)==width&&GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)==height&&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL)==0&&compare==GL11.GL_NONE;}finally{GL13.glActiveTexture(old);}}
}
