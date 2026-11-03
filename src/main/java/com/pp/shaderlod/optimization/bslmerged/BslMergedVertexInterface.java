package com.pp.shaderlod.optimization.bslmerged;
/** Exact captured BSL three-seam wrapper around the existing merged input implementation. */
public final class BslMergedVertexInterface {private BslMergedVertexInterface(){}public static String adapt(String s,String helper,boolean qualified){if(s==null||s.contains("gl_VertexID")||s.contains("gl_BaseVertex")||s.contains("gl_DrawID"))return null;return com.pp.shaderlod.optimization.makeupmerged.MakeupMergedVertexInterface.adapt(s,helper,qualified);}}
