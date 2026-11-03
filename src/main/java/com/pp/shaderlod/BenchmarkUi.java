package com.pp.shaderlod;

import com.pp.shaderlod.benchmark.BenchmarkWorld;
import com.pp.shaderlod.benchmark.BenchmarkRunSession;
import com.pp.shaderlod.tuning.AutoTuner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Normal client entry points; no development launcher or external controller required. */
public final class BenchmarkUi {
    private static boolean wasTuning;
    private static boolean wasBenchmarking;
    private BenchmarkUi(){}
    public static void title(ScreenEvent.Init.Post event){
        var s=event.getScreen();if(!(s instanceof TitleScreen))return;
        event.addListener(Button.builder(Component.literal("ShaderLOD benchmark"),b->Minecraft.getInstance().setScreen(new BenchmarkScreen(s)))
                .bounds(Math.max(4,s.width-154),6,148,20).build());
    }
    private static void withDemo(Runnable ready){
        var client=Minecraft.getInstance();
        if(BenchmarkWorld.ready()){client.setScreen(null);ready.run();return;}
        if(client.level!=null)throw new IllegalStateException("Save & Quit, then open ShaderLOD benchmark from the main menu");
        if(BenchmarkWorld.active())throw new IllegalStateException(BenchmarkWorld.status());
        BenchmarkWorld.open(()->{
            try{client.setScreen(null);ready.run();}
            catch(RuntimeException failure){client.gui.getChat().addMessage(Component.literal("ShaderLOD: "+failure.getMessage()));}
        });
    }
    public static void start(int seconds,boolean full){start(new com.pp.shaderlod.tuning.TuningRequest(com.pp.shaderlod.tuning.TuningGoal.PERFORMANCE,seconds,full));}
    public static void start(com.pp.shaderlod.tuning.TuningRequest request) {
        BenchmarkRunSession.requireIdle();
        AutoTuner.requireIdleForBenchmark();
        if(com.pp.shaderlod.tuning.SavedTunings.pending())throw new IllegalStateException("Use saved tuning Undo before starting another tune");
        withDemo(()->{
            AutoTuner.start(request);
            if(!AutoTuner.active())Minecraft.getInstance().gui.getChat().addMessage(Component.literal("ShaderLOD: "+AutoTuner.status()));
        });
    }
    public static void benchmark(){BenchmarkRunSession.start();}
    public static void benchmark(com.pp.shaderlod.benchmark.BenchmarkScenario.DhState expectedDh){BenchmarkRunSession.start(expectedDh);}
    public static Component benchmarkResultsMessage(){
        var message=Component.literal(BenchmarkRunSession.results());
        var result=BenchmarkRunSession.lastResult();
        if(result!=null)message.withStyle(style->style.withUnderlined(true).withClickEvent(new net.minecraft.network.chat.ClickEvent(
                net.minecraft.network.chat.ClickEvent.Action.OPEN_FILE,result.toString())));
        return message;
    }
    public static void openDemo(){BenchmarkRunSession.requireIdle();withDemo(()->{});}
    public static void flythrough(){BenchmarkRunSession.requireIdle();withDemo(BenchmarkWorld::beginFlythrough);}
    public static void undo(){BenchmarkRunSession.requireIdle();withDemo(AutoTuner::undo);}
    public static void exitDemo(){
        if(BenchmarkRunSession.owned()){BenchmarkRunSession.cancel();return;}
        if(com.pp.shaderlod.tuning.SavedTunings.pending())throw new IllegalStateException("Saved tuning restoration is pending; use its Undo before exiting");
        if(AutoTuner.active())AutoTuner.cancel();
        if(AutoTuner.restorationPending())throw new IllegalStateException("Tuner restoration is pending; keep this demo open and use Undo before exiting");
        com.pp.shaderlod.measurement.RuntimeMeasurement.cancelActivePair();
        com.pp.shaderlod.measurement.RuntimeMeasurement.request(false);
        BenchmarkWorld.exit();
    }
    public static void overlay(RenderGuiEvent.Post event){
        var client=Minecraft.getInstance();
        if(wasBenchmarking&&!BenchmarkRunSession.owned())client.gui.getChat().addMessage(benchmarkResultsMessage());
        wasBenchmarking=BenchmarkRunSession.owned();
        if(wasTuning&&!AutoTuner.active())client.gui.getChat().addMessage(Component.literal("ShaderLOD: "+AutoTuner.status()));
        wasTuning=AutoTuner.active();
        if(client.options.hideGui||!BenchmarkWorld.active()&&!AutoTuner.active()&&!BenchmarkRunSession.owned())return;
        String text=BenchmarkRunSession.owned()?BenchmarkRunSession.status():AutoTuner.status();
        var g=event.getGuiGraphics();
        g.drawString(client.font,client.font.plainSubstrByWidth("ShaderLOD: "+text,client.getWindow().getGuiScaledWidth()-12),6,6,0xffffff,true);
        g.drawString(client.font,client.font.plainSubstrByWidth(BenchmarkWorld.status(),client.getWindow().getGuiScaledWidth()-12),6,19,0xb0d8ff,true);
        if(client.level!=null)g.drawString(client.font,BenchmarkRunSession.owned()?"/shaderlod benchmark status | cancel | results":"/shaderlod tune status | cancel | undo | demo exit",6,32,0xb0d8ff,true);
    }
}
