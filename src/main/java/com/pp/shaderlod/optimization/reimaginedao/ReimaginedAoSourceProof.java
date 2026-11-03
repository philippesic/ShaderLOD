package com.pp.shaderlod.optimization.reimaginedao;
import java.nio.file.Path;import java.io.InputStream;import java.util.*;import java.security.*;import java.util.function.*;
import com.pp.shaderlod.packs.PackSelectionService;
/** Real packaged recipe and complete generated archive/state/owned-map proof. No size/mtime cache. */
public final class ReimaginedAoSourceProof {
 public static final String RECIPE_SHA="7eb35919b4c5781ebd2a6bf4dac58b5b7b40b2e9979ec6afa9b7529e371bd158";
 private ReimaginedAoSourceProof(){}
 public static boolean verify(Path p,Supplier<InputStream> recipe){return verify(p,recipe,PackSelectionService::verifyGeneratedRendererGraph);}
 public static boolean verify(Path p,Supplier<InputStream> recipe,BiFunction<Path,Map<String,String>,String> reader){
  if(p==null)return false;try(var in=recipe.get()){if(in==null||!RECIPE_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))))return false;
   return ReimaginedAoContract.ID.equals(reader.apply(p,ReimaginedAoContract.generated("0")));
  }catch(Exception|LinkageError unavailable){return false;}
 }
}
