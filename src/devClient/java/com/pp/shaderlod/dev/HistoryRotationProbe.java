package com.pp.shaderlod.dev;

import com.google.gson.JsonObject;
import com.google.gson.Gson;


/** Thin untimed control adapter. Call on render thread; all returned state is immutable/value-only. */
public final class HistoryRotationProbe {
    private HistoryRotationProbe() {}
    public static JsonObject request(String action) {
        return new Gson().toJsonTree(invoke("request",action)).getAsJsonObject();
    }
    private static Object invoke(String method,String argument) {
        try {return Class.forName("com.pp.shaderlod.optimization.framecopy.rotation.HistoryRotationPreparation").getMethod(method,String.class).invoke(null,argument);}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("History rotation unavailable for this development tuple",unavailable);}
    }
    public static void closeBeforeMutation(String reason) { if(Boolean.getBoolean("shaderlod.experimentalMakeUpHistoryRotation"))invoke("close",reason); }
    public static JsonObject status() { return request("status"); }
}
