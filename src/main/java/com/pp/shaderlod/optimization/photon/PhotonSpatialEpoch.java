package com.pp.shaderlod.optimization.photon;
import java.util.*;
/** Completed actual draw identity, never a setup declaration or carried-over cache. */
public final class PhotonSpatialEpoch {
 public record Context(Object pipeline,Object pack,Object targets,long generation,int frame,int width,int height,int producer,int consumer,Map<String,String> options) {public Context{options=Map.copyOf(options);}}
 public record Output(int cache,int sky,int fbo){}
 private Context context;private Output produced;private boolean completed;
 public void begin(Context now){context=now;produced=null;completed=false;}
 public void returned(Context now,Output output,boolean actualDrawReturned,boolean beforeAndAfterValid){completed=actualDrawReturned&&beforeAndAfterValid&&Objects.equals(context,now);produced=completed?output:null;}
 public boolean consumer(Context now,int cache,int sky){return completed&&Objects.equals(context,now)&&produced!=null&&produced.cache()==cache&&produced.sky()==sky;}
 public void revoke(){completed=false;produced=null;}
}
