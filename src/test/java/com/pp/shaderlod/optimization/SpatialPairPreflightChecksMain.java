package com.pp.shaderlod.optimization;

import com.pp.shaderlod.measurement.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the genuine runtime entry before any Minecraft/GL/scene callback is reached. */
public final class SpatialPairPreflightChecksMain {
    private static int checks;
    private static void check(boolean value,String note){checks++;if(!value)throw new AssertionError(note);}
    public static void main(String[] args)throws Exception {
        var previous=OptimizationConfig.state();boolean timingBefore=RuntimeMeasurement.requested();
        var file=Files.createTempDirectory("shaderlod-spatial-pair-").resolve("flags.properties");
        try {
            OptimizationConfig.load(file);OptimizationConfig.enableShaderProfiles();RuntimeMeasurement.request(true);
            for(String family:List.of("photon","bliss-shader"))for(var selected:List.of(OptimizationId.SPATIAL_LIGHTING_REUSE,OptimizationId.SPATIAL_LIGHTING_RATE)) {
                OptimizationConfig.selectFamily(family);
                var other=selected==OptimizationId.SPATIAL_LIGHTING_REUSE?OptimizationId.SPATIAL_LIGHTING_RATE:OptimizationId.SPATIAL_LIGHTING_REUSE;
                var original=OptimizationSnapshot.noneRequested().with(other,true).with(OptimizationId.CLOUD_OPACITY_EXIT,true);
                OptimizationConfig.replace(original);byte[] bytes=Files.readAllBytes(file);long generation=OptimizationConfig.generation();
                var pair=RuntimeMeasurement.pair();String scene=RuntimeMeasurement.sceneProvenance(),status=RuntimeMeasurement.status();var settingsCalls=new AtomicInteger();
                try {RuntimeMeasurement.startPair(selected,1,2,()->{settingsCalls.incrementAndGet();throw new AssertionError("settings capture reached before refusal");});throw new AssertionError("conflicting comparison accepted");}
                catch(IllegalStateException expected){check(expected.getMessage().contains("Turn "+other.key()+" Off"),"actionable counterpart Off reason");}
                check(OptimizationConfig.snapshot().equals(original)&&OptimizationConfig.generation()==generation,"no flag or generation mutation "+family+" "+selected);
                check(Arrays.equals(bytes,Files.readAllBytes(file)),"no persisted profile mutation");
                check(RuntimeMeasurement.requested()&&RuntimeMeasurement.pair()==pair&&RuntimeMeasurement.sceneProvenance().equals(scene)&&RuntimeMeasurement.status().equals(status),"no timing/pair/scene/status ownership mutation");
                check(settingsCalls.get()==0,"no settings/source capture");
                var pins=new AtomicInteger();var applies=new AtomicInteger();
                try {PairedMeasurement.prepare(selected,1,2,()->{pins.incrementAndGet();return Map.of();},flags->{applies.incrementAndGet();return true;});throw new AssertionError("pure caller bypassed graph conflict");}
                catch(IllegalStateException expected){check(expected.getMessage().contains(other.key()),"direct paired caller has same refusal");}
                check(pins.get()==0&&applies.get()==0&&OptimizationConfig.snapshot().equals(original),"direct caller has no source/apply mutation");
                PairedMeasurement.requireIndependentSelection(family,selected,original.with(other,false));check(true,"counterpart Off admitted by pure preflight");
                PairedMeasurement.requireIndependentSelection(family,OptimizationId.AO_RENDER_SCALE,original.with(selected,true));check(true,"unrelated selected optimization unchanged");
            }
            PairedMeasurement.requireIndependentSelection("bsl-shaders",OptimizationId.SPATIAL_LIGHTING_REUSE,OptimizationSnapshot.allRequested());check(true,"other families retain existing control behavior");
            check(!CompileOptionControl.controlledOptions(OptimizationId.SPATIAL_LIGHTING_REUSE).contains(CompileOptionControl.PHOTON_LIGHTING_RATE)&&!CompileOptionControl.controlledOptions(OptimizationId.SPATIAL_LIGHTING_RATE).contains(CompileOptionControl.PHOTON_SPATIAL),"the other Photon private option stays in fixed pin comparison");
            System.out.println("PASS "+checks+" actual runtime/direct paired preflight no-mutation and fixed-other-option checks; no client/GL used");
        }finally {RuntimeMeasurement.request(timingBefore);OptimizationConfig.restoreState(previous);}
    }
}
