package com.pp.shaderlod.dev;

import com.google.gson.JsonObject;
import com.pp.shaderlod.measurement.RuntimeMeasurement;
import com.pp.shaderlod.optimization.*;

/** Calls the genuine private action dispatcher; an authentic preflight refusal needs no client/GL. */
public final class OptimizationMeasurementTimingChecksMain {
    public static void main(String[] args)throws Exception {
        var method=OptimizationMeasurementControl.class.getDeclaredMethod("normalAction",OptimizationMeasurementControl.Request.class);method.setAccessible(true);
        OptimizationRuntimeContext.configure(()->null,()->{throw new IllegalStateException("intentional offline consumer preflight refusal");});
        var original=OptimizationConfig.snapshot();
        try {
            RuntimeMeasurement.request(true);
            var cancelled=(JsonObject)method.invoke(null,new OptimizationMeasurementControl.Request("no_pair_cancel","cancel",null,null));
            if(!RuntimeMeasurement.requested()||!cancelled.get("normal_timing_requested").getAsBoolean())throw new AssertionError("no-pair cancel disabled normal timing");
            var refused=(JsonObject)method.invoke(null,new OptimizationMeasurementControl.Request("refused_set","set",OptimizationId.CLOUD_PRIMARY_DETAIL,!original.enabled(OptimizationId.CLOUD_PRIMARY_DETAIL)));
            if(refused.get("success").getAsBoolean())throw new AssertionError("real consumer preflight refusal not reported");
            if(!RuntimeMeasurement.requested()||!refused.get("normal_timing_requested").getAsBoolean())throw new AssertionError("refused Apply disabled normal timing");
            if(!OptimizationConfig.snapshot().equals(original))throw new AssertionError("refused Apply changed original flags");
            System.out.println("PASS actual normalAction cancel without pair and set/preflight-refusal preserve timing request and flags; no client/GL used");
        }finally {RuntimeMeasurement.request(false);}
    }
}
