package com.pp.shaderlod.optimization.blissao;
import java.nio.file.Path;import java.io.InputStream;import java.util.*;import java.security.*;import java.util.function.*;
import com.pp.shaderlod.packs.PackSelectionService;
/** Same complete actual maintained graph/state verification used by existing AO producers. */
public final class BlissAoSourceProof {
 public static final String RECIPE_SHA="fc63141d1952018fdab645074d728cff98108da7a247730c21372b5517e023dd";
 private BlissAoSourceProof(){}
 public static boolean verify(Path path,Supplier<InputStream> recipe){try(var in=recipe.get()){return path!=null&&in!=null&&RECIPE_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())))&&BlissAoContract.ID.equals(PackSelectionService.verifyGeneratedRendererGraph(path,BlissAoContract.generated("0")));}catch(Exception|LinkageError unavailable){return false;}}
}
