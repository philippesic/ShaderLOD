package com.pp.shaderlod;
import com.pp.shaderlod.optimization.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.irisshaders.iris.Iris;
/** Staged independent requests. Cancel and closing never publish edits. */
public final class OptimizationScreen extends Screen {
    private final Screen parent;
    private OptimizationSnapshot staged=OptimizationConfig.snapshot();
    private final String family=OptimizationConfig.activeFamily();
    private int selected; private String feedback="Requests do not override native applicability guards";
    public OptimizationScreen(Screen parent) { super(Component.literal("Independent optimizations")); this.parent=parent; }
    private OptimizationId selected() { return OptimizationId.values()[selected]; }
    @Override protected void init() {
        int x=width/2-150,y=height/2-65;
        addRenderableWidget(Button.builder(Component.literal("<"),b->{ selected=(selected+OptimizationId.values().length-1)%OptimizationId.values().length; rebuildWidgets(); }).bounds(x,y,30,20).build());
        addRenderableWidget(Button.builder(Component.literal(selected().key()),b->{ selected=(selected+1)%OptimizationId.values().length; rebuildWidgets(); }).bounds(x+35,y,230,20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b->{ selected=(selected+1)%OptimizationId.values().length; rebuildWidgets(); }).bounds(x+270,y,30,20).build());
        addRenderableWidget(Button.builder(Component.literal("Requested: "+(staged.enabled(selected())?"On":"Off")),b->{ staged=staged.with(selected(),!staged.enabled(selected())); rebuildWidgets(); }).bounds(x,y+25,300,20).build());
        addRenderableWidget(Button.builder(Component.literal("Request all On"),b->{ staged=OptimizationSnapshot.allRequested(); rebuildWidgets(); }).bounds(x,y+50,145,20).build());
        addRenderableWidget(Button.builder(Component.literal("Request all Off"),b->{ staged=OptimizationSnapshot.noneRequested(); rebuildWidgets(); }).bounds(x+155,y+50,145,20).build());
        addRenderableWidget(Button.builder(Component.literal("Timing On/Off"),b->{com.pp.shaderlod.measurement.RuntimeMeasurement.request(!com.pp.shaderlod.measurement.RuntimeMeasurement.requested());feedback=com.pp.shaderlod.measurement.RuntimeMeasurement.status();}).bounds(x,y+110,95,20).build());
        addRenderableWidget(Button.builder(Component.literal("Compare selected"),b->{
            try {if(!family.equals(OptimizationConfig.activeFamily()))throw new IllegalStateException("Shader changed; reopen optimization controls");minecraft.setScreen(null);com.pp.shaderlod.measurement.RuntimeMeasurement.startPair(selected(),120,240,()->ShaderLodConfig.snapshot().toString()+ShaderLodConfig.PRESET.get());}
            catch(RuntimeException failure){feedback=failure.getMessage();minecraft.setScreen(this);}
        }).bounds(x+100,y+110,130,20).build());
        addRenderableWidget(Button.builder(Component.literal("Export"),b->{
            try {feedback=com.pp.shaderlod.measurement.RuntimeMeasurement.export(Iris.getShaderpacksDirectory().resolveSibling("shaderlod-measurements")).toString();}
            catch(java.io.IOException failure){feedback=failure.getMessage();}
        }).bounds(x+235,y+110,65,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel comparison"),b->{com.pp.shaderlod.measurement.RuntimeMeasurement.cancelActivePair();feedback=com.pp.shaderlod.measurement.RuntimeMeasurement.status();}).bounds(x,y+135,300,20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply"),b->{ com.pp.shaderlod.measurement.RuntimeMeasurement.cancelActivePair();if(!family.equals(OptimizationConfig.activeFamily())){feedback="Shader changed; reopen optimization controls";return;}var result=OptimizationEditTransaction.apply(staged,Iris::reload,Iris::isFallback,CompileOptionControl::open); feedback=result.message(); if(result.success()) onClose(); }).bounds(x,y+80,145,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(x+155,y+80,145,20).build());
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        super.render(graphics,mouseX,mouseY,partialTick);
        graphics.drawCenteredString(font,com.pp.shaderlod.measurement.RuntimeMeasurement.status(),width/2,height/2-80,0xffffff);
        graphics.drawCenteredString(font,title.copy().append(" — "+family),width/2,height/2-100,0xffffff);
        String implementation=OptimizationRuntimeContext.implementation(family,selected());
        graphics.drawCenteredString(font,font.plainSubstrByWidth(implementation,width-20),width/2,height/2-115,0xaaaaaa);
        graphics.drawCenteredString(font,feedback,width/2,height/2+95,0xffffff);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
