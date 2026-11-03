package com.pp.shaderlod.fabric.dev;

import com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.shaderpack.programs.ProgramFallbackResolver;
import net.minecraft.client.Minecraft;

/** One named native Sildur compiler-source mode. No shader mutation, reload or Apply. */
final class PortSildurOriginalSourceChecks {
    private PortSildurOriginalSourceChecks() {}
    static boolean propertiesAllow(Properties properties) {
        Set<String> allowed=Set.of("shaderlod.dev.portSildurOriginalSource","shaderlod.dev.portRoundtripOriginal",
            "shaderlod.dev.portBslOriginalSource","shaderlod.dev.portRoundtrip","shaderlod.dev.portQualityRoundtrip",
            "shaderlod.dev.portRoundtripUi","shaderlod.dev.portRendererSpeedProbe");
        Set<String> off=Set.of("shaderlod.dev.portBslOriginalSource","shaderlod.dev.portRoundtrip",
            "shaderlod.dev.portQualityRoundtrip","shaderlod.dev.portRoundtripUi","shaderlod.dev.portRendererSpeedProbe",
            "shaderlod.rendererSpeedPortR1","shaderlod.experimentalRendererResearch","shaderlod.photonQuadOriginalGraphR1");
        if (!"true".equals(properties.getProperty("shaderlod.dev.portSildurOriginalSource"))) return false;
        for(String key:properties.stringPropertyNames()) {
            if(key.startsWith("shaderlod.dev.port") && !allowed.contains(key)) return false;
            if(off.contains(key) && !"false".equals(properties.getProperty(key))) return false;
        }
        return true;
    }
    static boolean optionsMatch(Map<String,String> actual) { return SildurQuadEligibility.optionsMatch(actual); }
    static boolean sourceMatches(Path archive,Map<String,String> actual) {
        return archive!=null && !Files.isSymbolicLink(archive) && SildurQuadEligibility.validate(archive,actual).eligible();
    }
    static void requireNativeSidecar(Path archive) throws Exception {
        Path sidecar=Path.of(archive.toString()+".txt");
        if(!Files.exists(sidecar,LinkOption.NOFOLLOW_LINKS)) return;
        if(!Files.isRegularFile(sidecar,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(sidecar)||Files.size(sidecar)>128*1024)
            throw new IllegalStateException("Sildur native option sidecar aliases/invalid");
        var overrides=new Properties();try(var in=Files.newInputStream(sidecar)){overrides.load(in);}
        if(!overrides.isEmpty()) throw new IllegalStateException("Sildur original-only requires empty native option overrides");
    }
    record ProgramMapping(String irisPass,String originalId,String sourceName,int programHandle) {}
    static Map<String,ProgramMapping> programMapping(Minecraft client) throws Exception {
        // Read the exact resolver retained by the actual current pipeline; no replacement resolver or mutation.
        var pipeline=(IrisRenderingPipeline)Iris.getPipelineManager().getPipelineNullable();
        var resolverField=IrisRenderingPipeline.class.getDeclaredField("resolver");
        if(resolverField.getType()!=ProgramFallbackResolver.class || !resolverField.trySetAccessible())
            throw new IllegalStateException("Actual selected pipeline resolver unavailable");
        var resolver=(ProgramFallbackResolver)resolverField.get(pipeline);
        if(resolver==null || resolver.getClass()!=ProgramFallbackResolver.class)
            throw new IllegalStateException("Unexpected selected pipeline resolver");
        var programs=pipeline.getSodiumPrograms();
        var result=new TreeMap<String,ProgramMapping>();
        for(String pass:List.of("SOLID","CUTOUT")) {
            var irisPass=pass.equals("SOLID")?SodiumPrograms.Pass.TERRAIN:SodiumPrograms.Pass.TERRAIN_CUTOUT;
            var source=resolver.resolve(irisPass.getOriginalId()).orElseThrow();
            String name=source.getName();
            if(!name.matches("gbuffers_(?:terrain(?:_solid|_cutout)?|textured(?:_lit)?)") ||
                    source.getVertexSource().isEmpty() || source.getFragmentSource().isEmpty() ||
                    source.getGeometrySource().isPresent() || source.getTessControlSource().isPresent() || source.getTessEvalSource().isPresent())
                throw new IllegalStateException("Unadmitted native Sildur opaque fallback/stages: "+name);
            int handle=programs.getProgram(pass.equals("SOLID")?
                net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.SOLID:
                net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.CUTOUT).handle();
            if(handle<=0) throw new IllegalStateException("Missing native Sildur program for "+pass);
            result.put(pass,new ProgramMapping(irisPass.name(),irisPass.getOriginalId().name(),name,handle));
        }
        return Map.copyOf(result);
    }
    static Map<String,String> terrainExports(Path profile,long startedMillis,Map<String,ProgramMapping> mapping) throws Exception {
        if(!mapping.keySet().equals(Set.of("SOLID","CUTOUT"))) throw new IllegalStateException("Missing observed opaque pass mapping");
        Path root=profile.toAbsolutePath().normalize().resolve("patched_shaders");
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(root))
            throw new IllegalStateException("Fresh Sildur compiler exports absent/aliased");
        var expected=new TreeMap<String,Integer>();
        for(var pass:mapping.values())expected.merge(pass.sourceName(),1,Integer::sum);
        var result=new TreeMap<String,String>();var pairs=new TreeMap<String,Set<String>>();var names=new HashMap<String,String>();
        Pattern opaque=Pattern.compile("([0-9]{3})_sodium_(gbuffers_(?:terrain(?:_solid|_cutout)?|textured(?:_lit)?))\\.(vsh|fsh|gsh|tcs|tes)");
        try(var files=Files.list(root)) {
            for(Path file:files.toList()) {
                if(Files.isSymbolicLink(file))throw new IllegalStateException("Aliased compiler export: "+file);
                var match=opaque.matcher(file.getFileName().toString());if(!match.matches())continue;
                if(!expected.containsKey(match.group(2)) || !Set.of("vsh","fsh").contains(match.group(3)) ||
                        !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)==0 || Files.size(file)>1024*1024 ||
                        Files.getLastModifiedTime(file).toMillis()<startedMillis-1000)
                    throw new IllegalStateException("Extra/stale/invalid Sildur opaque export: "+file);
                String base=match.group(1)+"_sodium_"+match.group(2);
                names.put(base,match.group(2));pairs.computeIfAbsent(base,key->new HashSet<>()).add(match.group(3));
                result.put(file.toAbsolutePath().toString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
            }
        }
        var counts=new TreeMap<String,Integer>();for(String name:names.values())counts.merge(name,1,Integer::sum);
        if(result.size()!=4 || pairs.size()!=2 || !counts.equals(expected) ||
                pairs.values().stream().anyMatch(pair->!pair.equals(Set.of("vsh","fsh"))))
            throw new IllegalStateException("Expected exactly two fresh actual Sildur opaque pairs matching selected fallback names");
        // Same-name SOLID/CUTOUT exports remain a pair set: never invent a file-to-handle association.
        return Map.copyOf(result);
    }
}
