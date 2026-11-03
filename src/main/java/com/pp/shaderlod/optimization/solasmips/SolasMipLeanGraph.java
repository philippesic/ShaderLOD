package com.pp.shaderlod.optimization.solasmips;

import com.google.gson.JsonParser;
import com.pp.shaderlod.optimization.CompileOptionControl;
import com.pp.shaderlod.optimization.mips.BslLeanGraph;
import java.util.*;
import java.security.MessageDigest;

/** Exact singleton native graph; only the mod-owned adapter and mip request differ. */
public final class SolasMipLeanGraph {
    private SolasMipLeanGraph() {}
    private static final Map<String,String> PROFILE=load();
    private static Map<String,String> load(){
        try(var in=SolasMipLeanGraph.class.getResourceAsStream("/assets/shaderlod/solasmips/options438.json")){
            if(in==null)return Map.of();byte[] bytes=in.readAllBytes();
            if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals("cec334bea3a1e3e5de638b856ed7b1ffaf5cfeb0538d4ad6de0e250a6218eb43"))return Map.of();
            var result=new TreeMap<String,String>();JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(e->result.put(e.getKey(),e.getValue().getAsString()));
            if(result.size()!=438)return Map.of();result.put(BslLeanGraph.ADAPTER,"0");return Map.copyOf(result);
        }catch(Exception|LinkageError unavailable){return Map.of();}
    }
    public static boolean nativeProfile(Map<String,String> actual){var nativeOptions=com.pp.shaderlod.integration.solasquad.SolasQuadEligibility.nativeOptions();return nativeOptions.size()==428&&nativeOptions.equals(actual);}
    public static boolean profile(Map<String,String> actual){
        if(PROFILE.isEmpty()||actual==null||actual.size()!=PROFILE.size()||!Set.of("0","1").contains(actual.getOrDefault(CompileOptionControl.SOLAS_DEAD_MIP,"invalid")))return false;
        var copy=new TreeMap<>(actual);copy.put(CompileOptionControl.SOLAS_DEAD_MIP,"0");return PROFILE.equals(copy);
    }
}
