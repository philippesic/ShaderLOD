package com.pp.shaderlod.optimization.makeupao;
import java.nio.file.Path;import java.io.InputStream;import java.util.*;import java.security.*;import java.util.function.*;
import com.pp.shaderlod.packs.PackSelectionService;
/** Real packaged recipe and complete generated archive/state/owned-map proof. No size/mtime cache. */
public final class MakeUpAoSourceProof {
 public static final String RECIPE_SHA="80260ee772f9231fc2159aab76812e4254ae8f090a213b5ec0214050067cf5e9";
 private MakeUpAoSourceProof(){}
 public static boolean verify(Path p,Supplier<InputStream> recipe){return verify(p,recipe,PackSelectionService::verifyGeneratedRendererGraph);}
 public static boolean verify(Path p,Supplier<InputStream> recipe,BiFunction<Path,Map<String,String>,String> reader){
  if(p==null)return false;try(var in=recipe.get()){if(in==null||!RECIPE_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))))return false;
   return MakeUpAoContract.ID.equals(reader.apply(p,MakeUpAoContract.generated("0")));
  }catch(Exception|LinkageError unavailable){return false;}
 }
}
