package com.pp.shaderlod.dev;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class OptimizationMeasurementControlChecksMain {
    private static int checks;
    private static void check(boolean condition,String note){checks++;if(!condition)throw new AssertionError(note);}
    private static JsonObject control(String id,String action) {var nested=new JsonObject();nested.addProperty("request_id",id);nested.addProperty("action",action);var root=new JsonObject();root.add("optimizationMeasurement",nested);return root;}
    private static JsonObject read(Path receipt)throws Exception{return JsonParser.parseString(Files.readString(receipt)).getAsJsonObject();}
    public static void main(String[] args)throws Exception {
        Path directory=Files.createTempDirectory("normal-control-entry-");Path file=directory.resolve("control.json");var calls=new AtomicInteger();
        OptimizationMeasurementControl.Action fake=request->{calls.incrementAndGet();var result=new JsonObject();result.addProperty("success",true);result.addProperty("action",request.action());return result;};
        for(String action:List.of("set","timing_on","timing_off","compare","cancel","export","status")) {
            var root=control("valid_"+action,action);var nested=root.getAsJsonObject("optimizationMeasurement");
            if(action.equals("set")||action.equals("compare"))nested.addProperty("feature","cloud_primary_detail");
            if(action.equals("set"))nested.addProperty("enabled",false);
            root.addProperty("x",999);root.addProperty("time",17000);root.addProperty("preset","QUALITY"); // ignored by early branch, never scene actions
            var request=OptimizationMeasurementControl.parse(root);check(request.action().equals(action),"exact action schema "+action);
            Path receipt=OptimizationMeasurementControl.consume(root,file,()->{},fake);check(read(receipt).get("status").getAsString().equals("ACCEPTED"),"named receipt "+action);
            int before=calls.get();try {OptimizationMeasurementControl.consume(root,file,()->{},fake);throw new AssertionError("duplicate executed");}catch(FileAlreadyExistsException expected){checks++;}
            check(calls.get()==before,"duplicate ID never repeats normal action");
        }
        for(String action:List.of("unknown","status")) {
            var root=control("reject_"+action,action);if(action.equals("status"))root.getAsJsonObject("optimizationMeasurement").addProperty("warmup",0);
            int before=calls.get();Path receipt=OptimizationMeasurementControl.consume(root,file,()->{},fake);
            check(read(receipt).get("status").getAsString().equals("REJECTED")&&calls.get()==before,"unknown action/field rejected before API");
        }
        var badFeature=control("bad_feature","compare");badFeature.getAsJsonObject("optimizationMeasurement").addProperty("feature","bogus");
        check(read(OptimizationMeasurementControl.consume(badFeature,file,()->{},fake)).get("status").getAsString().equals("REJECTED"),"unknown stable feature rejected");
        var badBoolean=control("bad_boolean","set");badBoolean.getAsJsonObject("optimizationMeasurement").addProperty("feature","cloud_primary_detail");badBoolean.getAsJsonObject("optimizationMeasurement").addProperty("enabled","false");
        check(read(OptimizationMeasurementControl.consume(badBoolean,file,()->{},fake)).get("status").getAsString().equals("REJECTED"),"Boolean string not accepted");
        var badId=control("../escape","status");try{OptimizationMeasurementControl.consume(badId,file,()->{},fake);throw new AssertionError("unsafe path accepted");}catch(IllegalArgumentException expected){checks++;}
        var missingId=control("unused","status");missingId.getAsJsonObject("optimizationMeasurement").remove("request_id");try{OptimizationMeasurementControl.consume(missingId,file,()->{},fake);throw new AssertionError("missing id accepted");}catch(IllegalArgumentException expected){checks++;}
        int before=calls.get();var guard=control("timed_guard","timing_on");Path rejected=OptimizationMeasurementControl.consume(guard,file,()->{throw new IllegalArgumentException("Settings probe requires untimed inspection");},fake);
        check(read(rejected).get("status").getAsString().equals("REJECTED")&&calls.get()==before,"existing untimed guard runs before normal API");
        var refused=control("normal_apply_refused","set");refused.getAsJsonObject("optimizationMeasurement").addProperty("feature","cloud_primary_detail");refused.getAsJsonObject("optimizationMeasurement").addProperty("enabled",false);
        var returned=read(OptimizationMeasurementControl.consume(refused,file,()->{},request->{var result=new JsonObject();result.addProperty("success",false);result.addProperty("message","real API refusal");return result;}));
        check(returned.get("status").getAsString().equals("REJECTED")&&returned.getAsJsonObject("normal_api_result").get("message").getAsString().equals("real API refusal"),"normal API result preserved without fake pass");
        var status=read(OptimizationMeasurementControl.consume(control("actual_normal_status","status"),file,()->{}));
        check(status.get("status").getAsString().equals("ACCEPTED")&&status.getAsJsonObject("normal_api_result").has("runtime_status"),"actual normal RuntimeMeasurement status entry");
        System.out.println("PASS "+checks+" one-shot/schema/guard/receipt checks; mutating action seam fake, actual normal status/API linkage real; no client run");
    }
}
