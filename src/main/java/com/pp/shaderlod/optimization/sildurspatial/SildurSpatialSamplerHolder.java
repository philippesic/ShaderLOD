package com.pp.shaderlod.optimization.sildurspatial;
import java.util.*;import java.util.function.IntSupplier;import net.irisshaders.iris.gl.sampler.*;import net.irisshaders.iris.gl.texture.TextureType;import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
/** One owned private field needs exact stored texels/frame tags; native samplers delegate unchanged. */
public final class SildurSpatialSamplerHolder implements SamplerHolder {
 private final SamplerHolder target;
 public SildurSpatialSamplerHolder(SamplerHolder target){this.target=Objects.requireNonNull(target);}
 public void addExternalSampler(int unit,String...names){target.addExternalSampler(unit,names);}
 public boolean hasSampler(String name){return target.hasSampler(name);}
 public boolean addDefaultSampler(TextureType t,IntSupplier s,ValueUpdateNotifier n,GlSampler sampler,String...names){return target.addDefaultSampler(t,s,n,sampler,names);}
 private GlSampler sampler(TextureType type,GlSampler nativeSampler,String[] names){return type==TextureType.TEXTURE_2D&&Arrays.equals(names,new String[]{"colortex12"})&&target.hasSampler("colortex12")?GlSampler.NEAREST:nativeSampler;}
 public boolean addDynamicSampler(TextureType t,IntSupplier s,GlSampler sampler,String...names){return target.addDynamicSampler(t,s,sampler(t,sampler,names),names);}
 public boolean addDynamicSampler(TextureType t,IntSupplier s,ValueUpdateNotifier n,GlSampler sampler,String...names){return target.addDynamicSampler(t,s,n,sampler(t,sampler,names),names);}
}
