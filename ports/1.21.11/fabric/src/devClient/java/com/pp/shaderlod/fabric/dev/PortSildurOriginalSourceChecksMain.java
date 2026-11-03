package com.pp.shaderlod.fabric.dev;

import java.nio.file.*;
import java.util.*;
/** Parent runs against actual produced classes/resources; no GL or fabricated positive shader exports. */
public final class PortSildurOriginalSourceChecksMain {
    private static void check(boolean pass) { if(!pass)throw new AssertionError("Sildur original source admission mismatch"); }
    private interface Refusal { void run() throws Exception; }
    private static void refuses(Refusal action) throws Exception {
        try { action.run(); } catch(IllegalStateException expected) { return; }
        throw new AssertionError("Expected source admission refusal");
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("One authentic Sildur archive path required");
        Path archive=Path.of(args[0]);var nativeOptions=com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.nativeOptions();
        check(nativeOptions.size()==115);check(PortSildurOriginalSourceChecks.sourceMatches(archive,nativeOptions));
        check(!PortSildurOriginalSourceChecks.sourceMatches(Path.of("missing.zip"),nativeOptions));
        var base=new Properties();base.setProperty("shaderlod.dev.portSildurOriginalSource","true");
        check(PortSildurOriginalSourceChecks.propertiesAllow(base));
        for(String flag:List.of("shaderlod.dev.portBslOriginalSource","shaderlod.dev.portRoundtrip","shaderlod.dev.portQualityRoundtrip",
            "shaderlod.dev.portRoundtripUi","shaderlod.dev.portRendererSpeedProbe","shaderlod.rendererSpeedPortR1",
            "shaderlod.experimentalRendererResearch","shaderlod.photonQuadOriginalGraphR1","shaderlod.dev.portUnknown")) {
            var conflict=new Properties();conflict.putAll(base);conflict.setProperty(flag,"true");check(!PortSildurOriginalSourceChecks.propertiesAllow(conflict));
            conflict.setProperty(flag,"bogus");check(!PortSildurOriginalSourceChecks.propertiesAllow(conflict));
        }
        check(!PortSildurOriginalSourceChecks.optionsMatch(null));check(!PortSildurOriginalSourceChecks.optionsMatch(Map.of()));
        for(String key:nativeOptions.keySet()) {
            var changed=new TreeMap<>(nativeOptions);changed.remove(key);check(!PortSildurOriginalSourceChecks.optionsMatch(changed));
            changed.put(key,"foreign");check(!PortSildurOriginalSourceChecks.optionsMatch(changed));
        }
        var extra=new TreeMap<>(nativeOptions);extra.put("SHADERLOD_PRIVATE_SOURCE","1");check(!PortSildurOriginalSourceChecks.optionsMatch(extra));
        Path temp=Files.createTempDirectory("shaderlod-sildur-negative-");
        try {
            Path wrong=temp.resolve("wrong.zip");Files.writeString(wrong,"not a shader archive");check(!PortSildurOriginalSourceChecks.sourceMatches(wrong,nativeOptions));
            Path alias=temp.resolve("alias.zip");Files.createSymbolicLink(alias,archive.toAbsolutePath());check(!PortSildurOriginalSourceChecks.sourceMatches(alias,nativeOptions));
            Path sidecar=Path.of(wrong+".txt");Files.writeString(sidecar,"SHADERLOD_PRIVATE_SOURCE=1\n");refuses(()->PortSildurOriginalSourceChecks.requireNativeSidecar(wrong));
            Files.delete(sidecar);Files.createSymbolicLink(sidecar,archive.toAbsolutePath());refuses(()->PortSildurOriginalSourceChecks.requireNativeSidecar(wrong));
            Path exports=temp.resolve("patched_shaders");Files.createSymbolicLink(exports,temp.resolve("absent"));refuses(()->PortBslOriginalSourceChecks.requireFreshExportDirectory(temp));
            Files.delete(exports);Files.createDirectory(exports);Files.writeString(exports.resolve("stale-marker"),"stale");refuses(()->PortBslOriginalSourceChecks.requireFreshExportDirectory(temp));
            refuses(()->PortSildurOriginalSourceChecks.terrainExports(temp,System.currentTimeMillis(),Map.of()));
        } finally {
            try(var files=Files.walk(temp)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.delete(file);}
        }
        System.out.println("Sildur named flags/archive/native115/sidecar/alias admission negatives passed; actual mapping/export/runtime unverified");
    }
}
