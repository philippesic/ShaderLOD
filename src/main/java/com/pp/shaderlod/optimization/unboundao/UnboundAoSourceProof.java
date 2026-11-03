package com.pp.shaderlod.optimization.unboundao;
import java.nio.file.Path;import java.io.InputStream;import java.util.*;import java.security.*;import java.util.function.*;
import com.pp.shaderlod.packs.PackSelectionService;
/** Real packaged recipe and complete generated archive/state/owned-map proof. No size/mtime cache. */
public final class UnboundAoSourceProof {
 public static final String RECIPE_SHA="d19db58ed1901563a7ad064d3a49c175afb29fd1e46062c08db08929bf0a51fa";
 private UnboundAoSourceProof(){}
 public static boolean verify(Path p,Supplier<InputStream> recipe){return verify(p,recipe,PackSelectionService::verifyGeneratedRendererGraph);}
 public static boolean verify(Path p,Supplier<InputStream> recipe,BiFunction<Path,Map<String,String>,String> reader){
  if(p==null)return false;try(var in=recipe.get()){if(in==null||!RECIPE_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))))return false;
   return UnboundAoContract.ID.equals(reader.apply(p,UnboundAoContract.generated("0")));
  }catch(Exception|LinkageError unavailable){return false;}
 }
}
