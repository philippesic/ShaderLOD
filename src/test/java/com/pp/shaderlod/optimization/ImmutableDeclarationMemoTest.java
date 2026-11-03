package com.pp.shaderlod.optimization;
import com.pp.shaderlod.optimization.sourcecache.ImmutableDeclarationMemo;
import com.pp.shaderlod.optimization.sourcecache.RawOptionOverrides;
import java.util.concurrent.atomic.AtomicInteger;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ImmutableDeclarationMemoTest {
 private static StringOption option(String value){return StringOption.create(OptionType.DEFINE,"CONTROL","[0 1]",value);}
 private static OptionLocation location(){return new OptionLocation(AbsolutePackPath.fromAbsolutePath("/test.glsl"),0);}
 private static OptionSet declarations(){var b=OptionSet.builder();b.addStringOption(location(),option("0"));return b.build();}
 @Test void immutableDefaultsCacheButActualOverrideValuesRemainLive(){
  var memo=new ImmutableDeclarationMemo();var calls=new AtomicInteger();var declaration=declarations();assertTrue(RawOptionOverrides.immutableDefaults(declaration));
  var values=new MutableOptionValues(declaration,java.util.Map.of());
  for(int i=0;i<3;i++)assertTrue(memo.check(declaration,()->{calls.incrementAndGet();return true;}));assertEquals(1,calls.get());
  values.getStringValues().put("CONTROL","1");assertEquals("1",values.getStringValueOrDefault("CONTROL"));assertTrue(memo.check(declaration,()->false));
  assertFalse(memo.check(declarations(),()->false));assertTrue(memo.check(declaration,()->false));
 }
 @Test void foreignMutableMergedOptionNeverAcquiresImmutableMemoPermission(){
  var current=new java.util.concurrent.atomic.AtomicReference<>(option("0"));
  var foreign=new MergedStringOption(location(),current.get()){@Override public StringOption getOption(){return current.get();}};
  var builder=OptionSet.builder();builder.addStringOption(foreign);var declaration=builder.build();assertFalse(RawOptionOverrides.immutableDefaults(declaration));
  var memo=new ImmutableDeclarationMemo();assertFalse(memo.check(declaration,()->{throw new AssertionError("Mutable declarations cannot build inventories on rendering callbacks");}));
  current.set(option("1"));assertFalse(memo.check(declaration,()->declaration.getStringOptions().get("CONTROL").getOption().getDefaultValue().equals("0")));
 }
}
