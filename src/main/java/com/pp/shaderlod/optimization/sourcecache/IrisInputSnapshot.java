package com.pp.shaderlod.optimization.sourcecache;

import com.pp.shaderlod.packs.CapturedRendererInputs;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

/** Complete input-tree observation made before ShaderPack discovers any program roots.
 * Includes empty directories (dimension fallback decisions) and every property/resource file.
 * This is loading evidence only: a linked pipeline never polls its former source tree.
 */
public record IrisInputSnapshot(Map<String,String> files, Set<String> directories) {
    public IrisInputSnapshot { files=Map.copyOf(files); directories=Set.copyOf(directories); }

    public static IrisInputSnapshot capture(Path shaders) throws Exception {
        var files=new TreeMap<String,String>();var directories=new TreeSet<String>();
        try(var paths=Files.walk(shaders)) {
            for(var path:paths.toList()) {
                if(path.equals(shaders))continue;
                if(Files.isSymbolicLink(path))throw new IllegalStateException("Linked shader input");
                String member="shaders/"+shaders.relativize(path).toString().replace('\\','/');
                if(Files.isDirectory(path))directories.add(member+"/");
                else if(Files.isRegularFile(path))try(var in=Files.newInputStream(path)){files.put(member,hash(in));}
                else throw new IllegalStateException("Unknown shader input");
            }
        }
        return new IrisInputSnapshot(files,directories);
    }
    private static String hash(java.io.InputStream in)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];int count;
        while((count=in.read(buffer))!=-1)digest.update(buffer,0,count);
        return HexFormat.of().formatHex(digest.digest());
    }
    public boolean unchanged(Path shaders) {
        try{return equals(capture(shaders));}catch(Exception|LinkageError unavailable){return false;}
    }
    public boolean matches(CapturedRendererInputs snapshot) {
        return snapshot!=null&&files.equals(snapshot.shaderFileHashes());
    }
    /** Exact names returned by pinned Iris discovery, including absent optional roots. */
    public boolean discovered(net.irisshaders.iris.shaderpack.include.AbsolutePackPath directory,
            List<String> candidates, List<net.irisshaders.iris.shaderpack.include.AbsolutePackPath> actual, boolean present) {
        var expected=new ArrayList<net.irisshaders.iris.shaderpack.include.AbsolutePackPath>();
        for(String candidate:candidates) {
            var root=directory.resolve(candidate);String member="shaders"+root.getPathString();
            if(files.containsKey(member)||directories.contains(member+"/"))expected.add(root);
        }
        return expected.equals(actual)&&present==!expected.isEmpty();
    }
    public boolean exists(String relative,boolean actual) {
        String member="shaders/"+relative.replace('\\','/');
        return actual==(files.containsKey(member)||directories.contains(member+"/"));
    }
    public boolean property(String name,String text) {
        String expected=files.get("shaders/"+name);
        if(text==null)return expected==null;
        try{return Objects.equals(expected,hash(new java.io.ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1))));}
        catch(Exception unavailable){return false;}
    }
    public boolean matchesArchive(Path path)throws Exception {
        var files=new TreeMap<String,String>();var directories=new TreeSet<String>();
        try(var archive=new ZipFile(path.toFile())) {
            var names=new HashSet<String>();var entries=archive.entries();
            while(entries.hasMoreElements()) {
                var entry=entries.nextElement();String name=entry.getName();
                if(!names.add(name))return false;
                if(!name.startsWith("shaders/")||name.equals("shaders/"))continue;
                if(name.contains("\\")||Arrays.asList(name.split("/")).stream().anyMatch(p->p.equals(".")||p.equals("..")||p.isEmpty()))return false;
                if(entry.isDirectory())directories.add(name);
                else try(var in=archive.getInputStream(entry)){files.put(name,hash(in));}
                for(int slash=name.lastIndexOf('/');slash>"shaders".length();slash=name.lastIndexOf('/',slash-1))
                    directories.add(name.substring(0,slash+1));
            }
        }
        return this.files.equals(files)&&this.directories.equals(directories);
    }
}
