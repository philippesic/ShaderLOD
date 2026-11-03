package com.pp.shaderlod.optimization.blisspixel;
import java.util.*;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
/** Every typed read follows actual declaration and exact generated family/source proof. */
public final class BlissPixelOptionGuard {
 private BlissPixelOptionGuard(){}
 public static boolean declaration(OptionSet options) {
  // A generated family must declare its own string control. Reject native and
  // other-family graphs before reading their complete option inventory.
  if(options==null||options.getBooleanOptions().containsKey(BlissPixelContract.OPTION)
      ||!options.getStringOptions().containsKey(BlissPixelContract.OPTION))return false;
  var expected=new TreeMap<>(BlissPixelContract.generated("0"));expected.put("SHADERLOD_EFFECT_ADAPTER","0");var defaults=new TreeMap<String,String>();
  options.getBooleanOptions().forEach((k,v)->defaults.put(k,Boolean.toString(v.getOption().getDefaultValue())));
  options.getStringOptions().forEach((k,v)->defaults.put(k,v.getOption().getDefaultValue()));
  if(!expected.keySet().equals(defaults.keySet()))return false;
  // An observed selected native profile is not the declaration default: the
  // native CLOUDSHADOWSONLY wrapper marker differs in broad/default inventories.
  // Native effective values are checked separately against the complete profile.
  for(var e:expected.entrySet()){
   if(Set.of("true","false").contains(e.getValue())){if(!options.getBooleanOptions().containsKey(e.getKey()))return false;}
   else{var v=options.getStringOptions().get(e.getKey());if(v==null||!v.getOption().getAllowedValues().contains(e.getValue()))return false;}
   if(e.getKey().startsWith("SHADERLOD_")&&!e.getValue().equals(defaults.get(e.getKey())))return false;
  }
  for(String k:List.of(BlissPixelContract.OPTION)) {
   if(options.getBooleanOptions().containsKey(k))return false;var entry=options.getStringOptions().get(k);
   if(entry==null||!Set.copyOf(entry.getOption().getAllowedValues()).equals(Set.of("0","1")))return false;
  }
  return true;
 }
 /** Cheap classification only: exact470 family declaration/default/type contract, never source admission. */
 public static Optional<String> declaredGraph(OptionSet options,OptionValues values){if(values==null||!declaration(options))return Optional.empty();return Optional.of(values.getStringValueOrDefault(BlissPixelContract.OPTION));}
 public static Map<String,String> snapshot(OptionSet options,OptionValues values,boolean exactBlissSource) {
  if(!exactBlissSource||values==null||!declaration(options))return Map.of();return com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(options,values);}
}
