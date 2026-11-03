package com.pp.shaderlod;

import com.pp.shaderlod.tuning.*;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A small normal-client library; saving/importing does not apply settings or certify a win. */
public final class SavedTuningsScreen extends Screen {
    private final Screen parent;
    private List<TuningPreset> presets=List.of();private List<Path> imports=List.of();
    private int selected,selectedImport;private String name="My tuning",feedback="Load a shader in a world to save or apply; imported settings need a fresh tune";
    public SavedTuningsScreen(Screen parent){super(Component.literal("Saved tunings"));this.parent=parent;}
    private TuningPreset selected(){if(presets.isEmpty())throw new IllegalStateException("No saved tuning selected");return presets.get(selected);}
    private void refresh(){presets=SavedTunings.list();imports=SavedTunings.imports();selected=Math.min(selected,Math.max(0,presets.size()-1));selectedImport=Math.min(selectedImport,Math.max(0,imports.size()-1));}
    @Override protected void init() {
        try{refresh();}catch(RuntimeException e){feedback=e.getMessage();}
        int x=width/2-155,y=38;
        var edit=new EditBox(font,x,y,310,18,Component.literal("Tuning name"));edit.setMaxLength(64);edit.setValue(name);edit.setResponder(v->name=v);addRenderableWidget(edit);
        button(presets.isEmpty()?"No saved tunings":font.plainSubstrByWidth(selected().name()+" — "+selected().evidence().provenance().label(),290),x,y+22,310,()->{
            if(presets.isEmpty())return;selected=(selected+1)%presets.size();name=selected().name();feedback=SavedTunings.compatibility(selected());rebuildWidgets();
        });
        button("Save current as new",x,y+44,150,()->{var p=SavedTunings.saveCurrent(name);feedback="Saved "+p.name()+"; "+p.evidence().provenance().label();rebuildWidgets();});
        button("Rename selected",x+160,y+44,150,()->{SavedTunings.rename(selected().id(),name);feedback="Renamed; settings unchanged";rebuildWidgets();});
        button("Apply selected",x,y+66,150,()->{SavedTunings.apply(selected().id());feedback="Applied; "+SavedTunings.compatibility(selected());});
        button("Undo preset Apply",x+160,y+66,150,()->{SavedTunings.undo();feedback="Original preset settings restored";});
        button("Edit current controls",x,y+88,150,()->{if(AutoTuner.active()||AutoTuner.restorationPending()||SavedTunings.pending())throw new IllegalStateException("Restore pending work first");minecraft.setScreen(new OptimizationScreen(this));});
        button("Export selected",x+160,y+88,150,()->{var p=SavedTunings.export(selected().id());feedback="Exported preset JSON and evidence ZIP";Util.getPlatform().openPath(p.getParent());});
        button(imports.isEmpty()?"No import JSONs — open folder below":"Import: "+font.plainSubstrByWidth(imports.get(selectedImport).getFileName().toString(),240),x,y+110,310,()->{if(!imports.isEmpty())selectedImport=(selectedImport+1)%imports.size();rebuildWidgets();});
        button("Import selected JSON",x,y+132,150,()->{
            if(imports.isEmpty())throw new IllegalStateException("Copy a preset JSON into the imports folder first");
            var p=SavedTunings.importFile(imports.get(selectedImport).getFileName().toString());feedback="Imported "+p.name()+" as unverified settings; nothing applied";rebuildWidgets();
        });
        button("Open imports folder",x+160,y+132,150,()->{SavedTunings.imports();Util.getPlatform().openPath(SavedTunings.directory().resolve("imports"));});
        button("Back",x,y+154,310,this::onClose);
    }
    private void button(String label,int x,int y,int width,Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label),b->{try{action.run();}catch(RuntimeException e){feedback=e.getMessage()==null?e.toString():e.getMessage();}}).bounds(x,y,width,18).build());
    }
    @Override public void render(GuiGraphics g,int x,int y,float delta) {
        super.render(g,x,y,delta);g.drawCenteredString(font,title,width/2,12,0xffffff);
        g.drawCenteredString(font,"Named settings retain native quality choices; measurements are setup-specific",width/2,25,0xc0c0c0);
        g.drawCenteredString(font,font.plainSubstrByWidth(feedback,width-20),width/2,height-14,0xb0d8ff);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
