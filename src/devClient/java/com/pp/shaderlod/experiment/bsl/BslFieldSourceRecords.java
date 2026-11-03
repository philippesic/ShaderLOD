package com.pp.shaderlod.experiment.bsl;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** Native linked-driver stage evidence captured before detach, claimed by an
 * actual Program object. A numeric GL name alone never identifies a lifetime. */
public final class BslFieldSourceRecords {
    public static final int VERTEX=0x8B31, FRAGMENT=0x8B30;
    public record Proof(long generation, int program, String route, Map<Integer,String> stages, String sha256) {}
    private record Entry(long generation, Object owner, Proof proof) {}
    private final Map<Integer,Entry> entries=new TreeMap<>();
    private long generation;
    public long beforeLink(int program) {
        long next=++generation;
        entries.remove(program); // New/reused ID invalidates all prior object claims.
        if(program>0)entries.put(program,new Entry(next,null,null));
        return next;
    }
    public Proof linked(long token,int program,boolean success,Map<Integer,String> actualDriverStages) {
        return linked(token,program,success,actualDriverStages,"iris-driver-after-link-before-detach");
    }
    public Proof linked(long token,int program,boolean success,Map<Integer,String> actualDriverStages,String route) {
        Entry e=entries.get(program);
        if(e==null||e.generation!=token||!success){if(e!=null&&e.generation==token)entries.remove(program);return null;}
        String digest=completeDigest(actualDriverStages);
        if(digest==null){entries.remove(program);return null;}
        Proof p=new Proof(token,program,route,Map.copyOf(actualDriverStages),digest);
        entries.put(program,new Entry(token,null,p));return p;
    }
    /** Capture registration's exact already-linked generation BEFORE actual
     * Program construction finishes. This token supplies provenance only;
     * caller must later join forOwner/live lifetime before any upload/use. */
    public Proof forRegistration(int program) {
        Entry e=entries.get(program);return e==null?null:e.proof;
    }
    public Proof claim(Object owner,int program) {
        Entry e=entries.get(program);
        if(owner==null||e==null||e.proof==null||e.owner!=null)return null;
        entries.put(program,new Entry(e.generation,owner,e.proof));return e.proof;
    }
    /** Exact pending generation claim; a delayed old callback cannot claim a
     * newer linked program that reused this numeric name. */
    public Proof claim(Object owner,int program,long token) {
        Entry e=entries.get(program);
        return e!=null&&e.generation==token?claim(owner,program):null;
    }
    /** Constructor/link finally cleanup. Never deletes a newer generation or
     * an actual owner claimed by a successful normal return. */
    public void discardUnclaimed(long token,int program) {
        Entry e=entries.get(program);
        if(e!=null&&e.generation==token&&e.owner==null)entries.remove(program);
    }
    public Proof forOwner(Object owner,int program) {
        Entry e=entries.get(program);return owner!=null&&e!=null&&e.owner==owner?e.proof:null;
    }
    public void remove(Object owner,int program) {
        Entry e=entries.get(program);if(e!=null&&e.owner==owner)entries.remove(program);
    }
    /** No empty hash. Exactly complete nonempty V/F stages, canonical type order. */
    public static String completeDigest(Map<Integer,String> stages) {
        if(stages==null||stages.size()!=2||!stages.containsKey(VERTEX)||!stages.containsKey(FRAGMENT))return null;
        StringBuilder text=new StringBuilder();
        for(var stage:new TreeMap<>(stages).entrySet()){
            if(stage.getValue()==null||stage.getValue().isBlank())return null;
            text.append(stage.getKey()).append(':').append(stage.getValue()).append('\n');
        }
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
}
