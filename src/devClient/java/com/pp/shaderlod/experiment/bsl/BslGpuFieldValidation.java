package com.pp.shaderlod.experiment.bsl;

/** Complete bit-exact reducer on GPU-converted echo X(t); no tolerance. */
public final class BslGpuFieldValidation {
    private BslGpuFieldValidation() {}
    public record Result(boolean valid,boolean normalDomain,int minima,int mismatches,
                         int firstX,int firstY,int firstExpectedBits,int firstActualBits,
                         boolean stampExact,int cpuGpuDifferentTexels,int cpuGpuDifferentMinima) {}
    public static Result compare(float[] echo,float[] physicalField,float[] cpu,int frame) {
        int size=2048*2048;
        if(echo==null||physicalField==null||echo.length!=size||physicalField.length!=size
            ||cpu!=null&&cpu.length!=size||frame<0||frame>=16777215)
            throw new IllegalArgumentException("ECHO_SIZE_OR_FRAME");
        boolean domain=true;int different=0;
        for(int i=0;i<size;i++) {
            if(!BslRawQueryContract.normalDepth(echo[i]))domain=false;
            if(cpu!=null&&bits(cpu[i])!=bits(echo[i]))different++;
        }
        int mismatches=0,firstX=-1,firstY=-1,expected=0,actual=0,cpuMinDiff=0;
        for(int cy=0;cy<128;cy++)for(int cx=0;cx<128;cx++) {
            if(cx==127&&cy==127)continue;
            float minimum=1,cpumin=1;
            for(int y=0;y<16;y++)for(int x=0;x<16;x++) {
                int i=(cy*16+y)*2048+cx*16+x;
                float z=echo[i];minimum=z>=0&&z<=1?Math.min(minimum,z):0;
                if(cpu!=null){float c=cpu[i];cpumin=c>=0&&c<=1?Math.min(cpumin,c):0;}
            }
            int e=bits(minimum),a=bits(physicalField[cy*2048+cx]);
            if(cpu!=null&&bits(cpumin)!=e)cpuMinDiff++;
            if(e!=a){mismatches++;if(firstX<0){firstX=cx;firstY=cy;expected=e;actual=a;}}
        }
        boolean stamp=bits(physicalField[127*2048+127])==bits(-(float)(frame+1));
        return new Result(domain&&stamp&&mismatches==0,domain,16383,mismatches,
            firstX,firstY,expected,actual,stamp,different,cpuMinDiff);
    }
    private static int bits(float value){return Float.floatToRawIntBits(value);}
}
