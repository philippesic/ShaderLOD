package com.pp.shaderlod.optimization;
import com.pp.shaderlod.integration.bindingcompat.BindingInputCertificateCache;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.*;
import java.util.*;
public final class BindingSliceChecksMain {
    private static int checks;
    private static void check(boolean value,String note){checks++;if(!value)throw new AssertionError(note);}
    private static final SourceArchiveDigest.DigestFactory SHA=()->MessageDigest.getInstance("SHA-256");
    private record Family(String name,String hash,Map<String,String> options,Validate validate) {}
    @FunctionalInterface private interface Validate {boolean run(Path archive,Map<String,String> options);}
    private static InputStream failing(){return new InputStream(){int count;
        public int read()throws IOException{if(count++>=70001)throw new IOException("read-provider-failure");return 17;}
    };}
    public static void main(String[] paths)throws Exception {
        if(paths.length!=8)throw new IllegalArgumentException("Actual seven pinned originals and adopted generated MakeUp receiver required");
        var directory=Files.createTempDirectory("binding-slice-check-");var path=directory.resolve("bytes.bin");
        for(int size:new int[]{0,1,65535,65536,65537,131071,131072,131073,2097155}) {
            byte[] bytes=new byte[size];new Random(9182).nextBytes(bytes);Files.write(path,bytes);
            String expected=HexFormat.of().formatHex(SHA.create().digest(bytes));
            check(expected.equals(SourceArchiveDigest.sha256(path,false,SHA)),"legacy complete bytes size"+size);
            check(expected.equals(SourceArchiveDigest.sha256(path,true,SHA)),"stream full bytes chunk boundary"+size);
        }
        for(boolean streaming:new boolean[]{false,true}) {
            try{SourceArchiveDigest.sha256(directory.resolve("absent"),streaming,SHA);throw new AssertionError("read error hidden");}catch(NoSuchFileException expected){checks++;}
            try{SourceArchiveDigest.sha256(new ByteArrayInputStream(new byte[10]),streaming,()->{throw new NoSuchAlgorithmException("provider-unavailable");});throw new AssertionError("provider error hidden");}catch(NoSuchAlgorithmException expected){check(expected.getMessage().equals("provider-unavailable"),"same provider exception");}
            try{SourceArchiveDigest.sha256(failing(),streaming,()->{throw new NoSuchAlgorithmException("also-provider-unavailable");});throw new AssertionError("read priority hidden");}catch(IOException expected){check(expected.getMessage().equals("read-provider-failure"),"legacy read failure priority");}
        }
        try(var fs=FileSystems.newFileSystem(java.net.URI.create("jar:"+directory.resolve("provider.zip").toUri()),Map.of("create","true"))) {
            Path entry=fs.getPath("/bounded.bin");Files.write(entry,new byte[65537]);
            check(SourceArchiveDigest.sha256(entry,false,SHA).equals(SourceArchiveDigest.sha256(entry,true,SHA)),"actual zip filesystem provider full digest");
        }
        var families=List.of(
            new Family("makeup",com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.ARCHIVE_SHA,com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.nativeOptions(),(p,o)->com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.validate(p,o).eligible()),
            new Family("bsl",com.pp.shaderlod.integration.bslquad.BslQuadEligibility.ARCHIVE_SHA,com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions(),(p,o)->com.pp.shaderlod.integration.bslquad.BslQuadEligibility.validate(p,o).eligible()),
            new Family("reimagined",com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.ARCHIVE_SHA,com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.nativeOptions(),(p,o)->com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.validate(p,o).eligible()),
            new Family("unbound",com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.ARCHIVE_SHA,com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.nativeOptions(),(p,o)->com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.validate(p,o).eligible()),
            new Family("sildur",com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.ARCHIVE_SHA,com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.nativeOptions(),(p,o)->com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.validate(p,o).eligible()),
            new Family("bliss",com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.ARCHIVE_SHA,com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.nativeOptions(),(p,o)->com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.validate(p,o).eligible()),
            new Family("photon",com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility.ARCHIVE_SHA,com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility.nativeOptions(),(p,o)->com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility.validate(p,o).eligible())
        );
        OptimizationConfig.load(directory.resolve("flags.properties"));
        for(boolean streaming:new boolean[]{false,true}) {
            OptimizationConfig.replace(OptimizationConfig.snapshot().with(OptimizationId.BINDING_DIGEST_STREAMING,streaming));
            for(int i=0;i<7;i++) {
                var family=families.get(i);Path original=Path.of(paths[i]);
                check(SourceArchiveDigest.sha256(original).equals(family.hash()),"authentic archive digest "+family.name());
                Object observedStamp=switch(family.name()) {
                    case "makeup"->com.pp.shaderlod.integration.bindingcompat.MakeUpSourceCertificate.stamp(original.getParent(),original.getFileName().toString(),original.getFileName().toString());
                    case "bsl"->com.pp.shaderlod.integration.bindingcompat.BslSourceCertificate.stamp(original.getParent(),original.getFileName().toString(),original.getFileName().toString());
                    case "reimagined"->com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.stamp(original.getParent(),original.getFileName().toString(),original.getFileName().toString());
                    case "unbound"->com.pp.shaderlod.integration.bindingcompat.UnboundSourceCertificate.stamp(original.getParent(),original.getFileName().toString(),original.getFileName().toString());
                    case "sildur"->com.pp.shaderlod.integration.bindingcompat.SildurSourceCertificate.stamp(original.getParent(),original.getFileName().toString(),original.getFileName().toString());
                    case "bliss"->com.pp.shaderlod.integration.bindingcompat.BlissSourceCertificate.stamp(original.getParent(),original.getFileName().toString(),original.getFileName().toString());
                    case "photon"->com.pp.shaderlod.integration.bindingcompat.PhotonSourceCertificate.stamp(original.getParent(),original.getFileName().toString(),original.getFileName().toString());
                    default->throw new AssertionError();
                };
                check(observedStamp.equals(List.of(family.hash(),family.hash())),"actual native source stamp "+family.name());
                check(family.validate().run(original,family.options()),"actual validator accepts "+family.name()+" streaming="+streaming);
                check(!family.validate().run(original,Map.of()),"native options guard retained "+family.name());
                Path changed=directory.resolve(family.name()+"-tamper.zip");Files.copy(original,changed,StandardCopyOption.REPLACE_EXISTING);long size=Files.size(changed);FileTime time=Files.getLastModifiedTime(changed);
                byte[] bytes=Files.readAllBytes(changed);bytes[bytes.length-1]^=1;Files.write(changed,bytes);Files.setLastModifiedTime(changed,time);
                check(Files.size(changed)==size&&Files.getLastModifiedTime(changed).equals(time),"same-size same-mtime fixture "+family.name());
                check(!family.validate().run(changed,family.options()),"full bytes reject tamper "+family.name());
            }
        }
        Path generated=Path.of(paths[7]);Path receiver=directory.resolve("adopted-generated");Files.createDirectories(receiver);
        try(var members=Files.list(generated.getParent())) {for(var file:members.toList())if(Files.isRegularFile(file))Files.copy(file,receiver.resolve(file.getFileName()));}
        String generatedName=generated.getFileName().toString();Object expectedStamp=null;
        for(boolean streaming:new boolean[]{false,true}) {
            OptimizationConfig.replace(OptimizationConfig.snapshot().with(OptimizationId.BINDING_DIGEST_STREAMING,streaming));
            Object observed=com.pp.shaderlod.integration.bindingcompat.MakeUpSourceCertificate.stamp(receiver,generatedName,"Original.zip");
            if(expectedStamp==null)expectedStamp=observed;else check(expectedStamp.equals(observed),"authentic generated/archive/state stamp identical across modes");
        }
        Path state;
        try(var zip=new java.util.zip.ZipFile(receiver.resolve(generatedName).toFile())) {
            String text=new String(zip.getInputStream(zip.getEntry("shaderlod-selection.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            state=receiver.resolve(com.google.gson.JsonParser.parseString(text).getAsJsonObject().get("stateFile").getAsString());
        }
        var stateTime=Files.getLastModifiedTime(state);byte[] stateBytes=Files.readAllBytes(state);long stateSize=stateBytes.length;
        stateBytes[stateBytes.length-1]^=1;Files.write(state,stateBytes);Files.setLastModifiedTime(state,stateTime);
        check(Files.size(state)==stateSize&&Files.getLastModifiedTime(state).equals(stateTime),"same-size restored-mtime authentic state tamper");
        check(!expectedStamp.equals(com.pp.shaderlod.integration.bindingcompat.MakeUpSourceCertificate.stamp(receiver,generatedName,"Original.zip")),"generated full-byte state stamp detects metadata replay tamper");
        var cache=new BindingInputCertificateCache();Object pack=new Object(),pipeline=new Object(),world=new Object(),values=new Object();var bool=Map.of("native",true);var string=Map.of("AA","2");var resources=List.of("vanilla","PBR");var stamp=List.of("full_archive_sha","full_state_sha");
        OptimizationConfig.replace(OptimizationConfig.snapshot().with(OptimizationId.BINDING_CERTIFICATE_CACHE,true));
        cache.remember(pack,pipeline,world,values,7,"pack",bool,string,resources,stamp,true);
        check(cache.matches(pack,pipeline,world,values,7,"pack",bool,string,resources,stamp)&&cache.eligible(),"cache on exact input reuse");
        check(!cache.matches(pack,new Object(),world,values,7,"pack",bool,string,resources,stamp),"pipeline identity guard");
        check(!cache.matches(pack,pipeline,new Object(),values,7,"pack",bool,string,resources,stamp),"world identity guard");
        check(!cache.matches(pack,pipeline,world,values,8,"pack",bool,string,resources,stamp),"lifecycle generation guard");
        check(!cache.matches(pack,pipeline,world,values,7,"other",bool,string,resources,stamp),"selected source name guard");
        check(!cache.matches(pack,pipeline,world,values,7,"pack",Map.of("native",false),string,resources,stamp),"complete boolean options guard");
        check(!cache.matches(pack,pipeline,world,values,7,"pack",bool,Map.of("AA","0"),resources,stamp),"complete string options guard");
        check(!cache.matches(pack,pipeline,world,values,7,"pack",bool,string,List.of("PBR","vanilla"),stamp),"resource iteration order guard");
        check(!cache.matches(pack,pipeline,world,values,7,"pack",bool,string,resources,List.of("tampered_full_sha","full_state_sha")),"content-stamp guard");
        OptimizationConfig.replace(OptimizationConfig.snapshot().with(OptimizationId.BINDING_CERTIFICATE_CACHE,false));
        check(!cache.matches(pack,pipeline,world,values,7,"pack",bool,string,resources,stamp),"cache off forces full certificate caller");
        cache.remember(pack,pipeline,world,values,7,"pack",bool,string,resources,stamp,true);
        check(cache.eligible(),"cache off does not disable mandatory correctness correction");
        check(!cache.matches(pack,pipeline,world,values,7,"pack",bool,string,resources,stamp),"cache off does not reuse fresh certificate");
        OptimizationConfig.replace(OptimizationConfig.snapshot().with(OptimizationId.BINDING_CERTIFICATE_CACHE,true));
        check(!cache.eligible(),"flag generation invalidates prior memo");
        cache.remember(pack,pipeline,world,values,7,"pack",bool,string,resources,stamp,true);
        OptimizationConfig.replace(OptimizationConfig.snapshot().with(OptimizationId.BINDING_DIGEST_STREAMING,false));
        check(!cache.matches(pack,pipeline,world,values,7,"pack",bool,string,resources,stamp),"other optimization generation invalidates memo");
        System.out.println("PASS "+checks+" actual archive/chunk/provider/tamper/memo checks; no rendering or profitability claim");
    }
}
