package com.pp.shaderlod.optimization.unboundao;
/** Bootstrap-safe exact tuple selection from class bytes; never initializes Minecraft during Mixin preparation. */
public final class UnboundAoApiPins {
 private UnboundAoApiPins(){}
 public static boolean exact(){return com.pp.shaderlod.optimization.makeupao.MakeUpAoApiPins.exact("neoforge1.21.1")&&"Quad".equals(backend());}
 public static String backend(){return com.pp.shaderlod.optimization.makeupao.MakeUpAoApiPins.backend();}
 public static String status(){return "NeoForge1.21.1 "+com.pp.shaderlod.optimization.makeupao.MakeUpAoApiPins.status();}
}
