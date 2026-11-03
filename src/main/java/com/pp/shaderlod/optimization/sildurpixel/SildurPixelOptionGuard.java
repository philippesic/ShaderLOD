package com.pp.shaderlod.optimization.sildurpixel;
import java.util.*;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
/** Every typed read follows actual declaration and exact generated family/source proof. */
public final class SildurPixelOptionGuard {
 private SildurPixelOptionGuard(){}
 public static boolean declaration(OptionSet options) {
  // A generated family must declare its own string control. Reject native and
  // other-family graphs before reading their complete option inventory.
  if(options==null||options.getBooleanOptions().containsKey(SildurPixelContract.OPTION)
      ||!options.getStringOptions().containsKey(SildurPixelContract.OPTION))return false;
  var expected=new TreeMap<>(SildurPixelContract.generated("0"));expected.put("SHADERLOD_EFFECT_ADAPTER","0");var defaults=new TreeMap<String,String>();
  options.getBooleanOptions().forEach((k,v)->defaults.put(k,Boolean.toString(v.getOption().getDefaultValue())));
  options.getStringOptions().forEach((k,v)->defaults.put(k,v.getOption().getDefaultValue()));
  if(!expected.equals(defaults))return false;
  for(String k:List.of(SildurPixelContract.OPTION)) {
   if(options.getBooleanOptions().containsKey(k))return false;var entry=options.getStringOptions().get(k);
   if(entry==null||!Set.copyOf(entry.getOption().getAllowedValues()).equals(Set.of("0","1")))return false;
  }
  return true;
 }
 /** Cheap classification only: exact436 family declaration/default/type contract, never source admission. */
 public static Optional<String> declaredGraph(OptionSet options,OptionValues values){if(values==null||!declaration(options))return Optional.empty();return Optional.of(values.getStringValueOrDefault(SildurPixelContract.OPTION));}
 public static Map<String,String> snapshot(OptionSet options,OptionValues values,boolean exactSildurSource) {
  if(!exactSildurSource||values==null||!declaration(options))return Map.of();return com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(options,values);}
}
