package com.pp.shaderlod.optimization.solasao;
import java.nio.file.Path;import java.io.InputStream;import java.util.*;import java.security.*;import java.util.function.*;
import com.pp.shaderlod.packs.PackSelectionService;
/** Real packaged recipe and complete generated archive/state/owned-map proof. No size/mtime cache. */
public final class SolasAoSourceProof {
 public static final String RECIPE_SHA="11ab545e3c55cb59b683ce35600460b7fb1a72671a69a508841d47cabcd3726a";
 private SolasAoSourceProof(){}
 public static boolean verify(Path p,Supplier<InputStream> recipe){return verify(p,recipe,PackSelectionService::verifyGeneratedRendererGraph);}
 public static boolean verify(Path p,Supplier<InputStream> recipe,BiFunction<Path,Map<String,String>,String> reader){
  if(p==null)return false;try(var in=recipe.get()){if(in==null||!RECIPE_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))))return false;
   return SolasAoContract.ID.equals(reader.apply(p,SolasAoContract.generated("0")));
  }catch(Exception|LinkageError unavailable){return false;}
 }
}
