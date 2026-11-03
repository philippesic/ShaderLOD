package com.pp.shaderlod.experiment.bsl;

import java.util.IdentityHashMap;
import java.util.Map;

/** A live object claim, not a GL-name cache. Link invalidates prior generation;
 * ordinary pass/owner joins are established at pinned Sodium construction. */
public final class BslSteadyPrograms {
    public record Draw(Object pipeline,Object program,Object shader,String pass,
                       BslFieldSourceRecords.Proof proof,Object context) {}
    private final BslFieldSourceRecords sources=new BslFieldSourceRecords();
    private final Map<Object,Draw> shaders=new IdentityHashMap<>();
    private Draw active;
    public long beforeLink(int program){active=null;return sources.beforeLink(program);}
    public void linked(long token,int program,boolean success,Map<Integer,String> stages){
        sources.linked(token,program,success,stages,"sodium-driver-after-link-before-stage-delete");
    }
    public BslFieldSourceRecords.Proof forRegistration(int program){return sources.forRegistration(program);}
    /** Builder normal return owns even non-Iris/outside typed-build programs.
     * Exceptional completion discards only this unowned pending generation. */
    public boolean linkReturned(long token,Object program,int id) {
        return sources.claim(program,id,token)!=null;
    }
    public void linkFinished(long token,int id){sources.discardUnclaimed(token,id);}
    public boolean built(Object pipeline,Object program,Object shader,int id,String pass,Object context) {
        Draw duplicate=shaders.get(shader);
        if(duplicate!=null){deleted(duplicate.program,duplicate.proof.program());return false;}
        var proof=sources.forOwner(program,id);
        if(pipeline==null||program==null||shader==null||context==null||proof==null
                ||!("TERRAIN".equals(pass)||"TERRAIN_CUTOUT".equals(pass))) {
            deleted(program,id);return false;
        }
        shaders.put(shader,new Draw(pipeline,program,shader,pass,proof,context));return true;
    }
    public void setupBegin(){active=null;}
    public void setupReturned(Object shader){
        Draw d=shaders.get(shader);
        active=d!=null&&sources.forOwner(d.program,d.proof.program())==d.proof?d:null;
    }
    public Draw current(Object pipeline,int id,Object context){
        Draw d=active;
        return d!=null&&d.pipeline==pipeline&&d.proof.program()==id&&d.context==context
            &&sources.forOwner(d.program,id)==d.proof?d:null;
    }
    /** Native ProgramUniforms.update attaches callbacks before setup RETURN.
     * Ownership can be checked then without admitting a consuming draw. */
    public BslFieldSourceRecords.Proof boundOwner(Object pipeline,int id,Object context) {
        for(Draw d:shaders.values())if(d.pipeline==pipeline&&d.proof.program()==id&&d.context==context
                &&sources.forOwner(d.program,id)==d.proof)return d.proof;
        return null;
    }
    public void reset(Object shader){if(active!=null&&active.shader==shader)active=null;}
    public void deleted(Object program,int id){
        sources.remove(program,id);
        shaders.values().removeIf(d -> d.program==program);
        if(active!=null&&active.program==program)active=null;
    }
    public void dispose(Object pipeline){
        shaders.values().removeIf(d -> {if(d.pipeline!=pipeline)return false;sources.remove(d.program,d.proof.program());return true;});
        if(active!=null&&active.pipeline==pipeline)active=null;
    }
}
