package com.pp.shaderlod;

import com.pp.shaderlod.benchmark.BenchmarkWorld;
import com.pp.shaderlod.benchmark.BenchmarkRunSession;
import com.pp.shaderlod.benchmark.BenchmarkScenario;
import com.pp.shaderlod.tuning.AutoTuner;
import com.pp.shaderlod.tuning.TuningGoal;
import com.pp.shaderlod.tuning.TuningRequest;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Entry to the owned benchmark save; results and undo remain available after a run. */
public final class BenchmarkScreen extends Screen {
    private final Screen parent;
    private String feedback="";
    private TuningGoal goal=TuningGoal.PERFORMANCE;
    private boolean tuningControls;
    private BenchmarkScenario.DhState expectedDh=BenchmarkRunSession.suggestedDhState();
    public BenchmarkScreen(Screen parent){super(Component.literal("ShaderLOD benchmark and tune"));this.parent=parent;}
    @Override protected void init(){
        int left=width/2-155,top=62;
        if(tuningControls){
            button("Tuning goal: "+goal.label(),left,top,310,()->{goal=TuningGoal.values()[(goal.ordinal()+1)%TuningGoal.values().length];rebuildWidgets();});
            button("Quick tune (5 minutes)",left,top+23,150,()->BenchmarkUi.start(TuningRequest.quick(goal)));
            button("Full tune (60 min/segment)",left+160,top+23,150,()->BenchmarkUi.start(TuningRequest.full(goal)));
            button("Cancel tuning",left,top+46,150,AutoTuner::cancel);
            button("Undo last tune",left+160,top+46,150,BenchmarkUi::undo);
            button("Saved tunings",left,top+69,150,()->{BenchmarkRunSession.requireIdle();minecraft.setScreen(new SavedTuningsScreen(this));});
            button("Tuning results",left+160,top+69,150,()->Util.getPlatform().openPath(minecraft.gameDirectory.toPath().resolve("shaderlod-tuning")));
            button("Benchmark controls",left,top+92,150,()->{tuningControls=false;rebuildWidgets();});
            button("Back",left+160,top+92,150,this::onClose);
            return;
        }
        button("Run FAST benchmark",left,top,150,()->BenchmarkUi.benchmark(expectedDh));
        button("Cancel benchmark",left+160,top,150,BenchmarkRunSession::cancel);
        button("Expected DH: "+expectedDh,left,top+23,150,()->{
            expectedDh=switch(expectedDh){case ABSENT->BenchmarkScenario.DhState.INSTALLED_DISABLED;case INSTALLED_DISABLED->BenchmarkScenario.DhState.ENABLED;default->BenchmarkScenario.DhState.ABSENT;};rebuildWidgets();
        });
        button("Benchmark results",left+160,top+23,150,()->minecraft.setScreen(new com.pp.shaderlod.benchmark.BenchmarkRunResultsScreen(this)));
        button("Run diagnostic (up to 15 min)",left,top+46,310,()->BenchmarkRunSession.startDiagnostic(expectedDh));
        button("Explore demo",left,top+69,150,BenchmarkUi::openDemo);
        button("Watch flythrough",left+160,top+69,150,BenchmarkUi::flythrough);
        button("Tuning controls",left,top+92,310,()->{tuningControls=true;rebuildWidgets();});
        button("Exit demo",left,top+115,150,BenchmarkUi::exitDemo);
        button("Back",left+160,top+115,150,this::onClose);
    }
    private void button(String label,int x,int y,int w,Runnable action){
        addRenderableWidget(Button.builder(Component.literal(label),b->{
            try{action.run();feedback="";}catch(RuntimeException failure){feedback=failure.getMessage()==null?failure.toString():failure.getMessage();}
        }).bounds(x,y,w,20).build());
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        super.render(g,x,y,partial);
        g.drawCenteredString(font,title,width/2,20,0xffffff);
        g.drawCenteredString(font,"Uses a separate, repeatable demo world and your selected shader",width/2,37,0xc0c0c0);
        g.drawCenteredString(font,tuningControls?"Quick is partial; Full resumes with unchanged, bound inputs.":"FAST: selected settings, native AA; RD/resolution unchanged; no tuning.",width/2,49,0xc0c0c0);
        g.drawCenteredString(font,Component.literal(font.plainSubstrByWidth(BenchmarkRunSession.owned()?BenchmarkRunSession.status():BenchmarkRunSession.lastResult()!=null?BenchmarkRunSession.results():AutoTuner.status(),width-20)),width/2,height-30,0xb0d8ff);
        g.drawCenteredString(font,Component.literal(font.plainSubstrByWidth(feedback.isBlank()?BenchmarkWorld.status():feedback,width-20)),width/2,height-16,feedback.isBlank()?0xb0d8ff:0xff9999);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(minecraft.level==null?parent:null);}
}
