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
        Long gpu=CostMapSession.issueGpu("run-tick",false,64);
        CostMapSession.endCpu(cpu,1300L,null);
        CostMapSession.frame(2,true);CostMapSession.resolveGpu(gpu,null,null,100L);
        CostMapSession.finish(Path.of(args[0]),"fixture complete");
        if(CostMapSession.active())throw new AssertionError("owner survived stop");
    }
}
