package com.pp.shaderlod.optimization;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
/** Full byte identity only. No size/mtime admission, digest sampling, exception fallback or digest memo. */
public final class SourceArchiveDigest {
    public static final int BUFFER_BYTES=64*1024;
    @FunctionalInterface public interface DigestFactory {MessageDigest create() throws NoSuchAlgorithmException;}
    private SourceArchiveDigest() {}
    public static String implementation() {
        return OptimizationConfig.requested(OptimizationId.BINDING_DIGEST_STREAMING)?"SHA-256 full bytes; 64KiB streaming":"SHA-256 full bytes; legacy byte-array";
    }
    public static String sha256(Path path) throws IOException,NoSuchAlgorithmException {
        return sha256(path,OptimizationConfig.requested(OptimizationId.BINDING_DIGEST_STREAMING),()->MessageDigest.getInstance("SHA-256"));
    }
    public static String sha256(Path path,boolean streaming,DigestFactory factory) throws IOException,NoSuchAlgorithmException {
        if(!streaming) {
            byte[] bytes=Files.readAllBytes(path);return HexFormat.of().formatHex(factory.create().digest(bytes));
        }
        try(var input=Files.newInputStream(path)) {return sha256(input,true,factory);}
    }
    /** Test/read-provider seam; caller owns the supplied stream. Both routes exhaust all input bytes. */
    public static String sha256(InputStream input,boolean streaming,DigestFactory factory) throws IOException,NoSuchAlgorithmException {
        if(!streaming) {byte[] bytes=input.readAllBytes();return HexFormat.of().formatHex(factory.create().digest(bytes));}
        MessageDigest digest=null;NoSuchAlgorithmException unavailable=null;
        try {digest=factory.create();}catch(NoSuchAlgorithmException error){unavailable=error;}
        // Preserve legacy read-error priority over a missing digest provider without retaining whole input.
        byte[] buffer=new byte[BUFFER_BYTES];int count;
        while((count=input.read(buffer))!=-1) {
            if(count==0) {int single=input.read();if(single==-1)break;if(digest!=null)digest.update((byte)single);}
            else if(digest!=null)digest.update(buffer,0,count);
        }
        if(unavailable!=null)throw unavailable;
        return HexFormat.of().formatHex(digest.digest());
    }
}
