package com.pp.shaderlod.optimization.sourcecache;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import net.irisshaders.iris.shaderpack.option.OptionSet;
/** Immutable declaration/default/domain checks; unsupported mutable declarations stay refused. */
public final class ImmutableDeclarationMemo {
 private final Map<OptionSet,Boolean> accepted=new WeakHashMap<>();
 public synchronized boolean check(OptionSet options,BooleanSupplier validation){
  Boolean prior=accepted.get(options);if(prior!=null)return prior;
  boolean immutable=RawOptionOverrides.immutableDefaults(options);
  if(!immutable){accepted.put(options,false);return false;}
  boolean result=validation.getAsBoolean();accepted.put(options,result);return result;
 }
}
