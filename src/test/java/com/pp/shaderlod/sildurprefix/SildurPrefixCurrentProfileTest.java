package com.pp.shaderlod.sildurprefix;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class SildurPrefixCurrentProfileTest {
 private static Map<String,String> current(){var m=new TreeMap<>(SildurPrefixProfile.expected());for(String key:List.of("SHADERLOD_SILDUR_PIXEL_GRAPH","SHADERLOD_SILDUR_SPATIAL_REUSE","SHADERLOD_SILDUR_LIGHTING_RATE"))m.put(key,"0");return m;}
 @Test void acceptsCurrentDeclaredNativePOMProfileAndHistoricalProfile(){assertEquals(128,SildurPrefixProfile.expected().size());assertTrue(SildurPrefixProfile.accepts(SildurPrefixProfile.expected()));assertEquals(131,current().size());assertTrue(SildurPrefixProfile.accepts(current()));}
 @Test void refusesOtherGraphsNativeOptionMutationsAndUnknownDeclarations(){for(String key:List.of("SHADERLOD_SILDUR_PIXEL_GRAPH","SHADERLOD_SILDUR_SPATIAL_REUSE","SHADERLOD_SILDUR_LIGHTING_RATE","nMap")){var m=current();m.put(key,"1");assertFalse(SildurPrefixProfile.accepts(m),key);}var m=current();m.put("TAA","false");assertFalse(SildurPrefixProfile.accepts(m));m=current();m.remove("SHADERLOD_SILDUR_PIXEL_GRAPH");assertFalse(SildurPrefixProfile.accepts(m));m=current();m.put("foreign_graph","0");assertFalse(SildurPrefixProfile.accepts(m));assertFalse(SildurPrefixProfile.accepts(null));}
}
