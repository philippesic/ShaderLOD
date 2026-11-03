package com.pp.shaderlod.optimization.blisspixel;
import net.minecraft.client.Minecraft;import net.minecraft.world.level.Level;import net.irisshaders.iris.uniforms.CapturedRenderingState;
public final class BlissPixelEnvironment {
 public record State(Object world,Object camera,boolean overworld,boolean clear,float rain,boolean projection){}
 private BlissPixelEnvironment(){}
 public static State actual(){var c=Minecraft.getInstance();var l=c.level;if(l==null||c.getCameraEntity()==null)return new State(null,null,false,false,-1,false);float a=l.getRainLevel(0),b=l.getRainLevel(1);float[] p=new float[16];CapturedRenderingState.INSTANCE.getGbufferProjection().get(p);return new State(l,c.getCameraEntity(),l.dimension().equals(Level.OVERWORLD),a==0&&b==0,b,BlissPixelContract.projection(p));}
}
