package com.pp.shaderlod.optimization.nativechain;
/** The typed native chain is qualified only for root NeoForge 1.21.1. */
public final class NativeChainAdmission {
 private NativeChainAdmission() {}
 public static boolean useReplacement(){return false;}
 public static String status(){return "legacy fallback: typed chain unqualified on this loader/version";}
}
