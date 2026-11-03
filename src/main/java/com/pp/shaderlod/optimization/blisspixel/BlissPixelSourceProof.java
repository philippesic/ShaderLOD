package com.pp.shaderlod.optimization.blisspixel;
import java.nio.file.Path;import java.io.InputStream;import java.util.*;import java.security.*;import java.util.function.*;
import com.pp.shaderlod.packs.PackSelectionService;
/** Real packaged recipe and complete generated archive/state/owned-map proof. No size/mtime cache. */
public final class BlissPixelSourceProof {
 public static final String RECIPE_SHA="fc63141d1952018fdab645074d728cff98108da7a247730c21372b5517e023dd";
 private BlissPixelSourceProof(){}
 public static boolean verify(Path p,Supplier<InputStream> recipe){return verify(p,recipe,PackSelectionService::verifyGeneratedRendererGraph);}
 public static boolean verify(Path p,Supplier<InputStream> recipe,BiFunction<Path,Map<String,String>,String> reader){
  if(p==null)return false;try(var in=recipe.get()){if(in==null||!RECIPE_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))))return false;
   return BlissPixelContract.ID.equals(reader.apply(p,BlissPixelContract.generated("0")));
  }catch(Exception|LinkageError unavailable){return false;}
 }
}
