package com.pp.shaderlod.optimization.photonreceiver;
import java.util.*;import java.security.MessageDigest;
/** Fabric1.21.1 named/intermediary callback/provider pins, including exact shadow texture ownership API. */
public final class PhotonReceiverApi {
 private PhotonReceiverApi(){}private static final boolean EXACT=check();public static boolean exact(){return EXACT;}
 private static boolean check(){try{
 if(!com.pp.shaderlod.optimization.photonearlyalpha.mixin.PhotonEarlyAlphaPlugin.exactApi()||!com.pp.shaderlod.optimization.photonhistory.PhotonAoHistoryPins.exactApi()||com.pp.shaderlod.optimization.photonhistory.PhotonAoHistoryPins.drawKind()!=0)return false;
 for(var p:Map.ofEntries(Map.entry("net/irisshaders/iris/shadows/ShadowRenderTargets.class","38ec50d46524d1eaefb910793d5faa70a7de4b7145c1bc3bf83a0de45a7a5dbb"),Map.entry("net/irisshaders/iris/targets/DepthTexture.class","d125ff4e81b70b7fe3fb27934bba5f64eade80a86a57986564e032d47b165ef1")).entrySet())try(var in=PhotonReceiverApi.class.getClassLoader().getResourceAsStream(p.getKey())){if(in==null||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(p.getValue()))return false;}
 return true;}catch(Exception|LinkageError unavailable){return false;}}
}
