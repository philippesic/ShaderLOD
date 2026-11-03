package com.pp.shaderlod.optimization.sildurao;
import java.nio.file.Path;import java.io.InputStream;import java.util.*;import java.security.*;import java.util.function.*;
import com.pp.shaderlod.packs.PackSelectionService;
/** Real packaged recipe and complete generated archive/state/owned-map proof. No size/mtime cache. */
public final class SildurAoSourceProof {
 public static final String RECIPE_SHA="a729dfcf50af6bc89ee2e3cdc5dd669bd64ce6aae757c6a67bc8166d438b4a41";
 private SildurAoSourceProof(){}
 public static boolean verify(Path p,Supplier<InputStream> recipe){return verify(p,recipe,PackSelectionService::verifyGeneratedRendererGraph);}
 public static boolean verify(Path p,Supplier<InputStream> recipe,BiFunction<Path,Map<String,String>,String> reader){
  if(p==null)return false;try(var in=recipe.get()){if(in==null||!RECIPE_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))))return false;
   return SildurAoContract.ID.equals(reader.apply(p,SildurAoContract.generated("0")));
  }catch(Exception|LinkageError unavailable){return false;}
 }
}
