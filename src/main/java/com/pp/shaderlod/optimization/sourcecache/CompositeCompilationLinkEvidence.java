package com.pp.shaderlod.optimization.sourcecache;

import java.util.Objects;

/** One synchronous composite compile/link chain; partial or substituted evidence never publishes. */
public final class CompositeCompilationLinkEvidence<C> {
    public enum Stage { VERTEX, FRAGMENT }
    private final C context;
    private String vertex,fragment;
    private Object vertexShader,fragmentShader,builder,program;
    private int phase,id;
    public CompositeCompilationLinkEvidence(C context){this.context=Objects.requireNonNull(context);}
    public void poison(){phase=-1;}
    public boolean poisoned(){return phase<0;}
    public void transformed(String vertex,String geometry,String fragment){
        if(phase!=0||vertex==null||fragment==null||geometry!=null){poison();return;}
        this.vertex=vertex;this.fragment=fragment;phase=1;
    }
    public void begin(String vertex,String geometry,String fragment){
        if(phase!=1||!Objects.equals(this.vertex,vertex)||geometry!=null||!Objects.equals(this.fragment,fragment)){poison();return;}
        phase=2;
    }
    public void shader(Stage stage,String source,Object shader){
        if(phase!=2||stage==null||shader==null){poison();return;}
        if(stage==Stage.VERTEX){
            if(vertexShader!=null||fragmentShader!=null||!vertex.equals(source)){poison();return;}
            vertexShader=shader;
        }else{
            if(vertexShader==null||fragmentShader!=null||!fragment.equals(source)||shader==vertexShader){poison();return;}
            fragmentShader=shader;
        }
    }
    public boolean shaders(Object[] shaders){
        return phase==2&&vertexShader!=null&&fragmentShader!=null&&shaders!=null&&shaders.length==2
            &&shaders[0]==vertexShader&&shaders[1]==fragmentShader;
    }
    public void linked(Object[] shaders,int id){
        if(!shaders(shaders)||id<=0){poison();return;}
        this.id=id;phase=3;
    }
    public void builder(Object builder,int id){
        if(phase!=3||builder==null||this.id!=id){poison();return;}
        this.builder=builder;phase=4;
    }
    public void built(Object builder,Object program,int id){
        if(phase!=4||this.builder!=builder||program==null||this.id!=id){poison();return;}
        this.program=program;phase=5;
    }
    public C complete(Object program,int id){
        if(phase!=5||this.program!=program||this.id!=id){poison();return null;}
        phase=6;return context;
    }
}
