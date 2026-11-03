package com.pp.shaderlod.integration.makeupquad;
import com.google.gson.JsonParser;
import com.pp.shaderlod.packs.PackSelectionService;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
/** Full producer replay plus exact certified native vertex-stage dependency closure.
 * Altered include behavior requires a separate reviewed named contract; raw vsh equality cannot admit it. */
public final class GeneratedRendererEligibility {
    private GeneratedRendererEligibility() {}
    public static boolean validate(Path selected,Map<String,String> effective) {
        try {
            String family=PackSelectionService.verifyGeneratedRendererGraph(selected,effective);
            if(family==null || !GeneratedRendererOptionContract.accepts(family,effective))return false;
            var generated=archive(selected);var marker=JsonParser.parseString(new String(generated.get("shaderlod-selection.json"),StandardCharsets.UTF_8)).getAsJsonObject();
            String name=marker.get("originalFile").getAsString();
            if(name.isBlank() || !Path.of(name).getFileName().toString().equals(name))return false;
            Path original=selected.toAbsolutePath().normalize().getParent().resolve(name);
            boolean nativeSafe=switch(family) {
                case "makeup-ultra-fast-shaders" -> MakeUpQuadEligibility.validate(original,MakeUpQuadEligibility.nativeOptions()).eligible();
                case "complementary-unbound" -> com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.validate(original,com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.nativeOptions()).eligible();
                case "complementary-reimagined" -> com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.validate(original,com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.nativeOptions()).eligible();
                case "bsl-shaders" -> com.pp.shaderlod.integration.bslquad.BslQuadEligibility.validate(original,com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions()).eligible();
                case "photon" -> com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility.validate(original,com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility.nativeOptions()).eligible();
                case "sildurs-vibrant-shaders" -> com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.validate(original,com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.nativeOptions()).eligible();
                case "bliss-shader" -> com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.validate(original,com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.nativeOptions()).eligible();
                case "astralex" -> com.pp.shaderlod.integration.astraquad.AstraQuadEligibility.validate(original,com.pp.shaderlod.integration.astraquad.AstraQuadEligibility.nativeOptions()).eligible();
                case "solas-shader" -> com.pp.shaderlod.integration.solasquad.SolasQuadEligibility.validate(original,com.pp.shaderlod.integration.solasquad.SolasQuadEligibility.nativeOptions()).eligible();
                default -> false;
            };
            return nativeSafe && (VertexDependencyContract.sameVertexContract(archive(original),generated) || NamedVertexContract.accepts(family,archive(original),generated));
        } catch(Exception | LinkageError unavailable) {return false;}
    }
    private static Map<String,byte[]> archive(Path path) throws Exception {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)>128L*1024*1024)throw new IllegalArgumentException("Archive unavailable");
        var files=new TreeMap<String,byte[]>();long total=0;
        try(var zip=new ZipInputStream(Files.newInputStream(path))) {java.util.zip.ZipEntry e;while((e=zip.getNextEntry())!=null) {
            byte[] bytes=zip.readNBytes(128*1024*1024+1);total+=bytes.length;if(total>128L*1024*1024)throw new IllegalArgumentException("Archive too large");
            if(!e.isDirectory() && files.put(e.getName(),bytes)!=null)throw new IllegalArgumentException("Duplicate member");
        }}return files;
    }
}
