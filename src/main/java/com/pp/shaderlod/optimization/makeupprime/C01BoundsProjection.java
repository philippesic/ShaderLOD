package com.pp.shaderlod.optimization.makeupprime;

/** Reusable outward interval arithmetic for the pinned FP32 position expression.
 * gamma(7) bounds a four-product dot with any addition order or FMA contraction.
 * Both legal matrix association forms are enclosed. No heuristic world margin.
 */
public final class C01BoundsProjection {
 private static final double U=0x1p-24,G7=7*U/(1-7*U);
 private final double[] matrixLo=new double[16],matrixHi=new double[16];
 private final double[] lo=new double[4],hi=new double[4],viewLo=new double[4],viewHi=new double[4];
 private final float[] p=new float[16],m=new float[16];
 public void matrices(float[] projection,float[] model){
  System.arraycopy(projection,0,p,0,16);System.arraycopy(model,0,m,0,16);
  for(int c=0;c<4;c++)for(int r=0;r<4;r++){
   double sum=0,absolute=0;
   for(int k=0;k<4;k++){double term=(double)p[k*4+r]*m[c*4+k];sum+=term;absolute+=Math.abs(term);}
   double error=G7*absolute+0x1p-120;
   matrixLo[c*4+r]=Math.nextDown(sum-error);matrixHi[c*4+r]=Math.nextUp(sum+error);
  }
 }
 private static double roundedLow(double value){return Math.nextDown((float)value);}
 private static double roundedHigh(double value){return Math.nextUp((float)value);}
 private static double productLow(double a,double b,double c,double d){return Math.min(Math.min(a*c,a*d),Math.min(b*c,b*d));}
 private static double productHigh(double a,double b,double c,double d){return Math.max(Math.max(a*c,a*d),Math.max(b*c,b*d));}
 private void combined(double[] xlo,double[] xhi){
  for(int r=0;r<4;r++){
   double low=0,high=0,absolute=0;
   for(int k=0;k<4;k++){
    double a=productLow(matrixLo[k*4+r],matrixHi[k*4+r],xlo[k],xhi[k]);
    double b=productHigh(matrixLo[k*4+r],matrixHi[k*4+r],xlo[k],xhi[k]);
    low+=a;high+=b;absolute+=Math.max(Math.abs(a),Math.abs(b));
   }
   double error=G7*absolute+0x1p-120;lo[r]=Math.nextDown(low-error);hi[r]=Math.nextUp(high+error);
  }
 }
 private static void transform(float[] matrix,double[] xlo,double[] xhi,double[] outLo,double[] outHi){
  for(int r=0;r<4;r++){
   double low=0,high=0,absolute=0;
   for(int k=0;k<4;k++){
    double a=matrix[k*4+r],b=a>=0?a*xlo[k]:a*xhi[k],c=a>=0?a*xhi[k]:a*xlo[k];
    low+=b;high+=c;absolute+=Math.max(Math.abs(b),Math.abs(c));
   }
   double error=G7*absolute+0x1p-120;outLo[r]=Math.nextDown(low-error);outHi[r]=Math.nextUp(high+error);
  }
 }
 private final double[] xlo=new double[4],xhi=new double[4],otherLo=new double[4],otherHi=new double[4];
 /** out=minX,minY,maxX,maxY,minZ in NDC. False means native fail-open. */
 public boolean project(float[] min,float[] max,float[] offset,float jitterX,float jitterY,int width,int height,float[] out){
  if(width<=0||height<=0||!Float.isFinite(jitterX)||!Float.isFinite(jitterY))return false;
  boolean inside=true;
  for(int a=0;a<3;a++){
   // Native expression is (decoded + regionOffset) + localSectionTranslation.
   xlo[a]=roundedLow(min[a]+(double)offset[a]);xhi[a]=roundedHigh(max[a]+(double)offset[a]);
   inside&=xlo[a]<=0&&xhi[a]>=0;
  }
  if(inside)return false;
  out[0]=out[1]=out[4]=Float.POSITIVE_INFINITY;out[2]=out[3]=Float.NEGATIVE_INFINITY;
  for(int corner=0;corner<8;corner++){
   for(int a=0;a<3;a++){
    double value=((corner&(1<<a))==0?min[a]:max[a])+(double)offset[a];
    xlo[a]=roundedLow(value);xhi[a]=roundedHigh(value);
   }
   xlo[3]=xhi[3]=1;
   combined(xlo,xhi);
   transform(m,xlo,xhi,viewLo,viewHi);transform(p,viewLo,viewHi,otherLo,otherHi);
   for(int r=0;r<4;r++){lo[r]=Math.min(lo[r],otherLo[r]);hi[r]=Math.max(hi[r],otherHi[r]);}
   // Every corner must remain on the positive-w, uncut side of the near plane.
   if(!(lo[3]>0)||!(lo[2]+lo[3]>0))return false;
   for(int a=0;a<2;a++){
    double jitter=a==0?jitterX:jitterY;
    double low=jitter>=0?jitter*lo[3]:jitter*hi[3],high=jitter>=0?jitter*hi[3]:jitter*lo[3];
    lo[a]=roundedLow(lo[a]+roundedLow(low));hi[a]=roundedHigh(hi[a]+roundedHigh(high));
   }
   for(int a=0;a<3;a++){
    double low=productLow(lo[a],hi[a],1/hi[3],1/lo[3]);
    double high=productHigh(lo[a],hi[a],1/hi[3],1/lo[3]);
    if(!Double.isFinite(low)||!Double.isFinite(high))return false;
    // Outward float conversion plus one full viewport pixel on either side.
    if(a<2){double pixel=2.0/(a==0?width:height);out[a]=Math.min(out[a],Math.nextDown((float)(low-pixel)));out[a+2]=Math.max(out[a+2],Math.nextUp((float)(high+pixel)));}
    else out[4]=Math.min(out[4],Math.nextDown((float)low));
   }
  }
  return out[4]>-1&&out[4]<1&&out[0]<1&&out[2]>-1&&out[1]<1&&out[3]>-1;
 }
}
