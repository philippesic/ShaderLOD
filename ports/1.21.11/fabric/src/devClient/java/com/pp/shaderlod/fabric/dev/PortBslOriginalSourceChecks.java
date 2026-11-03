package com.pp.shaderlod.fabric.dev;
import com.pp.shaderlod.integration.bslquad.BslQuadEligibility;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
/** Named BSL original-only certificate. Native export numbering is observed, never invented. */
final class PortBslOriginalSourceChecks {
    private PortBslOriginalSourceChecks() {}
    static boolean flagsAllow(boolean speed,boolean roundtrip,boolean quality,boolean ui,boolean candidate,boolean research) {
        return !speed && !roundtrip && !quality && !ui && !candidate && !research;
    }
    static boolean optionsMatch(Map<String,String> actual) { return BslQuadEligibility.optionsMatch(actual); }
    static boolean sourceMatches(Path archive,Map<String,String> actual) {
        return BslQuadEligibility.validate(archive,actual).eligible();
    }
    static void requireFreshExportDirectory(Path profile) throws Exception {
        Path exports=profile.toAbsolutePath().normalize().resolve("patched_shaders");
        if(Files.isSymbolicLink(exports)) throw new IllegalStateException("Compiler export directory aliases another path");
        if(Files.exists(exports)) try(var files=Files.list(exports)) {
            if(files.findAny().isPresent()) throw new IllegalStateException("Fresh empty compiler exports required before launch");
        }
    }
    static Map<String,String> terrainExports(Path profile,long startedMillis) throws Exception {
        Path root=profile.toAbsolutePath().normalize().resolve("patched_shaders");
        if(!Files.isDirectory(root)||Files.isSymbolicLink(root)) throw new IllegalStateException("Compiler terrain exports absent");
        var result=new TreeMap<String,String>();var pairs=new TreeMap<String,Set<String>>();
        try(var files=Files.list(root)) {
            for(Path file:files.toList()) {
                String name=file.getFileName().toString();
                if(!name.matches("[0-9]{3}_sodium_gbuffers_terrain(?:_solid|_cutout)?\\.(?:vsh|fsh)")) continue;
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(file)||
                        Files.getLastModifiedTime(file).toMillis()<startedMillis-1000||
                        Files.size(file)==0||Files.size(file)>1024*1024)
                    throw new IllegalStateException("Stale/invalid native terrain export: "+file);
                result.put(file.toAbsolutePath().toString(),HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
                int dot=name.lastIndexOf('.');
                pairs.computeIfAbsent(name.substring(0,dot),key->new HashSet<>()).add(name.substring(dot+1));
            }
        }
        if(result.size()!=4||pairs.size()!=2||pairs.values().stream().anyMatch(pair->!pair.equals(Set.of("vsh","fsh"))))
            throw new IllegalStateException("Expected two fresh actual BSL opaque/cutout terrain compiler pairs, found "+result.size());
        return Map.copyOf(result);
    }
}
