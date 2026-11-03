package com.pp.shaderlod.optimization.astraao;
import java.util.*;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
/** Every typed read follows actual declaration and exact generated family/source proof. */
public final class AstraAoOptionGuard {
 private AstraAoOptionGuard(){}
 private static final com.pp.shaderlod.optimization.sourcecache.ImmutableDeclarationMemo DECLARATIONS=new com.pp.shaderlod.optimization.sourcecache.ImmutableDeclarationMemo();
 public static boolean declaration(OptionSet options){
  if(options==null||options.getBooleanOptions().containsKey(AstraAoContract.OPTION)||!options.getStringOptions().containsKey(AstraAoContract.OPTION))return false;
  return DECLARATIONS.check(options,()->declarationUncached(options));
 }
 private static boolean declarationUncached(OptionSet options) {
  // A generated family must declare its own string control. Reject native and
  // other-family graphs before reading their complete option inventory.
  if(options==null||options.getBooleanOptions().containsKey(AstraAoContract.OPTION)
      ||!options.getStringOptions().containsKey(AstraAoContract.OPTION))return false;
  var expected=new TreeMap<>(AstraAoContract.generated("0"));expected.put("SHADERLOD_REFINEMENT_ADAPTER","0");var defaults=new TreeMap<String,String>();
  options.getBooleanOptions().forEach((k,v)->defaults.put(k,Boolean.toString(v.getOption().getDefaultValue())));
  options.getStringOptions().forEach((k,v)->defaults.put(k,v.getOption().getDefaultValue()));
  if(!expected.equals(defaults))return false;
  for(String k:List.of(AstraAoContract.OPTION,"SHADERLOD_ASTRALEX_DYNAMIC_GAUSSIAN","SHADERLOD_ASTRALEX_WORLD_GRAPH")) {
   if(options.getBooleanOptions().containsKey(k))return false;var entry=options.getStringOptions().get(k);
   if(entry==null||!Set.copyOf(entry.getOption().getAllowedValues()).equals(Set.of("0","1")))return false;
  }
  return true;
 }
 /** Cheap classification only: exact1027 family declaration/default/type contract, never source admission. */
 public static Optional<String> declaredHalf(OptionSet options,OptionValues values){if(values==null||!declaration(options))return Optional.empty();return Optional.of(values.getStringValueOrDefault(AstraAoContract.OPTION));}
 public static Map<String,String> snapshot(OptionSet options,OptionValues values,boolean exactAstraSource) {
  if(!exactAstraSource||values==null||!declaration(options))return Map.of();return com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(options,values);}
}
