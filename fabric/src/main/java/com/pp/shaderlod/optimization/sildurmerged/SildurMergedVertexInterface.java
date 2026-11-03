package com.pp.shaderlod.optimization.sildurmerged;
/** Sildur native/current-source callback qualification plus exact captured three offset seams. */
public final class SildurMergedVertexInterface {private SildurMergedVertexInterface(){}public static String adapt(String s,String helper,boolean qualified){if(s==null||s.contains("gl_VertexID")||s.contains("gl_BaseVertex")||s.contains("gl_DrawID")||s.contains("gl_BaseInstance")||s.contains("gl_InstanceID"))return null;return com.pp.shaderlod.optimization.makeupmerged.MakeupMergedVertexInterface.adapt(s,helper,qualified);}}
