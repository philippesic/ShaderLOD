package com.pp.shaderlod.optimization.sildurworld;
import java.util.*;
/** First native-RGB slice; existing public pixel bit, no native settings normalized. */
public final class SildurWorldContract{
 private SildurWorldContract(){}
 public static boolean graph(Map<String,String> m){return m!=null&&"1".equals(m.get("SHADERLOD_EFFECT_ADAPTER"))&&"1".equals(m.get("SHADERLOD_SILDUR_PIXEL_GRAPH"))&&"0".equals(m.get("SHADERLOD_SILDUR_SHADOW_QUERY_GRAPH"))&&"0".equals(m.get("SHADERLOD_SILDUR_AO_HALF_RES"))&&"0".equals(m.get("SHADERLOD_SILDUR_SPATIAL_REUSE"))&&"0".equals(m.get("SHADERLOD_SILDUR_LIGHTING_RATE"));}
 public static boolean options(Map<String,String> m){return graph(m)&&com.pp.shaderlod.optimization.sildurpixel.SildurPixelContract.options(m)&&"0".equals(m.get("SHADERLOD_SILDUR_SHADOW_QUERY_GRAPH"));}
}
