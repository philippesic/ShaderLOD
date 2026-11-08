package com.pp.shaderlod.dev.timing.costmap;

import java.util.*;
import java.nio.file.*;
import static com.pp.shaderlod.dev.timing.costmap.CostMapRecords.*;

/** Headless e2e input comes from actual registry/owner transitions, not handwritten timing rows. */
public final class CostMapPipelineFixture {
    static String json(Object value)throws Exception {
        if(value==null)return "null";
        if(value instanceof String || value instanceof Enum<?>)return "\""+value.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\"";
        if(value instanceof Number || value instanceof Boolean)return value.toString();
        List<String> parts=new ArrayList<>();
        if(value instanceof Map<?,?> map){for(var e:map.entrySet())parts.add(json(e.getKey().toString())+":"+json(e.getValue()));return "{"+String.join(",",parts)+"}";}
        if(value instanceof Iterable<?> values){for(Object item:values)parts.add(json(item));return "["+String.join(",",parts)+"]";}
        for(var component:value.getClass().getRecordComponents())parts.add(json(component.getName())+":"+json(component.getAccessor().invoke(value)));
        return "{"+String.join(",",parts)+"}";
    }
    public static void main(String[] args)throws Exception {
        var registry=CaptureOwnerTest.registry();var scope=CaptureOwnerTest.scope(registry);
        var mock=new CaptureOwnerTest.Mock();
        var owner=new CaptureOwner(registry,mock,CaptureOwnerTest.CLOCK,Backend.ELAPSED,4,1);
        owner.request(CaptureOwner.Mode.DIAGNOSTIC);
        for(int i=1;i<=3;i++){
            owner.frame(CaptureOwnerTest.frame(1,i),true);
            var invocation=owner.begin(scope.id(),i*1000L,null,"main","world","opaque");
            owner.issue(invocation,true);owner.end(invocation,i*1000L+i*100L,null);
        }
        owner.stop("fixture completed",1);
        var batch=registry.drain();
        var data=new LinkedHashMap<String,Object>();data.put("schema_version",1);
        data.put("manifest",Map.of("capture_id","capture","cohort_id","cohort","pack_revision","makeup-9.5g","dh_state","ABSENT","evidence","synthetic lifecycle fixture; no hardware timing"));
        data.put("scopes",List.of(Map.of("id",scope.id(),"name",scope.name(),"program","deferred","graph_generation",1)));
        data.put("events",batch.events());data.put("coverage",batch.coverage());data.put("health",batch.health());
        data.put("frames",List.of(Map.of("frame_id",1,"start_ns",1000,"next_start_ns",2000)));
        Files.writeString(Path.of(args[0]),json(data));
    }
}
