package com.pp.shaderlod.optimization.blissspatial;
import java.util.*;import java.util.function.IntSupplier;import net.irisshaders.iris.gl.sampler.*;import net.irisshaders.iris.gl.texture.TextureType;import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
/** One exact spatial consumer's unread default aliases. Every active sampler delegates unchanged. */
public final class BlissSpatialSamplerHolder implements SamplerHolder {
 private final SamplerHolder target;private static long skipped,delegated;
 public BlissSpatialSamplerHolder(SamplerHolder target){this.target=Objects.requireNonNull(target);}
 public static String status(){return "unusedDefaultSkipped="+skipped+"; defaultDelegated="+delegated;}
 public void addExternalSampler(int unit,String...names){target.addExternalSampler(unit,names);}
 public boolean hasSampler(String name){return target.hasSampler(name);}
 public boolean addDefaultSampler(TextureType type,IntSupplier texture,ValueUpdateNotifier notifier,GlSampler sampler,String...names){
  // Exact pinned Iris addRenderTargetSamplers native default group only. Unknown aliases fail closed.
  if(type==TextureType.TEXTURE_2D&&Arrays.equals(names,new String[]{"colortex0","gcolor"})&&!target.hasSampler("colortex0")&&!target.hasSampler("gcolor")){skipped++;return false;}
  delegated++;return target.addDefaultSampler(type,texture,notifier,sampler,names);
 }
 public boolean addDynamicSampler(TextureType t,IntSupplier s,GlSampler sampler,String...names){return target.addDynamicSampler(t,s,sampler,names);}
 public boolean addDynamicSampler(TextureType t,IntSupplier s,ValueUpdateNotifier n,GlSampler sampler,String...names){return target.addDynamicSampler(t,s,n,sampler,names);}
}
