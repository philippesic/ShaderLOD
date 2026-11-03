package com.pp.shaderlod.optimization.photonhistory;
/** Exact base-only color-history law; scalar proofs do not establish observed GL readiness. */
public final class PhotonAoHistoryContract {
 private PhotonAoHistoryContract(){}
 public record Image(int id,int main,int alt,int width,int height,int ownerWidth,int ownerHeight,int level1Width,int level1Height,int format,int base,int compare,int min,int mag,int wrapS,int wrapT,float bias,float minLod,float maxLod,float aniso,int r,int g,int b,int a){}
 public record Frame(int counter,long generation,int width,int height,int history6,int history14,int output6,int output14){}
 public static boolean image(Image x,int format){return x!=null&&x.id()>0&&(x.id()==x.main()||x.id()==x.alt())&&x.width()>0&&x.height()>0&&x.width()==x.ownerWidth()&&x.height()==x.ownerHeight()&&x.level1Width()==0&&x.level1Height()==0&&x.format()==format&&x.base()==0&&x.compare()==0&&x.min()==0x2601&&x.mag()==0x2601&&x.wrapS()==0x812F&&x.wrapT()==0x812F&&x.bias()==0&&Float.isFinite(x.minLod())&&Float.isFinite(x.maxLod())&&x.minLod()<=0&&x.maxLod()>=0&&x.aniso()==1&&x.r()==0x1903&&x.g()==0x1904&&x.b()==0x1905&&x.a()==0x1906;}
 public static boolean history(Frame old,Frame now){return old!=null&&now!=null&&old.counter()>=0&&old.counter()<Integer.MAX_VALUE&&now.counter()==old.counter()+1&&old.generation()==now.generation()&&old.width()==now.width()&&old.height()==now.height()&&now.history6()==old.output6()&&now.history14()==old.output14()&&now.history6()!=now.output6()&&now.history14()!=now.output14();}
}
