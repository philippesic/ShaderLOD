package com.pp.shaderlod.optimization.blissspatial;
import java.nio.file.Path;import java.io.InputStream;import java.util.*;import java.security.*;import java.util.function.*;import com.pp.shaderlod.packs.PackSelectionService;
/** Full maintained graph/state/recipe admission; no timestamp shortcut. */
public final class BlissSpatialSourceProof {
 public static final String RECIPE_SHA="fc63141d1952018fdab645074d728cff98108da7a247730c21372b5517e023dd";
 private BlissSpatialSourceProof(){}
 public static boolean verify(Path p,Supplier<InputStream> recipe){try(var in=recipe.get()){return p!=null&&in!=null&&RECIPE_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())))&&BlissSpatialContract.ID.equals(PackSelectionService.verifyGeneratedRendererGraph(p,BlissSpatialContract.generated()));}catch(Exception|LinkageError unavailable){return false;}}
}
