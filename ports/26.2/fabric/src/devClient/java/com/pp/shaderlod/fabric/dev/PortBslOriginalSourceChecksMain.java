package com.pp.shaderlod.fabric.dev;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
/** Parent-owned post-build negative admission checks; no Minecraft client, GL, synthetic shader exports. */
public final class PortBslOriginalSourceChecksMain {
    private static void check(boolean pass) { if(!pass) throw new AssertionError("Original BSL source admission mismatch"); }
    public static void main(String[] args) throws Exception {
        Path archive=Path.of(args.length==1?args[0]:"dependencies/upstream/other-pack-research/BSL_v10.1.8.zip");
        var properties=new java.util.Properties();properties.setProperty("shaderlod.dev.portBslOriginalSource","true");check(PortBslOriginalSourceChecks.propertiesAllow(properties));
        properties.setProperty("shaderlod.dev.foreignMode","false");check(!PortBslOriginalSourceChecks.propertiesAllow(properties));properties.remove("shaderlod.dev.foreignMode");
        properties.setProperty("shaderlod.experimentalRendererPort092","true");check(!PortBslOriginalSourceChecks.propertiesAllow(properties));
        var nativeOptions=com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions();
        check(nativeOptions.size()==384);check(PortBslOriginalSourceChecks.flagsAllow(false,false,false,false,false,false));
        for(int flag=0;flag<6;flag++)check(!PortBslOriginalSourceChecks.flagsAllow(flag==0,flag==1,flag==2,flag==3,flag==4,flag==5));
        check(PortBslOriginalSourceChecks.sourceMatches(archive,nativeOptions));
        check(!PortBslOriginalSourceChecks.sourceMatches(Path.of("missing.zip"),nativeOptions));
        check(!PortBslOriginalSourceChecks.optionsMatch(null));check(!PortBslOriginalSourceChecks.optionsMatch(Map.of()));
        for(String key:nativeOptions.keySet()) {
            var changed=new TreeMap<>(nativeOptions);changed.remove(key);check(!PortBslOriginalSourceChecks.optionsMatch(changed));
            changed.put(key,"foreign");check(!PortBslOriginalSourceChecks.optionsMatch(changed));
        }
        var extra=new TreeMap<>(nativeOptions);extra.put("SHADERLOD_PRIVATE_SOURCE","1");check(!PortBslOriginalSourceChecks.optionsMatch(extra));
        System.out.println("BSL flags/archive/native384 negative admission checks passed; driver/compiler/export identity unverified");
    }
}
