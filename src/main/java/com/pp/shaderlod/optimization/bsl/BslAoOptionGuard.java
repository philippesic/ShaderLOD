package com.pp.shaderlod.optimization.bsl;
import java.util.*;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
/** Typed getters are legal only after exact-family source and actual declaration checks. */
public final class BslAoOptionGuard {
 public static final String HALF="SHADERLOD_BSL_AO_HALF_RES";
 private BslAoOptionGuard() {}
 public static boolean declaration(OptionSet options) {
  if(options==null||options.getBooleanOptions().containsKey(HALF))return false;
  var entry=options.getStringOptions().get(HALF);
  return entry!=null&&entry.getOption().getDefaultValue().equals("0")&&Set.copyOf(entry.getOption().getAllowedValues()).equals(Set.of("0","1"));
 }
 public static Optional<String> half(OptionSet options,OptionValues values,boolean qualifiedBslSource) {
  if(!qualifiedBslSource||values==null||!declaration(options))return Optional.empty();
  return Optional.of(values.getStringValueOrDefault(HALF));
 }
 public static boolean nativeProfile(OptionSet options,OptionValues values,boolean qualifiedBslSource) {
  if(!half(options,values,qualifiedBslSource).orElse("0").equals("1"))return false;
  if(!options.getBooleanOptions().containsKey("AO"))return false;
  var method=options.getStringOptions().get("AO_METHOD");if(method==null||!Set.copyOf(method.getOption().getAllowedValues()).equals(Set.of("0","1")))return false;
  return values.getBooleanValueOrDefault("AO")&&Set.of("0","1").contains(values.getStringValueOrDefault("AO_METHOD"));
 }
}
