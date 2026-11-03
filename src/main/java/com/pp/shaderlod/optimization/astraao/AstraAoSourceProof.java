package com.pp.shaderlod.optimization.astraao;
import java.nio.file.Path;import java.io.InputStream;import java.util.*;import java.security.*;import java.util.function.*;
import com.pp.shaderlod.packs.PackSelectionService;
/** Real packaged recipe and complete generated archive/state/owned-map proof. No size/mtime cache. */
public final class AstraAoSourceProof {
 public static final String RECIPE_SHA="7965fc2930f80491528a91aca3307b342dc333064ee47c382cf796e7f69a2be6";
 private AstraAoSourceProof(){}
 public static boolean verify(Path p,Map<String,String> actual,Supplier<InputStream> recipe){return verify(p,actual,recipe,PackSelectionService::verifyGeneratedRendererGraph);}
 public static boolean verify(Path p,Map<String,String> actual,Supplier<InputStream> recipe,BiFunction<Path,Map<String,String>,String> reader){
  if(p==null)return false;try(var in=recipe.get()){if(in==null||!RECIPE_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))))return false;
   return AstraAoContract.ID.equals(reader.apply(p,actual));
  }catch(Exception|LinkageError unavailable){return false;}
 }
}
