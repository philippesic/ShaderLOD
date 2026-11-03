package com.pp.shaderlod.dev;
import com.google.gson.*;
import java.util.*;
/** Captures each native cloud phase at its completed writer, avoiding polling aliasing. */
final class G08BoundQueuedCapture {
    private G08BoundQueuedCapture() {}
    private static String pin,error="";
    private static int phases;
    private static final Map<Integer,JsonObject> rows=new TreeMap<>();
    static JsonObject request(String digest,int count){
        if(!G08BoundProbe.observing()||pin!=null||count<1||count>16||!digest.matches("[a-f0-9]{64}"))throw new IllegalStateException("Exclusive bounded armed cloud capture required");
        pin=digest;phases=count;rows.clear();error="";return status();
    }
    static void cancel(){pin=null;rows.clear();}
    static void afterDraw(int frame){
        if(pin==null||!error.isEmpty()||frame<0||rows.containsKey(frame%phases))return;
        try{var row=G08BoundReadback.read(pin);row.addProperty("checkerboard_phase",frame%phases);rows.put(frame%phases,row);if(rows.size()==phases)pin=null;}
        catch(Exception|LinkageError failure){error=failure.toString();pin=null;}
    }
    static JsonObject status(){var out=new JsonObject();out.addProperty("active",pin!=null);out.addProperty("error",error);out.addProperty("phase_count",phases);var list=new JsonArray();rows.values().forEach(list::add);out.add("captures",list);return out;}
}
