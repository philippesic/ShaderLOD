package com.pp.shaderlod.optimization.reimaginedmerged;
/** Exact Reimagined source qualification and native Iris region-offset seams. */
public final class ReimaginedMergedVertexInterface {
 private ReimaginedMergedVertexInterface(){}
 public static boolean safeDrawIdentity(String s){return s!=null&&!s.contains("gl_VertexID")&&!s.contains("gl_BaseVertex")&&!s.contains("gl_DrawID")&&!s.contains("gl_BaseInstance")&&!s.contains("gl_InstanceID");}
 public static String adapt(String s,String helper,boolean qualified){return safeDrawIdentity(s)?com.pp.shaderlod.optimization.makeupmerged.MakeupMergedVertexInterface.adapt(s,helper,qualified):null;}
}
