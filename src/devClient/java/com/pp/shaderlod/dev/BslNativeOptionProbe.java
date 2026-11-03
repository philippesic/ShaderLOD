package com.pp.shaderlod.dev;
import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.Iris;
/** Explicit native user option edit through the same queue/reload entry as Iris's UI.
 * No product admission or option normalization is performed. Untimed DEV only. */
final class BslNativeOptionProbe {
 static Map<String,Object> taa(boolean next)throws Exception {
  var core=com.pp.shaderlod.optimization.OptimizationRuntimeContext.core();
  var c=net.minecraft.client.Minecraft.getInstance();var pack=Iris.getCurrentPack().orElseThrow();
  if(core==null||core.settings().enabled()||c.level==null||c.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD
      ||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty()
      ||!com.pp.shaderlod.optimization.bslearlyalpha.mixin.BslEarlyAlphaPlugin.exactApi()
      ||!pack.getShaderPackOptions().getOptionSet().getBooleanOptions().containsKey("TAA"))
   throw new IllegalStateException("Ordinary original BSL native boolean edit requires Off/Overworld/empty queue/pinned API");
  String selected=Iris.getCurrentPackName();var dir=Iris.getShaderpacksDirectory();
  if(!selected.equals(Path.of(selected).getFileName().toString())||Files.isSymbolicLink(dir.resolve(selected))
      ||!com.pp.shaderlod.integration.bslquad.BslQuadEligibility.ARCHIVE_SHA.equals(
          com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(dir.resolve(selected))))
   throw new IllegalStateException("Exact original BSL archive required");
  var before=com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(pack.getShaderPackOptions());
  var expected=new TreeMap<>(com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions());
  String prior=before.get("TAA"),requested=Boolean.toString(next);expected.put("TAA",prior);
  if(!Set.of("true","false").contains(prior)||!before.equals(expected))throw new IllegalStateException("Only explicit TAA delta from native384 allowed");
  var queue=Iris.getShaderPackOptionQueue();queue.put("TAA",requested);
  try {
   Iris.reload();expected.put("TAA",requested);
   var after=com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(Iris.getCurrentPack().orElseThrow().getShaderPackOptions());
   if(Iris.isFallback()||!selected.equals(Iris.getCurrentPackName())||!queue.isEmpty()||!after.equals(expected))
    throw new IllegalStateException("Native TAA option edit did not load exactly");
   return Map.of("previous_TAA",prior,"requested_TAA",requested,"effective_TAA",after.get("TAA"),"effective_option_count",after.size(),"ordinary_Iris_queue_reload",true,"original_selected",selected,"queue_empty",true);
  }catch(Exception failure){
   queue.remove("TAA",requested);
   if(queue.isEmpty())try{queue.put("TAA",prior);Iris.reload();}catch(Exception recovery){failure.addSuppressed(recovery);}
   throw failure;
  }
 }
}
