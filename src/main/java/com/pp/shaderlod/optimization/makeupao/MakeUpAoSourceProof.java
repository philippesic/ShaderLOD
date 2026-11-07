package com.pp.shaderlod.optimization.makeupao;
import java.nio.file.Path;import java.io.InputStream;import java.util.*;import java.security.*;import java.util.function.*;
import com.pp.shaderlod.packs.PackSelectionService;
/** Real packaged recipe and complete generated archive/state/owned-map proof. No size/mtime cache. */
public final class MakeUpAoSourceProof {
 public static final String RECIPE_SHA="ef6c557d5fb0f1fb1fc6b59bcd2e5c1792df28f2a900f9284aee63013621f074";
 private MakeUpAoSourceProof(){}
 public static boolean verify(Path p,Supplier<InputStream> recipe){return verify(p,recipe,PackSelectionService::verifyGeneratedRendererGraph);}
 public static boolean verify(Path p,Supplier<InputStream> recipe,BiFunction<Path,Map<String,String>,String> reader){
  if(p==null)return false;try(var in=recipe.get()){if(in==null||!RECIPE_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))))return false;
   return MakeUpAoContract.ID.equals(reader.apply(p,MakeUpAoContract.generated("0")));
  }catch(Exception|LinkageError unavailable){return false;}
 }
}
