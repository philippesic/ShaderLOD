package com.pp.shaderlod.dev.timing;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static com.pp.shaderlod.dev.timing.costmap.CostMapRecords.*;

/** Exercises the same canonical sink used by live timers, without loading Minecraft or OpenGL. */
public final class CostMapSessionTest {
    public static void main(String[] args)throws Exception {
        Map<CaptureField,Fact> identity=new EnumMap<>(CaptureField.class);
        for(var key:CaptureField.values())identity.put(key,new Fact("unknown","headless canonical sink fixture"));
        Map<DhField,Fact> dh=new EnumMap<>(DhField.class);
        for(var key:DhField.values())dh.put(key,new Fact("not_applicable","synthetic absent-DH fixture"));
        var capture=new Capture(1,"session-fixture","cohort","run","process",identity,new DhIdentity(DhState.ABSENT,"fixture",0,dh,Map.of()),1,1);
        var json=new GsonBuilder().serializeNulls().create();var request=new JsonObject();request.add("capture",json.toJsonTree(capture));
        var manifest=new JsonObject();manifest.addProperty("evidence","synthetic canonical live-sink test, no hardware measurement");request.add("manifest",manifest);
        request.add("scopes",JsonParser.parseString("[{\"name\":\"run-tick\",\"stage\":\"frame\",\"program\":\"frame\",\"operation\":\"CPU_WORK\"}]"));
        CostMapSession.start(request);CostMapSession.frame(1,true);
        Long cpu=CostMapSession.beginCpu("run-tick",1000L,null);
        CostMapSession.transfer(7,8,2560,1600);
        Long gpu=CostMapSession.issueGpu("run-tick",false,64);
        CostMapSession.endCpu(cpu,1300L,null);
        CostMapSession.frame(2,true);CostMapSession.resolveGpu(gpu,null,null,100L);
        CostMapSession.finish(Path.of(args[0]),"fixture complete");
        if(CostMapSession.active())throw new AssertionError("owner survived stop");
        var transfer=JsonParser.parseString(Files.readString(Path.of(args[0]).resolve("capture.json"))).getAsJsonObject().getAsJsonObject("manifest").getAsJsonArray("dh_transfers").get(0).getAsJsonObject();
        if(transfer.get("cpu_event").getAsLong()!=cpu||transfer.get("source_depth_texture").getAsInt()!=7||transfer.get("width").getAsInt()!=2560)throw new AssertionError("transfer identity lost");
        manifest.addProperty("dynamic_inventory",true);manifest.addProperty("graph_generation",5);
        request.add("scopes",new JsonArray());CostMapSession.start(request);CostMapSession.frame(3,true);
        Long dynamic=CostMapSession.beginCpu("new-live-hook",2000L,null);CostMapSession.endCpu(dynamic,2100L,null);
        Path extra=Path.of(args[0]).resolve("dynamic");CostMapSession.finish(extra,"dynamic inventory fixture");
        var saved=JsonParser.parseString(Files.readString(extra.resolve("capture.json"))).getAsJsonObject();
        if(saved.getAsJsonArray("scopes").get(0).getAsJsonObject().get("graph_generation").getAsInt()!=5)throw new AssertionError("graph ownership");
        if(saved.getAsJsonArray("events").size()!=1)throw new AssertionError("dynamic hook vanished");
    }
}
