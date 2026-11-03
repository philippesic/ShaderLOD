package com.pp.shaderlod.experiment.bsl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Three untimed source/state snapshots; never an invocation or cost profiler.
 * No GL API, pixel readback, shader source query, clock, thread or timer here. */
public final class BslSteadyWitness {
    private static final int LIMIT=3, SPACING=60, MAX_BYTES=131072;
    private final Path output,arm;
    private boolean armed,terminal,sampling;
    private Object pipeline,context;
    private long pipelineEpoch,armEpoch,lastEpoch,lastShadow=-1,lastReturnEpoch=-1,returns;
    private int lastCounter,width,height,phase;
    private JsonObject sample;
    private final JsonArray samples=new JsonArray();
    private final Map<Integer,String> sampledPrograms=new LinkedHashMap<>();
    private final Map<String,Owner> owners=new LinkedHashMap<>();
    private final Map<String,Long> forbidden=new LinkedHashMap<>();
    private record Owner(Object object,BslFieldSourceRecords.Proof proof) {}
    public static BslSteadyWitness fromProperties() {
        String out=System.getProperty("shaderlod.dev.bslSteadyWitness","");
        String arm=System.getProperty("shaderlod.dev.bslSteadyWitnessArm","");
        return out.isBlank()||arm.isBlank()?null:new BslSteadyWitness(Path.of(out),Path.of(arm));
    }
    BslSteadyWitness(Path output,Path arm) {
        this.output=output;this.arm=arm;
        for(String operation:new String[]{"texture_readback","gpu_echo","driver_source_query","source_export"})forbidden.put(operation,0L);
    }
    public boolean sampling() { return armed&&!terminal&&sampling; }
    public boolean wantsDraw(int program) {
        if(!sampling()||sampledPrograms.containsKey(program))return false;
        if(sampledPrograms.size()>=2){fail("MORE_THAN_TWO_TYPED_DRAW_PROGRAMS");return false;}
        return true;
    }
    public void begin(Object pipeline,Object context,BslFieldEpochs.Identity id,int w,int h,boolean allowed) {
        if(terminal)return;
        if(!armed) {
            if(!Files.isRegularFile(arm,LinkOption.NOFOLLOW_LINKS))return;
            armed=true;this.pipeline=pipeline;this.context=context;pipelineEpoch=id.pipelineEpoch();
            armEpoch=lastEpoch=id.renderEpoch();lastCounter=id.frame();width=w;height=h;
            if(!allowed||pipeline==null||context==null||w<=0||h<=0)fail("ARMED_MODE_OR_IDENTITY_REJECTED");
            return;
        }
        if(sampling){fail("MISSING_NATIVE_FINISH");return;}
        if(lastReturnEpoch!=lastEpoch){fail("MISSING_ACTUAL_PRODUCER_NORMAL_RETURN");return;}
        if(!allowed||this.pipeline!=pipeline||this.context!=context||pipelineEpoch!=id.pipelineEpoch()
            ||w!=width||h!=height||id.renderEpoch()!=lastEpoch+1||id.frame()<=lastCounter) {
            fail("MODE_LIFETIME_RESIZE_COUNTER_OR_RENDER_DISCONTINUITY");return;
        }
        lastEpoch=id.renderEpoch();lastCounter=id.frame();
        if(id.renderEpoch()-armEpoch==1L+SPACING*samples.size()) {
            sampling=true;phase=0;sampledPrograms.clear();sample=new JsonObject();
            sample.addProperty("native_frames_after_arm",id.renderEpoch()-armEpoch);
            sample.addProperty("pipeline_epoch",id.pipelineEpoch());sample.addProperty("render_epoch",id.renderEpoch());
            sample.addProperty("frame_counter",id.frame());sample.addProperty("window_width",width);sample.addProperty("window_height",height);
            sample.add("phases",new JsonArray());
        }
    }
    /** Count only the actual producer quad normal-return hook, also between samples. */
    public void producerReturned(BslFieldEpochs.Identity id,boolean valid) {
        if(!armed||terminal)return;
        if(!valid||id.renderEpoch()!=lastEpoch||id.renderEpoch()<=lastReturnEpoch||id.frame()!=lastCounter){fail("PRODUCER_NORMAL_RETURN_INVALID_DUPLICATE_OR_DIFFERENT_FRAME");return;}
        lastReturnEpoch=id.renderEpoch();
        returns++;
    }
    public void forbidden(String operation) {
        if(!armed||terminal)return;
        forbidden.merge(operation,1L,Long::sum);fail("FORBIDDEN_OBSERVER_OPERATION:"+operation);
    }
    public void phase(String name,BslFieldEpochs.Identity id,Object owner,BslFieldSourceRecords.Proof proof,JsonObject row,boolean valid) {
        if(!sampling())return;
        boolean draw="raw-terrain-before-draw".equals(name);
        int expected=switch(name){case "producer-bound"->0;case "field-returned"->1;case "shadow-returned"->2;case "raw-terrain-before-draw"->3;default->-1;};
        if(!valid||id.renderEpoch()!=lastEpoch||id.frame()!=lastCounter||expected<0||phase!=expected
            ||owner==null||proof==null||proof.sha256()==null||proof.generation()<=0||proof.program()<=0) {
            fail("PHASE_ORDER_CURRENT_EPOCH_OWNER_OR_QUALIFIER_REJECTED:"+name);return;
        }
        if(expected==0){if(id.shadowEpoch()<=lastShadow){fail("DUPLICATE_SHADOW_TRAVERSAL");return;}lastShadow=id.shadowEpoch();sample.addProperty("shadow_epoch",id.shadowEpoch());}
        else if(id.shadowEpoch()!=lastShadow){fail("DIFFERENT_SHADOW_EPOCH");return;}
        String key=draw?proof.sha256():"producer";
        Owner prior=owners.get(key);
        if(prior!=null&&(prior.object!=owner||prior.proof!=proof)){fail("SOURCE_GENERATION_OR_OWNER_CHANGED");return;}
        if(draw&&sampledPrograms.containsKey(proof.program())){fail("DUPLICATE_DRAW_ROW");return;}
        if(draw){
            if(!owners.containsKey(key)&&owners.size()>=3){fail("DIFFERENT_SOURCE_PAIR");return;}
            sampledPrograms.put(proof.program(),proof.sha256());
        }
        owners.putIfAbsent(key,new Owner(owner,proof));
        ((JsonArray)sample.get("phases")).add(row.deepCopy());
        if(!draw)phase++;
    }
    public void finish() {
        if(!sampling())return;
        if(phase!=3||sampledPrograms.size()!=2||returns!=lastEpoch-armEpoch+1) {fail("INCOMPLETE_SAMPLE_PHASES_TWO_SOURCE_ROWS_OR_NORMAL_RETURNS");return;}
        sample.addProperty("producer_normal_returns_since_arm",returns);
        samples.add(sample);sample=null;sampling=false;
        if(samples.size()==LIMIT)write("THREE_SAMPLED_STEADY_ACTIVATION_JOINS","not continuous invocation/quality/performance evidence");
    }
    public void fail(String reason) { if(armed&&!terminal)write("INCOMPLETE_REJECTED",reason); }
    private void write(String status,String reason) {
        terminal=true;
        try {
            JsonObject result=new JsonObject();result.addProperty("status",status);result.addProperty("reason",reason);
            result.addProperty("never_time",true);result.addProperty("sample_limit",LIMIT);result.addProperty("spacing_native_frames",SPACING);
            result.addProperty("armed_render_epoch",armEpoch);result.addProperty("last_render_epoch",lastEpoch);
            result.addProperty("producer_normal_returns_since_arm",returns);result.add("samples",samples);
            if(sample!=null)result.add("incomplete_sample",sample);
            result.addProperty("finite_field_probe_enabled",false);result.addProperty("gpu_echo_observer_enabled",false);
            result.add("instrumented_observer_operations_after_arm",new Gson().toJsonTree(forbidden));
            result.addProperty("absence_scope","instrumented bridge paths plus static guards; not universal GL trace");
            result.addProperty("actual_stamp_cpu_read",false);result.addProperty("invocation_or_synth_hit_activity_proved",false);
            result.addProperty("quality_or_speed_accepted",false);
            byte[] bytes=(new Gson().toJson(result)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if(bytes.length>MAX_BYTES||Files.exists(output,LinkOption.NOFOLLOW_LINKS))return;
            Path parent=output.toAbsolutePath().getParent();Files.createDirectories(parent);
            Path tmp=parent.resolve(output.getFileName()+".tmp");Files.write(tmp,bytes,StandardOpenOption.CREATE_NEW);
            Files.move(tmp,output.toAbsolutePath(),StandardCopyOption.ATOMIC_MOVE);
        }catch(Exception unavailable){/* Missing/partial output cannot certify. No retry or async job. */}
    }
}
