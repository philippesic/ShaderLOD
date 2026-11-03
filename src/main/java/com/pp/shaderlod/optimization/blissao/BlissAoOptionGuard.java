package com.pp.shaderlod.optimization.blissao;
import java.util.*;import net.irisshaders.iris.shaderpack.option.OptionSet;import net.irisshaders.iris.shaderpack.option.values.OptionValues;
/** Full actual declared profile before any typed read; unknown maps retain native AO. */
public final class BlissAoOptionGuard {
 private BlissAoOptionGuard(){}
 private static final com.pp.shaderlod.optimization.sourcecache.ImmutableDeclarationMemo DECLARATIONS=new com.pp.shaderlod.optimization.sourcecache.ImmutableDeclarationMemo();
 public static boolean declaration(OptionSet options){
  if(options==null||options.getBooleanOptions().containsKey(BlissAoContract.OPTION)||!options.getStringOptions().containsKey(BlissAoContract.OPTION))return false;
  return DECLARATIONS.check(options,()->declarationUncached(options));
 }
 private static boolean declarationUncached(OptionSet options){if(options==null||options.getBooleanOptions().containsKey(BlissAoContract.OPTION)||!options.getStringOptions().containsKey(BlissAoContract.OPTION))return false;if(!com.pp.shaderlod.optimization.blisspixel.BlissPixelOptionGuard.declaration(options))return false;var entry=options.getStringOptions().get(BlissAoContract.OPTION);return entry!=null&&!options.getBooleanOptions().containsKey(BlissAoContract.OPTION)&&Set.copyOf(entry.getOption().getAllowedValues()).equals(Set.of("0","1"));}
 public static Optional<String> declaredHalf(OptionSet options,OptionValues values){return values!=null&&declaration(options)?Optional.of(values.getStringValueOrDefault(BlissAoContract.OPTION)):Optional.empty();}
 public static Map<String,String> snapshot(OptionSet options,OptionValues values){if(values==null||!declaration(options))return Map.of();return com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(options,values);}
}
