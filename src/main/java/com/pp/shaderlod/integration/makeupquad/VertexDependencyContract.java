package com.pp.shaderlod.integration.makeupquad;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
/** Exact complete vertex-stage transitive source contract; no GLSL rewriting. */
public final class VertexDependencyContract {
    private VertexDependencyContract() {}
    /** Conservative all-dimension main-gbuffers vertex-stage contract, including inactive include branches. */
    public static boolean sameVertexContract(Map<String,byte[]> original,Map<String,byte[]> generated) {
        try {
            var roots=new TreeSet<String>();var other=new TreeSet<String>();
            for(String p:original.keySet())if(stage(p))roots.add(p);
            for(String p:generated.keySet())if(stage(p))other.add(p);
            if(roots.isEmpty() || !roots.equals(other))return false;
            var seen=new HashSet<String>();for(String p:roots)if(!sameClosure(p,original,generated,seen))return false;
            return true;
        } catch(Exception unavailable) {return false;}
    }
    private static boolean stage(String path) {return path.startsWith("shaders/") && Path.of(path).getFileName().toString().startsWith("gbuffers_") && (path.endsWith(".vsh") || path.endsWith(".gsh") || path.endsWith(".tcs") || path.endsWith(".tes"));}
    private static final Pattern INCLUDE=Pattern.compile("(?m)^\\s*#\\s*include\\s+[\"<]([^\">]+)[\">]\\s*(?://[^\\r\\n]*)?$");
    private static boolean sameClosure(String path,Map<String,byte[]> a,Map<String,byte[]> b,Set<String> seen) {
        if(!seen.add(path))return true;
        byte[] before=a.get(path),after=b.get(path);
        if(before==null || !Arrays.equals(before,after))return false;
        String text=new String(before,StandardCharsets.UTF_8);var includes=INCLUDE.matcher(text);
        int count=0;while(includes.find()) {
            count++;String member=includes.group(1);if(member.indexOf('\\')>=0)return false;
            Path resolved=member.startsWith("/")?Path.of("shaders"+member):Path.of(path).getParent().resolve(member);
            String next=resolved.normalize().toString().replace(java.io.File.separatorChar,'/');
            if(!next.startsWith("shaders/") || !sameClosure(next,a,b,seen))return false;
        }
        // Macro or malformed includes have no exact dependency contract.
        long directives=Pattern.compile("(?m)^\\s*#\\s*include\\b").matcher(text).results().count();
        return count==directives;
    }
}
