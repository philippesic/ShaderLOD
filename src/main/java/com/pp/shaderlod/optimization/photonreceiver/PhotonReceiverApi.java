package com.pp.shaderlod.optimization.photonreceiver;
import java.util.*;import java.security.MessageDigest;
/** NF1211 callback/provider pins, including exact shadow texture ownership API. */
public final class PhotonReceiverApi {
 private PhotonReceiverApi(){}private static final boolean EXACT=check();public static boolean exact(){return EXACT;}
 private static boolean check(){try{
 if(!com.pp.shaderlod.photonprefix.mixin.PhotonPrefixPlugin.nf1211()||!com.pp.shaderlod.optimization.photonhistory.PhotonAoHistoryPins.exactApi()||com.pp.shaderlod.optimization.photonhistory.PhotonAoHistoryPins.drawKind()!=0)return false;
 for(var p:Map.ofEntries(Map.entry("net/irisshaders/iris/shadows/ShadowRenderTargets.class","4d6f1b3294365738dbcacac691021105a8141b2893441d919aff8b148ff093c5"),Map.entry("net/irisshaders/iris/targets/DepthTexture.class","f9f7af036d89221ab874ca6158983606429ba245f8c8e243bfdf8ac151086ce0")).entrySet())try(var in=PhotonReceiverApi.class.getClassLoader().getResourceAsStream(p.getKey())){if(in==null||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(p.getValue()))return false;}
 return true;}catch(Exception|LinkageError unavailable){return false;}}
}
