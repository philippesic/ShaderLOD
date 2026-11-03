package com.pp.shaderlod.optimization.blisspixel;
import java.util.Objects;
/** Frozen frame-wide scale. No program can independently switch coordinate systems. */
public final class BlissPixelFrame {
 public record Key(Object pipeline,Object pack,Object targets,Object world,Object camera,int frame,int width,int height,long source,long flags,Object core){}
 private Key previous,current;private boolean complete,selected,previousSelected,reset;private int epoch;
 public void invalidate(){previous=current=null;complete=selected=previousSelected=false;reset=true;epoch=0;}
 private boolean continuous(Key k){return complete&&previous!=null&&k.pipeline()==previous.pipeline()&&k.pack()==previous.pack()&&k.targets()==previous.targets()&&k.world()==previous.world()&&k.camera()==previous.camera()&&k.frame()==previous.frame()+1&&k.width()==previous.width()&&k.height()==previous.height()&&k.source()==previous.source()&&k.flags()==previous.flags()&&Objects.equals(k.core(),previous.core());}
 public void begin(Key k,boolean qualified){boolean healthy=k!=null&&k.width()>1&&k.height()>1&&continuous(k);current=k;selected=qualified&&healthy;reset=!healthy||selected!=previousSelected;complete=false;epoch=epoch>=16777216?1:epoch+1;}
 public void returned(Key k,boolean actualTaaReturned){complete=current!=null&&current.equals(k)&&actualTaaReturned;previous=current;previousSelected=selected;}
 public boolean selected(){return selected;}public boolean wasSelected(){return previousSelected;}public boolean reset(){return reset;}public int epoch(){return epoch;}
}
